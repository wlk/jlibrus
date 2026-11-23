package io.github.wlk.jlibrus.server.domain.summary;

import java.util.Locale;

/** How often an e-mail summary is sent. */
public enum Frequency {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static Frequency parse(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
