package io.github.wlk.jlibrus.mock;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.wlk.jlibrus.mock.fixtures.SchoolFixtures;
import io.github.wlk.jlibrus.mock.fixtures.StudentCalendar;
import io.github.wlk.jlibrus.mock.fixtures.StudentExtras;
import io.github.wlk.jlibrus.mock.fixtures.StudentRecords;
import io.github.wlk.jlibrus.mock.http.MockExchange;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** {@code /gateway/api/2.0/*}: JSON resources for a valid {@code oauth_token}, Librus-style errors otherwise. */
final class GatewayHandler {

    static final String PREFIX = "/gateway/api/2.0/";
    static final String PARTIAL_GRADES = "Auth/DescriptiveGradingSystem/PartialGrades/Student/";
    private static final String EXPIRED =
            "{\"Status\":\"Error\",\"Code\":\"TokenIsExpired\",\"Message\":\"Access token is expired\"}";

    private record Context(MockAccount account, LocalDate today, Map<String, String> query) {
        StudentRecords records() {
            return new StudentRecords(account, today);
        }

        StudentCalendar calendar() {
            return new StudentCalendar(account, today);
        }

        StudentExtras extras() {
            return new StudentExtras(account, today);
        }
    }

    private static final Map<String, Function<Context, JsonNode>> RESOURCES = Map.ofEntries(
            Map.entry("Me", c -> c.records().me()),
            Map.entry("Classes", c -> c.records().schoolClass()),
            Map.entry("Schools", c -> SchoolFixtures.school()),
            Map.entry("Users", c -> SchoolFixtures.users()),
            Map.entry("Subjects", c -> SchoolFixtures.subjects()),
            Map.entry("Lessons", c -> SchoolFixtures.lessons()),
            Map.entry("Classrooms", c -> SchoolFixtures.classrooms()),
            Map.entry("Grades", c -> c.records().grades()),
            Map.entry("Grades/Categories", c -> SchoolFixtures.gradeCategories()),
            Map.entry("Grades/Comments", c -> c.records().gradeComments()),
            Map.entry("Attendances", c -> c.records().attendances()),
            Map.entry("Attendances/Types", c -> SchoolFixtures.attendanceTypes()),
            Map.entry("BehaviourGrades/Points", c -> c.records().behaviourPoints()),
            Map.entry("Notes", c -> c.records().notes()),
            Map.entry("Notes/Categories", c -> SchoolFixtures.noteCategories()),
            Map.entry("SchoolNotices", c -> c.records().announcements()),
            Map.entry("LuckyNumbers", c -> c.records().luckyNumber()),
            Map.entry("HomeWorks", c -> c.calendar().events()),
            Map.entry("HomeWorks/Categories", c -> SchoolFixtures.eventCategories()),
            Map.entry("HomeWorkAssignments", c -> c.calendar().homework()),
            Map.entry("ParentTeacherConferences", c -> c.calendar().parentMeetings()),
            Map.entry("SchoolFreeDays", c -> c.calendar().schoolFreeDays()),
            Map.entry("TeacherFreeDays", c -> c.calendar().teacherAbsences()),
            Map.entry("Justifications", c -> c.extras().justifications()),
            Map.entry("SchoolTrips", c -> c.extras().schoolTrips()),
            Map.entry("DescriptiveGrades", c -> c.extras().descriptiveGrades()),
            Map.entry("DescriptiveGrades/Skills", c -> StudentExtras.skills()),
            Map.entry("DescriptiveGrades/Comments", c -> StudentExtras.comments()),
            Map.entry("Auth/Subjects", c -> StudentExtras.authSubjects()),
            Map.entry("Timetables", c -> c.calendar().timetable(
                    LocalDate.parse(c.query().getOrDefault("weekStart", c.today().toString())))));

    private final MockState state;

    GatewayHandler(MockState state) {
        this.state = state;
    }

    void handle(MockExchange ex) {
        Optional<MockAccount> account = ex.cookie("oauth_token").flatMap(state::tokenLogin).flatMap(state::account);
        if (account.isEmpty()) {
            ex.json(401, EXPIRED);
            return;
        }
        String resource = ex.path().substring(PREFIX.length());
        if ("POST".equals(ex.method()) && resource.startsWith("SchoolNotices/MarkAsRead/")) {
            ex.json(200, "{\"Status\":\"Ok\"}");
            return;
        }
        if ("POST".equals(ex.method()) && resource.startsWith(PARTIAL_GRADES)) {
            LocalDate today = LocalDate.now(state.clock().withZone(MockLibrus.ZONE));
            ex.json(200, new StudentExtras(account.get(), today).partialGrades().toString());
            return;
        }
        if (resource.equals("Auth/TokenInfo")) {
            ex.json(200, "{\"UserIdentifier\":\"LID-" + account.get().seed() + "\",\"UserType\":5}");
            return;
        }
        if (resource.startsWith("Auth/UserInfo/")) {
            ex.json(200, "{\"IdentifierOfStudentAssignedWithUser\":\"LID-STUDENT-" + account.get().seed() + "\"}");
            return;
        }
        Function<Context, JsonNode> producer = RESOURCES.get(resource);
        if (producer == null) {
            ex.json(404, "{\"Status\":\"Error\",\"Code\":\"Resource not found\"}");
            return;
        }
        LocalDate today = LocalDate.now(state.clock().withZone(MockLibrus.ZONE));
        ex.json(200, producer.apply(new Context(account.get(), today, ex.query())).toString());
    }
}
