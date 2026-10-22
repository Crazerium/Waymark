package com.crazerium.waymark.quest.task;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.EditorField;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.TaskType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Requires a number of items matching an {@link ItemFilter}, either kept in the inventory or handed in. */
public class ItemTask extends Task {

    private final ItemFilter filter = new ItemFilter();
    private long count = 1;
    private boolean consume = true;

    public ItemTask(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public TaskType<?> getType() {
        return BuiltinTypes.ITEM_TASK;
    }

    @Override
    public List<EditorField> editorFields() {
        return Arrays.asList(
            EditorField.item("item"),
            EditorField.itemList("alternatives"),
            EditorField.text("ore_dict", ""),
            EditorField.integer("count", 1),
            EditorField.bool("consume", true),
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

    public boolean isConsume() {
        return consume;
    }

    public void setConsume(boolean consume) {
        this.consume = consume;
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
        return count > 1 ? count + "x " + filter.getName() : filter.getName();
    }

    @Override
    public boolean hasSubmitButton() {
        return consume;
    }

    /** Tasks that keep the items just check the inventory; tasks that consume them wait for the submit button. */
    @Override
    public boolean isAutoDetected() {
        return !consume;
    }

    @Override
    public long detect(EntityPlayerMP player, long progress) {
        if (consume) return progress;
        long found = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (filter.matches(stack)) found += stack.stackSize;
        }
        return Math.max(progress, Math.min(found, count));
    }

    @Override
    public long submit(EntityPlayerMP player, long progress) {
        if (!consume) return detect(player, progress);
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int i = 0; i < inventory.length && progress < count; i++) {
            ItemStack stack = inventory[i];
            if (!filter.matches(stack)) continue;
            int taken = (int) Math.min(stack.stackSize, count - progress);
            stack.stackSize -= taken;
            if (stack.stackSize <= 0) inventory[i] = null;
            progress += taken;
        }
        player.inventoryContainer.detectAndSendChanges();
        return progress;
    }

    @Override
    public void writeJson(JsonObject json) {
        filter.writeJson(json);
        if (count != 1) json.addProperty("count", count);
        if (!consume) json.addProperty("consume", false);
    }

    @Override
    public void readJson(JsonObject json) {
        filter.readJson(json);
        setCount(JsonUtil.getLong(json, "count", 1));
        consume = JsonUtil.getBoolean(json, "consume", true);
    }
}
