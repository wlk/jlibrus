package io.github.wlk.jlibrus.client.model;

import java.time.LocalTime;

/** Bell schedule entry: lesson number and its times. */
public record LessonSlot(int number, LocalTime from, LocalTime to) {
}
