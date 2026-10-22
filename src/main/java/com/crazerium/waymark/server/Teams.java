package com.crazerium.waymark.server;

import java.util.UUID;

import com.crazerium.waymark.Waymark;

import cpw.mods.fml.common.Loader;

/**
 * Which team's progress a player uses. With ServerUtilities installed that is the player's ServerUtilities team;
 * otherwise, or for players without a team, every player is a team of one identified by their own UUID.
 */
public final class Teams {

    private static Boolean serverUtilities;

    private Teams() {}

    /**
     * The Waymark team that has claimed the chunk at this block with ServerUtilities, or null when it is not claimed,
     * chunk claiming is off, or ServerUtilities is not installed.
     */
    public static UUID claimedBy(int dimension, int x, int y, int z) {
        if (!serverUtilities()) return null;
        try {
            return ServerUtilitiesTeams.claimedBy(dimension, x, y, z);
        } catch (LinkageError | RuntimeException e) {
            Waymark.LOG.error("Could not read ServerUtilities claims", e);
            return null;
        }
    }

    /** True when ServerUtilities is installed and its chunk claiming is switched on. */
    public static boolean claimsEnabled() {
        if (!serverUtilities()) return false;
        try {
            return ServerUtilitiesTeams.claimsEnabled();
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    private static boolean serverUtilities() {
        if (serverUtilities == null) {
            serverUtilities = Loader.isModLoaded("serverutilities");
            if (serverUtilities) Waymark.LOG.info("Using ServerUtilities teams for quest progress");
        }
        return serverUtilities;
    }

    public static UUID teamOf(UUID player) {
        if (serverUtilities()) {
            try {
                UUID team = ServerUtilitiesTeams.teamOf(player);
                if (team != null) return team;
            } catch (LinkageError | RuntimeException e) {
                // A ServerUtilities version with a different API: fall back to solo progress instead of crashing.
                Waymark.LOG.error("Could not read ServerUtilities teams, using solo progress", e);
                serverUtilities = false;
            }
        }
        return player;
    }
}
