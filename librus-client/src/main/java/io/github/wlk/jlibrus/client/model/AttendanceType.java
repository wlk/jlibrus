package io.github.wlk.jlibrus.client.model;

/** Attendance type ({@code Attendances/Types}), including school-specific custom ones. */
public record AttendanceType(long id, String name, String shortName, AttendanceKind kind, String colorRgb) {
}
