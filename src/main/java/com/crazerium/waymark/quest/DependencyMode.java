package com.crazerium.waymark.quest;

/** How a quest's dependencies must be completed before it unlocks. */
public enum DependencyMode {

    ALL,
    ANY;

    public String key() {
        return name().toLowerCase();
    }

    public static DependencyMode fromKey(String key) {
        for (DependencyMode mode : values()) {
            if (mode.key()
                .equals(key)) return mode;
        }
        return ALL;
    }
}
