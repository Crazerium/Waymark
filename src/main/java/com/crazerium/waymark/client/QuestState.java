package com.crazerium.waymark.client;

import java.util.UUID;

import net.minecraft.client.resources.I18n;

import com.crazerium.waymark.progress.TeamProgress;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.Visibility;
import com.crazerium.waymark.util.Durations;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** How a quest looks to the local player. */
@SideOnly(Side.CLIENT)
public enum QuestState {

    HIDDEN(0),
    LOCKED(0xFF555555),
    AVAILABLE(0xFFCCCCCC),
    IN_PROGRESS(0xFF5599FF),
    CLAIMABLE(0xFFFFCC00),
    COMPLETED(0xFF55DD55);

    /** Border color of the quest node. */
    public final int color;

    QuestState(int color) {
        this.color = color;
    }

    public static QuestState of(Quest quest) {
        TeamProgress progress = ClientQuests.getProgress();
        if (progress == null) return LOCKED;
        Visibility visibility = quest.getVisibility();
        if (visibility == Visibility.NEVER) return HIDDEN;
        if (progress.isCompleted(quest)) {
            UUID player = ClientQuests.playerId();
            for (Reward reward : quest.getRewards()) {
                if (!progress.isClaimed(player, reward)) return CLAIMABLE;
            }
            return COMPLETED;
        }
        if (visibility == Visibility.COMPLETED) return HIDDEN;
        if (!progress.isUnlocked(quest)) return visibility == Visibility.UNLOCKED ? HIDDEN : LOCKED;
        for (Task task : quest.getTasks()) {
            if (progress.getProgress(task) > 0) return IN_PROGRESS;
        }
        return AVAILABLE;
    }

    /** A line about repeating for a repeatable quest ("every 6h", "resets in 2h5m"), or null for a normal quest. */
    public static String repeatInfo(Quest quest) {
        if (!quest.isRepeatable()) return null;
        TeamProgress progress = ClientQuests.getProgress();
        long completedAt = progress == null ? -1 : progress.getCompletedTime(quest);
        if (completedAt >= 0) {
            long left = (quest.getRepeatCooldown() * 1000 - (System.currentTimeMillis() - completedAt)) / 1000;
            if (left > 0) return I18n.format("waymark.gui.repeat_in", Durations.format(left));
            return I18n.format("waymark.gui.repeat_claim");
        }
        if (quest.getRepeatCooldown() <= 0) return I18n.format("waymark.gui.repeatable");
        return I18n.format("waymark.gui.repeat_every", Durations.format(quest.getRepeatCooldown()));
    }

    /** Overall progress of a quest from 0 to 1, weighting every non-optional task equally. */
    public static double fraction(Quest quest) {
        TeamProgress progress = ClientQuests.getProgress();
        if (progress == null) return 0;
        if (progress.isCompleted(quest)) return 1;
        double sum = 0;
        int count = 0;
        for (Task task : quest.getTasks()) {
            if (task.isOptional()) continue;
            count++;
            sum += Math.min(1.0, (double) progress.getProgress(task) / task.getMaxProgress());
        }
        return count == 0 ? 0 : sum / count;
    }
}
