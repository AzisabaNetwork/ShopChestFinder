package net.azisaba.shopchestfinder;

import net.azisaba.shopchestfinder.cache.ShopCache;
import net.azisaba.shopchestfinder.config.ConfigManager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopChestFinder extends JavaPlugin {

    private static ShopChestFinder instance;
    private ConfigManager configManager;
    private ShopCache shopCache;

    @Override
    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        this.getLogger().info("ShopChestFinder has been enabled.");
        
        configManager = new ConfigManager(this);
        shopCache = new ShopCache(this);
        
        getCommand("findshop").setExecutor(new net.azisaba.shopchestfinder.command.FindShopCommand());
        getServer().getPluginManager().registerEvents(new net.azisaba.shopchestfinder.listener.InventoryClickListener(), this);
    }

    @Override
    public void onDisable() {
        this.getLogger().info("ShopChestFinder has been disabled.");
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

    public static boolean isExcluded(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return false;
        String matName = item.getType().name();
        
        java.util.List<String> excludeMaterials = getInstance().getConfig().getStringList("exclude-materials");
        for (String mat : excludeMaterials) {
            if (mat.equalsIgnoreCase(matName)) return true;
        }
        
        java.util.List<String> excludeVanillaMaterials = getInstance().getConfig().getStringList("exclude-vanilla-materials");
        for (String mat : excludeVanillaMaterials) {
            if (mat.equalsIgnoreCase(matName)) {
                if (!item.hasItemMeta() || (!item.getItemMeta().hasDisplayName() && !item.getItemMeta().hasLore())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static ItemStack stripUniqueData(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return item;
        
        java.util.List<String> groupMaterials = getInstance().getConfig().getStringList("group-materials");
        for (String mat : groupMaterials) {
            if (mat.equalsIgnoreCase(item.getType().name())) {
                return new ItemStack(item.getType());
            }
        }

        ItemStack clone = item.clone();

        if (clone.hasItemMeta() && clone.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta) {
            org.bukkit.inventory.meta.BlockStateMeta bsm = (org.bukkit.inventory.meta.BlockStateMeta) clone.getItemMeta();
            if (bsm.hasBlockState() && bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox) {
                org.bukkit.block.ShulkerBox shulker = (org.bukkit.block.ShulkerBox) bsm.getBlockState();
                shulker.getInventory().clear();
                bsm.setBlockState(shulker);
                clone.setItemMeta(bsm);
            }
        }

        if (clone.getType() == org.bukkit.Material.FILLED_MAP || clone.getType().name().equals("MAP")) {
            ItemStack plainMap = new ItemStack(clone.getType());
            org.bukkit.inventory.meta.ItemMeta plainMeta = plainMap.getItemMeta();
            if (clone.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta oldMeta = clone.getItemMeta();
                if (oldMeta.hasDisplayName()) plainMeta.setDisplayName(oldMeta.getDisplayName());
                if (oldMeta.hasLore()) plainMeta.setLore(oldMeta.getLore());
                if (oldMeta.hasEnchants()) {
                    for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : oldMeta.getEnchants().entrySet()) {
                        plainMeta.addEnchant(entry.getKey(), entry.getValue(), true);
                    }
                }
            }
            plainMap.setItemMeta(plainMeta);
            return plainMap;
        }
        
        return clone;
    }
}
