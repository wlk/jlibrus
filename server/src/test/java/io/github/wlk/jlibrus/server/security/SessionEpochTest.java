package io.github.wlk.jlibrus.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A damaged stored epoch stops the server instead of quietly reviving revoked sessions. */
class SessionEpochTest {

    @Test
    void storedEpochIsReadAsANumber() {
        assertThat(SessionEpoch.parse("42")).isEqualTo(42L);
    }

    @Test
    void damagedEpochIsReportedWithTheSettingName() {
        assertThatThrownBy(() -> SessionEpoch.parse("forty-two"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SessionEpoch.KEY)
                .hasCauseInstanceOf(NumberFormatException.class);
    }
}
