package io.github.wlk.jlibrus.server.service;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.server.config.AppContext;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Subscription;
import io.github.wlk.jlibrus.server.domain.TimelineEntry;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import io.github.wlk.jlibrus.server.domain.summary.Summary;
import io.github.wlk.jlibrus.server.domain.summary.SummaryBuilder;
import io.github.wlk.jlibrus.server.domain.summary.SummaryRenderer;
import io.github.wlk.jlibrus.server.domain.summary.SummaryWindow;
import io.github.wlk.jlibrus.server.persistence.DispatchRepository;
import io.github.wlk.jlibrus.server.persistence.SubscriptionRepository;
import io.github.wlk.jlibrus.server.persistence.TimelineRepository;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds and mails daily/weekly/monthly summaries. Each (subscription, frequency, period) is claimed in the
 * database before sending, so a repeated scheduler tick or a restart never mails twice; a failed send releases
 * the claim for the next run.
 */
@ApplicationScoped
public class SummaryService {

    private static final Logger LOG = Logger.getLogger(SummaryService.class);

    private final SubscriptionRepository subscriptions;
    private final DispatchRepository dispatches;
    private final TimelineRepository timeline;
    private final ChildService childService;
    private final SyncService sync;
    private final Mailer mailer;
    private final AppContext context;

    @Inject
    public SummaryService(SubscriptionRepository subscriptions, DispatchRepository dispatches, TimelineRepository timeline,
                          ChildService childService, SyncService sync, Mailer mailer, AppContext context) {
        this.subscriptions = subscriptions;
        this.dispatches = dispatches;
        this.timeline = timeline;
        this.childService = childService;
        this.sync = sync;
        this.mailer = mailer;
        this.context = context;
    }

    @Scheduled(cron = "${summary.daily-cron:0 0 18 * * ?}", timeZone = "${librus.timezone:Europe/Warsaw}")
    void daily() {
        sendDue(Frequency.DAILY);
    }

    @Scheduled(cron = "${summary.weekly-cron:0 0 18 ? * SUN}", timeZone = "${librus.timezone:Europe/Warsaw}")
    void weekly() {
        sendDue(Frequency.WEEKLY);
    }

    @Scheduled(cron = "${summary.monthly-cron:0 0 18 L * ?}", timeZone = "${librus.timezone:Europe/Warsaw}")
    void monthly() {
        sendDue(Frequency.MONTHLY);
    }

    /** Sends the summary of {@code frequency} to every subscription that wants it; returns how many were mailed. */
    public int sendDue(Frequency frequency) {
        sync.syncAll();
        SummaryWindow window = SummaryWindow.of(frequency, context.now());
        Context data = gather(window);
        int sent = 0;
        for (Subscription subscription : subscriptions.findWanting(frequency)) {
            if (!dispatches.claim(subscription.id(), frequency, window.periodKey(), context.clock().instant())) {
                continue;
            }
            sent += deliver(subscription, frequency, window, data) ? 1 : 0;
        }
        LOG.infof("%s summaries: %d sent for period %s", frequency, sent, window.periodKey());
        return sent;
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

    /** Sends the summary right now, ignoring the once-per-period rule (the "send test" button). */
    public Summary sendNow(Subscription subscription, Frequency frequency) {
        SummaryWindow window = SummaryWindow.of(frequency, context.now());
        Summary summary = build(subscription, frequency, window, gather(window));
        send(subscription, summary);
        return summary;
    }

    public Summary preview(Subscription subscription, Frequency frequency) {
        SummaryWindow window = SummaryWindow.of(frequency, context.now());
        return build(subscription, frequency, window, gather(window));
    }

    private record Context(Map<String, String> names, List<TimelineEntry> news, Map<String, List<AgendaItem>> agendas) {
    }

    private Context gather(SummaryWindow window) {
        Map<String, String> names = childService.names();
        List<TimelineEntry> news = timeline.findNoticed(Set.of(), EnumSet.allOf(EntryType.class),
                window.newSince(), window.newUntil());
        Map<String, List<AgendaItem>> agendas = new HashMap<>();
        names.keySet().forEach(id -> agendas.put(id, agendaOrEmpty(id, window)));
        return new Context(names, news, agendas);
    }

    private List<AgendaItem> agendaOrEmpty(String id, SummaryWindow window) {
        try {
            return childService.agenda(id, window.upcomingFrom(), window.upcomingTo());
        } catch (LibrusException e) {
            LOG.warnf("Agenda of %s unavailable for summary: %s", id, e.getMessage());
            return List.of();
        }
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
