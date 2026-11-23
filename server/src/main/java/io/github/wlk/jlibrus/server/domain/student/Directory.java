package io.github.wlk.jlibrus.server.domain.student;

import io.github.wlk.jlibrus.client.model.AccountInfo;
import io.github.wlk.jlibrus.client.model.AttendanceType;
import io.github.wlk.jlibrus.client.model.Classroom;
import io.github.wlk.jlibrus.client.model.EventCategory;
import io.github.wlk.jlibrus.client.model.GradeCategory;
import io.github.wlk.jlibrus.client.model.GradeComment;
import io.github.wlk.jlibrus.client.model.LessonAssignment;
import io.github.wlk.jlibrus.client.model.NoteCategory;
import io.github.wlk.jlibrus.client.model.Person;
import io.github.wlk.jlibrus.client.model.School;
import io.github.wlk.jlibrus.client.model.SchoolClass;
import io.github.wlk.jlibrus.client.model.Subject;

import java.util.Map;
import java.util.Optional;

/**
 * Rarely-changing reference data of one child, indexed by id, with name lookups that never fail
 * (unknown ids resolve to an empty string).
 */
public record Directory(AccountInfo me, SchoolClass schoolClass, School school,
                        Map<Long, Subject> subjects, Map<Long, Person> people,
                        Map<Long, LessonAssignment> lessons, Map<Long, Classroom> classrooms,
                        Map<Long, GradeCategory> gradeCategories, Map<Long, GradeComment> gradeComments,
                        Map<Long, AttendanceType> attendanceTypes, Map<Long, EventCategory> eventCategories,
                        Map<Long, NoteCategory> noteCategories) {

    public Directory {
        subjects = Map.copyOf(subjects);
        people = Map.copyOf(people);
        lessons = Map.copyOf(lessons);
        classrooms = Map.copyOf(classrooms);
        gradeCategories = Map.copyOf(gradeCategories);
        gradeComments = Map.copyOf(gradeComments);
        attendanceTypes = Map.copyOf(attendanceTypes);
        eventCategories = Map.copyOf(eventCategories);
        noteCategories = Map.copyOf(noteCategories);
    }

    public String subjectName(Long id) {
        return lookup(subjects, id).map(Subject::name).orElse("");
    }

    public String personName(Long id) {
        return lookup(people, id).map(Person::fullName).orElse("");
    }

    public String classroomName(Long id) {
        return lookup(classrooms, id).map(Classroom::displayName).orElse("");
    }

    public String eventCategoryName(Long id) {
        return lookup(eventCategories, id).map(EventCategory::name).orElse("");
    }

    public String noteCategoryName(Long id) {
        return lookup(noteCategories, id).map(NoteCategory::name).orElse("");
    }

    public Optional<LessonAssignment> lesson(Long id) {
        return lookup(lessons, id);
    }

    public Optional<AttendanceType> attendanceType(Long id) {
        return lookup(attendanceTypes, id);
    }

    public Optional<GradeCategory> gradeCategory(Long id) {
        return lookup(gradeCategories, id);
    }

    private static <T> Optional<T> lookup(Map<Long, T> map, Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(map.get(id));
    }
}
