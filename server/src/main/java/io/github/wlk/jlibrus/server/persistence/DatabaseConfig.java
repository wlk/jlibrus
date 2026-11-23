package io.github.wlk.jlibrus.server.persistence;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Database settings ({@code DB_URL}, {@code DB_USERNAME}, {@code DB_PASSWORD}, {@code DB_POOL_SIZE}).
 * The JDBC URL picks the engine: {@code jdbc:sqlite:...} or {@code jdbc:postgresql://...}.
 */
@ConfigMapping(prefix = "db")
public interface DatabaseConfig {

    @WithDefault("jdbc:sqlite:data/jlibrus.db")
    String url();

    Optional<String> username();

    Optional<String> password();

    @WithDefault("5")
    int poolSize();
}
