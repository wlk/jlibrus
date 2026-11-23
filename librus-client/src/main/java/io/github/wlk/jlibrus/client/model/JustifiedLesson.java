package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;

/** One lesson covered by a justification ({@code number} may be null). */
public record JustifiedLesson(LocalDate date, Integer number) {
}
