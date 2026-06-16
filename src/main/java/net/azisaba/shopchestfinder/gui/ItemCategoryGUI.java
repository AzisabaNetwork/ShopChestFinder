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
import java.util.Comparator;
import java.util.List;

public class ItemCategoryGUI {
    
    private static final int ITEMS_PER_PAGE = 45;
    
    public static final java.util.Map<java.util.UUID, Integer> lastPageMap = new java.util.HashMap<>();
    public static final java.util.Map<java.util.UUID, String> lastKeywordMap = new java.util.HashMap<>();
    
    public static final java.util.Map<java.util.UUID, java.util.Map<Integer, ItemStack>> slotItemMap = new java.util.HashMap<>();
    
    public static class ItemStats {
        public ItemStack item;
        public double maxBuy = -1;
        public double minBuy = Double.MAX_VALUE;
        public double sumBuy = 0;
        public int countBuy = 0;

        public double maxSell = -1;
        public double minSell = Double.MAX_VALUE;
        public double sumSell = 0;
        public int countSell = 0;

        public ItemStats(ItemStack item) {
            this.item = item.clone();
            this.item.setAmount(1);
        }

        public ItemStats clone() {
            ItemStats copy = new ItemStats(this.item);
            copy.maxBuy = this.maxBuy;
            copy.minBuy = this.minBuy;
            copy.sumBuy = this.sumBuy;
            copy.countBuy = this.countBuy;
            copy.maxSell = this.maxSell;
            copy.minSell = this.minSell;
            copy.sumSell = this.sumSell;
            copy.countSell = this.countSell;
            return copy;
        }

        public void merge(ItemStats other) {
            if (other.maxBuy > this.maxBuy) this.maxBuy = other.maxBuy;
            if (other.minBuy < this.minBuy) this.minBuy = other.minBuy;
            this.sumBuy += other.sumBuy;
            this.countBuy += other.countBuy;

            if (other.maxSell > this.maxSell) this.maxSell = other.maxSell;
            if (other.minSell < this.minSell) this.minSell = other.minSell;
            this.sumSell += other.sumSell;
            this.countSell += other.countSell;
        }

        public void addShop(Shop shop) {
            if (shop.getBuyPrice() > 0) {
                double p = shop.getBuyPrice();
                if (p > maxBuy) maxBuy = p;
                if (p < minBuy) minBuy = p;
                sumBuy += p;
                countBuy++;
            }
            if (shop.getSellPrice() > 0) {
                double p = shop.getSellPrice();
                if (p > maxSell) maxSell = p;
                if (p < minSell) minSell = p;
                sumSell += p;
                countSell++;
            }
        }
    }

    public static List<ItemStats> getStatsList(Player player) {
        if (player.hasPermission("shopchestfinder.admin")) {
            return net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getShopCache().getAdminStats();
        } else {
            List<ItemStats> publicStats = net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getShopCache().getPublicStats();
            java.util.Map<java.util.UUID, List<ItemStats>> privateStats = net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getShopCache().getPrivateStats();
            
            List<ItemStats> playerPrivateStats = privateStats.get(player.getUniqueId());
            if (playerPrivateStats == null || playerPrivateStats.isEmpty()) {
                return publicStats;
            }
            
            List<ItemStats> merged = new ArrayList<>();
            for (ItemStats ps : publicStats) {
                merged.add(ps.clone());
            }
            
            for (ItemStats myStat : playerPrivateStats) {
                boolean found = false;
                for (ItemStats m : merged) {
                    if (m.item.isSimilar(myStat.item)) {
                        m.merge(myStat);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    merged.add(myStat.clone());
                }
            }
            
            merged.sort(Comparator.comparing(s -> s.item.getType().name()));
            return merged;
        }
    }

    public static void open(Player player, int page, String keyword) {
        lastPageMap.put(player.getUniqueId(), page);
        if (keyword == null || keyword.isEmpty()) {
            lastKeywordMap.remove(player.getUniqueId());
        } else {
            lastKeywordMap.put(player.getUniqueId(), keyword);
        }

        List<ItemStats> statsList = getStatsList(player);
        List<ItemStats> filtered = new ArrayList<>();
        
        if (keyword != null && !keyword.isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            for (ItemStats stats : statsList) {
                String name = stats.item.getType().name().toLowerCase();
                if (stats.item.hasItemMeta() && stats.item.getItemMeta().hasDisplayName()) {
                    name = stats.item.getItemMeta().getDisplayName().toLowerCase();
                }
                if (name.contains(lowerKeyword)) {
                    filtered.add(stats);
                }
            }
        } else {
            filtered.addAll(statsList);
        }

        int totalPages = (int) Math.ceil((double) filtered.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        
        String titlePrefix = keyword != null && !keyword.isEmpty() ? "検索:" + (keyword.length() > 8 ? keyword.substring(0, 8) + ".." : keyword) : "アイテム一覧";
        Inventory inv = Bukkit.createInventory(null, 54, titlePrefix + " - ページ " + page);
        
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filtered.size());
        
        java.util.Map<Integer, ItemStack> slotMap = new java.util.HashMap<>();
        
        for (int i = startIndex; i < endIndex; i++) {
            ItemStats stats = filtered.get(i);
            int slot = i - startIndex;
            slotMap.put(slot, stats.item.clone());
            
            ItemStack item = stats.item.clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                lore.add("");
                if (stats.countBuy > 0) {
                    lore.add("§e【販売価格 (プレイヤーが買う価格)】");
                    lore.add("§7最大: §a" + String.format("%.2f", stats.maxBuy));
                    lore.add("§7最小: §a" + String.format("%.2f", stats.minBuy));
                    lore.add("§7平均: §a" + String.format("%.2f", stats.sumBuy / stats.countBuy));
                }
                if (stats.countSell > 0) {
                    lore.add("§e【買取価格 (プレイヤーが売る価格)】");
                    lore.add("§7最大: §c" + String.format("%.2f", stats.maxSell));
                    lore.add("§7最小: §c" + String.format("%.2f", stats.minSell));
                    lore.add("§7平均: §c" + String.format("%.2f", stats.sumSell / stats.countSell));
                }
                lore.add("");
                lore.add("§aクリックして販売中のショップ一覧を見る");
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }
        
        slotItemMap.put(player.getUniqueId(), slotMap);
        
        if (page > 1) {
            inv.setItem(45, createButton(Material.ARROW, "§a前のページ"));
        }
        
        ItemStack warningItem = new ItemStack(Material.BOOK);
        ItemMeta warningMeta = warningItem.getItemMeta();
        if (warningMeta != null) {
            warningMeta.setDisplayName("§c§l【注意】");
            List<String> lore = new ArrayList<>();
            lore.add("§7表示されている価格はあくまで目安です。");
            lore.add("§7実際の価格や在庫を保証するものではありません。");
            lore.add("§7価格はあてにしないでください。");
            warningMeta.setLore(lore);
            warningItem.setItemMeta(warningMeta);
        }
        inv.setItem(49, warningItem);

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
