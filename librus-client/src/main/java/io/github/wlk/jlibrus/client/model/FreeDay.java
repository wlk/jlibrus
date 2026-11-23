package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;

/** School-wide day(s) off. */
public record FreeDay(long id, String name, LocalDate from, LocalDate to) {
}
