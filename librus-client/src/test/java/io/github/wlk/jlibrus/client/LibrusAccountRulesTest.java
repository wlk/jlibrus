package io.github.wlk.jlibrus.client;

import io.github.wlk.jlibrus.client.http.HttpResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibrusAccountRulesTest {

    private static HttpResult result(int status, String body) {
        return new HttpResult(URI.create("https://x/y"), status, HttpHeaders.of(Map.of(), (a, b) -> true), body);
    }

    @Test
    void alreadyReadAnnouncementCountsAsMarked() {
        assertThat(LibrusAccount.isMarkedRead(result(200, "{}"))).isTrue();
        assertThat(LibrusAccount.isMarkedRead(result(400, "{\"Code\":\"InvalidRequest\"}"))).isTrue();
        assertThat(LibrusAccount.isMarkedRead(result(400, "{\"Code\":\"Other\"}"))).isFalse();
        assertThat(LibrusAccount.isMarkedRead(result(500, "InvalidRequest"))).isFalse();
    }

    @Test
    void announcementIdIsValidatedBeforeAnyRequest() {
        LibrusAccount account = LibrusAccount.builder(new Credentials("x", "y"))
                .endpoints(LibrusEndpoints.singleHost(URI.create("http://127.0.0.1:1"))).build();

        assertThatThrownBy(() -> account.markAnnouncementRead("../../x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.markAnnouncementRead(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(account.login()).isEqualTo("x");
    }
}
