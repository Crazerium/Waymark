package com.crazerium.waymark.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.network.MessageClaimReward;
import com.crazerium.waymark.network.MessageEditRequest;
import com.crazerium.waymark.network.MessageSubmitTask;
import com.crazerium.waymark.network.WaymarkNetwork;
import com.crazerium.waymark.progress.TeamProgress;
import com.crazerium.waymark.quest.BookEdits;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.quest.QuestObject;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.Task;
import com.google.gson.JsonObject;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** The client's copy of the quest book and of its team's progress, as last sent by the server. */
@SideOnly(Side.CLIENT)
public final class ClientQuests {

    private static QuestBook book;
    private static TeamProgress progress;
    private static boolean canEdit;

    private ClientQuests() {}

    public static boolean canEdit() {
        return canEdit;
    }

    public static void setCanEdit(boolean value) {
        canEdit = value;
    }

    /** Asks the server to make an editor change; if accepted it comes back through {@link #applyEdit}. */
    public static void sendEdit(JsonObject op) {
        WaymarkNetwork.CHANNEL.sendToServer(new MessageEditRequest(op));
    }

    public static void applyEdit(JsonObject op) {
        if (book == null) return;
        try {
            BookEdits.apply(book, op);
        } catch (IllegalArgumentException e) {
            Waymark.LOG.warn("Could not apply quest edit {}: {}", op, e.getMessage());
        }
    }

    /** Null until the server has sent the book. */
    public static QuestBook getBook() {
        return book;
    }

    /** Null until the server has sent progress. */
    public static TeamProgress getProgress() {
        return progress;
    }

    public static UUID playerId() {
        return Minecraft.getMinecraft().thePlayer.getUniqueID();
    }

    public static void setBook(QuestBook newBook) {
        book = newBook;
    }

    public static void setProgress(JsonObject json) {
        TeamProgress team = new TeamProgress(new UUID(0, 0));
        team.readJson(json);
        progress = team;
    }

    public static void onTaskProgress(long taskId, long value) {
        if (progress != null) progress.setProgress(taskId, value);
    }

    public static void onQuestCompleted(long questId, long time) {
        if (progress != null) progress.setCompleted(questId, time);
        Quest quest = book != null ? book.getQuest(questId) : null;
        if (quest != null) Toasts.questCompleted(quest);
    }

    public static void onQuestReset(long questId) {
        if (book == null || progress == null) return;
        Quest quest = book.getQuest(questId);
        if (quest != null) progress.resetQuest(quest);
    }

    public static void onRewardClaimed(long rewardId) {
        if (progress == null || book == null) return;
        QuestObject object = book.get(rewardId);
        if (object instanceof Reward) progress.setClaimed(playerId(), (Reward) object);
    }

    public static void submit(Task task) {
        WaymarkNetwork.CHANNEL.sendToServer(new MessageSubmitTask(task.getId()));
    }

    public static void claim(Reward reward, int choice) {
        WaymarkNetwork.CHANNEL.sendToServer(new MessageClaimReward(reward.getId(), choice));
    }

    public static void clear() {
        book = null;
        progress = null;
        canEdit = false;
    }
}
