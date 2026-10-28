package net.azisaba.shopchestfinder.config;

import net.azisaba.shopchestfinder.ShopChestFinder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player settings stored in data.yml: whether a vendor's shops are listed
 * for other players and whether other players may teleport to them.
 */
public class ConfigManager {

    private final ShopChestFinder plugin;
    private final File dataFile;
    // Read by the async cache rebuild, hence concurrent
    private final Set<UUID> publicPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> tpAllowedPlayers = ConcurrentHashMap.newKeySet();
    private final Object saveLock = new Object();

    public ConfigManager(ShopChestFinder plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        readUuids(data.getStringList("public-players"), publicPlayers);
        readUuids(data.getStringList("tp-allowed-players"), tpAllowedPlayers);
    }

    private void readUuids(List<String> values, Set<UUID> target) {
        for (String value : values) {
            try {
                target.add(UUID.fromString(value));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in data.yml: " + value);
            }
        }
    }

    public boolean isPublic(UUID uuid) {
        return publicPlayers.contains(uuid);
    }

    public void setPublic(UUID uuid, boolean isPublic) {
        if (isPublic ? publicPlayers.add(uuid) : publicPlayers.remove(uuid)) {
            saveAsync();
        }
    }

    public boolean isTpAllowed(UUID uuid) {
        return tpAllowedPlayers.contains(uuid);
    }

    public void setTpAllowed(UUID uuid, boolean allowed) {
        if (allowed ? tpAllowedPlayers.add(uuid) : tpAllowedPlayers.remove(uuid)) {
            saveAsync();
        }
    }

    private void saveAsync() {
        if (!plugin.isEnabled()) {
            saveNow();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveNow);
    }

    public void saveNow() {
        // Snapshot and write under one lock so an older snapshot never overwrites a newer one
        synchronized (saveLock) {
            YamlConfiguration data = new YamlConfiguration();
            data.set("public-players", toStrings(publicPlayers));
            data.set("tp-allowed-players", toStrings(tpAllowedPlayers));

            try {
                data.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save data.yml: " + e.getMessage());
            }
        }
    }

    private static List<String> toStrings(Set<UUID> uuids) {
        List<String> result = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) result.add(uuid.toString());
        return result;
    }
}
