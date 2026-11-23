package io.github.wlk.jlibrus.server.it;

import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import io.github.wlk.jlibrus.server.service.SummaryService;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
@QuarkusTestResource(MockLibrusResource.class)
class FeedsAndSummariesIT {

    @Inject
    MockMailbox mailbox;

    @Inject
    SummaryService summaries;

    private String cookie;

    @BeforeEach
    void setUp() {
        cookie = Session.login();
        mailbox.clear();
    }

    @Test
    void defaultFeedIsPublicReadOnlyIcal() {
        String path = Session.as(cookie).get("/api/feeds").then().statusCode(200)
                .extract().path("[0].path");

        RestAssured.get(path).then().statusCode(200)
                .contentType(startsWith("text/calendar"))
                .body(startsWith("BEGIN:VCALENDAR"))
                .body(containsString("BEGIN:VEVENT"))
                .body(containsString("Ola Kowalska: "));
    }

    @Test
    void feedLifecycleWithTokenReset() {
        String id = Session.as(cookie).contentType("application/json")
                .body(Map.of("name", "Tylko Ola", "childIds", List.of("1234567u"), "types", List.of("EVENT", "LESSON", "GRADE")))
                .post("/api/feeds").then().statusCode(200)
                .body("types", not(org.hamcrest.Matchers.hasItem("GRADE")))
                .extract().path("id");
        String oldPath = Session.as(cookie).get("/api/feeds").then().extract().path("find { it.id == '" + id + "' }.path");
        RestAssured.get(oldPath).then().statusCode(200).body(not(containsString("Ola Kowalska: ")));

        String newPath = Session.as(cookie).post("/api/feeds/" + id + "/reset-token").then().statusCode(200).extract().path("path");

        RestAssured.get(oldPath).then().statusCode(404);
        RestAssured.get(newPath).then().statusCode(200);
        Session.as(cookie).contentType("application/json").body(Map.of("name", "x", "types", List.of("GRADE")))
                .put("/api/feeds/" + id).then().statusCode(400);
        Session.as(cookie).delete("/api/feeds/" + id).then().statusCode(204);
        Session.as(cookie).delete("/api/feeds/" + id).then().statusCode(404);
    }

    @Test
    void subscriptionValidation() {
        Session.as(cookie).contentType("application/json")
                .body(Map.of("email", "not-an-email", "frequencies", List.of("DAILY"), "types", List.of("GRADE")))
                .post("/api/subscriptions").then().statusCode(400);
        Session.as(cookie).contentType("application/json")
                .body(Map.of("email", "a@example.pl", "frequencies", List.of(), "types", List.of("GRADE")))
                .post("/api/subscriptions").then().statusCode(400);
        Session.as(cookie).contentType("application/json")
                .body(Map.of("email", "a@example.pl", "frequencies", List.of("DAILY"), "types", List.of("NOPE")))
                .post("/api/subscriptions").then().statusCode(400);
    }

    @Test
    void summariesAreMailedOncePerPeriodAndOnDemand() {
        Session.as(cookie).post("/api/refresh").then().statusCode(200);
        String id = Session.as(cookie).contentType("application/json")
                .body(Map.of("email", "Mama@Example.PL", "frequencies", List.of("daily", "weekly"),
                        "types", List.of("GRADE", "EVENT", "HOMEWORK", "LESSON", "TIMETABLE_CHANGE"), "childIds", List.of()))
                .post("/api/subscriptions").then().statusCode(200)
                .body("email.value", equalTo("mama@example.pl")).extract().path("id");

        Session.as(cookie).get("/api/subscriptions/" + id + "/preview?frequency=weekly").then().statusCode(200)
                .body("subject", containsString("Tygodniowe podsumowanie")).body("html", containsString("Ola Kowalska"));
        Session.as(cookie).post("/api/subscriptions/" + id + "/send?frequency=daily").then().statusCode(200);
        assertThat(mailbox.getMailsSentTo("mama@example.pl")).hasSize(1);

        summaries.sendDue(Frequency.WEEKLY);
        summaries.sendDue(Frequency.WEEKLY);
        assertThat(mailbox.getMailsSentTo("mama@example.pl")).hasSize(2);
        assertThat(mailbox.getMailsSentTo("mama@example.pl").get(1).getText()).contains("== Janek ==");

        Session.as(cookie).contentType("application/json")
                .body(Map.of("email", "mama@example.pl", "frequencies", List.of("monthly"), "types", List.of("GRADE"),
                        "enabled", false))
                .put("/api/subscriptions/" + id).then().statusCode(200).body("enabled", equalTo(false));
        assertThat(summaries.sendDue(Frequency.MONTHLY)).isZero();
        Session.as(cookie).delete("/api/subscriptions/" + id).then().statusCode(204);
        Session.as(cookie).get("/api/subscriptions/" + id + "/preview").then().statusCode(404);
    }
}
