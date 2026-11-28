package io.github.wlk.jlibrus.server.persistence;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Minimal JDBC template: parameter binding, row mapping and checked-exception translation. */
public final class Jdbc {

    @FunctionalInterface
    public interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    private final DataSource dataSource;

    public Jdbc(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> List<T> query(String sql, Binder binder, RowMapper<T> mapper) {
        return read(sql, binder, rows -> {
            List<T> result = new ArrayList<>();
            while (rows.next()) {
                result.add(mapper.map(rows));
            }
            return List.copyOf(result);
        });
    }

    /** The first row only; later rows are neither read nor mapped. */
    public <T> Optional<T> queryOne(String sql, Binder binder, RowMapper<T> mapper) {
        return read(sql, binder, rows -> rows.next() ? Optional.of(mapper.map(rows)) : Optional.empty());
    }

    private <R> R read(String sql, Binder binder, RowMapper<R> reader) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                return reader.map(rows);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Query failed: " + firstLine(sql), e);
        }
    }

    public int update(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException("Update failed: " + firstLine(sql), e);
        }
    }

    /** Executes the same statement for many parameter sets in one transaction; returns rows changed. */
    public <T> int batch(String sql, List<T> items, ItemBinder<T> binder) {
        if (items.isEmpty()) {
            return 0;
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int changed = 0;
                for (T item : items) {
                    binder.bind(statement, item);
                    changed += statement.executeUpdate();
                }
                connection.commit();
                return changed;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Batch failed: " + firstLine(sql), e);
        }
    }

    @FunctionalInterface
    public interface ItemBinder<T> {
        void bind(PreparedStatement statement, T item) throws SQLException;
    }

    private static String firstLine(String sql) {
        String trimmed = sql.strip();
        int newline = trimmed.indexOf('\n');
        return newline < 0 ? trimmed : trimmed.substring(0, newline);
    }
}
