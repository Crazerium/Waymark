package com.crazerium.waymark.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.util.Ids;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Root of all quest data: the ordered chapters plus an index of every object by id. */
public class QuestBook {

    private final List<Chapter> chapters = new ArrayList<>();
    private final Map<Long, QuestObject> objects = new HashMap<>();
    private final Random random = new Random();

    public List<Chapter> getChapters() {
        return Collections.unmodifiableList(chapters);
    }

    public QuestObject get(long id) {
        return objects.get(id);
    }

    public Quest getQuest(long id) {
        QuestObject o = objects.get(id);
        return o instanceof Quest ? (Quest) o : null;
    }

    public int getObjectCount() {
        return objects.size();
    }

    public long newId() {
        long id;
        do {
            id = random.nextLong();
        } while (id == Ids.NONE || objects.containsKey(id));
        return id;
    }

    /** Reads the "id" field of an object being loaded, replacing it if it is missing or already taken. */
    long claimId(JsonObject json, String what) {
        long id = Ids.parse(JsonUtil.getString(json, "id", ""));
        if (id == Ids.NONE || objects.containsKey(id)) {
            long fresh = newId();
            Waymark.LOG
                .warn("{} has a missing or duplicate id '{}', assigned {}", what, json.get("id"), Ids.format(fresh));
            return fresh;
        }
        return id;
    }

    void register(QuestObject object) {
        objects.put(object.getId(), object);
    }

    void unregister(QuestObject object) {
        objects.remove(object.getId());
    }

    public Chapter createChapter(String title) {
        Chapter chapter = new Chapter(this, newId(), uniqueFilename(title));
        chapter.setTitle(title);
        addChapter(chapter);
        return chapter;
    }

    public void addChapter(Chapter chapter) {
        chapters.add(chapter);
        register(chapter);
    }

    public void removeChapter(Chapter chapter) {
        if (!chapters.remove(chapter)) return;
        for (Quest quest : new ArrayList<>(chapter.getQuests())) chapter.removeQuest(quest);
        unregister(chapter);
    }

    public String uniqueFilename(String title) {
        String base = title.toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "chapter";
        Set<String> taken = new HashSet<>();
        for (Chapter c : chapters) taken.add(c.getFilename());
        String name = base;
        for (int i = 2; taken.contains(name); i++) name = base + "_" + i;
        return name;
    }
}
