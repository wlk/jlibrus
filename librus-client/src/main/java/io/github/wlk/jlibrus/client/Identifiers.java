package io.github.wlk.jlibrus.client;

import java.util.regex.Pattern;

/**
 * Accepted shapes of identifiers the client puts into Librus addresses or message bodies. Checked before any
 * request, so a crafted value can never change the path. Every check is null-safe ({@code null} is never valid).
 */
public final class Identifiers {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final Pattern NUMERIC = Pattern.compile("[0-9]{1,18}");
    private static final Pattern GROUP = Pattern.compile("[A-Za-z]{1,40}");

    private Identifiers() {
    }

    /** Gateway ids such as announcement or user identifiers: 1-100 letters, digits, {@code _} or {@code -}. */
    public static boolean isSafeId(String value) {
        return matches(SAFE, value);
    }

    /** Message, attachment and account ids of the messages module: 1-18 digits, so always a {@code long}. */
    public static boolean isNumericId(String value) {
        return matches(NUMERIC, value);
    }

    /** Recipient group ids of the messages module such as {@code tutors}: 1-40 letters. */
    public static boolean isGroupId(String value) {
        return matches(GROUP, value);
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }
}
