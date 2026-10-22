package com.crazerium.waymark.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.crazerium.waymark.Waymark;
import com.crazerium.waymark.progress.TeamProgress;

/** Stores progress in the world folder: &lt;world&gt;/waymark/progress/&lt;team uuid&gt;.json. */
public class ProgressStorage {

    private final File dir;

    public ProgressStorage(File worldDir) {
        this.dir = new File(new File(worldDir, "waymark"), "progress");
    }

    public Map<UUID, TeamProgress> loadAll() {
        Map<UUID, TeamProgress> teams = new HashMap<>();
        String[] files = dir.list((d, n) -> n.endsWith(".json"));
        if (files == null) return teams;
        for (String file : files) {
            UUID id;
            try {
                id = UUID.fromString(file.substring(0, file.length() - ".json".length()));
            } catch (IllegalArgumentException e) {
                continue;
            }
            try {
                TeamProgress team = new TeamProgress(id);
                team.readJson(QuestBookStorage.readJson(new File(dir, file)));
                teams.put(id, team);
            } catch (Exception e) {
                Waymark.LOG.error("Failed to load progress file {}", file, e);
            }
        }
        return teams;
    }

    /** Writes only teams that changed since the last save. */
    public void saveDirty(Collection<TeamProgress> teams) {
        for (TeamProgress team : teams) {
            if (!team.isDirty()) continue;
            try {
                Files.createDirectories(dir.toPath());
                QuestBookStorage.writeJson(new File(dir, team.getId() + ".json"), team.toJson());
                team.clearDirty();
            } catch (IOException e) {
                Waymark.LOG.error("Failed to save progress of team {}", team.getId(), e);
            }
        }
    }
}
