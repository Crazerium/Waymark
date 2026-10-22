package com.crazerium.waymark.client.gui;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A full-screen editor for a quest description, with a toolbar that types formatting codes at the cursor: bold,
 * italic, underline, strikethrough, the 16 colours and reset. The description is shown with the codes as typed;
 * the quest window shows the result.
 */
@SideOnly(Side.CLIENT)
public class DescriptionEditScreen extends GuiScreen {

    private static final int BAR = 22;
    private static final String COLORS = "0123456789abcdef";
    private static final int[] COLOR_RGB = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
        0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

    private final GuiScreen parent;
    private final Consumer<List<String>> onSave;
    private List<String> lines;
    private TextArea area;
    private boolean palette;
    private int px, py, pw, ph;

    public DescriptionEditScreen(GuiScreen parent, List<String> lines, Consumer<List<String>> onSave) {
        this.parent = parent;
        this.lines = lines;
        this.onSave = onSave;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (area != null) lines = area.getLines();
        px = 8;
        py = 14;
        pw = width - 16;
        ph = height - 22;
        area = new TextArea(fontRendererObj, px + 6, py + BAR + 6, pw - 18, Math.max(3, (ph - BAR - 12) / 14), lines);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        area.tick();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Toolbar buttons from the left: save, then the formatting codes. */
    private static final String[][] TOOLS = { { "§l B", "&l" }, { "§o I", "&o" }, { "§n U", "&n" }, { "§m S", "&m" },
        { "r", "&r" } };

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        drawDefaultBackground();
        String heading = I18n.format("waymark.edit.description_heading");
        fontRendererObj
            .drawStringWithShadow(heading, (width - fontRendererObj.getStringWidth(heading)) / 2, 3, 0xFFFFFF);
        GuiUtil.frame(px, py, pw, ph, 0xE0101018, 0xFF8090B0);
        GuiUtil.rect(px + 1, py + BAR, pw - 2, 1, 0xFF404860);

        GuiUtil.button(fontRendererObj, px + 4, py + 4, 16, 14, "§a✔", mx, my);
        int bx = px + 28;
        for (String[] tool : TOOLS) {
            GuiUtil.button(fontRendererObj, bx, py + 4, 16, 14, tool[0].trim(), mx, my);
            bx += 18;
        }
        // The colour button shows a small grid of the palette.
        GuiUtil.button(fontRendererObj, bx, py + 4, 16, 14, "", mx, my);
        for (int i = 0; i < 4; i++)
            GuiUtil.rect(bx + 3 + (i % 2) * 5, py + 6 + (i / 2) * 5, 4, 4, 0xFF000000 | COLOR_RGB[9 + i * 2]);
        // Page break and image insert whole lines.
        GuiUtil.button(fontRendererObj, bx + 26, py + 4, 16, 14, "§7⇣", mx, my);
        GuiUtil.button(fontRendererObj, bx + 44, py + 4, 16, 14, "§b▣", mx, my);
        String tip = GuiUtil.inside(mx, my, bx + 26, py + 4, 16, 14) ? "waymark.edit.insert_page_break"
            : GuiUtil.inside(mx, my, bx + 44, py + 4, 16, 14) ? "waymark.edit.insert_image" : null;
        GuiUtil.button(fontRendererObj, px + pw - 20, py + 4, 16, 14, "§cx", mx, my);

        area.draw();

        if (palette) {
            int ox = paletteX(), oy = py + BAR;
            GuiUtil.frame(ox, oy, 8 * 12 + 4, 2 * 12 + 4, 0xF0101018, 0xFF8090B0);
            for (int i = 0; i < 16; i++) {
                int cx = ox + 2 + (i % 8) * 12, cy = oy + 2 + (i / 8) * 12;
                boolean hover = GuiUtil.inside(mx, my, cx, cy, 11, 11);
                GuiUtil.frame(cx, cy, 11, 11, 0xFF000000 | COLOR_RGB[i], hover ? 0xFFFFFFFF : 0xFF202020);
            }
        }
        super.drawScreen(mx, my, partialTicks);
        if (tip != null && !palette)
            drawHoveringText(java.util.Collections.singletonList(I18n.format(tip)), mx, my, fontRendererObj);
    }

    private int paletteX() {
        return px + 28 + TOOLS.length * 18;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (palette) {
            int ox = paletteX(), oy = py + BAR;
            for (int i = 0; i < 16; i++) {
                if (GuiUtil.inside(mx, my, ox + 2 + (i % 8) * 12, oy + 2 + (i / 8) * 12, 11, 11)) {
                    area.insert("&" + COLORS.charAt(i));
                    break;
                }
            }
            palette = false;
            return;
        }
        if (GuiUtil.inside(mx, my, px + 4, py + 4, 16, 14)) {
            save();
            return;
        }
        if (GuiUtil.inside(mx, my, px + pw - 20, py + 4, 16, 14)) {
            mc.displayGuiScreen(parent);
            return;
        }
        int bx = px + 28;
        for (String[] tool : TOOLS) {
            // Toolbar clicks must not take the cursor out of the text.
            if (GuiUtil.inside(mx, my, bx, py + 4, 16, 14)) {
                area.insert(tool[1]);
                return;
            }
            bx += 18;
        }
        if (GuiUtil.inside(mx, my, bx, py + 4, 16, 14)) {
            palette = true;
            return;
        }
        if (GuiUtil.inside(mx, my, bx + 26, py + 4, 16, 14)) {
            area.insertLine(DescriptionPages.PAGE_BREAK);
            return;
        }
        if (GuiUtil.inside(mx, my, bx + 44, py + 4, 16, 14)) {
            mc.displayGuiScreen(new ImagePickerScreen(this, line -> area.insertLine(line)));
            return;
        }
        area.mouseClicked(mx, my, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) area.scroll(wheel > 0 ? -3 : 3);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            if (palette) palette = false;
            else mc.displayGuiScreen(parent);
            return;
        }
        // Ctrl+Enter saves, like the check mark.
        if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && isCtrlKeyDown()) {
            save();
            return;
        }
        area.keyTyped(c, key);
    }

    private void save() {
        onSave.accept(area.getLines());
        mc.displayGuiScreen(parent);
    }
}
