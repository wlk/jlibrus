package io.github.wlk.jlibrus.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.parse.ExtraParser;
import io.github.wlk.jlibrus.client.parse.Json;
import io.github.wlk.jlibrus.client.session.Gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads both descriptive grading systems. The classic one needs the school-wide skill list (large), so it is
 * fetched only when there are grades; the newer one is a POST keyed by the child's identifier, looked up once.
 */
final class DescriptiveGradesReader {

    private final Gateway gateway;
    private final AtomicReference<Optional<String>> studentIdentifier = new AtomicReference<>();

    DescriptiveGradesReader(Gateway gateway) {
        this.gateway = gateway;
    }

    List<DescriptiveGrade> read() {
        List<DescriptiveGrade> result = new ArrayList<>(classicDescriptiveGrades());
        result.addAll(partialGrades());
        return List.copyOf(result);
    }

    private List<DescriptiveGrade> classicDescriptiveGrades() {
        JsonNode grades = gateway.getOrMissing("DescriptiveGrades");
        if (grades.path("Grades").isEmpty()) {
            return List.of();
        }
        Map<Long, String> skills =
                ExtraParser.namesById(gateway.getOrMissing("DescriptiveGrades/Skills"), "Skills", "Name");
        Map<Long, String> comments =
                ExtraParser.namesById(gateway.getOrMissing("DescriptiveGrades/Comments"), "Comments", "Text");
        return ExtraParser.descriptiveGrades(grades, skills, comments);
    }

    private List<DescriptiveGrade> partialGrades() {
        Optional<String> student = studentIdentifier();
        if (student.isEmpty()) {
            return List.of();
        }
        JsonNode root = Json.orMissing(() ->
                gateway.postForJson("Auth/DescriptiveGradingSystem/PartialGrades/Student/" + student.get()));
        if (root.path("data").isEmpty()) {
            return List.of();
        }
        return ExtraParser.partialGrades(root, ExtraParser.subjectsByLid(gateway.getOrMissing("Auth/Subjects")));
    }

    /** The child's identifier used by the newer services; looked up once, empty when Librus has none. */
    private Optional<String> studentIdentifier() {
        Optional<String> cached = studentIdentifier.get();
        if (cached != null) {
            return cached;
        }
        String user = gateway.getOrMissing("Auth/TokenInfo").path("UserIdentifier").asText("");
        Optional<String> found = Optional.empty();
        if (Identifiers.isSafeId(user)) {
            found = ExtraParser.studentIdentifier(gateway.getOrMissing("Auth/UserInfo/" + user)).or(() -> Optional.of(user))
                    .filter(Identifiers::isSafeId);
        }
        studentIdentifier.set(found);
        return found;
    }
}
