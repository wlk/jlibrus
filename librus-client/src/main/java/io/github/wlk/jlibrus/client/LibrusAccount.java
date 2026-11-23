package io.github.wlk.jlibrus.client;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.function.Function;

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

    private final SynergiaSession session;
    private final Gateway gateway;
    private final MessagesClient messages;
    private final DescriptiveGradesReader descriptive;

    private LibrusAccount(Builder builder) {
        ObjectMapper json = new ObjectMapper();
        HttpTransport http = new HttpTransport(builder.userAgent, builder.connectTimeout, builder.requestTimeout);
        this.session = new SynergiaSession(builder.credentials, builder.endpoints, http, json, builder.clock);
        this.gateway = new Gateway(session, json);
        this.messages = new MessagesClient(session, json);
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
        return optional("Classrooms", DictionaryParser::classrooms);
    }

    public List<LessonAssignment> lessons() {
        return DictionaryParser.lessons(gateway.get("Lessons"));
    }

    public List<GradeCategory> gradeCategories() {
        return DictionaryParser.gradeCategories(gateway.get("Grades/Categories"));
    }

    public List<GradeComment> gradeComments() {
        return optional("Grades/Comments", DictionaryParser::gradeComments);
    }

    public List<AttendanceType> attendanceTypes() {
        return DictionaryParser.attendanceTypes(gateway.get("Attendances/Types"));
    }

    public List<EventCategory> eventCategories() {
        return optional("HomeWorks/Categories", DictionaryParser::eventCategories);
    }

    public List<NoteCategory> noteCategories() {
        return optional("Notes/Categories", DictionaryParser::noteCategories);
    }

    public List<Grade> grades() {
        return RecordParser.grades(gateway.get("Grades"));
    }

    public List<BehaviourPoint> behaviour() {
        return optional("BehaviourGrades/Points", RecordParser::behaviourPoints);
    }

    public List<Attendance> attendances() {
        return RecordParser.attendances(gateway.get("Attendances"));
    }

    public List<Note> notes() {
        return optional("Notes", RecordParser::notes);
    }

    public List<Announcement> announcements() {
        return optional("SchoolNotices", RecordParser::announcements);
    }

    public Optional<LuckyNumber> luckyNumber() {
        try {
            return RecordParser.luckyNumber(gateway.get("LuckyNumbers"));
        } catch (LibrusException.ModuleUnavailable e) {
            return Optional.empty();
        }
    }

    /** Lessons of the week containing {@code day}; empty when the timetable is not published. */
    public List<TimetableEntry> timetable(LocalDate day) {
        LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return optional("Timetables?weekStart=" + monday, TimetableParser::week);
    }

    /** Calendar ("terminarz"): tests, quizzes, trips and other events. */
    public List<CalendarEvent> events() {
        return optional("HomeWorks", CalendarParser::events);
    }

    public List<Homework> homework() {
        return optional("HomeWorkAssignments", CalendarParser::homework);
    }

    public List<ParentMeeting> parentMeetings() {
        return optional("ParentTeacherConferences", CalendarParser::parentMeetings);
    }

    public List<FreeDay> schoolFreeDays() {
        return optional("SchoolFreeDays", CalendarParser::schoolFreeDays);
    }

    public List<TeacherAbsence> teacherAbsences() {
        return optional("TeacherFreeDays", CalendarParser::teacherAbsences);
    }

    /** Absence justifications the parent submitted, newest first, with the school's decision. */
    public List<Justification> justifications() {
        return optional("Justifications", ExtraParser::justifications);
    }

    /** School trips registered by the school, soonest first. */
    public List<SchoolTrip> schoolTrips() {
        return optional("SchoolTrips", ExtraParser::schoolTrips);
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
        if (announcementId == null || !announcementId.matches("[A-Za-z0-9_-]{1,100}")) {
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

    /** Modules a school may switch off answer 401/403/404; treat that as "nothing there". */
    private <T> List<T> optional(String resource, Function<JsonNode, List<T>> parser) {
        try {
            return parser.apply(gateway.get(resource));
        } catch (LibrusException.ModuleUnavailable e) {
            return List.of();
        }
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
