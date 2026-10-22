package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A small right-click menu. Dangerous items need a second click, so nothing is deleted by accident. */
@SideOnly(Side.CLIENT)
class ContextMenu {

    private static final int ITEM_H = 12;

    private static final class Item {

        final String label;
        final String confirmLabel;
        final Runnable action;
        boolean armed;

        Item(String label, String confirmLabel, Runnable action) {
            this.label = label;
            this.confirmLabel = confirmLabel;
            this.action = action;
        }
    }

    private final int x, y;
    private final List<Item> items = new ArrayList<>();
    private int w;

    ContextMenu(int x, int y) {
        this.x = x;
        this.y = y;
    }

    void add(String label, Runnable action) {
        items.add(new Item(label, null, action));
    }

    void addDangerous(String label, String confirmLabel, Runnable action) {
        items.add(new Item(label, confirmLabel, action));
    }

    void draw(FontRenderer font, int mx, int my) {
        w = 0;
        for (Item item : items) {
            w = Math.max(w, font.getStringWidth(item.label));
            if (item.confirmLabel != null) w = Math.max(w, font.getStringWidth(item.confirmLabel));
        }
        w += 12;
        GuiUtil.frame(x, y, w, items.size() * ITEM_H + 4, 0xF0101018, 0xFF8090B0);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int iy = y + 2 + i * ITEM_H;
            if (GuiUtil.inside(mx, my, x, iy, w, ITEM_H)) GuiUtil.rect(x + 1, iy, w - 2, ITEM_H, 0x40FFFFFF);
            boolean dangerous = item.confirmLabel != null;
            font.drawString(
                item.armed ? item.confirmLabel : item.label,
                x + 6,
                iy + 2,
                dangerous ? 0xFF6060 : 0xFFFFFF);
        }
    }

    /** Returns false if the click was outside, meaning the menu should close. */
    boolean mouseClicked(int mx, int my) {
        if (!GuiUtil.inside(mx, my, x, y, w, items.size() * ITEM_H + 4)) return false;
        int index = (my - y - 2) / ITEM_H;
        if (index < 0 || index >= items.size()) return true;
        Item item = items.get(index);
        if (item.confirmLabel != null && !item.armed) {
            item.armed = true;
            return true;
        }
        item.action.run();
        items.clear();
        return false;
    }
}
