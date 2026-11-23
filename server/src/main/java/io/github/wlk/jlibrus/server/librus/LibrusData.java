package io.github.wlk.jlibrus.server.librus;

import io.github.wlk.jlibrus.client.LibrusAccount;
import io.github.wlk.jlibrus.client.messages.Mailbox;
import io.github.wlk.jlibrus.client.messages.MessagePage;
import io.github.wlk.jlibrus.client.messages.Recipient;
import io.github.wlk.jlibrus.client.model.TimetableEntry;
import io.github.wlk.jlibrus.server.domain.student.Directory;
import io.github.wlk.jlibrus.server.domain.student.StudentRecords;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheKey;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cached reads from Librus. Reference data is kept for hours, records for minutes (see application.properties);
 * a failed fetch is never cached. Calls per child are sequential: Librus dislikes request bursts.
 */
@ApplicationScoped
public class LibrusData {

    public static final String DIRECTORY = "librus-directory";
    public static final String RECORDS = "librus-records";
    public static final String TIMETABLE = "librus-timetable";
    public static final String MESSAGES = "librus-messages";
    public static final String RECIPIENTS = "librus-recipients";

    private final Children children;

    @Inject
    public LibrusData(Children children) {
        this.children = children;
    }

    @CacheResult(cacheName = DIRECTORY)
    public Directory directory(@CacheKey String childId) {
        LibrusAccount a = children.account(childId);
        return new Directory(a.me(), a.schoolClass(), a.school(),
                index(a.subjects(), s -> s.id()), index(a.users(), p -> p.id()), index(a.lessons(), l -> l.id()),
                index(a.classrooms(), c -> c.id()), index(a.gradeCategories(), c -> c.id()),
                index(a.gradeComments(), c -> c.id()), index(a.attendanceTypes(), t -> t.id()),
                index(a.eventCategories(), c -> c.id()), index(a.noteCategories(), c -> c.id()));
    }

    @CacheResult(cacheName = RECORDS)
    public StudentRecords records(@CacheKey String childId) {
        LibrusAccount a = children.account(childId);
        return new StudentRecords(a.grades(), a.behaviour(), a.attendances(), a.events(), a.homework(),
                a.parentMeetings(), a.schoolFreeDays(), a.teacherAbsences(), a.announcements(), a.notes(),
                a.luckyNumber());
    }

    /** Lessons of the week starting on {@code monday}. */
    @CacheResult(cacheName = TIMETABLE)
    public List<TimetableEntry> timetable(@CacheKey String childId, @CacheKey LocalDate monday) {
        return children.account(childId).timetable(monday);
    }

    @CacheResult(cacheName = MESSAGES)
    public MessagePage messages(@CacheKey String childId, @CacheKey Mailbox mailbox, @CacheKey int page) {
        return children.account(childId).messages().list(mailbox, page, 20);
    }

    @CacheResult(cacheName = MESSAGES)
    public Map<String, Integer> unreadCounts(@CacheKey String childId) {
        return children.account(childId).messages().unreadCounts();
    }

    @CacheResult(cacheName = RECIPIENTS)
    public List<Recipient> recipients(@CacheKey String childId) {
        return children.account(childId).messages().allRecipients();
    }

    @CacheInvalidateAll(cacheName = RECORDS)
    @CacheInvalidateAll(cacheName = TIMETABLE)
    @CacheInvalidateAll(cacheName = MESSAGES)
    public void forgetRecords() {
        // annotations do the work
    }

    @CacheInvalidateAll(cacheName = MESSAGES)
    public void forgetMessages() {
        // annotations do the work
    }

    @CacheInvalidateAll(cacheName = DIRECTORY)
    @CacheInvalidateAll(cacheName = RECORDS)
    @CacheInvalidateAll(cacheName = TIMETABLE)
    @CacheInvalidateAll(cacheName = MESSAGES)
    @CacheInvalidateAll(cacheName = RECIPIENTS)
    public void forgetEverything() {
        // annotations do the work
    }

    private static <T> Map<Long, T> index(List<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }
}
