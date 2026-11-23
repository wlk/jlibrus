package io.github.wlk.jlibrus.server.domain;

import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Illegal feeds and subscriptions cannot be constructed; collections are defensive copies. */
class RecordInvariantsTest {

    private static final Instant T0 = Instant.EPOCH;
    private static final String TOKEN = SecretTokens.newToken();

    private static CalendarFeed feed(String name, String token, Set<String> children) {
        return new CalendarFeed("id", name, token, children, Set.of(EntryType.EVENT), T0);
    }

    @Test
    void feedNameAndTokenAreValidated() {
        assertThatThrownBy(() -> feed(" ", TOKEN, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> feed(null, TOKEN, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> feed("x".repeat(CalendarFeed.MAX_NAME_LENGTH + 1), TOKEN, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> feed("ok", "short", Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(feed("x".repeat(CalendarFeed.MAX_NAME_LENGTH), TOKEN, Set.of()).name()).hasSize(CalendarFeed.MAX_NAME_LENGTH);
        assertThatThrownBy(() -> new CalendarFeed(null, "n", TOKEN, Set.of(), Set.of(), T0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CalendarFeed("i", "n", TOKEN, Set.of(), Set.of(), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void feedChildFilterAndTokenRotation() {
        Set<String> children = new HashSet<>(Set.of("a"));
        CalendarFeed onlyA = feed("A", TOKEN, children);
        children.add("b");

        assertThat(onlyA.childIds()).containsExactly("a");
        assertThat(onlyA.includesChild("a")).isTrue();
        assertThat(onlyA.includesChild("b")).isFalse();
        assertThat(feed("all", TOKEN, Set.of()).includesChild("anyone")).isTrue();
        String newToken = SecretTokens.newToken();
        CalendarFeed rotated = onlyA.withToken(newToken);
        assertThat(rotated.token()).isEqualTo(newToken);
        assertThat(rotated).isEqualTo(new CalendarFeed("id", "A", newToken, Set.of("a"), Set.of(EntryType.EVENT), T0));
    }

    @Test
    void subscriptionNeedsFrequencyAndType() {
        EmailAddress email = EmailAddress.parse("a@example.pl");
        assertThatThrownBy(() -> new Subscription("s", email, Set.of(), Set.of(EntryType.GRADE), Set.of(), true, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Subscription("s", email, Set.of(Frequency.DAILY), Set.of(), Set.of(), true, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Subscription("s", null, Set.of(Frequency.DAILY), Set.of(EntryType.GRADE), Set.of(), true, T0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void subscriptionWantsOnlyItsFrequenciesWhenEnabled() {
        EmailAddress email = EmailAddress.parse("a@example.pl");
        Subscription enabled = new Subscription("s", email, Set.of(Frequency.WEEKLY), Set.of(EntryType.GRADE), Set.of("a"), true, T0);
        Subscription disabled = new Subscription("s", email, Set.of(Frequency.WEEKLY), Set.of(EntryType.GRADE), Set.of(), false, T0);

        assertThat(enabled.wants(Frequency.WEEKLY)).isTrue();
        assertThat(enabled.wants(Frequency.DAILY)).isFalse();
        assertThat(disabled.wants(Frequency.WEEKLY)).isFalse();
        assertThat(enabled.includesChild("a")).isTrue();
        assertThat(enabled.includesChild("b")).isFalse();
        assertThat(disabled.includesChild("b")).isTrue();
    }

    @Test
    void timelineEntryRequiresIdentity() {
        assertThatThrownBy(() -> new TimelineEntry(null, "k", EntryType.GRADE, "t", null, null, null, null, null, T0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TimelineEntry("c", "k", EntryType.GRADE, "t", null, null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
