-- Small key/value store for server state that must survive restarts (e.g. the session epoch).
CREATE TABLE app_setting (
    setting_key   VARCHAR(64) NOT NULL PRIMARY KEY,
    setting_value TEXT        NOT NULL
);
