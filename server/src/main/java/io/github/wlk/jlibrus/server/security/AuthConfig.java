package io.github.wlk.jlibrus.server.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

/** The single application login, set via {@code APP_USERNAME} / {@code APP_PASSWORD}. */
@ConfigMapping(prefix = "app.auth")
public interface AuthConfig {

    String username();

    String password();

    /** Failed attempts per client address before it is blocked. */
    @WithDefault("5")
    int maxFailures();

    @WithDefault("PT15M")
    Duration blockWindow();
}
