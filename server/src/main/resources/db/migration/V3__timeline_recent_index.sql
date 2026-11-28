-- Portable: runs unchanged on SQLite and PostgreSQL.
-- No query filters timeline entries by type; the per-child "recent entries" view filters by child and
-- orders by noticed time, which this index serves directly.
DROP INDEX idx_timeline_child_type;
CREATE INDEX idx_timeline_child_noticed ON timeline_entry (child_id, noticed_at);
