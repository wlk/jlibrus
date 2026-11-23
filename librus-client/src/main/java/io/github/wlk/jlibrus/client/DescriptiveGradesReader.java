package io.github.wlk.jlibrus.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.parse.ExtraParser;
import io.github.wlk.jlibrus.client.session.Gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Reads both descriptive grading systems. The classic one needs the school-wide skill list (large), so it is
 * fetched only when there are grades; the newer one is a POST keyed by the child's identifier, looked up once.
 */
final class DescriptiveGradesReader {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]{1,100}");

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
        JsonNode grades = optionalNode("DescriptiveGrades");
        if (grades.path("Grades").isEmpty()) {
            return List.of();
        }
        Map<Long, String> skills = ExtraParser.namesById(optionalNode("DescriptiveGrades/Skills"), "Skills", "Name");
        Map<Long, String> comments = ExtraParser.namesById(optionalNode("DescriptiveGrades/Comments"), "Comments", "Text");
        return ExtraParser.descriptiveGrades(grades, skills, comments);
    }

    private List<DescriptiveGrade> partialGrades() {
        Optional<String> student = studentIdentifier();
        if (student.isEmpty()) {
            return List.of();
        }
        try {
            JsonNode root = gateway.postForJson("Auth/DescriptiveGradingSystem/PartialGrades/Student/" + student.get());
            if (root.path("data").isEmpty()) {
                return List.of();
            }
            return ExtraParser.partialGrades(root, ExtraParser.subjectsByLid(optionalNode("Auth/Subjects")));
        } catch (LibrusException.ModuleUnavailable e) {
            return List.of();
        }
    }

    /** The child's identifier used by the newer services; looked up once, empty when Librus has none. */
    private Optional<String> studentIdentifier() {
        Optional<String> cached = studentIdentifier.get();
        if (cached != null) {
            return cached;
        }
        String user = optionalNode("Auth/TokenInfo").path("UserIdentifier").asText("");
        Optional<String> found = Optional.empty();
        if (IDENTIFIER.matcher(user).matches()) {
            found = ExtraParser.studentIdentifier(optionalNode("Auth/UserInfo/" + user)).or(() -> Optional.of(user))
                    .filter(id -> IDENTIFIER.matcher(id).matches());
        }
        studentIdentifier.set(found);
        return found;
    }

    private JsonNode optionalNode(String resource) {
        try {
            return gateway.get(resource);
        } catch (LibrusException.ModuleUnavailable e) {
            return MissingNode.getInstance();
        }
    }
}
