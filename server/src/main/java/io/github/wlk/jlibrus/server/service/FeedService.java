package io.github.wlk.jlibrus.server.service;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.server.config.AppContext;
import io.github.wlk.jlibrus.server.domain.CalendarFeed;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.SecretTokens;
import io.github.wlk.jlibrus.server.domain.agenda.AgendaItem;
import io.github.wlk.jlibrus.server.domain.ical.CalendarItem;
import io.github.wlk.jlibrus.server.domain.ical.ICalendarWriter;
import io.github.wlk.jlibrus.server.persistence.CalendarFeedRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** iCalendar feeds: CRUD, token rotation and rendering for the public, token-protected URL. */
@ApplicationScoped
public class FeedService {

    private static final Logger LOG = Logger.getLogger(FeedService.class);
    static final int DAYS_BACK = 30;
    static final int DAYS_AHEAD = 180;

    private final CalendarFeedRepository feeds;
    private final ChildService childService;
    private final AppContext context;
    private final Map<String, String> lastGood = new ConcurrentHashMap<>();

    @Inject
    public FeedService(CalendarFeedRepository feeds, ChildService childService, AppContext context) {
        this.feeds = feeds;
        this.childService = childService;
        this.context = context;
    }

    /** First start: one feed with every child and every calendar type except daily lessons. */
    void createDefault(@Observes StartupEvent event) {
        if (feeds.findAll().isEmpty()) {
            Set<EntryType> types = EnumSet.copyOf(EntryType.calendarTypes());
            types.remove(EntryType.LESSON);
            feeds.save(new CalendarFeed(UUID.randomUUID().toString(), "Librus", SecretTokens.newToken(), Set.of(), types,
                    context.clock().instant()));
            LOG.info("Created default calendar feed");
        }
    }

    public List<CalendarFeed> list() {
        return feeds.findAll();
    }

    public CalendarFeed create(String name, Set<String> childIds, Set<EntryType> types) {
        return feeds.save(new CalendarFeed(UUID.randomUUID().toString(), name, SecretTokens.newToken(), childIds,
                calendarOnly(types), context.clock().instant()));
    }

    public CalendarFeed update(String id, String name, Set<String> childIds, Set<EntryType> types) {
        CalendarFeed current = get(id);
        lastGood.remove(id);
        return feeds.save(new CalendarFeed(id, name, current.token(), childIds, calendarOnly(types), current.createdAt()));
    }

    /** Invalidates the old URL immediately; subscribers must use the new one. */
    public CalendarFeed resetToken(String id) {
        return feeds.save(get(id).withToken(SecretTokens.newToken()));
    }

    public void delete(String id) {
        lastGood.remove(id);
        if (!feeds.delete(id)) {
            throw new NotFoundException("Unknown feed");
        }
    }

    public CalendarFeed get(String id) {
        return feeds.findById(id).orElseThrow(() -> new NotFoundException("Unknown feed"));
    }

    /**
     * The .ics text of the feed with this token, or empty when the token is unknown. When Librus cannot be read
     * the last good rendering is served; without one the call fails - calendar apps must never receive an empty
     * calendar, which would make them delete every event.
     */
    public Optional<String> render(String token) {
        if (!SecretTokens.looksValid(token)) {
            return Optional.empty();
        }
        return feeds.findByToken(token).filter(f -> SecretTokens.matches(f.token(), token)).map(this::renderOrLastGood);
    }

    private String renderOrLastGood(CalendarFeed feed) {
        try {
            String ics = render(feed);
            lastGood.put(feed.id(), ics);
            return ics;
        } catch (LibrusException e) {
            String previous = lastGood.get(feed.id());
            if (previous == null) {
                throw new LibrusException.ServiceUnavailable("Calendar temporarily unavailable: " + e.getMessage());
            }
            LOG.warnf("Feed %s served from last good copy: %s", feed.id(), e.getMessage());
            return previous;
        }
    }

    private String render(CalendarFeed feed) {
        LocalDate today = context.today();
        Map<String, String> names = childService.names();
        boolean several = names.keySet().stream().filter(feed::includesChild).count() > 1;
        List<CalendarItem> items = new ArrayList<>();
        names.forEach((childId, name) -> {
            if (feed.includesChild(childId)) {
                childService.agenda(childId, today.minusDays(DAYS_BACK), today.plusDays(DAYS_AHEAD)).stream()
                        .filter(i -> feed.types().contains(i.type()))
                        .map(i -> toCalendarItem(i, several ? name : null)).forEach(items::add);
            }
        });
        return new ICalendarWriter(context.zone(), context.clock()::instant).write(feed.name(), items);
    }

    static CalendarItem toCalendarItem(AgendaItem item, String childName) {
        String summary = childName == null ? item.title() : childName + ": " + item.title();
        return new CalendarItem(item.uid(), summary, item.detail(), item.location(), item.type().name(), item.when());
    }

    private static Set<EntryType> calendarOnly(Set<EntryType> types) {
        Set<EntryType> result = EnumSet.noneOf(EntryType.class);
        types.stream().filter(EntryType::isCalendar).forEach(result::add);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one calendar entry type");
        }
        return result;
    }
}
