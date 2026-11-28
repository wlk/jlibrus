package io.github.wlk.jlibrus.server.service;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.messages.Mailbox;
import io.github.wlk.jlibrus.client.messages.MessageSummary;
import io.github.wlk.jlibrus.server.config.AppContext;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.student.Directory;
import io.github.wlk.jlibrus.server.domain.student.DirectoryGaps;
import io.github.wlk.jlibrus.server.domain.student.StudentRecords;
import io.github.wlk.jlibrus.server.domain.timeline.NoticePolicy;
import io.github.wlk.jlibrus.server.domain.timeline.TimelineExtractor;
import io.github.wlk.jlibrus.server.librus.Children;
import io.github.wlk.jlibrus.server.librus.LibrusData;
import io.github.wlk.jlibrus.server.persistence.TimelineRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Periodically refreshes every child's data and records entries not seen before. Runs one child after another
 * and never overlaps itself, so Librus never sees bursts and the timeline has a single writer. A failure of
 * one child never stops the others.
 */
@ApplicationScoped
public class SyncService {

    private static final Logger LOG = Logger.getLogger(SyncService.class);
    static final Duration DIRECTORY_REFRESH_COOLDOWN = Duration.ofHours(1);
    static final long WAIT_FOR_RUNNING_SYNC_MINUTES = 5;

    /**
     * Outcome of syncing one child.
     *
     * @param transientFailure true when retrying later may help (outage, expired session, unexpected answer)
     */
    public record Result(String childId, int newEntries, String error, boolean transientFailure) {
        static Result ok(String childId, int added) {
            return new Result(childId, added, null, false);
        }
    }

    private final Children children;
    private final LibrusData data;
    private final ChildService childService;
    private final TimelineRepository timeline;
    private final AppContext context;
    private final ReentrantLock running = new ReentrantLock();
    private final Map<String, Instant> directoryRefreshed = new ConcurrentHashMap<>();

    @Inject
    public SyncService(Children children, LibrusData data, ChildService childService, TimelineRepository timeline,
                       AppContext context) {
        this.children = children;
        this.data = data;
        this.childService = childService;
        this.timeline = timeline;
        this.context = context;
    }

    @Scheduled(every = "${sync.interval:15m}", delayed = "${sync.initial-delay:20s}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        syncAll();
    }

    /** Refreshes record caches and stores new timeline entries; waits for a sync already running. */
    public List<Result> syncAll() {
        return exclusively(data::forgetRecords);
    }

    /** Like {@link #syncAll()} but also drops reference data (the "refresh" button). */
    public List<Result> refreshEverything() {
        return exclusively(data::forgetEverything);
    }

    private List<Result> exclusively(Runnable forget) {
        try {
            if (!running.tryLock(WAIT_FOR_RUNNING_SYNC_MINUTES, TimeUnit.MINUTES)) {
                throw new LibrusException.ServiceUnavailable("Another sync is still running");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LibrusException.ServiceUnavailable("Interrupted while waiting for sync", e);
        }
        try {
            forget.run();
            return children.ids().stream().map(this::syncChild).toList();
        } finally {
            running.unlock();
        }
    }

    Result syncChild(String childId) {
        try {
            Set<String> known = timeline.knownKeys(childId);
            Optional<List<MessageSummary>> inbox = inbox(childId);
            if (NoticePolicy.isFirstSync(known) && inbox.isEmpty()) {
                return new Result(childId, 0, "Messages unavailable; first sync postponed", true);
            }
            List<TimelineEntry> entries = collect(childId, inbox.orElse(List.of()));
            int added = timeline.insertNew(NoticePolicy.unseen(entries, known, context.clock().instant()));
            LOG.debugf("Synced %s: %d new entries", childId, added);
            return Result.ok(childId, added);
        } catch (LibrusException e) {
            LOG.warnf("Sync of %s failed: %s", childId, e.getMessage());
            return new Result(childId, 0, e.getMessage(), isTransient(e));
        } catch (RuntimeException e) {
            LOG.errorf(e, "Sync of %s failed unexpectedly", childId);
            return new Result(childId, 0, "Unexpected error: " + e.getClass().getSimpleName(), true);
        }
    }

    static boolean isTransient(LibrusException e) {
        return !(e instanceof LibrusException.InvalidCredentials || e instanceof LibrusException.CaptchaRequired
                || e instanceof LibrusException.ModuleUnavailable);
    }

    private List<TimelineEntry> collect(String childId, List<MessageSummary> inbox) {
        StudentRecords records = data.records(childId);
        Directory directory = freshDirectory(childId, records);
        LocalDate today = context.today();
        List<AgendaItem> agenda = childService.agenda(childId, today.minusDays(7), today.plusDays(60));
        return new TimelineExtractor(childId, directory, context.zone(), context.locale()).extract(records, agenda, inbox);
    }

    /** Entries are written once, so names must resolve: refetch reference data that misses ids (rate limited). */
    private Directory freshDirectory(String childId, StudentRecords records) {
        Directory directory = data.directory(childId);
        Instant now = context.clock().instant();
        Instant last = directoryRefreshed.getOrDefault(childId, Instant.EPOCH);
        if (DirectoryGaps.stale(directory, records) && last.plus(DIRECTORY_REFRESH_COOLDOWN).isBefore(now)) {
            directoryRefreshed.put(childId, now);
            data.forgetDirectory(childId);
            LOG.infof("Reference data of %s was outdated; reloaded", childId);
            return data.directory(childId);
        }
        return directory;
    }

    /** Empty when the inbox could not be read for a reason that may go away; a disabled module is an empty inbox. */
    private Optional<List<MessageSummary>> inbox(String childId) {
        try {
            return Optional.of(data.messages(childId, Mailbox.INBOX, 1).items());
        } catch (LibrusException e) {
            LOG.debugf("Messages of %s unavailable: %s", childId, e.getMessage());
            return isTransient(e) ? Optional.empty() : Optional.of(List.of());
        }
    }
}
