package net.azisaba.shopchestfinder.util

import org.bukkit.Bukkit
import org.bukkit.Location
import java.lang.reflect.Method

/**
 * Resolves the Towny town a location belongs to.
 *
 * Accessed via reflection so the plugin neither requires Towny nor depends
 * on one specific Towny version: `TownyAPI#getTownName(Location)` is used on
 * newer versions and `TownyUniverse#getTownName(Location)` on older ones.
 */
object TownResolver {
    private var initialized = false
    private var apiInstance: Any? = null
    private var getTownName: Method? = null

    private fun init() {
        initialized = true
        if (!Bukkit.getPluginManager().isPluginEnabled("Towny")) return

        try {
            val api = Class.forName("com.palmergames.bukkit.towny.TownyAPI")
            apiInstance = api.getMethod("getInstance").invoke(null)
            getTownName = api.getMethod("getTownName", Location::class.java)
            return
        } catch (_: ReflectiveOperationException) {
            // Older Towny without TownyAPI
        } catch (_: LinkageError) {
        }

        try {
            val universe = Class.forName("com.palmergames.bukkit.towny.object.TownyUniverse")
            getTownName = universe.getMethod("getTownName", Location::class.java)
            apiInstance = null // static method
        } catch (_: ReflectiveOperationException) {
            unsupported()
        } catch (_: LinkageError) {
            unsupported()
        }
    }

    private fun unsupported() {
        Bukkit.getLogger().warning("[ShopChestFinder] Unsupported Towny version, town names are not shown")
        getTownName = null
    }

    /**
     * Must be called on the main thread.
     *
     * @return The name of the town at the location, or `null` for
     *         wilderness or if Towny is unavailable
     */
    fun getTownName(worldName: String, x: Int, y: Int, z: Int): String? {
        if (!initialized) init()
        val method = getTownName ?: return null
        val world = Bukkit.getWorld(worldName) ?: return null

        return try {
            method.invoke(apiInstance, Location(world, x.toDouble(), y.toDouble(), z.toDouble()))?.toString()
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}
