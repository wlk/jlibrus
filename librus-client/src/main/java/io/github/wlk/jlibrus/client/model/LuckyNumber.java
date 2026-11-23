package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;

/** Lucky number; {@code day} may be the next school day (published the afternoon before). */
public record LuckyNumber(int number, LocalDate day) {
}
