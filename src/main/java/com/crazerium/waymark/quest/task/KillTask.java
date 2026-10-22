package com.crazerium.waymark.quest.task;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.EditorField;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.TaskType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Kill a number of entities, identified by their entity name (e.g. "Zombie", "witchery.demon"). */
public class KillTask extends Task {

    private String entity = "Zombie";
    private long count = 1;
    private boolean subtypes;

    public KillTask(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public TaskType<?> getType() {
        return BuiltinTypes.KILL_TASK;
    }

    @Override
    public List<EditorField> editorFields() {
        return Arrays.asList(
            EditorField.text("entity", "Zombie"),
            EditorField.integer("count", 1),
            EditorField.bool("subtypes", false));
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = Math.max(1, count);
    }

    /** If true, entities that extend the target class count too. */
    public boolean isSubtypes() {
        return subtypes;
    }

    public void setSubtypes(boolean subtypes) {
        this.subtypes = subtypes;
    }

    @Override
    public long getMaxProgress() {
        return count;
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.iron_sword);
    }

    @Override
    public String getTitle() {
        String key = "entity." + entity + ".name";
        String name = StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : entity;
        return StatCollector.translateToLocalFormatted("waymark.task.kill", count, name);
    }

    public boolean matches(Entity victim) {
        if (entity.equals(EntityList.getEntityString(victim))) return true;
        if (!subtypes) return false;
        Class<?> target = (Class<?>) EntityList.stringToClassMapping.get(entity);
        return target != null && target.isInstance(victim);
    }

    @Override
    public void writeJson(JsonObject json) {
        json.addProperty("entity", entity);
        if (count != 1) json.addProperty("count", count);
        if (subtypes) json.addProperty("subtypes", true);
    }

    @Override
    public void readJson(JsonObject json) {
        entity = JsonUtil.getString(json, "entity", "Zombie");
        setCount(JsonUtil.getLong(json, "count", 1));
        subtypes = JsonUtil.getBoolean(json, "subtypes", false);
    }
}
