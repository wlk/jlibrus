package io.github.wlk.jlibrus.server.persistence;

import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

/**
 * Remembers which summaries were sent so each (subscription, frequency, period) is mailed at most once,
 * even when the scheduler fires twice or the server restarts mid-run.
 */
@ApplicationScoped
public class DispatchRepository {

    private final Jdbc jdbc;

    @Inject
    public DispatchRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    /** Atomically claims the period; returns {@code false} when it was already claimed. */
    public boolean claim(String subscriptionId, Frequency frequency, String periodKey, Instant now) {
        return jdbc.update("""
                INSERT INTO summary_dispatch (subscription_id, frequency, period_key, sent_at)
                VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, s -> {
            s.setString(1, subscriptionId);
            s.setString(2, frequency.name());
            s.setString(3, periodKey);
            Columns.setInstant(s, 4, now);
        }) == 1;
    }

    /** Releases a claim after a failed send so the next run retries. */
    public void release(String subscriptionId, Frequency frequency, String periodKey) {
        jdbc.update("DELETE FROM summary_dispatch WHERE subscription_id = ? AND frequency = ? AND period_key = ?",
                s -> {
                    s.setString(1, subscriptionId);
                    s.setString(2, frequency.name());
                    s.setString(3, periodKey);
                });
    }
}
