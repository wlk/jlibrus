package io.github.wlk.jlibrus.client.messages;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.http.HttpResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesFailureTest {

    @ParameterizedTest
    @CsvSource({"404, ModuleUnavailable", "500, ServiceUnavailable", "503, ServiceUnavailable",
            "429, ServiceUnavailable", "401, SessionExpired", "403, SessionExpired"})
    void failuresAreClassified(int status, String type) {
        LibrusException failure = MessagesClient.failure("/inbox/messages", status);
        assertThat(failure.getClass().getSimpleName()).isEqualTo(type);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "401|{}|true", "403|{}|true", "400|{}|true", "404|{}|false", "429|{}|false", "500|{}|false",
            "200|<html>login</html>|true", "200|{\"data\":[]}|false", "200| |false"})
    void decidesWhenTheMessagesSessionMustBeReopened(int status, String body, boolean expected) {
        HttpResult result = new HttpResult(URI.create("https://wiadomosci.librus.pl/api/me"), status,
                HttpHeaders.of(Map.of(), (a, b) -> true), body == null ? "" : body);
        assertThat(MessagesClient.needsNewSession(result)).isEqualTo(expected);
    }
}
