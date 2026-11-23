package io.github.wlk.jlibrus.server.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class SqliteRepositoryTest extends RepositoryContract {

    @TempDir
    static Path tempDir;

    private static HikariDataSource dataSource;
    private static Jdbc jdbc;

    @BeforeAll
    static void start() {
        dataSource = DatabaseProducer.createDataSource(
                new TestDatabaseConfig("jdbc:sqlite:" + tempDir.resolve("nested/dir/test.db"), null, null));
        DatabaseProducer.migrate(dataSource);
        jdbc = new Jdbc(dataSource);
    }

    @AfterAll
    static void stop() {
        dataSource.close();
    }

    @Override
    protected Jdbc jdbc() {
        return jdbc;
    }
}
