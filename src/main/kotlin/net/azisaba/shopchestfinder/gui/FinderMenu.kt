package net.azisaba.shopchestfinder.gui

import de.epiceric.shopchest.ShopChest
import net.azisaba.shopchestfinder.ShopChestFinder
import net.azisaba.shopchestfinder.cache.ShopCache
import net.azisaba.shopchestfinder.cache.ShopCache.FinderShop
import net.azisaba.shopchestfinder.cache.ShopCache.ItemGroup
import net.azisaba.shopchestfinder.config.ConfigManager
import net.azisaba.shopchestfinder.util.TownResolver
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.Locale

/**
 * The finder's inventory menus and their click handling.
 *
 * Menus are identified by their [InventoryHolder] instead of their title, so
 * renamed chests can't be mistaken for a menu, and each menu keeps the exact
 * state (page, full search keyword, slot contents) it was built with. Clicks
 * therefore always act on what the player actually saw.
 */
@Suppress("DEPRECATION") // Legacy section sign strings are used for titles, names and lore
class FinderMenu(private val plugin: ShopChestFinder) : Listener {

    private enum class Type { ITEMS, SHOPS, SETTINGS, TP_CONFIRM }

    /** Holder carrying the state of an open menu. */
    private class Holder(val type: Type, val page: Int, val keyword: String?) : InventoryHolder {
        val groups = ArrayList<ItemGroup>()
        val shops = ArrayList<FinderShop>()

        // Where the "back" button of a shop list leads
        var itemPage = 1
        var itemKeyword: String? = null
        var group: ItemGroup? = null
        lateinit var inv: Inventory

        override fun getInventory(): Inventory = inv
    }

    /** Price statistics per single item of the shops a player can see for one product. */
    private class Stats(val group: ItemGroup) {
        val shops = ArrayList<FinderShop>()
        var minBuy = Double.MAX_VALUE
        var maxBuy = 0.0
        var sumBuy = 0.0
        var minSell = Double.MAX_VALUE
        var maxSell = 0.0
        var sumSell = 0.0
        var countBuy = 0
        var countSell = 0
    }

    private val settings: ConfigManager
        get() = plugin.configManager

    private fun canSee(player: Player, shop: FinderShop): Boolean =
        ShopChestFinder.isAdmin(player) || shop.vendor == player.uniqueId || settings.isPublic(shop.vendor)

    private fun canTeleport(player: Player, shop: FinderShop): Boolean =
        ShopChestFinder.isAdmin(player) || shop.vendor == player.uniqueId || settings.isTpAllowed(shop.vendor)

    private fun computeStats(player: Player, group: ItemGroup): Stats? {
        val stats = Stats(group)
        // Shops sell different amounts per trade, so compare prices per single item
        for (shop in group.shops) {
            if (!canSee(player, shop)) continue
            stats.shops += shop
            val unitBuy = shop.unitBuyPrice()
            if (unitBuy > 0) {
                stats.minBuy = minOf(stats.minBuy, unitBuy)
                stats.maxBuy = maxOf(stats.maxBuy, unitBuy)
                stats.sumBuy += unitBuy
                stats.countBuy++
            }
            val unitSell = shop.unitSellPrice()
            if (unitSell > 0) {
                stats.minSell = minOf(stats.minSell, unitSell)
                stats.maxSell = maxOf(stats.maxSell, unitSell)
                stats.sumSell += unitSell
                stats.countSell++
            }
        }
        return if (stats.shops.isEmpty()) null else stats
    }

    fun openItems(player: Player, requestedPage: Int, keyword: String?) {
        // Color codes typed into the keyword must not prevent a match either
        val plainKeyword = if (keyword == null) "" else ShopCache.stripColors(keyword).trim()
        val lowerKeyword = plainKeyword.ifEmpty { null }?.lowercase(Locale.ROOT)

        val entries = plugin.shopCache.groups
            .filter { lowerKeyword == null || lowerKeyword in it.searchText }
            .mapNotNull { computeStats(player, it) }

        val totalPages = pageCount(entries.size)
        val page = requestedPage.coerceIn(1, totalPages)

        val title = if (lowerKeyword != null) {
            "検索:" + if (plainKeyword.length > 8) plainKeyword.substring(0, 8) + ".." else plainKeyword
        } else {
            "アイテム一覧"
        }
        val holder = Holder(Type.ITEMS, page, if (lowerKeyword == null) null else plainKeyword)
        val inv = Bukkit.createInventory(holder, 54, "$title - ページ $page/$totalPages")
        holder.inv = inv

        val start = (page - 1) * PAGE_SIZE
        val end = minOf(start + PAGE_SIZE, entries.size)
        for (i in start until end) {
            val stats = entries[i]
            holder.groups += stats.group
            inv.setItem(i - start, createItemIcon(stats))
        }

        if (page > 1) inv.setItem(SLOT_PREVIOUS, createButton(Material.ARROW, "§a前のページ"))
        inv.setItem(
            SLOT_INFO,
            createButton(
                Material.BOOK, "§c§l【注意】",
                "§7表示されている価格はあくまで目安です。",
                "§7実際の価格や在庫を保証するものではありません。",
                "§7一覧は定期的に更新されるため、最新でない場合があります。",
            ),
        )
        inv.setItem(
            SLOT_SETTINGS,
            createButton(Material.COMPARATOR, "§eショップ公開設定", "§7クリックして自分のショップの公開設定を開きます。"),
        )
        if (page < totalPages) inv.setItem(SLOT_NEXT, createButton(Material.ARROW, "§a次のページ"))

        if (entries.isEmpty()) {
            inv.setItem(22, createButton(Material.BARRIER, "§cショップが見つかりませんでした"))
        }

        player.openInventory(inv)
    }

    private fun createItemIcon(stats: Stats): ItemStack {
        val item = stats.group.icon.clone()
        val meta = item.itemMeta ?: return item

        val lore = if (meta.hasLore()) meta.lore.orEmpty().toMutableList() else mutableListOf()
        lore += ""
        if (stats.countBuy > 0) {
            lore += "§e【販売価格 (プレイヤーが買う価格・1個あたり)】"
            lore += "§7最大: §a" + format(stats.maxBuy)
            lore += "§7最小: §a" + format(stats.minBuy)
            lore += "§7平均: §a" + format(stats.sumBuy / stats.countBuy)
        }
        if (stats.countSell > 0) {
            lore += "§e【買取価格 (プレイヤーが売る価格・1個あたり)】"
            lore += "§7最大: §c" + format(stats.maxSell)
            lore += "§7最小: §c" + format(stats.minSell)
            lore += "§7平均: §c" + format(stats.sumSell / stats.countSell)
        }
        lore += ""
        lore += "§7ショップ数: §f" + stats.shops.size
        lore += "§aクリックして販売中のショップ一覧を見る"
        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    private fun openShops(player: Player, group: ItemGroup, requestedPage: Int, itemPage: Int, itemKeyword: String?) {
        // Cheapest offers per item first; shops that don't sell are listed last
        val shops = group.shops
            .filter { canSee(player, it) }
            .sortedWith(
                compareBy<FinderShop> { if (it.buyPrice > 0) it.unitBuyPrice() else Double.MAX_VALUE }
                    .thenBy { -it.unitSellPrice() },
            )

        val totalPages = pageCount(shops.size)
        val page = requestedPage.coerceIn(1, totalPages)

        val holder = Holder(Type.SHOPS, page, null)
        holder.group = group
        holder.itemPage = itemPage
        holder.itemKeyword = itemKeyword
        val inv = Bukkit.createInventory(holder, 54, "ショップ一覧 - ページ $page/$totalPages")
        holder.inv = inv

        val start = (page - 1) * PAGE_SIZE
        val end = minOf(start + PAGE_SIZE, shops.size)
        for (i in start until end) {
            val shop = shops[i]
            holder.shops += shop
            inv.setItem(i - start, createShopIcon(player, group, shop))
        }

        if (page > 1) inv.setItem(SLOT_PREVIOUS, createButton(Material.ARROW, "§a前のページ"))
        inv.setItem(SLOT_INFO, createButton(Material.BARRIER, "§cアイテム一覧に戻る"))
        if (page < totalPages) inv.setItem(SLOT_NEXT, createButton(Material.ARROW, "§a次のページ"))

        player.openInventory(inv)
    }

    private fun createShopIcon(player: Player, group: ItemGroup, shop: FinderShop): ItemStack {
        val item = group.icon.clone()
        item.amount = shop.amount.coerceIn(1, maxOf(1, item.maxStackSize))
        val meta = item.itemMeta ?: return item

        meta.setDisplayName(if (shop.admin) "§6アドミンショップ" else "§e${shop.vendorName} のショップ")
        val lore = mutableListOf<String>()
        lore += "§7個数: §f${shop.amount}個"
        lore += "§7販売価格: " + if (shop.buyPrice > 0) {
            "§a${format(shop.buyPrice)} §7(1個あたり §a${format(shop.unitBuyPrice())}§7)"
        } else {
            "§8なし"
        }
        lore += "§7買取価格: " + if (shop.sellPrice > 0) {
            "§c${format(shop.sellPrice)} §7(1個あたり §c${format(shop.unitSellPrice())}§7)"
        } else {
            "§8なし"
        }
        // Looked up on open so renamed or newly claimed towns show up immediately
        val town = TownResolver.getTownName(shop.world, shop.x, shop.y, shop.z)
        lore += "§7町: " + if (town != null) "§b$town" else "§8なし"
        lore += "§7座標: §f${shop.world} ${shop.x}, ${shop.y}, ${shop.z}"
        lore += ""
        lore += if (canTeleport(player, shop)) "§eクリックしてテレポート！" else "§7テレポート不可 (クリックで座標を表示)"
        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    fun openSettings(player: Player) {
        val holder = Holder(Type.SETTINGS, 1, null)
        val inv = Bukkit.createInventory(holder, 27, "ショップ公開設定")
        holder.inv = inv

        val isPublic = settings.isPublic(player.uniqueId)
        inv.setItem(
            SLOT_TOGGLE_PUBLIC,
            if (isPublic) {
                createButton(
                    Material.LIME_DYE, "§a検索公開設定: 公開中",
                    "§7クリックして設定を切り替えます。",
                    "§a他のプレイヤーがあなたのショップを検索できるようになっています。",
                )
            } else {
                createButton(
                    Material.GRAY_DYE, "§c検索公開設定: 非公開",
                    "§7クリックして設定を切り替えます。",
                    "§c他のプレイヤーの検索結果にあなたのショップは表示されません。",
                )
            },
        )

        val tpAllowed = settings.isTpAllowed(player.uniqueId)
        inv.setItem(
            SLOT_TOGGLE_TP,
            if (tpAllowed) {
                createButton(
                    Material.ENDER_PEARL, "§aTP許可設定: 許可中",
                    "§7クリックして設定を切り替えます。",
                    "§a他のプレイヤーがGUIから直接あなたのショップに",
                    "§aテレポートできるようになっています。",
                )
            } else {
                createButton(
                    Material.BARRIER, "§cTP許可設定: 非許可",
                    "§7クリックして設定を切り替えます。",
                    "§c他のプレイヤーはあなたのショップへテレポートできません。",
                    "§c(徒歩で向かう必要があります)",
                )
            },
        )

        player.openInventory(inv)
    }

    private fun openTpConfirm(player: Player) {
        val holder = Holder(Type.TP_CONFIRM, 1, null)
        val inv = Bukkit.createInventory(holder, 27, "TP許可の確認")
        holder.inv = inv

        inv.setItem(SLOT_CONFIRM, createButton(Material.LIME_WOOL, "§a§l[OK] リスクを承知の上、許可する"))
        inv.setItem(
            13,
            createButton(
                Material.PAPER, "§e§l【警告】",
                "§c本当に許可しますか？",
                "§cこの設定は自己責任のため、",
                "§c問題が発生したときに運営は干渉できません。",
            ),
        )
        inv.setItem(SLOT_CANCEL, createButton(Material.RED_WOOL, "§c§l[CANCEL] 戻る"))

        player.openInventory(inv)
    }

    @EventHandler(ignoreCancelled = true)
    fun onInventoryDrag(e: InventoryDragEvent) {
        if (e.inventory.getHolder(false) is Holder) {
            e.isCancelled = true
        }
    }

    @EventHandler
    fun onInventoryClick(e: InventoryClickEvent) {
        val holder = e.inventory.getHolder(false) as? Holder ?: return

        // Cancel every click (including shift clicks from the player's own
        // inventory) so nothing can be moved into or out of the menu
        e.isCancelled = true

        val player = e.whoClicked as? Player ?: return
        val slot = e.rawSlot
        if (slot < 0 || slot >= e.inventory.size) return

        when (holder.type) {
            Type.ITEMS -> handleItemsClick(player, holder, slot)
            Type.SHOPS -> handleShopsClick(player, holder, slot)
            Type.SETTINGS -> handleSettingsClick(player, slot)
            Type.TP_CONFIRM -> handleTpConfirmClick(player, slot)
        }
    }

    private fun handleItemsClick(player: Player, holder: Holder, slot: Int) {
        when {
            slot < PAGE_SIZE -> holder.groups.getOrNull(slot)?.let {
                openShops(player, it, 1, holder.page, holder.keyword)
            }
            slot == SLOT_PREVIOUS && holder.page > 1 -> openItems(player, holder.page - 1, holder.keyword)
            slot == SLOT_NEXT -> openItems(player, holder.page + 1, holder.keyword)
            slot == SLOT_SETTINGS -> openSettings(player)
        }
    }

    private fun handleShopsClick(player: Player, holder: Holder, slot: Int) {
        val group = holder.group ?: return
        when {
            slot < PAGE_SIZE -> holder.shops.getOrNull(slot)?.let { teleport(player, it) }
            slot == SLOT_PREVIOUS && holder.page > 1 ->
                openShops(player, group, holder.page - 1, holder.itemPage, holder.itemKeyword)
            slot == SLOT_NEXT -> openShops(player, group, holder.page + 1, holder.itemPage, holder.itemKeyword)
            slot == SLOT_INFO -> openItems(player, holder.itemPage, holder.itemKeyword)
        }
    }

    private fun handleSettingsClick(player: Player, slot: Int) {
        val uuid = player.uniqueId

        if (slot == SLOT_TOGGLE_PUBLIC) {
            val isPublic = !settings.isPublic(uuid)
            settings.setPublic(uuid, isPublic)
            player.sendMessage(if (isPublic) "§aショップを検索に公開しました。" else "§aショップを検索から非公開にしました。")
            openSettings(player)
        } else if (slot == SLOT_TOGGLE_TP) {
            if (!settings.isTpAllowed(uuid)) {
                openTpConfirm(player)
            } else {
                settings.setTpAllowed(uuid, false)
                player.sendMessage("§aショップへのTPを非許可にしました。")
                openSettings(player)
            }
        }
    }

    private fun handleTpConfirmClick(player: Player, slot: Int) {
        if (slot == SLOT_CONFIRM) {
            settings.setTpAllowed(player.uniqueId, true)
            player.sendMessage("§aショップへのTPを許可しました！")
            openSettings(player)
        } else if (slot == SLOT_CANCEL) {
            openSettings(player)
        }
    }

    private fun teleport(player: Player, shop: FinderShop) {
        // Check the current setting, not the one from when the list was built
        val allowed = canTeleport(player, shop)
        val world = Bukkit.getWorld(shop.world)

        player.closeInventory()
        if (!allowed || world == null) {
            if (!allowed) {
                player.sendMessage("§cこのショップはテレポートを許可していません。座標を元に徒歩で向かってください。")
            }
            player.sendMessage("§eショップの座標: §b${shop.world} §e/ §b${shop.x} §e/ §b${shop.y} §e/ §b${shop.z}")
            return
        }

        // The shop's chunk may have been unloaded since the list was built
        world.getChunkAtAsync(shop.x shr 4, shop.z shr 4).thenAccept {
            if (!player.isOnline) return@thenAccept
            val target = findStandingLocation(world.getBlockAt(shop.x, shop.y, shop.z))
            player.teleportAsync(target).thenAccept { success ->
                if (success) player.sendMessage("§aショップへテレポートしました！")
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 45
        const val SLOT_PREVIOUS = 45
        const val SLOT_INFO = 49
        const val SLOT_SETTINGS = 51
        const val SLOT_NEXT = 53
        const val SLOT_TOGGLE_PUBLIC = 11
        const val SLOT_TOGGLE_TP = 15
        const val SLOT_CONFIRM = 11
        const val SLOT_CANCEL = 15

        fun pageCount(size: Int): Int = maxOf(1, (size + PAGE_SIZE - 1) / PAGE_SIZE)

        fun format(price: Double): String {
            // Same economy and currency format ShopChest uses for its holograms
            val economy = ShopChest.getInstance().economy
            if (economy != null) {
                try {
                    return economy.format(price)
                } catch (_: RuntimeException) {
                    // Fall back to a plain number if the economy plugin fails
                }
            }
            return String.format("%,.2f", price)
        }

        fun createButton(material: Material, name: String, vararg lore: String): ItemStack {
            val item = ItemStack(material)
            val meta = item.itemMeta ?: return item
            meta.setDisplayName(name)
            if (lore.isNotEmpty()) meta.lore = lore.toList()
            item.itemMeta = meta
            return item
        }

        /**
         * The shop location is the chest itself; teleporting there would put the
         * player inside the block. Prefer the block in front of the chest, facing it.
         */
        fun findStandingLocation(chest: Block): Location {
            val chestCenter = chest.location.add(0.5, 0.5, 0.5)

            val data = chest.blockData
            if (data is Directional) {
                val front = chest.getRelative(data.facing)
                if (front.isPassable && front.getRelative(BlockFace.UP).isPassable &&
                    !front.getRelative(BlockFace.DOWN).isPassable
                ) {
                    val target = front.location.add(0.5, 0.0, 0.5)
                    target.direction = chestCenter.toVector().subtract(target.clone().add(0.0, 1.62, 0.0).toVector())
                    return target
                }
            }

            // Fall back to standing on top of the chest (shops always have air above)
            return chest.location.add(0.5, 1.0, 0.5)
        }
    }
}
