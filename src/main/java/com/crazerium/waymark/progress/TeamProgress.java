package com.crazerium.waymark.progress;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.util.Ids;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Quest progress shared by a team. Task progress and completed quests belong to the team; claimed rewards are
 * tracked per player so every member gets their own rewards. Until teams exist, each player is a team of one whose
 * id is the player's UUID.
 */
public class TeamProgress {

    private final UUID id;
    private final Map<Long, Long> taskProgress = new HashMap<>();
    /** Quest id to completion time (epoch millis). */
    private final Map<Long, Long> completed = new HashMap<>();
    private final Map<UUID, Set<Long>> claimed = new HashMap<>();
    private boolean dirty;

    public TeamProgress(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public long getProgress(Task task) {
        Long value = taskProgress.get(task.getId());
        return value == null ? 0 : value;
    }

    public void setProgress(long taskId, long value) {
        if (value <= 0) taskProgress.remove(taskId);
        else taskProgress.put(taskId, value);
        dirty = true;
    }

    public boolean isComplete(Task task) {
        return getProgress(task) >= task.getMaxProgress();
    }

    public boolean isCompleted(Quest quest) {
        return completed.containsKey(quest.getId());
    }

    public void setCompleted(long questId, long time) {
        completed.put(questId, time);
        dirty = true;
    }

    public int getCompletedCount() {
        return completed.size();
    }

    /** Dependencies that point at deleted quests are ignored. */
    public boolean isUnlocked(Quest quest) {
        List<Quest> deps = quest.getDependencies();
        if (deps.isEmpty()) return true;
        int met = 0;
        for (Quest dep : deps) if (isCompleted(dep)) met++;
        return quest.getDependencyMode()
            .test(met, deps.size());
    }

    /** True if enough non-optional tasks are done for the quest's task mode. A quest with none is done at once. */
    public boolean areTasksDone(Quest quest) {
        int total = 0, met = 0;
        for (Task task : quest.getTasks()) {
            if (task.isOptional()) continue;
            total++;
            if (isComplete(task)) met++;
        }
        return total == 0 || quest.getTaskMode()
            .test(met, total);
    }

    /** True if tasks of this quest can currently make progress. */
    public boolean canProgress(Quest quest) {
        return !isCompleted(quest) && isUnlocked(quest);
    }

    public boolean isClaimed(UUID player, Reward reward) {
        Set<Long> set = claimed.get(player);
        return set != null && set.contains(reward.getId());
    }

    public void setClaimed(UUID player, long rewardId) {
        claimed.computeIfAbsent(player, k -> new HashSet<>())
            .add(rewardId);
        dirty = true;
    }

    public void reset() {
        taskProgress.clear();
        completed.clear();
        claimed.clear();
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        JsonObject tasks = new JsonObject();
        for (Map.Entry<Long, Long> e : taskProgress.entrySet()) tasks.addProperty(Ids.format(e.getKey()), e.getValue());
        json.add("tasks", tasks);
        JsonObject quests = new JsonObject();
        for (Map.Entry<Long, Long> e : completed.entrySet()) quests.addProperty(Ids.format(e.getKey()), e.getValue());
        json.add("completed", quests);
        JsonObject rewards = new JsonObject();
        for (Map.Entry<UUID, Set<Long>> e : claimed.entrySet()) {
            JsonArray array = new JsonArray();
            for (long rewardId : e.getValue()) array.add(new JsonPrimitive(Ids.format(rewardId)));
            rewards.add(
                e.getKey()
                    .toString(),
                array);
        }
        json.add("claimed", rewards);
        return json;
    }

    public void readJson(JsonObject json) {
        taskProgress.clear();
        completed.clear();
        claimed.clear();
        readLongMap(json.get("tasks"), taskProgress);
        readLongMap(json.get("completed"), completed);
        JsonElement rewards = json.get("claimed");
        if (rewards != null && rewards.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : rewards.getAsJsonObject()
                .entrySet()) {
                if (!e.getValue()
                    .isJsonArray()) continue;
                UUID player;
                try {
                    player = UUID.fromString(e.getKey());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                Set<Long> set = new HashSet<>();
                for (JsonElement r : e.getValue()
                    .getAsJsonArray()) {
                    long rewardId = Ids.parse(r.getAsString());
                    if (rewardId != Ids.NONE) set.add(rewardId);
                }
                claimed.put(player, set);
            }
        }
        dirty = false;
    }

    private static void readLongMap(JsonElement element, Map<Long, Long> into) {
        if (element == null || !element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : element.getAsJsonObject()
            .entrySet()) {
            long key = Ids.parse(e.getKey());
            if (key != Ids.NONE && e.getValue()
                .isJsonPrimitive())
                into.put(
                    key,
                    e.getValue()
                        .getAsLong());
        }
    }
}
