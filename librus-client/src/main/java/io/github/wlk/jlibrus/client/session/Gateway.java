package io.github.wlk.jlibrus.client.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.http.HttpResult;

import java.io.IOException;
import java.util.Map;

/**
 * Reads JSON resources from the Synergia gateway ({@code /gateway/api/2.0/...}), renewing the session when
 * Librus says it expired: first with a token refresh, then with a full password login.
 */
public final class Gateway {

    private static final Map<String, String> ACCEPT_JSON = Map.of("Accept", "application/json");

    private final SynergiaSession session;
    private final ObjectMapper json;

    public Gateway(SynergiaSession session, ObjectMapper json) {
        this.session = session;
        this.json = json;
    }

    /** GET {@code resource} (e.g. {@code "Grades"} or {@code "Timetables?weekStart=2026-11-23"}). */
    public JsonNode get(String resource) {
        return session.exclusive(() -> call(resource, () -> fetch(resource)));
    }

    /** POST with an empty JSON object to a read-only resource that requires POST, returning its JSON. */
    public JsonNode postForJson(String resource) {
        return session.exclusive(() -> call(resource, () ->
                session.http().sendJson("POST", session.endpoints().gateway(resource), "{}")));
    }

    /** POST without body to an idempotent action such as {@code SchoolNotices/MarkAsRead/{id}}. */
    public HttpResult post(String resource) {
        return session.exclusive(() -> postWithRenewal(resource));
    }

    private HttpResult postWithRenewal(String resource) {
        int generation = session.ensureLoggedIn();
        HttpResult result = postOnce(resource);
        if (isExpired(result)) {
            session.renew(generation);
            result = postOnce(resource);
        }
        return result;
    }

    private HttpResult postOnce(String resource) {
        return session.http().sendJson("POST", session.endpoints().gateway(resource), null);
    }

    private JsonNode call(String resource, java.util.function.Supplier<HttpResult> request) {
        int generation = session.ensureLoggedIn();
        HttpResult result = request.get();
        if (isExpired(result)) {
            generation = session.renew(generation);
            result = request.get();
        }
        if (isExpired(result)) {
            session.relogin(generation);
            result = request.get();
        }
        if (isExpired(result)) {
            throw new LibrusException.SessionExpired("Librus keeps rejecting the session for " + resource);
        }
        return classify(resource, result);
    }

    private HttpResult fetch(String resource) {
        return session.http().get(session.endpoints().gateway(resource), ACCEPT_JSON);
    }

    /** A plain 401, or an HTML login page where JSON was expected, means the session is gone. */
    static boolean isExpired(HttpResult result) {
        if (result.status() == 401) {
            return !isInsufficientScopes(result);
        }
        return result.isSuccess() && !result.looksLikeJson();
    }

    private static boolean isInsufficientScopes(HttpResult result) {
        return result.body().contains("Insufficient scopes");
    }

    private JsonNode classify(String resource, HttpResult result) {
        if (result.isSuccess()) {
            return parse(resource, result.body());
        }
        throw failure(resource, result.status());
    }

    /** Maps a non-2xx status (after renewal attempts) to the exception the caller can act on. */
    static LibrusException failure(String resource, int status) {
        return switch (status) {
            case 401, 403, 404 -> new LibrusException.ModuleUnavailable(resource + " is not available for this account");
            case 429, 502, 503, 504 -> new LibrusException.ServiceUnavailable("Librus is unavailable (HTTP " + status + ")");
            default -> new LibrusException.ProtocolError("Unexpected HTTP " + status + " for " + resource);
        };
    }

    private JsonNode parse(String resource, String body) {
        try {
            return json.readTree(body);
        } catch (IOException e) {
            throw new LibrusException.ProtocolError("Malformed JSON from " + resource, e);
        }
    }
}
