package io.github.wlk.jlibrus.server.domain;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryTypeTest {

    @Test
    void blankCsvMeansAllTypes() {
        assertThat(EntryType.parseSet(" ")).hasSize(EntryType.values().length);
        assertThat(EntryType.parseSet(null)).hasSize(EntryType.values().length);
    }

    @Test
    void parsesCsvIgnoringCaseAndBlanks() {
        assertThat(EntryType.parseSet("grade, homework,,")).containsExactlyInAnyOrder(EntryType.GRADE, EntryType.HOMEWORK);
    }

    @Test
    void rejectsUnknownTypes() {
        assertThatThrownBy(() -> EntryType.parseSet("GRADE,NOPE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void labelsArePolishOrEnglish() {
        assertThat(EntryType.GRADE.label(Locale.forLanguageTag("pl"))).isEqualTo("Oceny");
        assertThat(EntryType.GRADE.label(Locale.ENGLISH)).isEqualTo("Grades");
    }

    @Test
    void labelsFallBackToPolishLikeEveryOtherServerText() {
        assertThat(EntryType.GRADE.label(Locale.GERMAN)).isEqualTo("Oceny");
        assertThat(Texts.get("summary.new", Locale.GERMAN)).isEqualTo("Nowe wpisy");
    }

    @Test
    void calendarTypesAreThoseWithDates() {
        assertThat(EntryType.calendarTypes())
                .contains(EntryType.EVENT, EntryType.HOMEWORK, EntryType.FREE_DAY)
                .doesNotContain(EntryType.GRADE, EntryType.MESSAGE);
    }
}
