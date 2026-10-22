package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.crazerium.waymark.Waymark;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * An overlay for picking an item: either from the player's inventory, or by searching every item in the game by
 * name. An empty search lists everything; "@mod" limits results to one mod (e.g. "@gregtech circuit").
 */
@SideOnly(Side.CLIENT)
class ItemPicker {

    private static final int SLOT = 18;
    private static final int COLS = 9;
    private static final int ROWS = 6;

    /** Every item and sub-item, with lowercase name and registry id for searching. Built once, on first search. */
    private static List<Object[]> allItems;

    private final Consumer<ItemStack> onPick;
    private final boolean allowClear;
    private boolean search;
    private GuiTextField query;
    private String lastQuery;
    private final List<ItemStack> results = new ArrayList<>();
    private int scroll;
    private int x, y, w, h;
    private ItemStack hovered;

    /** {@code onPick} gets a copy of the stack, or null if the player chose to clear. */
    ItemPicker(Consumer<ItemStack> onPick, boolean allowClear) {
        this.onPick = onPick;
        this.allowClear = allowClear;
    }

    private void layout(FontRenderer font, int screenW, int screenH) {
        w = COLS * SLOT + 16;
        h = ROWS * SLOT + 74;
        x = (screenW - w) / 2;
        y = (screenH - h) / 2;
        if (query == null) {
            query = new GuiTextField(font, x + 8, y + 32, w - 16, 12);
            query.setMaxStringLength(100);
        }
    }

    void draw(FontRenderer font, int screenW, int screenH, int mx, int my) {
        layout(font, screenW, screenH);
        GuiUtil.rect(0, 0, screenW, screenH, 0x80000000);
        GuiUtil.frame(x, y, w, h, 0xF0101018, 0xFF8090B0);

        // Tabs.
        int tabW = (w - 20) / 2;
        tab(font, x + 8, I18n.format("waymark.edit.picker_inventory"), !search, mx, my, tabW);
        tab(font, x + 12 + tabW, I18n.format("waymark.edit.picker_search"), search, mx, my, tabW);

        hovered = null;
        int gridTop = y + 50;
        if (search) {
            query.drawTextBox();
            updateResults();
            int maxScroll = Math.max(0, (results.size() + COLS - 1) / COLS - ROWS);
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            for (int i = 0; i < ROWS * COLS; i++) {
                int index = scroll * COLS + i;
                ItemStack stack = index < results.size() ? results.get(index) : null;
                drawSlot(font, stack, x + 8 + (i % COLS) * SLOT, gridTop + (i / COLS) * SLOT, mx, my);
            }
            if (results.isEmpty() && !query.getText()
                .trim()
                .isEmpty()) font.drawString(I18n.format("waymark.edit.picker_none"), x + 10, gridTop + 4, 0x808080);
        } else {
            font.drawString(I18n.format("waymark.edit.pick_item"), x + 8, y + 34, 0xC0C0C0);
            ItemStack[] inventory = Minecraft.getMinecraft().thePlayer.inventory.mainInventory;
            for (int i = 0; i < 36; i++) {
                // Hotbar (slots 0-8) goes at the bottom, like in the inventory screen.
                int row = i < 9 ? 3 : i / 9 - 1;
                drawSlot(font, inventory[i], x + 8 + (i % 9) * SLOT, gridTop + row * SLOT + (i < 9 ? 4 : 0), mx, my);
            }
        }

        int by = y + h - 18;
        if (allowClear) GuiUtil.button(font, x + 8, by, 60, 14, I18n.format("waymark.edit.clear"), mx, my);
        GuiUtil.button(font, x + w - 68, by, 60, 14, I18n.format("gui.cancel"), mx, my);
    }

    private void tab(FontRenderer font, int tx, String label, boolean active, int mx, int my, int tabW) {
        boolean hover = GuiUtil.inside(mx, my, tx, y + 8, tabW, 16);
        GuiUtil.frame(
            tx,
            y + 8,
            tabW,
            16,
            active ? 0xFF2A3550 : hover ? 0xFF303040 : 0xFF181820,
            active ? 0xFFFFCC66 : 0xFF404860);
        font.drawString(label, tx + (tabW - font.getStringWidth(label)) / 2, y + 12, active ? 0xFFFFFF : 0xA0A0A0);
    }

    private void drawSlot(FontRenderer font, ItemStack stack, int sx, int sy, int mx, int my) {
        boolean hover = GuiUtil.inside(mx, my, sx, sy, SLOT - 1, SLOT - 1);
        GuiUtil.frame(sx, sy, SLOT - 1, SLOT - 1, hover ? 0xFF404058 : 0xFF202030, 0xFF404860);
        if (stack == null) return;
        GuiUtil.item(stack, sx, sy);
        if (stack.stackSize > 1) {
            String n = String.valueOf(stack.stackSize);
            font.drawStringWithShadow(n, sx + 17 - font.getStringWidth(n), sy + 9, 0xFFFFFF);
        }
        if (hover) hovered = stack;
    }

    private void updateResults() {
        String text = query.getText()
            .trim()
            .toLowerCase(Locale.ROOT);
        if (text.equals(lastQuery)) return;
        lastQuery = text;
        results.clear();
        scroll = 0;
        String mod = null;
        StringBuilder words = new StringBuilder();
        for (String part : text.split("\\s+")) {
            if (part.startsWith("@") && part.length() > 1) mod = part.substring(1);
            else words.append(part)
                .append(' ');
        }
        String name = words.toString()
            .trim();
        // An empty search matches every item, so browsing works without typing.
        for (Object[] entry : allItems()) {
            String id = (String) entry[2];
            if (mod != null && !id.startsWith(mod)) continue;
            if (!name.isEmpty() && !((String) entry[1]).contains(name) && !id.contains(name)) continue;
            results.add((ItemStack) entry[0]);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> allItems() {
        if (allItems != null) return allItems;
        List<Object[]> list = new ArrayList<>();
        List<ItemStack> subs = new ArrayList<>();
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            subs.clear();
            try {
                item.getSubItems(item, item.getCreativeTab(), subs);
            } catch (RuntimeException e) {
                continue;
            }
            String id = String.valueOf(Item.itemRegistry.getNameForObject(item))
                .toLowerCase(Locale.ROOT);
            for (ItemStack stack : subs) {
                if (stack == null || stack.getItem() == null) continue;
                String display;
                try {
                    display = stack.getDisplayName()
                        .toLowerCase(Locale.ROOT);
                } catch (RuntimeException e) {
                    display = id;
                }
                list.add(new Object[] { stack, display, id });
            }
        }
        Waymark.LOG.info("Item search index: {} items", list.size());
        allItems = list;
        return list;
    }

    /** The stack under the mouse, for the item tooltip. */
    ItemStack hovered() {
        return hovered;
    }

    void scroll(int direction) {
        scroll += direction;
    }

    /** Returns true when the picker is done and should close. */
    boolean keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) return true;
        if (search) query.textboxKeyTyped(c, key);
        return false;
    }

    /** Returns true when the picker is done and should close. */
    boolean mouseClicked(int mx, int my) {
        int tabW = (w - 20) / 2;
        if (GuiUtil.inside(mx, my, x + 8, y + 8, tabW, 16)) {
            search = false;
            return false;
        }
        if (GuiUtil.inside(mx, my, x + 12 + tabW, y + 8, tabW, 16)) {
            search = true;
            query.setFocused(true);
            return false;
        }
        if (search) query.mouseClicked(mx, my, 0);
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
