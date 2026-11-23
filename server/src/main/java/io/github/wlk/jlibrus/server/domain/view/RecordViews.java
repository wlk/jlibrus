package io.github.wlk.jlibrus.server.domain.view;

import io.github.wlk.jlibrus.client.model.Announcement;
import io.github.wlk.jlibrus.client.model.Attendance;
import io.github.wlk.jlibrus.client.model.AttendanceKind;
import io.github.wlk.jlibrus.client.model.AttendanceType;
import io.github.wlk.jlibrus.client.model.BehaviourPoint;
import io.github.wlk.jlibrus.client.model.Homework;
import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.client.model.Note;
import io.github.wlk.jlibrus.client.model.NotePolarity;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.ical.When;
import io.github.wlk.jlibrus.server.domain.student.Directory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Display records for attendance, timetable, agenda, homework, notes and announcements. */
public final class RecordViews {

    public record AttendanceView(String id, LocalDate date, Integer lessonNo, String subject, String type,
                                 String shortName, AttendanceKind kind, String teacher, String colorHex) {
    }

    /** Counts over every attendance record; {@code presencePercent} counts late as present. */
    public record AttendanceStats(int lessons, Map<AttendanceKind, Integer> byKind, Double presencePercent) {
    }

    public record AttendanceReport(AttendanceStats stats, List<AttendanceView> absences) {
    }

    public record LessonView(LocalDate date, int lessonNo, LocalTime from, LocalTime to, String subject, String teacher,
                             String room, LessonStatus status, String originalTeacher, LocalDate movedToDate) {
    }

    public record AgendaView(String uid, EntryType type, String title, String detail, String subject, String location,
                             LocalDate start, LocalDate end, LocalTime startTime, LocalTime endTime, boolean allDay) {
    }

    public record HomeworkView(long id, String topic, String text, String teacher, LocalDate assignedOn, LocalDate dueOn,
                               List<String> attachments) {
    }

    public record NoteView(long id, LocalDate date, String category, String text, String teacher, NotePolarity polarity) {
    }

    public record BehaviourView(long id, String display, String text, String teacher, LocalDateTime addedAt) {
    }

    public record AnnouncementView(String id, String title, String content, LocalDate startDate, LocalDate endDate,
                                   String author, LocalDateTime createdAt, boolean read) {
    }

    private RecordViews() {
    }

    public static AttendanceReport attendance(Directory directory, List<Attendance> records) {
        Map<AttendanceKind, Integer> counts = new EnumMap<>(AttendanceKind.class);
        for (AttendanceKind kind : AttendanceKind.values()) {
            counts.put(kind, 0);
        }
        records.forEach(a -> counts.merge(kind(directory, a), 1, Integer::sum));
        int total = records.size();
        int present = counts.get(AttendanceKind.PRESENT) + counts.get(AttendanceKind.LATE);
        Double percent = total == 0 ? null : Math.round(1000.0 * present / total) / 10.0;
        List<AttendanceView> absences = records.stream()
                .filter(a -> kind(directory, a) != AttendanceKind.PRESENT)
                .sorted(Comparator.comparing(Attendance::date, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(a -> a.lessonNo() == null ? 0 : a.lessonNo()))
                .map(a -> attendanceView(directory, a))
                .toList();
        return new AttendanceReport(new AttendanceStats(total, Map.copyOf(counts), percent), absences);
    }

    private static AttendanceKind kind(Directory directory, Attendance attendance) {
        return directory.attendanceType(attendance.typeId()).map(AttendanceType::kind).orElse(AttendanceKind.PRESENT);
    }

    static AttendanceView attendanceView(Directory directory, Attendance a) {
        AttendanceType type = directory.attendanceType(a.typeId()).orElse(null);
        String subject = directory.lesson(a.lessonId()).map(l -> directory.subjectName(l.subjectId())).orElse("");
        return new AttendanceView(a.id(), a.date(), a.lessonNo(), subject, type == null ? "" : type.name(),
                type == null ? "" : type.shortName(), kind(directory, a), directory.personName(a.teacherId()),
                type == null || type.colorRgb().isBlank() ? null : "#" + type.colorRgb());
    }

    public static List<LessonView> lessons(Directory directory, List<TimetableEntry> entries) {
        return entries.stream().map(e -> new LessonView(e.date(), e.lessonNo(), e.from(), e.to(), e.subjectName(),
                e.teacherName(), directory.classroomName(e.classroomId()), e.status(), e.originalTeacher(),
                e.movedToDate())).toList();
    }

    public static AgendaView agenda(AgendaItem item) {
        return switch (item.when()) {
            case When.AllDay allDay -> new AgendaView(item.uid(), item.type(), item.title(), item.detail(), item.subject(),
                    item.location(), allDay.from(), allDay.toInclusive(), null, null, true);
            case When.Timed timed -> new AgendaView(item.uid(), item.type(), item.title(), item.detail(), item.subject(),
                    item.location(), timed.start().toLocalDate(), timed.end().toLocalDate(),
                    timed.start().toLocalTime(), timed.end().toLocalTime(), false);
        };
    }

    public static List<HomeworkView> homework(Directory directory, List<Homework> homework) {
        return homework.stream()
                .sorted(Comparator.comparing(Homework::dueOn, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(h -> new HomeworkView(h.id(), h.topic(), h.text(), directory.personName(h.teacherId()),
                        h.assignedOn(), h.dueOn(), h.attachmentNames()))
                .toList();
    }

    public static List<NoteView> notes(Directory directory, List<Note> notes) {
        return notes.stream()
                .sorted(Comparator.comparing(Note::date, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(n -> new NoteView(n.id(), n.date(), directory.noteCategoryName(n.categoryId()), n.text(),
                        directory.personName(n.teacherId()), n.polarity()))
                .toList();
    }

    public static List<BehaviourView> behaviour(Directory directory, List<BehaviourPoint> points) {
        return points.stream()
                .sorted(Comparator.comparing(BehaviourPoint::addedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(b -> new BehaviourView(b.id(), b.display(), b.text(), directory.personName(b.teacherId()), b.addedAt()))
                .toList();
    }

    public static List<AnnouncementView> announcements(Directory directory, List<Announcement> announcements) {
        return announcements.stream()
                .sorted(Comparator.comparing(Announcement::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(a -> new AnnouncementView(a.id(), a.title(), a.content(), a.startDate(), a.endDate(),
                        directory.personName(a.authorId()), a.createdAt(), a.read()))
                .toList();
    }
}
