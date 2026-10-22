package com.crazerium.waymark.quest.task;

import java.util.Arrays;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.EditorField;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.TaskType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Counts items the player crafts and/or smelts. Progress is added by the server's crafting events. */
public class CraftTask extends Task {

    private final ItemFilter filter = new ItemFilter();
    private long count = 1;
    private boolean crafting = true;
    private boolean smelting = true;

    public CraftTask(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public TaskType<?> getType() {
        return BuiltinTypes.CRAFT_TASK;
    }

    @Override
    public List<EditorField> editorFields() {
        return Arrays.asList(
            EditorField.item("item"),
            EditorField.itemList("alternatives"),
            EditorField.text("ore_dict", ""),
            EditorField.integer("count", 1),
            EditorField.bool("crafting", true),
            EditorField.bool("smelting", true),
            EditorField.bool("match_nbt", false));
    }

    public ItemFilter getFilter() {
        return filter;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = Math.max(1, count);
    }

    public boolean countsCrafting() {
        return crafting;
    }

    public void setCrafting(boolean crafting) {
        this.crafting = crafting;
    }

    public boolean countsSmelting() {
        return smelting;
    }

    public void setSmelting(boolean smelting) {
        this.smelting = smelting;
    }

    @Override
    public long getMaxProgress() {
        return count;
    }

    @Override
    public ItemStack getIcon() {
        return filter.getIcon();
    }

    @Override
    public String getTitle() {
        String name = count > 1 ? count + "x " + filter.getName() : filter.getName();
        return StatCollector.translateToLocalFormatted("waymark.task.craft", name);
    }

    @Override
    public void writeJson(JsonObject json) {
        filter.writeJson(json);
        if (count != 1) json.addProperty("count", count);
        if (!crafting) json.addProperty("crafting", false);
        if (!smelting) json.addProperty("smelting", false);
    }

    @Override
    public void readJson(JsonObject json) {
        filter.readJson(json);
        setCount(JsonUtil.getLong(json, "count", 1));
        crafting = JsonUtil.getBoolean(json, "crafting", true);
        smelting = JsonUtil.getBoolean(json, "smelting", true);
    }
}
