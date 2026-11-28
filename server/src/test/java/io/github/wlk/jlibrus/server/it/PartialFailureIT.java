package io.github.wlk.jlibrus.server.it;

import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import io.github.wlk.jlibrus.server.service.SummaryService;
import io.quarkus.mailer.Mail;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * One part failing must not take the rest down: a child whose Librus login no longer works, or one subscriber whose
 * mail server refuses a summary. Runs in its own application so the failing account's login backoff stays here.
 */
@QuarkusTest
@QuarkusTestResource(MockLibrusResource.class)
@TestProfile(PartialFailureIT.Profile.class)
class PartialFailureIT {

    /** A third child whose configured password is no longer valid, and a mail server that can refuse recipients. */
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("LIBRUS_ACCOUNT_3_LOGIN", "1111111u",
                    "LIBRUS_ACCOUNT_3_PASSWORD", "stale",
                    "LIBRUS_ACCOUNT_3_NAME", "Zosia");
        }

        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FlakyMailer.class);
        }
    }

    @Inject
    SummaryService summaries;

    @Inject
    FlakyMailer mailer;

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

    @Test
    void refusedSummaryIsRetriedForTheSamePeriodWithoutRepeatingTheOthers() {
        subscribe("retry-ok@example.pl");
        subscribe("retry-refused@example.pl");
        mailer.refuse("retry-refused@example.pl");

        assertThat(summaries.sendDue(Frequency.WEEKLY, Instant.now(), false)).isEqualTo(1);
        assertThat(mailer.sentTo("retry-refused@example.pl")).isEmpty();

        mailer.accept("retry-refused@example.pl");
        summaries.retryPending();

        List<Mail> delivered = mailer.sentTo("retry-ok@example.pl");
        List<Mail> retried = mailer.sentTo("retry-refused@example.pl");
        assertThat(delivered).hasSize(1);
        assertThat(retried).hasSize(1);
        assertThat(retried.getFirst().getHtml()).isEqualTo(delivered.getFirst().getHtml());
    }

    private void subscribe(String email) {
        Session.as(cookie).contentType("application/json")
                .body(Map.of("email", email, "frequencies", List.of("weekly"),
                        "types", List.of("GRADE", "EVENT", "HOMEWORK", "LESSON", "TIMETABLE_CHANGE"), "childIds", List.of()))
                .post("/api/subscriptions").then().statusCode(200);
    }
}
