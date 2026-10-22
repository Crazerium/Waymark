package com.crazerium.waymark.server;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.crazerium.waymark.io.ProgressStorage;
import com.crazerium.waymark.network.MessageBlob;
import com.crazerium.waymark.network.MessageQuestCompleted;
import com.crazerium.waymark.network.MessageRewardClaimed;
import com.crazerium.waymark.network.MessageTaskProgress;
import com.crazerium.waymark.network.WaymarkNetwork;
import com.crazerium.waymark.progress.TeamProgress;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.quest.QuestObject;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.reward.ChoiceReward;
import com.crazerium.waymark.quest.task.CraftTask;
import com.crazerium.waymark.quest.task.KillTask;

import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** Server-side quest progress: changes it, checks quest completion and keeps clients in sync. */
public final class ServerProgress {

    private static ProgressStorage storage;
    private static final Map<UUID, TeamProgress> teams = new HashMap<>();
    private static final List<Task> autoDetectedTasks = new ArrayList<>();
    private static final List<CraftTask> craftTasks = new ArrayList<>();
    private static final List<KillTask> killTasks = new ArrayList<>();

    private ServerProgress() {}

    public static void load(File worldDir) {
        storage = new ProgressStorage(worldDir);
        teams.clear();
        teams.putAll(storage.loadAll());
    }

    public static void save() {
        if (storage != null) storage.saveDirty(teams.values());
    }

    public static void unload() {
        storage = null;
        teams.clear();
        autoDetectedTasks.clear();
    }

    public static void onBookLoaded(QuestBook book) {
        autoDetectedTasks.clear();
        craftTasks.clear();
        killTasks.clear();
        for (Quest quest : book.getAllQuests()) {
            for (Task task : quest.getTasks()) {
                if (task.isAutoDetected()) autoDetectedTasks.add(task);
                if (task instanceof CraftTask) craftTasks.add((CraftTask) task);
                if (task instanceof KillTask) killTasks.add((KillTask) task);
            }
        }
    }

    /** Teams are not implemented yet, so every player is their own team. */
    private static UUID teamIdOf(UUID player) {
        return player;
    }

    public static TeamProgress get(EntityPlayer player) {
        return teams.computeIfAbsent(teamIdOf(player.getUniqueID()), TeamProgress::new);
    }

    public static Collection<TeamProgress> all() {
        return teams.values();
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> onlinePlayers() {
        return MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList;
    }

    private static List<EntityPlayerMP> onlineMembers(TeamProgress team) {
        List<EntityPlayerMP> list = new ArrayList<>();
        for (EntityPlayerMP player : onlinePlayers()) {
            if (teamIdOf(player.getUniqueID()).equals(team.getId())) list.add(player);
        }
        return list;
    }

    /** Sends the whole book and the player's progress, then completes anything that is already done. */
    public static void syncPlayer(EntityPlayerMP player) {
        QuestBook book = ServerQuests.get();
        for (MessageBlob part : MessageBlob.split(MessageBlob.BOOK, ServerQuests.syncData()))
            WaymarkNetwork.CHANNEL.sendTo(part, player);
        TeamProgress team = get(player);
        for (MessageBlob part : MessageBlob.split(MessageBlob.PROGRESS, team.toJson()))
            WaymarkNetwork.CHANNEL.sendTo(part, player);
        for (Quest quest : book.getAllQuests()) checkQuest(team, quest);
    }

    public static void syncAll() {
        for (EntityPlayerMP player : onlinePlayers()) syncPlayer(player);
    }

    public static void detect(EntityPlayerMP player) {
        TeamProgress team = get(player);
        for (Task task : autoDetectedTasks) {
            if (team.isComplete(task) || !team.canProgress(task.getQuest())) continue;
            setProgress(team, task, task.detect(player, team.getProgress(task)));
        }
    }

    public static void submit(EntityPlayerMP player, long taskId) {
        QuestObject object = ServerQuests.get()
            .get(taskId);
        if (!(object instanceof Task)) return;
        Task task = (Task) object;
        TeamProgress team = get(player);
        if (team.isComplete(task) || !team.canProgress(task.getQuest())) return;
        setProgress(team, task, task.submit(player, team.getProgress(task)));
    }

    public static void onCrafted(EntityPlayerMP player, ItemStack stack, boolean smelted) {
        if (stack == null || stack.stackSize <= 0) return;
        TeamProgress team = get(player);
        for (CraftTask task : craftTasks) {
            if (team.isComplete(task) || !team.canProgress(task.getQuest())) continue;
            if (!(smelted ? task.countsSmelting() : task.countsCrafting()) || !task.getFilter()
                .matches(stack)) continue;
            setProgress(team, task, team.getProgress(task) + stack.stackSize);
        }
    }

    public static void onKill(EntityPlayerMP player, Entity victim) {
        TeamProgress team = get(player);
        for (KillTask task : killTasks) {
            if (team.isComplete(task) || !team.canProgress(task.getQuest()) || !task.matches(victim)) continue;
            setProgress(team, task, team.getProgress(task) + 1);
        }
    }

    public static void claim(EntityPlayerMP player, long rewardId, int choice) {
        QuestObject object = ServerQuests.get()
            .get(rewardId);
        if (!(object instanceof Reward)) return;
        Reward reward = (Reward) object;
        if (reward instanceof ChoiceReward && (choice < 0 || choice >= ((ChoiceReward) reward).getChoices()
            .size())) return;
        TeamProgress team = get(player);
        if (!team.isCompleted(reward.getQuest()) || team.isClaimed(player.getUniqueID(), reward)) return;
        team.setClaimed(player.getUniqueID(), reward.getId());
        reward.claim(player, choice);
        WaymarkNetwork.CHANNEL.sendTo(new MessageRewardClaimed(reward.getId()), player);
    }

    /** Completes a quest regardless of its dependencies, filling all of its tasks. */
    public static void forceComplete(TeamProgress team, Quest quest) {
        if (team.isCompleted(quest)) return;
        for (Task task : quest.getTasks()) {
            team.setProgress(task.getId(), task.getMaxProgress());
            sendToTeam(team, new MessageTaskProgress(task.getId(), task.getMaxProgress()));
        }
        complete(team, quest);
    }

    public static void reset(TeamProgress team) {
        team.reset();
        for (EntityPlayerMP player : onlineMembers(team)) syncPlayer(player);
    }

    private static void setProgress(TeamProgress team, Task task, long value) {
        value = Math.max(0, Math.min(value, task.getMaxProgress()));
        if (value == team.getProgress(task)) return;
        team.setProgress(task.getId(), value);
        sendToTeam(team, new MessageTaskProgress(task.getId(), value));
        if (team.isComplete(task)) checkQuest(team, task.getQuest());
    }

    private static void checkQuest(TeamProgress team, Quest quest) {
        if (team.canProgress(quest) && team.areTasksDone(quest)) complete(team, quest);
    }

    private static void complete(TeamProgress team, Quest quest) {
        long time = System.currentTimeMillis();
        team.setCompleted(quest.getId(), time);
        sendToTeam(team, new MessageQuestCompleted(quest.getId(), time));

        ChatComponentTranslation message = new ChatComponentTranslation(
            "waymark.quest.completed",
            quest.getDisplayTitle());
        message.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN));
        for (EntityPlayerMP player : onlineMembers(team)) {
            player.addChatMessage(message);
            if (!quest.isAutoClaim()) continue;
            for (Reward reward : quest.getRewards()) {
                if (!(reward instanceof ChoiceReward)) claim(player, reward.getId(), -1);
            }
        }

        // Quests without tasks complete as soon as they unlock, so completion can cascade.
        for (Quest dependent : quest.getBook()
            .getDependents(quest)) checkQuest(team, dependent);
    }

    private static void sendToTeam(TeamProgress team, IMessage message) {
        for (EntityPlayerMP player : onlineMembers(team)) WaymarkNetwork.CHANNEL.sendTo(message, player);
    }
}
