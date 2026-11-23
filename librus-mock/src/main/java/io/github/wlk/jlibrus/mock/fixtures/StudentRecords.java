package io.github.wlk.jlibrus.mock.fixtures;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.wlk.jlibrus.mock.MockAccount;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

/**
 * Per-child records generated relative to "today": a day's grades and attendance are a deterministic function
 * of the date, so data grows naturally day by day and new items keep appearing in demo mode.
 */
public final class StudentRecords {

    private static final String[] MARKS = {"5", "4+", "3", "6", "4", "5-", "2+", "4-", "np", "+", "5", "3+"};
    private static final long[] CATEGORIES = {10, 11, 12, 13, 14};
    private static final int HISTORY_DAYS = 75;

    private final MockAccount account;
    private final LocalDate today;

    public StudentRecords(MockAccount account, LocalDate today) {
        this.account = account;
        this.today = today;
    }

    public ObjectNode me() {
        ObjectNode me = Nodes.object();
        me.putObject("Account").put("Id", 50_000 + account.seed()).put("UserId", 60_000 + account.seed())
                .put("FirstName", "Małgorzata").put("LastName", account.studentLastName())
                .put("Login", account.login()).put("GroupId", 5).put("IsPremium", false).put("IsPremiumDemo", false);
        me.putObject("User").put("FirstName", account.studentFirstName()).put("LastName", account.studentLastName());
        me.put("Refresh", 900);
        ObjectNode root = Nodes.object();
        root.set("Me", me);
        return root;
    }

    public ObjectNode schoolClass() {
        int startYear = today.getMonthValue() >= Month.AUGUST.getValue() ? today.getYear() : today.getYear() - 1;
        ObjectNode c = Nodes.object().put("Id", 7000 + account.seed()).put("Number", account.classNumber())
                .put("Symbol", account.classSymbol())
                .put("BeginSchoolYear", LocalDate.of(startYear, 9, 1).toString())
                .put("EndFirstSemester", LocalDate.of(startYear + 1, 1, 25).toString())
                .put("EndSchoolYear", LocalDate.of(startYear + 1, 6, 26).toString());
        c.set("ClassTutor", Nodes.ref(1001, "Users"));
        ObjectNode root = Nodes.object();
        root.set("Class", c);
        return root;
    }

    int semester(LocalDate day) {
        int month = day.getMonthValue();
        return month >= 2 && month <= 7 ? 2 : 1;
    }

    public ObjectNode grades() {
        ArrayNode items = Nodes.array();
        for (int back = HISTORY_DAYS; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            List<SchoolFixtures.Course> plan = SchoolFixtures.plan(day);
            for (int slot = 0; slot < plan.size(); slot++) {
                if (Nodes.pick(7, day.toEpochDay(), slot, account.seed()) == 0) {
                    items.add(grade(day, slot, plan.get(slot)));
                }
            }
        }
        return Nodes.root("Grades", items);
    }

    private ObjectNode grade(LocalDate day, int slot, SchoolFixtures.Course course) {
        long id = day.toEpochDay() * 100 + slot;
        ObjectNode g = Nodes.object().put("Id", id)
                .put("Grade", MARKS[Nodes.pick(MARKS.length, id, account.seed())])
                .put("Semester", semester(day))
                .put("AddDate", Nodes.timestamp(day.atTime(SchoolFixtures.BELLS[slot + 1][1])))
                .put("IsConstituent", true).put("IsSemester", false).put("IsSemesterProposition", false)
                .put("IsFinal", false).put("IsFinalProposition", false);
        g.set("Category", Nodes.ref(CATEGORIES[Nodes.pick(CATEGORIES.length, id)], "Grades/Categories"));
        g.set("Subject", Nodes.ref(course.subjectId(), "Subjects"));
        g.set("AddedBy", Nodes.ref(course.teacherId(), "Users"));
        if (Nodes.pick(4, id) == 0) {
            g.putArray("Comments").add(Nodes.ref(id, "Grades/Comments"));
        }
        return g;
    }

    public ObjectNode gradeComments() {
        ArrayNode items = Nodes.array();
        grades().path("Grades").forEach(g -> {
            if (g.has("Comments")) {
                items.addObject().put("Id", g.path("Id").asLong()).put("Text", "Komentarz nauczyciela do oceny");
            }
        });
        return Nodes.root("Comments", items);
    }

    public ObjectNode attendances() {
        ArrayNode items = Nodes.array();
        for (int back = 30; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            List<SchoolFixtures.Course> plan = SchoolFixtures.plan(day);
            for (int slot = 0; slot < plan.size(); slot++) {
                items.add(attendance(day, slot, plan.get(slot)));
            }
        }
        return Nodes.root("Attendances", items);
    }

    private ObjectNode attendance(LocalDate day, int slot, SchoolFixtures.Course course) {
        int roll = Nodes.pick(40, day.toEpochDay(), slot, account.seed(), 3);
        long type = roll == 0 ? 1 : roll == 1 ? 2 : roll == 2 ? 3 : 100;
        ObjectNode a = Nodes.object().put("Id", "t" + (day.toEpochDay() * 10 + slot))
                .put("Date", day.toString()).put("LessonNo", slot + 1).put("Semester", semester(day))
                .put("AddDate", Nodes.timestamp(day.atTime(SchoolFixtures.BELLS[slot + 1][0].plusMinutes(5))));
        a.set("Lesson", Nodes.ref(SchoolFixtures.lessonId(course), "Lessons"));
        a.set("Type", Nodes.ref(type, "Attendances/Types"));
        a.set("AddedBy", Nodes.ref(course.teacherId(), "Users"));
        return a;
    }

    public ObjectNode notes() {
        ArrayNode items = Nodes.array();
        note(items, 1, "Wzorowa pomoc koledze podczas lekcji.", today.minusDays(12), 1, 855, 1004);
        note(items, 2, "Rozmowy podczas sprawdzianu.", today.minusDays(1), 0, 856, 1002);
        return Nodes.root("Notes", items);
    }

    private void note(ArrayNode items, long id, String text, LocalDate date, int positive, long category, long teacher) {
        ObjectNode n = items.addObject().put("Id", id * 100 + account.seed()).put("Text", text)
                .put("Date", date.toString()).put("Positive", positive).put("Public", true);
        n.set("Category", Nodes.ref(category, "Notes/Categories"));
        n.set("Teacher", Nodes.ref(teacher, "Users"));
    }

    public ObjectNode behaviourPoints() {
        ArrayNode items = Nodes.array();
        ObjectNode monthly = items.addObject().put("Id", 1).put("Semester", semester(today))
                .put("AddDate", Nodes.timestamp(today.withDayOfMonth(1).atTime(12, 0)))
                .put("Text", "Ocena zachowania za poprzedni miesiąc");
        monthly.set("BehaviourGrade", Nodes.ref(2, "BehaviourGrades/Types"));
        monthly.set("AddedBy", Nodes.ref(1001, "Users"));
        ObjectNode points = items.addObject().put("Id", 2).put("Value", 5).put("ShortName", "+5")
                .put("Semester", semester(today)).put("AddDate", Nodes.timestamp(today.minusDays(6).atTime(10, 0)))
                .put("Text", "Udział w konkursie");
        points.set("AddedBy", Nodes.ref(1004, "Users"));
        return Nodes.root("Grades", items);
    }

    public ObjectNode announcements() {
        ArrayNode items = Nodes.array();
        notice(items, "nb-" + today, "Zbiórka makulatury",
                "W przyszłym tygodniu zbieramy makulaturę. Prosimy o przynoszenie jej do sali 14.", today, false);
        notice(items, "nb-open-day", "Dzień otwarty szkoły",
                "Zapraszamy rodziców na dzień otwarty w sobotę.\nProgram: prezentacje klas, kiermasz.",
                today.minusDays(5), false);
        notice(items, "nb-rules", "Nowy regulamin szkoły", "Nowa wersja regulaminu jest dostępna w sekretariacie.",
                today.minusDays(40), true);
        return Nodes.root("SchoolNotices", items);
    }

    private void notice(ArrayNode items, String id, String subject, String content, LocalDate created, boolean read) {
        ObjectNode n = items.addObject().put("Id", id).put("Subject", subject).put("Content", content)
                .put("StartDate", created.toString()).put("EndDate", created.plusDays(30).toString())
                .put("CreationDate", Nodes.timestamp(created.atTime(7, 30))).put("WasRead", read);
        n.set("AddedBy", Nodes.ref(1011, "Users"));
    }

    public ObjectNode luckyNumber() {
        LocalDate day = today;
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        ObjectNode root = Nodes.object();
        root.putObject("LuckyNumber").put("LuckyNumber", 1 + Nodes.pick(28, day.toEpochDay()))
                .put("LuckyNumberDay", day.toString());
        return root;
    }
}
