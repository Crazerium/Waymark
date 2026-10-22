package com.crazerium.waymark.quest.reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.RewardType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** The player picks one of several item stacks. Each option's stack size is the amount given. */
public class ChoiceReward extends Reward {

    private final List<ItemStack> choices = new ArrayList<>();

    public ChoiceReward(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public RewardType<?> getType() {
        return BuiltinTypes.CHOICE_REWARD;
    }

    public List<ItemStack> getChoices() {
        return Collections.unmodifiableList(choices);
    }

    public void setChoices(List<ItemStack> list) {
        choices.clear();
        for (ItemStack stack : list) if (stack != null) choices.add(stack.copy());
    }

    @Override
    public String getTitle() {
        return StatCollector.translateToLocal("waymark.reward.choice");
    }

    @Override
    public ItemStack getIcon() {
        return choices.isEmpty() ? null : choices.get(0);
    }

    @Override
    public void claim(EntityPlayerMP player, int choice) {
        if (choice < 0 || choice >= choices.size()) return;
        ItemStack stack = choices.get(choice);
        ItemReward.give(player, stack, stack.stackSize);
    }

    @Override
    public void writeJson(JsonObject json) {
        JsonArray array = new JsonArray();
        for (ItemStack stack : choices) array.add(JsonUtil.writeStack(stack));
        json.add("choices", array);
    }

    @Override
    public void readJson(JsonObject json) {
        List<ItemStack> list = new ArrayList<>();
        for (JsonElement e : JsonUtil.getArray(json, "choices")) {
            if (e.isJsonObject()) list.add(JsonUtil.readStack(e.getAsJsonObject()));
        }
        setChoices(list);
    }
}
