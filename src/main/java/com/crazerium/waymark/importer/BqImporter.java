package com.crazerium.waymark.importer;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.crazerium.waymark.util.Ids;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Converts a BetterQuesting 3 {@code DefaultQuests} folder (QuestLines/, Quests/, QuestLinesOrder.txt) into Waymark
 * chapter JSON. It only transforms JSON and never touches Minecraft classes, so it works without the pack's mods and
 * can run outside the game.
 *
 * <p>
 * Ids are derived from the BetterQuesting ids, so importing the same quests again gives the same Waymark ids and
 * players keep their progress.
 */
public class BqImporter {

    /** BetterQuesting positions are in pixels where a normal quest is 24 wide. */
    private static final double BQ_UNIT = 24.0;

    public static class Result {

        /** Chapter filename to chapter json, in book order. */
        public final Map<String, JsonObject> chapters = new LinkedHashMap<>();
        public final List<String> warnings = new ArrayList<>();
        public int quests, tasks, rewards, links, hiddenLines, unplaced;
        public final Map<String, Integer> convertedTypes = new TreeMap<>();

        void count(String what) {
            convertedTypes.merge(what, 1, Integer::sum);
        }
    }

    private Result result;
    private final Map<String, JsonObject> bqQuests = new HashMap<>();
    private final Set<String> placed = new HashSet<>();
    private final Set<String> usedFilenames = new HashSet<>();

    public Result convert(File dir) throws IOException {
        result = new Result();
        bqQuests.clear();
        placed.clear();
        usedFilenames.clear();

        File questsDir = new File(dir, "Quests");
        File linesDir = new File(dir, "QuestLines");
        if (!questsDir.isDirectory() || !linesDir.isDirectory())
            throw new IOException("Not a BetterQuesting DefaultQuests folder: " + dir);

        for (Path file : jsonFiles(questsDir.toPath())) {
            JsonObject quest = read(file);
            bqQuests.put(key(quest, "questID"), quest);
        }

        // Quest lines: the order file first (it may repeat lines), then any others alphabetically.
        Map<String, File> lineDirs = new TreeMap<>();
        Map<String, JsonObject> lineInfo = new HashMap<>();
        File[] dirs = linesDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File d : dirs) {
                File info = new File(d, "QuestLine.json");
                if (!info.isFile()) continue;
                JsonObject json = read(info.toPath());
                String k = key(json, "questLineID");
                lineDirs.put(k, d);
                lineInfo.put(k, json);
            }
        }
        List<String> order = new ArrayList<>();
        File orderFile = new File(dir, "QuestLinesOrder.txt");
        if (orderFile.isFile()) {
            for (String line : Files.readAllLines(orderFile.toPath(), StandardCharsets.UTF_8)) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String k = base64Key(
                    line.substring(0, colon)
                        .trim());
                if (k != null && lineDirs.containsKey(k) && !order.contains(k)) order.add(k);
            }
        }
        for (String k : lineDirs.keySet()) if (!order.contains(k)) order.add(k);

        for (String k : order) convertLine(lineInfo.get(k), lineDirs.get(k));

        List<String> leftovers = new ArrayList<>();
        for (String k : bqQuests.keySet()) if (!placed.contains(k)) leftovers.add(k);
        if (!leftovers.isEmpty()) {
            leftovers.sort(null);
            result.unplaced = leftovers.size();
            JsonArray quests = new JsonArray();
            int i = 0;
            for (String k : leftovers) {
                quests.add(convertQuest(bqQuests.get(k), (i % 20) * 1.5, (i / 20) * 1.5, 1.0));
                i++;
            }
            JsonObject chapter = new JsonObject();
            chapter.addProperty("id", Ids.format(mix(0x5EC2E7L, 0x1EF7L, 0xC4A7)));
            chapter.addProperty("title", "Not in any quest line");
            chapter.add("quests", quests);
            result.chapters.put(filename("unsorted"), chapter);
        }
        return result;
    }

    private void convertLine(JsonObject line, File dir) throws IOException {
        JsonObject props = props(line);
        long[] id = ids(line, "questLineID");

        JsonObject chapter = new JsonObject();
        chapter.addProperty("id", Ids.format(mix(id[0], id[1], 0xC4A7)));
        String title = str(props, "name:8", "Quest line");
        chapter.addProperty("title", title);
        JsonObject icon = item(obj(props, "icon:10"));
        if (icon != null) chapter.add("icon", icon);
        List<String> desc = lines(str(props, "desc:8", ""));
        if (!desc.isEmpty()) chapter.add("description", toArray(desc));

        JsonArray quests = new JsonArray();
        JsonArray links = new JsonArray();
        for (Path file : jsonFiles(dir.toPath())) {
            if (file.getFileName()
                .toString()
                .equals("QuestLine.json")) continue;
            JsonObject entry = read(file);
            String k = key(entry, "questID");
            JsonObject bq = bqQuests.get(k);
            if (bq == null) {
                result.warnings.add(title + ": entry for missing quest " + k);
                continue;
            }
            int sizeX = num(entry, "sizeX:3", 24);
            int sizeY = num(entry, "sizeY:3", 24);
            double x = (num(entry, "x:3", 0) + sizeX / 2.0) / BQ_UNIT;
            double y = (num(entry, "y:3", 0) + sizeY / 2.0) / BQ_UNIT;
            if (!placed.add(k)) {
                // A Waymark quest lives in one chapter; BetterQuesting's other placements become links to it.
                long[] bqId = ids(bq, "questID");
                JsonObject link = new JsonObject();
                link.addProperty("quest", Ids.format(questId(bqId[0], bqId[1])));
                link.addProperty("x", round(x));
                link.addProperty("y", round(y));
                if (sizeX != 24) link.addProperty("size", round(sizeX / BQ_UNIT));
                links.add(link);
                result.links++;
                continue;
            }
            quests.add(convertQuest(bq, x, y, sizeX / BQ_UNIT));
        }
        chapter.add("quests", quests);
        if (links.size() > 0) chapter.add("links", links);
        result.chapters.put(filename(title), chapter);
    }

    private JsonObject convertQuest(JsonObject bq, double x, double y, double size) {
        result.quests++;
        JsonObject props = props(bq);
        long[] bqId = ids(bq, "questID");
        long id = questId(bqId[0], bqId[1]);
        String title = str(props, "name:8", "");

        JsonObject quest = new JsonObject();
        quest.addProperty("id", Ids.format(id));
        if (!title.isEmpty()) quest.addProperty("title", title);
        List<String> desc = lines(str(props, "desc:8", ""));
        if (!desc.isEmpty()) quest.add("description", toArray(desc));
        JsonObject icon = item(obj(props, "icon:10"));
        if (icon != null) quest.add("icon", icon);
        quest.addProperty("x", round(x));
        quest.addProperty("y", round(y));
        if (size != 1.0) quest.addProperty("size", round(size));

        JsonArray deps = new JsonArray();
        JsonArray hidden = new JsonArray();
        for (Map.Entry<String, JsonElement> e : BqNbt.sortedByIndex(obj(bq, "preRequisites:9"))) {
            if (!e.getValue()
                .isJsonObject()) continue;
            long[] dep = ids(
                e.getValue()
                    .getAsJsonObject(),
                "questID");
            deps.add(new JsonPrimitive(Ids.format(questId(dep[0], dep[1]))));
            // Type 1 ("implicit") prerequisites count but BetterQuesting does not draw their line.
            if (num(
                e.getValue()
                    .getAsJsonObject(),
                "type:1",
                0) == 1) {
                hidden.add(new JsonPrimitive(Ids.format(questId(dep[0], dep[1]))));
                result.hiddenLines++;
            }
        }
        if (deps.size() > 0) quest.add("dependencies", deps);
        if (hidden.size() > 0) quest.add("hidden_lines", hidden);

        String questLogic = logic(str(props, "questLogic:8", "AND"), title);
        if (!questLogic.equals("all")) quest.addProperty("dependency_mode", questLogic);
        String taskLogic = logic(str(props, "taskLogic:8", "AND"), title);
        if (!taskLogic.equals("all")) quest.addProperty("task_mode", taskLogic);

        String visibility = visibility(str(props, "visibility:8", "NORMAL"));
        if (!visibility.equals("always")) quest.addProperty("visibility", visibility);
        if (num(props, "autoClaim:1", 0) == 1) quest.addProperty("auto_claim", true);
        if (num(props, "repeatTime:3", -1) >= 0) {
            result.warnings
                .add("'" + plain(title) + "' is repeatable in BetterQuesting; Waymark imports it as a normal quest");
            result.count("repeatable quest (not supported yet)");
        }

        JsonArray tasks = new JsonArray();
        int taskIndex = 0;
        for (Map.Entry<String, JsonElement> e : BqNbt.sortedByIndex(obj(bq, "tasks:9"))) {
            if (!e.getValue()
                .isJsonObject()) continue;
            for (JsonObject task : convertTask(
                e.getValue()
                    .getAsJsonObject(),
                title)) {
                task.addProperty("id", Ids.format(mix(id, 1000 + taskIndex++, 0x7A5C)));
                tasks.add(reorder(task));
            }
        }
        quest.add("tasks", tasks);
        result.tasks += tasks.size();

        JsonArray rewards = new JsonArray();
        int rewardIndex = 0;
        for (Map.Entry<String, JsonElement> e : BqNbt.sortedByIndex(obj(bq, "rewards:9"))) {
            if (!e.getValue()
                .isJsonObject()) continue;
            for (JsonObject reward : convertReward(
                e.getValue()
                    .getAsJsonObject(),
                title)) {
                reward.addProperty("id", Ids.format(mix(id, 2000 + rewardIndex++, 0x2E3A)));
                rewards.add(reorder(reward));
            }
        }
        if (rewards.size() > 0) quest.add("rewards", rewards);
        result.rewards += rewards.size();
        return quest;
    }

    private List<JsonObject> convertTask(JsonObject bq, String questTitle) {
        String type = str(bq, "taskID:8", "");
        List<JsonObject> out = new ArrayList<>();
        switch (type) {
            case "bq_standard:retrieval":
            case "bq_standard:optional_retrieval": {
                boolean optional = type.endsWith("optional_retrieval");
                boolean consume = num(bq, "consume:1", 0) == 1;
                boolean matchNbt = num(bq, "ignoreNBT:1", 1) == 0;
                List<JsonObject> items = items(obj(bq, "requiredItems:9"));
                if (num(bq, "requireOnlyOneItem:1", 0) == 1 && items.size() > 1) {
                    // Any one of the listed items: one task with alternatives.
                    JsonObject task = itemTask(items.get(0), consume, matchNbt, optional);
                    JsonArray alternatives = new JsonArray();
                    for (int i = 1; i < items.size(); i++) alternatives.add(stripCount(items.get(i)));
                    task.add("alternatives", alternatives);
                    out.add(task);
                } else {
                    for (JsonObject item : items) out.add(itemTask(item, consume, matchNbt, optional));
                }
                result.count(type + " -> item");
                break;
            }
            case "bq_standard:crafting":
                for (JsonObject item : items(obj(bq, "requiredItems:9"))) {
                    JsonObject task = new JsonObject();
                    task.addProperty("type", "craft");
                    putItemFilter(task, item, num(bq, "ignoreNBT:1", 1) == 0);
                    if (num(bq, "allowCraft:1", 1) == 0) task.addProperty("crafting", false);
                    if (num(bq, "allowSmelt:1", 1) == 0) task.addProperty("smelting", false);
                    out.add(task);
                }
                result.count(type + " -> craft");
                break;
            case "bq_standard:checkbox": {
                JsonObject task = new JsonObject();
                task.addProperty("type", "checkmark");
                out.add(task);
                result.count(type + " -> checkmark");
                break;
            }
            case "bq_standard:hunt": {
                JsonObject task = new JsonObject();
                task.addProperty("type", "kill");
                task.addProperty("entity", str(bq, "target:8", "Zombie"));
                int required = num(bq, "required:3", 1);
                if (required != 1) task.addProperty("count", required);
                if (num(bq, "subtypes:1", 0) == 1) task.addProperty("subtypes", true);
                out.add(task);
                result.count(type + " -> kill");
                break;
            }
            case "bq_standard:location": {
                JsonObject task = new JsonObject();
                task.addProperty("type", "location");
                String name = str(bq, "name:8", "");
                if (!name.isEmpty()) task.addProperty("title", name);
                task.addProperty("dimension", num(bq, "dimension:3", 0));
                int range = num(bq, "range:3", -1);
                if (range >= 0) {
                    task.addProperty("x", num(bq, "posX:3", 0));
                    task.addProperty("y", num(bq, "posY:3", 0));
                    task.addProperty("z", num(bq, "posZ:3", 0));
                    task.addProperty("range", range);
                }
                int biome = num(bq, "biome:3", -1);
                if (biome >= 0) task.addProperty("biome", biome);
                if (num(bq, "invert:1", 0) == 1 || !str(bq, "structure:8", "").isEmpty()) result.warnings
                    .add("'" + plain(questTitle) + "': location task uses invert/structure, imported without it");
                out.add(task);
                result.count(type + " -> location");
                break;
            }
            case "bq_standard:fluid": {
                // No fluid task yet: keep the quest completable with a checkmark that says what is needed.
                StringBuilder title = new StringBuilder();
                for (Map.Entry<String, JsonElement> e : BqNbt.sortedByIndex(obj(bq, "requiredFluids:9"))) {
                    if (!e.getValue()
                        .isJsonObject()) continue;
                    JsonObject fluid = e.getValue()
                        .getAsJsonObject();
                    if (title.length() > 0) title.append(", ");
                    title.append(num(fluid, "Amount:3", 1000))
                        .append(" mB ")
                        .append(str(fluid, "FluidName:8", "?"));
                }
                JsonObject task = new JsonObject();
                task.addProperty("type", "checkmark");
                task.addProperty("title", title.toString());
                out.add(task);
                result.count(type + " -> checkmark (no fluid task yet)");
                break;
            }
            case "bq_standard:meeting": {
                JsonObject task = new JsonObject();
                task.addProperty("type", "checkmark");
                task.addProperty("title", "Meet: " + str(bq, "target:8", "?"));
                out.add(task);
                result.count(type + " -> checkmark");
                break;
            }
            default: {
                // Kept as an unknown task so nothing is lost; it shows up red in the book.
                JsonObject task = new JsonObject();
                task.addProperty("type", type.isEmpty() ? "bq:unknown" : type);
                task.add("bq_data", bq);
                out.add(task);
                result.warnings.add("'" + plain(questTitle) + "': unsupported task type " + type);
                result.count(type + " -> unknown");
            }
        }
        return out;
    }

    private JsonObject itemTask(JsonObject item, boolean consume, boolean matchNbt, boolean optional) {
        JsonObject task = new JsonObject();
        task.addProperty("type", "item");
        if (optional) task.addProperty("optional", true);
        putItemFilter(task, item, matchNbt);
        if (!consume) task.addProperty("consume", false);
        return task;
    }

    /** Writes item/ore_dict/count/match_nbt; match_nbt only matters if the item has NBT. */
    private void putItemFilter(JsonObject task, JsonObject item, boolean matchNbt) {
        int count = item.has("count") ? item.get("count")
            .getAsInt() : 1;
        String ore = item.has("ore_dict") ? item.remove("ore_dict")
            .getAsString() : "";
        task.add("item", stripCount(item));
        if (!ore.isEmpty()) task.addProperty("ore_dict", ore);
        if (count != 1) task.addProperty("count", count);
        if (matchNbt && item.has("nbt")) task.addProperty("match_nbt", true);
    }

    private List<JsonObject> convertReward(JsonObject bq, String questTitle) {
        String type = str(bq, "rewardID:8", "");
        List<JsonObject> out = new ArrayList<>();
        switch (type) {
            case "bq_standard:item":
                for (JsonObject item : items(obj(bq, "rewards:9"))) {
                    item.remove("ore_dict");
                    int count = item.has("count") ? item.get("count")
                        .getAsInt() : 1;
                    JsonObject reward = new JsonObject();
                    reward.addProperty("type", "item");
                    reward.add("item", stripCount(item));
                    if (count != 1) reward.addProperty("count", count);
                    out.add(reward);
                }
                result.count(type + " -> item");
                break;
            case "bq_standard:choice": {
                JsonArray choices = new JsonArray();
                for (JsonObject item : items(obj(bq, "choices:9"))) {
                    item.remove("ore_dict");
                    choices.add(item);
                }
                JsonObject reward = new JsonObject();
                reward.addProperty("type", "choice");
                reward.add("choices", choices);
                out.add(reward);
                result.count(type + " -> choice");
                break;
            }
            case "bq_standard:questcompletion": {
                String k = base64Key(str(bq, "questID:8", ""));
                JsonObject reward = new JsonObject();
                reward.addProperty("type", "complete_quest");
                if (k != null) {
                    String[] parts = k.split(":");
                    reward
                        .addProperty("quest", Ids.format(questId(Long.parseLong(parts[0]), Long.parseLong(parts[1]))));
                }
                out.add(reward);
                result.count(type + " -> complete_quest");
                break;
            }
            case "bq_standard:xp": {
                JsonObject reward = new JsonObject();
                reward.addProperty("type", "xp");
                reward.addProperty("amount", num(bq, "amount:3", 1));
                if (num(bq, "isLevels:1", 0) == 1) reward.addProperty("levels", true);
                out.add(reward);
                result.count(type + " -> xp");
                break;
            }
            default: {
                JsonObject reward = new JsonObject();
                reward.addProperty("type", type.isEmpty() ? "bq:unknown" : type);
                reward.add("bq_data", bq);
                out.add(reward);
                result.warnings.add("'" + plain(questTitle) + "': unsupported reward type " + type);
                result.count(type + " -> unknown");
            }
        }
        return out;
    }

    // ---- items ----

    private List<JsonObject> items(JsonObject list) {
        List<JsonObject> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : BqNbt.sortedByIndex(list)) {
            if (!e.getValue()
                .isJsonObject()) continue;
            JsonObject item = item(
                e.getValue()
                    .getAsJsonObject());
            if (item != null) out.add(item);
        }
        return out;
    }

    /** {"id:8": ..., "Damage:2": ..., "Count:3": ..., "OreDict:8": ..., "tag:10": {...}} to a Waymark item. */
    private static JsonObject item(JsonObject bq) {
        if (bq == null) return null;
        String id = str(bq, "id:8", "");
        if (id.isEmpty()) return null;
        JsonObject item = new JsonObject();
        item.addProperty("id", id);
        int damage = num(bq, "Damage:2", 0);
        if (damage != 0) item.addProperty("damage", damage);
        int count = num(bq, "Count:3", 1);
        if (count != 1) item.addProperty("count", count);
        JsonObject tag = obj(bq, "tag:10");
        if (tag != null && tag.entrySet()
            .size() > 0) item.addProperty("nbt", BqNbt.compound(tag));
        String ore = str(bq, "OreDict:8", "");
        if (!ore.isEmpty()) item.addProperty("ore_dict", ore);
        return item;
    }

    private static JsonObject stripCount(JsonObject item) {
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> e : item.entrySet()) {
            if (!e.getKey()
                .equals("count")
                && !e.getKey()
                    .equals("ore_dict"))
                copy.add(e.getKey(), e.getValue());
        }
        return copy;
    }

    /** Puts "id" and "type" first so the files read nicely. */
    private static JsonObject reorder(JsonObject json) {
        JsonObject out = new JsonObject();
        if (json.has("id")) out.add("id", json.get("id"));
        if (json.has("type")) out.add("type", json.get("type"));
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
        }
        return out;
    }

    // ---- mapping helpers ----

    private String logic(String bq, String questTitle) {
        switch (bq) {
            case "AND":
                return "all";
            case "OR":
                return "any";
            case "XOR":
                return "one";
            default:
                result.warnings.add("'" + plain(questTitle) + "': logic " + bq + " is not supported, using AND");
                return "all";
        }
    }

    private static String visibility(String bq) {
        switch (bq) {
            case "UNLOCKED":
            case "CHAIN":
                return "unlocked";
            case "SECRET":
                return "completed";
            case "HIDDEN":
                return "never";
            default:
                return "always";
        }
    }

    private static long questId(long high, long low) {
        return mix(high, low, 0x9E57);
    }

    /** Deterministic 64-bit id from two numbers and a salt (SplitMix64 finalizer); never zero. */
    static long mix(long a, long b, long salt) {
        long z = a * 0x9E3779B97F4A7C15L ^ b ^ salt * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return z == Ids.NONE ? 1 : z;
    }

    private static String key(JsonObject json, String prefix) {
        long[] id = ids(json, prefix);
        return id[0] + ":" + id[1];
    }

    private static long[] ids(JsonObject json, String prefix) {
        return new long[] { lng(json, prefix + "High:4"), lng(json, prefix + "Low:4") };
    }

    /** BetterQuesting writes 128-bit ids as base64 of 16 bytes: the high long, then the low long. */
    private static String base64Key(String text) {
        try {
            byte[] bytes = Base64.getUrlDecoder()
                .decode(
                    text.replace('+', '-')
                        .replace('/', '_'));
            if (bytes.length != 16) return null;
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            return buf.getLong() + ":" + buf.getLong();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String filename(String title) {
        String base = plain(title).toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "chapter";
        String name = base;
        for (int i = 2; !usedFilenames.add(name); i++) name = base + "_" + i;
        return name;
    }

    /** Removes formatting codes, for file names and messages. */
    private static String plain(String text) {
        return text.replaceAll("§.", "");
    }

    private static List<String> lines(String text) {
        List<String> list = new ArrayList<>();
        if (text.isEmpty()) return list;
        for (String line : text.replace("\r", "")
            .split("\n", -1)) list.add(line);
        while (!list.isEmpty() && list.get(list.size() - 1)
            .isEmpty()) list.remove(list.size() - 1);
        return list;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static JsonArray toArray(List<String> list) {
        JsonArray array = new JsonArray();
        for (String s : list) array.add(new JsonPrimitive(s));
        return array;
    }

    private static JsonObject props(JsonObject json) {
        JsonObject p = obj(json, "properties:10");
        JsonObject bq = p != null ? obj(p, "betterquesting:10") : null;
        return bq != null ? bq : new JsonObject();
    }

    private static JsonObject obj(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String str(JsonObject json, String key, String def) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    private static int num(JsonObject json, String key, int def) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsInt() : def;
    }

    private static long lng(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
    }

    private static List<Path> jsonFiles(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(
                p -> p.toString()
                    .endsWith(".json"))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader)
                .getAsJsonObject();
        }
    }
}
