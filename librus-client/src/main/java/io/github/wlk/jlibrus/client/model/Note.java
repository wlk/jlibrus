package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;

/** Behaviour note ("uwaga"). */
public record Note(long id, String text, Long categoryId, Long teacherId, LocalDate date, NotePolarity polarity) {
}
