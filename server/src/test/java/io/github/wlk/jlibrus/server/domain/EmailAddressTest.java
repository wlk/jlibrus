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
            "a@example.com\r\nBcc: x@y.z", "a@.com", "a@-x.pl", "a@x-.pl", "a@b..pl", "a@", "a@b.pl@c.pl",
            "a@b.pl.", "a@xn_.pl"})
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

    @Test
    void acceptsRealisticAddresses() {
        assertThat(EmailAddress.parse("jan.kowalski+librus@szkola-7.edu.pl").value()).isEqualTo("jan.kowalski+librus@szkola-7.edu.pl");
        assertThat(EmailAddress.parse("x".repeat(64) + "@a.pl").value()).startsWith("xxx");
        assertThatThrownBy(() -> EmailAddress.parse("x".repeat(65) + "@a.pl")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EmailAddress.parse("a@" + "b".repeat(64) + ".pl")).isInstanceOf(IllegalArgumentException.class);
    }
}
