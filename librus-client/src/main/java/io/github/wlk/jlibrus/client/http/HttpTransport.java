package io.github.wlk.jlibrus.client.http;

import io.github.wlk.jlibrus.client.LibrusException;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * One HTTP client with its own cookie jar. Each Librus account gets its own transport so cookies of
 * different children can never mix. Redirects are never followed automatically.
 */
public final class HttpTransport {

    /** A current desktop browser; Librus serves marketing pages to clients it does not recognise. */
    public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    /** Requests a hand-walked redirect chain may take before it counts as a loop. */
    public static final int MAX_REDIRECTS = 10;

    private final HttpClient client;
    private final CookieJar cookies;
    private final String userAgent;
    private final Duration requestTimeout;

    public HttpTransport(String userAgent, Duration connectTimeout, Duration requestTimeout) {
        this.cookies = new CookieJar();
        this.userAgent = userAgent;
        this.requestTimeout = requestTimeout;
        this.client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .build();
    }

    public static HttpTransport withDefaults() {
        return new HttpTransport(DEFAULT_USER_AGENT, Duration.ofSeconds(10), Duration.ofSeconds(30));
    }

    public HttpResult get(URI uri, Map<String, String> headers) {
        return send(base(uri, headers).GET().build());
    }

    public HttpResult get(URI uri) {
        return get(uri, Map.of());
    }

    /**
     * GETs {@code start} (already trusted by the caller) and follows redirects by hand, only to targets
     * {@code allowed} accepts and for at most {@link #MAX_REDIRECTS} requests.
     *
     * @param flow names the walk in error messages, e.g. {@code "Librus login"}
     * @return the first answer that is not a redirect
     * @throws LibrusException.ProtocolError on a redirect to a target not allowed, or a chain that is too long
     */
    public HttpResult followRedirects(URI start, Predicate<URI> allowed, String flow) {
        URI current = start;
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpResult result = get(current);
            if (!result.isRedirect()) {
                return result;
            }
            current = result.location().orElseThrow();
            if (!allowed.test(current)) {
                throw new LibrusException.ProtocolError(flow + " redirected to an unexpected host: " + current.getHost());
            }
        }
        throw new LibrusException.ProtocolError(flow + " redirected more than " + MAX_REDIRECTS + " times");
    }

    public HttpResult postForm(URI uri, Map<String, String> form, Map<String, String> headers) {
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        Map<String, String> all = new LinkedHashMap<>(headers);
        all.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        return send(base(uri, all).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build());
    }

    public HttpResult sendJson(String method, URI uri, String json) {
        Map<String, String> headers = Map.of("Content-Type", "application/json", "Accept", "application/json");
        return send(base(uri, headers)
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build());
    }

    /** Cookie a request to {@code uri} would carry (respects domain and path), if present. */
    public Optional<HttpCookie> cookie(URI uri, String name) {
        return cookies.visible(uri).stream().filter(c -> c.getName().equals(name)).findFirst();
    }

    /** Forgets every cookie whose name is listed (used before a fresh login). */
    public void forgetCookies(List<String> names) {
        var store = cookies.store();
        store.getCookies().stream().filter(c -> names.contains(c.getName())).toList()
                .forEach(c -> store.remove(null, c));
    }

    private HttpRequest.Builder base(URI uri, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8");
        headers.forEach(builder::setHeader);
        return builder;
    }

    private HttpResult send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new HttpResult(request.uri(), response.statusCode(), response.headers(), response.body());
        } catch (IOException e) {
            throw new LibrusException.ServiceUnavailable(
                    "Cannot reach " + request.uri().getHost() + ": " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LibrusException.ServiceUnavailable("Interrupted while calling " + request.uri().getHost(), e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
