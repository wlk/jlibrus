package io.github.wlk.jlibrus.mock.fixtures;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.wlk.jlibrus.mock.MockAccount;

import java.time.LocalDate;

/**
 * Justifications, school trips and descriptive grades. Children in classes 1-4 get descriptive grades in both
 * Librus systems; older children get none (as in real schools).
 */
public final class StudentExtras {

    private final MockAccount account;
    private final LocalDate today;

    public StudentExtras(MockAccount account, LocalDate today) {
        this.account = account;
        this.today = today;
    }

    private boolean youngerPupil() {
        return account.classNumber() <= 4;
    }

    public ObjectNode justifications() {
        ArrayNode data = Nodes.array();
        LocalDate absence = today.minusDays(10);
        ObjectNode accepted = data.addObject().put("id", 300 + account.seed())
                .put("messageFromParent", "Proszę o usprawiedliwienie nieobecności - wizyta u lekarza.")
                .put("postDate", Nodes.timestamp(absence.plusDays(1).atTime(20, 15)))
                .put("justificationStatus", "accept").put("attachment", false)
                .put("dateFrom", absence.toString()).put("dateTo", absence.toString()).put("justifiedAbsences", 2);
        accepted.putArray("lessons").addObject().put("number", 1).put("date", absence.toString());
        accepted.putArray("notifiedTeachers").addObject().put("name", "Anna Nowak");
        ObjectNode pending = data.addObject().put("id", 400 + account.seed())
                .put("messageFromParent", "Choroba.")
                .put("postDate", Nodes.timestamp(today.minusDays(1).atTime(19, 0)))
                .put("justificationStatus", "new").put("attachment", true)
                .put("dateFrom", today.minusDays(3).toString()).put("dateTo", today.minusDays(2).toString())
                .put("justifiedAbsences", 0);
        pending.putArray("lessons");
        pending.putArray("notifiedTeachers");
        ObjectNode root = Nodes.object().put("status", "OK").put("message", "Pobrano listę usprawiedliwień");
        root.set("data", data);
        return root;
    }

    public ObjectNode schoolTrips() {
        ArrayNode data = Nodes.array();
        data.addObject().put("id", 2).put("destination", "Teatr Lalek").put("route", "Szkoła - Teatr - Szkoła")
                .put("locomotion", "autokar").put("termFrom", today.plusDays(16).toString())
                .put("termTo", today.plusDays(16).toString()).put("coordinatorName", "Nowak Anna");
        data.addObject().put("id", 1).put("destination", "Zielona szkoła").put("route", "Szkoła - Zakopane")
                .put("locomotion", "pociąg").put("termFrom", today.plusDays(40).toString())
                .put("termTo", today.plusDays(44).toString()).put("creatorName", "Ewa").put("creatorLastName", "Lis");
        ObjectNode root = Nodes.object();
        root.set("Data", data);
        return root;
    }

    public ObjectNode descriptiveGrades() {
        ArrayNode grades = Nodes.array();
        if (youngerPupil()) {
            grade(grades, 1, 2008, 501, 1008, "6", today.minusDays(12), true);
            grade(grades, 2, 2007, 502, 1007, "5", today, false);
        }
        return Nodes.root("Grades", grades);
    }

    private void grade(ArrayNode grades, long id, long subject, long skill, long teacher, String value, LocalDate date,
                       boolean comment) {
        ObjectNode g = grades.addObject().put("Id", id).put("Grade", 3).put("Map", value).put("RealGradeValue", value)
                .put("Date", date.toString()).put("AddDate", Nodes.timestamp(date.atTime(11, 20))).put("Semester", 1);
        g.set("Subject", Nodes.ref(subject, "Subjects"));
        g.set("Skill", Nodes.ref(skill, "DescriptiveGrades/Skills"));
        g.set("AddedBy", Nodes.ref(teacher, "Users"));
        ArrayNode comments = g.putArray("Comments");
        if (comment) {
            comments.add(Nodes.ref(44, "DescriptiveGrades/Comments"));
        }
    }

    public static ObjectNode skills() {
        ArrayNode skills = Nodes.array();
        skills.addObject().put("Id", 501).put("Name", "Ekspresja muzyczna. Śpiew");
        skills.addObject().put("Id", 502).put("Name", "Działalność plastyczna");
        return Nodes.root("Skills", skills);
    }

    public static ObjectNode comments() {
        ArrayNode comments = Nodes.array();
        comments.addObject().put("Id", 44).put("Text", "Pięknie śpiewa i zna słowa piosenek.");
        return Nodes.root("Comments", comments);
    }

    public static ObjectNode authSubjects() {
        ArrayNode data = Nodes.array();
        SchoolFixtures.COURSES.forEach(c -> data.addObject().put("identifier", "LID-SUBJ-" + c.subjectId())
                .put("numericIdentifier", c.subjectId()).put("name", c.name()));
        ObjectNode root = Nodes.object();
        root.set("data", data);
        return root;
    }

    public ObjectNode partialGrades() {
        ArrayNode data = Nodes.array();
        if (youngerPupil()) {
            ObjectNode g = data.addObject().put("gradeId", 11).put("subjectId", "LID-SUBJ-2002")
                    .put("content", "Czyta płynnie i ze zrozumieniem.").put("date", today.minusDays(3).toString())
                    .put("addDate", Nodes.timestamp(today.minusDays(3).atTime(10, 0))).put("semester", 1);
            g.putObject("area").put("name", "Czytanie");
            g.putObject("scaleValue").put("value", "B");
            g.putArray("comments").addObject().put("content", "Brawo!");
            g.putArray("implementedRequirements").addObject().put("name", "Czyta teksty dla dzieci");
        }
        ObjectNode root = Nodes.object();
        root.set("data", data);
        root.putObject("pagination").put("limit", 50).put("page", 1).put("total", data.size());
        return root;
    }
}
