package com.crazerium.waymark.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.crazerium.waymark.client.ClientQuests;
import com.crazerium.waymark.quest.BookEdits;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.RewardType;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.TaskType;
import com.crazerium.waymark.util.Durations;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Edits a quest's properties, tasks and rewards. Everything is edited on a JSON copy of the quest; Save sends it to
 * the server as one {@code update_quest} edit.
 */
@SideOnly(Side.CLIENT)
public class QuestEditScreen extends GuiScreen {

    private static final String[] MODES = { "all", "any", "one" };
    private static final String[] VISIBILITY = { "always", "unlocked", "completed", "never" };
    private static final int ROW = 18;
    /** Packets carry at most 32767 bytes; leave room for the rest of the message. */
    private static final int MAX_JSON = 30000;

    private final GuiScreen parent;
    private final Quest quest;
    private final JsonObject base;

    private GuiTextField title, subtitle;
    private String titleText, subtitleText;
    private TextArea description;
    private List<String> descriptionLines;
    private JsonObject icon;
    private String dependencyMode, taskMode, visibility;
    private boolean autoClaim, optional, repeatable;
    private GuiTextField cooldown;
    private String cooldownText;
    private double size;
    private final List<JsonObject> tasks = new ArrayList<>();
    private final List<JsonObject> rewards = new ArrayList<>();
    private int taskScroll, rewardScroll;

    private ItemPicker picker;
    private ContextMenu menu;
    private ItemStack tooltipStack;
    private boolean autoIconHint;
    private String error;

    private int px, py, pw, ph, listTop, listBottom;

    public QuestEditScreen(GuiScreen parent, Quest quest) {
        this.parent = parent;
        this.quest = quest;
        this.base = quest.toJson();
        titleText = JsonUtil.getString(base, "title", "");
        subtitleText = JsonUtil.getString(base, "subtitle", "");
        descriptionLines = JsonUtil.getStringList(base, "description");
        icon = JsonUtil.getObject(base, "icon");
        dependencyMode = JsonUtil.getString(base, "dependency_mode", "all");
        taskMode = JsonUtil.getString(base, "task_mode", "all");
        visibility = JsonUtil.getString(base, "visibility", "always");
        autoClaim = JsonUtil.getBoolean(base, "auto_claim", false);
        repeatable = JsonUtil.getBoolean(base, "repeatable", false);
        cooldownText = Durations.format(JsonUtil.getLong(base, "repeat_cooldown", 0));
        optional = JsonUtil.getBoolean(base, "optional", false);
        size = JsonUtil.getDouble(base, "size", 1.0);
        for (JsonElement e : JsonUtil.getArray(base, "tasks")) if (e.isJsonObject()) tasks.add(e.getAsJsonObject());
        for (JsonElement e : JsonUtil.getArray(base, "rewards")) if (e.isJsonObject()) rewards.add(e.getAsJsonObject());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (title != null) {
            titleText = title.getText();
            subtitleText = subtitle.getText();
            cooldownText = cooldown.getText();
            descriptionLines = description.getLines();
        }
        pw = Math.min(460, width - 20);
        ph = height - 20;
        px = (width - pw) / 2;
        py = 10;
        int fx = px + 70, fw = pw - 78;
        title = new GuiTextField(fontRendererObj, fx, py + 22, fw, 14);
        title.setMaxStringLength(500);
        title.setText(titleText);
        subtitle = new GuiTextField(fontRendererObj, fx, py + 40, fw, 14);
        subtitle.setMaxStringLength(500);
        subtitle.setText(subtitleText);
        int slotW = (pw - 100) / 3 - 2;
        cooldown = new GuiTextField(fontRendererObj, px + 92 + 2 * (slotW + 2), py + 90, slotW, 14);
        cooldown.setMaxStringLength(20);
        cooldown.setText(cooldownText);
        int lines = Math.max(3, Math.min(8, (ph - 246) / 14));
        description = new TextArea(fontRendererObj, fx, py + 120, fw - 6, lines, descriptionLines);
        listTop = py + 120 + description.height() + 22;
        listBottom = py + ph - 28;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        title.updateCursorCounter();
        subtitle.updateCursorCounter();
        cooldown.updateCursorCounter();
        description.tick();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---- drawing ----

    @Override
    public void drawScreen(int mx, int my, float partialTicks) {
        tooltipStack = null;
        autoIconHint = false;
        boolean blocked = picker != null || menu != null;
        int hx = blocked ? -1 : mx, hy = blocked ? -1 : my;
        drawDefaultBackground();
        GuiUtil.frame(px, py, pw, ph, 0xF0101018, 0xFF8090B0);
        fontRendererObj.drawStringWithShadow(I18n.format("waymark.edit.quest_heading"), px + 8, py + 8, 0xFFFFFF);

        label("title", py + 25);
        title.drawTextBox();
        label("subtitle", py + 43);
        subtitle.drawTextBox();

        label("icon", py + 64);
        drawSlot(icon, px + 70, py + 59, hx, hy);
        if (icon == null) drawAutoIcon(px + 70, py + 59, hx, hy);
        int bx = px + 92;
        for (Option o : options()) {
            GuiUtil.button(fontRendererObj, o.x(bx), o.y(py), o.w, 14, o.text(), hx, hy);
        }

        cooldown.drawTextBox();
        fontRendererObj.drawString(
            I18n.format("waymark.edit.cooldown"),
            px + 92 + (pw - 100) / 3 + 4,
            py + 93,
            repeatable ? 0xC0C0C0 : 0x606060);

        label("description", py + 123);
        description.draw();

        int colW = (pw - 24) / 2;
        drawList(I18n.format("waymark.gui.tasks"), tasks, px + 8, colW, taskScroll, true, hx, hy);
        drawList(I18n.format("waymark.gui.rewards"), rewards, px + 16 + colW, colW, rewardScroll, false, hx, hy);

        int by = py + ph - 22;
        if (error != null) fontRendererObj.drawString(error, px + 8, by + 4, 0xFF5555);
        GuiUtil.button(fontRendererObj, px + pw - 140, by, 64, 16, I18n.format("waymark.edit.save"), hx, hy);
        GuiUtil.button(fontRendererObj, px + pw - 72, by, 64, 16, I18n.format("gui.cancel"), hx, hy);

        if (picker != null) {
            picker.draw(fontRendererObj, width, height, mx, my);
            tooltipStack = picker.hovered();
        }
        if (menu != null) menu.draw(fontRendererObj, mx, my);
        super.drawScreen(mx, my, partialTicks);
        if (tooltipStack != null) renderToolTip(tooltipStack, mx, my);
        else if (autoIconHint) drawHoveringText(
            java.util.Collections.singletonList(I18n.format("waymark.edit.auto_icon")),
            mx,
            my,
            fontRendererObj);
    }

    private void label(String key, int y) {
        fontRendererObj.drawString(ObjectEditScreen.label(key), px + 8, y, 0xC0C0C0);
    }

    /**
     * With no icon chosen the quest shows its first task's icon. Draw that one dimmed, so it is clear there is an
     * icon and where it comes from; picking an item replaces it.
     */
    private void drawAutoIcon(int x, int y, int mx, int my) {
        ItemStack auto = null;
        for (JsonObject task : tasks) {
            auto = icon(preview(task, true));
            if (auto != null) break;
        }
        if (auto == null) return;
        GuiUtil.item(auto, x, y);
        GuiUtil.rect(x + 1, y + 1, 15, 15, 0x90101018);
        if (GuiUtil.inside(mx, my, x, y, 17, 17)) {
            tooltipStack = null;
            autoIconHint = true;
        }
    }

    private void drawSlot(JsonObject json, int x, int y, int mx, int my) {
        boolean hover = GuiUtil.inside(mx, my, x, y, 17, 17);
        GuiUtil.frame(x, y, 17, 17, hover ? 0xFF404058 : 0xFF202030, 0xFF404860);
        ItemStack stack = JsonUtil.readStack(json);
        if (stack == null) return;
        GuiUtil.item(stack, x, y);
        if (hover) tooltipStack = stack;
    }

    private void drawList(String heading, List<JsonObject> list, int x, int w, int scroll, boolean isTasks, int mx,
        int my) {
        fontRendererObj.drawString("§e" + heading, x, listTop - 14, 0xFFFFFF);
        GuiUtil.button(fontRendererObj, x + w - 50, listTop - 17, 50, 14, I18n.format("waymark.edit.add"), mx, my);
        GuiUtil.frame(x, listTop, w, listBottom - listTop, 0xFF080810, 0xFF404860);
        GuiUtil.scissor(x + 1, listTop + 1, w - 2, listBottom - listTop - 2);
        for (int i = 0; i < list.size(); i++) {
            int y = listTop + 2 + i * ROW - scroll;
            if (y + ROW < listTop || y > listBottom) continue;
            JsonObject json = list.get(i);
            Object preview = preview(json, isTasks);
            ItemStack stack = icon(preview);
            boolean inList = my >= listTop && my < listBottom;
            if (stack != null) {
                GuiUtil.item(stack, x + 2, y);
                if (inList && GuiUtil.inside(mx, my, x + 2, y, 16, 16)) tooltipStack = stack;
            }
            String text = describe(json, preview, isTasks);
            fontRendererObj.drawString(GuiUtil.trim(fontRendererObj, text, w - 60), x + 21, y + 5, 0xFFFFFF);
            GuiUtil.button(fontRendererObj, x + w - 34, y + 2, 14, 13, "✎", inList ? mx : -1, my);
            GuiUtil.button(fontRendererObj, x + w - 18, y + 2, 14, 13, "§cx", inList ? mx : -1, my);
        }
        GuiUtil.endScissor();
    }

    /** A detached task or reward built from its JSON, or null if its type is unknown. */
    private static Object preview(JsonObject json, boolean isTask) {
        try {
            String type = JsonUtil.getString(json, "type", "");
            if (isTask) {
                TaskType<?> t = TaskType.get(type);
                return t == null ? null : t.preview(json);
            }
            RewardType<?> r = RewardType.get(type);
            return r == null ? null : r.preview(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ItemStack icon(Object preview) {
        try {
            if (preview instanceof Task) return ((Task) preview).getIcon();
            if (preview instanceof Reward) return ((Reward) preview).getIcon();
        } catch (RuntimeException e) {
            // Detached previews have no quest book; such icons are simply not shown.
        }
        return null;
    }

    private static String describe(JsonObject json, Object preview, boolean isTask) {
        String custom = JsonUtil.getString(json, "title", "");
        if (isTask && !custom.isEmpty()) return custom;
        try {
            if (preview instanceof Task) return ((Task) preview).getTitle();
            if (preview instanceof Reward) return ((Reward) preview).getTitle();
        } catch (RuntimeException e) {
            // Some titles need the quest book (e.g. a quest to complete); fall back to the type name.
        }
        return typeName(JsonUtil.getString(json, "type", "?"), isTask);
    }

    static String typeName(String id, boolean isTask) {
        String k = "waymark." + (isTask ? "task_type." : "reward_type.") + id;
        String s = I18n.format(k);
        return s.equals(k) ? id : s;
    }

    // ---- option buttons ----

    /** A button in the options area, laid out in rows next to the icon. */
    private final class Option {

        final int w, row, offset;
        final java.util.function.Supplier<String> text;
        final Runnable action;

        Option(int row, int offset, int w, java.util.function.Supplier<String> text, Runnable action) {
            this.row = row;
            this.offset = offset;
            this.w = w;
            this.text = text;
            this.action = action;
        }

        int x(int left) {
            return left + offset;
        }

        int y(int top) {
            return top + 58 + row * 16;
        }

        String text() {
            return text.get();
        }
    }

    private List<Option> options() {
        int w = (pw - 100) / 3 - 2;
        return Arrays
            .asList(
                new Option(
                    0,
                    0,
                    w,
                    () -> I18n
                        .format("waymark.edit.dependency_mode", I18n.format("waymark.edit.mode." + dependencyMode)),
                    () -> dependencyMode = next(MODES, dependencyMode)),
                new Option(
                    0,
                    w + 2,
                    w,
                    () -> I18n.format("waymark.edit.task_mode", I18n.format("waymark.edit.mode." + taskMode)),
                    () -> taskMode = next(MODES, taskMode)),
                new Option(
                    0,
                    2 * (w + 2),
                    w,
                    () -> I18n.format("waymark.edit.visibility", I18n.format("waymark.edit.visibility." + visibility)),
                    () -> visibility = next(VISIBILITY, visibility)),
                new Option(
                    1,
                    0,
                    w,
                    () -> I18n.format("waymark.edit.auto_claim", yesNo(autoClaim)),
                    () -> autoClaim = !autoClaim),
                new Option(
                    1,
                    w + 2,
                    w,
                    () -> I18n.format("waymark.edit.optional", yesNo(optional)),
                    () -> optional = !optional),
                new Option(
                    2,
                    0,
                    w,
                    () -> I18n.format("waymark.edit.repeatable", yesNo(repeatable)),
                    () -> repeatable = !repeatable),
                new Option(
                    1,
                    2 * (w + 2),
                    w,
                    () -> I18n.format("waymark.edit.size", size),
                    () -> size = size >= 3 ? 0.5 : size + 0.25));
    }

    private static String yesNo(boolean b) {
        return I18n.format(b ? "waymark.edit.yes" : "waymark.edit.no");
    }

    private static String next(String[] values, String current) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) return values[(i + 1) % values.length];
        return values[0];
    }

    // ---- input ----

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (picker != null) {
            if (picker.mouseClicked(mx, my)) picker = null;
            return;
        }
        if (menu != null) {
            if (!menu.mouseClicked(mx, my)) menu = null;
            return;
        }
        error = null;
        title.mouseClicked(mx, my, button);
        subtitle.mouseClicked(mx, my, button);
        cooldown.mouseClicked(mx, my, button);
        description.mouseClicked(mx, my, button);

        if (GuiUtil.inside(mx, my, px + 70, py + 59, 17, 17)) {
            if (button == 1) icon = null;
            else picker = new ItemPicker(stack -> {
                if (stack != null) stack.stackSize = 1;
                icon = stack == null ? null : JsonUtil.writeStack(stack);
            }, true);
            return;
        }
        int bx = px + 92;
        for (Option o : options()) {
            if (GuiUtil.inside(mx, my, o.x(bx), o.y(py), o.w, 14)) {
                o.action.run();
                return;
            }
        }

        int colW = (pw - 24) / 2;
        if (listClicked(tasks, px + 8, colW, taskScroll, true, mx, my)) return;
        if (listClicked(rewards, px + 16 + colW, colW, rewardScroll, false, mx, my)) return;

        int by = py + ph - 22;
        if (GuiUtil.inside(mx, my, px + pw - 140, by, 64, 16)) save();
        else if (GuiUtil.inside(mx, my, px + pw - 72, by, 64, 16)) mc.displayGuiScreen(parent);
    }

    private boolean listClicked(List<JsonObject> list, int x, int w, int scroll, boolean isTasks, int mx, int my) {
        if (GuiUtil.inside(mx, my, x + w - 50, listTop - 17, 50, 14)) {
            menu = ObjectEditScreen.typeMenu(this, mx, my, isTasks, list::add);
            return true;
        }
        if (!GuiUtil.inside(mx, my, x, listTop, w, listBottom - listTop)) return false;
        int index = (my - listTop - 2 + scroll) / ROW;
        if (index < 0 || index >= list.size()) return true;
        int y = listTop + 2 + index * ROW - scroll;
        if (GuiUtil.inside(mx, my, x + w - 18, y + 2, 14, 13)) {
            list.remove(index);
        } else {
            JsonObject json = list.get(index);
            openObjectEditor(json, isTasks, edited -> list.set(list.indexOf(json), edited));
        }
        return true;
    }

    private void openObjectEditor(JsonObject json, boolean isTask, java.util.function.Consumer<JsonObject> onSave) {
        mc.displayGuiScreen(ObjectEditScreen.create(this, json, isTask, onSave));
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && picker != null) {
            picker.scroll(wheel > 0 ? -1 : 1);
            return;
        }
        if (wheel == 0 || picker != null || menu != null) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int step = wheel > 0 ? -ROW : ROW;
        int colW = (pw - 24) / 2;
        if (my >= listTop && my < listBottom) {
            if (mx < px + 8 + colW) taskScroll = clampScroll(taskScroll + step, tasks.size());
            else rewardScroll = clampScroll(rewardScroll + step, rewards.size());
        } else if (GuiUtil.inside(mx, my, px, py + 120, pw, description.height())) {
            description.scroll(wheel > 0 ? -1 : 1);
        }
    }

    private int clampScroll(int scroll, int rows) {
        return Math.max(0, Math.min(scroll, rows * ROW - (listBottom - listTop - 4)));
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
            else if (menu != null) menu = null;
            else mc.displayGuiScreen(parent);
            return;
        }
        if (description.keyTyped(c, key)) return;
        title.textboxKeyTyped(c, key);
        subtitle.textboxKeyTyped(c, key);
        cooldown.textboxKeyTyped(c, key);
    }

    private void save() {
        JsonObject data = new JsonObject();
        for (Map.Entry<String, JsonElement> e : base.entrySet()) data.add(e.getKey(), e.getValue());
        put(data, "title", title.getText(), "");
        put(data, "subtitle", subtitle.getText(), "");
        List<String> lines = description.getLines();
        data.remove("description");
        if (!lines.isEmpty()) data.add("description", JsonUtil.toArray(lines));
        data.remove("icon");
        if (icon != null) data.add("icon", icon);
        put(data, "dependency_mode", dependencyMode, "all");
        put(data, "task_mode", taskMode, "all");
        put(data, "visibility", visibility, "always");
        data.remove("auto_claim");
        if (autoClaim) data.addProperty("auto_claim", true);
        data.remove("optional");
        if (optional) data.addProperty("optional", true);
        data.remove("repeatable");
        data.remove("repeat_cooldown");
        if (repeatable) {
            long seconds = Durations.parse(cooldown.getText());
            if (seconds < 0) {
                error = I18n.format("waymark.edit.bad_cooldown");
                return;
            }
            data.addProperty("repeatable", true);
            if (seconds > 0) data.addProperty("repeat_cooldown", seconds);
        }
        data.remove("size");
        if (size != 1.0) data.addProperty("size", size);
        JsonArray taskArray = new JsonArray();
        for (JsonObject t : tasks) taskArray.add(t);
        data.add("tasks", taskArray);
        JsonArray rewardArray = new JsonArray();
        for (JsonObject r : rewards) rewardArray.add(r);
        data.remove("rewards");
        if (rewardArray.size() > 0) data.add("rewards", rewardArray);

        JsonObject op = BookEdits.updateQuest(quest, data);
        if (op.toString()
            .getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON) {
            error = I18n.format("waymark.edit.too_big");
            return;
        }
        ClientQuests.sendEdit(op);
        mc.displayGuiScreen(parent);
    }

    private static void put(JsonObject data, String key, String value, String def) {
        data.remove(key);
        if (!value.equals(def)) data.addProperty(key, value);
    }
}
