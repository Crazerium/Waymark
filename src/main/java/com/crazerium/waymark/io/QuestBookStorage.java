package com.crazerium.waymark.io;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.quest.Chapter;
import com.crazerium.waymark.quest.QuestBook;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads and writes the quest book as a folder of JSON files:
 *
 * <pre>
 * config/waymark/
 *   book.json             chapter order and book-wide settings
 *   chapters/&lt;name&gt;.json  one file per chapter with all its quests
 * </pre>
 */
public class QuestBookStorage {

    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private final File bookFile;
    private final File chaptersDir;

    /** Chapter files that loaded successfully. Only these may be deleted on save, so a broken file is never lost. */
    private final Set<String> loadedFiles = new HashSet<>();

    public QuestBookStorage(File dir) {
        this.bookFile = new File(dir, "book.json");
        this.chaptersDir = new File(dir, "chapters");
    }

    public QuestBook load() {
        QuestBook book = new QuestBook();
        loadedFiles.clear();

        List<String> order = new ArrayList<>();
        if (bookFile.isFile()) {
            try {
                JsonObject json = readJson(bookFile);
                order.addAll(JsonUtil.getStringList(json, "chapters"));
            } catch (Exception e) {
                Waymark.LOG.error("Failed to read {}", bookFile, e);
            }
        }

        // Chapters listed in book.json come first, in that order; any other files follow alphabetically.
        List<String> filenames = new ArrayList<>();
        for (String name : order) {
            if (new File(chaptersDir, name + ".json").isFile() && !filenames.contains(name)) filenames.add(name);
        }
        String[] files = chaptersDir.list((d, n) -> n.endsWith(".json"));
        if (files != null) {
            Arrays.sort(files);
            for (String file : files) {
                String name = file.substring(0, file.length() - ".json".length());
                if (!filenames.contains(name)) filenames.add(name);
            }
        }

        for (String name : filenames) {
            File file = new File(chaptersDir, name + ".json");
            try {
                Chapter.fromJson(book, name, readJson(file));
                loadedFiles.add(name);
            } catch (Exception e) {
                Waymark.LOG.error("Failed to load chapter {}, it will not be overwritten", file, e);
            }
        }

        Waymark.LOG.info(
            "Loaded {} chapters ({} objects)",
            book.getChapters()
                .size(),
            book.getObjectCount());
        return book;
    }

    /** Writes only the given chapters (and the chapter order), for small edits to a large book. */
    public void saveChapters(QuestBook book, Collection<Chapter> chapters) throws IOException {
        Files.createDirectories(chaptersDir.toPath());
        for (Chapter chapter : chapters) {
            writeJson(new File(chaptersDir, chapter.getFilename() + ".json"), chapter.toJson());
            loadedFiles.add(chapter.getFilename());
        }
        List<String> order = new ArrayList<>();
        for (Chapter chapter : book.getChapters()) order.add(chapter.getFilename());
        JsonObject json = new JsonObject();
        json.addProperty("version", FORMAT_VERSION);
        json.add("chapters", JsonUtil.toArray(order));
        writeJson(bookFile, json);
    }

    public void save(QuestBook book) throws IOException {
        Files.createDirectories(chaptersDir.toPath());

        Set<String> current = new HashSet<>();
        List<String> order = new ArrayList<>();
        for (Chapter chapter : book.getChapters()) {
            writeJson(new File(chaptersDir, chapter.getFilename() + ".json"), chapter.toJson());
            current.add(chapter.getFilename());
            order.add(chapter.getFilename());
        }

        JsonObject json = new JsonObject();
        json.addProperty("version", FORMAT_VERSION);
        json.add("chapters", JsonUtil.toArray(order));
        writeJson(bookFile, json);

        for (String name : loadedFiles) {
            if (!current.contains(name)) Files.deleteIfExists(new File(chaptersDir, name + ".json").toPath());
        }
        loadedFiles.clear();
        loadedFiles.addAll(current);
    }

    public static JsonObject readJson(File file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            if (!element.isJsonObject()) throw new IOException("Expected a JSON object");
            return element.getAsJsonObject();
        }
    }

    /** Writes to a temporary file first so a crash mid-write cannot leave a half-written file. */
    public static void writeJson(File file, JsonObject json) throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
            writer.write('\n');
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
