package io.github.wlk.jlibrus.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.http.HttpResult;
import io.github.wlk.jlibrus.client.http.HttpTransport;
import io.github.wlk.jlibrus.client.messages.MessagesClient;
import io.github.wlk.jlibrus.client.model.AccountInfo;
import io.github.wlk.jlibrus.client.model.Announcement;
import io.github.wlk.jlibrus.client.model.Attendance;
import io.github.wlk.jlibrus.client.model.AttendanceType;
import io.github.wlk.jlibrus.client.model.BehaviourPoint;
import io.github.wlk.jlibrus.client.model.CalendarEvent;
import io.github.wlk.jlibrus.client.model.DescriptiveGrade;
import io.github.wlk.jlibrus.client.model.Justification;
import io.github.wlk.jlibrus.client.model.SchoolTrip;
import io.github.wlk.jlibrus.client.model.Classroom;
import io.github.wlk.jlibrus.client.model.EventCategory;
import io.github.wlk.jlibrus.client.model.FreeDay;
import io.github.wlk.jlibrus.client.model.Grade;
import io.github.wlk.jlibrus.client.model.GradeCategory;
import io.github.wlk.jlibrus.client.model.GradeComment;
import io.github.wlk.jlibrus.client.model.Homework;
import io.github.wlk.jlibrus.client.model.LessonAssignment;
import io.github.wlk.jlibrus.client.model.LuckyNumber;
import io.github.wlk.jlibrus.client.model.Note;
import io.github.wlk.jlibrus.client.model.NoteCategory;
import io.github.wlk.jlibrus.client.model.ParentMeeting;
import io.github.wlk.jlibrus.client.model.Person;
import io.github.wlk.jlibrus.client.model.School;
import io.github.wlk.jlibrus.client.model.SchoolClass;
import io.github.wlk.jlibrus.client.model.Subject;
import io.github.wlk.jlibrus.client.model.TeacherAbsence;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import io.github.wlk.jlibrus.client.parse.CalendarParser;
import io.github.wlk.jlibrus.client.parse.DictionaryParser;
import io.github.wlk.jlibrus.client.parse.ExtraParser;
import io.github.wlk.jlibrus.client.parse.RecordParser;
import io.github.wlk.jlibrus.client.parse.TimetableParser;
import io.github.wlk.jlibrus.client.session.Gateway;
import io.github.wlk.jlibrus.client.session.SynergiaSession;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Entry point: one Librus Synergia account (one child). Thread-safe; logs in lazily on first use and keeps the
 * session alive. Every call goes to Librus - cache results in your application.
 *
 * <pre>{@code
 * LibrusAccount account = LibrusAccount.builder(new Credentials("1234567u", "secret")).build();
 * List<Grade> grades = account.grades();
 * }</pre>
 */
public final class LibrusAccount {

    /** Never reconfigured after creation, so one thread-safe mapper serves every account. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SynergiaSession session;
    private final Gateway gateway;
    private final MessagesClient messages;
    private final DescriptiveGradesReader descriptive;

    private LibrusAccount(Builder builder) {
        HttpTransport http = new HttpTransport(builder.userAgent, builder.connectTimeout, builder.requestTimeout);
        this.session = new SynergiaSession(builder.credentials, builder.endpoints, http, JSON, builder.clock);
        this.gateway = new Gateway(session, JSON);
        this.messages = new MessagesClient(session, JSON);
        this.descriptive = new DescriptiveGradesReader(gateway);
    }

    public static Builder builder(Credentials credentials) {
        return new Builder(credentials);
    }

    public String login() {
        return session.login();
    }

    /** Logs in now (instead of on first call) - useful to validate credentials at startup. */
    public void connect() {
        session.ensureLoggedIn();
    }

    public AccountInfo me() {
        return DictionaryParser.me(gateway.get("Me"));
    }

    public School school() {
        return DictionaryParser.school(gateway.get("Schools"));
    }

    public SchoolClass schoolClass() {
        return DictionaryParser.schoolClass(gateway.get("Classes"));
    }

    public List<Person> users() {
        return DictionaryParser.users(gateway.get("Users"));
    }

    public List<Subject> subjects() {
        return DictionaryParser.subjects(gateway.get("Subjects"));
    }

    public List<Classroom> classrooms() {
        return DictionaryParser.classrooms(gateway.getOrMissing("Classrooms"));
    }

    public List<LessonAssignment> lessons() {
        return DictionaryParser.lessons(gateway.get("Lessons"));
    }

    public List<GradeCategory> gradeCategories() {
        return DictionaryParser.gradeCategories(gateway.get("Grades/Categories"));
    }

    public List<GradeComment> gradeComments() {
        return DictionaryParser.gradeComments(gateway.getOrMissing("Grades/Comments"));
    }

    public List<AttendanceType> attendanceTypes() {
        return DictionaryParser.attendanceTypes(gateway.get("Attendances/Types"));
    }

    public List<EventCategory> eventCategories() {
        return DictionaryParser.eventCategories(gateway.getOrMissing("HomeWorks/Categories"));
    }

    public List<NoteCategory> noteCategories() {
        return DictionaryParser.noteCategories(gateway.getOrMissing("Notes/Categories"));
    }

    public List<Grade> grades() {
        return RecordParser.grades(gateway.get("Grades"));
    }

    public List<BehaviourPoint> behaviour() {
        return RecordParser.behaviourPoints(gateway.getOrMissing("BehaviourGrades/Points"));
    }

    public List<Attendance> attendances() {
        return RecordParser.attendances(gateway.get("Attendances"));
    }

    public List<Note> notes() {
        return RecordParser.notes(gateway.getOrMissing("Notes"));
    }

    public List<Announcement> announcements() {
        return RecordParser.announcements(gateway.getOrMissing("SchoolNotices"));
    }

    public Optional<LuckyNumber> luckyNumber() {
        return RecordParser.luckyNumber(gateway.getOrMissing("LuckyNumbers"));
    }

    /** Lessons of the week containing {@code day}; empty when the timetable is not published. */
    public List<TimetableEntry> timetable(LocalDate day) {
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return TimetableParser.week(gateway.getOrMissing("Timetables?weekStart=" + monday));
    }

    /** Calendar ("terminarz"): tests, quizzes, trips and other events. */
    public List<CalendarEvent> events() {
        return CalendarParser.events(gateway.getOrMissing("HomeWorks"));
    }

    public List<Homework> homework() {
        return CalendarParser.homework(gateway.getOrMissing("HomeWorkAssignments"));
    }

    public List<ParentMeeting> parentMeetings() {
        return CalendarParser.parentMeetings(gateway.getOrMissing("ParentTeacherConferences"));
    }

    public List<FreeDay> schoolFreeDays() {
        return CalendarParser.schoolFreeDays(gateway.getOrMissing("SchoolFreeDays"));
    }

    public List<TeacherAbsence> teacherAbsences() {
        return CalendarParser.teacherAbsences(gateway.getOrMissing("TeacherFreeDays"));
    }

    /** Absence justifications the parent submitted, newest first, with the school's decision. */
    public List<Justification> justifications() {
        return ExtraParser.justifications(gateway.getOrMissing("Justifications"));
    }

    /** School trips registered by the school, soonest first. */
    public List<SchoolTrip> schoolTrips() {
        return ExtraParser.schoolTrips(gateway.getOrMissing("SchoolTrips"));
    }

    /**
     * Descriptive grades of both Librus systems: the classic one (skills, values on the school scale, comments)
     * and the newer partial grades (grade 1 from 2026). Empty when the school uses neither.
     */
    public List<DescriptiveGrade> descriptiveGrades() {
        return descriptive.read();
    }

    /** Marks an announcement read; already-read announcements are not an error. */
    public void markAnnouncementRead(String announcementId) {
        if (!Identifiers.isSafeId(announcementId)) {
            throw new IllegalArgumentException("Invalid announcement id");
        }
        HttpResult result = gateway.post("SchoolNotices/MarkAsRead/" + announcementId);
        if (!isMarkedRead(result)) {
            throw new LibrusException.ProtocolError("Cannot mark announcement read (HTTP " + result.status() + ")");
        }
    }

    /** Librus answers 400 InvalidRequest for a notice that was already read. */
    static boolean isMarkedRead(HttpResult result) {
        return result.isSuccess() || (result.status() == 400 && result.body().contains("InvalidRequest"));
    }

    /** The messages module of this account. */
    public MessagesClient messages() {
        return messages;
    }

    /** Configuration of a {@link LibrusAccount}; defaults talk to production Librus. */
    public static final class Builder {
        private final Credentials credentials;
        private LibrusEndpoints endpoints = LibrusEndpoints.production();
        private String userAgent = HttpTransport.DEFAULT_USER_AGENT;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private Clock clock = Clock.systemUTC();

        private Builder(Credentials credentials) {
            this.credentials = Objects.requireNonNull(credentials, "credentials");
        }

        public Builder endpoints(LibrusEndpoints value) {
            this.endpoints = Objects.requireNonNull(value);
            return this;
        }

        public Builder userAgent(String value) {
            this.userAgent = Objects.requireNonNull(value);
            return this;
        }

        public Builder timeouts(Duration connect, Duration request) {
            this.connectTimeout = Objects.requireNonNull(connect);
            this.requestTimeout = Objects.requireNonNull(request);
            return this;
        }

        public Builder clock(Clock value) {
            this.clock = Objects.requireNonNull(value);
            return this;
        }

        public LibrusAccount build() {
            return new LibrusAccount(this);
        }
    }
}
