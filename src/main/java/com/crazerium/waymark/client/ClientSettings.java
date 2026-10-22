package com.crazerium.waymark.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Personal client options, kept in config/waymark-client.cfg (separate from the server's quests). */
@SideOnly(Side.CLIENT)
public final class ClientSettings {

    private static final String BOOK = "book";
    private static final String PIN_CHAPTERS = "pinChapterList";

    private static Configuration config;
    private static boolean pinChapters;

    private ClientSettings() {}

    public static void load(File configDir) {
        config = new Configuration(new File(configDir, "waymark-client.cfg"));
        pinChapters = config.getBoolean(
            PIN_CHAPTERS,
            BOOK,
            false,
            "Keep the chapter list open next to the quest map. When false it folds into a strip of icons and opens on hover.");
        if (config.hasChanged()) config.save();
    }

    public static boolean isChapterListPinned() {
        return pinChapters;
    }

    public static void setChapterListPinned(boolean pinned) {
        pinChapters = pinned;
        if (config == null) return;
        config.get(BOOK, PIN_CHAPTERS, false)
            .set(pinned);
        config.save();
    }
}
