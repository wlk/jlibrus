package io.github.wlk.jlibrus.server.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.flywaydb.core.Flyway;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates the connection pool for SQLite or PostgreSQL and migrates the schema before first use. */
@ApplicationScoped
public class DatabaseProducer {

    private static final Logger LOG = Logger.getLogger(DatabaseProducer.class);
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    private HikariDataSource dataSource;

    @Produces
    @Singleton
    Jdbc jdbc(DatabaseConfig config) {
        dataSource = createDataSource(config);
        migrate(dataSource);
        return new Jdbc(dataSource);
    }

    static HikariDataSource createDataSource(DatabaseConfig config) {
        String url = config.url();
        boolean sqlite = url.startsWith(SQLITE_PREFIX);
        if (sqlite) {
            ensureParentDirectory(url.substring(SQLITE_PREFIX.length()));
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(url);
        // Explicit driver: DriverManager caches drivers per class loader, which breaks across Quarkus restarts.
        hikari.setDriverClassName(sqlite ? "org.sqlite.JDBC" : driverFor(url));
        config.username().ifPresent(hikari::setUsername);
        config.password().ifPresent(hikari::setPassword);
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setPoolName("jlibrus");
        if (sqlite) {
            hikari.addDataSourceProperty("journal_mode", "WAL");
            hikari.addDataSourceProperty("busy_timeout", "10000");
            hikari.addDataSourceProperty("foreign_keys", "true");
        }
        LOG.infof("Using %s database", sqlite ? "SQLite" : "JDBC " + url.split(":", -1)[1]);
        return new HikariDataSource(hikari);
    }

    static String driverFor(String url) {
        if (url.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        throw new IllegalArgumentException("Unsupported DB_URL (use jdbc:sqlite:... or jdbc:postgresql://...)");
    }

    static void migrate(HikariDataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static void ensureParentDirectory(String file) {
        String path = file.contains("?") ? file.substring(0, file.indexOf('?')) : file;
        if (path.isBlank() || path.startsWith(":memory:")) {
            return;
        }
        Path parent = Path.of(path).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create database directory " + parent, e);
        }
    }

    void close(@Observes ShutdownEvent event) {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
