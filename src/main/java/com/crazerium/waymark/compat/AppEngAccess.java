package com.crazerium.waymark.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.PlayerSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;

/** Calls into Applied Energistics 2. Only loaded when AE2 is installed, so nothing else may reference AE2 classes. */
final class AppEngAccess {

    private AppEngAccess() {}

    private static IGrid grid(TileEntity tile) {
        if (!(tile instanceof IGridHost)) return null;
        IGridNode node = ((IGridHost) tile).getGridNode(ForgeDirection.UNKNOWN);
        return node != null ? node.getGrid() : null;
    }

    /** Any AE2 network block. Grid nodes only exist on the server, so the client can only check the tile type. */
    static boolean isNetworkBlock(World world, int x, int y, int z) {
        return world.getTileEntity(x, y, z) instanceof IGridHost;
    }

    private static IMEMonitor<IAEItemStack> items(TileEntity tile) {
        IGrid grid = grid(tile);
        if (grid == null) return null;
        IStorageGrid storage = grid.getCache(IStorageGrid.class);
        return storage != null ? storage.getItemInventory() : null;
    }

    /** Calls {@code each} with every stored item (as a stack of one) and how many are stored; false if unreachable. */
    static boolean forEachStored(World world, int x, int y, int z, BiConsumer<ItemStack, Long> each) {
        IMEMonitor<IAEItemStack> monitor = items(world.getTileEntity(x, y, z));
        if (monitor == null) return false;
        for (IAEItemStack stored : monitor.getStorageList()) {
            if (stored == null || stored.getStackSize() <= 0) continue;
            ItemStack stack = stored.getItemStack();
            if (stack == null) continue;
            stack.stackSize = 1;
            each.accept(stack, stored.getStackSize());
        }
        return true;
    }

    /** Takes up to {@code amount} matching items out of the network; returns how many were taken. */
    static long extract(World world, int x, int y, int z, EntityPlayerMP player, Predicate<ItemStack> matches,
        long amount) {
        TileEntity tile = world.getTileEntity(x, y, z);
        IMEMonitor<IAEItemStack> monitor = items(tile);
        if (monitor == null) return 0;
        // Collect first: extracting while walking the storage list would change it under us.
        List<IAEItemStack> candidates = new ArrayList<>();
        for (IAEItemStack stored : monitor.getStorageList()) {
            if (stored == null || stored.getStackSize() <= 0) continue;
            ItemStack stack = stored.getItemStack();
            if (stack != null && matches.test(stack)) candidates.add(stored.copy());
        }
        PlayerSource source = new PlayerSource(player, tile instanceof IActionHost ? (IActionHost) tile : null);
        long taken = 0;
        for (IAEItemStack candidate : candidates) {
            if (taken >= amount) break;
            IAEItemStack request = candidate.copy();
            request.setStackSize(Math.min(amount - taken, candidate.getStackSize()));
            IAEItemStack got = monitor.extractItems(request, Actionable.MODULATE, source);
            if (got != null) taken += got.getStackSize();
        }
        return taken;
    }
}
