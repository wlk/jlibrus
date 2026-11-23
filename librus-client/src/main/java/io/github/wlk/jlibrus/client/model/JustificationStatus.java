package io.github.wlk.jlibrus.client.model;

import java.util.Locale;

/** Decision on an absence justification sent by a parent. */
public enum JustificationStatus {
    PENDING,
    ACCEPTED,
    REJECTED;

    /** Librus sends free-form values such as "new", "accept", "rejected". */
    public static JustificationStatus parse(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("accept")) {
            return ACCEPTED;
        }
        if (value.startsWith("reject") || value.startsWith("refus") || value.startsWith("den") || value.startsWith("declin")) {
            return REJECTED;
        }
        return PENDING;
    }
}
