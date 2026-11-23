package io.github.wlk.jlibrus.mock.fixtures;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Small JSON-building helpers shared by the fixture generators. */
final class Nodes {

    static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Nodes() {
    }

    static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    /** Librus-style reference object. */
    static ObjectNode ref(long id, String resource) {
        return object().put("Id", id).put("Url", "https://api.librus.pl/2.0/" + resource + "/" + id);
    }

    static ObjectNode root(String key, ArrayNode items) {
        ObjectNode root = object();
        root.set(key, items);
        return root;
    }

    static String timestamp(LocalDateTime value) {
        return TIMESTAMP.format(value);
    }

    static String date(LocalDate value) {
        return value.toString();
    }

    /** Deterministic pseudo-random number in {@code [0, bound)} for the given inputs. */
    static int pick(int bound, long... parts) {
        long h = 1125899906842597L;
        for (long part : parts) {
            h = 31 * h + part;
            h ^= (h >>> 17);
        }
        return (int) Math.floorMod(h, (long) bound);
    }
}
