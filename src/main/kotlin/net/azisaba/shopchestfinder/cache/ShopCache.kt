package net.azisaba.shopchestfinder.cache

import de.epiceric.shopchest.ShopChest
import de.epiceric.shopchest.shop.Shop
import net.azisaba.shopchestfinder.ShopChestFinder
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Periodically rebuilt snapshot of the loaded shops, grouped by product.
 *
 * Visibility (public/private vendors) is applied when a menu is opened, so
 * changing a setting takes effect immediately without rebuilding the cache.
 */
class ShopCache(private val plugin: ShopChestFinder) : Listener {

    /** Immutable copy of the shop data the finder needs. */
    data class FinderShop(
        val vendor: UUID,
        val vendorName: String,
        val admin: Boolean,
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val buyPrice: Double,
        val sellPrice: Double,
        val amount: Int,
    ) {
        /** Buy price per single item, or 0 if buying is disabled. */
        fun unitBuyPrice(): Double = if (buyPrice > 0) buyPrice / amount else 0.0

        /** Sell price per single item, or 0 if selling is disabled. */
        fun unitSellPrice(): Double = if (sellPrice > 0) sellPrice / amount else 0.0
    }

    /** All shops selling or buying the same (stripped) product. */
    class ItemGroup(val icon: ItemStack, val searchText: String, val shops: List<FinderShop>)

    /** Shop data copied on the main thread, before grouping in the background. */
    private class RawShop(
        val product: ItemStack,
        val vendor: OfflinePlayer,
        /** Null if it still has to be resolved off the main thread. */
        val vendorName: String?,
        val admin: Boolean,
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
        val buyPrice: Double,
        val sellPrice: Double,
        val amount: Int,
    ) {
        fun toFinderShop(name: String) =
            FinderShop(vendor.uniqueId, name, admin, world, x, y, z, buyPrice, sellPrice, amount)
    }

    private val refreshing = AtomicBoolean()
    private var task: BukkitTask? = null

    @Volatile
    var groups: List<ItemGroup> = emptyList()
        private set

    /**
     * Vendor UUID -> last known name. OfflinePlayer#getName() of a player who
     * is neither online nor in usercache.json reads (and data-fixes) the whole
     * playerdata file, so every vendor is looked up at most once.
     */
    private val vendorNames = ConcurrentHashMap<UUID, String>()

    fun start(intervalTicks: Long) {
        // Runs on the main thread: ShopChest's shop map is not meant to be read asynchronously
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { refresh() }, 20L, intervalTicks)
        Bukkit.getPluginManager().registerEvents(this, plugin)
    }

    fun stop() {
        task?.cancel()
        task = null
    }

    /**
     * Copies the loaded shops on the main thread and groups them in the
     * background. Overlapping refreshes are skipped.
     */
    fun refresh() {
        if (!refreshing.compareAndSet(false, true)) return

        val rawShops = try {
            snapshot()
        } catch (e: RuntimeException) {
            refreshing.set(false)
            throw e
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                groups = buildGroups(rawShops)
            } catch (e: RuntimeException) {
                plugin.logger.severe("Failed to build shop list: $e")
            } finally {
                refreshing.set(false)
            }
        })
    }

    private fun snapshot(): List<RawShop> {
        val ignoredPrice = plugin.ignoredPriceThreshold
        // Double chest shops are registered under both chest locations
        val seenLocations = HashSet<Location>()
        val result = ArrayList<RawShop>()

        for (shop in ShopChest.getInstance().shopUtils.shops) {
            val product: ItemStack? = shop.product.itemStack
            if (product == null || plugin.isExcluded(product)) continue

            val loc = shop.location
            val world = loc.world ?: continue
            if (!seenLocations.add(loc)) continue

            // Display shops use a token price (e.g. 1) and would distort the
            // statistics; treat such prices as disabled
            val buyPrice = if (shop.buyPrice > ignoredPrice) shop.buyPrice else 0.0
            val sellPrice = if (shop.sellPrice > ignoredPrice) shop.sellPrice else 0.0
            val amount = shop.product.amount
            if ((buyPrice <= 0 && sellPrice <= 0) || amount <= 0) continue

            val vendor = shop.vendor
            result += RawShop(
                product.clone(), vendor, cachedVendorName(vendor.uniqueId),
                shop.shopType == Shop.ShopType.ADMIN, world.name,
                loc.blockX, loc.blockY, loc.blockZ, buyPrice, sellPrice, amount,
            )
        }
        return result
    }

    /**
     * Name of the vendor without touching the playerdata file: online player,
     * our own cache, then the server's user cache. Null if none of them knows it.
     */
    private fun cachedVendorName(uuid: UUID): String? {
        Bukkit.getPlayer(uuid)?.let { online ->
            vendorNames[uuid] = online.name
            return online.name
        }
        vendorNames[uuid]?.let { return it }

        val profile = Bukkit.createProfile(uuid)
        val name = if (profile.completeFromCache()) profile.name else null
        if (name != null) vendorNames[uuid] = name
        return name
    }

    /**
     * Last resort, run off the main thread: reads the vendor's playerdata
     * file once and remembers the result (the UUID if it has no name).
     */
    private fun resolveVendorName(vendor: OfflinePlayer): String =
        vendorNames.computeIfAbsent(vendor.uniqueId) { uuid ->
            val name = try {
                vendor.name
            } catch (e: RuntimeException) {
                plugin.logger.warning("Failed to look up the name of $uuid: $e")
                null
            }
            name ?: uuid.toString()
        }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        // Keeps renamed vendors up to date
        vendorNames[event.player.uniqueId] = event.player.name
    }

    private fun buildGroups(rawShops: List<RawShop>): List<ItemGroup> {
        // Stripped products all have an amount of 1, so ItemStack#equals/hashCode
        // match isSimilar() and grouping is a hash lookup instead of a linear scan
        val byProduct = LinkedHashMap<ItemStack, MutableList<FinderShop>>()
        for (raw in rawShops) {
            val key = plugin.stripUniqueData(raw.product)
            val name = raw.vendorName ?: resolveVendorName(raw.vendor)
            byProduct.getOrPut(key) { ArrayList() } += raw.toFinderShop(name)
        }

        return byProduct
            .map { (icon, shops) -> ItemGroup(icon, buildSearchText(icon), shops.toList()) }
            .sortedBy { it.icon.type.name }
    }

    companion object {
        // "&a" style codes that were stored literally, including "&#rrggbb" hex colors
        private val AMPERSAND_CODES = Regex("(?i)&(#[0-9a-f]{6}|[0-9a-fk-orx])")

        /** Text matched by `/findshop search`: material name and custom name. */
        @Suppress("DEPRECATION")
        private fun buildSearchText(item: ItemStack): String {
            val material = item.type.name.lowercase(Locale.ROOT)
            val sb = StringBuilder()
            sb.append(material).append('\n').append(material.replace('_', ' '))

            if (item.hasItemMeta()) {
                val meta = item.itemMeta
                if (meta.hasDisplayName()) {
                    sb.append('\n').append(stripColors(meta.displayName))
                }
            }
            return sb.toString().lowercase(Locale.ROOT)
        }

        /**
         * Removes section sign and ampersand color codes, so a colored (or
         * gradient, where every character has its own code) name still contains
         * the plain text players search for.
         */
        @Suppress("DEPRECATION")
        fun stripColors(text: String): String {
            val stripped = ChatColor.stripColor(text) ?: return ""
            return AMPERSAND_CODES.replace(stripped, "")
        }
    }
}
