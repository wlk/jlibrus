package io.github.wlk.jlibrus.server.it;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * One part failing must not take the rest down: a child whose Librus login no longer works. Runs in its own
 * application so the failing account's login backoff stays here.
 */
@QuarkusTest
@QuarkusTestResource(MockLibrusResource.class)
@TestProfile(PartialFailureIT.Profile.class)
class PartialFailureIT {

    /** A third child whose configured password is no longer valid. */
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("LIBRUS_ACCOUNT_3_LOGIN", "1111111u",
                    "LIBRUS_ACCOUNT_3_PASSWORD", "stale",
                    "LIBRUS_ACCOUNT_3_NAME", "Zosia");
        }
    }

    private String cookie;

    @BeforeEach
    void setUp() {
        cookie = Session.login();
    }

    @Test
    void childWithFailingLoginDoesNotHideTheOtherChildren() {
        Session.as(cookie).get("/api/children").then().statusCode(200)
                .body("id", contains("1234567u", "7654321u", "1111111u"))
                .body("[0].className", notNullValue())
                .body("[2].name", equalTo("Zosia"))
                .body("[2].className", nullValue());
    }
}
