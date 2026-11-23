package io.github.wlk.jlibrus.server.domain.view;

import io.github.wlk.jlibrus.client.grades.GradeAverages;
import io.github.wlk.jlibrus.client.grades.GradeScale;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade.DescriptiveSystem;
import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.GradeCategory;
import io.github.wlk.jlibrus.client.model.GradeKind;
import io.github.wlk.jlibrus.server.domain.student.Directory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/** Grades grouped by subject with weighted averages, ready for display. */
public final class GradeViews {

    /** One grade with names resolved. {@code weight} is 0 when it does not count. */
    public record GradeView(long id, String value, String category, int weight, String teacher, LocalDateTime addedAt,
                            int semester, GradeKind kind, String comment, String colorHex) {
    }

    /** Subject with its grades per semester and averages. */
    public record SubjectGrades(long subjectId, String subject, Double average, Double averageSemester1,
                                Double averageSemester2, List<GradeView> semester1, List<GradeView> semester2,
                                List<GradeView> summary) {
    }

    /** A descriptive grade with names resolved. */
    public record DescriptiveView(String id, String value, String skill, String teacher, LocalDate date,
                                  List<String> comments, List<String> requirements, DescriptiveSystem system) {
    }

    /** Descriptive grades of one subject, newest first. */
    public record DescriptiveSubject(String subject, List<DescriptiveView> grades) {
    }

    private GradeViews() {
    }

    public static List<DescriptiveSubject> descriptive(Directory directory, List<DescriptiveGrade> grades) {
        Map<String, List<DescriptiveGrade>> bySubject = grades.stream().collect(Collectors.groupingBy(
                g -> directory.subjectName(g.subjectId())));
        return bySubject.entrySet().stream()
                .map(e -> new DescriptiveSubject(e.getKey(), e.getValue().stream()
                        .sorted(Comparator.comparing(GradeViews::day, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(g -> new DescriptiveView(g.id(), g.value(), g.skill(), directory.personName(g.teacherId()),
                                day(g), g.comments(), g.requirements(), g.system()))
                        .toList()))
                .sorted(Comparator.comparing(DescriptiveSubject::subject, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static LocalDate day(DescriptiveGrade grade) {
        return grade.date() != null ? grade.date() : grade.addedAt() == null ? null : grade.addedAt().toLocalDate();
    }

    public static List<SubjectGrades> bySubject(Directory directory, List<Grade> grades, GradeScale scale) {
        Map<Long, List<Grade>> grouped = grades.stream().collect(Collectors.groupingBy(Grade::subjectId));
        return grouped.entrySet().stream()
                .map(e -> subject(directory, e.getKey(), e.getValue(), scale))
                .sorted(Comparator.comparing(SubjectGrades::subject, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static SubjectGrades subject(Directory directory, long subjectId, List<Grade> grades, GradeScale scale) {
        Map<Long, GradeCategory> categories = directory.gradeCategories();
        List<Grade> sorted = grades.stream()
                .sorted(Comparator.comparing(Grade::addedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        List<Grade> regular1 = regular(sorted, 1);
        List<Grade> regular2 = regular(sorted, 2);
        return new SubjectGrades(subjectId, nameOr(directory.subjectName(subjectId), subjectId),
                boxed(GradeAverages.weighted(sorted, categories, scale)),
                boxed(GradeAverages.weighted(regular1, categories, scale)),
                boxed(GradeAverages.weighted(regular2, categories, scale)),
                views(directory, regular1), views(directory, regular2),
                views(directory, sorted.stream().filter(g -> g.kind().isSummary()).toList()));
    }

    private static List<Grade> regular(List<Grade> grades, int semester) {
        return grades.stream().filter(g -> !g.kind().isSummary() && g.semester() == semester).toList();
    }

    private static List<GradeView> views(Directory directory, List<Grade> grades) {
        return grades.stream().map(g -> view(directory, g)).toList();
    }

    static GradeView view(Directory directory, Grade grade) {
        GradeCategory category = directory.gradeCategory(grade.categoryId()).orElse(null);
        String comment = grade.commentIds().stream().map(id -> directory.gradeComments().get(id))
                .filter(Objects::nonNull).map(c -> c.text()).collect(Collectors.joining("; "));
        return new GradeView(grade.id(), grade.value(), category == null ? "" : category.name(), weight(category),
                directory.personName(grade.teacherId()), grade.addedAt(), grade.semester(), grade.kind(),
                comment.isEmpty() ? null : comment, Palette.hex(category == null ? null : category.colorId()));
    }

    private static int weight(GradeCategory category) {
        if (category == null || Boolean.FALSE.equals(category.countsToAverage())) {
            return 0;
        }
        return category.weight() == null ? 1 : category.weight();
    }

    private static Double boxed(OptionalDouble value) {
        return value.isPresent() ? Math.round(value.getAsDouble() * 100) / 100.0 : null;
    }

    private static String nameOr(String name, long id) {
        return name.isEmpty() ? "#" + id : name;
    }
}
