package com.crazerium.waymark.server;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.crazerium.waymark.importer.BqImporter;
import com.crazerium.waymark.io.QuestBookStorage;
import com.crazerium.waymark.util.JsonUtil;
import com.google.gson.JsonObject;

/** Runs the BetterQuesting importer for /waymark import_bq and writes the result over config/waymark. */
final class BqImport {

    private BqImport() {}

    /** Returns lines to show the command sender. Without {@code apply} it only reports what would be imported. */
    static List<String> run(File bqDir, File waymarkDir, boolean apply) throws IOException {
        BqImporter.Result result = new BqImporter().convert(bqDir);
        List<String> out = new ArrayList<>();
        out.add(
            "BetterQuesting: " + result.chapters.size()
                + " chapters, "
                + result.quests
                + " quests, "
                + result.tasks
                + " tasks, "
                + result.rewards
                + " rewards");
        if (result.links > 0) out.add(result.links + " quests shown in several lines became links");
        if (result.unplaced > 0) out.add(result.unplaced + " quests in no line go to 'Not in any quest line'");
        out.add(result.warnings.size() + " warnings");

        if (!apply) {
            out.add(
                "Nothing changed yet. Run '/waymark import_bq confirm' to replace config/waymark (a backup is made).");
            return out;
        }

        if (waymarkDir.exists()) {
            File backup = new File(
                waymarkDir.getParentFile(),
                "waymark_backup_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));
            if (!waymarkDir.renameTo(backup)) throw new IOException("Could not move old quests to " + backup);
            out.add("Old quests moved to config/" + backup.getName());
        }

        File chaptersDir = new File(waymarkDir, "chapters");
        Files.createDirectories(chaptersDir.toPath());
        List<String> order = new ArrayList<>();
        for (Map.Entry<String, JsonObject> e : result.chapters.entrySet()) {
            QuestBookStorage.writeJson(new File(chaptersDir, e.getKey() + ".json"), e.getValue());
            order.add(e.getKey());
        }
        JsonObject book = new JsonObject();
        book.addProperty("version", 1);
        book.add("chapters", JsonUtil.toArray(order));
        QuestBookStorage.writeJson(new File(waymarkDir, "book.json"), book);

        File report = new File(waymarkDir, "import_report.txt");
        try (Writer w = Files.newBufferedWriter(report.toPath(), StandardCharsets.UTF_8)) {
            for (String line : out) w.write(line + "\n");
            w.write("\nConverted:\n");
            for (Map.Entry<String, Integer> e : result.convertedTypes.entrySet())
                w.write("  " + e.getKey() + ": " + e.getValue() + "\n");
            w.write("\nWarnings:\n");
            for (String warning : result.warnings) w.write("  " + warning + "\n");
        }
        out.add("Imported. Details in config/waymark/import_report.txt");
        return out;
    }
}
