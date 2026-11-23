package io.github.wlk.jlibrus.client.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One lesson in the weekly timetable.
 *
 * @param originalSubject for substitutions: the subject originally planned, else {@code null}
 * @param originalTeacher for substitutions: the teacher originally planned, else {@code null}
 * @param movedToDate     for moved-out lessons: the day it moved to, else {@code null}
 */
public record TimetableEntry(LocalDate date, int lessonNo, LocalTime from, LocalTime to,
                             Long subjectId, String subjectName, Long teacherId, String teacherName,
                             Long classroomId, LessonStatus status, String originalSubject,
                             String originalTeacher, LocalDate movedToDate, Integer movedToLessonNo) {
}
