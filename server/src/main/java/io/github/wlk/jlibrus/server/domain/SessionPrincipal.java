package io.github.wlk.jlibrus.server.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Principal stored in the encrypted session cookie: user name plus the session epoch it was issued in.
 * Logging out bumps the epoch, so every cookie issued before stops working - stateless cookies get revocation.
 */
public record SessionPrincipal(String username, long epoch) {

    private static final char SEPARATOR = '#';

    public SessionPrincipal {
        Objects.requireNonNull(username, "username");
        if (epoch < 0) {
            throw new IllegalArgumentException("epoch must not be negative");
        }
    }

    public String format() {
        return username + SEPARATOR + epoch;
    }

    public static Optional<SessionPrincipal> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        int at = name.lastIndexOf(SEPARATOR);
        if (at <= 0 || at == name.length() - 1) {
            return Optional.empty();
        }
        try {
            long epoch = Long.parseLong(name.substring(at + 1));
            return epoch < 0 ? Optional.empty() : Optional.of(new SessionPrincipal(name.substring(0, at), epoch));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
