package io.github.wlk.jlibrus.server.domain.summary;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * Time ranges one summary covers.
 *
 * @param newSince     entries noticed at or after this instant are "new"
 * @param newUntil     ... and before this instant
 * @param upcomingFrom first day (inclusive) of the "upcoming" agenda
 * @param upcomingTo   last day (inclusive) of the "upcoming" agenda
 * @param periodKey    stable key of the period; one summary per subscription, frequency and key
 */
public record SummaryWindow(Instant newSince, Instant newUntil, LocalDate upcomingFrom, LocalDate upcomingTo,
                            String periodKey) {

    public static SummaryWindow of(Frequency frequency, ZonedDateTime now) {
        LocalDate today = now.toLocalDate();
        return switch (frequency) {
            case DAILY -> new SummaryWindow(now.minusDays(1).toInstant(), now.toInstant(),
                    today, today.plusDays(1), today.toString());
            case WEEKLY -> new SummaryWindow(now.minusWeeks(1).toInstant(), now.toInstant(),
                    today, today.plusDays(6), weekKey(today));
            case MONTHLY -> new SummaryWindow(now.minusMonths(1).toInstant(), now.toInstant(),
                    today, today.plusMonths(1).minusDays(1),
                    String.format(Locale.ROOT, "%d-%02d", today.getYear(), today.getMonthValue()));
        };
    }

    /**
     * The same window, but "new" starts where the previous run of this frequency ended, so consecutive runs
     * neither drop nor repeat entries. Gaps longer than three periods (e.g. a long outage) are not caught up.
     */
    public SummaryWindow continuingFrom(Instant previousUntil) {
        if (previousUntil == null || !previousUntil.isBefore(newUntil)) {
            return this;
        }
        Duration span = Duration.between(newSince, newUntil);
        if (previousUntil.isBefore(newUntil.minus(span.multipliedBy(MAX_CATCH_UP_PERIODS)))) {
            return this;
        }
        return new SummaryWindow(previousUntil, newUntil, upcomingFrom, upcomingTo, periodKey);
    }

    static final int MAX_CATCH_UP_PERIODS = 3;

    public boolean isNew(Instant noticedAt) {
        return !noticedAt.isBefore(newSince) && noticedAt.isBefore(newUntil);
    }

    public boolean isUpcoming(LocalDate day) {
        return !day.isBefore(upcomingFrom) && !day.isAfter(upcomingTo);
    }

    private static String weekKey(LocalDate day) {
        return String.format(Locale.ROOT, "%d-W%02d",
                day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
