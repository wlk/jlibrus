package io.github.wlk.jlibrus.client.http;

import io.github.wlk.jlibrus.client.LibrusEndpoints;
import io.github.wlk.jlibrus.client.messages.OutgoingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Status boundaries, cookie bookkeeping, origin checks and message limits. */
class TransportEdgesTest {

    private static HttpResult result(int status, String body, boolean location) {
        Map<String, List<String>> headers = location ? Map.of("Location", List.of("/next")) : Map.of();
        return new HttpResult(URI.create("https://a.pl/x"), status, HttpHeaders.of(headers, (a, b) -> true), body);
    }

    @ParameterizedTest
    @CsvSource({"199, false, false", "200, true, false", "299, true, false", "300, false, true", "399, false, true",
            "400, false, false"})
    void statusBoundaries(int status, boolean success, boolean redirect) {
        assertThat(result(status, "", true).isSuccess()).isEqualTo(success);
        assertThat(result(status, "", true).isRedirect()).isEqualTo(redirect);
    }

    @Test
    void jsonDetectionIgnoresLeadingWhitespace() {
        assertThat(result(200, " \n[1]", false).looksLikeJson()).isTrue();
        assertThat(result(200, "\t{\"a\":1}", false).looksLikeJson()).isTrue();
        assertThat(result(200, "<html>", false).looksLikeJson()).isFalse();
    }

    @Test
    void cookieJarKeepsDistinctNamesAndPathsAndIgnoresOtherHeaders() {
        CookieJar jar = new CookieJar();
        URI site = URI.create("https://a.pl/x/y");
        jar.put(site, Map.of("Set-Cookie", List.of("a=1; Path=/")));
        jar.put(site, Map.of("set-cookie", List.of("b=2; Path=/")));
        jar.put(site, Map.of("Set-Cookie", List.of("a=3; Path=/x")));
        Map<String, List<String>> other = new HashMap<>();
        other.put(null, List.of("HTTP/1.1 200 OK"));
        other.put("Location", List.of("c=4"));
        jar.put(site, other);

        String header = jar.get(URI.create("https://a.pl/x/z"), Map.of()).get("Cookie").getFirst();

        assertThat(header.split("; ")).containsExactlyInAnyOrder("a=1", "b=2", "a=3");
        assertThat(jar.get(URI.create("https://a.pl"), Map.of()).get("Cookie").getFirst().split("; "))
                .containsExactlyInAnyOrder("a=1", "b=2");
        assertThat(CookieJar.defaultPath(URI.create("mailto:a@b.pl"))).isEqualTo("/");
        assertThat(CookieJar.defaultPath(URI.create("x/y"))).isEqualTo("/");
        assertThat(jar.get(URI.create("https://b.pl/"), Collections.emptyMap())).isEmpty();
    }

    @Test
    void ownServiceRequiresSameSchemeHostPortAndNoUserInfo() {
        LibrusEndpoints endpoints = LibrusEndpoints.production();

        assertThat(endpoints.isOwnService(URI.create("https://wiadomosci.librus.pl/api"))).isTrue();
        assertThat(endpoints.isOwnService(URI.create("https://u@wiadomosci.librus.pl/api"))).isFalse();
        assertThat(endpoints.isOwnService(URI.create("http://wiadomosci.librus.pl/api"))).isFalse();
        assertThat(endpoints.isOwnService(URI.create("https://wiadomosci.librus.pl:444/api"))).isFalse();
        assertThat(endpoints.isOwnService(URI.create("mailto:a@wiadomosci.librus.pl"))).isFalse();
        assertThat(endpoints.isOwnService(URI.create("/relative"))).isFalse();
    }

    @Test
    void outgoingMessageLimitsAreInclusive() {
        List<String> fifty = IntStream.rangeClosed(1, 50).mapToObj(String::valueOf).toList();
        List<String> fiftyOne = IntStream.rangeClosed(1, 51).mapToObj(String::valueOf).toList();

        assertThat(OutgoingMessage.newThread(fifty, "t".repeat(200), "x".repeat(10_000)).recipientIds()).hasSize(50);
        assertThatThrownBy(() -> OutgoingMessage.newThread(fiftyOne, "t", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutgoingMessage.newThread(List.of("1"), "t", "x".repeat(10_001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(OutgoingMessage.newThread(List.of(" ", "7"), "t", "x").recipientIds()).containsExactly("7");
    }
}
