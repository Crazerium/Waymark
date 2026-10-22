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

    public static UUID teamOf(UUID player) {
        if (serverUtilities == null) {
            serverUtilities = Loader.isModLoaded("serverutilities");
            if (serverUtilities) Waymark.LOG.info("Using ServerUtilities teams for quest progress");
        }
        if (serverUtilities) {
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
