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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Login branches the mock does not exercise: odd answers, loops, foreign hosts, missing cookies. */
class LoginEdgeCasesTest {

    private static final String SESSION = "DZIENNIKSID=d; Path=/";
    private static final String TOKEN = "oauth_token=t; Path=/; Max-Age=600";
    private final ScriptedServer server = new ScriptedServer();
    private final HttpTransport http = HttpTransport.withDefaults();

    @AfterEach
    void stop() {
        server.close();
    }

    private void login() {
        new SynergiaLogin(LibrusEndpoints.singleHost(server.base()), http, new ObjectMapper())
                .login(new Credentials("1u", "p"));
    }

    @Test
    void happyPathActivatesGatewayWithTokenInfoAndUserInfo() {
        server.happyLogin(SESSION, TOKEN)
                .on("/gateway/api/2.0/Auth/TokenInfo", Reply.json(200, "{\"UserIdentifier\":\"LID-9\"}"))
                .on("/gateway/api/2.0/Auth/UserInfo/LID-9", Reply.json(200, "{}"));

        login();

        assertThat(server.count("/gateway/api/2.0/Auth/UserInfo/LID-9")).isEqualTo(1);
        assertThat(server.seen().stream().filter(s -> s.path().equals("/OAuth/Authorization") && s.method().equals("POST"))
                .findFirst().orElseThrow().body()).isEqualTo("action=login&login=1u&pass=p");
    }

    @Test
    void suspiciousUserIdentifierIsNotPutIntoAPath() {
        server.happyLogin(SESSION, TOKEN)
                .on("/gateway/api/2.0/Auth/TokenInfo", Reply.json(200, "{\"UserIdentifier\":\"../x\"}"));

        login();

        assertThat(server.seen()).noneMatch(s -> s.path().contains("UserInfo"));
    }

    @Test
    void nonJsonTokenInfoSkipsActivation() {
        server.happyLogin(SESSION, TOKEN).on("/gateway/api/2.0/Auth/TokenInfo", Reply.html(200, "<html>"));

        login();

        assertThat(server.seen()).noneMatch(s -> s.path().contains("UserInfo"));
    }

    @Test
    void missingEntryRedirectIsServiceUnavailable() {
        server.on("/loguj/portalRodzina", Reply.html(200, "marketing"));

        assertThatThrownBy(this::login).isInstanceOf(LibrusException.ServiceUnavailable.class).hasMessageContaining("200");
    }

    @Test
    void entryRedirectToForeignHostIsRefused() {
        server.on("/loguj/portalRodzina", Reply.redirect("https://evil.example/OAuth"));

        assertThatThrownBy(this::login).isInstanceOf(LibrusException.ProtocolError.class).hasMessageContaining("evil.example");
    }

    @Test
    void nonJsonCredentialAnswerIsAnOutageNotBadPassword() {
        server.happyLogin().on("/OAuth/Authorization", s -> "POST".equals(s.method())
                ? Reply.html(503, "<html>Przerwa techniczna</html>") : Reply.html(200, "form"));

        assertThatThrownBy(this::login).isInstanceOf(LibrusException.ServiceUnavailable.class).hasMessageContaining("503");
    }

    @Test
    void okWithoutContinuationIsTreatedAsRejected() {
        server.happyLogin().on("/OAuth/Authorization", s -> "POST".equals(s.method())
                ? Reply.json(200, "{\"status\":\"ok\"}") : Reply.html(200, "form"));

        assertThatThrownBy(this::login).isInstanceOf(LibrusException.InvalidCredentials.class)
                .hasMessageContaining("login or password rejected");
    }

    @Test
    void redirectAnswerToCredentialsIsFollowed() {
        String b = server.base().toString();
        server.happyLogin(SESSION, TOKEN).on("/OAuth/Authorization", s -> "POST".equals(s.method())
                ? Reply.redirect(b + "/OAuth/Authorization/2FA?client_id=46") : Reply.html(200, "form"))
                .on("/gateway/api/2.0/Auth/TokenInfo", Reply.json(401, "{}"));

        login();

        assertThat(server.count("/OAuth/Authorization/2FA")).isEqualTo(1);
    }

    @Test
    void endlessRedirectChainIsCut() {
        String b = server.base().toString();
        server.happyLogin().on("/OAuth/Authorization/2FA", Reply.redirect(b + "/OAuth/Authorization/2FA"));

        assertThatThrownBy(this::login).isInstanceOf(LibrusException.ProtocolError.class).hasMessageContaining("10");
        assertThat(server.count("/OAuth/Authorization/2FA")).isEqualTo(SynergiaLogin.MAX_HOPS);
    }

    @Test
    void captchaAtTheEndOfTheChainIsRecognised() {
        server.happyLogin().on("/loguj/portalRodzina", s -> s.query() == null
                ? Reply.redirect(server.base() + "/OAuth/Authorization") : Reply.html(200, "<div class=g-recaptcha>"));

        assertThatThrownBy(this::login).isInstanceOf(LibrusException.CaptchaRequired.class);
    }

    @Test
    void chainWithoutSessionCookieFails() {
        server.happyLogin(TOKEN);

        assertThatThrownBy(this::login).isInstanceOf(LibrusException.ProtocolError.class)
                .hasMessageContaining("session cookie");
    }

    @Test
    void missingTokenIsFetchedWithRefresh() {
        server.happyLogin(SESSION).on("/refreshToken", Reply.cookies(200, "", TOKEN));

        login();

        assertThat(server.count("/refreshToken")).isEqualTo(1);
    }

    @Test
    void missingTokenThatCannotBeRefreshedFails() {
        server.happyLogin(SESSION).on("/refreshToken", Reply.html(200, ""));

        assertThatThrownBy(this::login).isInstanceOf(LibrusException.ProtocolError.class).hasMessageContaining("API token");
    }

    @Test
    void failedRefreshStatusFails() {
        server.happyLogin(SESSION).on("/refreshToken", Reply.cookies(500, "", TOKEN));

        assertThatThrownBy(this::login).isInstanceOf(LibrusException.ProtocolError.class);
    }

    @Test
    void oldSessionCookiesAreDroppedBeforeLogin() {
        server.happyLogin(SESSION, TOKEN).on("/old", Reply.cookies(200, "", "SDZIENNIKSID=stale; Path=/"));
        http.get(server.base().resolve("/old"));

        login();

        assertThat(server.seen().stream().filter(s -> s.path().equals("/loguj/portalRodzina")).findFirst()
                .orElseThrow().cookie()).isNull();
    }

    @Test
    void stableFormRedirectIsOpened() {
        String b = server.base().toString();
        server.happyLogin(SESSION, TOKEN).on("/OAuth/Authorization", s -> {
            if ("POST".equals(s.method())) {
                return Reply.json(200, "{\"status\":\"ok\",\"goTo\":\"/OAuth/Authorization/2FA?client_id=46\"}");
            }
            return s.query() != null && s.query().contains("response_type")
                    ? Reply.redirect(b + "/OAuth/Authorization?client_id=46") : Reply.html(200, "form");
        });

        login();

        assertThat(server.seen().stream().filter(s -> s.path().equals("/OAuth/Authorization") && "GET".equals(s.method())))
                .hasSize(2);
    }

    @Test
    void credentialsPostCarriesXhrHeadersOnlyToLibrus() {
        server.happyLogin(SESSION, TOKEN);

        login();

        assertThat(server.seen()).filteredOn(s -> "POST".equals(s.method())).hasSize(1);
    }
}
