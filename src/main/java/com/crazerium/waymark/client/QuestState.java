package com.crazerium.waymark.client;

import java.util.UUID;

import com.crazerium.waymark.progress.TeamProgress;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Reward;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.Visibility;

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
