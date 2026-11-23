package io.github.wlk.jlibrus.server.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * A read-only iCalendar feed reachable without login via its secret token.
 *
 * @param childIds       children included; empty means all children
 * @param types    entry types exported (only calendar types make sense)
 */
public record CalendarFeed(String id, String name, String token, Set<String> childIds, Set<EntryType> types,
                           Instant createdAt) {

    public static final int MAX_NAME_LENGTH = 200;

    public CalendarFeed {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Feed name must be 1-" + MAX_NAME_LENGTH + " characters");
        }
        if (!SecretTokens.looksValid(token)) {
            throw new IllegalArgumentException("Invalid feed token");
        }
        childIds = Set.copyOf(childIds);
        types = Set.copyOf(types);
    }

    public boolean includesChild(String childId) {
        return childIds.isEmpty() || childIds.contains(childId);
    }

    public CalendarFeed withToken(String newToken) {
        return new CalendarFeed(id, name, newToken, childIds, types, createdAt);
    }
}
