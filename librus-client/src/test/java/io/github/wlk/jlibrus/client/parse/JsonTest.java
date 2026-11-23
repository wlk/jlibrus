package io.github.wlk.jlibrus.client.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wlk.jlibrus.client.LibrusException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode node(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void readsReferenceIdsGivenAsNumbersOrStrings() throws Exception {
        JsonNode node = node("{\"A\":{\"Id\":42},\"B\":{\"Id\":\"43\"},\"C\":null,\"D\":7,\"E\":{\"Id\":\"t41\"}}");

        assertThat(Json.refLong(node, "A")).contains(42L);
        assertThat(Json.refLong(node, "B")).contains(43L);
        assertThat(Json.refLong(node, "C")).isEmpty();
        assertThat(Json.refLong(node, "missing")).isEmpty();
        assertThat(Json.refId(node, "D")).contains("7");
        assertThat(Json.refLong(node, "E")).isEmpty();
        assertThat(Json.refId(node, "E")).contains("t41");
    }

    @Test
    void textTrimsAndTreatsBlankAndContainersAsMissing() throws Exception {
        JsonNode node = node("{\"a\":\"  x \",\"b\":\"  \",\"c\":{},\"d\":5}");

        assertThat(Json.text(node, "a")).contains("x");
        assertThat(Json.text(node, "b")).isEmpty();
        assertThat(Json.text(node, "c")).isEmpty();
        assertThat(Json.text(node, "d")).contains("5");
        assertThat(Json.textOr(node, "zzz", "fallback")).isEqualTo("fallback");
    }

    @Test
    void flagsAcceptBooleansNumbersAndStrings() throws Exception {
        JsonNode node = node("{\"a\":true,\"b\":1,\"c\":\"1\",\"d\":\"true\",\"e\":0,\"f\":null,\"g\":false}");

        assertThat(Json.flag(node, "a")).isTrue();
        assertThat(Json.flag(node, "b")).isTrue();
        assertThat(Json.flag(node, "c")).isTrue();
        assertThat(Json.flag(node, "d")).isTrue();
        assertThat(Json.flag(node, "e")).isFalse();
        assertThat(Json.flag(node, "f")).isFalse();
        assertThat(Json.flag(node, "g")).isFalse();
        assertThat(Json.flag(node, "missing")).isFalse();
        assertThat(Json.optionalFlag(node, "f")).isEmpty();
        assertThat(Json.optionalFlag(node, "g")).contains(false);
    }

    @Test
    void parsesLibrusDateAndTimeFormats() throws Exception {
        JsonNode node = node("{\"d\":\"2026-11-23\",\"ts\":\"2026-11-23 08:15:00\",\"iso\":\"2026-11-23T08:15:00\","
                + "\"t1\":\"08:00\",\"t2\":\"17:00:00\",\"bad\":\"soon\",\"dateWithTime\":\"2026-11-23 10:00:00\"}");

        assertThat(Json.date(node, "d")).contains(LocalDate.of(2026, 11, 23));
        assertThat(Json.date(node, "dateWithTime")).contains(LocalDate.of(2026, 11, 23));
        assertThat(Json.dateTime(node, "ts")).contains(LocalDateTime.of(2026, 11, 23, 8, 15));
        assertThat(Json.dateTime(node, "iso")).contains(LocalDateTime.of(2026, 11, 23, 8, 15));
        assertThat(Json.dateTime(node, "d")).contains(LocalDateTime.of(2026, 11, 23, 0, 0));
        assertThat(Json.time(node, "t1")).contains(LocalTime.of(8, 0));
        assertThat(Json.time(node, "t2")).contains(LocalTime.of(17, 0));
        assertThat(Json.date(node, "bad")).isEmpty();
        assertThat(Json.dateTime(node, "bad")).isEmpty();
        assertThat(Json.time(node, "bad")).isEmpty();
    }

    @Test
    void integersAcceptNumericStrings() throws Exception {
        JsonNode node = node("{\"a\":\"3\",\"b\":4,\"c\":\"x\"}");

        assertThat(Json.integer(node, "a")).contains(3);
        assertThat(Json.integer(node, "b")).contains(4);
        assertThat(Json.integer(node, "c")).isEmpty();
        assertThat(Json.requiredLong(node, "b")).isEqualTo(4L);
        assertThatThrownBy(() -> Json.requiredLong(node, "c")).isInstanceOf(LibrusException.ProtocolError.class);
    }

    @Test
    void listsMapItemsAndToleratesMissingRoot() throws Exception {
        JsonNode node = node("{\"Items\":[{\"Id\":1},{\"Id\":2}]}");

        assertThat(Json.list(node, "Items", n -> Json.requiredLong(n, "Id"))).containsExactly(1L, 2L);
        assertThat(Json.list(node, "Other", n -> 1)).isEmpty();
        assertThatThrownBy(() -> Json.object(node, "Missing")).isInstanceOf(LibrusException.ProtocolError.class);
    }
}
