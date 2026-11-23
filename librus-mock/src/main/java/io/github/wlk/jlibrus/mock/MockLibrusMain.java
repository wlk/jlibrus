package io.github.wlk.jlibrus.mock;

import java.time.Clock;
import java.util.List;
import java.util.logging.Logger;

/**
 * Runs the mock as a standalone server. Environment: {@code MOCK_PORT} (default 8090), {@code MOCK_HOST}
 * (default 0.0.0.0) and {@code MOCK_PUBLIC_URL} (address the client uses, default {@code http://localhost:PORT}).
 */
public final class MockLibrusMain {

    private static final Logger LOG = Logger.getLogger(MockLibrusMain.class.getName());

    private MockLibrusMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        int port = Integer.parseInt(env("MOCK_PORT", "8090"));
        String host = env("MOCK_HOST", "0.0.0.0");
        String publicUrl = env("MOCK_PUBLIC_URL", "http://localhost:" + port);
        List<MockAccount> accounts = List.of(MockAccount.ola(), MockAccount.jan());
        MockLibrus mock = MockLibrus.start(host, port, publicUrl, Clock.systemUTC(), accounts);
        LOG.info(() -> "Mock Librus listening on " + host + ":" + port + " as " + mock.baseUri());
        accounts.forEach(a -> LOG.info(() -> "  account " + a.login() + " / " + a.password()
                + " (" + a.studentFirstName() + " " + a.studentLastName() + ")"));
        Runtime.getRuntime().addShutdownHook(new Thread(mock::close));
        Thread.currentThread().join();
    }

    private static String env(String name, String fallback) {
        return java.util.Optional.ofNullable(System.getenv(name)).filter(v -> !v.isBlank()).orElse(fallback);
    }
}
