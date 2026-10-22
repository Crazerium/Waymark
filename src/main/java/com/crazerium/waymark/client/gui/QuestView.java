package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import com.crazerium.waymark.client.ClientQuests;
import com.crazerium.waymark.client.QuestState;
import com.crazerium.waymark.progress.TeamProgress;
import com.crazerium.waymark.quest.LogicMode;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.reward.ChoiceReward;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The window that opens over the map when a quest is clicked: description, tasks and rewards. */
@SideOnly(Side.CLIENT)
class QuestView {

    private static final int ROW = 18;
    private static final int BUTTON_W = 54;

    private static final class Area {

        final int x, y, w, h;
        final Runnable action;

        Area(int x, int y, int w, int h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }

    final Quest quest;
    private final QuestBookScreen screen;
    private final List<Area> areas = new ArrayList<>();
    private int x, y, w, h;
    private int scroll, contentHeight, viewTop, viewBottom;

    QuestView(QuestBookScreen screen, Quest quest) {
        this.screen = screen;
        this.quest = quest;
        layout();
    }

    void layout() {
        w = Math.min(320, screen.width - 40);
        h = Math.min(260, screen.height - 40);
        x = (screen.width - w) / 2;
        y = (screen.height - h) / 2;
    }

    void draw(int mx, int my) {
        areas.clear();
        FontRenderer font = screen.font();
        TeamProgress progress = ClientQuests.getProgress();
        UUID player = ClientQuests.playerId();

        GuiUtil.rect(0, 0, screen.width, screen.height, 0x80000000);
        GuiUtil.frame(x, y, w, h, 0xF0101018, QuestState.of(quest).color);

        font.drawStringWithShadow(
            GuiUtil.trim(font, GuiUtil.colorCodes(quest.getDisplayTitle()), w - 30),
            x + 8,
            y + 8,
            0xFFFFFF);
        int closeX = x + w - 16, closeY = y + 5;
        GuiUtil.button(font, closeX, closeY, 12, 12, "x", mx, my);
        areas.add(new Area(closeX, closeY, 12, 12, screen::closeQuest));

        viewTop = y + 22;
        viewBottom = y + h - 6;
        int left = x + 8;
        int inner = w - 16;
        int right = x + w - 8;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (viewBottom - viewTop)));
        int cy = viewTop - scroll;
        boolean mouseInView = my >= viewTop && my < viewBottom;

        GuiUtil.scissor(x + 1, viewTop, w - 2, viewBottom - viewTop);

        if (!quest.getSubtitle()
            .isEmpty()) {
            for (String line : wrap(font, "§7" + GuiUtil.colorCodes(quest.getSubtitle()), inner)) {
                font.drawString(line, left, cy, 0xAAAAAA);
                cy += 10;
            }
            cy += 2;
        }

        if (progress != null && !progress.isUnlocked(quest)) {
            StringBuilder deps = new StringBuilder();
            for (Quest dep : quest.getDependencies()) {
                if (progress.isCompleted(dep)) continue;
                if (deps.length() > 0) deps.append(", ");
                deps.append(GuiUtil.colorCodes(dep.getDisplayTitle()));
            }
            for (String line : wrap(font, "§c" + I18n.format("waymark.gui.requires", deps), inner)) {
                font.drawString(line, left, cy, 0xFF5555);
                cy += 10;
            }
            cy += 2;
        }

        for (String paragraph : quest.getDescription()) {
            if (paragraph.isEmpty()) {
                cy += 6;
                continue;
            }
            for (String line : wrap(font, GuiUtil.colorCodes(paragraph), inner)) {
                font.drawString(line, left, cy, 0xFFFFFF);
                cy += 10;
            }
        }

        if (!quest.getTasks()
            .isEmpty()) {
            cy += 6;
            String header = "§e" + I18n.format("waymark.gui.tasks");
            if (quest.getTaskMode() != LogicMode.ALL) header += " §7" + I18n.format(
                "waymark.gui.task_mode." + quest.getTaskMode()
                    .key());
            font.drawString(header, left, cy, 0xFFFFFF);
            cy += 12;
            boolean canProgress = progress != null && progress.canProgress(quest);
            for (Task task : quest.getTasks()) {
                long value = progress != null ? progress.getProgress(task) : 0;
                boolean done = progress != null && (progress.isComplete(task) || progress.isCompleted(quest));
                int textRight = right;

                if (done) {
                    String check = "§a✔";
                    font.drawString(check, right - font.getStringWidth(check), cy + 5, 0xFFFFFF);
                    textRight -= 12;
                } else {
                    if (canProgress && task.hasSubmitButton()) {
                        int bx = right - BUTTON_W;
                        GuiUtil.button(
                            font,
                            bx,
                            cy + 1,
                            BUTTON_W,
                            14,
                            I18n.format("waymark.gui.submit"),
                            mouseInView ? mx : -1,
                            my);
                        areas.add(new Area(bx, cy + 1, BUTTON_W, 14, () -> ClientQuests.submit(task)));
                        textRight = bx - 4;
                    }
                    if (task.getMaxProgress() > 1) {
                        String count = "§7" + value + "/" + task.getMaxProgress();
                        textRight -= font.getStringWidth(count);
                        font.drawString(count, textRight, cy + 5, 0xFFFFFF);
                        textRight -= 6;
                    }
                }

                drawIconRow(
                    font,
                    task.getIcon(),
                    task.isOptional() ? task.getDisplayTitle() + " §8" + I18n.format("waymark.gui.optional")
                        : task.getDisplayTitle(),
                    left,
                    cy,
                    textRight,
                    done ? 0x88CC88 : task.isOptional() ? 0xAAAAAA : 0xFFFFFF,
                    mx,
                    my,
                    mouseInView);
                cy += ROW;
            }
        }

        if (!quest.getRewards()
            .isEmpty()) {
            cy += 6;
            font.drawString("§6" + I18n.format("waymark.gui.rewards"), left, cy, 0xFFFFFF);
            cy += 12;
            boolean completed = progress != null && progress.isCompleted(quest);
            for (Reward reward : quest.getRewards()) {
                int textRight = right;
                boolean claimed = progress != null && progress.isClaimed(player, reward);
                if (claimed) {
                    String check = "§a✔";
                    font.drawString(check, right - font.getStringWidth(check), cy + 5, 0xFFFFFF);
                    textRight -= 12;
                } else if (completed && !(reward instanceof ChoiceReward)) {
                    int bx = right - BUTTON_W;
                    GuiUtil.button(
                        font,
                        bx,
                        cy + 1,
                        BUTTON_W,
                        14,
                        I18n.format("waymark.gui.claim"),
                        mouseInView ? mx : -1,
                        my);
                    areas.add(new Area(bx, cy + 1, BUTTON_W, 14, () -> ClientQuests.claim(reward, -1)));
                    textRight = bx - 4;
                }
                drawIconRow(
                    font,
                    reward.getIcon(),
                    reward.getTitle(),
                    left,
                    cy,
                    textRight,
                    claimed ? 0x88CC88 : 0xFFFFFF,
                    mx,
                    my,
                    mouseInView);
                cy += ROW;
                if (reward instanceof ChoiceReward && !claimed)
                    cy = drawChoices(font, (ChoiceReward) reward, completed, left, inner, cy, mx, my, mouseInView);
            }
        }

        GuiUtil.endScissor();
        contentHeight = cy + scroll - viewTop;

        int viewHeight = viewBottom - viewTop;
        if (contentHeight > viewHeight) {
            int barH = Math.max(10, viewHeight * viewHeight / contentHeight);
            int barY = viewTop + (viewHeight - barH) * scroll / (contentHeight - viewHeight);
            GuiUtil.rect(x + w - 4, barY, 2, barH, 0xFF8090B0);
        }
    }

    /** Draws the options of a choice reward as clickable icons; returns the new y. */
    private int drawChoices(FontRenderer font, ChoiceReward reward, boolean claimable, int left, int inner, int cy,
        int mx, int my, boolean mouseInView) {
        List<ItemStack> choices = reward.getChoices();
        int perRow = Math.max(1, (inner - 20) / 20);
        for (int i = 0; i < choices.size(); i++) {
            int ix = left + 20 + (i % perRow) * 20;
            int iy = cy + (i / perRow) * 20;
            ItemStack stack = choices.get(i);
            boolean hover = mouseInView && GuiUtil.inside(mx, my, ix - 1, iy - 1, 18, 18);
            GuiUtil.frame(ix - 1, iy - 1, 18, 18, 0xFF1A1A24, hover && claimable ? 0xFFFFCC00 : 0xFF404860);
            GuiUtil.item(stack, ix, iy);
            if (stack.stackSize > 1) {
                String n = String.valueOf(stack.stackSize);
                font.drawStringWithShadow(n, ix + 17 - font.getStringWidth(n), iy + 9, 0xFFFFFF);
            }
            if (hover) screen.showItemTooltip(stack, mx, my);
            if (claimable) {
                int choice = i;
                areas.add(new Area(ix - 1, iy - 1, 18, 18, () -> ClientQuests.claim(reward, choice)));
            }
        }
        int rows = (choices.size() + perRow - 1) / perRow;
        if (claimable) {
            font.drawString("§e" + I18n.format("waymark.gui.pick_one"), left + 20, cy + rows * 20, 0xFFFFFF);
            cy += 10;
        }
        return cy + rows * 20 + 2;
    }

    private void drawIconRow(FontRenderer font, ItemStack icon, String text, int left, int cy, int textRight, int color,
        int mx, int my, boolean mouseInView) {
        int textX = left;
        if (icon != null) {
            GuiUtil.item(icon, left, cy);
            if (mouseInView && GuiUtil.inside(mx, my, left, cy, 16, 16)) screen.showItemTooltip(icon, mx, my);
            textX += 20;
        }
        font.drawString(GuiUtil.trim(font, text, textRight - textX), textX, cy + 5, color);
    }

    @SuppressWarnings("unchecked")
    private static List<String> wrap(FontRenderer font, String text, int width) {
        return font.listFormattedStringToWidth(text, width);
    }

    void mouseClicked(int mx, int my, int button) {
        if (!GuiUtil.inside(mx, my, x, y, w, h)) {
            screen.closeQuest();
            return;
        }
        if (button != 0) return;
        for (Area area : areas) {
            boolean clipped = area.y >= viewTop && (my < viewTop || my >= viewBottom);
            if (!clipped && GuiUtil.inside(mx, my, area.x, area.y, area.w, area.h)) {
                screen.playClick();
                area.action.run();
                return;
            }
        }
    }

    void scroll(int direction) {
        scroll += direction * 20;
    }
}
