package com.crazerium.waymark.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.quest.reward.UnknownReward;
import com.crazerium.waymark.quest.task.UnknownTask;
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
    /** Dependencies that still count but whose line is not drawn, to keep busy maps readable. */
    private final Set<Long> hiddenLines = new HashSet<>();

    private String title = "";
    private String subtitle = "";
    private List<String> description = new ArrayList<>();
    private ItemStack icon;
    private double x, y;
    private double size = 1.0;
    private LogicMode dependencyMode = LogicMode.ALL;
    private LogicMode taskMode = LogicMode.ALL;
    private Visibility visibility = Visibility.ALWAYS;
    private boolean optional;
    private boolean autoClaim;

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

    /** The title, or the icon's item name if the title is empty. */
    public String getDisplayTitle() {
        if (!title.isEmpty()) return title;
        ItemStack displayIcon = getIcon();
        return displayIcon != null ? displayIcon.getDisplayName() : "Quest " + Ids.format(getId());
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

    public LogicMode getDependencyMode() {
        return dependencyMode;
    }

    public void setDependencyMode(LogicMode dependencyMode) {
        this.dependencyMode = dependencyMode;
    }

    /** How many of the non-optional tasks must be done. */
    public LogicMode getTaskMode() {
        return taskMode;
    }

    public void setTaskMode(LogicMode taskMode) {
        this.taskMode = taskMode;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public boolean isOptional() {
        return optional;
    }

    public void setOptional(boolean optional) {
        this.optional = optional;
    }

    /** Rewards are given automatically on completion (except ones where the player has to choose). */
    public boolean isAutoClaim() {
        return autoClaim;
    }

    public void setAutoClaim(boolean autoClaim) {
        this.autoClaim = autoClaim;
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

    /** Removes a dependency by id, also one that points at a deleted quest. Returns true if it was there. */
    boolean removeDependencyId(long id) {
        hiddenLines.remove(id);
        return dependencies.remove(Long.valueOf(id));
    }

    public void removeDependency(Quest quest) {
        dependencies.remove(quest.getId());
        hiddenLines.remove(quest.getId());
    }

    public boolean isLineHidden(Quest dependency) {
        return hiddenLines.contains(dependency.getId());
    }

    public void setLineHidden(Quest dependency, boolean hidden) {
        if (hidden) hiddenLines.add(dependency.getId());
        else hiddenLines.remove(dependency.getId());
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
            JsonArray hidden = new JsonArray();
            for (long id : dependencies) if (hiddenLines.contains(id)) hidden.add(new JsonPrimitive(Ids.format(id)));
            if (hidden.size() > 0) json.add("hidden_lines", hidden);
        }
        if (dependencyMode != LogicMode.ALL) json.addProperty("dependency_mode", dependencyMode.key());
        if (taskMode != LogicMode.ALL) json.addProperty("task_mode", taskMode.key());
        if (visibility != Visibility.ALWAYS) json.addProperty("visibility", visibility.key());
        if (optional) json.addProperty("optional", true);
        if (autoClaim) json.addProperty("auto_claim", true);

        JsonArray taskArray = new JsonArray();
        for (Task task : tasks) {
            JsonObject t = new JsonObject();
            t.addProperty("id", Ids.format(task.getId()));
            t.addProperty(
                "type",
                task.getType()
                    .getId());
            if (!task.getCustomTitle()
                .isEmpty()) t.addProperty("title", task.getCustomTitle());
            if (task.isOptional()) t.addProperty("optional", true);
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

    /**
     * Replaces everything about this quest with {@code json}, as written by the quest editor. Position and
     * dependencies are kept, since the map editor owns those. Tasks and rewards that keep their ids keep progress.
     */
    void reload(JsonObject json) {
        JsonObject data = new JsonObject();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) data.add(e.getKey(), e.getValue());
        JsonObject current = toJson();
        for (String key : new String[] { "x", "y", "dependencies", "hidden_lines" }) {
            data.remove(key);
            if (current.has(key)) data.add(key, current.get(key));
        }
        for (Task task : tasks) getBook().unregister(task);
        for (Reward reward : rewards) getBook().unregister(reward);
        tasks.clear();
        rewards.clear();
        dependencies.clear();
        hiddenLines.clear();
        icon = null;
        readJson(data);
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
        for (String dep : JsonUtil.getStringList(json, "hidden_lines")) {
            long id = Ids.parse(dep);
            if (dependencies.contains(id)) hiddenLines.add(id);
        }
        dependencyMode = LogicMode.fromKey(JsonUtil.getString(json, "dependency_mode", "all"));
        taskMode = LogicMode.fromKey(JsonUtil.getString(json, "task_mode", "all"));
        visibility = Visibility.fromKey(JsonUtil.getString(json, "visibility", "always"));
        optional = JsonUtil.getBoolean(json, "optional", false);
        autoClaim = JsonUtil.getBoolean(json, "auto_claim", false);

        for (JsonElement e : JsonUtil.getArray(json, "tasks")) {
            if (!e.isJsonObject()) continue;
            JsonObject t = e.getAsJsonObject();
            String typeId = JsonUtil.getString(t, "type", "");
            TaskType<?> type = TaskType.get(typeId);
            long id = getBook().claimId(t, "Task in " + this);
            Task task;
            if (type != null) {
                task = type.create(this, id);
            } else {
                Waymark.LOG.warn("{}: unknown task type '{}', it is kept but does nothing", this, typeId);
                task = new UnknownTask(this, id);
            }
            task.setCustomTitle(JsonUtil.getString(t, "title", ""));
            task.setOptional(JsonUtil.getBoolean(t, "optional", false));
            tasks.add(task);
            getBook().register(task);
            task.readJson(t);
        }

        for (JsonElement e : JsonUtil.getArray(json, "rewards")) {
            if (!e.isJsonObject()) continue;
            JsonObject r = e.getAsJsonObject();
            String typeId = JsonUtil.getString(r, "type", "");
            RewardType<?> type = RewardType.get(typeId);
            long id = getBook().claimId(r, "Reward in " + this);
            Reward reward;
            if (type != null) {
                reward = type.create(this, id);
            } else {
                Waymark.LOG.warn("{}: unknown reward type '{}', it is kept but does nothing", this, typeId);
                reward = new UnknownReward(this, id);
            }
            rewards.add(reward);
            getBook().register(reward);
            reward.readJson(r);
        }
    }
}
