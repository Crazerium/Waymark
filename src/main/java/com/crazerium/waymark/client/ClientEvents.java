package com.crazerium.waymark.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.crazerium.waymark.client.gui.QuestBookScreen;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientEvents {

    public static final KeyBinding OPEN_BOOK = new KeyBinding(
        "key.waymark.open",
        Keyboard.KEY_L,
        "key.categories.waymark");

    public static void registerKeys() {
        ClientRegistry.registerKeyBinding(OPEN_BOOK);
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (OPEN_BOOK.isPressed() && mc.currentScreen == null) mc.displayGuiScreen(new QuestBookScreen());
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientQuests.clear();
        Toasts.clear();
        ClientImages.clear();
    }
}
