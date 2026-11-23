package io.github.wlk.jlibrus.server.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/** Persistent key/value server settings. */
@ApplicationScoped
public class SettingRepository {

    private final Jdbc jdbc;

    @Inject
    public SettingRepository(Jdbc jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> get(String key) {
        return jdbc.queryOne("SELECT setting_value FROM app_setting WHERE setting_key = ?",
                s -> s.setString(1, key), row -> row.getString(1));
    }

    public void put(String key, String value) {
        jdbc.update("""
                INSERT INTO app_setting (setting_key, setting_value) VALUES (?, ?)
                ON CONFLICT (setting_key) DO UPDATE SET setting_value = excluded.setting_value
                """, s -> {
            s.setString(1, key);
            s.setString(2, value);
        });
    }
}
