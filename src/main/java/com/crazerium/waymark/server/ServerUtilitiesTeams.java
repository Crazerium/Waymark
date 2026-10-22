package com.crazerium.waymark.server;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import serverutils.data.ClaimedChunk;
import serverutils.data.ClaimedChunks;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.ForgeTeam;
import serverutils.lib.data.Universe;
import serverutils.lib.math.ChunkDimPos;

/**
 * Reads teams and chunk claims from ServerUtilities. Only loaded when that mod is installed, so nothing else may
 * reference its classes.
 */
final class ServerUtilitiesTeams {

    private ServerUtilitiesTeams() {}

    /** The player's ServerUtilities team as a stable UUID, or null if the player has no team. */
    static UUID teamOf(UUID player) {
        if (!Universe.loaded()) return null;
        ForgePlayer forgePlayer = Universe.get()
            .getPlayer(player);
        if (forgePlayer == null || !forgePlayer.hasTeam()) return null;
        return teamId(forgePlayer.team);
    }

    /** The team that has claimed the chunk at this block, as the same UUID as {@link #teamOf}, or null. */
    static UUID claimedBy(int dimension, int x, int y, int z) {
        if (!ClaimedChunks.isActive()) return null;
        ClaimedChunk chunk = ClaimedChunks.instance.getChunk(new ChunkDimPos(x, y, z, dimension));
        if (chunk == null) return null;
        ForgeTeam team = chunk.getTeam();
        return team != null && team.isValid() ? teamId(team) : null;
    }

    static boolean claimsEnabled() {
        return ClaimedChunks.isActive();
    }

    private static UUID teamId(ForgeTeam team) {
        // The short uid is saved by ServerUtilities and survives restarts and team renames.
        String key = "serverutilities:team:" + team.getUID();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
