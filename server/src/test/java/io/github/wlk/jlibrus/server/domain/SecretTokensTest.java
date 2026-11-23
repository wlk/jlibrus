package io.github.wlk.jlibrus.server.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecretTokensTest {

    @Test
    void generatesUrlSafeTokensWith256BitsOfEntropy() {
        String token = SecretTokens.newToken();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void tokensAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(SecretTokens.newToken());
        }
        assertThat(seen).hasSize(1000);
    }

    @Test
    void comparesInConstantTimeSemantics() {
        assertThat(SecretTokens.matches("secret", "secret")).isTrue();
        assertThat(SecretTokens.matches("secret", "Secret")).isFalse();
        assertThat(SecretTokens.matches("secret", "secret2")).isFalse();
        assertThat(SecretTokens.matches(null, "secret")).isFalse();
        assertThat(SecretTokens.matches("secret", null)).isFalse();
    }

    @Test
    void validatesTokenShape() {
        assertThat(SecretTokens.looksValid(SecretTokens.newToken())).isTrue();
        assertThat(SecretTokens.looksValid("../etc/passwd")).isFalse();
        assertThat(SecretTokens.looksValid(null)).isFalse();
        assertThat(SecretTokens.looksValid("a".repeat(44))).isFalse();
    }
}
