package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;

/**
 * The student's class, including school-year boundaries.
 *
 * @param endFirstSemester last day of the first semester (inclusive)
 * @param tutorId          homeroom teacher, may be {@code null}
 */
public record SchoolClass(long id, String name, LocalDate beginSchoolYear, LocalDate endFirstSemester,
                          LocalDate endSchoolYear, Long tutorId) {

    /** Semester (1 or 2) a day belongs to. */
    public int semesterOf(LocalDate day) {
        return endFirstSemester != null && day.isAfter(endFirstSemester) ? 2 : 1;
    }
}
