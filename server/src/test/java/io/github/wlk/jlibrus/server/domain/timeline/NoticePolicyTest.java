package io.github.wlk.jlibrus.server.domain.timeline;

import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class NoticePolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");
    private static final Instant ADDED = Instant.parse("2026-11-23T08:30:00Z");

    @Test
    void nothingKnownMeansFirstSync() {
        assertThat(NoticePolicy.isFirstSync(Set.of())).isTrue();
        assertThat(NoticePolicy.isFirstSync(Set.of("GRADE:1"))).isFalse();
    }

    @Test
    void firstSyncDatesEntriesByLibrusTimestampCappedAtNowOrEpochWhenMissing() {
        List<TimelineEntry> entries = List.of(entry("GRADE:1", ADDED), entry("NOTE:2", null),
                entry("GRADE:3", NOW.plusSeconds(60)));

        assertThat(NoticePolicy.unseen(entries, Set.of(), NOW)).extracting(TimelineEntry::key, TimelineEntry::noticedAt)
                .containsExactly(tuple("GRADE:1", ADDED), tuple("NOTE:2", Instant.EPOCH), tuple("GRADE:3", NOW));
    }

    @Test
    void laterSyncsKeepOnlyUnknownEntriesNoticedNow() {
        List<TimelineEntry> entries = List.of(entry("GRADE:1", ADDED), entry("NOTE:2", null), entry("GRADE:3", ADDED));

        assertThat(NoticePolicy.unseen(entries, Set.of("GRADE:1", "GRADE:3"), NOW))
                .extracting(TimelineEntry::key, TimelineEntry::noticedAt).containsExactly(tuple("NOTE:2", NOW));
    }

    @Test
    void nothingToWriteWhenEverythingIsKnown() {
        assertThat(NoticePolicy.unseen(List.of(entry("GRADE:1", ADDED)), Set.of("GRADE:1"), NOW)).isEmpty();
    }

    private static TimelineEntry entry(String key, Instant addedAt) {
        return new TimelineEntry("kid", key, EntryType.GRADE, key, null, null, null, null, addedAt, Instant.EPOCH);
    }
}
