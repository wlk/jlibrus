-- Portable schema: runs unchanged on SQLite and PostgreSQL.
-- Ids are application-generated text, instants are epoch milliseconds, sets are comma-separated text.

CREATE TABLE calendar_feed (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    token           VARCHAR(64)  NOT NULL UNIQUE,
    child_ids       TEXT         NOT NULL,
    entry_types     TEXT         NOT NULL,
    created_at      BIGINT       NOT NULL
);

CREATE TABLE email_subscription (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    email       VARCHAR(254) NOT NULL,
    frequencies TEXT         NOT NULL,
    entry_types TEXT         NOT NULL,
    child_ids   TEXT         NOT NULL,
    enabled     BOOLEAN      NOT NULL,
    created_at  BIGINT       NOT NULL
);

CREATE TABLE summary_dispatch (
    subscription_id VARCHAR(36) NOT NULL,
    frequency       VARCHAR(16) NOT NULL,
    period_key      VARCHAR(16) NOT NULL,
    sent_at         BIGINT      NOT NULL,
    PRIMARY KEY (subscription_id, frequency, period_key)
);

CREATE TABLE timeline_entry (
    child_id    VARCHAR(64)  NOT NULL,
    entry_key   VARCHAR(128) NOT NULL,
    entry_type  VARCHAR(32)  NOT NULL,
    title       TEXT         NOT NULL,
    detail      TEXT,
    subject     VARCHAR(200),
    author      VARCHAR(200),
    occurs_on   VARCHAR(10),
    added_at    BIGINT,
    noticed_at  BIGINT       NOT NULL,
    PRIMARY KEY (child_id, entry_key)
);

CREATE INDEX idx_timeline_noticed ON timeline_entry (noticed_at);
CREATE INDEX idx_timeline_child_type ON timeline_entry (child_id, entry_type);
