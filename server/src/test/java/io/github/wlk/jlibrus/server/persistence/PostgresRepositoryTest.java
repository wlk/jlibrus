package io.github.wlk.jlibrus.server.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Runs the repository contract against a real PostgreSQL; skipped automatically when Docker is unavailable. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresRepositoryTest extends RepositoryContract {

    private static PostgreSQLContainer postgres;
    private static HikariDataSource dataSource;
    private static Jdbc jdbc;

    @BeforeAll
    static void start() {
        postgres = new PostgreSQLContainer("postgres:17-alpine");
        postgres.start();
        dataSource = DatabaseProducer.createDataSource(
                new TestDatabaseConfig(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        DatabaseProducer.migrate(dataSource);
        jdbc = new Jdbc(dataSource);
    }

    @AfterAll
    static void stop() {
        if (dataSource != null) {
            dataSource.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Override
    protected Jdbc jdbc() {
        return jdbc;
    }
}
