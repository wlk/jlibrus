package io.github.wlk.jlibrus.mock;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Minimal test browser: keeps cookies by name, never follows redirects. */
final class Browser {

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final URI base;

    Browser(URI base) {
        this.base = base;
    }

    HttpResponse<String> get(String pathOrUrl) {
        return send(request(pathOrUrl).GET());
    }

    HttpResponse<String> post(String path, String contentType, String body) {
        return send(request(path).header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    String cookie(String name) {
        return cookies.get(name);
    }

    void forget(String name) {
        cookies.remove(name);
    }

    /** Runs the whole Synergia login and returns the final response. */
    HttpResponse<String> login(String login, String password) {
        get(get("/loguj/portalRodzina").headers().firstValue("Location").orElseThrow());
        get("/OAuth/Authorization?client_id=46");
        post("/OAuth/Authorization?client_id=46", "application/x-www-form-urlencoded",
                "action=login&login=" + login + "&pass=" + password);
        HttpResponse<String> current = get("/OAuth/Authorization/2FA?client_id=46");
        while (current.statusCode() == 302) {
            current = get(current.headers().firstValue("Location").orElseThrow());
        }
        return current;
    }

    private HttpRequest.Builder request(String pathOrUrl) {
        URI uri = pathOrUrl.startsWith("http") ? URI.create(pathOrUrl) : base.resolve(pathOrUrl);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("; ")));
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            response.headers().allValues("Set-Cookie").forEach(header -> {
                String pair = header.split(";", 2)[0];
                int eq = pair.indexOf('=');
                cookies.put(pair.substring(0, eq), pair.substring(eq + 1));
            });
            return response;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
