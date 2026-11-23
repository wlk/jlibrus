package io.github.wlk.jlibrus.server.api;

import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.view.GradeViews;
import io.github.wlk.jlibrus.server.domain.view.RecordViews;
import io.github.wlk.jlibrus.server.service.ChildService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Read API for one child's Librus data. */
@Path("/api/children")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ChildrenResource {

    static final int MAX_RANGE_DAYS = 400;

    /** Notes and behaviour together. */
    public record Behaviour(List<RecordViews.NoteView> notes, List<RecordViews.BehaviourView> points) {
    }

    @Inject
    ChildService children;

    @GET
    public List<ChildService.ChildView> list() {
        return children.list();
    }

    @GET
    @Path("/{id}/dashboard")
    public ChildService.Dashboard dashboard(@PathParam("id") String id) {
        return children.dashboard(id);
    }

    @GET
    @Path("/{id}/grades")
    public List<GradeViews.SubjectGrades> grades(@PathParam("id") String id) {
        return children.grades(id);
    }

    @GET
    @Path("/{id}/descriptive-grades")
    public List<GradeViews.DescriptiveSubject> descriptiveGrades(@PathParam("id") String id) {
        return children.descriptiveGrades(id);
    }

    @GET
    @Path("/{id}/trips")
    public List<RecordViews.TripView> trips(@PathParam("id") String id) {
        return children.trips(id);
    }

    @GET
    @Path("/{id}/attendance")
    public RecordViews.AttendanceReport attendance(@PathParam("id") String id) {
        return children.attendance(id);
    }

    @GET
    @Path("/{id}/timetable")
    public List<RecordViews.LessonView> timetable(@PathParam("id") String id, @QueryParam("date") String date) {
        return children.timetable(id, date == null ? null : parse(date));
    }

    @GET
    @Path("/{id}/agenda")
    public List<RecordViews.AgendaView> agenda(@PathParam("id") String id, @QueryParam("from") String from,
                                               @QueryParam("to") String to) {
        LocalDate start = parse(from);
        LocalDate end = parse(to);
        if (end.isBefore(start) || end.isAfter(start.plusDays(MAX_RANGE_DAYS))) {
            throw new IllegalArgumentException("Invalid date range");
        }
        return children.agenda(id, start, end).stream().map(RecordViews::agenda).toList();
    }

    @GET
    @Path("/{id}/homework")
    public List<RecordViews.HomeworkView> homework(@PathParam("id") String id) {
        return children.homework(id);
    }

    @GET
    @Path("/{id}/behaviour")
    public Behaviour behaviour(@PathParam("id") String id) {
        return new Behaviour(children.notes(id), children.behaviour(id));
    }

    @GET
    @Path("/{id}/announcements")
    public List<RecordViews.AnnouncementView> announcements(@PathParam("id") String id) {
        return children.announcements(id);
    }

    @POST
    @Path("/{id}/announcements/{announcementId}/read")
    public void markRead(@PathParam("id") String id, @PathParam("announcementId") String announcementId) {
        children.markAnnouncementRead(id, announcementId);
    }

    @GET
    @Path("/{id}/timeline")
    public List<TimelineEntry> timeline(@PathParam("id") String id, @QueryParam("limit") @DefaultValue("100") int limit) {
        return children.timeline(id, limit);
    }

    static LocalDate parse(String date) {
        if (date == null) {
            throw new IllegalArgumentException("Date is required (yyyy-MM-dd)");
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date, expected yyyy-MM-dd");
        }
    }
}
