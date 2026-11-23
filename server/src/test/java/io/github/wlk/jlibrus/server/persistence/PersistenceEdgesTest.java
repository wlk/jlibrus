package io.github.wlk.jlibrus.server.persistence;

import com.zaxxer.hikari.HikariDataSource;
import io.github.wlk.jlibrus.server.domain.EmailAddress;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Subscription;
import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Not-found answers, cascading deletes, transactional batches and column conversions (SQLite). */
class PersistenceEdgesTest {

    @TempDir
    static Path dir;
    private static HikariDataSource dataSource;
    private static Jdbc jdbc;

    @BeforeAll
    static void start() {
        dataSource = DatabaseProducer.createDataSource(new TestDatabaseConfig("jdbc:sqlite:" + dir.resolve("edges.db"), null, null));
        DatabaseProducer.migrate(dataSource);
        jdbc = new Jdbc(dataSource);
    }

    @AfterAll
    static void stop() {
        dataSource.close();
    }

    @BeforeEach
    void clean() {
        for (String table : List.of("summary_dispatch", "email_subscription", "calendar_feed", "timeline_entry")) {
            jdbc.update("DELETE FROM " + table, s -> { });
        }
    }

    @Test
    void deletingOrFindingUnknownIdsReportsNothing() {
        assertThat(new CalendarFeedRepository(jdbc).delete("nope")).isFalse();
        assertThat(new CalendarFeedRepository(jdbc).findById("nope")).isEmpty();
        assertThat(new SubscriptionRepository(jdbc).delete("nope")).isFalse();
    }

    @Test
    void deletingASubscriptionAlsoForgetsItsDispatchClaims() {
        SubscriptionRepository subscriptions = new SubscriptionRepository(jdbc);
        DispatchRepository dispatches = new DispatchRepository(jdbc);
        Subscription subscription = new Subscription("s1", EmailAddress.parse("a@b.pl"), Set.of(Frequency.DAILY),
                Set.of(EntryType.GRADE), Set.of(), true, Instant.EPOCH);
        assertThat(subscriptions.save(subscription)).isSameAs(subscription);
        dispatches.claim("s1", Frequency.DAILY, "2026-11-23", Instant.EPOCH);
        dispatches.claim("other", Frequency.DAILY, "2026-11-23", Instant.EPOCH);

        assertThat(subscriptions.delete("s1")).isTrue();

        assertThat(dispatches.claim("s1", Frequency.DAILY, "2026-11-23", Instant.EPOCH)).isTrue();
        assertThat(dispatches.claim("other", Frequency.DAILY, "2026-11-23", Instant.EPOCH)).isFalse();
    }

    @Test
    void failedBatchIsRolledBackCompletely() {
        String insert = "INSERT INTO timeline_entry (child_id, entry_key, entry_type, title, noticed_at) VALUES (?, ?, 'GRADE', ?, 0)";

        assertThatThrownBy(() -> jdbc.batch(insert, List.of("ok", "bad"), (s, key) -> {
            s.setString(1, "kid");
            s.setString(2, key);
            s.setString(3, "bad".equals(key) ? null : "title");
        })).isInstanceOf(PersistenceException.class).hasMessage("Batch failed: " + insert);

        assertThat(jdbc.query("SELECT entry_key FROM timeline_entry", s -> { }, r -> r.getString(1))).isEmpty();
        assertThat(jdbc.batch(insert, List.of(), (s, key) -> { })).isZero();
    }

    @Test
    void errorMessagesShowOnlyTheFirstSqlLine() {
        assertThatThrownBy(() -> jdbc.query("""
                SELECT nope
                FROM missing_table""", s -> { }, r -> 1))
                .isInstanceOf(PersistenceException.class).hasMessage("Query failed: SELECT nope");
        assertThatThrownBy(() -> jdbc.update("  DELETE FROM missing_table  ", s -> { }))
                .hasMessage("Update failed: DELETE FROM missing_table");
    }

    @Test
    void csvColumnsIgnoreBlanksAndSortValues() {
        assertThat(Columns.fromCsv(null, Function.identity())).isEmpty();
        assertThat(Columns.fromCsv(" ", Function.identity())).isEmpty();
        assertThat(Columns.fromCsv(" b, ,a ,", Function.identity())).containsExactlyInAnyOrder("a", "b");
        assertThat(Columns.csv(Set.of("b", "a"), Function.identity())).isEqualTo("a,b");
    }
}
