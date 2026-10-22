package com.crazerium.waymark.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.crazerium.waymark.client.ClientQuests;
import com.crazerium.waymark.quest.BookEdits;
import com.crazerium.waymark.quest.Chapter;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Edits a chapter's title, icon and description. */
@SideOnly(Side.CLIENT)
public class ChapterEditScreen extends GuiScreen {

    private final GuiScreen parent;
    private final Chapter chapter;

    private GuiTextField title;
    private String titleText;
    private TextArea description;
    private List<String> descriptionLines;
    private JsonObject icon;
    private ItemPicker picker;
    private ItemStack tooltipStack;
    private int px, py, pw, ph;

    public ChapterEditScreen(GuiScreen parent, Chapter chapter) {
        this.parent = parent;
        this.chapter = chapter;
        JsonObject json = chapter.toJson();
        titleText = JsonUtil.getString(json, "title", "");
        descriptionLines = JsonUtil.getStringList(json, "description");
        icon = JsonUtil.getObject(json, "icon");
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (title != null) {
            titleText = title.getText();
            descriptionLines = description.getLines();
        }
        pw = Math.min(360, width - 20);
        ph = Math.min(190, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        title = new GuiTextField(fontRendererObj, px + 80, py + 22, pw - 88, 14);
        title.setMaxStringLength(200);
        title.setText(titleText);
        description = new TextArea(
            fontRendererObj,
            px + 80,
            py + 64,
            pw - 94,
            Math.max(3, (ph - 100) / 14),
            descriptionLines);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        title.updateCursorCounter();
        description.tick();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        tooltipStack = null;
        int hx = picker == null ? mx : -1, hy = picker == null ? my : -1;
        drawDefaultBackground();
        GuiUtil.frame(px, py, pw, ph, 0xF0101018, 0xFF8090B0);
        fontRendererObj.drawStringWithShadow(I18n.format("waymark.edit.chapter_heading"), px + 8, py + 8, 0xFFFFFF);

        fontRendererObj.drawString(ObjectEditScreen.label("title"), px + 8, py + 25, 0xC0C0C0);
        title.drawTextBox();

        fontRendererObj.drawString(ObjectEditScreen.label("icon"), px + 8, py + 45, 0xC0C0C0);
        boolean hover = GuiUtil.inside(hx, hy, px + 80, py + 40, 17, 17);
        GuiUtil.frame(px + 80, py + 40, 17, 17, hover ? 0xFF404058 : 0xFF202030, 0xFF404860);
        ItemStack stack = JsonUtil.readStack(icon);
        if (stack != null) {
            GuiUtil.item(stack, px + 80, py + 40);
            if (hover) tooltipStack = stack;
        }

        fontRendererObj.drawString(ObjectEditScreen.label("description"), px + 8, py + 67, 0xC0C0C0);
        description.draw();

        int by = py + ph - 22;
        GuiUtil.button(fontRendererObj, px + pw - 140, by, 64, 16, I18n.format("waymark.edit.save"), hx, hy);
        GuiUtil.button(fontRendererObj, px + pw - 72, by, 64, 16, I18n.format("gui.cancel"), hx, hy);

        if (picker != null) {
            picker.draw(fontRendererObj, width, height, mx, my);
            tooltipStack = picker.hovered();
        }
        super.drawScreen(mx, my, partialTicks);
        if (tooltipStack != null) renderToolTip(tooltipStack, mx, my);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (picker != null) {
            if (picker.mouseClicked(mx, my)) picker = null;
            return;
        }
        title.mouseClicked(mx, my, button);
        description.mouseClicked(mx, my, button);
        if (GuiUtil.inside(mx, my, px + 80, py + 40, 17, 17)) {
            if (button == 1) icon = null;
            else picker = new ItemPicker(s -> {
                if (s != null) s.stackSize = 1;
                icon = s == null ? null : JsonUtil.writeStack(s);
            }, true);
            return;
        }
        int by = py + ph - 22;
        if (GuiUtil.inside(mx, my, px + pw - 140, by, 64, 16)) save();
        else if (GuiUtil.inside(mx, my, px + pw - 72, by, 64, 16)) mc.displayGuiScreen(parent);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && picker != null) {
            picker.scroll(wheel > 0 ? -1 : 1);
            return;
        }
        if (wheel != 0 && picker == null) description.scroll(wheel > 0 ? -1 : 1);
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (picker != null) {
            // The picker has its own search box; it closes itself on Esc.
            if (picker.keyTyped(c, key)) picker = null;
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (picker != null) picker = null;
            else mc.displayGuiScreen(parent);
            return;
        }
        if (description.keyTyped(c, key)) return;
        title.textboxKeyTyped(c, key);
    }

    private void save() {
        JsonObject data = new JsonObject();
        data.addProperty("title", title.getText());
        List<String> lines = description.getLines();
        if (!lines.isEmpty()) data.add("description", JsonUtil.toArray(lines));
        if (icon != null) data.add("icon", icon);
        ClientQuests.sendEdit(BookEdits.updateChapter(chapter, data));
        mc.displayGuiScreen(parent);
    }
}
