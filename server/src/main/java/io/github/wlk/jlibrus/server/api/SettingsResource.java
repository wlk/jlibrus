package io.github.wlk.jlibrus.server.api;

import io.github.wlk.jlibrus.server.domain.CalendarFeed;
import io.github.wlk.jlibrus.server.domain.EmailAddress;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Subscription;
import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import io.github.wlk.jlibrus.server.domain.summary.SummaryRenderer;
import io.github.wlk.jlibrus.server.service.FeedService;
import io.github.wlk.jlibrus.server.service.SubscriptionService;
import io.github.wlk.jlibrus.server.service.SummaryService;
import io.github.wlk.jlibrus.server.service.SyncService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Calendar feeds, e-mail subscriptions, entry types and maintenance actions. */
@Path("/api")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class SettingsResource {

    /** A feed as shown in settings; {@code path} is relative to the server address. */
    public record FeedView(String id, String name, String path, Set<String> childIds, Set<EntryType> types) {
        static FeedView of(CalendarFeed feed) {
            return new FeedView(feed.id(), feed.name(), "/ical/" + feed.token() + ".ics", feed.childIds(), feed.types());
        }
    }

    public record FeedRequest(String name, Set<String> childIds, Set<String> types) {
    }

    public record SubscriptionRequest(String email, Set<String> frequencies, Set<String> types, Set<String> childIds,
                                      Boolean enabled) {
    }

    public record EntryTypeView(EntryType id, String labelPl, String labelEn, boolean calendar) {
    }

    public record Schedule(String daily, String weekly, String monthly, String timezone, String sync) {
    }

    public record Preview(String subject, String html) {
    }

    @Inject
    FeedService feeds;

    @Inject
    SubscriptionService subscriptions;

    @Inject
    SummaryService summaries;

    @Inject
    SyncService sync;

    @ConfigProperty(name = "summary.daily-cron", defaultValue = "0 0 18 * * ?")
    String dailyCron;

    @ConfigProperty(name = "summary.weekly-cron", defaultValue = "0 0 18 ? * SUN")
    String weeklyCron;

    @ConfigProperty(name = "summary.monthly-cron", defaultValue = "0 0 18 L * ?")
    String monthlyCron;

    @ConfigProperty(name = "librus.timezone", defaultValue = "Europe/Warsaw")
    String timezone;

    @ConfigProperty(name = "sync.interval", defaultValue = "15m")
    String syncInterval;

    @GET
    @Path("/entry-types")
    public List<EntryTypeView> entryTypes() {
        return Arrays.stream(EntryType.values()).map(t -> new EntryTypeView(t, t.label(Locale.forLanguageTag("pl")),
                t.label(Locale.ENGLISH), t.isCalendar())).toList();
    }

    @GET
    @Path("/schedule")
    public Schedule schedule() {
        return new Schedule(dailyCron, weeklyCron, monthlyCron, timezone, syncInterval);
    }

    @POST
    @Path("/refresh")
    public List<SyncService.Result> refresh() {
        return sync.refreshEverything();
    }

    @GET
    @Path("/feeds")
    public List<FeedView> feeds() {
        return feeds.list().stream().map(FeedView::of).toList();
    }

    @POST
    @Path("/feeds")
    @Consumes(MediaType.APPLICATION_JSON)
    public FeedView createFeed(FeedRequest request) {
        FeedRequest r = require(request);
        return FeedView.of(feeds.create(r.name(), ids(r.childIds()), types(r.types())));
    }

    @PUT
    @Path("/feeds/{feedId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public FeedView updateFeed(@PathParam("feedId") String feedId, FeedRequest request) {
        FeedRequest r = require(request);
        return FeedView.of(feeds.update(feedId, r.name(), ids(r.childIds()), types(r.types())));
    }

    @POST
    @Path("/feeds/{feedId}/reset-token")
    public FeedView resetToken(@PathParam("feedId") String feedId) {
        return FeedView.of(feeds.resetToken(feedId));
    }

    @DELETE
    @Path("/feeds/{feedId}")
    public void deleteFeed(@PathParam("feedId") String feedId) {
        feeds.delete(feedId);
    }

    @GET
    @Path("/subscriptions")
    public List<Subscription> subscriptions() {
        return subscriptions.list();
    }

    @POST
    @Path("/subscriptions")
    @Consumes(MediaType.APPLICATION_JSON)
    public Subscription createSubscription(SubscriptionRequest request) {
        return subscriptions.create(draft(require(request)));
    }

    @PUT
    @Path("/subscriptions/{subscriptionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Subscription updateSubscription(@PathParam("subscriptionId") String id, SubscriptionRequest request) {
        return subscriptions.update(id, draft(require(request)));
    }

    @DELETE
    @Path("/subscriptions/{subscriptionId}")
    public void deleteSubscription(@PathParam("subscriptionId") String id) {
        subscriptions.delete(id);
    }

    @GET
    @Path("/subscriptions/{subscriptionId}/preview")
    public Preview preview(@PathParam("subscriptionId") String id, @QueryParam("frequency") String frequency) {
        var summary = summaries.preview(subscriptions.get(id), Frequency.parse(frequency == null ? "daily" : frequency));
        return new Preview(SummaryRenderer.subject(summary), SummaryRenderer.html(summary));
    }

    @POST
    @Path("/subscriptions/{subscriptionId}/send")
    public Preview sendNow(@PathParam("subscriptionId") String id, @QueryParam("frequency") String frequency) {
        var summary = summaries.sendNow(subscriptions.get(id), Frequency.parse(frequency == null ? "daily" : frequency));
        return new Preview(SummaryRenderer.subject(summary), SummaryRenderer.html(summary));
    }

    static SubscriptionService.Draft draft(SubscriptionRequest r) {
        Set<Frequency> frequencies = r.frequencies() == null ? Set.of()
                : r.frequencies().stream().map(Frequency::parse).collect(Collectors.toUnmodifiableSet());
        return new SubscriptionService.Draft(EmailAddress.parse(r.email()), frequencies, types(r.types()),
                ids(r.childIds()), r.enabled() == null || r.enabled());
    }

    static Set<EntryType> types(Set<String> raw) {
        return raw == null ? Set.of() : EntryType.parseSet(String.join(",", raw));
    }

    static Set<String> ids(Set<String> raw) {
        if (raw == null) {
            return Set.of();
        }
        if (raw.stream().anyMatch(id -> id == null || !id.matches("[A-Za-z0-9._-]{1,64}"))) {
            throw new IllegalArgumentException("Invalid child id");
        }
        return Set.copyOf(raw);
    }

    private static <T> T require(T request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        return request;
    }
}
