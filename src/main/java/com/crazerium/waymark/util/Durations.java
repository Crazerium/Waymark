package com.crazerium.waymark.util;

/** Durations written for people: "90s", "30m", "6h", "1d", or combined like "1d12h". A bare number is seconds. */
public final class Durations {

    private Durations() {}

    /** Seconds, or -1 if the text is not a duration. */
    public static long parse(String text) {
        String s = text.trim()
            .toLowerCase()
            .replace(" ", "");
        if (s.isEmpty()) return -1;
        if (s.matches("\\d+")) return Long.parseLong(s);
        if (!s.matches("(\\d+[dhms])+")) return -1;
        long total = 0;
        long number = 0;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                number = number * 10 + (c - '0');
                continue;
            }
            total += number * (c == 'd' ? 86400 : c == 'h' ? 3600 : c == 'm' ? 60 : 1);
            number = 0;
        }
        return total;
    }

    /** The shortest exact form, e.g. 21600 to "6h" and 5400 to "1h30m". */
    public static String format(long seconds) {
        if (seconds <= 0) return "0s";
        StringBuilder sb = new StringBuilder();
        long[] units = { 86400, 3600, 60, 1 };
        char[] names = { 'd', 'h', 'm', 's' };
        for (int i = 0; i < units.length; i++) {
            long n = seconds / units[i];
            if (n == 0) continue;
            sb.append(n)
                .append(names[i]);
            seconds -= n * units[i];
        }
        return sb.toString();
    }
}
