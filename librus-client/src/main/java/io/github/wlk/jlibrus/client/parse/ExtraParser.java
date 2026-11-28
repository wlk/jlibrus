package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.model.Justification;
import io.github.wlk.jlibrus.client.model.JustificationStatus;
import io.github.wlk.jlibrus.client.model.JustifiedLesson;
import io.github.wlk.jlibrus.client.model.SchoolTrip;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses the newer gateway resources: absence justifications and school trips (lower-case fields in
 * {@code data}/{@code Data} envelopes) and both descriptive grading systems.
 */
public final class ExtraParser {

    private ExtraParser() {
    }

    /** {@code Justifications}, newest first. */
    public static List<Justification> justifications(JsonNode root) {
        List<Justification> result = new ArrayList<>(Json.list(root, "data", ExtraParser::justification).stream()
                .flatMap(Optional::stream).toList());
        result.sort(Comparator.comparing(Justification::postedAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        return List.copyOf(result);
    }

    private static Optional<Justification> justification(JsonNode j) {
        return Json.longValue(j, "id").map(id -> {
            LocalDate from = Json.date(j, "dateFrom").orElse(null);
            return new Justification(id,
                    JustificationStatus.parse(Json.textOr(j, "justificationStatus", "")),
                    Json.textOr(j, "messageFromParent", ""),
                    Json.dateTime(j, "postDate").orElse(null),
                    from,
                    Json.date(j, "dateTo").orElse(from),
                    Json.list(j, "lessons", l -> new JustifiedLesson(Json.date(l, "date").orElse(null),
                            Json.integer(l, "number").orElse(null))),
                    Json.integer(j, "justifiedAbsences").orElse(0),
                    Json.flag(j, "attachment"),
                    Json.list(j, "notifiedTeachers", t -> Json.textOr(t, "name", "")).stream()
                            .filter(name -> !name.isEmpty()).toList());
        });
    }

    /** {@code SchoolTrips}, soonest first. */
    public static List<SchoolTrip> schoolTrips(JsonNode root) {
        List<SchoolTrip> result = new ArrayList<>(Json.list(root, "Data", ExtraParser::trip).stream()
                .flatMap(Optional::stream).toList());
        result.sort(Comparator.comparing(SchoolTrip::from, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(result);
    }

    private static Optional<SchoolTrip> trip(JsonNode t) {
        return Json.longValue(t, "id").map(id -> {
            LocalDate from = Json.date(t, "termFrom").orElse(null);
            String creator = Json.fullName(t, "creatorName", "creatorLastName");
            String coordinator = Json.text(t, "coordinatorName").orElse(creator.isEmpty() ? null : creator);
            return new SchoolTrip(id, Json.textOr(t, "destination", ""), Json.textOr(t, "route", ""),
                    Json.textOr(t, "locomotion", ""), from, Json.date(t, "termTo").orElse(from), coordinator);
        });
    }

    /** {@code DescriptiveGrades}: the shown value is {@code Map}/{@code RealGradeValue}, never {@code Grade}. */
    public static List<DescriptiveGrade> descriptiveGrades(JsonNode root, Map<Long, String> skills,
                                                           Map<Long, String> comments) {
        return Json.list(root, "Grades", g -> classic(g, skills, comments)).stream().flatMap(Optional::stream).toList();
    }

    private static Optional<DescriptiveGrade> classic(JsonNode g, Map<Long, String> skills, Map<Long, String> comments) {
        return Json.longValue(g, "Id").map(id -> {
            Long skillId = Json.refLong(g, "Skill").orElse(null);
            List<String> texts = Json.refLongs(g.path("Comments")).stream()
                    .map(comments::get).filter(c -> c != null && !c.isBlank()).toList();
            return new DescriptiveGrade(String.valueOf(id), Json.refLong(g, "Subject").orElse(null),
                    Json.text(g, "Map").or(() -> Json.text(g, "RealGradeValue")).orElse(""),
                    skillId == null ? null : skills.get(skillId),
                    Json.refLong(g, "AddedBy").orElse(null),
                    Json.date(g, "Date").orElse(null),
                    Json.dateTime(g, "AddDate").orElse(null),
                    Json.integer(g, "Semester").orElse(null),
                    texts, List.of(), DescriptiveGrade.DescriptiveSystem.CLASSIC);
        });
    }

    /** Partial grades of the newer descriptive system ({@code data} envelope, camelCase fields). */
    public static List<DescriptiveGrade> partialGrades(JsonNode root, Map<String, Long> subjectsByLid) {
        return Json.list(root, "data", g -> partial(g, subjectsByLid)).stream().flatMap(Optional::stream).toList();
    }

    private static Optional<DescriptiveGrade> partial(JsonNode g, Map<String, Long> subjectsByLid) {
        return Json.text(g, "gradeId").map(gradeId -> new DescriptiveGrade("p" + gradeId,
                partialSubject(g.path("subjectId"), subjectsByLid),
                scaleValue(g.path("scaleValue")),
                Json.text(g.path("area"), "name").orElse(null),
                null,
                Json.date(g, "date").orElse(null),
                Json.dateTime(g, "addDate").orElse(null),
                Json.integer(g, "semester").orElse(null),
                partialComments(g),
                Json.list(g, "implementedRequirements", r -> Json.textOr(r, "name", "")).stream()
                        .filter(s -> !s.isEmpty()).toList(),
                DescriptiveGrade.DescriptiveSystem.PARTIAL));
    }

    private static Long partialSubject(JsonNode subject, Map<String, Long> subjectsByLid) {
        if (subject.isTextual() && subjectsByLid.containsKey(subject.asText())) {
            return subjectsByLid.get(subject.asText());
        }
        return subject.isNumber() || subject.isTextual() ? Json.parseLong(subject.asText()).orElse(null) : null;
    }

    private static String scaleValue(JsonNode scale) {
        JsonNode value = scale.path("value");
        return value.isTextual() || value.isNumber() ? value.asText() : "";
    }

    private static List<String> partialComments(JsonNode g) {
        List<String> texts = new ArrayList<>();
        Json.text(g, "content").ifPresent(texts::add);
        JsonNode comments = g.path("comments");
        if (comments.isTextual() || comments.isObject()) {
            commentText(comments).ifPresent(texts::add);
        } else {
            comments.forEach(c -> commentText(c).ifPresent(texts::add));
        }
        return List.copyOf(texts);
    }

    private static Optional<String> commentText(JsonNode comment) {
        if (comment.isTextual()) {
            return Optional.of(comment.asText().strip()).filter(s -> !s.isEmpty());
        }
        return Json.text(comment, "content").or(() -> Json.text(comment, "text"));
    }

    /** {@code Auth/Subjects}: subject identifier of the newer services to the ordinary subject id. */
    public static Map<String, Long> subjectsByLid(JsonNode root) {
        Map<String, Long> result = new HashMap<>();
        root.path("data").forEach(s -> Json.text(s, "identifier").ifPresent(lid ->
                Json.longValue(s, "numericIdentifier").ifPresent(id -> result.put(lid, id))));
        return Map.copyOf(result);
    }

    /** Id to text of a list such as {@code {"Skills":[{"Id","Name"}]}} or {@code {"Comments":[{"Id","Text"}]}}. */
    public static Map<Long, String> namesById(JsonNode root, String rootKey, String field) {
        Map<Long, String> result = new HashMap<>();
        root.path(rootKey).forEach(item -> Json.longValue(item, "Id").ifPresent(id ->
                Json.text(item, field).ifPresent(text -> result.put(id, text))));
        return Map.copyOf(result);
    }

    /** The child's identifier for the newer services ({@code Auth/UserInfo}). */
    public static Optional<String> studentIdentifier(JsonNode userInfo) {
        JsonNode info = userInfo.path("UserInfo").isObject() ? userInfo.path("UserInfo") : userInfo;
        return Json.text(info, "IdentifierOfStudentAssignedWithUser");
    }
}
