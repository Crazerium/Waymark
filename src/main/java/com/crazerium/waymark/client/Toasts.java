package com.crazerium.waymark.client;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.crazerium.waymark.quest.Quest;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * "Quest completed" notifications that slide in at the top right, one after another. Drawn after everything else,
 * so they show over open screens too.
 */
@SideOnly(Side.CLIENT)
public class Toasts {

    private static final long SHOW_MS = 4000;
    private static final long SLIDE_MS = 250;
    private static final int W = 160, H = 32;
    private static final RenderItem ITEM_RENDER = new RenderItem();

    private static final class Toast {

        final String title;
        final ItemStack icon;
        long start = -1;

        Toast(String title, ItemStack icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private static final Deque<Toast> queue = new ArrayDeque<>();

    public static void questCompleted(Quest quest) {
        queue.add(new Toast(quest.getDisplayTitle(), quest.getIcon()));
    }

    public static void clear() {
        queue.clear();
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || queue.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        Toast toast = queue.peek();
        long now = System.currentTimeMillis();
        if (toast.start < 0) {
            toast.start = now;
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("random.levelup"), 1.0F));
        }
        long age = now - toast.start;
        if (age > SHOW_MS) {
            queue.poll();
            return;
        }
        // Slide in, hold, slide out.
        double shown = Math.min(1, Math.min(age, SHOW_MS - age) / (double) SLIDE_MS);

        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int x = res.getScaledWidth() - (int) (W * shown) - 2;
        int y = 4;
        FontRenderer font = mc.fontRenderer;

        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, 500);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        Gui.drawRect(x, y, x + W, y + H, 0xFFFFCC00);
        Gui.drawRect(x + 1, y + 1, x + W - 1, y + H - 1, 0xF0101018);
        font.drawStringWithShadow(I18n.format("waymark.toast.completed"), x + 28, y + 6, 0xFFCC00);
        String title = font.trimStringToWidth(toast.title.replaceAll("&([0-9a-fk-or])", "§$1"), W - 34);
        font.drawStringWithShadow(title, x + 28, y + 18, 0xFFFFFF);
        if (toast.icon != null) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            ITEM_RENDER.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), toast.icon, x + 7, y + 8);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glPopMatrix();
    }
}
