package com.crazerium.waymark.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A kind of task, identified in files by its string id. Other mods can register their own. */
public final class TaskType<T extends Task> {

    public interface Factory<T extends Task> {

        T create(Quest quest, long id);
    }

    private static final Map<String, TaskType<?>> REGISTRY = new LinkedHashMap<>();

    private final String id;
    private final Factory<T> factory;

    private TaskType(String id, Factory<T> factory) {
        this.id = id;
        this.factory = factory;
    }

    public static <T extends Task> TaskType<T> register(String id, Factory<T> factory) {
        if (REGISTRY.containsKey(id)) throw new IllegalArgumentException("Duplicate task type: " + id);
        TaskType<T> type = new TaskType<>(id, factory);
        REGISTRY.put(id, type);
        return type;
    }

    public static TaskType<?> get(String id) {
        return REGISTRY.get(id);
    }

    public static Collection<TaskType<?>> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public String getId() {
        return id;
    }

    T create(Quest quest, long id) {
        return factory.create(quest, id);
    }
}
