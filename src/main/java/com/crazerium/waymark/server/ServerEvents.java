package com.crazerium.waymark.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ServerEvents {

    private static final int DETECT_INTERVAL = 20;

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) ServerProgress.syncPlayer((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        if (event.player.ticksExisted % DETECT_INTERVAL == 0) ServerProgress.detect((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.player instanceof EntityPlayerMP)
            ServerProgress.onCrafted((EntityPlayerMP) event.player, event.crafting, false);
    }

    @SubscribeEvent
    public void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.player instanceof EntityPlayerMP)
            ServerProgress.onCrafted((EntityPlayerMP) event.player, event.smelting, true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.source.getEntity() instanceof EntityPlayerMP)
            ServerProgress.onKill((EntityPlayerMP) event.source.getEntity(), event.entityLiving);
    }

    @SubscribeEvent
    public void onWorldSave(WorldEvent.Save event) {
        if (!event.world.isRemote && event.world.provider.dimensionId == 0) ServerProgress.save();
    }
}
