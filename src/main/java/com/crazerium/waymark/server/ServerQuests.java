package com.crazerium.waymark.server;

import java.io.File;
import java.io.IOException;

import com.crazerium.waymark.io.QuestBookStorage;
import com.crazerium.waymark.network.BookSync;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.util.Compression;

/** Holds the server's copy of the quest book, which is the source of truth for all clients. */
public final class ServerQuests {

    private static QuestBookStorage storage;
    private static QuestBook book;
    /** The book as gzipped JSON for clients, built on first use after each load. */
    private static byte[] syncData;

    private ServerQuests() {}

    public static void init(File configDir) {
        storage = new QuestBookStorage(new File(configDir, "waymark"));
    }

    public static QuestBook get() {
        return book;
    }

    public static void load() {
        book = storage.load();
        syncData = null;
        ServerProgress.onBookLoaded(book);
    }

    public static byte[] syncData() {
        if (syncData == null) syncData = Compression.gzip(
            BookSync.write(book)
                .toString());
        return syncData;
    }

    public static void save() throws IOException {
        if (book != null) storage.save(book);
    }

    public static void unload() {
        book = null;
        syncData = null;
    }
}
