package io.github.wlk.jlibrus.server.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** A syntactically valid, normalised (lower-case) e-mail address. */
public record EmailAddress(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern SHAPE = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$");

    public EmailAddress {
        if (value == null || value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid e-mail address: " + value);
        }
    }

    public static EmailAddress parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Invalid e-mail address: null");
        }
        return new EmailAddress(raw.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
