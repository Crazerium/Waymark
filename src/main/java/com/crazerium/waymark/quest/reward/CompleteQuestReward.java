package com.crazerium.waymark.quest.reward;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.RewardType;
import com.crazerium.waymark.server.ServerProgress;
import com.crazerium.waymark.util.Ids;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Completes another quest for the player's team, e.g. to skip a branch that no longer applies. */
public class CompleteQuestReward extends Reward {

    private long questId;

    public CompleteQuestReward(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public RewardType<?> getType() {
        return BuiltinTypes.COMPLETE_QUEST_REWARD;
    }

    public Quest getTarget() {
        return getBook().getQuest(questId);
    }

    public void setTarget(Quest quest) {
        questId = quest.getId();
    }

    @Override
    public String getTitle() {
        Quest target = getTarget();
        return StatCollector.translateToLocalFormatted(
            "waymark.reward.complete_quest",
            target != null ? target.getDisplayTitle() : Ids.format(questId));
    }

    @Override
    public ItemStack getIcon() {
        Quest target = getTarget();
        ItemStack icon = target != null ? target.getIcon() : null;
        return icon != null ? icon : new ItemStack(Items.book);
    }

    @Override
    public void claim(EntityPlayerMP player, int choice) {
        Quest target = getTarget();
        if (target != null) ServerProgress.forceComplete(ServerProgress.get(player), target);
    }

    @Override
    public void writeJson(JsonObject json) {
        json.addProperty("quest", Ids.format(questId));
    }

    @Override
    public void readJson(JsonObject json) {
        questId = Ids.parse(JsonUtil.getString(json, "quest", ""));
    }
}
