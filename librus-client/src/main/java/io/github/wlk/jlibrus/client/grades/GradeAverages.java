package io.github.wlk.jlibrus.client.grades;

import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.GradeCategory;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Weighted averages the way Librus shows them: only regular (non-summary) numeric grades, weighted by their
 * category. A category marked as not counting has weight 0; one without the flag counts with its weight or 1.
 */
public final class GradeAverages {

    private GradeAverages() {
    }

    public static OptionalDouble weighted(List<Grade> grades, Map<Long, GradeCategory> categories, GradeScale scale) {
        double sum = 0;
        double weights = 0;
        for (Grade grade : grades) {
            OptionalDouble value = scale.numeric(grade.value());
            int weight = weight(grade, categories);
            if (grade.kind().isSummary() || value.isEmpty() || weight == 0) {
                continue;
            }
            sum += value.getAsDouble() * weight;
            weights += weight;
        }
        return weights == 0 ? OptionalDouble.empty() : OptionalDouble.of(sum / weights);
    }

    /** Average per subject id; subjects without countable grades are left out. */
    public static Map<Long, Double> perSubject(List<Grade> grades, Map<Long, GradeCategory> categories,
                                               GradeScale scale) {
        Map<Long, List<Grade>> bySubject = grades.stream().collect(Collectors.groupingBy(Grade::subjectId));
        Map<Long, Double> result = new TreeMap<>();
        bySubject.forEach((subject, list) -> weighted(list, categories, scale)
                .ifPresent(avg -> result.put(subject, avg)));
        return Map.copyOf(result);
    }

    static int weight(Grade grade, Map<Long, GradeCategory> categories) {
        GradeCategory category = grade.categoryId() == null ? null : categories.get(grade.categoryId());
        if (category == null || Boolean.FALSE.equals(category.countsToAverage())) {
            return 0;
        }
        return category.weight() == null ? 1 : Math.max(0, category.weight());
    }
}
