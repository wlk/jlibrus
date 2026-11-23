package io.github.wlk.jlibrus.server.it;

import io.github.wlk.jlibrus.mock.MockLibrus;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.time.Clock;
import java.util.Map;

/** Starts the offline Librus mock and points the server at it with two configured children. */
public class MockLibrusResource implements QuarkusTestResourceLifecycleManager {

    private static volatile MockLibrus mock;

    /** The running mock, so tests can simulate outages and expiry. */
    public static MockLibrus mock() {
        return mock;
    }

    @Override
    public Map<String, String> start() {
        mock = MockLibrus.startLocal(Clock.systemUTC());
        return Map.of(
                "librus.endpoint-override", mock.baseUri().toString(),
                "LIBRUS_ACCOUNT_1_LOGIN", "1234567u",
                "LIBRUS_ACCOUNT_1_PASSWORD", "demo",
                "LIBRUS_ACCOUNT_2_LOGIN", "7654321u",
                "LIBRUS_ACCOUNT_2_PASSWORD", "demo",
                "LIBRUS_ACCOUNT_2_NAME", "Janek");
    }

    @Override
    public void stop() {
        if (mock != null) {
            mock.close();
        }
    }
}
