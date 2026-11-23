package io.github.wlk.jlibrus.client.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClassroomTest {

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "Sala matematyczna, 12, 12 Sala matematyczna",
            "Sala 12, 12, Sala 12",
            "12, h12, h12",
            "NULL, 12, 12",
            "Aula, NULL, Aula",
            "NULL, NULL, ''"})
    void displayName(String name, String symbol, String expected) {
        assertThat(new Classroom(1, name, symbol).displayName()).isEqualTo(expected);
    }
}
