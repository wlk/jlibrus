package io.github.wlk.jlibrus.server.service;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.model.LuckyNumber;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import io.github.wlk.jlibrus.server.config.AppContext;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaBuilder;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.student.Directory;
import io.github.wlk.jlibrus.server.domain.view.GradeViews;
import io.github.wlk.jlibrus.server.domain.view.RecordViews;
import io.github.wlk.jlibrus.server.librus.Children;
import io.github.wlk.jlibrus.server.librus.LibrusData;
import io.github.wlk.jlibrus.server.persistence.TimelineRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read side for the UI: combines cached Librus data into display models. */
@ApplicationScoped
public class ChildService {

    private static final Logger LOG = Logger.getLogger(ChildService.class);
    /** Timetable weeks are fetched only near today to bound Librus calls. */
    private static final int TIMETABLE_DAYS_BACK = 7;
    private static final int TIMETABLE_DAYS_AHEAD = 21;

    /** A child as shown in the child switcher. */
    public record ChildView(String id, String name, String className, String school) {
    }

    /** The home screen of one child. */
    public record Dashboard(ChildView child, LocalDate today, LocalDate lessonsDay, List<RecordViews.LessonView> lessons,
                            List<RecordViews.AgendaView> upcoming, LuckyNumber luckyNumber, Integer unreadMessages,
                            List<TimelineEntry> recent) {
    }

    private final Children children;
    private final LibrusData data;
    private final TimelineRepository timeline;
    private final AppContext context;

    @Inject
    public ChildService(Children children, LibrusData data, TimelineRepository timeline, AppContext context) {
        this.children = children;
        this.data = data;
        this.timeline = timeline;
        this.context = context;
    }

    public List<ChildView> list() {
        return children.ids().stream().map(this::child).toList();
    }

    /** Child id to display name, in configuration order; Librus failures fall back to the login. */
    public Map<String, String> names() {
        Map<String, String> names = new LinkedHashMap<>();
        children.ids().forEach(id -> names.put(id, safeName(id)));
        return names;
    }

    private String safeName(String id) {
        try {
            return child(id).name();
        } catch (LibrusException e) {
            LOG.warnf("Cannot read name of %s: %s", id, e.getMessage());
            return children.label(id).orElse(id);
        }
    }

    public ChildView child(String id) {
        Directory directory = data.directory(id);
        String name = children.label(id).orElse(directory.me().studentName());
        return new ChildView(id, name, directory.schoolClass().name(), directory.school().name());
    }

    public Dashboard dashboard(String id) {
        LocalDate today = context.today();
        LocalDate lessonsDay = nextSchoolDay(id, today);
        List<RecordViews.LessonView> lessons = RecordViews.lessons(data.directory(id),
                week(id, lessonsDay).stream().filter(l -> l.date().equals(lessonsDay)).toList());
        List<RecordViews.AgendaView> upcoming = agenda(id, today, today.plusDays(7)).stream()
                .filter(i -> i.type() != EntryType.LESSON).map(RecordViews::agenda).toList();
        LuckyNumber lucky = data.records(id).luckyNumber().filter(l -> !l.day().isBefore(today)).orElse(null);
        return new Dashboard(child(id), today, lessonsDay, lessons, upcoming, lucky, unread(id), timeline.findRecent(id, 15));
    }

    private Integer unread(String id) {
        try {
            return data.unreadCounts(id).getOrDefault("inbox", 0);
        } catch (LibrusException e) {
            return null;
        }
    }

    /** Today when it still has lessons ahead, otherwise the next day with lessons (within two weeks). */
    LocalDate nextSchoolDay(String id, LocalDate today) {
        boolean afterSchool = context.now().getHour() >= 16;
        LocalDate day = afterSchool ? today.plusDays(1) : today;
        for (int i = 0; i < 14; i++, day = day.plusDays(1)) {
            LocalDate candidate = day;
            if (week(id, candidate).stream().anyMatch(l -> l.date().equals(candidate))) {
                return candidate;
            }
        }
        return today;
    }

    public List<TimetableEntry> week(String id, LocalDate day) {
        return data.timetable(id, day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    /** Agenda items overlapping {@code [from, to]}; lessons only near today. */
    public List<AgendaItem> agenda(String id, LocalDate from, LocalDate to) {
        LocalDate today = context.today();
        List<TimetableEntry> lessons = new ArrayList<>();
        LocalDate first = max(from, today.minusDays(TIMETABLE_DAYS_BACK));
        LocalDate last = min(to, today.plusDays(TIMETABLE_DAYS_AHEAD));
        for (LocalDate week = monday(first); !week.isAfter(last); week = week.plusWeeks(1)) {
            lessons.addAll(data.timetable(id, week));
        }
        return new AgendaBuilder(id, data.directory(id), context.locale()).build(data.records(id), lessons).stream()
                .filter(i -> i.when().overlaps(from, to))
                .toList();
    }

    public List<GradeViews.SubjectGrades> grades(String id) {
        return GradeViews.bySubject(data.directory(id), data.records(id).grades(), context.scale());
    }

    public RecordViews.AttendanceReport attendance(String id) {
        return RecordViews.attendance(data.directory(id), data.records(id).attendances());
    }

    /** Lessons of the week containing {@code day} (today when null). */
    public List<RecordViews.LessonView> timetable(String id, LocalDate day) {
        return RecordViews.lessons(data.directory(id), week(id, day == null ? context.today() : day));
    }

    public List<RecordViews.HomeworkView> homework(String id) {
        return RecordViews.homework(data.directory(id), data.records(id).homework());
    }

    public List<RecordViews.NoteView> notes(String id) {
        return RecordViews.notes(data.directory(id), data.records(id).notes());
    }

    public List<RecordViews.BehaviourView> behaviour(String id) {
        return RecordViews.behaviour(data.directory(id), data.records(id).behaviour());
    }

    public List<RecordViews.AnnouncementView> announcements(String id) {
        return RecordViews.announcements(data.directory(id), data.records(id).announcements());
    }

    public void markAnnouncementRead(String id, String announcementId) {
        children.account(id).markAnnouncementRead(announcementId);
        data.forgetRecords();
    }

    public List<TimelineEntry> timeline(String id, int limit) {
        return timeline.findRecent(id, Math.clamp(limit, 1, 500));
    }

    private static LocalDate monday(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
