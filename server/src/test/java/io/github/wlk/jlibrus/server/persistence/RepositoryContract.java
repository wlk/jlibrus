package io.github.wlk.jlibrus.server.persistence;

import io.github.wlk.jlibrus.server.domain.CalendarFeed;
import io.github.wlk.jlibrus.server.domain.EmailAddress;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.SecretTokens;
import io.github.wlk.jlibrus.server.domain.Subscription;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Behaviour every supported database must satisfy; run once per engine by subclasses. */
abstract class RepositoryContract {

    private static final Instant T0 = Instant.parse("2026-11-20T08:00:00Z");

    protected abstract Jdbc jdbc();

    private CalendarFeedRepository feeds;
    private SubscriptionRepository subscriptions;
    private DispatchRepository dispatches;
    private TimelineRepository timeline;

    @BeforeEach
    void setUpRepositories() {
        Jdbc jdbc = jdbc();
        for (String table : List.of("summary_dispatch", "email_subscription", "calendar_feed", "timeline_entry")) {
            jdbc.update("DELETE FROM " + table, s -> { });
        }
        feeds = new CalendarFeedRepository(jdbc);
        subscriptions = new SubscriptionRepository(jdbc);
        dispatches = new DispatchRepository(jdbc);
        timeline = new TimelineRepository(jdbc);
    }

    @Test
    void feedRoundTripAndTokenLookup() {
        CalendarFeed feed = new CalendarFeed(UUID.randomUUID().toString(), "Family", SecretTokens.newToken(),
                Set.of("1234567u"), Set.of(EntryType.EVENT, EntryType.HOMEWORK), T0);

        feeds.save(feed);

        assertThat(feeds.findByToken(feed.token())).contains(feed);
        assertThat(feeds.findAll()).containsExactly(feed);
    }

    @Test
    void savingFeedWithNewTokenInvalidatesOldToken() {
        CalendarFeed feed = feeds.save(new CalendarFeed(UUID.randomUUID().toString(), "All",
                SecretTokens.newToken(), Set.of(), EntryType.calendarTypes(), T0));
        CalendarFeed rotated = feeds.save(feed.withToken(SecretTokens.newToken()));

        assertThat(feeds.findByToken(feed.token())).isEmpty();
        assertThat(feeds.findByToken(rotated.token())).contains(rotated);
        assertThat(feeds.delete(feed.id())).isTrue();
        assertThat(feeds.findById(feed.id())).isEmpty();
    }

    @Test
    void subscriptionRoundTripAndFrequencyFilter() {
        Subscription daily = subscription("mama@example.pl", Set.of(Frequency.DAILY), true);
        Subscription weeklyDisabled = subscription("tata@example.pl", Set.of(Frequency.WEEKLY), false);
        subscriptions.save(daily);
        subscriptions.save(weeklyDisabled);

        assertThat(subscriptions.findById(daily.id())).contains(daily);
        assertThat(subscriptions.findWanting(Frequency.DAILY)).containsExactly(daily);
        assertThat(subscriptions.findWanting(Frequency.WEEKLY)).isEmpty();
        assertThat(subscriptions.delete(daily.id())).isTrue();
        assertThat(subscriptions.findAll()).containsExactly(weeklyDisabled);
    }

    @Test
    void dispatchClaimIsIdempotentUntilReleased() {
        assertThat(dispatches.claim("s1", Frequency.DAILY, "2026-11-23", T0)).isTrue();
        assertThat(dispatches.claim("s1", Frequency.DAILY, "2026-11-23", T0)).isFalse();
        assertThat(dispatches.claim("s1", Frequency.WEEKLY, "2026-11-23", T0)).isTrue();

        dispatches.release("s1", Frequency.DAILY, "2026-11-23");

        assertThat(dispatches.claim("s1", Frequency.DAILY, "2026-11-23", T0)).isTrue();
    }

    @Test
    void timelineInsertsOnlyUnknownEntriesAndKeepsFirstNoticedTime() {
        TimelineEntry grade = entry("GRADE:1", EntryType.GRADE, T0);
        assertThat(timeline.hasAny("kid")).isFalse();

        assertThat(timeline.insertNew(List.of(grade, entry("NOTE:2", EntryType.NOTE, T0)))).isEqualTo(2);
        assertThat(timeline.insertNew(List.of(grade.withNoticedAt(T0.plusSeconds(3600))))).isZero();

        assertThat(timeline.hasAny("kid")).isTrue();
        assertThat(timeline.findRecent("kid", 10)).extracting(TimelineEntry::noticedAt).containsOnly(T0);
    }

    @Test
    void timelineFindsNoticedEntriesInHalfOpenRangeByTypeAndChild() {
        timeline.insertNew(List.of(
                entry("GRADE:1", EntryType.GRADE, T0),
                entry("GRADE:2", EntryType.GRADE, T0.plusSeconds(60)),
                entry("NOTE:3", EntryType.NOTE, T0.plusSeconds(30)),
                entry("GRADE:4", EntryType.GRADE, T0.plusSeconds(120))));

        List<TimelineEntry> found = timeline.findNoticed(Set.of(), Set.of(EntryType.GRADE), T0, T0.plusSeconds(120));

        assertThat(found).extracting(TimelineEntry::key).containsExactly("GRADE:2", "GRADE:1");
        assertThat(timeline.findNoticed(Set.of("other"), Set.of(EntryType.GRADE), T0, T0.plusSeconds(500))).isEmpty();
    }

    @Test
    void timelinePreservesOptionalColumns() {
        TimelineEntry full = new TimelineEntry("kid", "EVENT:9", EntryType.EVENT, "Sprawdzian", "Działy 1-3",
                "Matematyka", "Jan Kowalski", LocalDate.of(2026, 12, 1), T0.minusSeconds(10), T0);
        TimelineEntry sparse = new TimelineEntry("kid", "LUCKY_NUMBER:2026-12-01", EntryType.LUCKY_NUMBER, "7",
                null, null, null, null, null, T0);
        timeline.insertNew(List.of(full, sparse));

        assertThat(timeline.findRecent("kid", 10)).containsExactlyInAnyOrder(full, sparse);
    }

    private static Subscription subscription(String email, Set<Frequency> frequencies, boolean enabled) {
        return new Subscription(UUID.randomUUID().toString(), EmailAddress.parse(email), frequencies,
                Set.of(EntryType.GRADE, EntryType.EVENT), Set.of(), enabled, T0);
    }

    private static TimelineEntry entry(String key, EntryType type, Instant noticed) {
        return new TimelineEntry("kid", key, type, key, null, null, null, null, null, noticed);
    }
}
