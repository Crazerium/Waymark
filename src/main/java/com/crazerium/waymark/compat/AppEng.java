package com.crazerium.waymark.compat;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.Fluid;

import com.crazerium.waymark.Waymark;

import cpw.mods.fml.common.Loader;

/**
 * Applied Energistics 2 support: a team's bound ME network counts for its item and fluid tasks. Every call checks the
 * network's world and chunk are loaded and safely does nothing when AE2 is missing.
 */
public final class AppEng {

    private static Boolean loaded;

    private AppEng() {}

    public static boolean available() {
        if (loaded == null) loaded = Loader.isModLoaded("appliedenergistics2");
        return loaded;
    }

    /** True if the block at this position is part of an ME network. */
    public static boolean isNetworkBlock(World world, int x, int y, int z) {
        if (!available()) return false;
        try {
            return AppEngAccess.isNetworkBlock(world, x, y, z);
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    /** The loaded world of a bound device, or null if its world or chunk is not loaded right now. */
    private static World world(int[] link) {
        World world = DimensionManager.getWorld(link[0]);
        return world != null && world.blockExists(link[1], link[2], link[3]) ? world : null;
    }

    /** See {@link AppEngAccess#forEachStored}; false when the network cannot be reached. */
    public static boolean forEachStored(int[] link, BiConsumer<ItemStack, Long> each) {
        if (!available()) return false;
        World world = world(link);
        if (world == null) return false;
        try {
            return AppEngAccess.forEachStored(world, link[1], link[2], link[3], each);
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    /** See {@link AppEngAccess#extract}; 0 when the network cannot be reached. */
    public static long extract(int[] link, EntityPlayerMP player, Predicate<ItemStack> matches, long amount) {
        if (!available() || amount <= 0) return 0;
        World world = world(link);
        if (world == null) return 0;
        try {
            return AppEngAccess.extract(world, link[1], link[2], link[3], player, matches, amount);
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return 0;
        }
    }

    /** See {@link AppEngAccess#forEachFluid}; false when the network cannot be reached. */
    public static boolean forEachFluid(int[] link, BiConsumer<Fluid, Long> each) {
        if (!available()) return false;
        World world = world(link);
        if (world == null) return false;
        try {
            return AppEngAccess.forEachFluid(world, link[1], link[2], link[3], each);
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    /** See {@link AppEngAccess#extractFluid}; 0 when the network cannot be reached. */
    public static long extractFluid(int[] link, EntityPlayerMP player, Fluid fluid, long amount) {
        if (!available() || fluid == null || amount <= 0) return 0;
        World world = world(link);
        if (world == null) return 0;
        try {
            return AppEngAccess.extractFluid(world, link[1], link[2], link[3], player, fluid, amount);
        } catch (LinkageError | RuntimeException e) {
            fail(e);
            return 0;
        }
    }

    private static void fail(Throwable e) {
        Waymark.LOG.error("Applied Energistics 2 integration failed, turning it off", e);
        loaded = false;
    }
}
