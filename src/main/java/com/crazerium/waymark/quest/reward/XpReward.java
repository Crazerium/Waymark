package com.crazerium.waymark.quest.reward;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.RewardType;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Gives experience points, or whole levels if {@code levels} is true. */
public class XpReward extends Reward {

    private int amount = 100;
    private boolean levels;

    public XpReward(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public RewardType<?> getType() {
        return BuiltinTypes.XP_REWARD;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }

    public boolean isLevels() {
        return levels;
    }

    public void setLevels(boolean levels) {
        this.levels = levels;
    }

    @Override
    public String getTitle() {
        return StatCollector
            .translateToLocalFormatted(levels ? "waymark.reward.xp_levels" : "waymark.reward.xp", amount);
    }

    @Override
    public ItemStack getIcon() {
        return new ItemStack(Items.experience_bottle);
    }

    @Override
    public void claim(EntityPlayerMP player, int choice) {
        if (levels) player.addExperienceLevel(amount);
        else player.addExperience(amount);
    }

    @Override
    public void writeJson(JsonObject json) {
        json.addProperty("amount", amount);
        if (levels) json.addProperty("levels", true);
    }

    @Override
    public void readJson(JsonObject json) {
        setAmount(JsonUtil.getInt(json, "amount", 100));
        levels = JsonUtil.getBoolean(json, "levels", false);
    }
}
