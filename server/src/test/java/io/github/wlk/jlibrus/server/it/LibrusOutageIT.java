package io.github.wlk.jlibrus.server.it;

import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import io.github.wlk.jlibrus.server.librus.LibrusData;
import io.github.wlk.jlibrus.server.service.SummaryService;
import io.github.wlk.jlibrus.server.service.SyncService;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/** What users see while Librus is down: no empty calendars, no lost summaries, isolated sync failures. */
@QuarkusTest
@QuarkusTestResource(MockLibrusResource.class)
class LibrusOutageIT {

    @Inject
    LibrusData data;

    @Inject
    SummaryService summaries;

    @Inject
    SyncService sync;

    @Inject
    MockMailbox mailbox;

    private String cookie;

    @BeforeEach
    void setUp() {
        cookie = Session.login();
        mailbox.clear();
    }

    @AfterEach
    void endOutage() {
        MockLibrusResource.mock().state().setOutage(false);
        data.forgetEverything();
    }

    private void outage() {
        MockLibrusResource.mock().state().setOutage(true);
        MockLibrusResource.mock().state().expireSynergiaSessions();
        data.forgetEverything();
    }

    @Test
    void feedKeepsServingLastGoodCalendarAndNeverAnEmptyOne() {
        String path = Session.as(cookie).get("/api/feeds").then().extract().path("[0].path");
        String good = RestAssured.get(path).then().statusCode(200).extract().asString();
        String fresh = Session.as(cookie).contentType("application/json")
                .body(Map.of("name", "Nowy", "types", List.of("EVENT"))).post("/api/feeds")
                .then().statusCode(200).extract().path("path");

        outage();

        assertThat(RestAssured.get(path).then().statusCode(200).extract().asString()).isEqualTo(good);
        RestAssured.get(fresh).then().statusCode(503).header("Retry-After", equalTo("300"));
    }

    @Test
    void summariesArePostponedNotLostAndSyncFailuresAreReported() {
        Session.as(cookie).contentType("application/json")
                .body(Map.of("email", "tata@example.pl", "frequencies", List.of("MONTHLY"), "types", List.of("GRADE", "EVENT")))
                .post("/api/subscriptions").then().statusCode(200);
        outage();

        assertThat(sync.syncAll()).allSatisfy(r -> assertThat(r.transientFailure()).isTrue());
        assertThat(summaries.sendDue(Frequency.MONTHLY, Instant.now(), false)).isZero();
        assertThat(mailbox.getMailsSentTo("tata@example.pl")).isEmpty();

        MockLibrusResource.mock().state().setOutage(false);
        summaries.retryPending();

        assertThat(mailbox.getMailsSentTo("tata@example.pl")).hasSize(1);
        Session.as(cookie).get("/api/children/1234567u/grades").then().statusCode(200);
    }

    @Test
    void apiReportsOutageWithRetryAfter() {
        outage();

        Session.as(cookie).get("/api/children/1234567u/grades").then().statusCode(503)
                .header("Retry-After", equalTo("300")).body("error", startsWith("librus_unavailable"));
    }
}
