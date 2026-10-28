package net.azisaba.shopchestfinder.gui;

import de.epiceric.shopchest.ShopChest;
import net.azisaba.shopchestfinder.ShopChestFinder;
import net.azisaba.shopchestfinder.cache.ShopCache;
import net.azisaba.shopchestfinder.cache.ShopCache.FinderShop;
import net.azisaba.shopchestfinder.cache.ShopCache.ItemGroup;
import net.azisaba.shopchestfinder.config.ConfigManager;
import net.azisaba.shopchestfinder.util.TownResolver;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The finder's inventory menus and their click handling.
 * <p>
 * Menus are identified by their {@link InventoryHolder} instead of their
 * title, so renamed chests can't be mistaken for a menu, and each menu keeps
 * the exact state (page, full search keyword, slot contents) it was built
 * with. Clicks therefore always act on what the player actually saw.
 */
public class FinderMenu implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_SETTINGS = 51;
    private static final int SLOT_NEXT = 53;
    private static final int SLOT_TOGGLE_PUBLIC = 11;
    private static final int SLOT_TOGGLE_TP = 15;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_CANCEL = 15;

    private enum Type { ITEMS, SHOPS, SETTINGS, TP_CONFIRM }

    /** Holder carrying the state of an open menu. */
    private static final class Holder implements InventoryHolder {
        private final Type type;
        private final int page;
        private final String keyword;
        private final List<ItemGroup> groups = new ArrayList<>();
        private final List<FinderShop> shops = new ArrayList<>();
        // Where the "back" button of a shop list leads
        private int itemPage = 1;
        private String itemKeyword;
        private ItemGroup group;
        private Inventory inventory;

        private Holder(Type type, int page, String keyword) {
            this.type = type;
            this.page = page;
            this.keyword = keyword;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** Price statistics per single item of the shops a player can see for one product. */
    private static final class Stats {
        private final ItemGroup group;
        private final List<FinderShop> shops = new ArrayList<>();
        private double minBuy = Double.MAX_VALUE, maxBuy, sumBuy;
        private double minSell = Double.MAX_VALUE, maxSell, sumSell;
        private int countBuy, countSell;

        private Stats(ItemGroup group) {
            this.group = group;
        }
    }

    private final ShopChestFinder plugin;

    public FinderMenu(ShopChestFinder plugin) {
        this.plugin = plugin;
    }

    private ConfigManager settings() {
        return plugin.getConfigManager();
    }

    private boolean canSee(Player player, FinderShop shop) {
        return ShopChestFinder.isAdmin(player)
                || shop.vendor.equals(player.getUniqueId())
                || settings().isPublic(shop.vendor);
    }

    private boolean canTeleport(Player player, FinderShop shop) {
        return ShopChestFinder.isAdmin(player)
                || shop.vendor.equals(player.getUniqueId())
                || settings().isTpAllowed(shop.vendor);
    }

    private Stats computeStats(Player player, ItemGroup group) {
        Stats stats = new Stats(group);
        // Shops sell different amounts per trade, so compare prices per single item
        for (FinderShop shop : group.shops) {
            if (!canSee(player, shop)) continue;
            stats.shops.add(shop);
            double unitBuy = shop.unitBuyPrice();
            if (unitBuy > 0) {
                stats.minBuy = Math.min(stats.minBuy, unitBuy);
                stats.maxBuy = Math.max(stats.maxBuy, unitBuy);
                stats.sumBuy += unitBuy;
                stats.countBuy++;
            }
            double unitSell = shop.unitSellPrice();
            if (unitSell > 0) {
                stats.minSell = Math.min(stats.minSell, unitSell);
                stats.maxSell = Math.max(stats.maxSell, unitSell);
                stats.sumSell += unitSell;
                stats.countSell++;
            }
        }
        return stats.shops.isEmpty() ? null : stats;
    }

    private static String format(double price) {
        // Same economy and currency format ShopChest uses for its holograms
        Economy economy = ShopChest.getInstance().getEconomy();
        if (economy != null) {
            try {
                return economy.format(price);
            } catch (RuntimeException ignored) {
                // Fall back to a plain number if the economy plugin fails
            }
        }
        return String.format("%,.2f", price);
    }

    public void openItems(Player player, int page, String keyword) {
        // Color codes typed into the keyword must not prevent a match either
        String plainKeyword = keyword == null ? "" : ShopCache.stripColors(keyword).trim();
        String lowerKeyword = plainKeyword.isEmpty() ? null : plainKeyword.toLowerCase(Locale.ROOT);

        List<Stats> entries = new ArrayList<>();
        for (ItemGroup group : plugin.getShopCache().getGroups()) {
            if (lowerKeyword != null && !group.searchText.contains(lowerKeyword)) continue;
            Stats stats = computeStats(player, group);
            if (stats != null) entries.add(stats);
        }

        int totalPages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, totalPages));

        String title = lowerKeyword != null
                ? "検索:" + (plainKeyword.length() > 8 ? plainKeyword.substring(0, 8) + ".." : plainKeyword)
                : "アイテム一覧";
        Holder holder = new Holder(Type.ITEMS, page, lowerKeyword == null ? null : plainKeyword);
        Inventory inv = Bukkit.createInventory(holder, 54, title + " - ページ " + page + "/" + totalPages);
        holder.inventory = inv;

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());
        for (int i = start; i < end; i++) {
            Stats stats = entries.get(i);
            holder.groups.add(stats.group);
            inv.setItem(i - start, createItemIcon(stats));
        }

        if (page > 1) inv.setItem(SLOT_PREVIOUS, createButton(Material.ARROW, "§a前のページ"));
        inv.setItem(SLOT_INFO, createButton(Material.BOOK, "§c§l【注意】",
                "§7表示されている価格はあくまで目安です。",
                "§7実際の価格や在庫を保証するものではありません。",
                "§7一覧は定期的に更新されるため、最新でない場合があります。"));
        inv.setItem(SLOT_SETTINGS, createButton(Material.COMPARATOR, "§eショップ公開設定",
                "§7クリックして自分のショップの公開設定を開きます。"));
        if (page < totalPages) inv.setItem(SLOT_NEXT, createButton(Material.ARROW, "§a次のページ"));

        if (entries.isEmpty()) {
            inv.setItem(22, createButton(Material.BARRIER, "§cショップが見つかりませんでした"));
        }

        player.openInventory(inv);
    }

    private ItemStack createItemIcon(Stats stats) {
        ItemStack item = stats.group.icon.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<String>();
        lore.add("");
        if (stats.countBuy > 0) {
            lore.add("§e【販売価格 (プレイヤーが買う価格・1個あたり)】");
            lore.add("§7最大: §a" + format(stats.maxBuy));
            lore.add("§7最小: §a" + format(stats.minBuy));
            lore.add("§7平均: §a" + format(stats.sumBuy / stats.countBuy));
        }
        if (stats.countSell > 0) {
            lore.add("§e【買取価格 (プレイヤーが売る価格・1個あたり)】");
            lore.add("§7最大: §c" + format(stats.maxSell));
            lore.add("§7最小: §c" + format(stats.minSell));
            lore.add("§7平均: §c" + format(stats.sumSell / stats.countSell));
        }
        lore.add("");
        lore.add("§7ショップ数: §f" + stats.shops.size());
        lore.add("§aクリックして販売中のショップ一覧を見る");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void openShops(Player player, ItemGroup group, int page, int itemPage, String itemKeyword) {
        List<FinderShop> shops = new ArrayList<>();
        for (FinderShop shop : group.shops) {
            if (canSee(player, shop)) shops.add(shop);
        }
        // Cheapest offers per item first; shops that don't sell are listed last
        shops.sort(Comparator.comparingDouble((FinderShop s) -> s.buyPrice > 0 ? s.unitBuyPrice() : Double.MAX_VALUE)
                .thenComparingDouble(s -> -s.unitSellPrice()));

        int totalPages = Math.max(1, (shops.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, totalPages));

        Holder holder = new Holder(Type.SHOPS, page, null);
        holder.group = group;
        holder.itemPage = itemPage;
        holder.itemKeyword = itemKeyword;
        Inventory inv = Bukkit.createInventory(holder, 54, "ショップ一覧 - ページ " + page + "/" + totalPages);
        holder.inventory = inv;

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, shops.size());
        for (int i = start; i < end; i++) {
            FinderShop shop = shops.get(i);
            holder.shops.add(shop);
            inv.setItem(i - start, createShopIcon(player, group, shop));
        }

        if (page > 1) inv.setItem(SLOT_PREVIOUS, createButton(Material.ARROW, "§a前のページ"));
        inv.setItem(SLOT_INFO, createButton(Material.BARRIER, "§cアイテム一覧に戻る"));
        if (page < totalPages) inv.setItem(SLOT_NEXT, createButton(Material.ARROW, "§a次のページ"));

        player.openInventory(inv);
    }

    private ItemStack createShopIcon(Player player, ItemGroup group, FinderShop shop) {
        ItemStack item = group.icon.clone();
        item.setAmount(Math.max(1, Math.min(shop.amount, item.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(shop.admin ? "§6アドミンショップ" : "§e" + shop.vendorName + " のショップ");
        List<String> lore = new ArrayList<>();
        lore.add("§7個数: §f" + shop.amount + "個");
        lore.add("§7販売価格: " + (shop.buyPrice > 0
                ? "§a" + format(shop.buyPrice) + " §7(1個あたり §a" + format(shop.unitBuyPrice()) + "§7)"
                : "§8なし"));
        lore.add("§7買取価格: " + (shop.sellPrice > 0
                ? "§c" + format(shop.sellPrice) + " §7(1個あたり §c" + format(shop.unitSellPrice()) + "§7)"
                : "§8なし"));
        // Looked up on open so renamed or newly claimed towns show up immediately
        String town = TownResolver.getTownName(shop.world, shop.x, shop.y, shop.z);
        lore.add("§7町: " + (town != null ? "§b" + town : "§8なし"));
        lore.add("§7座標: §f" + shop.world + " " + shop.x + ", " + shop.y + ", " + shop.z);
        lore.add("");
        lore.add(canTeleport(player, shop) ? "§eクリックしてテレポート！" : "§7テレポート不可 (クリックで座標を表示)");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void openSettings(Player player) {
        Holder holder = new Holder(Type.SETTINGS, 1, null);
        Inventory inv = Bukkit.createInventory(holder, 27, "ショップ公開設定");
        holder.inventory = inv;

        boolean isPublic = settings().isPublic(player.getUniqueId());
        inv.setItem(SLOT_TOGGLE_PUBLIC, isPublic
                ? createButton(Material.LIME_DYE, "§a検索公開設定: 公開中",
                        "§7クリックして設定を切り替えます。",
                        "§a他のプレイヤーがあなたのショップを検索できるようになっています。")
                : createButton(Material.GRAY_DYE, "§c検索公開設定: 非公開",
                        "§7クリックして設定を切り替えます。",
                        "§c他のプレイヤーの検索結果にあなたのショップは表示されません。"));

        boolean tpAllowed = settings().isTpAllowed(player.getUniqueId());
        inv.setItem(SLOT_TOGGLE_TP, tpAllowed
                ? createButton(Material.ENDER_PEARL, "§aTP許可設定: 許可中",
                        "§7クリックして設定を切り替えます。",
                        "§a他のプレイヤーがGUIから直接あなたのショップに",
                        "§aテレポートできるようになっています。")
                : createButton(Material.BARRIER, "§cTP許可設定: 非許可",
                        "§7クリックして設定を切り替えます。",
                        "§c他のプレイヤーはあなたのショップへテレポートできません。",
                        "§c(徒歩で向かう必要があります)"));

        player.openInventory(inv);
    }

    private void openTpConfirm(Player player) {
        Holder holder = new Holder(Type.TP_CONFIRM, 1, null);
        Inventory inv = Bukkit.createInventory(holder, 27, "TP許可の確認");
        holder.inventory = inv;

        inv.setItem(SLOT_CONFIRM, createButton(Material.LIME_WOOL, "§a§l[OK] リスクを承知の上、許可する"));
        inv.setItem(13, createButton(Material.PAPER, "§e§l【警告】",
                "§c本当に許可しますか？",
                "§cこの設定は自己責任のため、",
                "§c問題が発生したときに運営は干渉できません。"));
        inv.setItem(SLOT_CANCEL, createButton(Material.RED_WOOL, "§c§l[CANCEL] 戻る"));

        player.openInventory(inv);
    }

    private static ItemStack createButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        Holder holder = (Holder) e.getInventory().getHolder();

        // Cancel every click (including shift clicks from the player's own
        // inventory) so nothing can be moved into or out of the menu
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        switch (holder.type) {
            case ITEMS:
                handleItemsClick(player, holder, slot);
                break;
            case SHOPS:
                handleShopsClick(player, holder, slot);
                break;
            case SETTINGS:
                handleSettingsClick(player, slot);
                break;
            case TP_CONFIRM:
                handleTpConfirmClick(player, slot);
                break;
        }
    }

    private void handleItemsClick(Player player, Holder holder, int slot) {
        if (slot < PAGE_SIZE) {
            if (slot < holder.groups.size()) {
                openShops(player, holder.groups.get(slot), 1, holder.page, holder.keyword);
            }
        } else if (slot == SLOT_PREVIOUS && holder.page > 1) {
            openItems(player, holder.page - 1, holder.keyword);
        } else if (slot == SLOT_NEXT) {
            openItems(player, holder.page + 1, holder.keyword);
        } else if (slot == SLOT_SETTINGS) {
            openSettings(player);
        }
    }

    private void handleShopsClick(Player player, Holder holder, int slot) {
        if (slot < PAGE_SIZE) {
            if (slot < holder.shops.size()) {
                teleport(player, holder.shops.get(slot));
            }
        } else if (slot == SLOT_PREVIOUS && holder.page > 1) {
            openShops(player, holder.group, holder.page - 1, holder.itemPage, holder.itemKeyword);
        } else if (slot == SLOT_NEXT) {
            openShops(player, holder.group, holder.page + 1, holder.itemPage, holder.itemKeyword);
        } else if (slot == SLOT_INFO) {
            openItems(player, holder.itemPage, holder.itemKeyword);
        }
    }

    private void handleSettingsClick(Player player, int slot) {
        UUID uuid = player.getUniqueId();

        if (slot == SLOT_TOGGLE_PUBLIC) {
            boolean isPublic = !settings().isPublic(uuid);
            settings().setPublic(uuid, isPublic);
            player.sendMessage(isPublic ? "§aショップを検索に公開しました。" : "§aショップを検索から非公開にしました。");
            openSettings(player);
        } else if (slot == SLOT_TOGGLE_TP) {
            if (!settings().isTpAllowed(uuid)) {
                openTpConfirm(player);
            } else {
                settings().setTpAllowed(uuid, false);
                player.sendMessage("§aショップへのTPを非許可にしました。");
                openSettings(player);
            }
        }
    }

    private void handleTpConfirmClick(Player player, int slot) {
        if (slot == SLOT_CONFIRM) {
            settings().setTpAllowed(player.getUniqueId(), true);
            player.sendMessage("§aショップへのTPを許可しました！");
            openSettings(player);
        } else if (slot == SLOT_CANCEL) {
            openSettings(player);
        }
    }

    private void teleport(Player player, FinderShop shop) {
        // Check the current setting, not the one from when the list was built
        boolean allowed = canTeleport(player, shop);
        World world = Bukkit.getWorld(shop.world);

        player.closeInventory();
        if (!allowed || world == null) {
            if (!allowed) {
                player.sendMessage("§cこのショップはテレポートを許可していません。座標を元に徒歩で向かってください。");
            }
            player.sendMessage("§eショップの座標: §b" + shop.world + " §e/ §b" + shop.x + " §e/ §b" + shop.y + " §e/ §b" + shop.z);
            return;
        }

        // The shop's chunk may have been unloaded since the list was built
        world.getChunkAtAsync(shop.x >> 4, shop.z >> 4).thenAccept(chunk -> {
            if (!player.isOnline()) return;
            Location target = findStandingLocation(world.getBlockAt(shop.x, shop.y, shop.z));
            player.teleportAsync(target).thenAccept(success -> {
                if (success) player.sendMessage("§aショップへテレポートしました！");
            });
        });
    }

    /**
     * The shop location is the chest itself; teleporting there would put the
     * player inside the block. Prefer the block in front of the chest, facing it.
     */
    private static Location findStandingLocation(Block chest) {
        Location chestCenter = chest.getLocation().add(0.5, 0.5, 0.5);

        BlockData data = chest.getBlockData();
        if (data instanceof Directional) {
            BlockFace facing = ((Directional) data).getFacing();
            Block front = chest.getRelative(facing);
            if (front.isPassable() && front.getRelative(BlockFace.UP).isPassable()
                    && !front.getRelative(BlockFace.DOWN).isPassable()) {
                Location target = front.getLocation().add(0.5, 0, 0.5);
                target.setDirection(chestCenter.toVector().subtract(target.clone().add(0, 1.62, 0).toVector()));
                return target;
            }
        }

        // Fall back to standing on top of the chest (shops always have air above)
        return chest.getLocation().add(0.5, 1, 0.5);
    }
}
