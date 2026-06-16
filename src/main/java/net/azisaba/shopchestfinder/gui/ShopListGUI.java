package net.azisaba.shopchestfinder.gui;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class ShopListGUI {
    private static final int SHOPS_PER_PAGE = 45;
    public static final Map<UUID, ItemStack> viewMap = new HashMap<>();

    public static void open(Player player, ItemStack targetItem, int page) {
        viewMap.put(player.getUniqueId(), targetItem);
        List<Shop> shops = new ArrayList<>();
        boolean isAdmin = player.hasPermission("shopchestfinder.admin");
        java.util.Set<org.bukkit.Location> seenLocations = new java.util.HashSet<>();
        
        for (Shop shop : net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getShopCache().getCachedShops()) {
            ItemStack product = shop.getProduct().getItemStack();
            if (product != null) {
                ItemStack strippedProduct = net.azisaba.shopchestfinder.ShopChestFinder.stripUniqueData(product);
                if (strippedProduct.isSimilar(targetItem)) {
                    if (isAdmin || net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getConfigManager().isPublic(shop.getVendor().getUniqueId()) || shop.getVendor().getUniqueId().equals(player.getUniqueId())) {
                        if (seenLocations.add(shop.getLocation())) {
                            shops.add(shop);
                        }
                    }
                }
            }
        }
        
        int totalPages = (int) Math.ceil((double) shops.size() / SHOPS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        
        Inventory inv = Bukkit.createInventory(null, 54, "ショップ一覧 - ページ " + page);
        
        int startIndex = (page - 1) * SHOPS_PER_PAGE;
        int endIndex = Math.min(startIndex + SHOPS_PER_PAGE, shops.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            Shop shop = shops.get(i);
            ItemStack item = targetItem.clone(); // use the target item icon to represent shop
            item.setAmount(1);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + shop.getVendor().getName() + " のショップ");
                List<String> lore = new ArrayList<>();
                lore.add("§7販売価格: §a" + shop.getBuyPrice());
                lore.add("§7買取価格: §c" + shop.getSellPrice());
                lore.add("§7座標: §f" + shop.getLocation().getBlockX() + ", " + shop.getLocation().getBlockY() + ", " + shop.getLocation().getBlockZ());
                lore.add("");
                lore.add("§eクリックしてテレポート！");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(i - startIndex, item);
        }
        
        // Navigation
        if (page > 1) {
            inv.setItem(45, createButton(Material.ARROW, "§a前のページ"));
        }
        inv.setItem(49, createButton(Material.BARRIER, "§cカテゴリ一覧に戻る"));
        if (page < totalPages) {
            inv.setItem(53, createButton(Material.ARROW, "§a次のページ"));
        }
        
        player.openInventory(inv);
    }

    private static ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
