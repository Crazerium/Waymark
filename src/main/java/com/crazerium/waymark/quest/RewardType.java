package com.crazerium.waymark.quest;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.google.gson.JsonObject;

/** A kind of reward, identified in files by its string id. Other mods can register their own. */
public final class RewardType<R extends Reward> {

    public interface Factory<R extends Reward> {

        R create(Quest quest, long id);
    }

    private static final Map<String, RewardType<?>> REGISTRY = new LinkedHashMap<>();

    private final String id;
    private final Factory<R> factory;
    private ItemStack icon;

    private RewardType(String id, Factory<R> factory) {
        this.id = id;
        this.factory = factory;
    }

    public static <R extends Reward> RewardType<R> register(String id, Factory<R> factory) {
        if (REGISTRY.containsKey(id)) throw new IllegalArgumentException("Duplicate reward type: " + id);
        RewardType<R> type = new RewardType<>(id, factory);
        REGISTRY.put(id, type);
        return type;
    }

    /** A type that is not in the registry, for placeholders that should never be created from files. */
    public static <R extends Reward> RewardType<R> unregistered(String id, Factory<R> factory) {
        return new RewardType<>(id, factory);
    }

    public static RewardType<?> get(String id) {
        return REGISTRY.get(id);
    }

    public static Collection<RewardType<?>> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** The fields the editor shows for this type. */
    public List<EditorField> editorFields() {
        return factory.create(null, 0)
            .editorFields();
    }

    /**
     * A detached Reward built from editor JSON, only for showing its title and icon in the editor. It belongs to no
     * quest, so anything that needs the quest book must not be called on it.
     */
    public R preview(JsonObject json) {
        R obj = factory.create(null, 0);
        obj.readJson(json);
        return obj;
    }

    /** Sets the icon shown next to this type in the editor's menus. */
    public RewardType<R> withIcon(ItemStack icon) {
        this.icon = icon;
        return this;
    }

    /** The menu icon, or null if none was set. */
    public ItemStack getIcon() {
        return icon;
    }

    public String getId() {
        return id;
    }

    R create(Quest quest, long id) {
        return factory.create(quest, id);
    }
}
