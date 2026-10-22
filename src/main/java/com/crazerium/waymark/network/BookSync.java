package com.crazerium.waymark.network;

import com.crazerium.waymark.quest.Chapter;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** The quest book as one JSON document for sending to clients. */
public final class BookSync {

    private BookSync() {}

    public static JsonObject write(QuestBook book) {
        JsonArray chapters = new JsonArray();
        for (Chapter chapter : book.getChapters()) {
            JsonObject c = chapter.toJson();
            c.addProperty("filename", chapter.getFilename());
            chapters.add(c);
        }
        JsonObject root = new JsonObject();
        root.add("chapters", chapters);
        return root;
    }

    public static QuestBook read(JsonObject root) {
        QuestBook book = new QuestBook();
        for (JsonElement e : JsonUtil.getArray(root, "chapters")) {
            JsonObject c = e.getAsJsonObject();
            Chapter.fromJson(book, JsonUtil.getString(c, "filename", ""), c);
        }
        return book;
    }
}
