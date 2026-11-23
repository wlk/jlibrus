package io.github.wlk.jlibrus.client.session;

import io.github.wlk.jlibrus.client.Credentials;
import io.github.wlk.jlibrus.client.LibrusEndpoints;
import io.github.wlk.jlibrus.client.http.HttpResult;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayRulesTest {

    private static HttpResult result(int status, String body, String location) {
        Map<String, List<String>> headers = location == null ? Map.of() : Map.of("Location", List.of(location));
        return new HttpResult(URI.create("https://synergia.librus.pl/gateway/api/2.0/Me"), status,
                HttpHeaders.of(headers, (a, b) -> true), body);
    }

    @Test
    void plain401AndHtmlInsteadOfJsonMeanExpired() {
        assertThat(Gateway.isExpired(result(401, "{\"Code\":\"TokenIsExpired\"}", null))).isTrue();
        assertThat(Gateway.isExpired(result(200, "<html>login</html>", null))).isTrue();
    }

    @Test
    void insufficientScopesAndJsonAreNotExpiry() {
        assertThat(Gateway.isExpired(result(401, "{\"Message\":\"Insufficient scopes\"}", null))).isFalse();
        assertThat(Gateway.isExpired(result(200, "{\"Me\":{}}", null))).isFalse();
        assertThat(Gateway.isExpired(result(403, "{}", null))).isFalse();
    }

    @Test
    void redirectLocationIsResolvedAgainstRequest() {
        HttpResult redirect = result(302, "", "/OAuth/Authorization/Grant?client_id=46");

        assertThat(redirect.isRedirect()).isTrue();
        assertThat(redirect.location()).contains(URI.create("https://synergia.librus.pl/OAuth/Authorization/Grant?client_id=46"));
        assertThat(result(302, "", null).isRedirect()).isFalse();
        assertThat(result(302, "", " ").location()).isEmpty();
    }

    @Test
    void loginRedirectsStayOnLibrusHosts() {
        LibrusEndpoints production = LibrusEndpoints.production();

        assertThat(production.isTrustedLoginHop(URI.create("https://api.librus.pl/OAuth/Authorization/2FA"))).isTrue();
        assertThat(production.isTrustedLoginHop(URI.create("https://synergia.librus.pl/loguj/portalRodzina?code=1"))).isTrue();
        assertThat(production.isTrustedLoginHop(URI.create("http://synergia.librus.pl/x"))).isFalse();
        assertThat(production.isTrustedLoginHop(URI.create("https://evil.example/x"))).isFalse();
        assertThat(production.isTrustedLoginHop(URI.create("https://user@api.librus.pl/x"))).isFalse();
        assertThat(production.isTrustedLoginHop(URI.create("https://wiadomosci.librus.pl/x"))).isFalse();
        assertThat(production.isOwnService(URI.create("https://wiadomosci.librus.pl/x"))).isTrue();
        assertThat(production.isTrustedLoginHop(URI.create("https://api.librus.pl:8443/x"))).isFalse();
    }

    @Test
    void singleHostEndpointsStripTrailingSlash() {
        LibrusEndpoints mock = LibrusEndpoints.singleHost(URI.create("http://localhost:8090/"));

        assertThat(mock.gateway("Me")).isEqualTo(URI.create("http://localhost:8090/gateway/api/2.0/Me"));
        assertThat(mock.messagesPath("/api/me")).isEqualTo(URI.create("http://localhost:8090/api/me"));
        assertThat(mock.oauthPath("/OAuth")).isEqualTo(URI.create("http://localhost:8090/OAuth"));
    }

    @Test
    void credentialsNeverPrintThePassword() {
        assertThat(new Credentials(" 1234567u ", "s3cret").toString()).doesNotContain("s3cret").contains("1234567u");
        assertThat(new Credentials(" 1234567u ", "s3cret").login()).isEqualTo("1234567u");
        assertThatThrownBy(() -> new Credentials(" ", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Credentials("x", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "401, ModuleUnavailable", "403, ModuleUnavailable", "404, ModuleUnavailable",
            "429, ServiceUnavailable", "502, ServiceUnavailable", "503, ServiceUnavailable", "504, ServiceUnavailable",
            "500, ProtocolError", "418, ProtocolError"})
    void gatewayFailuresAreClassified(int status, String type) {
        assertThat(Gateway.failure("Grades", status).getClass().getSimpleName()).isEqualTo(type);
    }
}
