package com.crazerium.waymark.quest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.crazerium.waymark.util.Ids;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/**
 * Changes to the quest book made in the editor. An edit is a small JSON object such as
 * {@code {"op": "move_quest", "quest": "...", "x": 2, "y": 3}}. The server checks and applies it, saves, and sends
 * the same edit to every client, which applies it to its own copy. Applying is deterministic, so all copies stay
 * equal without resending the whole book.
 */
public final class BookEdits {

    public static final String MOVE_QUEST = "move_quest";
    public static final String MOVE_LINK = "move_link";
    public static final String CREATE_QUEST = "create_quest";
    public static final String DELETE_QUEST = "delete_quest";
    public static final String DELETE_LINK = "delete_link";
    public static final String TOGGLE_DEPENDENCY = "toggle_dependency";
    public static final String UPDATE_QUEST = "update_quest";
    public static final String MOVE_QUEST_TO_CHAPTER = "move_quest_to_chapter";
    public static final String ADD_LINK = "add_link";
    public static final String CREATE_CHAPTER = "create_chapter";
    public static final String UPDATE_CHAPTER = "update_chapter";
    public static final String DELETE_CHAPTER = "delete_chapter";
    public static final String MOVE_CHAPTER = "move_chapter";

    /** Moves a quest to another chapter, keeping its id, so progress and dependencies stay. */
    public static JsonObject moveQuestToChapter(Quest quest, Chapter chapter) {
        JsonObject op = op(MOVE_QUEST_TO_CHAPTER);
        op.addProperty("quest", Ids.format(quest.getId()));
        op.addProperty("chapter", Ids.format(chapter.getId()));
        return op;
    }

    public static JsonObject addLink(Chapter chapter, Quest quest, double x, double y) {
        JsonObject op = op(ADD_LINK);
        op.addProperty("chapter", Ids.format(chapter.getId()));
        op.addProperty("quest", Ids.format(quest.getId()));
        op.addProperty("x", x);
        op.addProperty("y", y);
        return op;
    }

    /** The server fills in "id" and "filename" before applying. */
    public static JsonObject createChapter(String title) {
        JsonObject op = op(CREATE_CHAPTER);
        op.addProperty("title", title);
        return op;
    }

    /** {@code data} holds "title", "description" and "icon", as in a chapter file. */
    public static JsonObject updateChapter(Chapter chapter, JsonObject data) {
        JsonObject op = op(UPDATE_CHAPTER);
        op.addProperty("chapter", Ids.format(chapter.getId()));
        op.add("data", data);
        return op;
    }

    /** Deletes a chapter and every quest in it. */
    public static JsonObject deleteChapter(Chapter chapter) {
        JsonObject op = op(DELETE_CHAPTER);
        op.addProperty("chapter", Ids.format(chapter.getId()));
        return op;
    }

    public static JsonObject moveChapter(Chapter chapter, int index) {
        JsonObject op = op(MOVE_CHAPTER);
        op.addProperty("chapter", Ids.format(chapter.getId()));
        op.addProperty("index", index);
        return op;
    }

    private BookEdits() {}

    public static JsonObject moveQuest(Quest quest, double x, double y) {
        JsonObject op = op(MOVE_QUEST);
        op.addProperty("quest", Ids.format(quest.getId()));
        op.addProperty("x", x);
        op.addProperty("y", y);
        return op;
    }

    public static JsonObject moveLink(Chapter chapter, int index, double x, double y) {
        JsonObject op = op(MOVE_LINK);
        op.addProperty("chapter", Ids.format(chapter.getId()));
        op.addProperty("index", index);
        op.addProperty("x", x);
        op.addProperty("y", y);
        return op;
    }

    /** The server fills in the new quest's "id" before applying. */
    public static JsonObject createQuest(Chapter chapter, double x, double y) {
        JsonObject op = op(CREATE_QUEST);
        op.addProperty("chapter", Ids.format(chapter.getId()));
        op.addProperty("x", x);
        op.addProperty("y", y);
        return op;
    }

    public static JsonObject deleteQuest(Quest quest) {
        JsonObject op = op(DELETE_QUEST);
        op.addProperty("quest", Ids.format(quest.getId()));
        return op;
    }

    public static JsonObject deleteLink(Chapter chapter, int index) {
        JsonObject op = op(DELETE_LINK);
        op.addProperty("chapter", Ids.format(chapter.getId()));
        op.addProperty("index", index);
        return op;
    }

    /** Makes {@code quest} depend on {@code dependency}, or removes that dependency if it exists. */
    public static JsonObject toggleDependency(Quest quest, Quest dependency) {
        JsonObject op = op(TOGGLE_DEPENDENCY);
        op.addProperty("quest", Ids.format(quest.getId()));
        op.addProperty("dependency", Ids.format(dependency.getId()));
        return op;
    }

    /**
     * Replaces a quest's properties, tasks and rewards with {@code data} (quest JSON from the editor). The server
     * gives tasks and rewards without a usable id a new one before applying.
     */
    public static JsonObject updateQuest(Quest quest, JsonObject data) {
        JsonObject op = op(UPDATE_QUEST);
        op.addProperty("quest", Ids.format(quest.getId()));
        op.add("data", data);
        return op;
    }

    private static JsonObject op(String name) {
        JsonObject op = new JsonObject();
        op.addProperty("op", name);
        return op;
    }

    /**
     * Applies an edit and returns the chapters whose files changed.
     *
     * @throws IllegalArgumentException if the edit is invalid; the book is left unchanged.
     */
    public static Set<Chapter> apply(QuestBook book, JsonObject op) {
        Set<Chapter> changed = new LinkedHashSet<>();
        String name = JsonUtil.getString(op, "op", "");
        switch (name) {
            case MOVE_QUEST: {
                Quest quest = quest(book, op, "quest");
                quest.setPosition(coord(op, "x"), coord(op, "y"));
                changed.add(quest.getChapter());
                break;
            }
            case MOVE_LINK: {
                Chapter chapter = chapter(book, op);
                link(chapter, op).setPosition(coord(op, "x"), coord(op, "y"));
                changed.add(chapter);
                break;
            }
            case CREATE_QUEST: {
                Chapter chapter = chapter(book, op);
                long id = Ids.parse(JsonUtil.getString(op, "id", ""));
                if (id == Ids.NONE || book.get(id) != null) throw new IllegalArgumentException("Bad new quest id");
                chapter.createQuest(id, coord(op, "x"), coord(op, "y"));
                changed.add(chapter);
                break;
            }
            case DELETE_QUEST: {
                Quest quest = quest(book, op, "quest");
                long id = quest.getId();
                quest.getChapter()
                    .removeQuest(quest);
                changed.add(quest.getChapter());
                // Nothing may keep pointing at the deleted quest.
                for (Chapter chapter : book.getChapters()) {
                    if (chapter.removeLinksTo(id)) changed.add(chapter);
                    for (Quest other : chapter.getQuests()) {
                        if (other.removeDependencyId(id)) changed.add(chapter);
                    }
                }
                break;
            }
            case DELETE_LINK: {
                Chapter chapter = chapter(book, op);
                chapter.removeLink(link(chapter, op));
                changed.add(chapter);
                break;
            }
            case TOGGLE_DEPENDENCY: {
                Quest quest = quest(book, op, "quest");
                Quest dependency = quest(book, op, "dependency");
                if (quest == dependency) throw new IllegalArgumentException("A quest cannot depend on itself");
                if (quest.getDependencyIds()
                    .contains(dependency.getId())) {
                    quest.removeDependency(dependency);
                } else {
                    if (dependsOn(dependency, quest))
                        throw new IllegalArgumentException("That would make a dependency loop");
                    quest.addDependency(dependency);
                }
                changed.add(quest.getChapter());
                break;
            }
            case MOVE_QUEST_TO_CHAPTER: {
                Quest quest = quest(book, op, "quest");
                Chapter from = quest.getChapter();
                Chapter to = chapter(book, op);
                if (from == to) break;
                JsonObject json = quest.toJson();
                from.removeQuest(quest);
                Quest moved = to.createQuest(quest.getId(), quest.getX(), quest.getY());
                moved.readJson(json);
                // A link to the quest in its new home chapter would now show it twice.
                to.removeLinksTo(quest.getId());
                changed.add(from);
                changed.add(to);
                break;
            }
            case ADD_LINK: {
                Chapter chapter = chapter(book, op);
                Quest quest = quest(book, op, "quest");
                if (quest.getChapter() == chapter)
                    throw new IllegalArgumentException("The quest is already in this chapter");
                chapter.addLink(quest, coord(op, "x"), coord(op, "y"));
                changed.add(chapter);
                break;
            }
            case CREATE_CHAPTER: {
                long id = Ids.parse(JsonUtil.getString(op, "id", ""));
                String filename = JsonUtil.getString(op, "filename", "");
                if (id == Ids.NONE || book.get(id) != null || !filename.matches("[a-z0-9_]+"))
                    throw new IllegalArgumentException("Bad new chapter");
                for (Chapter c : book.getChapters()) if (c.getFilename()
                    .equals(filename)) throw new IllegalArgumentException("Chapter file already exists");
                Chapter chapter = new Chapter(book, id, filename);
                chapter.setTitle(JsonUtil.getString(op, "title", ""));
                book.addChapter(chapter);
                changed.add(chapter);
                break;
            }
            case UPDATE_CHAPTER: {
                Chapter chapter = chapter(book, op);
                JsonObject data = JsonUtil.getObject(op, "data");
                if (data == null) throw new IllegalArgumentException("No chapter data");
                chapter.setTitle(JsonUtil.getString(data, "title", ""));
                chapter.setDescription(JsonUtil.getStringList(data, "description"));
                chapter.setIcon(JsonUtil.readStack(JsonUtil.getObject(data, "icon")));
                changed.add(chapter);
                break;
            }
            case DELETE_CHAPTER: {
                Chapter chapter = chapter(book, op);
                Set<Long> removed = new HashSet<>();
                for (Quest quest : chapter.getQuests()) removed.add(quest.getId());
                book.removeChapter(chapter);
                for (Chapter other : book.getChapters()) {
                    for (long id : removed) {
                        if (other.removeLinksTo(id)) changed.add(other);
                        for (Quest q : other.getQuests()) if (q.removeDependencyId(id)) changed.add(other);
                    }
                }
                break;
            }
            case MOVE_CHAPTER: {
                Chapter chapter = chapter(book, op);
                book.moveChapter(chapter, JsonUtil.getInt(op, "index", 0));
                break;
            }
            case UPDATE_QUEST: {
                Quest quest = quest(book, op, "quest");
                JsonObject data = JsonUtil.getObject(op, "data");
                if (data == null) throw new IllegalArgumentException("No quest data");
                quest.reload(data);
                changed.add(quest.getChapter());
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown edit '" + name + "'");
        }
        return changed;
    }

    /** True if {@code quest} depends on {@code target}, directly or through other quests. */
    private static boolean dependsOn(Quest quest, Quest target) {
        Set<Quest> seen = new HashSet<>();
        Deque<Quest> todo = new ArrayDeque<>();
        todo.add(quest);
        while (!todo.isEmpty()) {
            Quest q = todo.poll();
            if (q == target) return true;
            if (!seen.add(q)) continue;
            todo.addAll(q.getDependencies());
        }
        return false;
    }

    private static Quest quest(QuestBook book, JsonObject op, String key) {
        Quest quest = book.getQuest(Ids.parse(JsonUtil.getString(op, key, "")));
        if (quest == null) throw new IllegalArgumentException("No such quest: " + op.get(key));
        return quest;
    }

    private static Chapter chapter(QuestBook book, JsonObject op) {
        QuestObject o = book.get(Ids.parse(JsonUtil.getString(op, "chapter", "")));
        if (!(o instanceof Chapter)) throw new IllegalArgumentException("No such chapter: " + op.get("chapter"));
        return (Chapter) o;
    }

    private static QuestLink link(Chapter chapter, JsonObject op) {
        List<QuestLink> links = chapter.getLinks();
        int index = JsonUtil.getInt(op, "index", -1);
        if (index < 0 || index >= links.size()) throw new IllegalArgumentException("No such link: " + index);
        return links.get(index);
    }

    private static double coord(JsonObject op, String key) {
        double v = JsonUtil.getDouble(op, key, Double.NaN);
        if (Double.isNaN(v) || Double.isInfinite(v) || Math.abs(v) > 100000)
            throw new IllegalArgumentException("Bad coordinate " + key);
        return v;
    }
}
