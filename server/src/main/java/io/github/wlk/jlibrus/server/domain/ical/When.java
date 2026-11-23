package io.github.wlk.jlibrus.server.domain.ical;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/** When a calendar item happens: either whole days or a local (school time zone) time range. */
public sealed interface When {

    LocalDate firstDay();

    LocalDate lastDay();

    /** Whether any day of this item falls within {@code [from, to]} (inclusive). */
    default boolean overlaps(LocalDate from, LocalDate to) {
        return !lastDay().isBefore(from) && !firstDay().isAfter(to);
    }

    /** Whole-day item covering {@code from}..{@code toInclusive}. */
    record AllDay(LocalDate from, LocalDate toInclusive) implements When {
        public AllDay {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(toInclusive, "toInclusive");
            if (toInclusive.isBefore(from)) {
                throw new IllegalArgumentException("toInclusive before from: " + from + " > " + toInclusive);
            }
        }

        public static AllDay single(LocalDate day) {
            return new AllDay(day, day);
        }

        @Override
        public LocalDate firstDay() {
            return from;
        }

        @Override
        public LocalDate lastDay() {
            return toInclusive;
        }
    }

    /** Item with a start and end time in the school's local time zone. */
    record Timed(LocalDateTime start, LocalDateTime end) implements When {
        public Timed {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("end before start: " + start + " > " + end);
            }
        }

        @Override
        public LocalDate firstDay() {
            return start.toLocalDate();
        }

        @Override
        public LocalDate lastDay() {
            return end.toLocalDate();
        }
    }
}
