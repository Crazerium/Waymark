package com.crazerium.waymark.quest;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.crazerium.waymark.quest.reward.ChoiceReward;
import com.crazerium.waymark.quest.reward.CommandReward;
import com.crazerium.waymark.quest.reward.CompleteQuestReward;
import com.crazerium.waymark.quest.reward.ItemReward;
import com.crazerium.waymark.quest.reward.XpReward;
import com.crazerium.waymark.quest.task.CheckmarkTask;
import com.crazerium.waymark.quest.task.CraftTask;
import com.crazerium.waymark.quest.task.FluidTask;
import com.crazerium.waymark.quest.task.ItemTask;
import com.crazerium.waymark.quest.task.KillTask;
import com.crazerium.waymark.quest.task.LocationTask;

/** Task and reward types that ship with Waymark. */
public final class BuiltinTypes {

    public static final TaskType<ItemTask> ITEM_TASK = TaskType.register("item", ItemTask::new)
        .withIcon(new ItemStack(Items.diamond));
    public static final TaskType<CheckmarkTask> CHECKMARK_TASK = TaskType.register("checkmark", CheckmarkTask::new)
        .withIcon(new ItemStack(Items.paper));
    public static final TaskType<CraftTask> CRAFT_TASK = TaskType.register("craft", CraftTask::new)
        .withIcon(new ItemStack(Blocks.crafting_table));
    public static final TaskType<KillTask> KILL_TASK = TaskType.register("kill", KillTask::new)
        .withIcon(new ItemStack(Items.iron_sword));
    public static final TaskType<LocationTask> LOCATION_TASK = TaskType.register("location", LocationTask::new)
        .withIcon(new ItemStack(Items.compass));
    public static final TaskType<FluidTask> FLUID_TASK = TaskType.register("fluid", FluidTask::new)
        .withIcon(new ItemStack(Items.water_bucket));

    public static final RewardType<ItemReward> ITEM_REWARD = RewardType.register("item", ItemReward::new)
        .withIcon(new ItemStack(Items.diamond));
    public static final RewardType<ChoiceReward> CHOICE_REWARD = RewardType.register("choice", ChoiceReward::new)
        .withIcon(new ItemStack(Blocks.chest));
    public static final RewardType<XpReward> XP_REWARD = RewardType.register("xp", XpReward::new)
        .withIcon(new ItemStack(Items.experience_bottle));
    public static final RewardType<CommandReward> COMMAND_REWARD = RewardType.register("command", CommandReward::new)
        .withIcon(new ItemStack(Blocks.command_block));
    public static final RewardType<CompleteQuestReward> COMPLETE_QUEST_REWARD = RewardType
        .register("complete_quest", CompleteQuestReward::new)
        .withIcon(new ItemStack(Items.book));

    private BuiltinTypes() {}

    /** Forces the static fields above to register. Call once during pre-init. */
    public static void init() {}
}
