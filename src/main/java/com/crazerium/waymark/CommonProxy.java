package com.crazerium.waymark;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;

import com.crazerium.waymark.item.ItemMissing;
import com.crazerium.waymark.item.ItemQuestBook;
import com.crazerium.waymark.network.WaymarkNetwork;
import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.server.ServerEvents;
import com.crazerium.waymark.server.ServerProgress;
import com.crazerium.waymark.server.ServerQuests;
import com.crazerium.waymark.server.WaymarkCommand;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.registry.GameRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Waymark.LOG.info("{} {} loading", Waymark.NAME, Tags.VERSION);
        ItemMissing.INSTANCE = new ItemMissing();
        GameRegistry.registerItem(ItemMissing.INSTANCE, "missing");
        ItemQuestBook.INSTANCE = new ItemQuestBook();
        GameRegistry.registerItem(ItemQuestBook.INSTANCE, "quest_book");
        BuiltinTypes.init();
        WaymarkNetwork.init();
        ServerQuests.init(event.getModConfigurationDirectory());
    }

    public void init(FMLInitializationEvent event) {
        ServerEvents events = new ServerEvents();
        FMLCommonHandler.instance()
            .bus()
            .register(events);
        MinecraftForge.EVENT_BUS.register(events);
        // A book and a compass: a guide for the way ahead.
        GameRegistry.addShapelessRecipe(new ItemStack(ItemQuestBook.INSTANCE), Items.book, Items.compass);
    }

    public void postInit(FMLPostInitializationEvent event) {}

    /** Opens the quest book; only does something on the client. */
    public void openQuestBook() {}

    public void serverStarting(FMLServerStartingEvent event) {
        ServerProgress.load(DimensionManager.getCurrentSaveRootDirectory());
        ServerQuests.load();
        event.registerServerCommand(new WaymarkCommand());
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        ServerProgress.save();
    }

    public void serverStopped(FMLServerStoppedEvent event) {
        ServerProgress.unload();
        ServerQuests.unload();
    }
}
