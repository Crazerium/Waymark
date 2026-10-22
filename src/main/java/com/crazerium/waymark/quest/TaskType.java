package com.crazerium.waymark.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

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

    /** A type that is not in the registry, for placeholders that should never be created from files. */
    public static <T extends Task> TaskType<T> unregistered(String id, Factory<T> factory) {
        return new TaskType<>(id, factory);
    }

    public static TaskType<?> get(String id) {
        return REGISTRY.get(id);
    }

    public static Collection<TaskType<?>> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** The fields the editor shows for this type. */
    public List<EditorField> editorFields() {
        return factory.create(null, 0)
            .editorFields();
    }

    /**
     * A detached Task built from editor JSON, only for showing its title and icon in the editor. It belongs to no
     * quest, so anything that needs the quest book must not be called on it.
     */
    public T preview(JsonObject json) {
        T obj = factory.create(null, 0);
        obj.readJson(json);
        return obj;
    }

    public String getId() {
        return id;
    }

    T create(Quest quest, long id) {
        return factory.create(quest, id);
    }
}
