package com.crazerium.waymark.quest;

/** How many of a set of conditions (a quest's dependencies or its tasks) must be met. */
public enum LogicMode {

    /** Every one of them. */
    ALL,
    /** At least one. */
    ANY,
    /** Exactly one; used for branches where picking one path closes the others. */
    ONE;

    public String key() {
        return name().toLowerCase();
    }

    public static LogicMode fromKey(String key) {
        for (LogicMode mode : values()) {
            if (mode.key()
                .equals(key)) return mode;
        }
        return ALL;
    }

    public boolean test(int met, int total) {
        switch (this) {
            case ANY:
                return met > 0;
            case ONE:
                return met == 1;
            default:
                return met == total;
        }
    }
}
