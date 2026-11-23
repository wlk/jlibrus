package io.github.wlk.jlibrus.server.domain.agenda;

import io.github.wlk.jlibrus.client.model.CalendarEvent;
import io.github.wlk.jlibrus.client.model.FreeDay;
import io.github.wlk.jlibrus.client.model.Homework;
import io.github.wlk.jlibrus.client.model.LessonStatus;
import io.github.wlk.jlibrus.client.model.ParentMeeting;
import io.github.wlk.jlibrus.client.model.SchoolTrip;
import io.github.wlk.jlibrus.client.model.TeacherAbsence;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Texts;
import io.github.wlk.jlibrus.server.domain.ical.When;
import io.github.wlk.jlibrus.server.domain.student.Directory;
import io.github.wlk.jlibrus.server.domain.student.StudentRecords;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Turns a child's Librus data into dated {@link AgendaItem}s. Pure: same input, same output. */
public final class AgendaBuilder {

    private static final String DOMAIN = "@jlibrus";

    private final String childId;
    private final Directory directory;
    private final Locale locale;

    public AgendaBuilder(String childId, Directory directory, Locale locale) {
        this.childId = childId;
        this.directory = directory;
        this.locale = locale;
    }

    /** Every agenda item of the records and the given timetable weeks, sorted by start. */
    public List<AgendaItem> build(StudentRecords records, List<TimetableEntry> timetable) {
        List<AgendaItem> items = new ArrayList<>();
        records.events().stream().filter(e -> e.date() != null).map(this::event).forEach(items::add);
        records.homework().stream().filter(h -> h.dueOn() != null).map(this::homework).forEach(items::add);
        records.meetings().stream().filter(m -> m.date() != null).map(this::meeting).forEach(items::add);
        records.freeDays().stream().filter(d -> d.from() != null).map(this::freeDay).forEach(items::add);
        records.teacherAbsences().stream().filter(t -> t.from() != null).map(this::teacherAbsence).forEach(items::add);
        records.trips().stream().filter(t -> t.from() != null).map(this::trip).forEach(items::add);
        timetable.forEach(entry -> items.add(entry.status() == LessonStatus.NORMAL ? lesson(entry) : change(entry)));
        items.sort(Comparator.comparing((AgendaItem i) -> i.when().firstDay()).thenComparing(AgendaBuilder::startTime)
                .thenComparing(AgendaItem::uid));
        return List.copyOf(items);
    }

    private static LocalTime startTime(AgendaItem item) {
        return item.when() instanceof When.Timed timed ? timed.start().toLocalTime() : LocalTime.MIN;
    }

    AgendaItem event(CalendarEvent event) {
        String category = directory.eventCategoryName(event.categoryId());
        String subject = directory.subjectName(event.subjectId());
        String title = join(" – ", category, subject);
        return new AgendaItem(uid(EntryType.EVENT, String.valueOf(event.id())), childId, EntryType.EVENT,
                title.isEmpty() ? event.content() : title, event.content(), blankToNull(subject),
                null, eventTiming(event));
    }

    private When eventTiming(CalendarEvent event) {
        if (event.timeFrom() != null && event.timeTo() != null && event.timeTo().isAfter(event.timeFrom())) {
            return new When.Timed(event.date().atTime(event.timeFrom()), event.date().atTime(event.timeTo()));
        }
        return slotTiming(event.date(), event.lessonNo());
    }

    private When slotTiming(LocalDate date, Integer lessonNo) {
        if (lessonNo == null) {
            return When.AllDay.single(date);
        }
        return directory.school().slot(lessonNo)
                .<When>map(slot -> new When.Timed(date.atTime(slot.from()), date.atTime(slot.to())))
                .orElse(When.AllDay.single(date));
    }

    AgendaItem homework(Homework homework) {
        String teacher = directory.personName(homework.teacherId());
        return new AgendaItem(uid(EntryType.HOMEWORK, String.valueOf(homework.id())), childId, EntryType.HOMEWORK,
                Texts.get("homework", locale) + ": " + homework.topic(),
                join("\n", homework.text(), teacher), null, null, When.AllDay.single(homework.dueOn()));
    }

    AgendaItem meeting(ParentMeeting meeting) {
        When when = meeting.time() == null ? When.AllDay.single(meeting.date())
                : new When.Timed(meeting.date().atTime(meeting.time()), meeting.date().atTime(meeting.time()).plusHours(1));
        return new AgendaItem(uid(EntryType.PARENT_MEETING, String.valueOf(meeting.id())), childId,
                EntryType.PARENT_MEETING, meeting.name(),
                join("\n", meeting.topic(), directory.personName(meeting.teacherId())), null,
                blankToNull(meeting.room()), when);
    }

    AgendaItem freeDay(FreeDay day) {
        LocalDate to = day.to() == null || day.to().isBefore(day.from()) ? day.from() : day.to();
        return new AgendaItem(uid(EntryType.FREE_DAY, String.valueOf(day.id())), childId, EntryType.FREE_DAY,
                day.name(), null, null, null, new When.AllDay(day.from(), to));
    }

    AgendaItem teacherAbsence(TeacherAbsence absence) {
        LocalDate to = absence.to() == null || absence.to().isBefore(absence.from()) ? absence.from() : absence.to();
        boolean timed = absence.timeFrom() != null && absence.timeTo() != null && to.equals(absence.from())
                && absence.timeTo().isAfter(absence.timeFrom());
        When when = timed ? new When.Timed(absence.from().atTime(absence.timeFrom()), absence.from().atTime(absence.timeTo()))
                : new When.AllDay(absence.from(), to);
        return new AgendaItem(uid(EntryType.TEACHER_ABSENCE, String.valueOf(absence.id())), childId,
                EntryType.TEACHER_ABSENCE,
                Texts.get("teacherAbsent", locale) + ": " + directory.personName(absence.teacherId()),
                null, null, null, when);
    }

    AgendaItem trip(SchoolTrip trip) {
        LocalDate to = trip.to() == null || trip.to().isBefore(trip.from()) ? trip.from() : trip.to();
        String detail = join(" · ", trip.route(), trip.transport(), trip.coordinator());
        return new AgendaItem(uid(EntryType.SCHOOL_TRIP, String.valueOf(trip.id())), childId, EntryType.SCHOOL_TRIP,
                Texts.get("trip", locale) + ": " + trip.destination(), blankToNull(detail), null, null,
                new When.AllDay(trip.from(), to));
    }

    AgendaItem change(TimetableEntry entry) {
        String prefix = Texts.get(switch (entry.status()) {
            case CANCELLED -> "cancelled";
            case SUBSTITUTION -> "substitution";
            case MOVED_IN -> "movedIn";
            default -> "movedOut";
        }, locale);
        String detail = join("\n",
                entry.originalTeacher() == null ? null : entry.originalTeacher() + " → " + entry.teacherName(),
                entry.movedToDate() == null ? null : entry.movedToDate() + " (" + entry.movedToLessonNo() + ")");
        return new AgendaItem(uid(EntryType.TIMETABLE_CHANGE, entry.date() + "-" + entry.lessonNo() + "-" + entry.status()),
                childId, EntryType.TIMETABLE_CHANGE, prefix + ": " + entry.subjectName(), blankToNull(detail),
                blankToNull(entry.subjectName()), blankToNull(directory.classroomName(entry.classroomId())),
                lessonTiming(entry));
    }

    AgendaItem lesson(TimetableEntry entry) {
        return new AgendaItem(uid(EntryType.LESSON, entry.date() + "-" + entry.lessonNo()), childId, EntryType.LESSON,
                entry.subjectName(), blankToNull(entry.teacherName()), blankToNull(entry.subjectName()),
                blankToNull(directory.classroomName(entry.classroomId())), lessonTiming(entry));
    }

    private When lessonTiming(TimetableEntry entry) {
        if (entry.from() != null && entry.to() != null && entry.to().isAfter(entry.from())) {
            return new When.Timed(entry.date().atTime(entry.from()), entry.date().atTime(entry.to()));
        }
        return slotTiming(entry.date(), entry.lessonNo());
    }

    private String uid(EntryType type, String id) {
        return type.name().toLowerCase(Locale.ROOT) + "-" + childId + "-" + id + DOMAIN;
    }

    static String join(String separator, String... parts) {
        return String.join(separator, Stream.of(parts).filter(Objects::nonNull).map(String::strip)
                .filter(s -> !s.isEmpty()).toList());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
