package com.crazerium.waymark.quest;

import net.minecraft.item.ItemStack;

import com.google.gson.JsonObject;

/** Something a player has to do to complete a quest. Progress is a number from 0 to {@link #getMaxProgress()}. */
public abstract class Task extends QuestObject {

    private final Quest quest;

    protected Task(Quest quest, long id) {
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

    public abstract TaskType<?> getType();

    public long getMaxProgress() {
        return 1;
    }

    public ItemStack getIcon() {
        return null;
    }

    /** Writes type-specific fields; "id" and "type" are written by the quest. */
    public void writeJson(JsonObject json) {}

    public void readJson(JsonObject json) {}
}
