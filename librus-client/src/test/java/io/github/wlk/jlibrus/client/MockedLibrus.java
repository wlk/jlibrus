package io.github.wlk.jlibrus.client;

import io.github.wlk.jlibrus.mock.MockAccount;
import io.github.wlk.jlibrus.mock.MockLibrus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Shared test fixture: a mock Librus on a random local port with a fixed "today" (Monday 2026-11-23). */
final class MockedLibrus {

    static final Instant NOW = Instant.parse("2026-11-23T09:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private MockedLibrus() {
    }

    static MockLibrus start() {
        return MockLibrus.startLocal(CLOCK);
    }

    static LibrusAccount account(MockLibrus mock, MockAccount who) {
        return account(mock, who.login(), who.password());
    }

    static LibrusAccount account(MockLibrus mock, String login, String password) {
        return LibrusAccount.builder(new Credentials(login, password))
                .endpoints(LibrusEndpoints.singleHost(mock.baseUri()))
                .clock(CLOCK)
                .build();
    }
}
