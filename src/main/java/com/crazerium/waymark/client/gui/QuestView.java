package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.crazerium.waymark.client.ClientQuests;
import com.crazerium.waymark.client.QuestState;
import com.crazerium.waymark.progress.TeamProgress;
import com.crazerium.waymark.quest.BookEdits;
import com.crazerium.waymark.quest.LogicMode;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.reward.ChoiceReward;
import com.crazerium.waymark.util.Ids;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The window that opens over the map when a quest is clicked. Tasks and rewards are two columns of icons: clicking a
 * task submits it, clicking a reward claims it. In edit mode the same window edits the quest: "+" adds tasks and
 * rewards, right-click edits or removes one, and the Edit menu (or its keys) changes the texts.
 */
@SideOnly(Side.CLIENT)
class QuestView {

    private static final int CELL = 20;
    private static final int TASK_COLOR = 0x6670FF;
    private static final int REWARD_COLOR = 0xFFAA22;

    private static final class Area {

        final int x, y, w, h;
        final Runnable left, right;

        Area(int x, int y, int w, int h, Runnable left, Runnable right) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.left = left;
            this.right = right;
        }
    }

    final Quest quest;
    private final QuestBookScreen screen;
    private final List<Area> areas = new ArrayList<>();
    /** Clickable options of the open choice reward; while it is open only these respond. */
    private final List<Area> choiceAreas = new ArrayList<>();
    private int x, y, w, h;
    private int scroll, contentHeight, viewTop, viewBottom;
    /** A choice reward whose options are shown for the player to pick from. */
    private ChoiceReward choosing;

    QuestView(QuestBookScreen screen, Quest quest) {
        this.screen = screen;
        this.quest = quest;
        layout();
    }

    void layout() {
        w = Math.min(320, screen.width - 40);
        h = Math.min(280, screen.height - 40);
        x = (screen.width - w) / 2;
        y = (screen.height - h) / 2;
    }

    // ---- drawing ----

    void draw(int mx, int my) {
        areas.clear();
        choiceAreas.clear();
        FontRenderer font = screen.font();
        TeamProgress progress = ClientQuests.getProgress();
        boolean editing = QuestBookScreen.isEditing();

        GuiUtil.rect(0, 0, screen.width, screen.height, 0x80000000);
        GuiUtil.frame(x, y, w, h, 0xF0101018, QuestState.of(quest).color);

        // Header: icon, centred title, close button.
        GuiUtil.item(quest.getIcon(), x + 4, y + 3);
        String title = GuiUtil.trim(font, GuiUtil.colorCodes(quest.getDisplayTitle()), w - 50);
        font.drawStringWithShadow(title, x + (w - font.getStringWidth(title)) / 2, y + 7, 0xFFFFFF);
        int closeX = x + w - 16;
        GuiUtil.button(font, closeX, y + 4, 12, 12, "§cx", mx, my);
        areas.add(new Area(closeX, y + 4, 12, 12, screen::closeQuest, null));
        GuiUtil.rect(x + 1, y + 21, w - 2, 1, 0xFF404860);

        // Task and reward columns.
        int colW = (w - 2) / 2;
        int top = y + 24;
        font.drawString(
            I18n.format("waymark.gui.tasks"),
            x + (colW - font.getStringWidth(I18n.format("waymark.gui.tasks"))) / 2,
            top,
            TASK_COLOR);
        String rewardsLabel = I18n.format("waymark.gui.rewards");
        font.drawString(rewardsLabel, x + colW + (colW - font.getStringWidth(rewardsLabel)) / 2, top, REWARD_COLOR);
        int taskBottom = drawTasks(font, progress, editing, x + 1, top + 12, colW, mx, my);
        int rewardBottom = drawRewards(font, progress, editing, x + 1 + colW, top + 12, colW, mx, my);
        int columnsBottom = Math.max(taskBottom, rewardBottom) + 4;
        GuiUtil.rect(x + colW, top - 2, 1, columnsBottom - top + 2, 0xFF404860);
        GuiUtil.rect(x + 1, columnsBottom, w - 2, 1, 0xFF404860);

        // Subtitle and, in edit mode, the Edit menu button.
        int cy = columnsBottom + 5;
        String subtitle = quest.getSubtitle();
        if (!subtitle.isEmpty() || editing) {
            String text = subtitle.isEmpty() ? "§8§o" + I18n.format("waymark.edit.no_subtitle")
                : "§7§o" + GuiUtil.colorCodes(subtitle);
            for (String line : wrap(font, text, w - 16)) {
                font.drawString(line, x + (w - font.getStringWidth(line)) / 2, cy, 0xAAAAAA);
                cy += 10;
            }
        }
        if (editing) {
            String label = "✎ " + I18n.format("waymark.edit.menu") + " ▼";
            int bw = font.getStringWidth(label) + 12, bx = x + (w - bw) / 2;
            GuiUtil.button(font, bx, cy + 1, bw, 14, label, mx, my);
            int menuY = cy + 16;
            areas.add(new Area(bx, cy + 1, bw, 14, () -> openEditMenu(bx, menuY), null));
            cy += 18;
        }
        cy += 2;

        // Scrolling part: state lines and the description.
        viewTop = cy;
        viewBottom = y + h - 4;
        scroll = Math.max(0, Math.min(scroll, contentHeight - (viewBottom - viewTop)));
        int left = x + 8, inner = w - 16;
        int ty = viewTop - scroll;
        GuiUtil.scissor(x + 1, viewTop, w - 2, viewBottom - viewTop);

        String repeat = QuestState.repeatInfo(quest);
        if (repeat != null) {
            font.drawString("§b" + repeat, left, ty, 0xFFFFFF);
            ty += 12;
        }
        if (progress != null && !progress.isUnlocked(quest)) {
            StringBuilder deps = new StringBuilder();
            for (Quest dep : quest.getDependencies()) {
                if (progress.hasEverCompleted(dep)) continue;
                if (deps.length() > 0) deps.append(", ");
                deps.append(GuiUtil.colorCodes(dep.getDisplayTitle()));
            }
            for (String line : wrap(font, "§c" + I18n.format("waymark.gui.requires", deps), inner)) {
                font.drawString(line, left, ty, 0xFF5555);
                ty += 10;
            }
            ty += 2;
        }
        for (String paragraph : quest.getDescription()) {
            if (paragraph.isEmpty()) {
                ty += 6;
                continue;
            }
            for (String line : wrap(font, GuiUtil.colorCodes(paragraph), inner)) {
                font.drawString(line, left, ty, 0xFFFFFF);
                ty += 10;
            }
        }
        GuiUtil.endScissor();
        contentHeight = ty + scroll - viewTop;
        int viewHeight = viewBottom - viewTop;
        if (contentHeight > viewHeight && viewHeight > 0) {
            int barH = Math.max(10, viewHeight * viewHeight / contentHeight);
            int barY = viewTop + (viewHeight - barH) * scroll / (contentHeight - viewHeight);
            GuiUtil.rect(x + w - 4, barY, 2, barH, 0xFF8090B0);
        }

        if (choosing != null) drawChoices(font, mx, my);
    }

    /** Lays icons out in centred rows inside a column; returns the y below the last row. */
    private static int[] cell(int index, int count, int colX, int colW, int top) {
        int perRow = Math.max(1, (colW - 8) / CELL);
        int row = index / perRow;
        int inRow = Math.min(perRow, count - row * perRow);
        int startX = colX + (colW - inRow * CELL) / 2;
        return new int[] { startX + (index % perRow) * CELL + 2, top + row * CELL + 2 };
    }

    private static int rowsBottom(int count, int colW, int top) {
        int perRow = Math.max(1, (colW - 8) / CELL);
        return top + Math.max(1, (count + perRow - 1) / perRow) * CELL;
    }

    private int drawTasks(FontRenderer font, TeamProgress progress, boolean editing, int colX, int top, int colW,
        int mx, int my) {
        List<Task> tasks = quest.getTasks();
        int count = tasks.size() + (editing ? 1 : 0);
        boolean canProgress = progress != null && progress.canProgress(quest);
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            int[] p = cell(i, count, colX, colW, top);
            long value = progress != null ? progress.getProgress(task) : 0;
            boolean done = progress != null && (progress.isComplete(task) || progress.isCompleted(quest));
            boolean submittable = canProgress && !done && task.hasSubmitButton();
            boolean hover = GuiUtil.inside(mx, my, p[0] - 1, p[1] - 1, 18, 18);
            if (hover || submittable) GuiUtil
                .frame(p[0] - 1, p[1] - 1, 18, 18, hover ? 0x40FFFFFF : 0, submittable ? 0xFF6670FF : 0xFF808080);
            ItemStack icon = task.getIcon();
            if (icon != null) GuiUtil.item(icon, p[0], p[1]);
            else font.drawString("?", p[0] + 5, p[1] + 4, 0xFFFFFF);
            if (task.isOptional() && !done) GuiUtil.rect(p[0], p[1], 16, 16, 0x60000000);
            if (done) font.drawStringWithShadow("§a✔", p[0] + 10, p[1] + 9, 0xFFFFFF);
            else if (task.getMaxProgress() > 1)
                smallText(font, compact(value) + "/" + compact(task.getMaxProgress()), p[0] + 17, p[1] + 12);

            if (hover) {
                List<String> lines = new ArrayList<>();
                lines.add(task.getDisplayTitle());
                if (task.isOptional()) lines.add("§8" + I18n.format("waymark.gui.optional"));
                if (task.getMaxProgress() > 1) lines.add("§7" + value + " / " + task.getMaxProgress());
                if (done) lines.add("§a" + I18n.format("waymark.gui.completed"));
                else if (submittable) lines.add("§e" + I18n.format("waymark.gui.click_submit"));
                String hint = QuestBookScreen.recipeHint();
                if (hint != null && icon != null) lines.add(hint);
                screen.setHoveredStack(icon);
                if (editing) lines.add("§8" + I18n.format("waymark.edit.right_click_edit"));
                screen.showTooltip(lines, mx, my);
            }
            areas.add(
                new Area(
                    p[0] - 1,
                    p[1] - 1,
                    18,
                    18,
                    submittable ? () -> ClientQuests.submit(task) : null,
                    editing ? () -> objectMenu(task.getId(), true) : null));
        }
        if (editing) addButton(font, cell(tasks.size(), count, colX, colW, top), true, mx, my);
        if (quest.getTaskMode() != LogicMode.ALL) {
            String mode = "§7" + I18n.format(
                "waymark.gui.task_mode." + quest.getTaskMode()
                    .key());
            int bottom = rowsBottom(count, colW, top);
            font.drawString(mode, colX + (colW - font.getStringWidth(mode)) / 2, bottom + 1, 0xFFFFFF);
            return bottom + 10;
        }
        return rowsBottom(count, colW, top);
    }

    private int drawRewards(FontRenderer font, TeamProgress progress, boolean editing, int colX, int top, int colW,
        int mx, int my) {
        List<Reward> rewards = quest.getRewards();
        int count = rewards.size() + (editing ? 1 : 0);
        UUID player = ClientQuests.playerId();
        boolean completed = progress != null && progress.isCompleted(quest);
        for (int i = 0; i < rewards.size(); i++) {
            Reward reward = rewards.get(i);
            int[] p = cell(i, count, colX, colW, top);
            boolean claimed = progress != null && progress.isClaimed(player, reward);
            boolean claimable = completed && !claimed;
            boolean hover = GuiUtil.inside(mx, my, p[0] - 1, p[1] - 1, 18, 18);
            if (claimable) {
                float pulse = (float) (0.6 + 0.4 * Math.sin(System.currentTimeMillis() / 200.0));
                int gold = 0xFF000000 | ((int) (0xFF * pulse) << 16) | ((int) (0xAA * pulse) << 8);
                GuiUtil.frame(p[0] - 1, p[1] - 1, 18, 18, hover ? 0x40FFFFFF : 0, gold);
            } else if (hover) GuiUtil.frame(p[0] - 1, p[1] - 1, 18, 18, 0x40FFFFFF, 0xFF808080);
            ItemStack icon = reward.getIcon();
            if (icon != null) GuiUtil.item(icon, p[0], p[1]);
            if (claimed) font.drawStringWithShadow("§a✔", p[0] + 10, p[1] + 9, 0xFFFFFF);
            else if (icon != null && icon.stackSize > 1) smallText(font, compact(icon.stackSize), p[0] + 17, p[1] + 12);

            if (hover) {
                List<String> lines = new ArrayList<>();
                lines.add(reward.getTitle());
                if (reward.isTeamReward()) lines.add("§d" + I18n.format("waymark.gui.team_reward"));
                if (claimed) lines.add("§a" + I18n.format("waymark.gui.claimed"));
                else if (claimable) lines.add("§e" + I18n.format("waymark.gui.click_claim"));
                String hint = QuestBookScreen.recipeHint();
                if (hint != null && icon != null) lines.add(hint);
                screen.setHoveredStack(icon);
                if (editing) lines.add("§8" + I18n.format("waymark.edit.right_click_edit"));
                screen.showTooltip(lines, mx, my);
            }
            Runnable claim = !claimable ? null
                : reward instanceof ChoiceReward ? () -> choosing = (ChoiceReward) reward
                    : () -> ClientQuests.claim(reward, -1);
            areas.add(
                new Area(p[0] - 1, p[1] - 1, 18, 18, claim, editing ? () -> objectMenu(reward.getId(), false) : null));
        }
        if (editing) addButton(font, cell(rewards.size(), count, colX, colW, top), false, mx, my);
        return rowsBottom(count, colW, top);
    }

    private void addButton(FontRenderer font, int[] p, boolean isTask, int mx, int my) {
        boolean hover = GuiUtil.inside(mx, my, p[0] - 1, p[1] - 1, 18, 18);
        GuiUtil.frame(p[0] - 1, p[1] - 1, 18, 18, hover ? 0x40FFFFFF : 0x20FFFFFF, 0xFF55AA55);
        font.drawStringWithShadow("§a+", p[0] + 5, p[1] + 4, 0xFFFFFF);
        if (hover) screen.showTooltip(
            java.util.Collections
                .singletonList(I18n.format(isTask ? "waymark.edit.add_task" : "waymark.edit.add_reward")),
            mx,
            my);
        areas.add(new Area(p[0] - 1, p[1] - 1, 18, 18, () -> openTypeMenu(p[0], p[1] + 18, isTask), null));
    }

    private void drawChoices(FontRenderer font, int mx, int my) {
        List<ItemStack> choices = choosing.getChoices();
        int cw = Math.min(9, choices.size()) * CELL + 12;
        int ch = ((choices.size() + 8) / 9) * CELL + 26;
        int cx = x + (w - cw) / 2, cyTop = y + (h - ch) / 2;
        GuiUtil.frame(cx, cyTop, cw, ch, 0xF8181820, 0xFFFFAA22);
        font.drawString(I18n.format("waymark.gui.pick_one"), cx + 6, cyTop + 6, 0xFFCC66);
        for (int i = 0; i < choices.size(); i++) {
            int ix = cx + 6 + (i % 9) * CELL, iy = cyTop + 18 + (i / 9) * CELL;
            ItemStack stack = choices.get(i);
            boolean hover = GuiUtil.inside(mx, my, ix - 1, iy - 1, 18, 18);
            GuiUtil.frame(ix - 1, iy - 1, 18, 18, hover ? 0x40FFFFFF : 0xFF202030, hover ? 0xFFFFCC00 : 0xFF404860);
            GuiUtil.item(stack, ix, iy);
            if (stack.stackSize > 1) smallText(font, compact(stack.stackSize), ix + 17, iy + 12);
            if (hover) screen.showItemTooltip(stack, mx, my);
            int choice = i;
            ChoiceReward reward = choosing;
            choiceAreas.add(new Area(ix - 1, iy - 1, 18, 18, () -> {
                ClientQuests.claim(reward, choice);
                choosing = null;
            }, null));
        }
    }

    private static void smallText(FontRenderer font, String text, int right, int y) {
        GL11.glPushMatrix();
        GL11.glTranslatef(right, y, 0);
        GL11.glScalef(0.5f, 0.5f, 1);
        font.drawStringWithShadow(text, -font.getStringWidth(text), 0, 0xFFFFFF);
        GL11.glPopMatrix();
    }

    private static String compact(long n) {
        if (n >= 1_000_000) return n / 1_000_000 + "M";
        if (n >= 10_000) return n / 1000 + "K";
        return String.valueOf(n);
    }

    @SuppressWarnings("unchecked")
    private static List<String> wrap(FontRenderer font, String text, int width) {
        return font.listFormattedStringToWidth(text, width);
    }

    // ---- editing ----

    /** Sends the quest back to the server with {@code change} applied to its JSON. */
    private void change(Consumer<JsonObject> change) {
        JsonObject data = quest.toJson();
        change.accept(data);
        ClientQuests.sendEdit(BookEdits.updateQuest(quest, data));
    }

    private void openTypeMenu(int mx, int my, boolean isTask) {
        String list = isTask ? "tasks" : "rewards";
        screen.openMenu(ObjectEditScreen.typeMenu(screen, mx, my, isTask, created -> change(data -> {
            JsonArray array = JsonUtil.getArray(data, list);
            array.add(created);
            data.add(list, array);
        })));
    }

    /** Right-click menu of a task or reward in edit mode. */
    private void objectMenu(long id, boolean isTask) {
        String list = isTask ? "tasks" : "rewards";
        JsonObject json = null;
        for (JsonElement e : JsonUtil.getArray(quest.toJson(), list)) {
            if (Ids.parse(JsonUtil.getString(e.getAsJsonObject(), "id", "")) == id) json = e.getAsJsonObject();
        }
        if (json == null) return;
        JsonObject original = json;
        ContextMenu menu = new ContextMenu(lastMouseX, lastMouseY);
        menu.add(
            I18n.format("waymark.edit.edit"),
            () -> Minecraft.getMinecraft()
                .displayGuiScreen(
                    ObjectEditScreen.create(screen, original, isTask, edited -> replace(list, id, edited))));
        menu.addDangerous(
            I18n.format("waymark.edit.remove"),
            I18n.format("waymark.edit.confirm"),
            () -> replace(list, id, null));
        screen.openMenu(menu);
    }

    /** Replaces (or with null removes) the task or reward with this id. */
    private void replace(String list, long id, JsonObject with) {
        change(data -> {
            JsonArray out = new JsonArray();
            for (JsonElement e : JsonUtil.getArray(data, list)) {
                boolean match = Ids.parse(JsonUtil.getString(e.getAsJsonObject(), "id", "")) == id;
                if (!match) out.add(e);
                else if (with != null) out.add(with);
            }
            data.add(list, out);
        });
    }

    private void openEditMenu(int mx, int my) {
        ContextMenu menu = new ContextMenu(mx, my);
        menu.add(I18n.format("waymark.edit.menu_title") + " §8[T]", this::editTitle);
        menu.add(I18n.format("waymark.edit.menu_subtitle") + " §8[S]", this::editSubtitle);
        menu.add(I18n.format("waymark.edit.menu_description") + " §8[D]", this::editDescription);
        menu.add(I18n.format("waymark.edit.menu_add_line") + " §8[L]", this::addLine);
        menu.add(I18n.format("waymark.edit.menu_properties") + " §8[Q]", () -> screen.editQuest(quest));
        screen.openMenu(menu);
    }

    private void editTitle() {
        screen.openPrompt(
            I18n.format("waymark.edit.menu_title"),
            quest.getTitle(),
            text -> change(data -> putText(data, "title", text)));
    }

    private void editSubtitle() {
        screen.openPrompt(
            I18n.format("waymark.edit.menu_subtitle"),
            quest.getSubtitle(),
            text -> change(data -> putText(data, "subtitle", text)));
    }

    private void addLine() {
        screen.openPrompt(I18n.format("waymark.edit.menu_add_line"), "", text -> change(data -> {
            JsonArray lines = JsonUtil.getArray(data, "description");
            lines.add(new com.google.gson.JsonPrimitive(text));
            data.add("description", lines);
        }));
    }

    private void editDescription() {
        Minecraft.getMinecraft()
            .displayGuiScreen(new DescriptionEditScreen(screen, quest.getDescription(), lines -> change(data -> {
                data.remove("description");
                if (!lines.isEmpty()) data.add("description", JsonUtil.toArray(lines));
            })));
    }

    private static void putText(JsonObject data, String key, String text) {
        data.remove(key);
        if (!text.isEmpty()) data.addProperty(key, text);
    }

    /** Edit-mode keyboard shortcuts while the window is open; returns true if the key was used. */
    boolean shortcut(int key) {
        switch (key) {
            case Keyboard.KEY_T:
                editTitle();
                return true;
            case Keyboard.KEY_S:
                editSubtitle();
                return true;
            case Keyboard.KEY_D:
                editDescription();
                return true;
            case Keyboard.KEY_L:
                addLine();
                return true;
            case Keyboard.KEY_Q:
                screen.editQuest(quest);
                return true;
            default:
                return false;
        }
    }

    // ---- input ----

    private int lastMouseX, lastMouseY;

    void mouseClicked(int mx, int my, int button) {
        lastMouseX = mx;
        lastMouseY = my;
        if (choosing != null) {
            for (Area area : choiceAreas) {
                if (area.left != null && GuiUtil.inside(mx, my, area.x, area.y, area.w, area.h)) {
                    screen.playClick();
                    area.left.run();
                    return;
                }
            }
            choosing = null;
            return;
        }
        if (!GuiUtil.inside(mx, my, x, y, w, h)) {
            screen.closeQuest();
            return;
        }
        for (Area area : areas) {
            if (!GuiUtil.inside(mx, my, area.x, area.y, area.w, area.h)) continue;
            Runnable action = button == 1 ? area.right : button == 0 ? area.left : null;
            if (action != null) {
                screen.playClick();
                action.run();
            }
            return;
        }
    }

    void scroll(int direction) {
        scroll += direction * 20;
    }
}
