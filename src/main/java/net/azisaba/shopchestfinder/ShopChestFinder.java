package net.azisaba.shopchestfinder;

import net.azisaba.shopchestfinder.cache.ShopCache;
import net.azisaba.shopchestfinder.command.FindShopCommand;
import net.azisaba.shopchestfinder.config.ConfigManager;
import net.azisaba.shopchestfinder.gui.FinderMenu;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.PluginCommand;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ShopChestFinder extends JavaPlugin {

    public static final List<String> MATERIAL_LISTS = Collections.unmodifiableList(
            Arrays.asList("group-materials", "exclude-materials", "exclude-vanilla-materials"));

    private static ShopChestFinder instance;
    private ConfigManager configManager;
    private ShopCache shopCache;
    private FinderMenu menu;

    // Replaced as a whole on reload; read from the async cache rebuild
    private volatile Set<Material> groupMaterials = EnumSet.noneOf(Material.class);
    private volatile Set<Material> excludeMaterials = EnumSet.noneOf(Material.class);
    private volatile Set<Material> excludeVanillaMaterials = EnumSet.noneOf(Material.class);
    private volatile double ignoredPriceThreshold;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadSettings();

        configManager = new ConfigManager(this);
        shopCache = new ShopCache(this);
        menu = new FinderMenu(this);

        PluginCommand command = getCommand("findshop");
        if (command != null) {
            FindShopCommand executor = new FindShopCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getServer().getPluginManager().registerEvents(menu, this);

        shopCache.start(Math.max(5, getConfig().getLong("refresh-interval", 60)) * 20L);
        getLogger().info("ShopChestFinder has been enabled.");
    }

    @Override
    public void onDisable() {
        if (configManager != null) {
            configManager.saveNow();
        }
        getLogger().info("ShopChestFinder has been disabled.");
    }

    /**
     * Reads the material lists and the price threshold from config.yml.
     */
    public void loadSettings() {
        ignoredPriceThreshold = getConfig().getDouble("ignore-price-at-or-below", 1);
        groupMaterials = readMaterials("group-materials");
        excludeMaterials = readMaterials("exclude-materials");
        excludeVanillaMaterials = readMaterials("exclude-vanilla-materials");
    }

    private Set<Material> readMaterials(String list) {
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (String name : getConfig().getStringList(list)) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                getLogger().warning("Invalid material in " + list + ": " + name);
            } else {
                result.add(material);
            }
        }
        return result;
    }

    public static ShopChestFinder getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ShopCache getShopCache() {
        return shopCache;
    }

    public FinderMenu getMenu() {
        return menu;
    }

    /**
     * @return Prices at or below this value are treated as disabled (display shops)
     */
    public double getIgnoredPriceThreshold() {
        return ignoredPriceThreshold;
    }

    public static boolean isAdmin(Player player) {
        return player.hasPermission("shopchestfinder.admin");
    }

    /**
     * @return Whether the product should be hidden from the finder
     */
    public boolean isExcluded(ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        Material type = item.getType();

        if (excludeMaterials.contains(type)) return true;

        if (excludeVanillaMaterials.contains(type)) {
            if (!item.hasItemMeta()) return true;
            ItemMeta meta = item.getItemMeta();
            return !meta.hasDisplayName() && !meta.hasLore();
        }
        return false;
    }

    /**
     * Removes data that should not split otherwise equal products into
     * separate entries (shulker box contents, map IDs, or everything for
     * materials listed in {@code group-materials}).
     *
     * @return A copy of the item with an amount of 1
     */
    public ItemStack stripUniqueData(ItemStack item) {
        if (groupMaterials.contains(item.getType())) {
            return new ItemStack(item.getType());
        }

        ItemStack clone = item.clone();
        clone.setAmount(1);

        if (clone.hasItemMeta() && clone.getItemMeta() instanceof BlockStateMeta) {
            BlockStateMeta bsm = (BlockStateMeta) clone.getItemMeta();
            if (bsm.hasBlockState()) {
                BlockState state = bsm.getBlockState();
                if (state instanceof ShulkerBox) {
                    ((ShulkerBox) state).getInventory().clear();
                    bsm.setBlockState(state);
                    clone.setItemMeta(bsm);
                }
            }
        }

        if (clone.getType() == Material.FILLED_MAP) {
            // Keep only what players can see: name, lore and enchantments
            ItemStack plainMap = new ItemStack(Material.FILLED_MAP);
            ItemMeta plainMeta = plainMap.getItemMeta();
            if (clone.hasItemMeta() && plainMeta != null) {
                ItemMeta oldMeta = clone.getItemMeta();
                if (oldMeta.hasDisplayName()) plainMeta.setDisplayName(oldMeta.getDisplayName());
                if (oldMeta.hasLore()) plainMeta.setLore(oldMeta.getLore());
                for (Map.Entry<Enchantment, Integer> entry : oldMeta.getEnchants().entrySet()) {
                    plainMeta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
                plainMap.setItemMeta(plainMeta);
            }
            return plainMap;
        }

        return clone;
    }
}
