package io.github.wlk.jlibrus.server.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionPrincipalTest {

    @Test
    void roundTrips() {
        SessionPrincipal principal = new SessionPrincipal("par#ent", 7);

        assertThat(principal.format()).isEqualTo("par#ent#7");
        assertThat(SessionPrincipal.parse("par#ent#7")).contains(principal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"parent", "#7", "parent#", "parent#x", "parent#-1", ""})
    void rejectsMalformedNames(String name) {
        assertThat(SessionPrincipal.parse(name)).isEmpty();
    }

    @Test
    void rejectsNullAndNegativeEpoch() {
        assertThat(SessionPrincipal.parse(null)).isEmpty();
        assertThatThrownBy(() -> new SessionPrincipal("p", -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
