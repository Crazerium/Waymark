package com.crazerium.waymark.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.item.ItemMissing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class JsonUtil {

    private JsonUtil() {}

    private static JsonPrimitive primitive(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsJsonPrimitive() : null;
    }

    public static String getString(JsonObject json, String key, String def) {
        JsonPrimitive p = primitive(json, key);
        return p != null ? p.getAsString() : def;
    }

    public static int getInt(JsonObject json, String key, int def) {
        JsonPrimitive p = primitive(json, key);
        return p != null && p.isNumber() ? p.getAsInt() : def;
    }

    public static long getLong(JsonObject json, String key, long def) {
        JsonPrimitive p = primitive(json, key);
        return p != null && p.isNumber() ? p.getAsLong() : def;
    }

    public static double getDouble(JsonObject json, String key, double def) {
        JsonPrimitive p = primitive(json, key);
        return p != null && p.isNumber() ? p.getAsDouble() : def;
    }

    public static boolean getBoolean(JsonObject json, String key, boolean def) {
        JsonPrimitive p = primitive(json, key);
        return p != null && p.isBoolean() ? p.getAsBoolean() : def;
    }

    public static JsonObject getObject(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static JsonArray getArray(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    public static List<String> getStringList(JsonObject json, String key) {
        List<String> list = new ArrayList<>();
        for (JsonElement e : getArray(json, key)) {
            if (e.isJsonPrimitive()) list.add(e.getAsString());
        }
        return list;
    }

    public static JsonArray toArray(List<String> list) {
        JsonArray array = new JsonArray();
        for (String s : list) array.add(new JsonPrimitive(s));
        return array;
    }

    /** {"id": "minecraft:stone", "damage": 1, "count": 2, "nbt": "{...}"}; defaults are omitted. */
    public static JsonObject writeStack(ItemStack stack) {
        JsonObject original = ItemMissing.original(stack);
        if (original != null) {
            JsonObject json = new JsonObject();
            for (Map.Entry<String, JsonElement> e : original.entrySet()) {
                if (!e.getKey()
                    .equals("count")) json.add(e.getKey(), e.getValue());
            }
            if (stack.stackSize != 1) json.addProperty("count", stack.stackSize);
            return json;
        }
        JsonObject json = new JsonObject();
        json.addProperty("id", Item.itemRegistry.getNameForObject(stack.getItem()));
        if (stack.getItemDamage() != 0) json.addProperty("damage", stack.getItemDamage());
        if (stack.stackSize != 1) json.addProperty("count", stack.stackSize);
        if (stack.hasTagCompound()) json.addProperty("nbt", Snbt.write(stack.getTagCompound()));
        return json;
    }

    private static final Set<String> warnedIds = new HashSet<>();

    /**
     * Returns null for a null json. Items that do not exist (their mod is not installed) or have broken NBT become
     * {@link ItemMissing} placeholders that remember the original json.
     */
    public static ItemStack readStack(JsonObject json) {
        if (json == null) return null;
        String id = getString(json, "id", "");
        int count = getInt(json, "count", 1);
        Item item = (Item) Item.itemRegistry.getObject(id);
        if (item == null) {
            if (warnedIds.add(id)) Waymark.LOG.warn("Unknown item '{}', keeping it as a placeholder", id);
            return missing(json, count);
        }
        ItemStack stack = new ItemStack(item, count, getInt(json, "damage", 0));
        String nbt = getString(json, "nbt", null);
        if (nbt != null) {
            try {
                stack.setTagCompound(Snbt.parseCompound(nbt));
            } catch (IllegalArgumentException e) {
                Waymark.LOG.warn("Invalid NBT for item '{}': {}", id, e.getMessage());
                return missing(json, count);
            }
        }
        return stack;
    }

    private static ItemStack missing(JsonObject json, int count) {
        ItemStack stack = ItemMissing.create(json);
        stack.stackSize = count;
        return stack;
    }
}
