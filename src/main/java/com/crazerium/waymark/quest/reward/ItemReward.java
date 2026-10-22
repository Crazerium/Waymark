package com.crazerium.waymark.quest.reward;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.RewardType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Gives items; anything that does not fit in the inventory is dropped at the player's feet. */
public class ItemReward extends Reward {

    private ItemStack item;
    private int count = 1;

    public ItemReward(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public RewardType<?> getType() {
        return BuiltinTypes.ITEM_REWARD;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item == null ? null : item.copy();
        if (this.item != null) this.item.stackSize = 1;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.max(1, count);
    }

    @Override
    public void claim(EntityPlayerMP player) {
        if (item == null) return;
        int remaining = count;
        while (remaining > 0) {
            ItemStack stack = item.copy();
            stack.stackSize = Math.min(remaining, item.getMaxStackSize());
            remaining -= stack.stackSize;
            if (!player.inventory.addItemStackToInventory(stack) && stack.stackSize > 0) {
                player.dropPlayerItemWithRandomChoice(stack, false);
            }
        }
        player.inventoryContainer.detectAndSendChanges();
    }

    @Override
    public void writeJson(JsonObject json) {
        if (item != null) json.add("item", JsonUtil.writeStack(item));
        if (count != 1) json.addProperty("count", count);
    }

    @Override
    public void readJson(JsonObject json) {
        setItem(JsonUtil.readStack(JsonUtil.getObject(json, "item")));
        setCount(JsonUtil.getInt(json, "count", 1));
    }
}
