package io.github.wlk.jlibrus.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the mock's HTTP surface the way the client and a browser use it. */
class MockLibrusTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MockAccount NO_MESSAGES = new MockAccount("555u", "pw", "Ala", "Nowak", 1, "a", false);
    private MockLibrus mock;
    private Browser browser;

    @BeforeEach
    void start() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-28T09:00:00Z"), ZoneOffset.UTC);
        mock = MockLibrus.start("127.0.0.1", 0, null, clock, List.of(MockAccount.ola(), NO_MESSAGES));
        browser = new Browser(mock.baseUri());
    }

    @AfterEach
    void stop() {
        mock.close();
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception {
        return MAPPER.readTree(response.body());
    }

    @Test
    void loginFlowSetsSessionCookies() {
        HttpResponse<String> last = browser.login("1234567u", "demo");

        assertThat(last.statusCode()).isEqualTo(200);
        assertThat(browser.cookie("oauth_token")).isNotBlank();
        assertThat(browser.cookie("DZIENNIKSID")).isNotBlank();
        assertThat(browser.cookie("DeviceCookie")).startsWith("device-");
        assertThat(mock.state().passwordLogins()).isEqualTo(1);
    }

    @Test
    void wrongPasswordCaptchaAndMissingSessionAreRejected() throws Exception {
        browser.get("/OAuth/Authorization?client_id=46");
        String form = "application/x-www-form-urlencoded";
        assertThat(json(browser.post("/OAuth/Authorization", form, "action=login&login=1234567u&pass=bad"))
                .path("status").asText()).isEqualTo("error");
        assertThat(browser.post("/OAuth/Authorization", form, "action=login&login=captcha&pass=x").body())
                .contains("g-recaptcha");
        assertThat(browser.get("/OAuth/Authorization/2FA").statusCode()).isEqualTo(403);
        assertThat(browser.get("/OAuth/Authorization/Grant").statusCode()).isEqualTo(403);
        assertThat(browser.get("/loguj/portalRodzina?code=nope").statusCode()).isEqualTo(400);
        browser.forget(AuthHandlers.API_SESSION);
        assertThat(json(browser.post("/OAuth/Authorization", form, "action=login&login=1234567u&pass=demo"))
                .path("status").asText()).isEqualTo("error");
    }

    @ParameterizedTest
    @CsvSource({"Me, Me", "Classes, Class", "Schools, School", "Users, Users", "Subjects, Subjects", "Lessons, Lessons",
            "Classrooms, Classrooms", "Grades, Grades", "Grades/Categories, Categories", "Grades/Comments, Comments",
            "Attendances, Attendances", "Attendances/Types, Types", "BehaviourGrades/Points, Grades", "Notes, Notes",
            "Notes/Categories, Categories", "SchoolNotices, SchoolNotices", "LuckyNumbers, LuckyNumber",
            "HomeWorks, HomeWorks", "HomeWorks/Categories, Categories", "HomeWorkAssignments, HomeWorkAssignments",
            "ParentTeacherConferences, ParentTeacherConferences", "SchoolFreeDays, SchoolFreeDays",
            "TeacherFreeDays, TeacherFreeDays", "Timetables?weekStart=2026-11-23, Timetable", "Timetables, Timetable",
            "Justifications, data", "SchoolTrips, Data", "DescriptiveGrades, Grades", "DescriptiveGrades/Skills, Skills",
            "DescriptiveGrades/Comments, Comments", "Auth/Subjects, data"})
    void gatewayServesEveryResource(String resource, String rootKey) throws Exception {
        browser.login("1234567u", "demo");

        HttpResponse<String> response = browser.get("/gateway/api/2.0/" + resource);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).has(rootKey)).isTrue();
    }

    @Test
    void gatewayAuthAndErrors() throws Exception {
        assertThat(browser.get("/gateway/api/2.0/Me").statusCode()).isEqualTo(401);
        browser.login("1234567u", "demo");
        assertThat(json(browser.get("/gateway/api/2.0/Auth/TokenInfo")).has("UserIdentifier")).isTrue();
        assertThat(browser.get("/gateway/api/2.0/Auth/UserInfo/LID-1").body()).contains("IdentifierOfStudentAssignedWithUser");
        assertThat(browser.get("/gateway/api/2.0/Nope").statusCode()).isEqualTo(404);
        assertThat(browser.post("/gateway/api/2.0/SchoolNotices/MarkAsRead/x", "application/json", "").statusCode())
                .isEqualTo(200);
        assertThat(json(browser.post("/gateway/api/2.0/Auth/DescriptiveGradingSystem/PartialGrades/Student/LID-1",
                "application/json", "{}")).path("data")).hasSize(1);
        mock.state().expireTokens();
        assertThat(browser.get("/gateway/api/2.0/Me").statusCode()).isEqualTo(401);
        assertThat(browser.get("/refreshToken").statusCode()).isEqualTo(200);
        assertThat(browser.get("/gateway/api/2.0/Me").statusCode()).isEqualTo(200);
        mock.state().expireSynergiaSessions();
        assertThat(browser.get("/refreshToken").statusCode()).isEqualTo(401);
        assertThat(browser.get("/somewhere").statusCode()).isEqualTo(404);
        assertThat(browser.get("/GetFile/1").statusCode()).isEqualTo(200);
    }

    @Test
    void messagesModuleReadAndSend() throws Exception {
        browser.login("1234567u", "demo");
        assertThat(browser.get("/api/me").statusCode()).isEqualTo(401);
        openMessages();

        assertThat(json(browser.get("/api/inbox/messages?page=1&limit=2")).path("total").asInt()).isEqualTo(3);
        assertThat(json(browser.get("/api/inbox/unreadMessagesCount")).path("data").path("inbox").asInt()).isEqualTo(3);
        assertThat(json(browser.get("/api/inbox/messages/7003")).path("data").path("attachments")).hasSize(1);
        assertThat(json(browser.get("/api/inbox/unreadMessagesCount")).path("data").path("inbox").asInt()).isEqualTo(2);
        assertThat(json(browser.get("/api/inbox/messages?page=1&limit=10")).path("data").get(0).path("readDate").isNull())
                .isFalse();
        assertThat(browser.get("/api/inbox/messages/1").statusCode()).isEqualTo(404);
        assertThat(json(browser.get("/api/receivers/types")).path("data").path("list")).hasSize(4);
        for (String group : List.of("tutors", "teachers", "secretary", "other")) {
            assertThat(browser.get("/api/receivers/groups/school-employees?receiverType=" + group).statusCode()).isEqualTo(200);
        }
        assertThat(json(browser.get("/api/attachments/55/messages/7003")).path("data").path("downloadLink").asText())
                .endsWith("/GetFile/55");
        assertThat(json(browser.get("/api/me")).has("accountId")).isTrue();
        assertThat(browser.get("/api/unknown").statusCode()).isEqualTo(404);

        String body = "{\"receivers\":{\"schoolReceivers\":[{\"accountId\":\"1001\"}]},\"topic\":\"" + b64("Temat")
                + "\",\"content\":\"" + b64("Treść") + "\"}";
        JsonNode sent = json(browser.post("/api/messages", "application/json", body));
        assertThat(sent.path("data").path("messageId").asLong()).isPositive();
        String reply = body.replace("}", ",\"originalMessageId\":7003}");
        assertThat(json(browser.post("/api/messages/reply", "application/json", reply)).path("data").has("messageId")).isFalse();
        assertThat(browser.post("/api/messages", "application/json", "{").statusCode()).isEqualTo(400);
        assertThat(browser.post("/api/messages", "application/json", "{\"topic\":\"!!\"}").statusCode()).isEqualTo(400);
        assertThat(json(browser.get("/api/outbox/messages")).path("total").asInt()).isEqualTo(2);
        long id = sent.path("data").path("messageId").asLong();
        assertThat(json(browser.get("/api/outbox/messages/" + id)).path("data").path("receivers")).hasSize(1);
        assertThat(browser.get("/api/outbox/messages/1").statusCode()).isEqualTo(404);
        assertThat(mock.state().sentMessages().getFirst().text()).isEqualTo("Treść");
    }

    @Test
    void messagesBootstrapRequiresSynergiaAndEnabledModule() {
        assertThat(browser.get("/wiadomosci3").body()).contains("Brak dostępu");
        browser.login("555u", "pw");
        assertThat(browser.get("/wiadomosci3").body()).contains("Brak dostępu");
        assertThat(browser.get("/MultiDomainLogon?login=ghost").statusCode()).isEqualTo(400);
    }

    private void openMessages() {
        HttpResponse<String> current = browser.get("/wiadomosci3");
        while (current.statusCode() == 302) {
            current = browser.get(current.headers().firstValue("Location").orElseThrow());
        }
        assertThat(current.body()).contains("Wiadomości");
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void outageAnswersMaintenanceEverywhere() {
        mock.state().setOutage(true);

        assertThat(browser.get("/loguj/portalRodzina").statusCode()).isEqualTo(503);
        assertThat(browser.get("/gateway/api/2.0/Me").body()).contains("Przerwa techniczna");

        mock.state().setOutage(false);
        assertThat(browser.get("/loguj/portalRodzina").statusCode()).isEqualTo(302);
    }
}
