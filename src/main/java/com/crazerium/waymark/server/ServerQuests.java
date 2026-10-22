package com.crazerium.waymark.server;

import java.io.File;
import java.io.IOException;

import com.crazerium.waymark.io.QuestBookStorage;
import com.crazerium.waymark.quest.QuestBook;

/** Holds the server's copy of the quest book, which is the source of truth for all clients. */
public final class ServerQuests {

    private static QuestBookStorage storage;
    private static QuestBook book;

    private ServerQuests() {}

    public static void init(File configDir) {
        storage = new QuestBookStorage(new File(configDir, "waymark"));
    }

    public static QuestBook get() {
        return book;
    }

    public static void load() {
        book = storage.load();
        ServerProgress.onBookLoaded(book);
    }

    public static void save() throws IOException {
        if (book != null) storage.save(book);
    }

    public static void unload() {
        book = null;
    }
}
