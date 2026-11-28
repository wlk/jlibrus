package io.github.wlk.jlibrus.client.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.Credentials;
import io.github.wlk.jlibrus.client.LibrusEndpoints;
import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.ScriptedServer;
import io.github.wlk.jlibrus.client.ScriptedServer.Reply;
import io.github.wlk.jlibrus.client.http.HttpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Renewal, single-flight generations and login backoff. */
class SynergiaSessionTest {

    private final ScriptedServer server = new ScriptedServer();
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-23T08:00:00Z"));
    private final AtomicInteger tokens = new AtomicInteger();

    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @AfterEach
    void stop() {
        server.close();
    }

    private SynergiaSession session() {
        return new SynergiaSession(new Credentials("1u", "p"), LibrusEndpoints.singleHost(server.base()),
                HttpTransport.withDefaults(), new ObjectMapper(), clock);
    }

    private String token() {
        return "oauth_token=t" + tokens.incrementAndGet() + "; Path=/; Max-Age=600";
    }

    @Test
    void generationsIncreaseAndStaleCallersDoNotRenewTwice() {
        server.happyLogin("DZIENNIKSID=d; Path=/", token()).on("/refreshToken", s -> Reply.cookies(200, "", token()));
        SynergiaSession session = session();

        int first = session.ensureLoggedIn();
        assertThat(session.ensureLoggedIn()).isEqualTo(first);
        int second = session.renew(first).generation();
        assertThat(second).isEqualTo(first + 1);
        assertThat(session.renew(first)).isEqualTo(new SynergiaSession.Renewal(second, false));
        assertThat(session.relogin(first)).isEqualTo(second);
        assertThat(server.count("/refreshToken")).isEqualTo(1);
        assertThat(session.relogin(second)).isEqualTo(second + 1);
        assertThat(session.login()).isEqualTo("1u");
    }

    @Test
    void refreshWithoutNewTokenFallsBackToPasswordLogin() {
        server.happyLogin("DZIENNIKSID=d; Path=/", "oauth_token=same; Path=/")
                .on("/refreshToken", Reply.cookies(200, "", "oauth_token=same; Path=/"));
        SynergiaSession session = session();
        int first = session.ensureLoggedIn();

        SynergiaSession.Renewal renewed = session.renew(first);

        assertThat(renewed).isEqualTo(new SynergiaSession.Renewal(first + 1, true));
        assertThat(server.seen()).filteredOn(s -> "POST".equals(s.method())).hasSize(2);
    }

    @Test
    void renewBeforeAnyLoginLogsIn() {
        server.happyLogin("DZIENNIKSID=d; Path=/", token());

        assertThat(session().renew(0)).isEqualTo(new SynergiaSession.Renewal(1, true));
        assertThat(server.count("/refreshToken")).isZero();
    }

    @Test
    void rejectedLoginBacksOffExponentiallyUpToTwoHours() {
        server.happyLogin().on("/OAuth/Authorization", s -> "POST".equals(s.method())
                ? Reply.json(200, "{\"status\":\"error\",\"errors\":[{\"message\":\"Złe hasło\"}]}") : Reply.html(200, "f"));
        SynergiaSession session = session();

        Duration expected = SynergiaSession.FIRST_BACKOFF;
        for (int attempt = 1; attempt <= 7; attempt++) {
            assertThatThrownBy(session::ensureLoggedIn).isInstanceOf(LibrusException.InvalidCredentials.class);
            long posts = server.seen().stream().filter(s -> "POST".equals(s.method())).count();
            assertThat(posts).isEqualTo(attempt);
            now.set(now.get().plus(expected).minusSeconds(1));
            assertThatThrownBy(session::ensureLoggedIn).isInstanceOf(LibrusException.InvalidCredentials.class);
            assertThat(server.seen().stream().filter(s -> "POST".equals(s.method())).count()).isEqualTo(attempt);
            now.set(now.get().plusSeconds(1));
            expected = expected.multipliedBy(2).compareTo(SynergiaSession.MAX_BACKOFF) > 0
                    ? SynergiaSession.MAX_BACKOFF : expected.multipliedBy(2);
        }
    }

    @Test
    void successfulLoginResetsBackoff() {
        AtomicInteger posts = new AtomicInteger();
        server.happyLogin("DZIENNIKSID=d; Path=/", token()).on("/OAuth/Authorization", s -> {
            if (!"POST".equals(s.method())) {
                return Reply.html(200, "f");
            }
            return posts.incrementAndGet() == 1
                    ? Reply.json(200, "{\"status\":\"error\"}")
                    : Reply.json(200, "{\"status\":\"ok\",\"goTo\":\"/OAuth/Authorization/2FA\"}");
        });
        SynergiaSession session = session();
        assertThatThrownBy(session::ensureLoggedIn).isInstanceOf(LibrusException.InvalidCredentials.class);
        now.set(now.get().plus(SynergiaSession.FIRST_BACKOFF));

        assertThat(session.ensureLoggedIn()).isEqualTo(1);
    }

    @Test
    void outagesDoNotTriggerBackoff() {
        server.on("/loguj/portalRodzina", Reply.html(503, "maintenance"));
        SynergiaSession session = session();

        assertThatThrownBy(session::ensureLoggedIn).isInstanceOf(LibrusException.ServiceUnavailable.class);
        assertThatThrownBy(session::ensureLoggedIn).isInstanceOf(LibrusException.ServiceUnavailable.class);
        assertThat(server.count("/loguj/portalRodzina")).isEqualTo(2);
    }

    @Test
    void requestsOfOneAccountNeverOverlap() throws Exception {
        java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger maxInFlight = new java.util.concurrent.atomic.AtomicInteger();
        SynergiaSession session = session();
        Runnable work = () -> session.exclusive(() -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            inFlight.decrementAndGet();
            return session.exclusive(() -> "reentrant");
        });
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 8; i++) {
                executor.submit(work);
            }
        }

        assertThat(maxInFlight.get()).isEqualTo(1);
    }
}
