package io.github.wlk.jlibrus.client;

import java.net.URI;
import java.util.Objects;

/**
 * Base addresses of the Librus services. Production values are the default; tests and demo mode point
 * every service at one mock host with {@link #singleHost(URI)}.
 *
 * @param synergia Synergia web + JSON gateway (cookies)
 * @param oauth    OAuth login server
 * @param messages messages module ("Wiadomości")
 */
public record LibrusEndpoints(URI synergia, URI oauth, URI messages) {

    public static final String GATEWAY_PATH = "/gateway/api/2.0/";

    public LibrusEndpoints {
        Objects.requireNonNull(synergia, "synergia");
        Objects.requireNonNull(oauth, "oauth");
        Objects.requireNonNull(messages, "messages");
    }

    public static LibrusEndpoints production() {
        return new LibrusEndpoints(URI.create("https://synergia.librus.pl"),
                URI.create("https://api.librus.pl"),
                URI.create("https://wiadomosci.librus.pl"));
    }

    /** All services on one host, e.g. {@code http://localhost:8090} of the mock server. */
    public static LibrusEndpoints singleHost(URI base) {
        URI root = stripTrailingSlash(base);
        return new LibrusEndpoints(root, root, root);
    }

    public URI gateway(String resource) {
        return URI.create(synergia + GATEWAY_PATH + resource);
    }

    public URI synergiaPath(String path) {
        return URI.create(synergia + path);
    }

    public URI oauthPath(String path) {
        return URI.create(oauth + path);
    }

    public URI messagesPath(String path) {
        return URI.create(messages + path);
    }

    /** Whether a login redirect may be followed to this address (only our own services, same scheme). */
    public boolean isTrustedLoginHop(URI target) {
        return target.getUserInfo() == null && (sameOrigin(target, synergia) || sameOrigin(target, oauth));
    }

    /** Whether a redirect may be followed while opening the messages module. */
    public boolean isOwnService(URI target) {
        return isTrustedLoginHop(target) || (target.getUserInfo() == null && sameOrigin(target, messages));
    }

    private static boolean sameOrigin(URI a, URI b) {
        return sameIgnoringCase(a.getScheme(), b.getScheme()) && sameIgnoringCase(a.getHost(), b.getHost())
                && a.getPort() == b.getPort();
    }

    private static boolean sameIgnoringCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    private static URI stripTrailingSlash(URI uri) {
        String text = uri.toString();
        return URI.create(text.endsWith("/") ? text.substring(0, text.length() - 1) : text);
    }
}
