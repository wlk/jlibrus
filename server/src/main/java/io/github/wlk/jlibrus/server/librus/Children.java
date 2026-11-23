package io.github.wlk.jlibrus.server.librus;

import io.github.wlk.jlibrus.client.Credentials;
import io.github.wlk.jlibrus.client.LibrusAccount;
import io.github.wlk.jlibrus.client.LibrusEndpoints;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The configured children: one Librus account each, identified by its Synergia login. */
@ApplicationScoped
public class Children {

    private static final Logger LOG = Logger.getLogger(Children.class);

    private final Map<String, LibrusAccount> accounts = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();

    @Inject
    public Children(Config config, LibrusSettings settings) {
        this(AccountSettings.parse(collect(config)), settings);
    }

    Children(List<AccountSettings> configured, LibrusSettings settings) {
        LibrusEndpoints endpoints = settings.endpointOverride().filter(s -> !s.isBlank())
                .map(url -> LibrusEndpoints.singleHost(URI.create(url)))
                .orElse(LibrusEndpoints.production());
        configured.forEach(account -> {
            LibrusAccount.Builder builder = LibrusAccount.builder(new Credentials(account.login(), account.password()))
                    .endpoints(endpoints)
                    .timeouts(Duration.ofSeconds(10), settings.requestTimeout());
            settings.userAgent().filter(s -> !s.isBlank()).ifPresent(builder::userAgent);
            accounts.put(account.login(), builder.build());
            labels.put(account.login(), account.label());
        });
        if (accounts.isEmpty()) {
            LOG.warn("No Librus accounts configured - set LIBRUS_ACCOUNT_1_LOGIN and LIBRUS_ACCOUNT_1_PASSWORD");
        } else {
            LOG.infof("Configured %d Librus account(s) against %s", accounts.size(), endpoints.synergia().getHost());
        }
    }

    private static Map<String, String> collect(Config config) {
        Map<String, String> values = new HashMap<>();
        for (String name : config.getPropertyNames()) {
            if (AccountSettings.normalise(name).startsWith("LIBRUS_ACCOUNT_")) {
                config.getOptionalValue(name, String.class).ifPresent(v -> values.put(name, v));
            }
        }
        return values;
    }

    public List<String> ids() {
        return List.copyOf(accounts.keySet());
    }

    public LibrusAccount account(String childId) {
        LibrusAccount account = accounts.get(childId);
        if (account == null) {
            throw new NotFoundException("Unknown child");
        }
        return account;
    }

    public Optional<String> label(String childId) {
        return Optional.ofNullable(labels.get(childId));
    }
}
