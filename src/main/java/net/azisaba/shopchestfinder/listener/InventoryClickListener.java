package net.azisaba.shopchestfinder.listener;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.shop.Shop;
import net.azisaba.shopchestfinder.gui.ItemCategoryGUI;
import net.azisaba.shopchestfinder.gui.ShopListGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;

import java.util.List;

public class InventoryClickListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (title.startsWith("アイテム一覧 - ページ ") || title.startsWith("検索:")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            int currentPage;
            String keyword = null;
            if (title.startsWith("検索:")) {
                String[] split = title.split(" - ページ ");
                keyword = split[0].substring(3);
                if (keyword.endsWith("..")) keyword = keyword.substring(0, keyword.length() - 2); // Best effort, exact keyword is lost if truncated, but for pagination it's mostly fine
                currentPage = Integer.parseInt(split[1]);
            } else {
                currentPage = Integer.parseInt(title.replace("アイテム一覧 - ページ ", ""));
            }
            
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getRawSlot();

            if (clickedItem.getType() == Material.ARROW) {
                if (clickedItem.getItemMeta().getDisplayName().contains("前")) {
                    ItemCategoryGUI.open(player, currentPage - 1, keyword);
                } else if (clickedItem.getItemMeta().getDisplayName().contains("次")) {
                    ItemCategoryGUI.open(player, currentPage + 1, keyword);
                }
            } else if (slot < 45) {
                java.util.Map<Integer, ItemStack> slotMap = ItemCategoryGUI.slotItemMap.get(player.getUniqueId());
                if (slotMap != null && slotMap.containsKey(slot)) {
                    ShopListGUI.open(player, slotMap.get(slot), 1);
                }
            }
        } else if (title.startsWith("ショップ一覧 - ページ ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

            int currentPage = Integer.parseInt(title.replace("ショップ一覧 - ページ ", ""));
            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getRawSlot();
            
            if (clickedItem.getType() == Material.ARROW) {
                ItemStack targetItem = ShopListGUI.viewMap.get(player.getUniqueId());
                if (targetItem != null) {
                    if (clickedItem.getItemMeta().getDisplayName().contains("前")) {
                        ShopListGUI.open(player, targetItem, currentPage - 1);
                    } else if (clickedItem.getItemMeta().getDisplayName().contains("次")) {
                        ShopListGUI.open(player, targetItem, currentPage + 1);
                    }
                }
            } else if (clickedItem.getType() == Material.BARRIER && clickedItem.getItemMeta().getDisplayName().contains("カテゴリ一覧に戻る")) {
                int lastPage = net.azisaba.shopchestfinder.gui.ItemCategoryGUI.lastPageMap.getOrDefault(player.getUniqueId(), 1);
                String lastKeyword = net.azisaba.shopchestfinder.gui.ItemCategoryGUI.lastKeywordMap.get(player.getUniqueId());
                ItemCategoryGUI.open(player, lastPage, lastKeyword);
            } else {
                ItemStack targetItem = ShopListGUI.viewMap.get(player.getUniqueId());
                if (targetItem != null && slot < 45) {
                    int index = (currentPage - 1) * 45 + slot;
                    int i = 0;
                    boolean isAdmin = player.hasPermission("shopchestfinder.admin");
                    for (Shop shop : net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getShopCache().getCachedShops()) {
                        ItemStack product = shop.getProduct().getItemStack();
                        if (product != null) {
                            ItemStack strippedProduct = net.azisaba.shopchestfinder.ShopChestFinder.stripUniqueData(product);
                            if (strippedProduct.isSimilar(targetItem)) {
                                if (isAdmin || net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getConfigManager().isPublic(shop.getVendor().getUniqueId()) || shop.getVendor().getUniqueId().equals(player.getUniqueId())) {
                                    if (i == index) {
                                        if (isAdmin || net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getConfigManager().isTpAllowed(shop.getVendor().getUniqueId())) {
                                            player.teleport(shop.getLocation());
                                            player.sendMessage("§aショップへテレポートしました！");
                                        } else {
                                            player.sendMessage("§cこのショップはテレポートを許可していません。座標を元に徒歩で向かってください。");
                                            org.bukkit.Location loc = shop.getLocation();
                                            player.sendMessage("§eショップの座標: §b" + loc.getWorld().getName() + " §e/ §b" + loc.getBlockX() + " §e/ §b" + loc.getBlockY() + " §e/ §b" + loc.getBlockZ());
                                            player.closeInventory();
                                        }
                                        break;
                                    }
                                    i++;
                                }
                            }
                        }
                    }
                }
            }
        } else if (title.equals("ショップ公開設定")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 11) {
                boolean isPublic = net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getConfigManager().isPublic(player.getUniqueId());
                net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getConfigManager().setPublic(player.getUniqueId(), !isPublic);
                player.sendMessage("§aショップの公開設定を変更しました。キャッシュを更新しています...");
                net.azisaba.shopchestfinder.gui.SettingsGUI.open(player);
                Bukkit.getScheduler().runTaskAsynchronously(net.azisaba.shopchestfinder.ShopChestFinder.getInstance(), () -> {
                    net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getShopCache().updateCache();
                    player.sendMessage("§aキャッシュの更新が完了しました！");
                });
            } else if (event.getRawSlot() == 15) {
                boolean isTpAllowed = net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getConfigManager().isTpAllowed(player.getUniqueId());
                if (!isTpAllowed) {
                    net.azisaba.shopchestfinder.gui.TPConfirmGUI.open(player);
                } else {
                    net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getConfigManager().setTpAllowed(player.getUniqueId(), false);
                    player.sendMessage("§aショップへのTPを非許可にしました。");
                    net.azisaba.shopchestfinder.gui.SettingsGUI.open(player);
                }
            }
        } else if (title.equals("TP許可の確認")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 11) { // OK
                net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getConfigManager().setTpAllowed(player.getUniqueId(), true);
                player.sendMessage("§aショップへのTPを許可しました！");
                net.azisaba.shopchestfinder.gui.SettingsGUI.open(player);
            } else if (event.getRawSlot() == 15) { // CANCEL
                net.azisaba.shopchestfinder.gui.SettingsGUI.open(player);
            }
        }
    }
}
