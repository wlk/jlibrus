package io.github.wlk.jlibrus.client.http;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RFC 6265 style cookie handling. {@link CookieManager} turns cookies carrying {@code Max-Age} into RFC 2965
 * cookies and sends them back as {@code $Version="1"; name="value"; $Path="/"}, which servers such as
 * Librus do not understand. This jar stores cookies in the JDK store but always sends plain
 * {@code name=value} pairs, honouring domain, path, secure flag and expiry.
 */
final class CookieJar extends CookieHandler {

    private final CookieStore store = new CookieManager().getCookieStore();

    CookieStore store() {
        return store;
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) {
        String path = Objects.toString(uri.getPath(), "");
        // the JDK store already leaves out expired cookies and secure cookies on plain http
        String header = store.get(uri).stream()
                .filter(c -> pathMatches(path, c.getPath()))
                .map(c -> c.getName() + "=" + unquote(c.getValue()))
                .distinct()
                .collect(Collectors.joining("; "));
        return header.isEmpty() ? Map.of() : Map.of("Cookie", List.of(header));
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) {
        responseHeaders.forEach((name, values) -> {
            if ("Set-Cookie".equalsIgnoreCase(name)) {
                values.forEach(value -> store(uri, value));
            }
        });
    }

    private void store(URI uri, String header) {
        List<HttpCookie> cookies;
        try {
            cookies = HttpCookie.parse(header);
        } catch (IllegalArgumentException e) {
            return;
        }
        for (HttpCookie cookie : cookies) {
            // version 0 = Netscape/RFC 6265 matching in the JDK store (version 1 would apply RFC 2965 rules)
            cookie.setVersion(0);
            if (cookie.getPath() == null) {
                cookie.setPath(defaultPath(uri));
            }
            if (cookie.getDomain() != null) {
                String domain = normaliseDomain(cookie.getDomain());
                if (!domainMatches(uri.getHost(), domain)) {
                    continue;
                }
                cookie.setDomain("." + domain);
            }
            replace(uri, cookie);
        }
    }

    /** One value per (name, domain, path): drop older copies so a renewed token never travels twice. */
    private void replace(URI uri, HttpCookie cookie) {
        store.getCookies().stream()
                .filter(old -> old.getName().equals(cookie.getName()))
                .filter(old -> samePath(old, cookie))
                .filter(old -> store.get(uri).contains(old))
                .toList()
                .forEach(old -> store.remove(null, old));
        store.add(uri, cookie); // the JDK store ignores cookies with Max-Age=0, which completes a deletion
    }

    /** RFC 6265 section 5.1.3: the host is the domain itself or one of its subdomains. */
    static boolean domainMatches(String host, String domain) {
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        return !domain.isEmpty() && (h.equals(domain) || h.endsWith("." + domain));
    }

    /** A leading dot is ignored (RFC 6265 section 5.2.3). */
    static String normaliseDomain(String domain) {
        String lower = domain.toLowerCase(Locale.ROOT);
        return lower.startsWith(".") ? lower.substring(1) : lower;
    }

    private static boolean samePath(HttpCookie a, HttpCookie b) {
        return String.valueOf(a.getPath()).equals(String.valueOf(b.getPath()));
    }

    static boolean pathMatches(String requestPath, String cookiePath) {
        if (cookiePath == null || cookiePath.isEmpty() || "/".equals(cookiePath)) {
            return true;
        }
        return requestPath.equals(cookiePath)
                || (requestPath.startsWith(cookiePath)
                && (cookiePath.endsWith("/") || requestPath.charAt(cookiePath.length()) == '/'));
    }

    static String defaultPath(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty() || !path.startsWith("/")) {
            return "/";
        }
        int last = path.lastIndexOf('/');
        return last <= 0 ? "/" : path.substring(0, last);
    }

    static String unquote(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }
}
