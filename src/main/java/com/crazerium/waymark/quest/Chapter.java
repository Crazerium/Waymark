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
    private final List<QuestLink> links = new ArrayList<>();

    private String title = "";
    private List<String> description = new ArrayList<>();
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

    public List<String> getDescription() {
        return description;
    }

    public void setDescription(List<String> description) {
        this.description = new ArrayList<>(description);
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

    /** Quests from other chapters shown on this chapter's map. */
    public List<QuestLink> getLinks() {
        return Collections.unmodifiableList(links);
    }

    public QuestLink addLink(Quest quest, double x, double y) {
        QuestLink link = new QuestLink(this, quest.getId(), x, y);
        links.add(link);
        return link;
    }

    public void removeLink(QuestLink link) {
        links.remove(link);
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
        if (!description.isEmpty()) json.add("description", JsonUtil.toArray(description));
        if (icon != null) json.add("icon", JsonUtil.writeStack(icon));
        JsonArray array = new JsonArray();
        for (Quest quest : quests) array.add(quest.toJson());
        json.add("quests", array);
        if (!links.isEmpty()) {
            JsonArray linkArray = new JsonArray();
            for (QuestLink link : links) linkArray.add(link.toJson());
            json.add("links", linkArray);
        }
        return json;
    }

    public static Chapter fromJson(QuestBook book, String filename, JsonObject json) {
        Chapter chapter = new Chapter(book, book.claimId(json, "Chapter '" + filename + "'"), filename);
        book.addChapter(chapter);
        chapter.title = JsonUtil.getString(json, "title", "");
        chapter.description = JsonUtil.getStringList(json, "description");
        chapter.icon = JsonUtil.readStack(JsonUtil.getObject(json, "icon"));
        for (JsonElement e : JsonUtil.getArray(json, "quests")) {
            if (!e.isJsonObject()) continue;
            JsonObject questJson = e.getAsJsonObject();
            Quest quest = new Quest(chapter, book.claimId(questJson, "Quest in '" + filename + "'"));
            chapter.quests.add(quest);
            book.register(quest);
            quest.readJson(questJson);
        }
        for (JsonElement e : JsonUtil.getArray(json, "links")) {
            if (!e.isJsonObject()) continue;
            QuestLink link = QuestLink.fromJson(chapter, e.getAsJsonObject());
            if (link != null) chapter.links.add(link);
        }
        return chapter;
    }
}
