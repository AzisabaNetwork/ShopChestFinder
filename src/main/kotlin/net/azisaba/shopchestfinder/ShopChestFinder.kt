package net.azisaba.shopchestfinder

import net.azisaba.shopchestfinder.cache.ShopCache
import net.azisaba.shopchestfinder.command.FindShopCommand
import net.azisaba.shopchestfinder.config.ConfigManager
import net.azisaba.shopchestfinder.gui.FinderMenu
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.EnumSet

class ShopChestFinder : JavaPlugin() {

    lateinit var configManager: ConfigManager
        private set
    lateinit var shopCache: ShopCache
        private set
    lateinit var menu: FinderMenu
        private set

    // Replaced as a whole on reload; read from the async cache rebuild
    @Volatile
    private var groupMaterials: Set<Material> = EnumSet.noneOf(Material::class.java)

    @Volatile
    private var excludeMaterials: Set<Material> = EnumSet.noneOf(Material::class.java)

    @Volatile
    private var excludeVanillaMaterials: Set<Material> = EnumSet.noneOf(Material::class.java)

    /** Prices at or below this value are treated as disabled (display shops). */
    @Volatile
    var ignoredPriceThreshold: Double = 0.0
        private set

    override fun onEnable() {
        instance = this
        saveDefaultConfig()
        loadSettings()

        configManager = ConfigManager(this)
        shopCache = ShopCache(this)
        menu = FinderMenu(this)

        getCommand("findshop")?.let { command ->
            val executor = FindShopCommand(this)
            command.setExecutor(executor)
            command.tabCompleter = executor
        }
        server.pluginManager.registerEvents(menu, this)

        shopCache.start(config.getLong("refresh-interval", 60).coerceAtLeast(5) * 20L)
        logger.info("ShopChestFinder has been enabled.")
    }

    override fun onDisable() {
        if (::configManager.isInitialized) {
            configManager.saveNow()
        }
        logger.info("ShopChestFinder has been disabled.")
    }

    /** Reads the material lists and the price threshold from config.yml. */
    fun loadSettings() {
        ignoredPriceThreshold = config.getDouble("ignore-price-at-or-below", 1.0)
        groupMaterials = readMaterials("group-materials")
        excludeMaterials = readMaterials("exclude-materials")
        excludeVanillaMaterials = readMaterials("exclude-vanilla-materials")
    }

    private fun readMaterials(list: String): Set<Material> {
        val result = EnumSet.noneOf(Material::class.java)
        for (name in config.getStringList(list)) {
            val material = Material.matchMaterial(name)
            if (material == null) {
                logger.warning("Invalid material in $list: $name")
            } else {
                result.add(material)
            }
        }
        return result
    }

    /** @return Whether the product should be hidden from the finder */
    fun isExcluded(item: ItemStack?): Boolean {
        if (item == null || item.type.isAir) return true
        val type = item.type

        if (type in excludeMaterials) return true

        if (type in excludeVanillaMaterials) {
            if (!item.hasItemMeta()) return true
            val meta = item.itemMeta
            return !meta.hasDisplayName() && !meta.hasLore()
        }
        return false
    }

    /**
     * Removes data that should not split otherwise equal products into
     * separate entries (shulker box contents, map IDs, or everything for
     * materials listed in `group-materials`).
     *
     * @return A copy of the item with an amount of 1
     */
    @Suppress("DEPRECATION")
    fun stripUniqueData(item: ItemStack): ItemStack {
        if (item.type in groupMaterials) {
            return ItemStack(item.type)
        }

        val clone = item.clone()
        clone.amount = 1

        if (clone.hasItemMeta()) {
            val bsm = clone.itemMeta
            if (bsm is BlockStateMeta && bsm.hasBlockState()) {
                val state = bsm.blockState
                if (state is ShulkerBox) {
                    state.inventory.clear()
                    bsm.blockState = state
                    clone.itemMeta = bsm
                }
            }
        }

        if (clone.type == Material.FILLED_MAP) {
            // Keep only what players can see: name, lore and enchantments
            val plainMap = ItemStack(Material.FILLED_MAP)
            val plainMeta = plainMap.itemMeta
            if (clone.hasItemMeta() && plainMeta != null) {
                val oldMeta = clone.itemMeta
                if (oldMeta.hasDisplayName()) plainMeta.setDisplayName(oldMeta.displayName)
                if (oldMeta.hasLore()) plainMeta.lore = oldMeta.lore
                for ((enchantment, level) in oldMeta.enchants) {
                    plainMeta.addEnchant(enchantment, level, true)
                }
                plainMap.itemMeta = plainMeta
            }
            return plainMap
        }

        return clone
    }

    companion object {
        @JvmField
        val MATERIAL_LISTS: List<String> = listOf("group-materials", "exclude-materials", "exclude-vanilla-materials")

        @JvmStatic
        lateinit var instance: ShopChestFinder
            private set

        @JvmStatic
        fun isAdmin(player: Player): Boolean = player.hasPermission("shopchestfinder.admin")
    }
}
