package io.github.wlk.jlibrus.client.http;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        boolean secure = "https".equalsIgnoreCase(uri.getScheme());
        String header = store.get(uri).stream()
                .filter(c -> !c.hasExpired())
                .filter(c -> pathMatches(path, c.getPath()))
                .filter(c -> secure || !c.getSecure())
                .map(c -> c.getName() + "=" + unquote(c.getValue()))
                .distinct()
                .collect(Collectors.joining("; "));
        return header.isEmpty() ? Map.of() : Map.of("Cookie", List.of(header));
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) {
        responseHeaders.forEach((name, values) -> {
            if (name != null && "Set-Cookie".equalsIgnoreCase(name)) {
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
            cookie.setVersion(0);
            if (cookie.getPath() == null) {
                cookie.setPath(defaultPath(uri));
            }
            if (cookie.getDomain() != null && !HttpCookie.domainMatches(cookie.getDomain(), uri.getHost())
                    && !cookie.getDomain().equalsIgnoreCase(uri.getHost())) {
                continue;
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
        if (cookie.getMaxAge() != 0) {
            store.add(uri, cookie);
        }
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

    private static String unquote(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }
}
