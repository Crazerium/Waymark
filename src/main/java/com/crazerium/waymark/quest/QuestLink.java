package com.crazerium.waymark.quest;

import com.crazerium.waymark.util.Ids;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/**
 * Shows a quest from another chapter on this chapter's map. It is only a picture of that quest: progress, clicks and
 * dependency lines all go to the original.
 */
public class QuestLink {

    private final Chapter chapter;
    private long questId;
    private double x, y;
    private double size = 1.0;

    public QuestLink(Chapter chapter, long questId, double x, double y) {
        this.chapter = chapter;
        this.questId = questId;
        this.x = x;
        this.y = y;
    }

    public Chapter getChapter() {
        return chapter;
    }

    /** The linked quest, or null if it was deleted. */
    public Quest getQuest() {
        return chapter.getBook()
            .getQuest(questId);
    }

    public long getQuestId() {
        return questId;
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

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("quest", Ids.format(questId));
        json.addProperty("x", x);
        json.addProperty("y", y);
        if (size != 1.0) json.addProperty("size", size);
        return json;
    }

    static QuestLink fromJson(Chapter chapter, JsonObject json) {
        long id = Ids.parse(JsonUtil.getString(json, "quest", ""));
        if (id == Ids.NONE) return null;
        QuestLink link = new QuestLink(chapter, id, JsonUtil.getDouble(json, "x", 0), JsonUtil.getDouble(json, "y", 0));
        link.size = JsonUtil.getDouble(json, "size", 1.0);
        return link;
    }
}
