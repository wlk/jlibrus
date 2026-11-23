package io.github.wlk.jlibrus.server.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Conversions between domain values and portable column types (text, epoch millis). */
final class Columns {

    private Columns() {
    }

    static <T> String csv(Collection<T> values, Function<T, String> toText) {
        return values.stream().map(toText).sorted().collect(Collectors.joining(","));
    }

    static <T> Set<T> fromCsv(String csv, Function<String, T> parser) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(parser).collect(Collectors.toUnmodifiableSet());
    }

    static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value.toEpochMilli());
        }
    }

    static Instant getInstant(ResultSet row, String column) throws SQLException {
        long millis = row.getLong(column);
        return row.wasNull() ? null : Instant.ofEpochMilli(millis);
    }

    static void setDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        statement.setString(index, value == null ? null : value.toString());
    }

    static LocalDate getDate(ResultSet row, String column) throws SQLException {
        String text = row.getString(column);
        return text == null ? null : LocalDate.parse(text);
    }
}
