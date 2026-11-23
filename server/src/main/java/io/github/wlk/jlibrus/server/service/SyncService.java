package io.github.wlk.jlibrus.server.service;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.messages.Mailbox;
import io.github.wlk.jlibrus.client.messages.MessageSummary;
import io.github.wlk.jlibrus.server.config.AppContext;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.timeline.NoticePolicy;
import io.github.wlk.jlibrus.server.domain.timeline.TimelineExtractor;
import io.github.wlk.jlibrus.server.librus.Children;
import io.github.wlk.jlibrus.server.librus.LibrusData;
import io.github.wlk.jlibrus.server.persistence.TimelineRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Periodically refreshes every child's data and records entries not seen before. Runs one child after another
 * and never overlaps itself, so Librus never sees bursts and the timeline has a single writer.
 */
@ApplicationScoped
public class SyncService {

    private static final Logger LOG = Logger.getLogger(SyncService.class);

    /** Outcome of syncing one child. */
    public record Result(String childId, int newEntries, String error) {
    }

    private final Children children;
    private final LibrusData data;
    private final ChildService childService;
    private final TimelineRepository timeline;
    private final AppContext context;
    private final ReentrantLock running = new ReentrantLock();

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

    /** Refreshes caches and stores new timeline entries for every child. */
    public List<Result> syncAll() {
        if (!running.tryLock()) {
            return List.of();
        }
        try {
            data.forgetRecords();
            return children.ids().stream().map(this::syncChild).toList();
        } finally {
            running.unlock();
        }
    }

    Result syncChild(String childId) {
        try {
            int added = timeline.insertNew(NoticePolicy.stamp(collect(childId), !timeline.hasAny(childId),
                    context.clock().instant()));
            LOG.debugf("Synced %s: %d new entries", childId, added);
            return new Result(childId, added, null);
        } catch (LibrusException e) {
            LOG.warnf("Sync of %s failed: %s", childId, e.getMessage());
            return new Result(childId, 0, e.getMessage());
        }
    }

    private List<TimelineEntry> collect(String childId) {
        LocalDate today = context.today();
        List<AgendaItem> agenda = childService.agenda(childId, today.minusDays(7), today.plusDays(60));
        return new TimelineExtractor(childId, data.directory(childId), context.zone(), context.locale())
                .extract(data.records(childId), agenda, inbox(childId));
    }

    private List<MessageSummary> inbox(String childId) {
        try {
            return data.messages(childId, Mailbox.INBOX, 1).items();
        } catch (LibrusException e) {
            LOG.debugf("Messages of %s unavailable: %s", childId, e.getMessage());
            return List.of();
        }
    }
}
