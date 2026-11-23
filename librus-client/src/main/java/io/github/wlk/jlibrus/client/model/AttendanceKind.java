package io.github.wlk.jlibrus.client.model;

/** What an attendance type means, whatever the school calls it. */
public enum AttendanceKind {
    PRESENT,
    LATE,
    ABSENT,
    EXCUSED,
    RELEASED;

    public boolean isAbsence() {
        return this == ABSENT || this == EXCUSED;
    }
}
