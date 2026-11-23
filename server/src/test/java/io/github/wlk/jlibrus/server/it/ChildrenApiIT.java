package io.github.wlk.jlibrus.server.it;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.not;

/** The read API end-to-end: server -> librus-client -> mock Librus. */
@QuarkusTest
@QuarkusTestResource(MockLibrusResource.class)
class ChildrenApiIT {

    private static final String OLA = "/api/children/1234567u";
    private String cookie;

    @BeforeEach
    void login() {
        cookie = Session.login();
    }

    @Test
    void listsConfiguredChildrenWithNamesAndLabels() {
        Session.as(cookie).get("/api/children").then().statusCode(200)
                .body("name", containsInAnyOrder("Ola Kowalska", "Janek"))
                .body("className", hasItem("4b"));
    }

    @Test
    void unknownChildIs404() {
        Session.as(cookie).get("/api/children/nobody/grades").then().statusCode(404);
    }

    @Test
    void dashboardShowsLessonsAgendaAndLuckyNumber() {
        Session.as(cookie).get(OLA + "/dashboard").then().statusCode(200)
                .body("child.name", equalTo("Ola Kowalska"))
                .body("upcoming", not(empty()))
                .body("unreadMessages", notNullValue());
    }

    @Test
    void gradesAttendanceTimetable() {
        Session.as(cookie).get(OLA + "/grades").then().statusCode(200).body("subject", hasItem("Matematyka"));
        Session.as(cookie).get(OLA + "/attendance").then().statusCode(200).body("stats.lessons", greaterThan(0));
        Session.as(cookie).get(OLA + "/timetable").then().statusCode(200).body("subject", hasItem("Matematyka"));
        Session.as(cookie).get(OLA + "/timetable?date=nope").then().statusCode(400).body("error", equalTo("invalid_request"));
    }

    @Test
    void agendaWithRangeValidation() {
        LocalDate today = LocalDate.now();
        Session.as(cookie).get(OLA + "/agenda?from=" + today + "&to=" + today.plusDays(14)).then().statusCode(200)
                .body("type", hasItem("EVENT"));
        Session.as(cookie).get(OLA + "/agenda?from=" + today + "&to=" + today.minusDays(1)).then().statusCode(400);
        Session.as(cookie).get(OLA + "/agenda?from=" + today).then().statusCode(400);
    }

    @Test
    void descriptiveGradesJustificationsAndTrips() {
        Session.as(cookie).get(OLA + "/descriptive-grades").then().statusCode(200)
                .body("subject", hasItem("Muzyka")).body("subject", hasItem("Język polski"));
        Session.as(cookie).get("/api/children/7654321u/descriptive-grades").then().statusCode(200).body("$", empty());
        Session.as(cookie).get(OLA + "/attendance").then().statusCode(200)
                .body("justifications.status", hasItem("ACCEPTED")).body("justifications.status", hasItem("PENDING"));
        Session.as(cookie).get(OLA + "/trips").then().statusCode(200).body("destination", hasItem("Teatr Lalek"));
        LocalDate today = LocalDate.now();
        Session.as(cookie).get(OLA + "/agenda?from=" + today + "&to=" + today.plusDays(60)).then().statusCode(200)
                .body("type", hasItem("SCHOOL_TRIP"));
    }

    @Test
    void homeworkBehaviourAnnouncements() {
        Session.as(cookie).get(OLA + "/homework").then().statusCode(200).body("topic", hasItem("Karta pracy"));
        Session.as(cookie).get(OLA + "/behaviour").then().statusCode(200).body("notes", not(empty()));
        Session.as(cookie).get(OLA + "/announcements").then().statusCode(200).body("title", hasItem("Dzień otwarty szkoły"));
        Session.as(cookie).post(OLA + "/announcements/nb-open-day/read").then().statusCode(204);
        Session.as(cookie).post(OLA + "/announcements/..%2Fx/read").then().statusCode(400);
    }

    @Test
    void messagesReadRecipientsAndSend() {
        Session.as(cookie).get(OLA + "/messages").then().statusCode(200).body("total", equalTo(3));
        Session.as(cookie).get(OLA + "/messages/unread").then().statusCode(200).body("counts.inbox", notNullValue());
        Session.as(cookie).get(OLA + "/messages/inbox/7003").then().statusCode(200).body("senderAccountId", equalTo("1001"));
        Session.as(cookie).get(OLA + "/messages/inbox/7003/attachments/55").then().statusCode(200)
                .body("url", notNullValue());
        Session.as(cookie).get(OLA + "/messages/recipients").then().statusCode(200).body("accountId", hasItem("1001"));
        Session.as(cookie).contentType("application/json")
                .body(Map.of("recipientIds", List.of("1001"), "topic", "Pytanie", "text", "Czy jutro jest sprawdzian?"))
                .post(OLA + "/messages").then().statusCode(200).body("messageId", notNullValue());
        Session.as(cookie).contentType("application/json")
                .body(Map.of("recipientIds", List.of(), "topic", "x", "text", "y"))
                .post(OLA + "/messages").then().statusCode(400);
        Session.as(cookie).get(OLA + "/messages?mailbox=outbox").then().statusCode(200).body("total", greaterThan(0));
        Session.as(cookie).get(OLA + "/messages?mailbox=spam").then().statusCode(400);
    }

    @Test
    void refreshFillsTheTimeline() {
        Session.as(cookie).post("/api/refresh").then().statusCode(200).body("error", hasItem((Object) null));

        Session.as(cookie).get(OLA + "/timeline?limit=500").then().statusCode(200)
                .body("type", hasItem("GRADE")).body("type", hasItem("MESSAGE"))
                .body("type", hasItem("DESCRIPTIVE_GRADE")).body("type", hasItem("JUSTIFICATION"))
                .body("type", hasItem("SCHOOL_TRIP"));
    }

    @Test
    void metadata() {
        Session.as(cookie).get("/api/entry-types").then().statusCode(200).body("id", hasItem("LESSON"))
                .body("labelPl", hasItem("Oceny"));
        Session.as(cookie).get("/api/schedule").then().statusCode(200).body("timezone", equalTo("Europe/Warsaw"));
    }
}
