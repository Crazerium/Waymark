package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A small right-click menu, optionally with an item icon per entry. Dangerous entries need a second click, so
 * nothing is deleted by accident.
 */
@SideOnly(Side.CLIENT)
class ContextMenu {

    private static final int ITEM_H = 14;

    private static final class Item {

        final ItemStack icon;
        final String label;
        final String confirmLabel;
        final Runnable action;
        boolean armed;

        Item(ItemStack icon, String label, String confirmLabel, Runnable action) {
            this.icon = icon;
            this.label = label;
            this.confirmLabel = confirmLabel;
            this.action = action;
        }
    }

    private int x, y;
    private final List<Item> items = new ArrayList<>();
    private int w;

    ContextMenu(int x, int y) {
        this.x = x;
        this.y = y;
    }

    void add(String label, Runnable action) {
        items.add(new Item(null, label, null, action));
    }

    void add(ItemStack icon, String label, Runnable action) {
        items.add(new Item(icon, label, null, action));
    }

    void addDangerous(String label, String confirmLabel, Runnable action) {
        items.add(new Item(null, label, confirmLabel, action));
    }

    private int height() {
        return items.size() * ITEM_H + 4;
    }

    void draw(FontRenderer font, int mx, int my) {
        boolean icons = false;
        w = 0;
        for (Item item : items) {
            icons |= item.icon != null;
            w = Math.max(w, font.getStringWidth(item.label));
            if (item.confirmLabel != null) w = Math.max(w, font.getStringWidth(item.confirmLabel));
        }
        int textX = icons ? 20 : 6;
        w += textX + 8;

        // Keep the whole menu on screen, e.g. when opened near the bottom right corner.
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        x = Math.max(0, Math.min(x, res.getScaledWidth() - w));
        y = Math.max(0, Math.min(y, res.getScaledHeight() - height()));

        GuiUtil.frame(x, y, w, height(), 0xF0101018, 0xFF8090B0);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int iy = y + 2 + i * ITEM_H;
            if (GuiUtil.inside(mx, my, x, iy, w, ITEM_H)) GuiUtil.rect(x + 1, iy, w - 2, ITEM_H, 0x40FFFFFF);
            if (item.icon != null) {
                GL11.glPushMatrix();
                GL11.glTranslated(x + 4, iy + 1, 0);
                GL11.glScaled(0.75, 0.75, 1);
                GuiUtil.item(item.icon, 0, 0);
                GL11.glPopMatrix();
            }
            boolean dangerous = item.confirmLabel != null;
            font.drawString(
                item.armed ? item.confirmLabel : item.label,
                x + textX,
                iy + 3,
                dangerous ? 0xFF6060 : 0xFFFFFF);
        }
    }

    /** Returns false if the click was outside, meaning the menu should close. */
    boolean mouseClicked(int mx, int my) {
        if (!GuiUtil.inside(mx, my, x, y, w, height())) return false;
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
