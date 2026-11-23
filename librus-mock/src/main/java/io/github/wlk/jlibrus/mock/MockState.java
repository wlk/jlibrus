package io.github.wlk.jlibrus.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Server-side sessions of the mock, with knobs tests use to simulate expiry. */
public final class MockState {

    /** Real Librus tokens slide with a 10 minute max age. */
    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private record Token(String login, Instant expiresAt) {
    }

    private final Clock clock;
    private final Map<String, MockAccount> accounts = new ConcurrentHashMap<>();
    private final Map<String, String> pendingLogins = new ConcurrentHashMap<>();
    private final Map<String, String> codes = new ConcurrentHashMap<>();
    private final Map<String, String> synergiaSessions = new ConcurrentHashMap<>();
    private final Map<String, Token> tokens = new ConcurrentHashMap<>();
    private final Map<String, String> messageSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> readMessages = new ConcurrentHashMap<>();
    private final List<SentRecord> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger passwordLogins = new AtomicInteger();
    private volatile boolean outage;

    /** A message accepted by the mock's send endpoint. */
    public record SentRecord(String login, long id, List<String> recipients, String topic, String text,
                             String replyTo, Instant at) {
    }

    public MockState(Clock clock, List<MockAccount> accounts) {
        this.clock = clock;
        accounts.forEach(a -> this.accounts.put(a.login(), a));
    }

    public Clock clock() {
        return clock;
    }

    public Optional<MockAccount> account(String login) {
        return Optional.ofNullable(accounts.get(login));
    }

    public List<MockAccount> accounts() {
        return List.copyOf(accounts.values());
    }

    String newApiSession() {
        String id = random();
        pendingLogins.put(id, "");
        return id;
    }

    boolean knowsApiSession(String id) {
        return pendingLogins.containsKey(id);
    }

    void authenticate(String apiSession, String login) {
        pendingLogins.put(apiSession, login);
        passwordLogins.incrementAndGet();
    }

    Optional<String> authenticatedLogin(String apiSession) {
        return Optional.ofNullable(pendingLogins.get(apiSession)).filter(s -> !s.isEmpty());
    }

    String issueCode(String login) {
        String code = random();
        codes.put(code, login);
        return code;
    }

    Optional<String> redeemCode(String code) {
        return Optional.ofNullable(codes.remove(code));
    }

    String newSynergiaSession(String login) {
        String id = random();
        synergiaSessions.put(id, login);
        return id;
    }

    Optional<String> synergiaLogin(String sessionId) {
        return Optional.ofNullable(sessionId).map(synergiaSessions::get);
    }

    String newToken(String login) {
        String token = random();
        tokens.put(token, new Token(login, clock.instant().plus(TOKEN_TTL)));
        return token;
    }

    /** Login of a valid token; using a token slides its expiry like Librus does. */
    Optional<String> tokenLogin(String token) {
        Token found = token == null ? null : tokens.get(token);
        if (found == null || !found.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        tokens.put(token, new Token(found.login(), clock.instant().plus(TOKEN_TTL)));
        return Optional.of(found.login());
    }

    String newMessageSession(String login) {
        String id = random();
        messageSessions.put(id, login);
        return id;
    }

    Optional<String> messageLogin(String sessionId) {
        return Optional.ofNullable(sessionId).map(messageSessions::get);
    }

    void markRead(String login, String messageId) {
        readMessages.computeIfAbsent(login, k -> ConcurrentHashMap.newKeySet()).add(messageId);
    }

    boolean isRead(String login, String messageId) {
        return readMessages.getOrDefault(login, Set.of()).contains(messageId);
    }

    long recordSent(String login, List<String> recipients, String topic, String text, String replyTo) {
        long id = 900_000L + sent.size() + 1;
        sent.add(new SentRecord(login, id, List.copyOf(recipients), topic, text, replyTo, clock.instant()));
        return id;
    }

    public List<SentRecord> sentMessages() {
        return List.copyOf(sent);
    }

    public int passwordLogins() {
        return passwordLogins.get();
    }

    /** While on, every endpoint answers 503 "Przerwa techniczna" (maintenance). */
    public void setOutage(boolean on) {
        outage = on;
    }

    public boolean outage() {
        return outage;
    }

    /** Simulates idle expiry of every API token (Synergia session cookies stay valid). */
    public void expireTokens() {
        tokens.clear();
    }

    /** Simulates the whole Synergia session being gone (tokens and session ids). */
    public void expireSynergiaSessions() {
        tokens.clear();
        synergiaSessions.clear();
    }

    /** Simulates the messages module dropping its own session. */
    public void expireMessageSessions() {
        messageSessions.clear();
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
