package com.crazerium.waymark.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * One editable field of a task or reward, as stored in its JSON. Types list their fields so the in-game editor can
 * build a form for any of them, including types added by other mods. The label is looked up as
 * {@code waymark.field.<key>}.
 */
public final class EditorField {

    public enum Kind {
        TEXT,
        INTEGER,
        BOOLEAN,
        /** One item without a count: {"id", "damage", "nbt"}. */
        ITEM,
        /** A list of items with their counts. */
        ITEM_LIST
    }

    public final String key;
    public final Kind kind;
    /** Value used when the JSON leaves the field out; null for items. */
    public final JsonElement defaultValue;

    private EditorField(String key, Kind kind, JsonElement defaultValue) {
        this.key = key;
        this.kind = kind;
        this.defaultValue = defaultValue;
    }

    public static EditorField text(String key, String def) {
        return new EditorField(key, Kind.TEXT, new JsonPrimitive(def));
    }

    public static EditorField integer(String key, long def) {
        return new EditorField(key, Kind.INTEGER, new JsonPrimitive(def));
    }

    public static EditorField bool(String key, boolean def) {
        return new EditorField(key, Kind.BOOLEAN, new JsonPrimitive(def));
    }

    public static EditorField item(String key) {
        return new EditorField(key, Kind.ITEM, null);
    }

    public static EditorField itemList(String key) {
        return new EditorField(key, Kind.ITEM_LIST, null);
    }
}
