package io.github.wlk.jlibrus.server.domain.timeline;

import io.github.wlk.jlibrus.client.messages.MessageSummary;
import io.github.wlk.jlibrus.client.model.Announcement;
import io.github.wlk.jlibrus.client.model.Attendance;
import io.github.wlk.jlibrus.client.model.AttendanceKind;
import io.github.wlk.jlibrus.client.model.BehaviourPoint;
import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.LuckyNumber;
import io.github.wlk.jlibrus.client.model.Note;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Texts;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.student.Directory;
import io.github.wlk.jlibrus.server.domain.student.StudentRecords;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Converts a child's current data into timeline entries with stable keys, so a sync can store what it has
 * not seen before. {@code noticedAt} is a placeholder here; the sync decides it.
 */
public final class TimelineExtractor {

    private final String childId;
    private final Directory directory;
    private final ZoneId zone;
    private final Locale locale;

    public TimelineExtractor(String childId, Directory directory, ZoneId zone, Locale locale) {
        this.childId = childId;
        this.directory = directory;
        this.zone = zone;
        this.locale = locale;
    }

    public List<TimelineEntry> extract(StudentRecords records, List<AgendaItem> agenda, List<MessageSummary> inbox) {
        List<TimelineEntry> entries = new ArrayList<>();
        records.grades().forEach(g -> entries.add(grade(g)));
        records.behaviour().forEach(b -> entries.add(behaviour(b)));
        records.attendances().stream().map(this::attendance).flatMap(Optional::stream).forEach(entries::add);
        records.notes().forEach(n -> entries.add(note(n)));
        records.announcements().forEach(a -> entries.add(announcement(a)));
        records.luckyNumber().ifPresent(l -> entries.add(lucky(l)));
        agenda.stream().filter(i -> i.type() != EntryType.LESSON).forEach(i -> entries.add(fromAgenda(i)));
        inbox.forEach(m -> entries.add(message(m)));
        return List.copyOf(entries);
    }

    TimelineEntry grade(Grade grade) {
        String subject = directory.subjectName(grade.subjectId());
        String category = directory.gradeCategory(grade.categoryId()).map(c -> c.name()).orElse("");
        String comments = String.join("; ", grade.commentIds().stream()
                .map(id -> directory.gradeComments().get(id)).filter(c -> c != null).map(c -> c.text()).toList());
        String detail = String.join(" · ", List.of(category, comments).stream().filter(s -> !s.isBlank()).toList());
        return entry(EntryType.GRADE, "G" + grade.id(), subject + ": " + grade.value(), detail, subject,
                directory.personName(grade.teacherId()), grade.addedAt(), grade.addedAt());
    }

    TimelineEntry behaviour(BehaviourPoint point) {
        return entry(EntryType.BEHAVIOUR_GRADE, "B" + point.id(), point.display(), point.text(), null,
                directory.personName(point.teacherId()), point.addedAt(), point.addedAt());
    }

    Optional<TimelineEntry> attendance(Attendance attendance) {
        AttendanceKind kind = directory.attendanceType(attendance.typeId()).map(t -> t.kind()).orElse(AttendanceKind.PRESENT);
        if (kind == AttendanceKind.PRESENT || attendance.date() == null) {
            return Optional.empty();
        }
        String subject = directory.lesson(attendance.lessonId()).map(l -> directory.subjectName(l.subjectId())).orElse("");
        String lesson = attendance.lessonNo() == null ? "" : " (" + Texts.get("lesson", locale) + " " + attendance.lessonNo() + ")";
        String title = Texts.get("attendance." + kind.name(), locale) + (subject.isEmpty() ? "" : ": " + subject) + lesson;
        return Optional.of(new TimelineEntry(childId, EntryType.ATTENDANCE.name() + ":A" + attendance.id() + ":" + kind,
                EntryType.ATTENDANCE, title, null, blankToNull(subject),
                blankToNull(directory.personName(attendance.teacherId())), attendance.date(),
                instant(attendance.addedAt()), placeholder()));
    }

    TimelineEntry note(Note note) {
        String title = Texts.get("note." + note.polarity().name(), locale)
                + optionalSuffix(directory.noteCategoryName(note.categoryId()));
        return new TimelineEntry(childId, key(EntryType.NOTE, "N" + note.id()), EntryType.NOTE, title, note.text(), null,
                blankToNull(directory.personName(note.teacherId())), note.date(), null, placeholder());
    }

    TimelineEntry announcement(Announcement announcement) {
        return entry(EntryType.ANNOUNCEMENT, "S" + announcement.id(), announcement.title(), announcement.content(), null,
                directory.personName(announcement.authorId()), announcement.createdAt(), announcement.createdAt());
    }

    TimelineEntry lucky(LuckyNumber lucky) {
        return new TimelineEntry(childId, key(EntryType.LUCKY_NUMBER, lucky.day().toString()), EntryType.LUCKY_NUMBER,
                Texts.get("luckyNumber", locale) + ": " + lucky.number(), null, null, null, lucky.day(),
                instant(lucky.day().atStartOfDay()), placeholder());
    }

    TimelineEntry fromAgenda(AgendaItem item) {
        return new TimelineEntry(childId, key(item.type(), item.uid()), item.type(), item.title(), item.detail(),
                item.subject(), null, item.when().firstDay(), null, placeholder());
    }

    TimelineEntry message(MessageSummary message) {
        return entry(EntryType.MESSAGE, "M" + message.id(), message.topic(), message.preview(), null,
                message.counterpart(), message.sentAt(), message.sentAt());
    }

    private TimelineEntry entry(EntryType type, String id, String title, String detail, String subject, String author,
                                LocalDateTime occurred, LocalDateTime added) {
        return new TimelineEntry(childId, key(type, id), type, title, blankToNull(detail), blankToNull(subject),
                blankToNull(author), occurred == null ? null : occurred.toLocalDate(), instant(added), placeholder());
    }

    private static String key(EntryType type, String id) {
        return type.name() + ":" + id;
    }

    private Instant instant(LocalDateTime local) {
        return local == null ? null : local.atZone(zone).toInstant();
    }

    private static Instant placeholder() {
        return Instant.EPOCH;
    }

    private static String optionalSuffix(String text) {
        return text.isBlank() ? "" : " – " + text;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
