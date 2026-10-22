package com.crazerium.waymark.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;

import com.crazerium.waymark.network.MessageClaimReward;
import com.crazerium.waymark.network.MessageSubmitTask;
import com.crazerium.waymark.network.WaymarkNetwork;
import com.crazerium.waymark.progress.TeamProgress;
import com.crazerium.waymark.quest.QuestBook;
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

    private ClientQuests() {}

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
    }

    public static void onRewardClaimed(long rewardId) {
        if (progress != null) progress.setClaimed(playerId(), rewardId);
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
    }
}
