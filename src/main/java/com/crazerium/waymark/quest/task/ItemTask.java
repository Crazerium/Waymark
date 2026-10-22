package com.crazerium.waymark.quest.task;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.TaskType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/**
 * Requires a number of matching items. Matches either a specific item (damage 32767 matches any damage) or,
 * when {@code ore_dict} is set, any item with that ore dictionary name.
 */
public class ItemTask extends Task {

    private ItemStack item;
    private String oreDict = "";
    private long count = 1;
    private boolean consume = true;
    private boolean matchNbt;

    public ItemTask(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public TaskType<?> getType() {
        return BuiltinTypes.ITEM_TASK;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item == null ? null : item.copy();
        if (this.item != null) this.item.stackSize = 1;
    }

    public String getOreDict() {
        return oreDict;
    }

    public void setOreDict(String oreDict) {
        this.oreDict = oreDict;
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

    public boolean isMatchNbt() {
        return matchNbt;
    }

    public void setMatchNbt(boolean matchNbt) {
        this.matchNbt = matchNbt;
    }

    @Override
    public long getMaxProgress() {
        return count;
    }

    @Override
    public ItemStack getIcon() {
        if (item != null) return item;
        if (!oreDict.isEmpty()) {
            for (ItemStack ore : OreDictionary.getOres(oreDict)) return ore;
        }
        return null;
    }

    @Override
    public String getTitle() {
        String name;
        if (!oreDict.isEmpty()) name = StatCollector.translateToLocalFormatted("waymark.task.item.ore", oreDict);
        else if (item != null) name = item.getDisplayName();
        else name = "?";
        return count > 1 ? count + "x " + name : name;
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
            if (matches(stack)) found += stack.stackSize;
        }
        return Math.max(progress, Math.min(found, count));
    }

    @Override
    public long submit(EntityPlayerMP player, long progress) {
        if (!consume) return detect(player, progress);
        ItemStack[] inventory = player.inventory.mainInventory;
        for (int i = 0; i < inventory.length && progress < count; i++) {
            ItemStack stack = inventory[i];
            if (!matches(stack)) continue;
            int taken = (int) Math.min(stack.stackSize, count - progress);
            stack.stackSize -= taken;
            if (stack.stackSize <= 0) inventory[i] = null;
            progress += taken;
        }
        player.inventoryContainer.detectAndSendChanges();
        return progress;
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (!oreDict.isEmpty()) {
            int wanted = OreDictionary.getOreID(oreDict);
            for (int id : OreDictionary.getOreIDs(stack)) {
                if (id == wanted) return true;
            }
            return false;
        }
        if (item == null || item.getItem() != stack.getItem()) return false;
        if (item.getItemDamage() != OreDictionary.WILDCARD_VALUE && item.getItemDamage() != stack.getItemDamage())
            return false;
        return !matchNbt || ItemStack.areItemStackTagsEqual(item, stack);
    }

    @Override
    public void writeJson(JsonObject json) {
        if (item != null) json.add("item", JsonUtil.writeStack(item));
        if (!oreDict.isEmpty()) json.addProperty("ore_dict", oreDict);
        if (count != 1) json.addProperty("count", count);
        if (!consume) json.addProperty("consume", false);
        if (matchNbt) json.addProperty("match_nbt", true);
    }

    @Override
    public void readJson(JsonObject json) {
        setItem(JsonUtil.readStack(JsonUtil.getObject(json, "item")));
        oreDict = JsonUtil.getString(json, "ore_dict", "");
        setCount(JsonUtil.getLong(json, "count", 1));
        consume = JsonUtil.getBoolean(json, "consume", true);
        matchNbt = JsonUtil.getBoolean(json, "match_nbt", false);
    }
}
