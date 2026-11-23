package io.github.wlk.jlibrus.mock;

import io.github.wlk.jlibrus.mock.http.MockExchange;

import java.util.Optional;

/**
 * Emulates the Synergia login: entry point redirect, OAuth form, credentials POST answering JSON, the
 * 2FA/Grant redirect chain back to Synergia that sets session cookies, and {@code /refreshToken}.
 */
final class AuthHandlers {

    static final String API_SESSION = "LIBRUS_API_SID";
    private static final String SESSION_ATTRIBUTES = "Path=/; HttpOnly";

    private final MockState state;
    private final String base;

    AuthHandlers(MockState state, String base) {
        this.state = state;
        this.base = base;
    }

    /** {@code GET /loguj/portalRodzina}: start of the flow, or its end when called back with a code. */
    void portalRodzina(MockExchange ex) {
        String code = ex.query().get("code");
        if (code == null) {
            ex.redirect(base + "/OAuth/Authorization?client_id=46&response_type=code&scope=mydata&state=mock");
            return;
        }
        Optional<String> login = state.redeemCode(code);
        if (login.isEmpty()) {
            ex.html(400, "<html><body>invalid_request</body></html>");
            return;
        }
        ex.setCookie("DZIENNIKSID", state.newSynergiaSession(login.get()), SESSION_ATTRIBUTES)
                .setCookie("SDZIENNIKSID", "s" + System.nanoTime(), SESSION_ATTRIBUTES)
                .setCookie("oauth_token", state.newToken(login.get()), "Path=/; Max-Age=600; HttpOnly")
                .html(200, "<html><body>jesteś zalogowany</body></html>");
    }

    /** {@code /OAuth/Authorization}: GET shows the form, POST checks the credentials. */
    void authorization(MockExchange ex) {
        if ("GET".equals(ex.method()) && ex.query().containsKey("response_type")) {
            ex.redirect(base + "/OAuth/Authorization?client_id=46");
            return;
        }
        if ("GET".equals(ex.method())) {
            ex.setCookie(API_SESSION, state.newApiSession(), "Path=/OAuth; HttpOnly")
                    .html(200, "<html><form method=post><input name=login><input name=pass></form></html>");
            return;
        }
        Optional<String> apiSession = ex.cookie(API_SESSION).filter(state::knowsApiSession);
        if (apiSession.isEmpty()) {
            ex.json(200, "{\"status\":\"error\",\"errors\":[{\"message\":\"Sesja wygasła\"}]}");
            return;
        }
        String login = ex.form().getOrDefault("login", "");
        String pass = ex.form().getOrDefault("pass", "");
        if ("captcha".equals(login)) {
            ex.html(200, "<html><div class=\"g-recaptcha\" data-sitekey=\"x\"></div></html>");
            return;
        }
        boolean valid = "login".equals(ex.form().get("action"))
                && state.account(login).map(a -> a.password().equals(pass)).orElse(false);
        if (!valid) {
            ex.json(200, "{\"status\":\"error\",\"errors\":[{\"message\":\"Nieprawidłowy login lub hasło.\"}]}");
            return;
        }
        state.authenticate(apiSession.get(), login);
        ex.json(200, "{\"status\":\"ok\",\"goTo\":\"/OAuth/Authorization/2FA?client_id=46\"}");
    }

    /** {@code GET /OAuth/Authorization/2FA}: device check, sets the long-lived device cookie. */
    void twoFactor(MockExchange ex) {
        if (apiLogin(ex).isEmpty()) {
            ex.html(403, "<html>Brak dostępu</html>");
            return;
        }
        ex.setCookie("DeviceCookie", "device-" + System.nanoTime(), "Path=/OAuth; Max-Age=31536000; HttpOnly")
                .redirect(base + "/OAuth/Authorization/Grant?client_id=46");
    }

    /** {@code GET /OAuth/Authorization/Grant}: issues the code and sends the browser back to Synergia. */
    void grant(MockExchange ex) {
        Optional<String> login = apiLogin(ex);
        if (login.isEmpty()) {
            ex.html(403, "<html>Brak dostępu</html>");
            return;
        }
        ex.redirect(base + "/loguj/portalRodzina?code=" + state.issueCode(login.get()) + "&state=mock");
    }

    /** {@code GET /refreshToken}: new API token for a live Synergia session. */
    void refreshToken(MockExchange ex) {
        Optional<String> login = state.synergiaLogin(ex.cookie("DZIENNIKSID").orElse(null));
        if (login.isEmpty()) {
            ex.html(401, "");
            return;
        }
        ex.setCookie("oauth_token", state.newToken(login.get()), "Path=/; Max-Age=600; HttpOnly")
                .setCookie("SDZIENNIKSID", "s" + System.nanoTime(), SESSION_ATTRIBUTES)
                .html(200, "");
    }

    private Optional<String> apiLogin(MockExchange ex) {
        return ex.cookie(API_SESSION).flatMap(state::authenticatedLogin);
    }
}
