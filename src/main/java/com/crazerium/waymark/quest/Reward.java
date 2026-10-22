package com.crazerium.waymark.quest;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonObject;

/** Something a player receives after completing a quest. */
public abstract class Reward extends QuestObject {

    private final Quest quest;
    private boolean teamReward;

    protected Reward(Quest quest, long id) {
        super(id);
        this.quest = quest;
    }

    public Quest getQuest() {
        return quest;
    }

    @Override
    public QuestBook getBook() {
        return quest.getBook();
    }

    public abstract RewardType<?> getType();

    /** A team reward is claimed once for the whole team; a normal one by every member for themselves. */
    public boolean isTeamReward() {
        return teamReward;
    }

    public void setTeamReward(boolean teamReward) {
        this.teamReward = teamReward;
    }

    /** Short text shown in the quest window, e.g. "50 XP". */
    public abstract String getTitle();

    public ItemStack getIcon() {
        return null;
    }

    /** Gives the reward to the player. Called on the server only; {@code choice} is the option picked, if any. */
    public abstract void claim(EntityPlayerMP player, int choice);

    /** Writes type-specific fields; "id" and "type" are written by the quest. */
    public void writeJson(JsonObject json) {}

    public void readJson(JsonObject json) {}

    /** Fields the in-game editor shows for this type; base fields like the task title are added by the editor. */
    public List<EditorField> editorFields() {
        return Collections.emptyList();
    }
}
