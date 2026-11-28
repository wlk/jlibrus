package io.github.wlk.jlibrus.server.service;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.server.config.AppContext;
import io.github.wlk.jlibrus.server.domain.Subscription;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import io.github.wlk.jlibrus.server.domain.summary.Summary;
import io.github.wlk.jlibrus.server.domain.summary.SummaryBuilder;
import io.github.wlk.jlibrus.server.domain.summary.SummaryRenderer;
import io.github.wlk.jlibrus.server.domain.summary.SummaryWindow;
import io.github.wlk.jlibrus.server.persistence.DispatchRepository;
import io.github.wlk.jlibrus.server.persistence.SettingRepository;
import io.github.wlk.jlibrus.server.persistence.SubscriptionRepository;
import io.github.wlk.jlibrus.server.persistence.TimelineRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and mails daily/weekly/monthly summaries.
 * <ul>
 *   <li>Each (subscription, frequency, period) is claimed in the database before sending, so a repeated tick or a
 *   restart never mails twice; a failed send releases the claim.</li>
 *   <li>When Librus is temporarily unavailable nothing is claimed; the run is retried every
 *   {@code summary.retry-interval} and sent with whatever is available after {@link #MAX_RETRIES} attempts.</li>
 *   <li>"New" starts where the previous successful run of the same frequency ended, so entries are never
 *   dropped between runs or mailed twice.</li>
 * </ul>
 */
@ApplicationScoped
public class SummaryService {

    private static final Logger LOG = Logger.getLogger(SummaryService.class);
    static final int MAX_RETRIES = 6;

    private final SubscriptionRepository subscriptions;
    private final DispatchRepository dispatches;
    private final TimelineRepository timeline;
    private final SettingRepository settings;
    private final ChildService childService;
    private final SyncService sync;
    private final Mailer mailer;
    private final AppContext context;
    private final Map<Frequency, Integer> pendingRetries = new ConcurrentHashMap<>();

    @Inject
    public SummaryService(SubscriptionRepository subscriptions, DispatchRepository dispatches, TimelineRepository timeline,
                          SettingRepository settings, ChildService childService, SyncService sync, Mailer mailer,
                          AppContext context) {
        this.subscriptions = subscriptions;
        this.dispatches = dispatches;
        this.timeline = timeline;
        this.settings = settings;
        this.childService = childService;
        this.sync = sync;
        this.mailer = mailer;
        this.context = context;
    }

    @Scheduled(cron = "${summary.daily-cron:0 0 18 * * ?}", timeZone = "${librus.timezone:Europe/Warsaw}")
    void daily(ScheduledExecution execution) {
        scheduled(Frequency.DAILY, execution.getScheduledFireTime());
    }

    @Scheduled(cron = "${summary.weekly-cron:0 0 18 ? * SUN}", timeZone = "${librus.timezone:Europe/Warsaw}")
    void weekly(ScheduledExecution execution) {
        scheduled(Frequency.WEEKLY, execution.getScheduledFireTime());
    }

    @Scheduled(cron = "${summary.monthly-cron:0 0 18 L * ?}", timeZone = "${librus.timezone:Europe/Warsaw}")
    void monthly(ScheduledExecution execution) {
        scheduled(Frequency.MONTHLY, execution.getScheduledFireTime());
    }

    @Scheduled(every = "${summary.retry-interval:30m}", delayed = "5m",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void retryPending() {
        pendingRetries.keySet().forEach(frequency -> {
            int attempt = pendingRetries.merge(frequency, 1, Integer::sum);
            sendDue(frequency, context.clock().instant(), attempt >= MAX_RETRIES);
        });
    }

    private void scheduled(Frequency frequency, Instant firedAt) {
        pendingRetries.remove(frequency);
        sendDue(frequency, firedAt, false);
    }

    /** Sends the summary of {@code frequency} now; returns how many were mailed. */
    public int sendDue(Frequency frequency) {
        return sendDue(frequency, context.clock().instant(), true);
    }

    /**
     * Sends due summaries for a run at {@code at}. Unless {@code acceptPartialData}, a run with incomplete Librus
     * data sends nothing and is queued for retry.
     */
    public synchronized int sendDue(Frequency frequency, Instant at, boolean acceptPartialData) {
        boolean syncDegraded = sync.syncAll().stream().anyMatch(SyncService.Result::transientFailure);
        SummaryWindow window = window(frequency, at);
        Context data = gather(window);
        if ((syncDegraded || data.degraded()) && !acceptPartialData) {
            pendingRetries.putIfAbsent(frequency, 0);
            LOG.warnf("%s summaries postponed: Librus data incomplete, will retry", frequency);
            return 0;
        }
        int sent = 0;
        for (Subscription subscription : subscriptions.findWanting(frequency)) {
            if (dispatches.claim(subscription.id(), frequency, window.periodKey(), context.clock().instant())) {
                sent += deliver(subscription, frequency, window, data) ? 1 : 0;
            }
        }
        settings.put(untilKey(frequency), window.newUntil().toString());
        pendingRetries.remove(frequency);
        LOG.infof("%s summaries: %d sent for period %s", frequency, sent, window.periodKey());
        return sent;
    }

    /** Window for a run at {@code at} (minute precision), continuing from where the last run ended. */
    SummaryWindow window(Frequency frequency, Instant at) {
        ZonedDateTime time = at.atZone(context.zone()).truncatedTo(ChronoUnit.MINUTES);
        Instant previous = settings.get(untilKey(frequency)).map(Instant::parse).orElse(null);
        return SummaryWindow.of(frequency, time).continuingFrom(previous);
    }

    private static String untilKey(Frequency frequency) {
        return "summary." + frequency.name().toLowerCase(java.util.Locale.ROOT) + ".until";
    }

    private boolean deliver(Subscription subscription, Frequency frequency, SummaryWindow window, Context data) {
        try {
            Summary summary = build(subscription, frequency, window, data);
            if (summary.isEmpty()) {
                return false;
            }
            send(subscription, summary);
            return true;
        } catch (RuntimeException e) {
            dispatches.release(subscription.id(), frequency, window.periodKey());
            LOG.errorf(e, "Summary to subscription %s failed; will retry next run", subscription.id());
            return false;
        }
    }

    /** Sends the summary right now, ignoring the once-per-period rule (the "send now" button). */
    public Summary sendNow(Subscription subscription, Frequency frequency) {
        Summary summary = preview(subscription, frequency);
        send(subscription, summary);
        return summary;
    }

    public Summary preview(Subscription subscription, Frequency frequency) {
        SummaryWindow window = SummaryWindow.of(frequency, context.now());
        return build(subscription, frequency, window, gather(window));
    }

    private record Context(Map<String, String> names, List<TimelineEntry> news, Map<String, List<AgendaItem>> agendas,
                           boolean degraded) {
    }

    private Context gather(SummaryWindow window) {
        Map<String, String> names = childService.names();
        List<TimelineEntry> news = timeline.findNoticed(window.newSince(), window.newUntil());
        Map<String, List<AgendaItem>> agendas = new HashMap<>();
        boolean degraded = false;
        for (String id : names.keySet()) {
            try {
                agendas.put(id, childService.agenda(id, window.upcomingFrom(), window.upcomingTo()));
            } catch (LibrusException e) {
                LOG.warnf("Agenda of %s unavailable for summary: %s", id, e.getMessage());
                agendas.put(id, List.of());
                degraded |= SyncService.isTransient(e);
            }
        }
        return new Context(names, news, agendas, degraded);
    }

    private Summary build(Subscription subscription, Frequency frequency, SummaryWindow window, Context data) {
        return SummaryBuilder.build(subscription, frequency, window, context.locale(), data.names(), data.news(),
                data.agendas());
    }

    private void send(Subscription subscription, Summary summary) {
        mailer.send(Mail.withHtml(subscription.email().value(), SummaryRenderer.subject(summary), SummaryRenderer.html(summary))
                .setText(SummaryRenderer.text(summary)));
    }
}
