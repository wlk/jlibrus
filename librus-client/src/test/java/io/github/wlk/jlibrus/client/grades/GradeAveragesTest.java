package io.github.wlk.jlibrus.client.grades;

import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.GradeCategory;
import io.github.wlk.jlibrus.client.model.GradeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GradeAveragesTest {

    private static final GradeScale SCALE = GradeScale.DEFAULT;
    private static final Map<Long, GradeCategory> CATEGORIES = List.of(
            new GradeCategory(1, "Sprawdzian", 3, true, null),
            new GradeCategory(2, "Kartkówka", 2, true, null),
            new GradeCategory(3, "Aktywność", null, false, null),
            new GradeCategory(4, "Bez flagi", null, null, null),
            new GradeCategory(5, "Waga zero", 0, true, null)
    ).stream().collect(Collectors.toMap(GradeCategory::id, Function.identity()));

    private static Grade grade(String value, long category, long subject, GradeKind kind) {
        return new Grade(value.hashCode(), value, category, subject, null, 1, null, kind, List.of(), null);
    }

    private static Grade grade(String value, long category) {
        return grade(value, category, 100, GradeKind.REGULAR);
    }

    @ParameterizedTest
    @CsvSource({"6, 6.0", "1, 1.0", "4+, 4.5", "5-, 4.75", "3 +, 3.5", "-5, 4.75", "+4, 4.5"})
    void numericValueOfMarks(String mark, double expected) {
        assertThat(SCALE.numeric(mark)).hasValue(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"np", "+", "-", "bz", "0", "7", "", "nb", "4++"})
    void nonNumericMarksHaveNoValue(String mark) {
        assertThat(SCALE.numeric(mark)).isEmpty();
    }

    @Test
    void weightedAverageUsesCategoryWeights() {
        List<Grade> grades = List.of(grade("5", 1), grade("3", 2));

        assertThat(GradeAverages.weighted(grades, CATEGORIES, SCALE)).hasValue((5 * 3 + 3 * 2) / 5.0);
    }

    @Test
    void skipsNonCountingSummaryAndNonNumericGrades() {
        List<Grade> grades = List.of(grade("5", 1), grade("1", 3), grade("np", 1), grade("2", 5),
                grade("1", 1, 100, GradeKind.SEMESTER), grade("1", 99));

        assertThat(GradeAverages.weighted(grades, CATEGORIES, SCALE)).hasValue(5.0);
    }

    @Test
    void categoryWithoutFlagCountsWithWeightOne() {
        List<Grade> grades = List.of(grade("6", 4), grade("4", 4));

        assertThat(GradeAverages.weighted(grades, CATEGORIES, SCALE)).hasValue(5.0);
    }

    @Test
    void emptyWhenNothingCounts() {
        assertThat(GradeAverages.weighted(List.of(grade("5", 3)), CATEGORIES, SCALE)).isEmpty();
        assertThat(GradeAverages.weighted(List.of(), CATEGORIES, SCALE)).isEmpty();
    }

    @Test
    void averagesPerSubject() {
        List<Grade> grades = List.of(grade("5", 1, 100, GradeKind.REGULAR), grade("4+", 2, 200, GradeKind.REGULAR),
                grade("np", 2, 300, GradeKind.REGULAR));

        Map<Long, Double> averages = GradeAverages.perSubject(grades, CATEGORIES, SCALE);

        assertThat(averages).containsOnlyKeys(100L, 200L);
        assertThat(averages.get(200L)).isCloseTo(4.5, within(1e-9));
    }

    @Test
    void scaleIsValidated() {
        assertThat(new GradeScale(0.33, 0.33).numeric("4+")).hasValue(4.33);
        assertThatThrownBy(() -> new GradeScale(-0.1, 0.25)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GradeScale(0.5, 1.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
