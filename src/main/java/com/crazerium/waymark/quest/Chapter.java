package com.crazerium.waymark.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.crazerium.waymark.util.Ids;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A page of the quest book. Each chapter is stored in its own file, chapters/&lt;filename&gt;.json. */
public class Chapter extends QuestObject {

    private final QuestBook book;
    private final String filename;
    private final List<Quest> quests = new ArrayList<>();

    private String title = "";
    private ItemStack icon;

    public Chapter(QuestBook book, long id, String filename) {
        super(id);
        this.book = book;
        this.filename = filename;
    }

    @Override
    public QuestBook getBook() {
        return book;
    }

    public String getFilename() {
        return filename;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public void setIcon(ItemStack icon) {
        this.icon = icon;
    }

    public List<Quest> getQuests() {
        return Collections.unmodifiableList(quests);
    }

    public Quest createQuest(double x, double y) {
        Quest quest = new Quest(this, book.newId());
        quest.setPosition(x, y);
        quests.add(quest);
        book.register(quest);
        return quest;
    }

    public void removeQuest(Quest quest) {
        if (!quests.remove(quest)) return;
        for (Task task : new ArrayList<>(quest.getTasks())) quest.removeTask(task);
        for (Reward reward : new ArrayList<>(quest.getRewards())) quest.removeReward(reward);
        book.unregister(quest);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", Ids.format(getId()));
        json.addProperty("title", title);
        if (icon != null) json.add("icon", JsonUtil.writeStack(icon));
        JsonArray array = new JsonArray();
        for (Quest quest : quests) array.add(quest.toJson());
        json.add("quests", array);
        return json;
    }

    public static Chapter fromJson(QuestBook book, String filename, JsonObject json) {
        Chapter chapter = new Chapter(book, book.claimId(json, "Chapter '" + filename + "'"), filename);
        book.addChapter(chapter);
        chapter.title = JsonUtil.getString(json, "title", "");
        chapter.icon = JsonUtil.readStack(JsonUtil.getObject(json, "icon"));
        for (JsonElement e : JsonUtil.getArray(json, "quests")) {
            if (!e.isJsonObject()) continue;
            JsonObject questJson = e.getAsJsonObject();
            Quest quest = new Quest(chapter, book.claimId(questJson, "Quest in '" + filename + "'"));
            chapter.quests.add(quest);
            book.register(quest);
            quest.readJson(questJson);
        }
        return chapter;
    }
}
