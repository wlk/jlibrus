package io.github.wlk.jlibrus.server.service;

import io.github.wlk.jlibrus.server.config.AppContext;
import io.github.wlk.jlibrus.server.domain.EmailAddress;
import io.github.wlk.jlibrus.server.domain.EntryType;
import io.github.wlk.jlibrus.server.domain.Subscription;
import io.github.wlk.jlibrus.server.domain.summary.Frequency;
import io.github.wlk.jlibrus.server.persistence.SubscriptionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** E-mail subscriptions: who gets which summaries, how often, about which children and entry types. */
@ApplicationScoped
public class SubscriptionService {

    /** Validated input for create/update. */
    public record Draft(EmailAddress email, Set<Frequency> frequencies, Set<EntryType> types, Set<String> childIds,
                        boolean enabled) {
    }

    static final int MAX_SUBSCRIPTIONS = 50;

    private final SubscriptionRepository repository;
    private final AppContext context;

    @Inject
    public SubscriptionService(SubscriptionRepository repository, AppContext context) {
        this.repository = repository;
        this.context = context;
    }

    public List<Subscription> list() {
        return repository.findAll();
    }

    public Subscription get(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Unknown subscription"));
    }

    public Subscription create(Draft draft) {
        if (repository.findAll().size() >= MAX_SUBSCRIPTIONS) {
            throw new IllegalArgumentException("Too many subscriptions (max " + MAX_SUBSCRIPTIONS + ")");
        }
        return repository.save(new Subscription(UUID.randomUUID().toString(), draft.email(), draft.frequencies(),
                draft.types(), draft.childIds(), draft.enabled(), context.clock().instant()));
    }

    public Subscription update(String id, Draft draft) {
        Subscription current = get(id);
        return repository.save(new Subscription(id, draft.email(), draft.frequencies(), draft.types(), draft.childIds(),
                draft.enabled(), current.createdAt()));
    }

    public void delete(String id) {
        if (!repository.delete(id)) {
            throw new NotFoundException("Unknown subscription");
        }
    }
}
