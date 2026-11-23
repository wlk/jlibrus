package io.github.wlk.jlibrus.server.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/** A syntactically valid, normalised (lower-case) e-mail address. */
public record EmailAddress(String value) {

    private static final int MAX_LENGTH = 254;
    private static final int MAX_LABEL = 63;
    // single character classes only: linear-time matching, no ReDoS
    private static final Pattern LOCAL = Pattern.compile("[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}");
    private static final Pattern LABEL = Pattern.compile("[a-z0-9-]{1," + MAX_LABEL + "}");

    public EmailAddress {
        if (value == null || value.length() > MAX_LENGTH || !valid(value)) {
            throw new IllegalArgumentException("Invalid e-mail address: " + value);
        }
    }

    private static boolean valid(String value) {
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || !LOCAL.matcher(value.substring(0, at)).matches()) {
            return false;
        }
        String[] labels = value.substring(at + 1).split("\\.", -1);
        return labels.length >= 2 && Arrays.stream(labels).allMatch(EmailAddress::validLabel);
    }

    private static boolean validLabel(String label) {
        return LABEL.matcher(label).matches() && !label.startsWith("-") && !label.endsWith("-");
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
