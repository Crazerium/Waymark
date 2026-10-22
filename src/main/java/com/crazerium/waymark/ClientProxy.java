package com.crazerium.waymark;

import net.minecraft.client.Minecraft;

import com.crazerium.waymark.client.ClientEvents;
import com.crazerium.waymark.client.ClientSettings;
import com.crazerium.waymark.client.Toasts;
import com.crazerium.waymark.client.gui.QuestBookScreen;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void openQuestBook() {
        Minecraft.getMinecraft()
            .displayGuiScreen(new QuestBookScreen());
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ClientSettings.load(event.getModConfigurationDirectory());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientEvents.registerKeys();
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientEvents());
        FMLCommonHandler.instance()
            .bus()
            .register(new Toasts());
    }
}
