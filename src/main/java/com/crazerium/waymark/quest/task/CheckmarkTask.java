package com.crazerium.waymark.quest.task;

import net.minecraft.entity.player.EntityPlayerMP;

import com.crazerium.waymark.quest.BuiltinTypes;
import com.crazerium.waymark.quest.Quest;
import com.crazerium.waymark.quest.Task;
import com.crazerium.waymark.quest.TaskType;

/** Completed by clicking a button in the quest screen. */
public class CheckmarkTask extends Task {

    public CheckmarkTask(Quest quest, long id) {
        super(quest, id);
    }

    @Override
    public TaskType<?> getType() {
        return BuiltinTypes.CHECKMARK_TASK;
    }

    @Override
    public long submit(EntityPlayerMP player, long progress) {
        return 1;
    }
}
