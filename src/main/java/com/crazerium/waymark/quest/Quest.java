package com.crazerium.waymark.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.util.Ids;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class Quest extends QuestObject {

    private final Chapter chapter;
    private final List<Task> tasks = new ArrayList<>();
    private final List<Reward> rewards = new ArrayList<>();
    private final List<Long> dependencies = new ArrayList<>();

    private String title = "";
    private String subtitle = "";
    private List<String> description = new ArrayList<>();
    private ItemStack icon;
    private double x, y;
    private double size = 1.0;
    private DependencyMode dependencyMode = DependencyMode.ALL;
    private boolean hiddenUntilUnlocked;
    private boolean optional;

    Quest(Chapter chapter, long id) {
        super(id);
        this.chapter = chapter;
    }

    @Override
    public QuestBook getBook() {
        return chapter.getBook();
    }

    public Chapter getChapter() {
        return chapter;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public List<String> getDescription() {
        return description;
    }

    public void setDescription(List<String> description) {
        this.description = new ArrayList<>(description);
    }

    /** The explicit icon, or the first task's icon if none is set. */
    public ItemStack getIcon() {
        if (icon != null) return icon;
        for (Task task : tasks) {
            ItemStack taskIcon = task.getIcon();
            if (taskIcon != null) return taskIcon;
        }
        return null;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public DependencyMode getDependencyMode() {
        return dependencyMode;
    }

    public void setDependencyMode(DependencyMode dependencyMode) {
        this.dependencyMode = dependencyMode;
    }

    public boolean isHiddenUntilUnlocked() {
        return hiddenUntilUnlocked;
    }

    public void setHiddenUntilUnlocked(boolean hiddenUntilUnlocked) {
        this.hiddenUntilUnlocked = hiddenUntilUnlocked;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    /** Ids of quests this one depends on. Ids that point at deleted quests are kept and simply ignored. */
    public List<Long> getDependencyIds() {
        return Collections.unmodifiableList(dependencies);
    }

    public List<Quest> getDependencies() {
        List<Quest> list = new ArrayList<>();
        for (long id : dependencies) {
            Quest quest = getBook().getQuest(id);
            if (quest != null) list.add(quest);
        }
        return list;
    }

    public void addDependency(Quest quest) {
        if (quest != this && !dependencies.contains(quest.getId())) dependencies.add(quest.getId());
    }

    public void removeDependency(Quest quest) {
        dependencies.remove(quest.getId());
    }

    public List<Task> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public <T extends Task> T addTask(TaskType<T> type) {
        T task = type.create(this, getBook().newId());
        tasks.add(task);
        getBook().register(task);
        return task;
    }

    public void removeTask(Task task) {
        if (tasks.remove(task)) getBook().unregister(task);
    }

    public List<Reward> getRewards() {
        return Collections.unmodifiableList(rewards);
    }

    public <R extends Reward> R addReward(RewardType<R> type) {
        R reward = type.create(this, getBook().newId());
        rewards.add(reward);
        getBook().register(reward);
        return reward;
    }

    public void removeReward(Reward reward) {
        if (rewards.remove(reward)) getBook().unregister(reward);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", Ids.format(getId()));
        if (!title.isEmpty()) json.addProperty("title", title);
        if (!subtitle.isEmpty()) json.addProperty("subtitle", subtitle);
        if (!description.isEmpty()) json.add("description", JsonUtil.toArray(description));
        if (icon != null) json.add("icon", JsonUtil.writeStack(icon));
        json.addProperty("x", x);
        json.addProperty("y", y);
        if (size != 1.0) json.addProperty("size", size);
        if (!dependencies.isEmpty()) {
            JsonArray deps = new JsonArray();
            for (long id : dependencies) deps.add(new JsonPrimitive(Ids.format(id)));
            json.add("dependencies", deps);
        }
        if (dependencyMode != DependencyMode.ALL) json.addProperty("dependency_mode", dependencyMode.key());
        if (hiddenUntilUnlocked) json.addProperty("hidden_until_unlocked", true);
        if (optional) json.addProperty("optional", true);

        JsonArray taskArray = new JsonArray();
        for (Task task : tasks) {
            JsonObject t = new JsonObject();
            t.addProperty("id", Ids.format(task.getId()));
            t.addProperty(
                "type",
                task.getType()
                    .getId());
            task.writeJson(t);
            taskArray.add(t);
        }
        json.add("tasks", taskArray);

        JsonArray rewardArray = new JsonArray();
        for (Reward reward : rewards) {
            JsonObject r = new JsonObject();
            r.addProperty("id", Ids.format(reward.getId()));
            r.addProperty(
                "type",
                reward.getType()
                    .getId());
            reward.writeJson(r);
            rewardArray.add(r);
        }
        if (rewardArray.size() > 0) json.add("rewards", rewardArray);
        return json;
    }

    void readJson(JsonObject json) {
        title = JsonUtil.getString(json, "title", "");
        subtitle = JsonUtil.getString(json, "subtitle", "");
        description = JsonUtil.getStringList(json, "description");
        icon = JsonUtil.readStack(JsonUtil.getObject(json, "icon"));
        x = JsonUtil.getDouble(json, "x", 0);
        y = JsonUtil.getDouble(json, "y", 0);
        size = JsonUtil.getDouble(json, "size", 1.0);
        for (String dep : JsonUtil.getStringList(json, "dependencies")) {
            long id = Ids.parse(dep);
            if (id != Ids.NONE) dependencies.add(id);
        }
        dependencyMode = DependencyMode.fromKey(JsonUtil.getString(json, "dependency_mode", "all"));
        hiddenUntilUnlocked = JsonUtil.getBoolean(json, "hidden_until_unlocked", false);
        optional = JsonUtil.getBoolean(json, "optional", false);

        for (JsonElement e : JsonUtil.getArray(json, "tasks")) {
            if (!e.isJsonObject()) continue;
            JsonObject t = e.getAsJsonObject();
            TaskType<?> type = TaskType.get(JsonUtil.getString(t, "type", ""));
            if (type == null) {
                Waymark.LOG.error("{}: unknown task type '{}', task skipped", this, t.get("type"));
                continue;
            }
            Task task = type.create(this, getBook().claimId(t, "Task in " + this));
            tasks.add(task);
            getBook().register(task);
            task.readJson(t);
        }

        for (JsonElement e : JsonUtil.getArray(json, "rewards")) {
            if (!e.isJsonObject()) continue;
            JsonObject r = e.getAsJsonObject();
            RewardType<?> type = RewardType.get(JsonUtil.getString(r, "type", ""));
            if (type == null) {
                Waymark.LOG.error("{}: unknown reward type '{}', reward skipped", this, r.get("type"));
                continue;
            }
            Reward reward = type.create(this, getBook().claimId(r, "Reward in " + this));
            rewards.add(reward);
            getBook().register(reward);
            reward.readJson(r);
        }
    }
}
