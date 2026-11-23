package io.github.wlk.jlibrus.server.persistence;

import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Remembers every entry ever seen so summaries can report what is new, independent of Librus caches. */
@ApplicationScoped
public class TimelineRepository {

    private static final String COLUMNS =
            "child_id, entry_key, entry_type, title, detail, subject, author, occurs_on, added_at, noticed_at";

    private final Jdbc jdbc;

    @Inject
    public TimelineRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasAny(String childId) {
        return jdbc.queryOne("SELECT 1 FROM timeline_entry WHERE child_id = ? LIMIT 1",
                s -> s.setString(1, childId), row -> true).isPresent();
    }

    /** Inserts entries not yet known; already-known keys keep their original noticed time. Returns new count. */
    public int insertNew(List<TimelineEntry> entries) {
        return jdbc.batch("INSERT INTO timeline_entry (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (child_id, entry_key) DO NOTHING", entries, (s, e) -> {
            s.setString(1, e.childId());
            s.setString(2, e.key());
            s.setString(3, e.type().name());
            s.setString(4, e.title());
            s.setString(5, e.detail());
            s.setString(6, e.subject());
            s.setString(7, e.author());
            Columns.setDate(s, 8, e.occursOn());
            Columns.setInstant(s, 9, e.addedAt());
            Columns.setInstant(s, 10, e.noticedAt());
        });
    }

    /** Entries noticed in {@code [from, to)} for the given children (empty = all) and types. */
    public List<TimelineEntry> findNoticed(Set<String> childIds, Set<EntryType> types, Instant from, Instant to) {
        return jdbc.query("SELECT " + COLUMNS + " FROM timeline_entry WHERE noticed_at >= ? AND noticed_at < ? "
                        + "ORDER BY noticed_at DESC, entry_key", s -> {
                    Columns.setInstant(s, 1, from);
                    Columns.setInstant(s, 2, to);
                }, this::map).stream()
                .filter(e -> childIds.isEmpty() || childIds.contains(e.childId()))
                .filter(e -> types.contains(e.type()))
                .toList();
    }

    public List<TimelineEntry> findRecent(String childId, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM timeline_entry WHERE child_id = ? "
                + "ORDER BY noticed_at DESC, entry_key LIMIT ?", s -> {
            s.setString(1, childId);
            s.setInt(2, limit);
        }, this::map);
    }

    private TimelineEntry map(ResultSet row) throws SQLException {
        return new TimelineEntry(
                row.getString("child_id"),
                row.getString("entry_key"),
                EntryType.valueOf(row.getString("entry_type")),
                row.getString("title"),
                row.getString("detail"),
                row.getString("subject"),
                row.getString("author"),
                Columns.getDate(row, "occurs_on"),
                Columns.getInstant(row, "added_at"),
                Columns.getInstant(row, "noticed_at"));
    }
}
