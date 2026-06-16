package net.azisaba.shopchestfinder.command;

import net.azisaba.shopchestfinder.gui.ItemCategoryGUI;
import net.azisaba.shopchestfinder.gui.SettingsGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FindShopCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is only for players.");
            return true;
        }
        
        Player player = (Player) sender;
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("settings")) {
                SettingsGUI.open(player);
                return true;
            } else if (args[0].equalsIgnoreCase("search")) {
                if (args.length > 1) {
                    String keyword = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    ItemCategoryGUI.open(player, 1, keyword);
                } else {
                    player.sendMessage("§c使用方法: /findshop search <キーワード>");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("config")) {
                if (!sender.hasPermission("shopchestfinder.admin")) {
                    sender.sendMessage("§cこのコマンドを実行する権限がありません。");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§c使用方法: /findshop config <group-materials|exclude-materials|exclude-vanilla-materials> <add|remove> <Material>");
                    return true;
                }
                String listName = args[1].toLowerCase();
                if (!listName.equals("group-materials") && !listName.equals("exclude-materials") && !listName.equals("exclude-vanilla-materials")) {
                    sender.sendMessage("§c無効なリスト名です。");
                    return true;
                }
                String action = args[2].toLowerCase();
                String materialName = args[3].toUpperCase();
                
                try {
                    org.bukkit.Material.valueOf(materialName);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§c無効なマテリアル名です: " + materialName);
                    return true;
                }
                
                org.bukkit.configuration.file.FileConfiguration config = net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getConfig();
                java.util.List<String> list = config.getStringList(listName);
                if (action.equals("add")) {
                    if (!list.contains(materialName)) {
                        list.add(materialName);
                        config.set(listName, list);
                        net.azisaba.shopchestfinder.ShopChestFinder.getInstance().saveConfig();
                        sender.sendMessage("§a" + listName + " に " + materialName + " を追加しました。");
                        net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getShopCache().updateCache();
                    } else {
                        sender.sendMessage("§c既にリストに含まれています。");
                    }
                } else if (action.equals("remove")) {
                    if (list.contains(materialName)) {
                        list.remove(materialName);
                        config.set(listName, list);
                        net.azisaba.shopchestfinder.ShopChestFinder.getInstance().saveConfig();
                        sender.sendMessage("§a" + listName + " から " + materialName + " を削除しました。");
                        net.azisaba.shopchestfinder.ShopChestFinder.getInstance().getShopCache().updateCache();
                    } else {
                        sender.sendMessage("§cリストに含まれていません。");
                    }
                } else {
                    sender.sendMessage("§c使用方法: /findshop config <リスト名> <add|remove> <Material>");
                }
                return true;
            } else {
                try {
                    int page = Integer.parseInt(args[0]);
                    ItemCategoryGUI.open(player, page, null);
                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage("§c使用方法: /findshop [ページ番号 | search <キーワード> | settings | config ...]");
                    return true;
                }
            }
        }

        ItemCategoryGUI.open(player, 1, null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if ("search".startsWith(args[0].toLowerCase())) completions.add("search");
            if ("settings".startsWith(args[0].toLowerCase())) completions.add("settings");
            if (sender.hasPermission("shopchestfinder.admin") && "config".startsWith(args[0].toLowerCase())) completions.add("config");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("config") && sender.hasPermission("shopchestfinder.admin")) {
            if ("group-materials".startsWith(args[1].toLowerCase())) completions.add("group-materials");
            if ("exclude-materials".startsWith(args[1].toLowerCase())) completions.add("exclude-materials");
            if ("exclude-vanilla-materials".startsWith(args[1].toLowerCase())) completions.add("exclude-vanilla-materials");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("config") && sender.hasPermission("shopchestfinder.admin")) {
            if ("add".startsWith(args[2].toLowerCase())) completions.add("add");
            if ("remove".startsWith(args[2].toLowerCase())) completions.add("remove");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("config") && sender.hasPermission("shopchestfinder.admin")) {
            for (org.bukkit.Material mat : org.bukkit.Material.values()) {
                if (mat.name().startsWith(args[3].toUpperCase())) {
                    completions.add(mat.name());
                }
            }
        }
        return completions;
    }
}
