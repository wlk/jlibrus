package io.github.wlk.jlibrus.mock;

import com.sun.net.httpserver.HttpServer;
import io.github.wlk.jlibrus.mock.http.MockExchange;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Offline stand-in for every Librus host on one port: Synergia login + gateway, OAuth server and the messages
 * module. Used by tests and by the demo profile of docker compose. Never talks to the real Librus.
 */
public final class MockLibrus implements AutoCloseable {

    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final Logger LOG = Logger.getLogger(MockLibrus.class.getName());

    private final HttpServer server;
    private final MockState state;
    private final URI baseUri;

    private MockLibrus(HttpServer server, MockState state, URI baseUri) {
        this.server = server;
        this.state = state;
        this.baseUri = baseUri;
    }

    /** Starts on {@code port} (0 = any free port) bound to {@code host}. */
    public static MockLibrus start(String host, int port, String publicBase, Clock clock, List<MockAccount> accounts) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
            String base = publicBase != null ? publicBase : "http://" + host + ":" + server.getAddress().getPort();
            MockState state = new MockState(clock, accounts);
            route(server, state, base);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return new MockLibrus(server, state, URI.create(base));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start mock Librus", e);
        }
    }

    /** Random port on localhost with the two demo children. */
    public static MockLibrus startLocal(Clock clock) {
        return start("127.0.0.1", 0, null, clock, List.of(MockAccount.ola(), MockAccount.jan()));
    }

    private static void route(HttpServer server, MockState state, String base) {
        AuthHandlers auth = new AuthHandlers(state, base);
        GatewayHandler gateway = new GatewayHandler(state);
        MessagesHandler messages = new MessagesHandler(state, base);
        handle(server, "/loguj/portalRodzina", auth::portalRodzina);
        handle(server, "/OAuth/Authorization/2FA", auth::twoFactor);
        handle(server, "/OAuth/Authorization/Grant", auth::grant);
        handle(server, "/OAuth/Authorization", auth::authorization);
        handle(server, "/refreshToken", auth::refreshToken);
        handle(server, GatewayHandler.PREFIX, gateway::handle);
        handle(server, "/wiadomosci3", messages::bootstrap);
        handle(server, "/MultiDomainLogon", messages::multiDomainLogon);
        handle(server, "/messages-app", messages::app);
        handle(server, "/api/", messages::api);
        handle(server, "/GetFile/", ex -> ex.send(200, "application/pdf", "%PDF-1.4 demo"));
        handle(server, "/", ex -> ex.html(404, "<html>Nie znaleziono</html>"));
    }

    private static void handle(HttpServer server, String path, Consumer<MockExchange> handler) {
        server.createContext(path, exchange -> {
            try {
                handler.accept(new MockExchange(exchange));
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Mock handler failed for " + exchange.getRequestURI(), e);
                new MockExchange(exchange).html(500, "<html>error</html>");
            }
        });
    }

    public URI baseUri() {
        return baseUri;
    }

    public MockState state() {
        return state;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
