package com.crazerium.waymark;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.server.ServerQuests;
import com.crazerium.waymark.server.WaymarkCommand;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Waymark.LOG.info("{} {} loading", Waymark.NAME, Tags.VERSION);
        BuiltinTypes.init();
        ServerQuests.init(event.getModConfigurationDirectory());
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        ServerQuests.load();
        event.registerServerCommand(new WaymarkCommand());
    }

    public void serverStopped(FMLServerStoppedEvent event) {
        ServerQuests.unload();
    }
}
