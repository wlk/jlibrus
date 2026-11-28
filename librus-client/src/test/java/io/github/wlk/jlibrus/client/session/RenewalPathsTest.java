package io.github.wlk.jlibrus.client.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.Credentials;
import io.github.wlk.jlibrus.client.LibrusAccount;
import io.github.wlk.jlibrus.client.LibrusEndpoints;
import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.ScriptedServer;
import io.github.wlk.jlibrus.client.ScriptedServer.Reply;
import io.github.wlk.jlibrus.client.http.HttpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every path through gateway renewal, plus lock release, login details and the descriptive reader. */
class RenewalPathsTest {

    private final ScriptedServer server = new ScriptedServer();
    private final AtomicInteger tokens = new AtomicInteger();

    @AfterEach
    void stop() {
        server.close();
    }

    private ScriptedServer loginWithRefreshingTokens() {
        return server.happyLogin("DZIENNIKSID=d; Path=/", "oauth_token=t0; Path=/")
                .on("/refreshToken", s -> Reply.cookies(200, "", "oauth_token=r" + tokens.incrementAndGet() + "; Path=/"));
    }

    private SynergiaSession session() {
        return new SynergiaSession(new Credentials("1u", "p"), LibrusEndpoints.singleHost(server.base()),
                HttpTransport.withDefaults(), new ObjectMapper(), Clock.systemUTC());
    }

    private long posts() {
        return server.seen().stream().filter(s -> "POST".equals(s.method()) && s.path().startsWith("/OAuth")).count();
    }

    @Test
    void expiredTokenIsRefreshedOnceWithoutPasswordLogin() {
        AtomicInteger calls = new AtomicInteger();
        loginWithRefreshingTokens().on("/gateway/api/2.0/Me", s -> calls.incrementAndGet() == 1
                ? Reply.json(401, "{\"Code\":\"TokenIsExpired\"}") : Reply.json(200, "{\"Me\":{}}"));

        assertThat(new Gateway(session(), new ObjectMapper()).get("Me").has("Me")).isTrue();
        assertThat(posts()).isEqualTo(1);
        assertThat(server.count("/refreshToken")).isEqualTo(1);
    }

    @Test
    void stillRejectedAfterRefreshLogsInAgain() {
        AtomicInteger calls = new AtomicInteger();
        loginWithRefreshingTokens().on("/gateway/api/2.0/Me", s -> calls.incrementAndGet() <= 2
                ? Reply.html(200, "<html>login</html>") : Reply.json(200, "{\"Me\":{}}"));

        assertThat(new Gateway(session(), new ObjectMapper()).get("Me").has("Me")).isTrue();
        assertThat(posts()).isEqualTo(2);
    }

    @Test
    void failedRefreshThenStillRejectedDoesNotLogInWithThePasswordTwice() {
        server.happyLogin("DZIENNIKSID=d; Path=/", "oauth_token=t0; Path=/")
                .on("/refreshToken", Reply.json(500, "{}"))
                .on("/gateway/api/2.0/Me", Reply.json(401, "{}"));
        Gateway gateway = new Gateway(session(), new ObjectMapper());

        assertThatThrownBy(() -> gateway.get("Me")).isInstanceOf(LibrusException.SessionExpired.class);
        // first login, then the fallback login after the failed refresh; a third login cannot help
        assertThat(posts()).isEqualTo(2);
        assertThat(server.count("/gateway/api/2.0/Me")).isEqualTo(2);
    }

    @Test
    void permanentRejectionIsSessionExpiredAndScopesAreModuleUnavailable() {
        loginWithRefreshingTokens()
                .on("/gateway/api/2.0/Me", Reply.json(401, "{}"))
                .on("/gateway/api/2.0/Notes", Reply.json(401, "{\"Message\":\"Insufficient scopes\"}"));
        Gateway gateway = new Gateway(session(), new ObjectMapper());

        assertThatThrownBy(() -> gateway.get("Me")).isInstanceOf(LibrusException.SessionExpired.class);
        long refreshes = server.count("/refreshToken");
        assertThatThrownBy(() -> gateway.get("Notes")).isInstanceOf(LibrusException.ModuleUnavailable.class);
        assertThat(server.count("/refreshToken")).isEqualTo(refreshes);
    }

    @Test
    void malformedJsonIsAProtocolError() {
        loginWithRefreshingTokens().on("/gateway/api/2.0/Me", Reply.json(200, "{broken"));

        assertThatThrownBy(() -> new Gateway(session(), new ObjectMapper()).get("Me"))
                .isInstanceOf(LibrusException.ProtocolError.class);
    }

    @Test
    void postRenewsOnceAndReturnsTheSecondAnswer() {
        AtomicInteger calls = new AtomicInteger();
        loginWithRefreshingTokens().on("/gateway/api/2.0/SchoolNotices/MarkAsRead/x", s -> calls.incrementAndGet() == 1
                ? Reply.json(401, "{}") : Reply.json(200, "{}"));

        assertThat(new Gateway(session(), new ObjectMapper()).post("SchoolNotices/MarkAsRead/x").status()).isEqualTo(200);
        assertThat(server.count("/refreshToken")).isEqualTo(1);
    }

    @Test
    void acceptedPostIsSentOnceWithoutRenewal() {
        loginWithRefreshingTokens().on("/gateway/api/2.0/SchoolNotices/MarkAsRead/y", Reply.json(200, "{}"));

        assertThat(new Gateway(session(), new ObjectMapper()).post("SchoolNotices/MarkAsRead/y").status()).isEqualTo(200);
        assertThat(server.count("/refreshToken")).isZero();
        assertThat(server.count("/gateway/api/2.0/SchoolNotices/MarkAsRead/y")).isEqualTo(1);
    }

    @Test
    void refreshAnsweringErrorFallsBackToPasswordLogin() {
        server.happyLogin("DZIENNIKSID=d; Path=/", "oauth_token=t0; Path=/")
                .on("/refreshToken", Reply.cookies(500, "", "oauth_token=new; Path=/"));
        SynergiaSession session = session();
        int first = session.ensureLoggedIn();

        session.renew(first);

        assertThat(posts()).isEqualTo(2);
    }

    @Test
    void locksAreReleasedForOtherThreads() throws Exception {
        loginWithRefreshingTokens();
        SynergiaSession session = session();
        int generation = session.ensureLoggedIn();
        session.renew(generation);
        session.relogin(session.ensureLoggedIn());
        session.exclusive(() -> "done");

        CompletableFuture<Integer> other = CompletableFuture.supplyAsync(() -> {
            session.exclusive(() -> "other thread");
            return session.relogin(session.ensureLoggedIn());
        });

        assertThat(other.get(5, TimeUnit.SECONDS)).isPositive();
    }

    @Test
    void credentialsPostLooksLikeTheBrowserAndStableFormOnlyForAuthorizationPaths() {
        String b = server.base().toString();
        loginWithRefreshingTokens().on("/OAuth/Authorization", s -> {
            if ("POST".equals(s.method())) {
                return Reply.json(200, "{\"status\":\"ok\",\"goTo\":\"/OAuth/Authorization/2FA?client_id=46\"}");
            }
            return s.query() != null && s.query().contains("response_type") ? Reply.redirect(b + "/elsewhere")
                    : Reply.html(200, "form");
        }).on("/gateway/api/2.0/Auth/TokenInfo", Reply.json(401, "{\"UserIdentifier\":\"LID\"}"));

        session().ensureLoggedIn();

        var post = server.seen().stream().filter(s -> "POST".equals(s.method())).findFirst().orElseThrow();
        assertThat(post.header("X-Requested-With")).isEqualTo("XMLHttpRequest");
        assertThat(post.header("Origin")).isEqualTo(b);
        assertThat(server.count("/elsewhere")).isZero();
        assertThat(server.seen()).noneMatch(s -> s.path().contains("UserInfo"));
    }

    @Test
    void jsonMentioningCaptchaIsNotACaptchaPage() {
        server.happyLogin().on("/OAuth/Authorization", s -> "POST".equals(s.method())
                ? Reply.json(200, "{\"status\":\"error\",\"errors\":[{\"message\":\"captcha field invalid\"}]}")
                : Reply.html(200, "form"));

        assertThatThrownBy(() -> session().ensureLoggedIn()).isInstanceOf(LibrusException.InvalidCredentials.class);
    }

    @Test
    void okWithEmptyContinuationIsRejected() {
        server.happyLogin().on("/OAuth/Authorization", s -> "POST".equals(s.method())
                ? Reply.json(200, "{\"status\":\"ok\",\"goTo\":\"\"}") : Reply.html(200, "form"));

        assertThatThrownBy(() -> session().ensureLoggedIn()).isInstanceOf(LibrusException.InvalidCredentials.class);
    }

    @Test
    void descriptiveReaderSkipsWhatIsNotThereAndCachesTheStudentIdentifier() {
        loginWithRefreshingTokens()
                .on("/gateway/api/2.0/DescriptiveGrades", Reply.json(200, "{\"Grades\":[]}"))
                .on("/gateway/api/2.0/Auth/TokenInfo", Reply.json(200, "{\"UserIdentifier\":\"LID-OWN\"}"))
                .on("/gateway/api/2.0/Auth/UserInfo/LID-OWN", Reply.json(200, "{}"))
                .on("/gateway/api/2.0/Auth/DescriptiveGradingSystem/PartialGrades/Student/LID-OWN",
                        Reply.json(200, "{\"data\":[]}"));
        LibrusAccount account = LibrusAccount.builder(new Credentials("1u", "p"))
                .endpoints(LibrusEndpoints.singleHost(server.base())).timeouts(Duration.ofSeconds(5), Duration.ofSeconds(5))
                .build();

        assertThat(account.descriptiveGrades()).isEmpty();
        long lookups = server.count("/gateway/api/2.0/Auth/UserInfo/LID-OWN");
        assertThat(account.descriptiveGrades()).isEmpty();

        assertThat(server.count("/gateway/api/2.0/DescriptiveGrades/Skills")).isZero();
        assertThat(server.count("/gateway/api/2.0/Auth/Subjects")).isZero();
        assertThat(server.count("/gateway/api/2.0/Auth/UserInfo/LID-OWN")).isEqualTo(lookups);
        assertThat(server.count("/gateway/api/2.0/Auth/DescriptiveGradingSystem/PartialGrades/Student/LID-OWN")).isEqualTo(2);
    }

    @Test
    void descriptiveReaderToleratesMissingIdentifierAndDisabledModule() {
        loginWithRefreshingTokens()
                .on("/gateway/api/2.0/Auth/TokenInfo", Reply.json(200, "{\"UserIdentifier\":\"../evil\"}"));
        LibrusAccount noIdentifier = LibrusAccount.builder(new Credentials("1u", "p"))
                .endpoints(LibrusEndpoints.singleHost(server.base())).build();
        assertThat(noIdentifier.descriptiveGrades()).isEmpty();
        assertThat(server.seen()).noneMatch(s -> s.path().contains("PartialGrades") || s.path().contains("UserInfo"));

        server.on("/gateway/api/2.0/Auth/TokenInfo", Reply.json(200, "{\"UserIdentifier\":\"LID-2\"}"))
                .on("/gateway/api/2.0/Auth/UserInfo/LID-2", Reply.json(200, "{\"IdentifierOfStudentAssignedWithUser\":\"KID\"}"))
                .on("/gateway/api/2.0/Auth/DescriptiveGradingSystem/PartialGrades/Student/KID", Reply.json(403, "{}"));
        LibrusAccount disabled = LibrusAccount.builder(new Credentials("2u", "p"))
                .endpoints(LibrusEndpoints.singleHost(server.base())).build();
        assertThat(disabled.descriptiveGrades()).isEmpty();
        assertThat(server.count("/gateway/api/2.0/Auth/DescriptiveGradingSystem/PartialGrades/Student/KID")).isEqualTo(1);
    }

    @Test
    void suspiciousStudentIdentifierIsNotPutIntoAPath() {
        loginWithRefreshingTokens()
                .on("/gateway/api/2.0/Auth/TokenInfo", Reply.json(200, "{\"UserIdentifier\":\"LID-3\"}"))
                .on("/gateway/api/2.0/Auth/UserInfo/LID-3", Reply.json(200, "{\"IdentifierOfStudentAssignedWithUser\":\"../kid\"}"));
        LibrusAccount account = LibrusAccount.builder(new Credentials("3u", "p"))
                .endpoints(LibrusEndpoints.singleHost(server.base())).build();

        assertThat(account.descriptiveGrades()).isEmpty();
        assertThat(server.seen()).noneMatch(s -> s.path().contains("PartialGrades"));
    }

    @Test
    void renewOrReloginBeforeAnyLoginPerformsAPasswordLogin() {
        loginWithRefreshingTokens();

        assertThat(session().renew(5).generation()).isEqualTo(1);
        assertThat(session().relogin(5)).isEqualTo(1);
        assertThat(posts()).isEqualTo(2);
        assertThat(server.count("/refreshToken")).isZero();
    }

    @Test
    void refreshWithoutTokenCookieFallsBackToPasswordLogin() {
        server.happyLogin("DZIENNIKSID=d; Path=/", "oauth_token=t0; Path=/").on("/refreshToken", Reply.html(200, ""));
        SynergiaSession session = session();

        session.renew(session.ensureLoggedIn());

        assertThat(posts()).isEqualTo(2);
    }

    @Test
    void refreshToAnEmptyTokenFallsBackToPasswordLogin() {
        server.happyLogin("DZIENNIKSID=d; Path=/", "oauth_token=t0; Path=/")
                .on("/refreshToken", Reply.cookies(200, "", "oauth_token=; Path=/"));
        SynergiaSession session = session();

        session.renew(session.ensureLoggedIn());

        assertThat(posts()).isEqualTo(2);
    }

    @Test
    void forgettingCookiesKeepsTheOthers() {
        loginWithRefreshingTokens().on("/set", Reply.cookies(200, "", "keep=1; Path=/", "drop=2; Path=/"));
        HttpTransport http = HttpTransport.withDefaults();
        http.get(server.base().resolve("/set"));

        http.forgetCookies(java.util.List.of("drop"));

        assertThat(http.cookie(server.base().resolve("/"), "keep")).isPresent();
        assertThat(http.cookie(server.base().resolve("/"), "drop")).isEmpty();
    }
}
