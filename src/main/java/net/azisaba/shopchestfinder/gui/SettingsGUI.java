package net.azisaba.shopchestfinder.gui;

import net.azisaba.shopchestfinder.ShopChestFinder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SettingsGUI {

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "ショップ公開設定");
        
        boolean isPublic = ShopChestFinder.getInstance().getConfigManager().isPublic(player.getUniqueId());
        
        ItemStack visibilityItem = new ItemStack(isPublic ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta visMeta = visibilityItem.getItemMeta();
        if (visMeta != null) {
            visMeta.setDisplayName(isPublic ? "§a検索公開設定: 公開中" : "§c検索公開設定: 非公開");
            List<String> lore = new ArrayList<>();
            lore.add("§7クリックして設定を切り替えます。");
            if (isPublic) {
                lore.add("§a他のプレイヤーがあなたのショップを検索できるようになっています。");
            } else {
                lore.add("§c他のプレイヤーの検索結果にあなたのショップは表示されません。");
            }
            visMeta.setLore(lore);
            visibilityItem.setItemMeta(visMeta);
        }
        
        boolean isTpAllowed = ShopChestFinder.getInstance().getConfigManager().isTpAllowed(player.getUniqueId());
        
        ItemStack tpItem = new ItemStack(isTpAllowed ? Material.ENDER_PEARL : Material.BARRIER);
        ItemMeta tpMeta = tpItem.getItemMeta();
        if (tpMeta != null) {
            tpMeta.setDisplayName(isTpAllowed ? "§aTP許可設定: 許可中" : "§cTP許可設定: 非許可");
            List<String> lore = new ArrayList<>();
            lore.add("§7クリックして設定を切り替えます。");
            if (isTpAllowed) {
                lore.add("§a他のプレイヤーがGUIから直接あなたのショップに");
                lore.add("§aテレポートできるようになっています。");
            } else {
                lore.add("§c他のプレイヤーはあなたのショップへテレポートできません。");
                lore.add("§c(徒歩で向かう必要があります)");
            }
            tpMeta.setLore(lore);
            tpItem.setItemMeta(tpMeta);
        }
        
        inv.setItem(11, visibilityItem);
        inv.setItem(15, tpItem);
        player.openInventory(inv);
    }
}
