package net.azisaba.shopchestfinder.cache;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.shop.Shop;
import net.azisaba.shopchestfinder.ShopChestFinder;
import net.azisaba.shopchestfinder.gui.ItemCategoryGUI.ItemStats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.azisaba.shopchestfinder.gui.ItemCategoryGUI.ItemStats;

public class ShopCache {
    private final ShopChestFinder plugin;
    
    private List<Shop> cachedShops = new ArrayList<>();
    private List<ItemStats> adminStats = new ArrayList<>();
    private List<ItemStats> publicStats = new ArrayList<>();
    private Map<java.util.UUID, List<ItemStats>> privateStats = new java.util.HashMap<>();

    public ShopCache(ShopChestFinder plugin) {
        this.plugin = plugin;
        startTask();
    }

    private void startTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateCache, 0L, 20L * 60L); // Update every 60 seconds
    }

    public void updateCache() {
        List<Shop> newShops = new ArrayList<>();
        List<ItemStats> newAdminStats = new ArrayList<>();
        List<ItemStats> newPublicStats = new ArrayList<>();
        Map<java.util.UUID, List<ItemStats>> newPrivateStats = new java.util.HashMap<>();

        for (Shop shop : ShopChest.getInstance().getShopUtils().getShops()) {
            ItemStack product = shop.getProduct().getItemStack();
            if (product != null && product.getType() != Material.AIR) {
                if (net.azisaba.shopchestfinder.ShopChestFinder.isExcluded(product)) continue;
                newShops.add(shop);
                ItemStack strippedProduct = net.azisaba.shopchestfinder.ShopChestFinder.stripUniqueData(product);
                addToStats(newAdminStats, shop, strippedProduct);
                if (plugin.getConfigManager().isPublic(shop.getVendor().getUniqueId())) {
                    addToStats(newPublicStats, shop, strippedProduct);
                } else {
                    List<ItemStats> pStats = newPrivateStats.computeIfAbsent(shop.getVendor().getUniqueId(), k -> new ArrayList<>());
                    addToStats(pStats, shop, strippedProduct);
                }
            }
        }
        
        newAdminStats.sort(Comparator.comparing(s -> s.item.getType().name()));
        newPublicStats.sort(Comparator.comparing(s -> s.item.getType().name()));

        this.cachedShops = newShops;
        this.adminStats = newAdminStats;
        this.publicStats = newPublicStats;
        this.privateStats = newPrivateStats;
    }

    private void addToStats(List<ItemStats> statsList, Shop shop, ItemStack product) {
        ItemStats found = null;
        for (ItemStats stats : statsList) {
            if (stats.item.isSimilar(product)) {
                found = stats;
                break;
            }
        }
        if (found == null) {
            found = new ItemStats(product);
            statsList.add(found);
        }
        found.addShop(shop);
    }

    public List<Shop> getCachedShops() {
        return cachedShops;
    }

    public List<ItemStats> getAdminStats() {
        return adminStats;
    }

    public List<ItemStats> getPublicStats() {
        return publicStats;
    }

    public Map<java.util.UUID, List<ItemStats>> getPrivateStats() {
        return privateStats;
    }
}
