package io.github.wlk.jlibrus.server.persistence;

import io.github.wlk.jlibrus.server.domain.CalendarFeed;
import io.github.wlk.jlibrus.server.domain.EntryType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Stores iCalendar feed definitions and their secret tokens. */
@ApplicationScoped
public class CalendarFeedRepository {

    private static final String COLUMNS = "id, name, token, child_ids, entry_types, created_at";

    private final Jdbc jdbc;

    @Inject
    public CalendarFeedRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public List<CalendarFeed> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM calendar_feed ORDER BY created_at", s -> { }, this::map);
    }

    public Optional<CalendarFeed> findById(String id) {
        return jdbc.queryOne("SELECT " + COLUMNS + " FROM calendar_feed WHERE id = ?", s -> s.setString(1, id), this::map);
    }

    public Optional<CalendarFeed> findByToken(String token) {
        return jdbc.queryOne("SELECT " + COLUMNS + " FROM calendar_feed WHERE token = ?",
                s -> s.setString(1, token), this::map);
    }

    /** Inserts or replaces the feed with the same id. */
    public CalendarFeed save(CalendarFeed feed) {
        jdbc.update("""
                INSERT INTO calendar_feed (id, name, token, child_ids, entry_types, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, token = excluded.token,
                  child_ids = excluded.child_ids, entry_types = excluded.entry_types
                """, s -> {
            s.setString(1, feed.id());
            s.setString(2, feed.name());
            s.setString(3, feed.token());
            s.setString(4, Columns.csv(feed.childIds(), Function.identity()));
            s.setString(5, Columns.csv(feed.types(), EntryType::name));
            Columns.setInstant(s, 6, feed.createdAt());
        });
        return feed;
    }

    public boolean delete(String id) {
        return jdbc.update("DELETE FROM calendar_feed WHERE id = ?", s -> s.setString(1, id)) > 0;
    }

    private CalendarFeed map(ResultSet row) throws SQLException {
        return new CalendarFeed(
                row.getString("id"),
                row.getString("name"),
                row.getString("token"),
                Columns.fromCsv(row.getString("child_ids"), Function.identity()),
                Columns.fromCsv(row.getString("entry_types"), EntryType::valueOf),
                Columns.getInstant(row, "created_at"));
    }
}
