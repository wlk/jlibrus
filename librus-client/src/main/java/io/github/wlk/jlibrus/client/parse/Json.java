package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.LibrusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Lenient readers for Librus JSON: ids may be numbers or strings, optional fields may be missing or null,
 * timestamps use a space instead of {@code T}. Every read is null-safe.
 */
public final class Json {

    private Json() {
    }

    /**
     * Parses a Librus answer. Blank text yields a {@link com.fasterxml.jackson.databind.node.MissingNode};
     * malformed text throws what {@code failure} makes of the parse error.
     */
    public static JsonNode parse(ObjectMapper mapper, String text,
                                 Function<? super JsonProcessingException, ? extends LibrusException> failure) {
        try {
            return mapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw failure.apply(e);
        }
    }

    /** Items of the array under {@code rootKey}, each mapped; a missing root yields an empty list. */
    public static <T> List<T> list(JsonNode root, String rootKey, Function<JsonNode, T> mapper) {
        JsonNode array = root.path(rootKey);
        if (!array.isArray()) {
            return List.of();
        }
        List<T> result = new ArrayList<>(array.size());
        array.forEach(item -> result.add(mapper.apply(item)));
        return List.copyOf(result);
    }

    public static JsonNode object(JsonNode root, String rootKey) {
        JsonNode node = root.path(rootKey);
        if (!node.isObject()) {
            throw new LibrusException.ProtocolError("Expected object '" + rootKey + "' in Librus response");
        }
        return node;
    }

    public static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isContainerNode()) {
            return Optional.empty();
        }
        String text = value.asText().trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    public static String textOr(JsonNode node, String field, String fallback) {
        return text(node, field).orElse(fallback);
    }

    /** Id of a reference object {@code {"Id": 123}} or {@code {"Id": "123"}}. */
    public static Optional<String> refId(JsonNode node, String field) {
        JsonNode ref = node.get(field);
        if (ref == null || ref.isNull()) {
            return Optional.empty();
        }
        return ref.isObject() ? text(ref, "Id") : Optional.of(ref.asText()).filter(s -> !s.isBlank());
    }

    public static Optional<Long> refLong(JsonNode node, String field) {
        return refId(node, field).flatMap(Json::parseLong);
    }

    public static long requiredLong(JsonNode node, String field) {
        return text(node, field).flatMap(Json::parseLong).orElseThrow(() ->
                new LibrusException.ProtocolError("Missing numeric '" + field + "' in Librus response"));
    }

    public static Optional<Long> longValue(JsonNode node, String field) {
        return text(node, field).flatMap(Json::parseLong);
    }

    public static Optional<Integer> integer(JsonNode node, String field) {
        return text(node, field).flatMap(Json::parseLong).map(Long::intValue);
    }

    public static boolean flag(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        String text = value.asText().trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    public static Optional<Boolean> optionalFlag(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? Optional.empty() : Optional.of(flag(node, field));
    }

    public static Optional<LocalDate> date(JsonNode node, String field) {
        return text(node, field).flatMap(Json::parseDate);
    }

    /** {@code yyyy-MM-dd HH:mm:ss}, {@code yyyy-MM-ddTHH:mm:ss} or a bare date (midnight). */
    public static Optional<LocalDateTime> dateTime(JsonNode node, String field) {
        return text(node, field).flatMap(Json::parseDateTime);
    }

    /** {@code HH:mm} or {@code HH:mm:ss}. */
    public static Optional<LocalTime> time(JsonNode node, String field) {
        return text(node, field).flatMap(Json::parseTime);
    }

    static Optional<Long> parseLong(String text) {
        try {
            return Optional.of(Long.parseLong(text.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    static Optional<LocalDate> parseDate(String text) {
        try {
            return Optional.of(LocalDate.parse(text.substring(0, Math.min(10, text.length()))));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    static Optional<LocalDateTime> parseDateTime(String text) {
        String normalised = text.replace(' ', 'T');
        try {
            return Optional.of(normalised.length() == 10
                    ? LocalDate.parse(normalised).atStartOfDay()
                    : LocalDateTime.parse(normalised));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    static Optional<LocalTime> parseTime(String text) {
        try {
            return Optional.of(LocalTime.parse(text));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
