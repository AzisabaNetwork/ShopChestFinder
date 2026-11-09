package net.azisaba.shopchestfinder.config

import net.azisaba.shopchestfinder.ShopChestFinder
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player settings stored in data.yml: whether a vendor's shops are listed
 * for other players and whether other players may teleport to them.
 */
class ConfigManager(private val plugin: ShopChestFinder) {

    private val dataFile: File
    // Read by the async cache rebuild, hence concurrent
    private val publicPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val tpAllowedPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val saveLock = Any()

    init {
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
        }
        dataFile = File(plugin.dataFolder, "data.yml")
        load()
    }

    private fun load() {
        val data = YamlConfiguration.loadConfiguration(dataFile)
        readUuids(data.getStringList("public-players"), publicPlayers)
        readUuids(data.getStringList("tp-allowed-players"), tpAllowedPlayers)
    }

    private fun readUuids(values: List<String>, target: MutableSet<UUID>) {
        for (value in values) {
            try {
                target.add(UUID.fromString(value))
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("Invalid UUID in data.yml: $value")
            }
        }
    }

    fun isPublic(uuid: UUID): Boolean = uuid in publicPlayers

    fun setPublic(uuid: UUID, isPublic: Boolean) {
        if (if (isPublic) publicPlayers.add(uuid) else publicPlayers.remove(uuid)) {
            saveAsync()
        }
    }

    fun isTpAllowed(uuid: UUID): Boolean = uuid in tpAllowedPlayers

    fun setTpAllowed(uuid: UUID, allowed: Boolean) {
        if (if (allowed) tpAllowedPlayers.add(uuid) else tpAllowedPlayers.remove(uuid)) {
            saveAsync()
        }
    }

    private fun saveAsync() {
        if (!plugin.isEnabled) {
            saveNow()
            return
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { saveNow() })
    }

    fun saveNow() {
        // Snapshot and write under one lock so an older snapshot never overwrites a newer one
        synchronized(saveLock) {
            val data = YamlConfiguration()
            data.set("public-players", publicPlayers.map { it.toString() })
            data.set("tp-allowed-players", tpAllowedPlayers.map { it.toString() })

            try {
                data.save(dataFile)
            } catch (e: IOException) {
                plugin.logger.severe("Failed to save data.yml: ${e.message}")
            }
        }
    }
}
