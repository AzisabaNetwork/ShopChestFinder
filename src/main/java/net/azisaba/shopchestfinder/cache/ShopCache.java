package net.azisaba.shopchestfinder.cache;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.shop.Shop;
import net.azisaba.shopchestfinder.ShopChestFinder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Periodically rebuilt snapshot of the loaded shops, grouped by product.
 * <p>
 * Visibility (public/private vendors) is applied when a menu is opened, so
 * changing a setting takes effect immediately without rebuilding the cache.
 */
public class ShopCache {

    // "&a" style codes that were stored literally, including "&#rrggbb" hex colors
    private static final Pattern AMPERSAND_CODES = Pattern.compile("(?i)&(#[0-9a-f]{6}|[0-9a-fk-orx])");

    /** Immutable copy of the shop data the finder needs. */
    public static final class FinderShop {
        public final UUID vendor;
        public final String vendorName;
        public final boolean admin;
        public final String world;
        public final int x, y, z;
        public final double buyPrice, sellPrice;
        public final int amount;

        FinderShop(UUID vendor, String vendorName, boolean admin, String world, int x, int y, int z,
                   double buyPrice, double sellPrice, int amount) {
            this.vendor = vendor;
            this.vendorName = vendorName;
            this.admin = admin;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.amount = amount;
        }

        /** Buy price per single item, or 0 if buying is disabled. */
        public double unitBuyPrice() {
            return buyPrice > 0 ? buyPrice / amount : 0;
        }

        /** Sell price per single item, or 0 if selling is disabled. */
        public double unitSellPrice() {
            return sellPrice > 0 ? sellPrice / amount : 0;
        }
    }

    /** All shops selling or buying the same (stripped) product. */
    public static final class ItemGroup {
        public final ItemStack icon;
        public final String searchText;
        public final List<FinderShop> shops;

        ItemGroup(ItemStack icon, String searchText, List<FinderShop> shops) {
            this.icon = icon;
            this.searchText = searchText;
            this.shops = shops;
        }
    }

    /** Shop data copied on the main thread, before grouping in the background. */
    private static final class RawShop {
        final ItemStack product;
        final FinderShop shop;

        RawShop(ItemStack product, FinderShop shop) {
            this.product = product;
            this.shop = shop;
        }
    }

    private final ShopChestFinder plugin;
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private volatile List<ItemGroup> groups = Collections.emptyList();
    private BukkitTask task;

    public ShopCache(ShopChestFinder plugin) {
        this.plugin = plugin;
    }

    public void start(long intervalTicks) {
        // Runs on the main thread: ShopChest's shop map is not meant to be read asynchronously
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20L, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public List<ItemGroup> getGroups() {
        return groups;
    }

    /**
     * Copies the loaded shops on the main thread and groups them in the
     * background. Overlapping refreshes are skipped.
     */
    public void refresh() {
        if (!refreshing.compareAndSet(false, true)) return;

        final List<RawShop> rawShops;
        try {
            rawShops = snapshot();
        } catch (RuntimeException e) {
            refreshing.set(false);
            throw e;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                groups = buildGroups(rawShops);
            } catch (RuntimeException e) {
                plugin.getLogger().severe("Failed to build shop list: " + e);
            } finally {
                refreshing.set(false);
            }
        });
    }

    private List<RawShop> snapshot() {
        double ignoredPrice = plugin.getIgnoredPriceThreshold();
        // Double chest shops are registered under both chest locations
        Set<Location> seenLocations = new HashSet<>();
        List<RawShop> result = new ArrayList<>();

        for (Shop shop : ShopChest.getInstance().getShopUtils().getShops()) {
            ItemStack product = shop.getProduct().getItemStack();
            if (product == null || plugin.isExcluded(product)) continue;

            Location loc = shop.getLocation();
            if (loc.getWorld() == null || !seenLocations.add(loc)) continue;

            // Display shops use a token price (e.g. 1) and would distort the
            // statistics; treat such prices as disabled
            double buyPrice = shop.getBuyPrice() > ignoredPrice ? shop.getBuyPrice() : 0;
            double sellPrice = shop.getSellPrice() > ignoredPrice ? shop.getSellPrice() : 0;
            int amount = shop.getProduct().getAmount();
            if ((buyPrice <= 0 && sellPrice <= 0) || amount <= 0) continue;

            String vendorName = shop.getVendor().getName();
            FinderShop entry = new FinderShop(shop.getVendor().getUniqueId(),
                    vendorName == null ? shop.getVendor().getUniqueId().toString() : vendorName,
                    shop.getShopType() == Shop.ShopType.ADMIN, loc.getWorld().getName(),
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), buyPrice, sellPrice, amount);
            result.add(new RawShop(product.clone(), entry));
        }
        return result;
    }

    private List<ItemGroup> buildGroups(List<RawShop> rawShops) {
        // Stripped products all have an amount of 1, so ItemStack#equals/hashCode
        // match isSimilar() and grouping is a hash lookup instead of a linear scan
        Map<ItemStack, List<FinderShop>> byProduct = new LinkedHashMap<>();
        for (RawShop raw : rawShops) {
            ItemStack key = plugin.stripUniqueData(raw.product);
            List<FinderShop> list = byProduct.get(key);
            if (list == null) {
                list = new ArrayList<>();
                byProduct.put(key, list);
            }
            list.add(raw.shop);
        }

        List<ItemGroup> result = new ArrayList<>(byProduct.size());
        for (Map.Entry<ItemStack, List<FinderShop>> e : byProduct.entrySet()) {
            result.add(new ItemGroup(e.getKey(), buildSearchText(e.getKey()),
                    Collections.unmodifiableList(e.getValue())));
        }
        result.sort(Comparator.comparing(g -> g.icon.getType().name()));
        return Collections.unmodifiableList(result);
    }

    /**
     * Text matched by {@code /findshop search}: material name and custom name.
     */
    private static String buildSearchText(ItemStack item) {
        StringBuilder sb = new StringBuilder();
        String material = item.getType().name().toLowerCase(Locale.ROOT);
        sb.append(material).append('\n').append(material.replace('_', ' '));

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                sb.append('\n').append(stripColors(meta.getDisplayName()));
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Removes section sign and ampersand color codes, so a colored (or
     * gradient, where every character has its own code) name still contains
     * the plain text players search for.
     */
    public static String stripColors(String text) {
        String stripped = ChatColor.stripColor(text);
        return stripped == null ? "" : AMPERSAND_CODES.matcher(stripped).replaceAll("");
    }
}
