package io.github.wlk.jlibrus.server.persistence;

import io.github.wlk.jlibrus.server.domain.EmailAddress;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Subscription;
import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Stores e-mail summary subscriptions. */
@ApplicationScoped
public class SubscriptionRepository {

    private static final String COLUMNS = "id, email, frequencies, entry_types, child_ids, enabled, created_at";

    private final Jdbc jdbc;

    @Inject
    public SubscriptionRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public List<Subscription> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM email_subscription ORDER BY email, created_at",
                s -> { }, this::map);
    }

    public Optional<Subscription> findById(String id) {
        return jdbc.queryOne("SELECT " + COLUMNS + " FROM email_subscription WHERE id = ?",
                s -> s.setString(1, id), this::map);
    }

    public List<Subscription> findWanting(Frequency frequency) {
        return findAll().stream().filter(s -> s.wants(frequency)).toList();
    }

    public Subscription save(Subscription subscription) {
        jdbc.update("""
                INSERT INTO email_subscription (id, email, frequencies, entry_types, child_ids, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET email = excluded.email, frequencies = excluded.frequencies,
                  entry_types = excluded.entry_types, child_ids = excluded.child_ids, enabled = excluded.enabled
                """, s -> {
            s.setString(1, subscription.id());
            s.setString(2, subscription.email().value());
            s.setString(3, Columns.csv(subscription.frequencies(), Frequency::name));
            s.setString(4, Columns.csv(subscription.types(), EntryType::name));
            s.setString(5, Columns.csv(subscription.childIds(), Function.identity()));
            s.setBoolean(6, subscription.enabled());
            Columns.setInstant(s, 7, subscription.createdAt());
        });
        return subscription;
    }

    public boolean delete(String id) {
        jdbc.update("DELETE FROM summary_dispatch WHERE subscription_id = ?", s -> s.setString(1, id));
        return jdbc.update("DELETE FROM email_subscription WHERE id = ?", s -> s.setString(1, id)) > 0;
    }

    private Subscription map(ResultSet row) throws SQLException {
        return new Subscription(
                row.getString("id"),
                EmailAddress.parse(row.getString("email")),
                Columns.fromCsv(row.getString("frequencies"), Frequency::valueOf),
                Columns.fromCsv(row.getString("entry_types"), EntryType::valueOf),
                Columns.fromCsv(row.getString("child_ids"), Function.identity()),
                row.getBoolean("enabled"),
                Columns.getInstant(row, "created_at"));
    }
}
