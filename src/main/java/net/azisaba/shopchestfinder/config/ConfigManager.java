package net.azisaba.shopchestfinder.config;

import net.azisaba.shopchestfinder.ShopChestFinder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConfigManager {

    private final ShopChestFinder plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    private List<String> publicPlayers;
    private List<String> tpAllowedPlayers;

    public ConfigManager(ShopChestFinder plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
        loadConfig();
    }

    private void saveDefaultConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadConfig() {
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        publicPlayers = dataConfig.getStringList("public-players");
        if (publicPlayers == null) {
            publicPlayers = new ArrayList<>();
        }
        tpAllowedPlayers = dataConfig.getStringList("tp-allowed-players");
        if (tpAllowedPlayers == null) {
            tpAllowedPlayers = new ArrayList<>();
        }
    }

    public void saveConfig() {
        dataConfig.set("public-players", publicPlayers);
        dataConfig.set("tp-allowed-players", tpAllowedPlayers);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isPublic(UUID uuid) {
        return publicPlayers.contains(uuid.toString());
    }

    public void setPublic(UUID uuid, boolean isPublic) {
        if (isPublic && !publicPlayers.contains(uuid.toString())) {
            publicPlayers.add(uuid.toString());
        } else if (!isPublic) {
            publicPlayers.remove(uuid.toString());
        }
        saveConfig();
    }

    public boolean isTpAllowed(UUID uuid) {
        return tpAllowedPlayers.contains(uuid.toString());
    }

    public void setTpAllowed(UUID uuid, boolean isAllowed) {
        if (isAllowed && !tpAllowedPlayers.contains(uuid.toString())) {
            tpAllowedPlayers.add(uuid.toString());
        } else if (!isAllowed) {
            tpAllowedPlayers.remove(uuid.toString());
        }
        saveConfig();
    }
}
