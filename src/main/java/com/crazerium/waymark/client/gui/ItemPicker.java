package com.crazerium.waymark.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * An overlay that shows the player's inventory; clicking a stack picks it. Editors use it to set task items, icons
 * and rewards from items the editor is holding.
 */
@SideOnly(Side.CLIENT)
class ItemPicker {

    private static final int SLOT = 18;

    private final Consumer<ItemStack> onPick;
    private final boolean allowClear;
    private int x, y, w, h;
    private ItemStack hovered;

    /** {@code onPick} gets a copy of the stack, or null if the player chose to clear. */
    ItemPicker(Consumer<ItemStack> onPick, boolean allowClear) {
        this.onPick = onPick;
        this.allowClear = allowClear;
    }

    void draw(FontRenderer font, int screenW, int screenH, int mx, int my) {
        w = 9 * SLOT + 16;
        h = 4 * SLOT + 50;
        x = (screenW - w) / 2;
        y = (screenH - h) / 2;
        GuiUtil.rect(0, 0, screenW, screenH, 0x80000000);
        GuiUtil.frame(x, y, w, h, 0xF0101018, 0xFF8090B0);
        font.drawString(I18n.format("waymark.edit.pick_item"), x + 8, y + 6, 0xFFFFFF);

        ItemStack[] inventory = Minecraft.getMinecraft().thePlayer.inventory.mainInventory;
        hovered = null;
        for (int i = 0; i < 36; i++) {
            // Hotbar (slots 0-8) goes at the bottom, like in the inventory screen.
            int row = i < 9 ? 3 : i / 9 - 1;
            int sx = x + 8 + (i % 9) * SLOT, sy = y + 18 + row * SLOT + (i < 9 ? 4 : 0);
            boolean hover = GuiUtil.inside(mx, my, sx, sy, SLOT - 1, SLOT - 1);
            GuiUtil.frame(sx, sy, SLOT - 1, SLOT - 1, hover ? 0xFF404058 : 0xFF202030, 0xFF404860);
            ItemStack stack = inventory[i];
            if (stack == null) continue;
            GuiUtil.item(stack, sx, sy);
            if (stack.stackSize > 1) {
                String n = String.valueOf(stack.stackSize);
                font.drawStringWithShadow(n, sx + 17 - font.getStringWidth(n), sy + 9, 0xFFFFFF);
            }
            if (hover) hovered = stack;
        }

        int by = y + h - 18;
        if (allowClear) GuiUtil.button(font, x + 8, by, 60, 14, I18n.format("waymark.edit.clear"), mx, my);
        GuiUtil.button(font, x + w - 68, by, 60, 14, I18n.format("gui.cancel"), mx, my);
    }

    /** The stack under the mouse, for the item tooltip. */
    ItemStack hovered() {
        return hovered;
    }

    /** Returns true when the picker is done and should close. */
    boolean mouseClicked(int mx, int my) {
        int by = y + h - 18;
        if (allowClear && GuiUtil.inside(mx, my, x + 8, by, 60, 14)) {
            onPick.accept(null);
            return true;
        }
        if (GuiUtil.inside(mx, my, x + w - 68, by, 60, 14) || !GuiUtil.inside(mx, my, x, y, w, h)) return true;
        if (hovered != null) {
            onPick.accept(hovered.copy());
            return true;
        }
        return false;
    }
}
