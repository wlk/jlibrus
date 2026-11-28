package io.github.wlk.jlibrus.client.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.Credentials;
import io.github.wlk.jlibrus.client.Identifiers;
import io.github.wlk.jlibrus.client.LibrusEndpoints;
import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.http.HttpResult;
import io.github.wlk.jlibrus.client.http.HttpTransport;
import io.github.wlk.jlibrus.client.parse.Json;

import java.net.HttpCookie;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Synergia password login: entry point, OAuth form, credentials POST, then a hand-walked redirect chain
 * that ends with session cookies on the Synergia host. Never follows a redirect outside Librus' own hosts.
 */
final class SynergiaLogin {

    static final String CLIENT_ID = "46";
    static final List<String> SESSION_COOKIES = List.of("oauth_token", "DZIENNIKSID", "SDZIENNIKSID");
    private static final List<String> CAPTCHA_MARKERS = List.of("g-recaptcha", "recaptcha", "hcaptcha", "captcha");

    private final LibrusEndpoints endpoints;
    private final HttpTransport http;
    private final ObjectMapper json;

    SynergiaLogin(LibrusEndpoints endpoints, HttpTransport http, ObjectMapper json) {
        this.endpoints = endpoints;
        this.http = http;
        this.json = json;
    }

    void login(Credentials credentials) {
        http.forgetCookies(SESSION_COOKIES);
        URI authorize = openEntryPoint();
        openAuthorizationForm(authorize);
        URI next = postCredentials(credentials);
        rejectCaptcha(http.followRedirects(next, endpoints::isTrustedLoginHop, "Librus login"));
        verifySession();
        activateGateway();
    }

    private URI openEntryPoint() {
        HttpResult entry = http.get(endpoints.synergiaPath("/loguj/portalRodzina"));
        URI authorize = entry.location().orElseThrow(() -> new LibrusException.ServiceUnavailable(
                "Librus did not start the login flow (HTTP " + entry.status() + ")"));
        return trusted(authorize);
    }

    private void openAuthorizationForm(URI authorize) {
        HttpResult form = http.get(authorize);
        form.location()
                .filter(next -> form.isRedirect() && next.getPath().startsWith("/OAuth/Authorization"))
                .map(this::trusted)
                .ifPresent(http::get);
    }

    private URI postCredentials(Credentials credentials) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("action", "login");
        form.put("login", credentials.login());
        form.put("pass", credentials.password());
        URI loginUri = endpoints.oauthPath("/OAuth/Authorization?client_id=" + CLIENT_ID);
        HttpResult result = http.postForm(loginUri, form, xhrHeaders(loginUri));
        if (result.isRedirect()) {
            return trusted(result.location().orElseThrow());
        }
        rejectCaptcha(result);
        return continuation(result);
    }

    private URI continuation(HttpResult result) {
        if (!result.looksLikeJson()) {
            throw new LibrusException.ServiceUnavailable(
                    "Librus login answered with a non-JSON page (HTTP " + result.status() + "); maintenance?");
        }
        JsonNode body = parse(result.body());
        String status = body.path("status").asText("");
        String goTo = body.path("goTo").asText("");
        if (!"ok".equals(status) || goTo.isEmpty()) {
            String reason = body.path("errors").path(0).path("message").asText("login or password rejected");
            throw new LibrusException.InvalidCredentials("Librus rejected the login: " + reason);
        }
        URI base = endpoints.oauthPath("/OAuth/Authorization?client_id=" + CLIENT_ID
                + "&response_type=code&scope=mydata");
        return trusted(base.resolve(goTo));
    }

    private void verifySession() {
        if (http.cookie(endpoints.synergiaPath("/"), "DZIENNIKSID").isEmpty()) {
            throw new LibrusException.ProtocolError("Librus login finished without a Synergia session cookie");
        }
        if (apiToken().isEmpty() && refreshApiToken().isEmpty()) {
            throw new LibrusException.ProtocolError("Librus login finished without an API token");
        }
    }

    /** Value of the API token cookie ({@code oauth_token}) that Synergia requests carry, if any. */
    Optional<String> apiToken() {
        return http.cookie(endpoints.synergiaPath("/"), "oauth_token").map(HttpCookie::getValue);
    }

    /** Asks Synergia for a fresh API token; the token then held, or empty when the refresh failed. */
    Optional<String> refreshApiToken() {
        HttpResult refresh = http.get(endpoints.synergiaPath("/refreshToken"));
        return refresh.isSuccess() ? apiToken() : Optional.empty();
    }

    /** Some accounts need TokenInfo/UserInfo touched once before other gateway resources answer. */
    private void activateGateway() {
        HttpResult tokenInfo = http.get(endpoints.gateway("Auth/TokenInfo"), Map.of("Accept", "application/json"));
        if (!tokenInfo.isSuccess() || !tokenInfo.looksLikeJson()) {
            return;
        }
        String user = parse(tokenInfo.body()).path("UserIdentifier").asText("");
        if (Identifiers.isSafeId(user)) {
            http.get(endpoints.gateway("Auth/UserInfo/" + user), Map.of("Accept", "application/json"));
        }
    }

    private static void rejectCaptcha(HttpResult result) {
        if (result.looksLikeJson()) {
            return;
        }
        String body = result.body().toLowerCase(Locale.ROOT);
        if (CAPTCHA_MARKERS.stream().anyMatch(body::contains)) {
            throw new LibrusException.CaptchaRequired(
                    "Librus asks for a captcha; log in once in a browser, then retry later");
        }
    }

    private URI trusted(URI target) {
        if (!endpoints.isTrustedLoginHop(target)) {
            throw new LibrusException.ProtocolError("Login redirect to an unexpected host: " + target.getHost());
        }
        return target;
    }

    private Map<String, String> xhrHeaders(URI loginUri) {
        String origin = endpoints.oauth().toString();
        return Map.of(
                "Accept", "application/json, text/javascript, */*; q=0.01",
                "Origin", origin,
                "Referer", loginUri.toString(),
                "X-Requested-With", "XMLHttpRequest",
                "Sec-Fetch-Dest", "empty",
                "Sec-Fetch-Mode", "cors",
                "Sec-Fetch-Site", "same-origin");
    }

    private JsonNode parse(String body) {
        return Json.parse(json, body,
                e -> new LibrusException.ServiceUnavailable("Librus login answered with malformed JSON", e));
    }
}
