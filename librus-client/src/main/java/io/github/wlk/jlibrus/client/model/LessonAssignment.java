package io.github.wlk.jlibrus.client.model;

/** A teaching assignment ({@code Lessons}): which teacher teaches which subject. */
public record LessonAssignment(long id, long subjectId, Long teacherId) {
}
