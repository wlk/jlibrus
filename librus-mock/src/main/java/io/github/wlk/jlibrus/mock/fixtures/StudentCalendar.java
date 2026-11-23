package io.github.wlk.jlibrus.mock.fixtures;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.wlk.jlibrus.mock.MockAccount;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Per-child dated data relative to "today": timetable, calendar events, homework, meetings, days off. */
public final class StudentCalendar {

    private final MockAccount account;
    private final LocalDate today;

    public StudentCalendar(MockAccount account, LocalDate today) {
        this.account = account;
        this.today = today;
    }

    /** Week starting at {@code weekStart}; the current week has one cancellation and one substitution. */
    public ObjectNode timetable(LocalDate weekStart) {
        LocalDate monday = weekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean currentWeek = monday.equals(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        ObjectNode days = Nodes.object();
        for (int d = 0; d < 7; d++) {
            LocalDate day = monday.plusDays(d);
            ArrayNode slots = days.putArray(day.toString());
            slots.addArray();
            List<SchoolFixtures.Course> plan = SchoolFixtures.plan(day);
            for (int slot = 0; slot < plan.size(); slot++) {
                slots.addArray().add(lesson(day, slot + 1, plan.get(slot), currentWeek));
            }
        }
        ObjectNode root = Nodes.object();
        root.set("Timetable", days);
        return root;
    }

    private ObjectNode lesson(LocalDate day, int lessonNo, SchoolFixtures.Course course, boolean currentWeek) {
        boolean cancelled = currentWeek && day.getDayOfWeek() == DayOfWeek.WEDNESDAY && lessonNo == 5;
        boolean substituted = currentWeek && day.getDayOfWeek() == DayOfWeek.THURSDAY && lessonNo == 2;
        long teacher = substituted ? 1009 : course.teacherId();
        ObjectNode l = Nodes.object().put("LessonNo", String.valueOf(lessonNo))
                .put("HourFrom", SchoolFixtures.BELLS[lessonNo][0].toString())
                .put("HourTo", SchoolFixtures.BELLS[lessonNo][1].toString())
                .put("IsCanceled", cancelled).put("IsSubstitutionClass", substituted);
        l.set("Subject", Nodes.object().put("Id", String.valueOf(course.subjectId())).put("Name", course.name()));
        l.set("Teacher", teacherRef(teacher));
        l.set("Classroom", Nodes.object().put("Id", String.valueOf(course.roomId())));
        l.set("Class", Nodes.object().put("Id", String.valueOf(7000 + account.seed())));
        if (substituted) {
            l.put("OrgDate", day.toString()).put("OrgLessonNo", String.valueOf(lessonNo));
            l.set("OrgSubject", Nodes.object().put("Id", String.valueOf(course.subjectId())).put("Name", course.name()));
            l.set("OrgTeacher", teacherRef(course.teacherId()));
        }
        return l;
    }

    private static ObjectNode teacherRef(long id) {
        SchoolFixtures.Staff staff = SchoolFixtures.staff(id);
        return Nodes.object().put("Id", String.valueOf(id)).put("FirstName", staff.first()).put("LastName", staff.last());
    }

    public ObjectNode events() {
        ArrayNode items = Nodes.array();
        event(items, 1, "Sprawdzian – ułamki zwykłe i dziesiętne", today.plusDays(2), 9001, 2001L, 1001, 3, 0);
        event(items, 2, "Kartkówka – słownictwo unit 4", today.plusDays(5), 9002, 2003L, 1003, 2, 1);
        event(items, 3, "Wycieczka do Muzeum Narodowego", today.plusDays(9), 9003, null, 1001, null, 3);
        event(items, 4, "Kartkówka – średniowiecze", today.plusDays(1), 9002, 2005L, 1005, 4, 0);
        event(items, 5, "Sprawdzian – ortografia", today.minusDays(3), 9001, 2002L, 1002, 2, 10);
        return Nodes.root("HomeWorks", items);
    }

    private void event(ArrayNode items, long n, String content, LocalDate date, long category, Long subject,
                       long teacher, Integer lessonNo, int addedDaysAgo) {
        ObjectNode e = items.addObject().put("Id", n * 1000 + date.toEpochDay() % 1000).put("Content", content)
                .put("Date", date.toString())
                .put("AddDate", Nodes.timestamp(today.minusDays(addedDaysAgo).atTime(14, 5)));
        e.set("Category", Nodes.ref(category, "HomeWorks/Categories"));
        e.set("CreatedBy", Nodes.ref(teacher, "Users"));
        if (subject != null) {
            e.set("Subject", Nodes.ref(subject, "Subjects"));
        }
        if (lessonNo == null) {
            e.putNull("LessonNo").put("TimeFrom", "08:00:00").put("TimeTo", "13:00:00");
        } else {
            e.put("LessonNo", lessonNo)
                    .put("TimeFrom", SchoolFixtures.BELLS[lessonNo][0] + ":00")
                    .put("TimeTo", SchoolFixtures.BELLS[lessonNo][1] + ":00");
        }
    }

    public ObjectNode homework() {
        ArrayNode items = Nodes.array();
        ObjectNode first = items.addObject().put("Id", 100 + today.toEpochDay() % 1000).put("Topic", "Zadania z podręcznika")
                .put("Text", "Strona 42, zadania 1-5.").put("Date", today.toString())
                .put("DueDate", today.plusDays(2).toString());
        first.set("Teacher", Nodes.ref(1001, "Users"));
        ObjectNode second = items.addObject().put("Id", 7).put("Topic", "Karta pracy")
                .put("Text", "Uzupełnij kartę pracy o średniowieczu.").put("Date", today.minusDays(4).toString())
                .put("DueDate", today.plusDays(3).toString());
        second.set("Teacher", Nodes.ref(1005, "Users"));
        second.putArray("HomeworkAssigmentFiles").addObject().put("Id", 31).put("Name", "karta-pracy.pdf");
        return Nodes.root("HomeWorkAssignments", items);
    }

    public ObjectNode parentMeetings() {
        ArrayNode items = Nodes.array();
        ObjectNode m = items.addObject().put("Id", 3359).put("Name", "Zebranie z rodzicami")
                .put("Topic", "Omówienie wyników śródrocznych").put("Room", "14")
                .put("Date", today.plusDays(6).toString()).put("Time", "17:00:00");
        m.set("Teacher", Nodes.ref(1001, "Users"));
        return Nodes.root("ParentTeacherConferences", items);
    }

    public ObjectNode schoolFreeDays() {
        ArrayNode items = Nodes.array();
        LocalDate christmas = LocalDate.of(today.getYear(), 12, 23);
        items.addObject().put("Id", 1).put("Name", "Zimowa przerwa świąteczna")
                .put("DateFrom", christmas.toString()).put("DateTo", christmas.plusDays(8).toString());
        items.addObject().put("Id", 2).put("Name", "Dzień wolny od zajęć dydaktycznych")
                .put("DateFrom", today.plusDays(12).toString()).put("DateTo", today.plusDays(12).toString());
        return Nodes.root("SchoolFreeDays", items);
    }

    public ObjectNode teacherAbsences() {
        ArrayNode items = Nodes.array();
        ObjectNode absence = items.addObject().put("Id", 4401).put("DateFrom", today.plusDays(1).toString())
                .put("DateTo", today.plusDays(1).toString());
        absence.set("Teacher", Nodes.ref(1003, "Users"));
        return Nodes.root("TeacherFreeDays", items);
    }
}
