package com.crazerium.waymark.quest;

import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.google.gson.JsonObject;

/** Something a player has to do to complete a quest. Progress is a number from 0 to {@link #getMaxProgress()}. */
public abstract class Task extends QuestObject {

    private final Quest quest;
    private String customTitle = "";
    private boolean optional;

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

    /** Default text shown in the quest window, e.g. "16x Oak Wood". */
    public abstract String getTitle();

    /** The custom title if one is set, otherwise {@link #getTitle()}. */
    public String getDisplayTitle() {
        return customTitle.isEmpty() ? getTitle() : customTitle;
    }

    public String getCustomTitle() {
        return customTitle;
    }

    public void setCustomTitle(String customTitle) {
        this.customTitle = customTitle;
    }

    /** Optional tasks show progress but are not needed to complete the quest. */
    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    /** True if the quest window should show a submit button while the task can progress. */
    public boolean hasSubmitButton() {
        return false;
    }

    /** True if the server should call {@link #detect} periodically for players who can progress this task. */
    public boolean isAutoDetected() {
        return false;
    }

    /** Called on the server about once a second if {@link #isAutoDetected()}; returns the new progress. */
    public long detect(EntityPlayerMP player, long progress) {
        return progress;
    }

    /** Called on the server when the player presses the task's submit button; returns the new progress. */
    public long submit(EntityPlayerMP player, long progress) {
        return progress;
    }

    /** Writes type-specific fields; "id", "type", "title" and "optional" are written by the quest. */
    public void writeJson(JsonObject json) {}

    public void readJson(JsonObject json) {}

    /** Fields the in-game editor shows for this type; base fields like the task title are added by the editor. */
    public List<EditorField> editorFields() {
        return Collections.emptyList();
    }
}
