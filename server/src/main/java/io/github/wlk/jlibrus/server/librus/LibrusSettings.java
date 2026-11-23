package io.github.wlk.jlibrus.server.librus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Optional;

/** How the server talks to Librus. Accounts themselves come from {@code LIBRUS_ACCOUNT_<n>_*}. */
@ConfigMapping(prefix = "librus")
public interface LibrusSettings {

    /** When set, every Librus host is replaced by this base URL (the offline mock in demo mode). */
    Optional<String> endpointOverride();

    Optional<String> userAgent();

    @WithDefault("PT30S")
    Duration requestTimeout();

    /** School time zone; Librus dates and times are local to it. */
    @WithDefault("Europe/Warsaw")
    String timezone();

    /** Value of "+" on a mark (schools differ). */
    @WithDefault("0.5")
    double gradePlus();

    /** Value of "-" on a mark. */
    @WithDefault("0.25")
    double gradeMinus();
}
