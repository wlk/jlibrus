package io.github.wlk.jlibrus.client.model;

/** State of a timetable entry. */
public enum LessonStatus {
    NORMAL,
    CANCELLED,
    /** Same slot, different teacher, subject or room. */
    SUBSTITUTION,
    /** Lesson moved into this slot from elsewhere. */
    MOVED_IN,
    /** Lesson that used to be in this slot and moved elsewhere. */
    MOVED_OUT
}
