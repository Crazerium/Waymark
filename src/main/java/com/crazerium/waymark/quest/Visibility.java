package com.crazerium.waymark.quest;

/** When a quest is drawn in the quest book. Hidden quests still work, players just cannot see them. */
public enum Visibility {

    ALWAYS,
    /** Shown once its dependencies are done. */
    UNLOCKED,
    /** Shown only after it is completed. */
    COMPLETED,
    /** Never shown. */
    NEVER;

    public String key() {
        return name().toLowerCase();
    }

    public static Visibility fromKey(String key) {
        for (Visibility v : values()) {
            if (v.key()
                .equals(key)) return v;
        }
        return ALWAYS;
    }
}
