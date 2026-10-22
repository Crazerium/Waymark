package com.crazerium.waymark.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class GuiUtil {

    private static final RenderItem ITEM_RENDER = new RenderItem();

    private GuiUtil() {}

    static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    static void rect(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + h, color);
    }

    static void frame(int x, int y, int w, int h, int fill, int border) {
        rect(x, y, w, h, border);
        rect(x + 1, y + 1, w - 2, h - 2, fill);
    }

    /** A flat button; returns true if the mouse is over it. */
    static boolean button(FontRenderer font, int x, int y, int w, int h, String text, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        frame(x, y, w, h, hover ? 0xFF4A5A80 : 0xFF303848, hover ? 0xFFFFFFFF : 0xFF8090B0);
        font.drawString(text, x + (w - font.getStringWidth(text)) / 2, y + (h - 8) / 2, 0xFFFFFF);
        return hover;
    }

    /** Draws a 16x16 item with its top-left corner at (x, y). Leaves depth testing off. */
    static void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.getItem() == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1, 1, 1, 1);
    }

    static void line(double x1, double y1, double x2, double y2, float width, int color) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glLineWidth(width);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        t.setColorRGBA_I(color & 0xFFFFFF, color >>> 24);
        t.addVertex(x1, y1, 0);
        t.addVertex(x2, y2, 0);
        t.draw();
        GL11.glLineWidth(1);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** Restricts drawing to a rectangle given in GUI coordinates. */
    static void scissor(int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getMinecraft();
        int f = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * f, mc.displayHeight - (y + h) * f, Math.max(0, w * f), Math.max(0, h * f));
    }

    static void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    static int guiScale() {
        Minecraft mc = Minecraft.getMinecraft();
        return new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();
    }

    /** Lets pack makers write colors as &amp;a, &amp;l etc. instead of the section sign. */
    static String colorCodes(String text) {
        return text.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }

    static String trim(FontRenderer font, String text, int width) {
        if (font.getStringWidth(text) <= width) return text;
        return font.trimStringToWidth(text, width - font.getStringWidth("...")) + "...";
    }
}
