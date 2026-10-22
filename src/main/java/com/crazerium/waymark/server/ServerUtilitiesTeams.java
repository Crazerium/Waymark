package com.crazerium.waymark.server;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.Universe;

/**
 * Reads teams from ServerUtilities. Only loaded when that mod is installed, so nothing else may reference its
 * classes.
 */
final class ServerUtilitiesTeams {

    private ServerUtilitiesTeams() {}

    /** The player's ServerUtilities team as a stable UUID, or null if the player has no team. */
    static UUID teamOf(UUID player) {
        if (!Universe.loaded()) return null;
        ForgePlayer forgePlayer = Universe.get()
            .getPlayer(player);
        if (forgePlayer == null || !forgePlayer.hasTeam()) return null;
        // The short uid is saved by ServerUtilities and survives restarts and team renames.
        String key = "serverutilities:team:" + forgePlayer.team.getUID();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
