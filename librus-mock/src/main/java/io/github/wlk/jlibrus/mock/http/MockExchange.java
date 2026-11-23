package io.github.wlk.jlibrus.mock.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Convenience wrapper over {@link HttpExchange}: parsed query, form, cookies and simple responses. */
public final class MockExchange {

    private final HttpExchange exchange;
    private final String body;

    public MockExchange(HttpExchange exchange) {
        this.exchange = exchange;
        this.body = readBody(exchange);
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    public String path() {
        return exchange.getRequestURI().getPath();
    }

    public String body() {
        return body;
    }

    public Map<String, String> query() {
        return parsePairs(exchange.getRequestURI().getRawQuery(), "&");
    }

    public Map<String, String> form() {
        return parsePairs(body, "&");
    }

    public Optional<String> cookie(String name) {
        List<String> headers = exchange.getRequestHeaders().getOrDefault("Cookie", List.of());
        return headers.stream()
                .flatMap(h -> Arrays.stream(h.split(";")))
                .map(String::trim)
                .filter(c -> c.startsWith(name + "="))
                .map(c -> c.substring(name.length() + 1))
                .reduce((first, second) -> second);
    }

    public MockExchange setCookie(String name, String value, String attributes) {
        exchange.getResponseHeaders().add("Set-Cookie", name + "=" + value + "; " + attributes);
        return this;
    }

    public void redirect(String location) {
        exchange.getResponseHeaders().set("Location", location);
        send(302, "text/html", "");
    }

    public void json(int status, String json) {
        send(status, "application/json; charset=utf-8", json);
    }

    public void html(int status, String html) {
        send(status, "text/html; charset=utf-8", html);
    }

    public void send(int status, String contentType, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            exchange.close();
        }
    }

    private static String readBody(HttpExchange exchange) {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Map<String, String> parsePairs(String raw, String separator) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String pair : raw.split(separator)) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }
}
