package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A multi-line text box built from a fixed column of one-line fields. Enter splits a line, Backspace at the start of
 * a line joins it to the previous one, the arrow keys move between lines and the wheel scrolls.
 */
@SideOnly(Side.CLIENT)
class TextArea {

    private static final int LINE_H = 14;

    private final List<String> lines;
    private final GuiTextField[] fields;
    private final int x, y, w;
    private int first;
    /** Index in {@link #lines} of the line being edited, or -1. */
    private int focus = -1;
    private int cursor;

    TextArea(FontRenderer font, int x, int y, int w, int visibleLines, List<String> initial) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.lines = new ArrayList<>(initial);
        fields = new GuiTextField[visibleLines];
        for (int i = 0; i < visibleLines; i++) {
            // No per-line box: the area draws one background, so the lines read as one text.
            fields[i] = new GuiTextField(font, x + 4, y + i * LINE_H + 3, w - 8, LINE_H - 3);
            fields[i].setMaxStringLength(2000);
            fields[i].setEnableBackgroundDrawing(false);
        }
        rebind();
    }

    int height() {
        return fields.length * LINE_H;
    }

    /** The text, without trailing empty lines. */
    List<String> getLines() {
        sync();
        List<String> out = new ArrayList<>(lines);
        while (!out.isEmpty() && out.get(out.size() - 1)
            .isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    boolean isFocused() {
        return focus >= 0;
    }

    /** Types text at the cursor of the line being edited, e.g. a formatting code from a toolbar button. */
    void insert(String text) {
        if (focus < first || focus >= first + fields.length) return;
        GuiTextField field = fields[focus - first];
        field.writeText(text);
        lines.set(focus, field.getText());
        cursor = field.getCursorPosition();
    }

    void draw() {
        GuiUtil.frame(x, y, w, height(), 0xFF0C0C12, 0xFF404860);
        if (focus >= first && focus < first + fields.length)
            GuiUtil.rect(x + 1, y + (focus - first) * LINE_H + 1, w - 2, LINE_H, 0x18FFFFFF);
        for (GuiTextField field : fields) field.drawTextBox();
        if (lines.size() > fields.length) {
            int barH = Math.max(6, height() * fields.length / lines.size());
            int barY = y + (height() - barH) * first / Math.max(1, lines.size() - fields.length);
            GuiUtil.rect(x + w + 2, barY, 2, barH, 0xFF8090B0);
        }
    }

    void tick() {
        for (GuiTextField field : fields) field.updateCursorCounter();
    }

    void scroll(int direction) {
        sync();
        first = Math.max(0, Math.min(first + direction, Math.max(0, lines.size() - fields.length + 1)));
        rebind();
    }

    boolean mouseClicked(int mx, int my, int button) {
        sync();
        for (int i = 0; i < fields.length; i++) {
            if (!GuiUtil.inside(mx, my, x, y + i * LINE_H, w, LINE_H)) continue;
            focus = first + i;
            while (lines.size() <= focus) lines.add("");
            rebind();
            fields[i].mouseClicked(mx, my, button);
            cursor = fields[i].getCursorPosition();
            return true;
        }
        focus = -1;
        rebind();
        return false;
    }

    boolean keyTyped(char c, int key) {
        if (focus < 0) return false;
        GuiTextField field = fields[focus - first];
        cursor = field.getCursorPosition();
        sync();
        String line = lines.get(focus);
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            lines.set(focus, line.substring(0, cursor));
            lines.add(focus + 1, line.substring(cursor));
            focus++;
            cursor = 0;
        } else if (key == Keyboard.KEY_BACK && cursor == 0 && focus > 0) {
            String prev = lines.get(focus - 1);
            lines.set(focus - 1, prev + line);
            lines.remove(focus);
            focus--;
            cursor = prev.length();
        } else if (key == Keyboard.KEY_UP && focus > 0) {
            focus--;
            cursor = Math.min(
                cursor,
                lines.get(focus)
                    .length());
        } else if (key == Keyboard.KEY_DOWN) {
            focus++;
            if (lines.size() <= focus) lines.add("");
            cursor = Math.min(
                cursor,
                lines.get(focus)
                    .length());
        } else {
            field.textboxKeyTyped(c, key);
            lines.set(focus, field.getText());
            cursor = field.getCursorPosition();
            return true;
        }
        if (focus < first) first = focus;
        if (focus >= first + fields.length) first = focus - fields.length + 1;
        rebind();
        return true;
    }

    /** Copies the focused field's text into the model. */
    private void sync() {
        if (focus >= first && focus < first + fields.length && focus < lines.size())
            lines.set(focus, fields[focus - first].getText());
    }

    /** Shows the lines from {@link #first} in the fields and puts the cursor in the focused one. */
    private void rebind() {
        for (int i = 0; i < fields.length; i++) {
            int index = first + i;
            fields[i].setText(index < lines.size() ? lines.get(index) : "");
            fields[i].setFocused(index == focus);
            if (index == focus) fields[i].setCursorPosition(cursor);
        }
    }
}
