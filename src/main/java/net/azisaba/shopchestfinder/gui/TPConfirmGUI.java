package net.azisaba.shopchestfinder.gui;

import de.epiceric.shopchest.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TPConfirmGUI {

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "TP許可の確認");
        
        ItemStack warningItem = new ItemStack(Material.PAPER);
        ItemMeta warningMeta = warningItem.getItemMeta();
        if (warningMeta != null) {
            warningMeta.setDisplayName("§e§l【警告】");
            List<String> lore = new ArrayList<>();
            lore.add("§c本当に許可しますか？");
            lore.add("§cこの設定は自己責任のため、");
            lore.add("§c問題が発生したときに運営は干渉できません。");
            warningMeta.setLore(lore);
            warningItem.setItemMeta(warningMeta);
        }
        
        ItemStack okItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta okMeta = okItem.getItemMeta();
        if (okMeta != null) {
            okMeta.setDisplayName("§a§l[OK] リスクを承知の上、許可する");
            okItem.setItemMeta(okMeta);
        }
        
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName("§c§l[CANCEL] 戻る");
            cancelItem.setItemMeta(cancelMeta);
        }
        
        inv.setItem(11, okItem);
        inv.setItem(13, warningItem);
        inv.setItem(15, cancelItem);
        
        player.openInventory(inv);
    }
}
