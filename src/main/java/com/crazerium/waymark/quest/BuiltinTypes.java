package com.crazerium.waymark.quest;

import com.crazerium.waymark.quest.reward.ChoiceReward;
import com.crazerium.waymark.quest.reward.CommandReward;
import com.crazerium.waymark.quest.reward.CompleteQuestReward;
import com.crazerium.waymark.quest.reward.ItemReward;
import com.crazerium.waymark.quest.reward.XpReward;
import com.crazerium.waymark.quest.task.CheckmarkTask;
import com.crazerium.waymark.quest.task.CraftTask;
import com.crazerium.waymark.quest.task.ItemTask;
import com.crazerium.waymark.quest.task.KillTask;
import com.crazerium.waymark.quest.task.LocationTask;

/** Task and reward types that ship with Waymark. */
public final class BuiltinTypes {

    public static final TaskType<ItemTask> ITEM_TASK = TaskType.register("item", ItemTask::new);
    public static final TaskType<CheckmarkTask> CHECKMARK_TASK = TaskType.register("checkmark", CheckmarkTask::new);
    public static final TaskType<CraftTask> CRAFT_TASK = TaskType.register("craft", CraftTask::new);
    public static final TaskType<KillTask> KILL_TASK = TaskType.register("kill", KillTask::new);
    public static final TaskType<LocationTask> LOCATION_TASK = TaskType.register("location", LocationTask::new);

    public static final RewardType<ItemReward> ITEM_REWARD = RewardType.register("item", ItemReward::new);
    public static final RewardType<ChoiceReward> CHOICE_REWARD = RewardType.register("choice", ChoiceReward::new);
    public static final RewardType<XpReward> XP_REWARD = RewardType.register("xp", XpReward::new);
    public static final RewardType<CommandReward> COMMAND_REWARD = RewardType.register("command", CommandReward::new);
    public static final RewardType<CompleteQuestReward> COMPLETE_QUEST_REWARD = RewardType
        .register("complete_quest", CompleteQuestReward::new);

    private BuiltinTypes() {}

    /** Forces the static fields above to register. Call once during pre-init. */
    public static void init() {}
}
