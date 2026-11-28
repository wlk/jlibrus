package io.github.wlk.jlibrus.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifiersTest {

    @Test
    void safeIdsAreShortWordsWithoutPathCharacters() {
        assertThat(Identifiers.isSafeId("LID-9_x")).isTrue();
        assertThat(Identifiers.isSafeId("a".repeat(100))).isTrue();
        assertThat(Identifiers.isSafeId("a".repeat(101))).isFalse();
        assertThat(Identifiers.isSafeId("../x")).isFalse();
        assertThat(Identifiers.isSafeId("")).isFalse();
        assertThat(Identifiers.isSafeId(null)).isFalse();
    }

    @Test
    void numericIdsAlwaysFitALong() {
        assertThat(Identifiers.isNumericId("9".repeat(18))).isTrue();
        assertThat(Identifiers.isNumericId("9".repeat(19))).isFalse();
        assertThat(Identifiers.isNumericId("1a")).isFalse();
        assertThat(Identifiers.isNumericId(null)).isFalse();
    }

    @Test
    void groupIdsAreLettersOnly() {
        assertThat(Identifiers.isGroupId("tutors")).isTrue();
        assertThat(Identifiers.isGroupId("a".repeat(41))).isFalse();
        assertThat(Identifiers.isGroupId("x&y=1")).isFalse();
        assertThat(Identifiers.isGroupId(null)).isFalse();
    }
}
