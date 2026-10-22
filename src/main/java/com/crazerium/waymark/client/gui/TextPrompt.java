package com.crazerium.waymark.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** A one-line text box drawn over the book, for quick edits like a quest's title. Enter accepts, Esc cancels. */
@SideOnly(Side.CLIENT)
class TextPrompt {

    private final String heading;
    private final Consumer<String> onAccept;
    private final GuiTextField field;
    private int x, y, w;

    TextPrompt(FontRenderer font, int screenW, int screenH, String heading, String value, Consumer<String> onAccept) {
        this.heading = heading;
        this.onAccept = onAccept;
        w = Math.min(320, screenW - 20);
        x = (screenW - w) / 2;
        y = screenH / 2 - 24;
        field = new GuiTextField(font, x + 8, y + 20, w - 16, 14);
        field.setMaxStringLength(2000);
        field.setText(value);
        field.setFocused(true);
    }

    void draw(FontRenderer font, int mx, int my) {
        GuiUtil.frame(x, y, w, 58, 0xF0101018, 0xFF8090B0);
        font.drawStringWithShadow(heading, x + 8, y + 6, 0xFFFFFF);
        field.drawTextBox();
        GuiUtil.button(font, x + w - 140, y + 38, 64, 14, I18n.format("waymark.edit.save"), mx, my);
        GuiUtil.button(font, x + w - 72, y + 38, 64, 14, I18n.format("gui.cancel"), mx, my);
    }

    void tick() {
        field.updateCursorCounter();
    }

    /** Returns true when the prompt is finished and should close. */
    boolean mouseClicked(int mx, int my, int button) {
        if (GuiUtil.inside(mx, my, x + w - 140, y + 38, 64, 14)) {
            onAccept.accept(field.getText());
            return true;
        }
        if (GuiUtil.inside(mx, my, x + w - 72, y + 38, 64, 14)) return true;
        field.mouseClicked(mx, my, button);
        return false;
    }

    /** Returns true when the prompt is finished and should close. */
    boolean keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) return true;
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            onAccept.accept(field.getText());
            return true;
        }
        field.textboxKeyTyped(c, key);
        return false;
    }
}
