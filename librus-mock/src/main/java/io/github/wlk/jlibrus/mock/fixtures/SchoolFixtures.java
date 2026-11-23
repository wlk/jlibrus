package io.github.wlk.jlibrus.mock.fixtures;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** The fictional school: staff, subjects, rooms, categories and the weekly plan. Same for every child. */
public final class SchoolFixtures {

    record Staff(long id, String first, String last) {
    }

    record Course(long subjectId, String name, String shortName, long teacherId, long roomId) {
    }

    record Room(long id, String symbol, String name) {
    }

    static final List<Staff> STAFF = List.of(
            new Staff(1001, "Anna", "Nowak"), new Staff(1002, "Piotr", "Wiśniewski"),
            new Staff(1003, "Marek", "Zieliński"), new Staff(1004, "Ewa", "Lis"),
            new Staff(1005, "Tomasz", "Wójcik"), new Staff(1006, "Katarzyna", "Kamińska"),
            new Staff(1007, "Magdalena", "Lewandowska"), new Staff(1008, "Krzysztof", "Dąbrowski"),
            new Staff(1009, "Joanna", "Szymańska"), new Staff(1010, "Agnieszka", "Woźniak"),
            new Staff(1011, "Barbara", "Kozłowska"));

    static final List<Course> COURSES = List.of(
            new Course(2001, "Matematyka", "mat", 1001, 4001),
            new Course(2002, "Język polski", "pol", 1002, 4002),
            new Course(2003, "Język angielski", "ang", 1003, 4003),
            new Course(2004, "Przyroda", "prz", 1004, 4004),
            new Course(2005, "Historia", "his", 1005, 4002),
            new Course(2006, "Wychowanie fizyczne", "wf", 1006, 4005),
            new Course(2007, "Plastyka", "pla", 1007, 4006),
            new Course(2008, "Muzyka", "muz", 1008, 4006),
            new Course(2009, "Informatyka", "inf", 1009, 4007),
            new Course(2010, "Religia", "rel", 1010, 4002));

    static final LocalTime[][] BELLS = {
            {null, null},
            {LocalTime.of(8, 0), LocalTime.of(8, 45)}, {LocalTime.of(8, 55), LocalTime.of(9, 40)},
            {LocalTime.of(9, 50), LocalTime.of(10, 35)}, {LocalTime.of(10, 45), LocalTime.of(11, 30)},
            {LocalTime.of(11, 50), LocalTime.of(12, 35)}, {LocalTime.of(12, 45), LocalTime.of(13, 30)},
            {LocalTime.of(13, 40), LocalTime.of(14, 25)}};

    /** Course indexes per weekday (Monday first), lesson 1 onwards. */
    private static final int[][] PLAN = {
            {0, 1, 2, 3, 5, 4},
            {1, 0, 6, 2, 9},
            {0, 3, 1, 8, 5, 7},
            {2, 1, 0, 4, 6},
            {1, 0, 3, 5, 2, 9}};

    private SchoolFixtures() {
    }

    /** Courses planned on a day (empty at weekends). */
    static List<Course> plan(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return List.of();
        }
        int[] indexes = PLAN[dow.getValue() - 1];
        return java.util.Arrays.stream(indexes).mapToObj(COURSES::get).toList();
    }

    static Staff staff(long id) {
        return STAFF.stream().filter(s -> s.id() == id).findFirst().orElseThrow();
    }

    public static ObjectNode users() {
        ArrayNode items = Nodes.array();
        STAFF.forEach(s -> items.addObject().put("Id", s.id()).put("AccountId", "LID-USER-" + s.id())
                .put("FirstName", s.first()).put("LastName", s.last())
                .put("IsSchoolAdministrator", s.id() == 1011).put("IsPedagogue", false));
        return Nodes.root("Users", items);
    }

    public static ObjectNode subjects() {
        ArrayNode items = Nodes.array();
        COURSES.forEach(c -> items.addObject().put("Id", c.subjectId()).put("Name", c.name()).put("Short", c.shortName()));
        return Nodes.root("Subjects", items);
    }

    public static ObjectNode lessons() {
        ArrayNode items = Nodes.array();
        COURSES.forEach(c -> {
            ObjectNode lesson = items.addObject().put("Id", lessonId(c));
            lesson.set("Subject", Nodes.ref(c.subjectId(), "Subjects"));
            lesson.set("Teacher", Nodes.ref(c.teacherId(), "Users"));
        });
        return Nodes.root("Lessons", items);
    }

    static long lessonId(Course course) {
        return course.subjectId() + 1000;
    }

    public static ObjectNode classrooms() {
        ArrayNode items = Nodes.array();
        List<Room> rooms = List.of(new Room(4001, "12", "Sala matematyczna"), new Room(4002, "14", "Sala polonistyczna"),
                new Room(4003, "21", "Sala językowa"), new Room(4004, "h106", "Pracownia przyrodnicza"),
                new Room(4005, "SG", "Sala gimnastyczna"), new Room(4006, "8", "Sala artystyczna"),
                new Room(4007, "31", "Pracownia komputerowa"));
        rooms.forEach(room -> items.addObject().put("Id", room.id()).put("Symbol", room.symbol()).put("Name", room.name()));
        return Nodes.root("Classrooms", items);
    }

    public static ObjectNode school() {
        ObjectNode school = Nodes.object().put("Id", 501).put("Name", "Szkoła Podstawowa nr 7 w Przykładowie")
                .put("Town", "Przykładowo").put("Street", "ul. Szkolna").put("BuildingNumber", "5");
        ArrayNode ranges = school.putArray("LessonsRange");
        for (LocalTime[] bell : BELLS) {
            ObjectNode range = ranges.addObject();
            if (bell[0] == null) {
                range.putNull("From").putNull("To");
            } else {
                range.put("From", bell[0].toString()).put("To", bell[1].toString());
            }
        }
        ObjectNode root = Nodes.object();
        root.set("School", school);
        return root;
    }

    public static ObjectNode gradeCategories() {
        ArrayNode items = Nodes.array();
        category(items, 10, "Sprawdzian", 3, true, 27);
        category(items, 11, "Kartkówka", 2, true, 13);
        category(items, 12, "Odpowiedź ustna", 1, true, 2);
        category(items, 13, "Aktywność", null, false, 16);
        category(items, 14, "Praca domowa", 1, true, 21);
        category(items, 15, "Ocena śródroczna", 0, false, 26);
        return Nodes.root("Categories", items);
    }

    private static void category(ArrayNode items, long id, String name, Integer weight, boolean counts, long color) {
        ObjectNode c = items.addObject().put("Id", id).put("Name", name).put("CountToTheAverage", counts);
        if (weight != null) {
            c.put("Weight", weight);
        }
        c.set("Color", Nodes.ref(color, "Colors"));
    }

    public static ObjectNode attendanceTypes() {
        ArrayNode items = Nodes.array();
        Object[][] types = {{1L, "Nieobecność", "nb", true, false, "FF8A80"}, {2L, "Spóźnienie", "sp", true, true, "FFD180"},
                {3L, "Nieobecność uspr.", "u", true, false, "B9F6CA"}, {4L, "Zwolnienie", "zw", true, false, "CFD8DC"},
                {100L, "Obecność", "ob", true, true, "E0E0E0"}, {1266L, "Wycieczka", "wy", false, true, "A0C4FF"}};
        for (Object[] t : types) {
            ObjectNode type = items.addObject().put("Id", (Long) t[0]).put("Name", (String) t[1]).put("Short", (String) t[2])
                    .put("Standard", (Boolean) t[3]).put("IsPresenceKind", (Boolean) t[4]).put("ColorRGB", (String) t[5]);
            if (!(Boolean) t[3]) {
                type.set("StandardType", Nodes.ref(100, "Attendances/Types"));
            }
        }
        return Nodes.root("Types", items);
    }

    public static ObjectNode eventCategories() {
        ArrayNode items = Nodes.array();
        String[] names = {"Sprawdzian", "Kartkówka", "Wycieczka", "Inne"};
        for (int i = 0; i < names.length; i++) {
            ObjectNode c = items.addObject().put("Id", 9001 + i).put("Name", names[i]);
            c.set("Color", Nodes.ref(27 - i * 5L, "Colors"));
        }
        return Nodes.root("Categories", items);
    }

    public static ObjectNode noteCategories() {
        ArrayNode items = Nodes.array();
        items.addObject().put("Id", 855).put("CategoryName", "Praca na lekcji");
        items.addObject().put("Id", 856).put("CategoryName", "Zachowanie na lekcji");
        return Nodes.root("Categories", items);
    }
}
