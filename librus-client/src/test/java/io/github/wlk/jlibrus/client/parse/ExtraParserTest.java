package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.model.Justification;
import io.github.wlk.jlibrus.client.model.JustificationStatus;
import io.github.wlk.jlibrus.client.model.JustifiedLesson;
import io.github.wlk.jlibrus.client.model.SchoolTrip;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Justifications, school trips and both descriptive grading systems. */
class ExtraParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    @Test
    void justificationsNewestFirstWithLessonsAndTeachers() throws Exception {
        List<Justification> items = ExtraParser.justifications(json("""
                {"status":"OK","message":"Pobrano","data":[
                 {"id":3,"messageFromParent":"Proszę o usprawiedliwienie.","postDate":"2026-09-06 22:17:53",
                  "justificationStatus":"accept","lessons":[{"number":8,"date":"2026-09-04"}],"attachment":false,
                  "dateFrom":"2026-09-04","dateTo":"2026-09-04","justifiedAbsences":1,"notifiedTeachers":[{"name":"Anna Nowak"}]},
                 {"id":5,"messageFromParent":"Choroba.","postDate":"2026-09-15 09:23:45","justificationStatus":"new",
                  "lessons":[],"attachment":true,"dateFrom":"2026-09-14","dateTo":"2026-09-16","justifiedAbsences":0,
                  "notifiedTeachers":[]},
                 {"id":7,"messageFromParent":"x","postDate":"2026-09-20 08:00:00","justificationStatus":"rejected",
                  "dateFrom":"2026-09-19"},
                 {"messageFromParent":"no id"}]}
                """));

        assertThat(items).extracting(Justification::id).containsExactly(7L, 5L, 3L);
        assertThat(items).extracting(Justification::status)
                .containsExactly(JustificationStatus.REJECTED, JustificationStatus.PENDING, JustificationStatus.ACCEPTED);
        Justification accepted = items.get(2);
        assertThat(accepted.lessons()).containsExactly(new JustifiedLesson(LocalDate.of(2026, 9, 4), 8));
        assertThat(accepted.notifiedTeachers()).containsExactly("Anna Nowak");
        assertThat(accepted.postedAt()).isEqualTo(LocalDateTime.of(2026, 9, 6, 22, 17, 53));
        assertThat(accepted.justifiedAbsences()).isEqualTo(1);
        assertThat(items.get(1).hasAttachment()).isTrue();
        assertThat(items.getFirst().to()).isEqualTo(LocalDate.of(2026, 9, 19));
    }

    @Test
    void justificationsWithoutDataListAreEmpty() throws Exception {
        assertThat(ExtraParser.justifications(json("{\"status\":\"OK\"}"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"accept, ACCEPTED", "Accepted, ACCEPTED", "rejected, REJECTED", "refused, REJECTED", "denied, REJECTED",
            "declined, REJECTED", "new, PENDING", "'', PENDING"})
    void justificationStatus(String raw, JustificationStatus expected) {
        assertThat(JustificationStatus.parse(raw)).isEqualTo(expected);
    }

    @Test
    void schoolTripsSoonestFirstWithCoordinatorFallback() throws Exception {
        List<SchoolTrip> trips = ExtraParser.schoolTrips(json("""
                {"Data":[
                 {"id":2,"destination":"Muzeum","route":"Szkoła - Muzeum","locomotion":"autokar","termFrom":"2026-10-20",
                  "termTo":"2026-10-20","coordinatorName":"Nowak Anna"},
                 {"id":1,"destination":"Kino","termFrom":"2026-09-23","creatorName":"Jan","creatorLastName":"Kowal"},
                 {"destination":"no id"}]}
                """));

        assertThat(trips).extracting(SchoolTrip::id).containsExactly(1L, 2L);
        assertThat(trips.getFirst().coordinator()).isEqualTo("Jan Kowal");
        assertThat(trips.getFirst().to()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(trips.get(1).transport()).isEqualTo("autokar");
        assertThat(trips.get(1).coordinator()).isEqualTo("Nowak Anna");
    }

    @Test
    void classicDescriptiveGradesUseMapValueAndResolveSkillsAndComments() throws Exception {
        Map<Long, String> skills = ExtraParser.namesById(json("{\"Skills\":[{\"Id\":501,\"Name\":\"Ekspresja muzyczna. Śpiew\"}]}"),
                "Skills", "Name");
        Map<Long, String> comments = ExtraParser.namesById(json("{\"Comments\":[{\"Id\":44,\"Text\":\"Pięknie śpiewa\"}]}"),
                "Comments", "Text");

        List<DescriptiveGrade> grades = ExtraParser.descriptiveGrades(json("""
                {"Grades":[
                 {"Id":1,"Subject":{"Id":9},"Skill":{"Id":501},"AddedBy":{"Id":7},"Grade":3,"Map":"6","RealGradeValue":"6",
                  "Date":"2026-09-30","AddDate":"2026-09-30 13:37:00","Semester":1,"Comments":[{"Id":44}]},
                 {"Id":2,"Skill":{"Id":999},"Grade":4,"RealGradeValue":"5"},
                 {"Id":3,"Grade":3}]}
                """), skills, comments);

        assertThat(grades).extracting(DescriptiveGrade::value).containsExactly("6", "5", "");
        DescriptiveGrade first = grades.getFirst();
        assertThat(first.id()).isEqualTo("1");
        assertThat(first.skill()).isEqualTo("Ekspresja muzyczna. Śpiew");
        assertThat(first.comments()).containsExactly("Pięknie śpiewa");
        assertThat(first.subjectId()).isEqualTo(9L);
        assertThat(first.teacherId()).isEqualTo(7L);
        assertThat(first.semester()).isEqualTo(1);
        assertThat(first.system()).isEqualTo(DescriptiveGrade.DescriptiveSystem.CLASSIC);
        assertThat(grades.get(1).skill()).isNull();
    }

    @Test
    void partialGradesMapSubjectLidsAndCollectTexts() throws Exception {
        Map<String, Long> subjects = ExtraParser.subjectsByLid(json(
                "{\"data\":[{\"identifier\":\"LID-S-1\",\"numericIdentifier\":9},{\"identifier\":\"x\"}]}"));

        List<DescriptiveGrade> grades = ExtraParser.partialGrades(json("""
                {"data":[
                 {"gradeId":11,"subjectId":"LID-S-1","area":{"name":"Czytanie"},"scaleValue":{"value":"B"},
                  "content":"Czyta płynnie","comments":[{"content":"Brawo"},"Tak dalej", 5],
                  "implementedRequirements":[{"name":"Czyta teksty"}],"date":"2026-11-02","addDate":"2026-11-02 10:00:00","semester":1},
                 {"gradeId":12,"subjectId":10,"comments":"Jedno zdanie","scaleValue":{"value":true}},
                 {"subjectId":1}],
                 "pagination":{"limit":50,"page":1,"total":2}}
                """), subjects);

        assertThat(grades).hasSize(2);
        DescriptiveGrade first = grades.getFirst();
        assertThat(first.id()).isEqualTo("p11");
        assertThat(first.subjectId()).isEqualTo(9L);
        assertThat(first.skill()).isEqualTo("Czytanie");
        assertThat(first.value()).isEqualTo("B");
        assertThat(first.comments()).containsExactly("Czyta płynnie", "Brawo", "Tak dalej");
        assertThat(first.requirements()).containsExactly("Czyta teksty");
        assertThat(first.system()).isEqualTo(DescriptiveGrade.DescriptiveSystem.PARTIAL);
        assertThat(first.teacherId()).isNull();
        DescriptiveGrade second = grades.get(1);
        assertThat(second.subjectId()).isEqualTo(10L);
        assertThat(second.value()).isEmpty();
        assertThat(second.comments()).containsExactly("Jedno zdanie");
    }

    @Test
    void studentIdentifierFromUserInfo() throws Exception {
        assertThat(ExtraParser.studentIdentifier(json("{\"IdentifierOfStudentAssignedWithUser\":\"LID-1\"}"))).contains("LID-1");
        assertThat(ExtraParser.studentIdentifier(json("{\"UserInfo\":{\"IdentifierOfStudentAssignedWithUser\":\"LID-2\"}}")))
                .contains("LID-2");
        assertThat(ExtraParser.studentIdentifier(json("{}"))).isEmpty();
    }
}
