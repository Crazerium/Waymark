package com.crazerium.waymark.server;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.oredict.OreDictionary;

import com.crazerium.waymark.io.ProgressStorage;
import com.crazerium.waymark.network.MessageBlob;
import com.crazerium.waymark.network.MessageCanEdit;
import com.crazerium.waymark.network.MessageQuestCompleted;
import com.crazerium.waymark.network.MessageQuestReset;
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
import com.crazerium.waymark.quest.task.ItemFilter;
import com.crazerium.waymark.quest.task.ItemTask;
import com.crazerium.waymark.quest.task.KillTask;

import cpw.mods.fml.common.network.simpleimpl.IMessage;

/** Server-side quest progress: changes it, checks quest completion and keeps clients in sync. */
public final class ServerProgress {

    private static ProgressStorage storage;
    private static final Map<UUID, TeamProgress> teams = new HashMap<>();
    /** Auto-detected tasks other than "have items", which are indexed below instead. */
    private static final List<Task> autoDetectedTasks = new ArrayList<>();
    /** "Have items" tasks by the item they want, and by ore dictionary id, so only plausible ones are checked. */
    private static final Map<Item, List<ItemTask>> heldByItem = new HashMap<>();
    private static final Map<Integer, List<ItemTask>> heldByOre = new HashMap<>();
    /** Quest id to the quests that depend on it. */
    private static final Map<Long, List<Quest>> dependents = new HashMap<>();
    private static final List<CraftTask> craftTasks = new ArrayList<>();
    private static final List<KillTask> killTasks = new ArrayList<>();
    private static final List<Quest> repeatableQuests = new ArrayList<>();

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
        lastTeam.clear();
    }

    public static void onBookLoaded(QuestBook book) {
        autoDetectedTasks.clear();
        heldByItem.clear();
        heldByOre.clear();
        dependents.clear();
        craftTasks.clear();
        repeatableQuests.clear();
        killTasks.clear();
        for (Quest quest : book.getAllQuests()) {
            if (quest.isRepeatable()) repeatableQuests.add(quest);
            for (Quest dep : quest.getDependencies()) dependents.computeIfAbsent(dep.getId(), k -> new ArrayList<>())
                .add(quest);
            for (Task task : quest.getTasks()) {
                if (task instanceof ItemTask && task.isAutoDetected()) indexHeldItemTask((ItemTask) task);
                else if (task.isAutoDetected()) autoDetectedTasks.add(task);
                if (task instanceof CraftTask) craftTasks.add((CraftTask) task);
                if (task instanceof KillTask) killTasks.add((KillTask) task);
            }
        }
    }

    private static UUID teamIdOf(UUID player) {
        return Teams.teamOf(player);
    }

    public static TeamProgress get(EntityPlayer player) {
        return teams.computeIfAbsent(teamIdOf(player.getUniqueID()), TeamProgress::new);
    }

    /**
     * The team each player was in when last checked this session, to notice joins and leaves (including being
     * removed from a team while offline).
     */
    private static final Map<UUID, UUID> lastTeam = new HashMap<>();

    /**
     * Notices that a player joined, left or switched team. Their old progress is merged into the new team's, so
     * joining a team never loses quests and leaving keeps what the team did. Then the player and their new team get
     * the merged progress.
     */
    private static void checkTeamChange(EntityPlayerMP player) {
        UUID now = teamIdOf(player.getUniqueID());
        UUID before = lastTeam.put(player.getUniqueID(), now);
        if (before == null || before.equals(now)) return;
        TeamProgress from = teams.get(before);
        TeamProgress to = teams.computeIfAbsent(now, TeamProgress::new);
        if (from != null) to.mergeFrom(from);
        for (EntityPlayerMP member : onlineMembers(to)) sendProgress(member, to);
    }

    private static void sendProgress(EntityPlayerMP player, TeamProgress team) {
        for (MessageBlob part : MessageBlob.split(MessageBlob.PROGRESS, team.toJson()))
            WaymarkNetwork.CHANNEL.sendTo(part, player);
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
        WaymarkNetwork.CHANNEL.sendTo(new MessageCanEdit(ServerEditor.canEdit(player)), player);
        ServerImages.sendIndex(player);
        checkTeamChange(player);
        TeamProgress team = get(player);
        sendProgress(player, team);
        for (Quest quest : book.getAllQuests()) checkQuest(team, quest);
    }

    public static void syncAll() {
        for (EntityPlayerMP player : onlinePlayers()) syncPlayer(player);
    }

    public static void detect(EntityPlayerMP player) {
        checkTeamChange(player);
        TeamProgress team = get(player);
        checkRepeats(team, player);
        detectHeldItems(team, player);
        for (Task task : autoDetectedTasks) {
            if (team.isComplete(task) || !team.canProgress(task.getQuest())) continue;
            setProgress(team, task, task.detect(player, team.getProgress(task)));
        }
    }

    /**
     * Resets repeatable quests whose cooldown has passed, once this player has claimed their rewards (otherwise the
     * reset would take the rewards away).
     */
    private static void checkRepeats(TeamProgress team, EntityPlayerMP player) {
        long now = System.currentTimeMillis();
        for (Quest quest : repeatableQuests) {
            long completedAt = team.getCompletedTime(quest);
            if (completedAt < 0 || now - completedAt < quest.getRepeatCooldown() * 1000) continue;
            boolean claimedAll = true;
            for (Reward reward : quest.getRewards()) {
                if (!team.isClaimed(player.getUniqueID(), reward)) claimedAll = false;
            }
            if (!claimedAll) continue;
            team.resetQuest(quest);
            sendToTeam(team, new MessageQuestReset(quest.getId()));
            checkQuest(team, quest);
        }
    }

    private static void indexHeldItemTask(ItemTask task) {
        ItemFilter filter = task.getFilter();
        if (!filter.getOreDict()
            .isEmpty()) {
            heldByOre.computeIfAbsent(OreDictionary.getOreID(filter.getOreDict()), k -> new ArrayList<>())
                .add(task);
            return;
        }
        for (ItemStack stack : filter.getItems()) heldByItem.computeIfAbsent(stack.getItem(), k -> new ArrayList<>())
            .add(task);
    }

    /**
     * Counts the player's items for "have items" tasks. Instead of testing every such task against every slot, each
     * stack only looks up the tasks that want its item or one of its ore names.
     */
    private static void detectHeldItems(TeamProgress team, EntityPlayerMP player) {
        Map<ItemTask, Long> found = new HashMap<>();
        Set<ItemTask> candidates = new HashSet<>();
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack == null || stack.getItem() == null) continue;
            candidates.clear();
            List<ItemTask> byItem = heldByItem.get(stack.getItem());
            if (byItem != null) candidates.addAll(byItem);
            if (!heldByOre.isEmpty()) for (int ore : OreDictionary.getOreIDs(stack)) {
                List<ItemTask> byOre = heldByOre.get(ore);
                if (byOre != null) candidates.addAll(byOre);
            }
            for (ItemTask task : candidates) {
                if (task.getFilter()
                    .matches(stack)) found.merge(task, (long) stack.stackSize, Long::sum);
            }
        }
        for (Map.Entry<ItemTask, Long> e : found.entrySet()) {
            ItemTask task = e.getKey();
            if (team.isComplete(task) || !team.canProgress(task.getQuest())) continue;
            long progress = team.getProgress(task);
            setProgress(team, task, Math.max(progress, Math.min(e.getValue(), task.getCount())));
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
        team.setClaimed(player.getUniqueID(), reward);
        reward.claim(player, choice);
        // A team reward is gone for everyone once one member takes it.
        if (reward.isTeamReward()) sendToTeam(team, new MessageRewardClaimed(reward.getId()));
        else WaymarkNetwork.CHANNEL.sendTo(new MessageRewardClaimed(reward.getId()), player);
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
        // Clients show a "quest completed" notification when this arrives.
        sendToTeam(team, new MessageQuestCompleted(quest.getId(), time));

        if (quest.isAutoClaim()) for (EntityPlayerMP player : onlineMembers(team)) {
            for (Reward reward : quest.getRewards()) {
                if (!(reward instanceof ChoiceReward)) claim(player, reward.getId(), -1);
            }
        }

        // Quests whose tasks are all optional complete as soon as they unlock, so completion can cascade.
        for (Quest dependent : dependents.getOrDefault(quest.getId(), Collections.emptyList()))
            checkQuest(team, dependent);
    }

    private static void sendToTeam(TeamProgress team, IMessage message) {
        for (EntityPlayerMP player : onlineMembers(team)) WaymarkNetwork.CHANNEL.sendTo(message, player);
    }
}
