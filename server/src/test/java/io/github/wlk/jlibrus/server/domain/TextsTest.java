package io.github.wlk.jlibrus.server.domain;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class TextsTest {

    @Test
    void englishOnlyForEnglishOtherwisePolishLikeTheWebApp() {
        assertThat(Texts.get("summary.new", Locale.ENGLISH)).isEqualTo("What's new");
        assertThat(Texts.get("summary.new", Locale.forLanguageTag("pl"))).isEqualTo("Nowe wpisy");
        assertThat(Texts.get("summary.new", Locale.GERMAN)).isEqualTo("Nowe wpisy");
    }

    @Test
    void unknownKeysAreShownAsTheKey() {
        assertThat(Texts.get("no.such.key", Locale.ENGLISH)).isEqualTo("no.such.key");
    }
}
