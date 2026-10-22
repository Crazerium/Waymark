package com.crazerium.waymark.quest;

import com.crazerium.waymark.util.Ids;

/** Anything in the quest book that has its own id: chapters, quests, tasks and rewards. */
public abstract class QuestObject {

    private final long id;

    protected QuestObject(long id) {
        this.id = id;
    }

    public final long getId() {
        return id;
    }

    public abstract QuestBook getBook();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "#" + Ids.format(id);
    }
}
