package io.github.wlk.jlibrus.client.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CookieJarTest {

    private static final URI SYNERGIA = URI.create("https://synergia.librus.pl/loguj/portalRodzina");
    private static final URI API = URI.create("https://api.librus.pl/OAuth/Authorization/2FA");
    private final CookieJar jar = new CookieJar();

    private void set(URI uri, String... headers) {
        jar.put(uri, Map.of("Set-Cookie", List.of(headers)));
    }

    private String sent(String uri) {
        return jar.get(URI.create(uri), Map.of()).getOrDefault("Cookie", List.of("")).getFirst();
    }

    @Test
    void sendsPlainNameValuePairsEvenForMaxAgeCookies() {
        set(SYNERGIA, "oauth_token=abc; Path=/; Max-Age=600; HttpOnly", "DZIENNIKSID=x1; path=/");

        assertThat(sent("https://synergia.librus.pl/gateway/api/2.0/Me"))
                .isIn("oauth_token=abc; DZIENNIKSID=x1", "DZIENNIKSID=x1; oauth_token=abc");
    }

    @Test
    void respectsPathAndHost() {
        set(API, "DeviceCookie=dev; Path=/OAuth; Max-Age=31536000");

        assertThat(sent("https://api.librus.pl/OAuth/Authorization")).isEqualTo("DeviceCookie=dev");
        assertThat(sent("https://api.librus.pl/OAuthX")).isEmpty();
        assertThat(sent("https://api.librus.pl/2.0/Me")).isEmpty();
        assertThat(sent("https://synergia.librus.pl/OAuth/x")).isEmpty();
    }

    @Test
    void renewedCookieReplacesOldValue() {
        set(SYNERGIA, "oauth_token=old; Path=/; Max-Age=600");
        set(URI.create("https://synergia.librus.pl/refreshToken"), "oauth_token=new; Path=/; Max-Age=600");

        assertThat(sent("https://synergia.librus.pl/gateway/api/2.0/Me")).isEqualTo("oauth_token=new");
    }

    @Test
    void zeroMaxAgeDeletes() {
        set(SYNERGIA, "oauth_token=old; Path=/");
        set(SYNERGIA, "oauth_token=deleted; Path=/; Max-Age=0");

        assertThat(sent("https://synergia.librus.pl/")).isEmpty();
    }

    @Test
    void secureCookiesStayOnHttps() {
        set(SYNERGIA, "s=1; Path=/; Secure");

        assertThat(sent("https://synergia.librus.pl/")).isEqualTo("s=1");
        assertThat(sent("http://synergia.librus.pl/")).isEmpty();
    }

    @Test
    void rejectsCookiesForForeignDomainsAndIgnoresGarbage() {
        set(SYNERGIA, "evil=1; Domain=example.com; Path=/", "=");

        assertThat(sent("https://example.com/")).isEmpty();
        assertThat(sent("https://synergia.librus.pl/")).isEmpty();
    }

    @Test
    void sharedParentDomainCookieIsVisibleOnSubdomains() {
        set(API, "shared=1; Domain=.librus.pl; Path=/");

        assertThat(sent("https://synergia.librus.pl/")).isEqualTo("shared=1");
    }

    @Test
    void defaultPathIsTheRequestDirectory() {
        assertThat(CookieJar.defaultPath(URI.create("https://a.pl/x/y/z"))).isEqualTo("/x/y");
        assertThat(CookieJar.defaultPath(URI.create("https://a.pl/x"))).isEqualTo("/");
        assertThat(CookieJar.defaultPath(URI.create("https://a.pl"))).isEqualTo("/");
        assertThat(CookieJar.pathMatches("/OAuth/", "/OAuth/")).isTrue();
        assertThat(CookieJar.pathMatches("/OAuth/x", "/OAuth/")).isTrue();
    }

    @Test
    void domainWithoutLeadingDotCoversSubdomains() {
        set(API, "shared=1; Domain=librus.pl; Path=/; Max-Age=600");

        assertThat(sent("https://synergia.librus.pl/gateway")).isEqualTo("shared=1");
        assertThat(sent("https://librus.pl/")).isEqualTo("shared=1");
        assertThat(sent("https://notlibrus.pl/")).isEmpty();
    }

    @Test
    void cookieWithoutPathDefaultsToRequestDirectory() {
        set(URI.create("https://a.pl/x/y"), "p=1");

        assertThat(sent("https://a.pl/x/z")).isEqualTo("p=1");
        assertThat(sent("https://a.pl/q")).isEmpty();
    }

    @Test
    void sameNameOnAnotherHostIsNotReplaced() {
        set(URI.create("https://a.pl/"), "t=1; Path=/");
        set(URI.create("https://b.pl/"), "t=2; Path=/");

        assertThat(sent("https://a.pl/")).isEqualTo("t=1");
        assertThat(sent("https://b.pl/")).isEqualTo("t=2");
    }

    @Test
    void quotedValuesAreSentWithoutQuotes() {
        set(SYNERGIA, "q=\"v\"; Path=/");

        assertThat(sent("https://synergia.librus.pl/")).isEqualTo("q=v");
        assertThat(CookieJar.unquote("\"x\"")).isEqualTo("x");
        assertThat(CookieJar.unquote("\"\"")).isEmpty();
        assertThat(CookieJar.unquote("\"")).isEqualTo("\"");
        assertThat(CookieJar.unquote("\"x")).isEqualTo("\"x");
        assertThat(CookieJar.unquote("x\"")).isEqualTo("x\"");
    }

    @Test
    void pathAndDomainRules() {
        assertThat(CookieJar.pathMatches("/a", null)).isTrue();
        assertThat(CookieJar.pathMatches("/a", "")).isTrue();
        assertThat(CookieJar.pathMatches("/a", "/a")).isTrue();
        assertThat(CookieJar.pathMatches("/a/b", "/a")).isTrue();
        assertThat(CookieJar.pathMatches("/ab", "/a")).isFalse();
        assertThat(CookieJar.pathMatches("/b", "/a")).isFalse();
        assertThat(CookieJar.pathMatches("", "/a")).isFalse();
        assertThat(CookieJar.defaultPath(URI.create("https://a.pl/x"))).isEqualTo("/");
        assertThat(CookieJar.defaultPath(URI.create("https://a.pl/x/"))).isEqualTo("/x");
        assertThat(CookieJar.domainMatches("A.PL", "a.pl")).isTrue();
        assertThat(CookieJar.domainMatches(null, "a.pl")).isFalse();
        assertThat(CookieJar.domainMatches("a.pl", "")).isFalse();
        assertThat(CookieJar.normaliseDomain(".Librus.PL")).isEqualTo("librus.pl");
    }
}
