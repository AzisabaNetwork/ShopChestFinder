package net.azisaba.shopchestfinder.command

import net.azisaba.shopchestfinder.ShopChestFinder
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.Locale

/**
 * `/findshop [page | search <keyword> | settings | config ...]`
 */
class FindShopCommand(private val plugin: ShopChestFinder) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isNotEmpty() && args[0].equals("config", ignoreCase = true)) {
            changeConfig(sender, args)
            return true
        }

        if (sender !is Player) {
            sender.sendMessage("This command is only for players.")
            return true
        }

        when {
            args.isEmpty() -> plugin.menu.openItems(sender, 1, null)
            args[0].equals("settings", ignoreCase = true) -> plugin.menu.openSettings(sender)
            args[0].equals("search", ignoreCase = true) -> {
                if (args.size > 1) {
                    plugin.menu.openItems(sender, 1, args.drop(1).joinToString(" "))
                } else {
                    sender.sendMessage("§c使用方法: /findshop search <キーワード>")
                }
            }
            else -> {
                val page = args[0].toIntOrNull()
                if (page != null) {
                    plugin.menu.openItems(sender, page, null)
                } else {
                    sender.sendMessage(USAGE)
                }
            }
        }
        return true
    }

    private fun changeConfig(sender: CommandSender, args: Array<String>) {
        if (!isAdmin(sender)) {
            sender.sendMessage("§cこのコマンドを実行する権限がありません。")
            return
        }
        if (args.size < 4) {
            sender.sendMessage(CONFIG_USAGE)
            return
        }

        val listName = args[1].lowercase(Locale.ROOT)
        if (listName !in ShopChestFinder.MATERIAL_LISTS) {
            sender.sendMessage("§c無効なリスト名です。")
            return
        }

        val material = Material.matchMaterial(args[3])
        if (material == null) {
            sender.sendMessage("§c無効なマテリアル名です: ${args[3]}")
            return
        }
        val materialName = material.name

        val list = plugin.config.getStringList(listName).toMutableList()
        // Entries may have been written by hand in a different case
        val contains = list.any { it.equals(materialName, ignoreCase = true) }

        when (args[2].lowercase(Locale.ROOT)) {
            "add" -> {
                if (contains) {
                    sender.sendMessage("§c既にリストに含まれています。")
                    return
                }
                list.add(materialName)
                sender.sendMessage("§a$listName に $materialName を追加しました。")
            }
            "remove" -> {
                if (!contains) {
                    sender.sendMessage("§cリストに含まれていません。")
                    return
                }
                list.removeIf { it.equals(materialName, ignoreCase = true) }
                sender.sendMessage("§a$listName から $materialName を削除しました。")
            }
            else -> {
                sender.sendMessage(CONFIG_USAGE)
                return
            }
        }

        plugin.config.set(listName, list)
        plugin.saveConfig()
        plugin.loadSettings()
        plugin.shopCache.refresh()
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>,
    ): List<String> {
        val completions = mutableListOf<String>()
        val config = args.size > 1 && args[0].equals("config", ignoreCase = true) && isAdmin(sender)

        when {
            args.size == 1 -> {
                addMatching(completions, args[0], "search", "settings")
                if (isAdmin(sender)) addMatching(completions, args[0], "config")
            }
            config && args.size == 2 -> addMatching(completions, args[1], *ShopChestFinder.MATERIAL_LISTS.toTypedArray())
            config && args.size == 3 -> addMatching(completions, args[2], "add", "remove")
            config && args.size == 4 -> {
                val prefix = args[3].uppercase(Locale.ROOT)
                @Suppress("DEPRECATION")
                Material.entries
                    .filter { it.isItem && !it.isLegacy && it.name.startsWith(prefix) }
                    .mapTo(completions) { it.name }
            }
        }
        return completions
    }

    private companion object {
        const val USAGE = "§c使用方法: /findshop [ページ番号 | search <キーワード> | settings | config ...]"
        const val CONFIG_USAGE =
            "§c使用方法: /findshop config <group-materials|exclude-materials|exclude-vanilla-materials> <add|remove> <Material>"

        fun isAdmin(sender: CommandSender): Boolean = sender !is Player || ShopChestFinder.isAdmin(sender)

        fun addMatching(completions: MutableList<String>, input: String, vararg options: String) {
            val lower = input.lowercase(Locale.ROOT)
            options.filterTo(completions) { it.startsWith(lower) }
        }
    }
}
