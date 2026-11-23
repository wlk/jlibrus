package io.github.wlk.jlibrus.server.security;

import io.github.wlk.jlibrus.server.persistence.SettingRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/** Current session epoch, persisted so revocation survives restarts. Single writer: {@link #bump()} is synchronized. */
@ApplicationScoped
public class SessionEpoch {

    static final String KEY = "session.epoch";

    private final SettingRepository settings;
    private volatile Long current;

    @Inject
    public SessionEpoch(SettingRepository settings) {
        this.settings = settings;
    }

    /** Loaded at startup so identity checks on the event loop never block on the database. */
    void load(@Observes StartupEvent event) {
        current();
    }

    public long current() {
        Long value = current;
        if (value == null) {
            value = settings.get(KEY).map(Long::parseLong).orElse(0L);
            current = value;
        }
        return value;
    }

    /** Invalidates every session issued so far. */
    public synchronized long bump() {
        long next = current() + 1;
        settings.put(KEY, Long.toString(next));
        current = next;
        return next;
    }
}
