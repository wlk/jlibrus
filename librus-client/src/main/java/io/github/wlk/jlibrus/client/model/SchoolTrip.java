package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;

/** A school trip ("wycieczka") registered by the school. */
public record SchoolTrip(long id, String destination, String route, String transport, LocalDate from, LocalDate to,
                         String coordinator) {
}
