package io.github.wlk.jlibrus.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Test HTTP server whose answers are scripted per path, for edge cases the mock never produces. */
public final class ScriptedServer implements AutoCloseable {

    /** Scripted answer. */
    public record Reply(int status, Map<String, List<String>> headers, String body) {
        public static Reply json(int status, String body) {
            return new Reply(status, Map.of("Content-Type", List.of("application/json")), body);
        }

        public static Reply html(int status, String body) {
            return new Reply(status, Map.of(), body);
        }

        public static Reply redirect(String location, String... cookies) {
            return new Reply(302, Map.of("Location", List.of(location), "Set-Cookie", List.of(cookies)), "");
        }

        public static Reply cookies(int status, String body, String... cookies) {
            return new Reply(status, Map.of("Set-Cookie", List.of(cookies)), body);
        }
    }

    /** What the server saw. */
    public record Seen(String method, String path, String query, String cookie, String body,
                       Map<String, List<String>> headers) {
        public String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().getFirst()).findFirst().orElse(null);
        }
    }

    private final HttpServer server;
    private final Map<String, Function<Seen, Reply>> routes = new ConcurrentHashMap<>();
    private final List<Seen> seen = new CopyOnWriteArrayList<>();

    public ScriptedServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public URI base() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    public ScriptedServer on(String path, Function<Seen, Reply> reply) {
        routes.put(path, reply);
        return this;
    }

    public ScriptedServer on(String path, Reply reply) {
        return on(path, s -> reply);
    }

    public List<Seen> seen() {
        return List.copyOf(seen);
    }

    public long count(String path) {
        return seen.stream().filter(s -> s.path().equals(path)).count();
    }

    /** Routes for a login that succeeds with the given cookies at the end of the chain. */
    public ScriptedServer happyLogin(String... finalCookies) {
        String b = base().toString();
        return on("/loguj/portalRodzina", s -> s.query() == null
                ? Reply.redirect(b + "/OAuth/Authorization?client_id=46&response_type=code")
                : Reply.cookies(200, "ok", finalCookies))
                .on("/OAuth/Authorization", s -> "POST".equals(s.method())
                        ? Reply.json(200, "{\"status\":\"ok\",\"goTo\":\"/OAuth/Authorization/2FA?client_id=46\"}")
                        : Reply.html(200, "form"))
                .on("/OAuth/Authorization/2FA", Reply.redirect(b + "/loguj/portalRodzina?code=1"));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Seen request = new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(), exchange.getRequestHeaders().getFirst("Cookie"), body,
                Map.copyOf(exchange.getRequestHeaders()));
        seen.add(request);
        Reply reply = routes.getOrDefault(request.path(), s -> Reply.html(404, "nope")).apply(request);
        reply.headers().forEach((k, values) -> values.forEach(v -> exchange.getResponseHeaders().add(k, v)));
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
