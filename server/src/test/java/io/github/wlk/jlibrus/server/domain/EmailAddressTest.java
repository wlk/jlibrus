package io.github.wlk.jlibrus.server.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressTest {

    @Test
    void normalisesCaseAndWhitespace() {
        assertThat(EmailAddress.parse("  Mama@Example.PL ").value()).isEqualTo("mama@example.pl");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plain", "a@b", "@example.com", "a@@example.com", "a b@example.com",
            "a@example.com\r\nBcc: x@y.z", "a@.com"})
    void rejectsInvalidAddresses(String raw) {
        assertThatThrownBy(() -> EmailAddress.parse(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid e-mail");
    }

    @Test
    void rejectsOverlongAddress() {
        String raw = "a".repeat(250) + "@example.com";
        assertThatThrownBy(() -> EmailAddress.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> EmailAddress.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
