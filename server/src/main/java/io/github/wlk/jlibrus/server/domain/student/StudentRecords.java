package io.github.wlk.jlibrus.server.domain.student;

import io.github.wlk.jlibrus.client.model.Announcement;
import io.github.wlk.jlibrus.client.model.Attendance;
import io.github.wlk.jlibrus.client.model.BehaviourPoint;
import io.github.wlk.jlibrus.client.model.CalendarEvent;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.model.FreeDay;
import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.Homework;
import io.github.wlk.jlibrus.client.model.Justification;
import io.github.wlk.jlibrus.client.model.LuckyNumber;
import io.github.wlk.jlibrus.client.model.Note;
import io.github.wlk.jlibrus.client.model.ParentMeeting;
import io.github.wlk.jlibrus.client.model.SchoolTrip;
import io.github.wlk.jlibrus.client.model.TeacherAbsence;

import java.util.List;
import java.util.Optional;

/** Frequently-changing data of one child, fetched together and cached briefly. */
public record StudentRecords(List<Grade> grades, List<BehaviourPoint> behaviour, List<Attendance> attendances,
                             List<CalendarEvent> events, List<Homework> homework, List<ParentMeeting> meetings,
                             List<FreeDay> freeDays, List<TeacherAbsence> teacherAbsences,
                             List<Announcement> announcements, List<Note> notes, Optional<LuckyNumber> luckyNumber,
                             List<DescriptiveGrade> descriptiveGrades, List<Justification> justifications,
                             List<SchoolTrip> trips) {

    public StudentRecords {
        grades = List.copyOf(grades);
        behaviour = List.copyOf(behaviour);
        attendances = List.copyOf(attendances);
        events = List.copyOf(events);
        homework = List.copyOf(homework);
        meetings = List.copyOf(meetings);
        freeDays = List.copyOf(freeDays);
        teacherAbsences = List.copyOf(teacherAbsences);
        announcements = List.copyOf(announcements);
        notes = List.copyOf(notes);
        descriptiveGrades = List.copyOf(descriptiveGrades);
        justifications = List.copyOf(justifications);
        trips = List.copyOf(trips);
    }

    public static StudentRecords empty() {
        return new StudentRecords(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of());
    }
}
