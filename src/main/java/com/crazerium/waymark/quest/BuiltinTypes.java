package com.crazerium.waymark.quest;

import com.crazerium.waymark.quest.reward.CommandReward;
import com.crazerium.waymark.quest.reward.ItemReward;
import com.crazerium.waymark.quest.reward.XpReward;
import com.crazerium.waymark.quest.task.CheckmarkTask;
import com.crazerium.waymark.quest.task.ItemTask;

/** Task and reward types that ship with Waymark. */
public final class BuiltinTypes {

    public static final TaskType<ItemTask> ITEM_TASK = TaskType.register("item", ItemTask::new);
    public static final TaskType<CheckmarkTask> CHECKMARK_TASK = TaskType.register("checkmark", CheckmarkTask::new);

    public static final RewardType<ItemReward> ITEM_REWARD = RewardType.register("item", ItemReward::new);
    public static final RewardType<XpReward> XP_REWARD = RewardType.register("xp", XpReward::new);
    public static final RewardType<CommandReward> COMMAND_REWARD = RewardType.register("command", CommandReward::new);

    private BuiltinTypes() {}

    /** Forces the static fields above to register. Call once during pre-init. */
    public static void init() {}
}
