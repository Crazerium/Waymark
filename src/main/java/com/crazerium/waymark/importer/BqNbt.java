package com.crazerium.waymark.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * BetterQuesting stores NBT as JSON whose keys carry the tag type, e.g. {@code "Count:3": 1} or
 * {@code "Lore:9": {"0:8": "text"}}. This turns that into the SNBT text Waymark uses. Plain Java on purpose, so the
 * importer can run without Minecraft.
 */
final class BqNbt {

    private BqNbt() {}

    /** Splits "name:type" into the name and the numeric type; a key without a type is a string. */
    static String name(String key) {
        int i = key.lastIndexOf(':');
        return i < 0 ? key : key.substring(0, i);
    }

    static int type(String key) {
        int i = key.lastIndexOf(':');
        if (i < 0) return 8;
        try {
            return Integer.parseInt(key.substring(i + 1));
        } catch (NumberFormatException e) {
            return 8;
        }
    }

    static String compound(JsonObject json) {
        StringBuilder sb = new StringBuilder();
        writeCompound(json, sb);
        return sb.toString();
    }

    private static void writeCompound(JsonObject json, StringBuilder sb) {
        // Sorted like Waymark's own SNBT writer, so loading and saving again changes nothing.
        Map<String, Map.Entry<String, JsonElement>> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) sorted.put(name(e.getKey()), e);
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Map.Entry<String, JsonElement>> e : sorted.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            String name = e.getKey();
            if (name.matches("[A-Za-z0-9._+-]+")) sb.append(name);
            else quote(name, sb);
            sb.append(':');
            writeValue(
                type(
                    e.getValue()
                        .getKey()),
                e.getValue()
                    .getValue(),
                sb);
        }
        sb.append('}');
    }

    private static void writeValue(int type, JsonElement value, StringBuilder sb) {
        switch (type) {
            case 1:
                sb.append(value.getAsByte())
                    .append('b');
                break;
            case 2:
                sb.append(value.getAsShort())
                    .append('s');
                break;
            case 3:
                sb.append(value.getAsInt());
                break;
            case 4:
                sb.append(value.getAsLong())
                    .append('L');
                break;
            case 5:
                sb.append(value.getAsFloat())
                    .append('f');
                break;
            case 6:
                sb.append(value.getAsDouble())
                    .append('d');
                break;
            case 7:
            case 11: {
                sb.append(type == 7 ? "[B;" : "[I;");
                List<JsonElement> items = listItems(value);
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(
                        items.get(i)
                            .getAsLong());
                    if (type == 7) sb.append('b');
                }
                sb.append(']');
                break;
            }
            case 9: {
                sb.append('[');
                if (value.isJsonObject()) {
                    boolean first = true;
                    for (Map.Entry<String, JsonElement> e : sortedByIndex(value.getAsJsonObject())) {
                        if (!first) sb.append(',');
                        first = false;
                        writeValue(type(e.getKey()), e.getValue(), sb);
                    }
                }
                sb.append(']');
                break;
            }
            case 10:
                writeCompound(value.isJsonObject() ? value.getAsJsonObject() : new JsonObject(), sb);
                break;
            default:
                quote(value.isJsonPrimitive() ? value.getAsString() : value.toString(), sb);
        }
    }

    private static List<JsonElement> listItems(JsonElement value) {
        List<JsonElement> items = new ArrayList<>();
        if (value.isJsonArray()) for (JsonElement e : value.getAsJsonArray()) items.add(e);
        else if (value.isJsonObject())
            for (Map.Entry<String, JsonElement> e : sortedByIndex(value.getAsJsonObject())) items.add(e.getValue());
        return items;
    }

    /** List entries are keyed "0:10", "1:10", ...; they must stay in numeric order. */
    static List<Map.Entry<String, JsonElement>> sortedByIndex(JsonObject json) {
        List<Map.Entry<String, JsonElement>> list = new ArrayList<>(json.entrySet());
        list.sort((a, b) -> Integer.compare(index(a.getKey()), index(b.getKey())));
        return list;
    }

    private static int index(String key) {
        try {
            return Integer.parseInt(name(key));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
    }
}
