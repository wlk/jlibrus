package io.github.wlk.jlibrus.server.domain.summary;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryWindowTest {

    private static final ZonedDateTime NOW =
            ZonedDateTime.of(2026, 11, 23, 18, 0, 0, 0, ZoneId.of("Europe/Warsaw"));

    @Test
    void dailyWindowCoversLast24HoursAndNextTwoDays() {
        SummaryWindow window = SummaryWindow.of(Frequency.DAILY, NOW);

        assertThat(window.newSince()).isEqualTo(NOW.minusDays(1).toInstant());
        assertThat(window.newUntil()).isEqualTo(NOW.toInstant());
        assertThat(window.upcomingFrom()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(window.upcomingTo()).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(window.periodKey()).isEqualTo("2026-11-23");
    }

    @Test
    void weeklyWindowUsesIsoWeekKey() {
        SummaryWindow window = SummaryWindow.of(Frequency.WEEKLY, NOW);

        assertThat(window.newSince()).isEqualTo(NOW.minusWeeks(1).toInstant());
        assertThat(window.upcomingTo()).isEqualTo(LocalDate.of(2026, 11, 29));
        assertThat(window.periodKey()).isEqualTo("2026-W48");
    }

    @Test
    void weeklyKeyUsesWeekBasedYearAroundNewYear() {
        ZonedDateTime newYearsEve = ZonedDateTime.of(2024, 12, 30, 8, 0, 0, 0, ZoneId.of("Europe/Warsaw"));

        assertThat(SummaryWindow.of(Frequency.WEEKLY, newYearsEve).periodKey()).isEqualTo("2025-W01");
    }

    @Test
    void monthlyWindowCoversPreviousMonthAndNextMonth() {
        SummaryWindow window = SummaryWindow.of(Frequency.MONTHLY, NOW);

        assertThat(window.newSince()).isEqualTo(NOW.minusMonths(1).toInstant());
        assertThat(window.upcomingFrom()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(window.upcomingTo()).isEqualTo(LocalDate.of(2026, 12, 22));
        assertThat(window.periodKey()).isEqualTo("2026-11");
    }

    @Test
    void containsChecksHalfOpenNewRange() {
        SummaryWindow window = SummaryWindow.of(Frequency.DAILY, NOW);

        assertThat(window.isNew(NOW.minusDays(1).toInstant())).isTrue();
        assertThat(window.isNew(NOW.toInstant())).isFalse();
        assertThat(window.isNew(NOW.minusDays(2).toInstant())).isFalse();
    }

    @Test
    void upcomingRangeIsInclusive() {
        SummaryWindow window = SummaryWindow.of(Frequency.DAILY, NOW);

        assertThat(window.isUpcoming(LocalDate.of(2026, 11, 23))).isTrue();
        assertThat(window.isUpcoming(LocalDate.of(2026, 11, 24))).isTrue();
        assertThat(window.isUpcoming(LocalDate.of(2026, 11, 25))).isFalse();
        assertThat(window.isUpcoming(LocalDate.of(2026, 11, 22))).isFalse();
    }

    @Test
    void frequencyParsesCaseInsensitively() {
        assertThat(Frequency.parse(" weekly ")).isEqualTo(Frequency.WEEKLY);
    }

    @Test
    void continuesFromPreviousUntilSoNothingFallsBetweenRuns() {
        SummaryWindow daily = SummaryWindow.of(Frequency.DAILY, NOW);
        java.time.Instant previous = NOW.minusDays(1).plusSeconds(35).toInstant();

        SummaryWindow next = daily.continuingFrom(previous);

        assertThat(next.newSince()).isEqualTo(previous);
        assertThat(next.newUntil()).isEqualTo(daily.newUntil());
        assertThat(next.periodKey()).isEqualTo(daily.periodKey());
    }

    @Test
    void catchesUpAfterMissedRunsButNotForever() {
        SummaryWindow daily = SummaryWindow.of(Frequency.DAILY, NOW);

        assertThat(daily.continuingFrom(NOW.minusDays(2).toInstant()).newSince())
                .isEqualTo(NOW.minusDays(2).toInstant());
        assertThat(daily.continuingFrom(NOW.minusDays(3).toInstant()).newSince())
                .isEqualTo(NOW.minusDays(3).toInstant());
        assertThat(daily.continuingFrom(NOW.minusDays(4).toInstant())).isEqualTo(daily);
        assertThat(daily.continuingFrom(NOW.toInstant())).isEqualTo(daily);
        assertThat(daily.continuingFrom(null)).isEqualTo(daily);
        SummaryWindow monthly = SummaryWindow.of(Frequency.MONTHLY, NOW);
        assertThat(monthly.continuingFrom(NOW.minusDays(80).toInstant()).newSince())
                .isEqualTo(NOW.minusDays(80).toInstant());
        assertThat(monthly.continuingFrom(NOW.minusDays(100).toInstant())).isEqualTo(monthly);
    }
}
