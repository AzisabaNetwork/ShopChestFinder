package net.azisaba.shopchestfinder.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Method;

/**
 * Resolves the Towny town a location belongs to.
 * <p>
 * Accessed via reflection so the plugin neither requires Towny nor depends
 * on one specific Towny version: {@code TownyAPI#getTownName(Location)} is
 * used on newer versions and {@code TownyUniverse#getTownName(Location)} on
 * older ones.
 */
public final class TownResolver {
    private static boolean initialized;
    private static Object apiInstance;
    private static Method getTownName;

    private TownResolver() {}

    private static void init() {
        initialized = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("Towny")) return;

        try {
            Class<?> api = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            apiInstance = api.getMethod("getInstance").invoke(null);
            getTownName = api.getMethod("getTownName", Location.class);
            return;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Older Towny without TownyAPI
        }

        try {
            Class<?> universe = Class.forName("com.palmergames.bukkit.towny.object.TownyUniverse");
            getTownName = universe.getMethod("getTownName", Location.class);
            apiInstance = null; // static method
        } catch (ReflectiveOperationException | LinkageError e) {
            Bukkit.getLogger().warning("[ShopChestFinder] Unsupported Towny version, town names are not shown");
            getTownName = null;
        }
    }

    /**
     * Must be called on the main thread.
     *
     * @return The name of the town at the location, or {@code null} for
     *         wilderness or if Towny is unavailable
     */
    public static String getTownName(String worldName, int x, int y, int z) {
        if (!initialized) init();
        if (getTownName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        try {
            Object name = getTownName.invoke(apiInstance, new Location(world, x, y, z));
            return name == null ? null : name.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
