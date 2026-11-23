package io.github.wlk.jlibrus.client.http;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.util.Optional;

/** Raw HTTP answer: status, headers, body and the address that produced it. */
public record HttpResult(URI uri, int status, HttpHeaders headers, String body) {

    public boolean isRedirect() {
        return status >= 300 && status < 400 && location().isPresent();
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }

    /** {@code Location} header resolved against the request address. */
    public Optional<URI> location() {
        return headers.firstValue("Location").map(String::trim).filter(s -> !s.isEmpty()).map(uri::resolve);
    }

    public boolean looksLikeJson() {
        String trimmed = body.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
