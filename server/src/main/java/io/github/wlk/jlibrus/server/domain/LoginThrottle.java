package io.github.wlk.jlibrus.server.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Blocks a client after too many failed logins within a sliding window. */
public final class LoginThrottle {

    private final int maxFailures;
    private final Duration window;
    private final Supplier<Instant> clock;
    /**
     * Hard cap on tracked clients: stale ones are dropped first; when every slot is still active new clients are
     * not tracked (the caller's global throttle still counts them), so spoofed addresses cannot exhaust memory.
     */
    static final int MAX_TRACKED = 10_000;

    private final ConcurrentMap<String, List<Instant>> failures = new ConcurrentHashMap<>();

    public LoginThrottle(int maxFailures, Duration window, Supplier<Instant> clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        return recent(key).size() >= maxFailures;
    }

    public void recordFailure(String key) {
        Instant now = clock.get();
        if (failures.size() >= MAX_TRACKED) {
            failures.entrySet().removeIf(e -> e.getValue().stream().noneMatch(t -> isRecent(t, now)));
            if (failures.size() >= MAX_TRACKED && !failures.containsKey(key)) {
                return;
            }
        }
        failures.compute(key, (k, old) -> Stream.concat(
                old == null ? Stream.empty() : old.stream().filter(t -> isRecent(t, now)),
                Stream.of(now)).toList());
    }

    public void recordSuccess(String key) {
        failures.remove(key);
    }

    int trackedClients() {
        return failures.size();
    }

    private List<Instant> recent(String key) {
        Instant now = clock.get();
        return failures.getOrDefault(key, List.of()).stream().filter(t -> isRecent(t, now)).toList();
    }

    private boolean isRecent(Instant failure, Instant now) {
        return failure.plus(window).isAfter(now);
    }
}
