package io.github.wlk.jlibrus.server.domain;

import io.github.wlk.jlibrus.server.domain.summary.Frequency;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * One e-mail recipient of periodic summaries.
 *
 * @param childIds children covered; empty means all children
 */
public record Subscription(String id, EmailAddress email, Set<Frequency> frequencies, Set<EntryType> types,
                           Set<String> childIds, boolean enabled, Instant createdAt) {

    public Subscription {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(createdAt, "createdAt");
        if (frequencies.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one frequency");
        }
        if (types.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one entry type");
        }
        frequencies = Set.copyOf(frequencies);
        types = Set.copyOf(types);
        childIds = Set.copyOf(childIds);
    }

    public boolean includesChild(String childId) {
        return childIds.isEmpty() || childIds.contains(childId);
    }

    public boolean wants(Frequency frequency) {
        return enabled && frequencies.contains(frequency);
    }
}
