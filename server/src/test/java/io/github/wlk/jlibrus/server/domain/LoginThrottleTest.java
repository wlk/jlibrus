package io.github.wlk.jlibrus.server.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LoginThrottleTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-23T10:00:00Z"));
    private final LoginThrottle throttle = new LoginThrottle(3, Duration.ofMinutes(15), now::get);

    @Test
    void allowsUntilMaxFailuresReached() {
        throttle.recordFailure("1.2.3.4");
        throttle.recordFailure("1.2.3.4");
        assertThat(throttle.isBlocked("1.2.3.4")).isFalse();

        throttle.recordFailure("1.2.3.4");
        assertThat(throttle.isBlocked("1.2.3.4")).isTrue();
    }

    @Test
    void blockIsPerKey() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("1.2.3.4");
        }
        assertThat(throttle.isBlocked("5.6.7.8")).isFalse();
    }

    @Test
    void failuresExpireAfterWindow() {
        for (int i = 0; i < 3; i++) {
            throttle.recordFailure("1.2.3.4");
        }
        now.set(now.get().plus(Duration.ofMinutes(15)));
        assertThat(throttle.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void successClearsFailures() {
        throttle.recordFailure("1.2.3.4");
        throttle.recordFailure("1.2.3.4");
        throttle.recordSuccess("1.2.3.4");
        throttle.recordFailure("1.2.3.4");
        assertThat(throttle.isBlocked("1.2.3.4")).isFalse();
    }

    @Test
    void staleClientsArePrunedWhenTooManyAreTracked() {
        for (int i = 0; i < LoginThrottle.MAX_TRACKED; i++) {
            throttle.recordFailure("10.0." + i);
        }
        now.set(now.get().plus(Duration.ofMinutes(16)));

        throttle.recordFailure("1.2.3.4");

        assertThat(throttle.trackedClients()).isEqualTo(1);
    }

    @Test
    void neverTracksMoreThanTheCapWhenAllClientsAreActive() {
        for (int i = 0; i < LoginThrottle.MAX_TRACKED + 50; i++) {
            throttle.recordFailure("10.1." + i);
        }

        assertThat(throttle.trackedClients()).isEqualTo(LoginThrottle.MAX_TRACKED);
        throttle.recordFailure("10.1.0");
        assertThat(throttle.trackedClients()).isEqualTo(LoginThrottle.MAX_TRACKED);
    }
}
