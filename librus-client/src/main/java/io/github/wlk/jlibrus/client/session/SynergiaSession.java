package io.github.wlk.jlibrus.client.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.Credentials;
import io.github.wlk.jlibrus.client.LibrusEndpoints;
import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.http.HttpResult;
import io.github.wlk.jlibrus.client.http.HttpTransport;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps one account's Synergia session alive. Renewal is single-flight: concurrent callers that saw the same
 * expired generation trigger one renewal. Renewal tries {@code /refreshToken} first and falls back to a full
 * password login. Failed logins back off exponentially so a wrong password never hammers Librus.
 */
public final class SynergiaSession {

    static final Duration FIRST_BACKOFF = Duration.ofMinutes(5);
    static final Duration MAX_BACKOFF = Duration.ofHours(2);

    private final Credentials credentials;
    private final LibrusEndpoints endpoints;
    private final HttpTransport http;
    private final SynergiaLogin login;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private int generation;
    private boolean loggedIn;
    private Instant blockedUntil = Instant.EPOCH;
    private Duration nextBackoff = FIRST_BACKOFF;
    private LibrusException lastLoginFailure;

    public SynergiaSession(Credentials credentials, LibrusEndpoints endpoints, HttpTransport http,
                           ObjectMapper json, Clock clock) {
        this.credentials = credentials;
        this.endpoints = endpoints;
        this.http = http;
        this.login = new SynergiaLogin(endpoints, http, json);
        this.clock = clock;
    }

    public String login() {
        return credentials.login();
    }

    public LibrusEndpoints endpoints() {
        return endpoints;
    }

    public HttpTransport http() {
        return http;
    }

    /** Logs in when no session exists yet; returns the current session generation. */
    public int ensureLoggedIn() {
        lock.lock();
        try {
            if (!loggedIn) {
                passwordLogin();
            }
            return generation;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called after a request made with {@code seenGeneration} was rejected. Refreshes the token, or logs in
     * again when refreshing fails. Returns the new generation; no-op if someone else already renewed.
     */
    public int renew(int seenGeneration) {
        lock.lock();
        try {
            if (seenGeneration != generation && loggedIn) {
                return generation;
            }
            if (!refreshToken()) {
                passwordLogin();
            } else {
                generation++;
            }
            return generation;
        } finally {
            lock.unlock();
        }
    }

    /** Full password login regardless of refresh state (used when a refreshed token is still rejected). */
    public int relogin(int seenGeneration) {
        lock.lock();
        try {
            if (seenGeneration != generation && loggedIn) {
                return generation;
            }
            passwordLogin();
            return generation;
        } finally {
            lock.unlock();
        }
    }

    private boolean refreshToken() {
        if (!loggedIn) {
            return false;
        }
        URI synergia = endpoints.synergiaPath("/");
        String before = http.cookie(synergia, "oauth_token").map(c -> c.getValue()).orElse("");
        HttpResult result = http.get(endpoints.synergiaPath("/refreshToken"));
        String after = http.cookie(synergia, "oauth_token").map(c -> c.getValue()).orElse("");
        return result.isSuccess() && !after.isEmpty() && !after.equals(before);
    }

    private void passwordLogin() {
        Instant now = clock.instant();
        if (now.isBefore(blockedUntil)) {
            throw lastLoginFailure;
        }
        loggedIn = false;
        try {
            login.login(credentials);
        } catch (LibrusException.InvalidCredentials | LibrusException.CaptchaRequired e) {
            lastLoginFailure = e;
            blockedUntil = now.plus(nextBackoff);
            nextBackoff = min(nextBackoff.multipliedBy(2), MAX_BACKOFF);
            throw e;
        }
        loggedIn = true;
        generation++;
        nextBackoff = FIRST_BACKOFF;
        blockedUntil = Instant.EPOCH;
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
