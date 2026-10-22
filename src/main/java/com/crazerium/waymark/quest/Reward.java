package com.crazerium.waymark.quest;

import net.minecraft.entity.player.EntityPlayerMP;

import com.google.gson.JsonObject;

/** Something a player receives after completing a quest. */
public abstract class Reward extends QuestObject {

    private final Quest quest;

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

    /** Gives the reward to the player. Called on the server only. */
    public abstract void claim(EntityPlayerMP player);

    /** Writes type-specific fields; "id" and "type" are written by the quest. */
    public void writeJson(JsonObject json) {}

    public void readJson(JsonObject json) {}
}
