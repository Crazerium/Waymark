package com.crazerium.waymark.util;

/**
 * Quest object ids are random 64-bit numbers, written to files as 16 hex digits.
 * Zero is reserved as "no id".
 */
public final class Ids {

    public static final long NONE = 0L;

    private Ids() {}

    public static String format(long id) {
        return String.format("%016X", id);
    }

    public static long parse(String text) {
        if (text == null || text.isEmpty()) return NONE;
        try {
            return Long.parseUnsignedLong(text, 16);
        } catch (NumberFormatException e) {
            return NONE;
        }
    }
}
