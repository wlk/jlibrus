package io.github.wlk.jlibrus.client.messages;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.http.HttpResult;
import io.github.wlk.jlibrus.client.session.SynergiaSession;

import java.net.URI;
import java.util.concurrent.locks.ReentrantLock;

/** Opens the messages-module session from a Synergia session by walking the {@code /wiadomosci3} redirects. */
final class MessagesSessionOpener {

    private static final int MAX_HOPS = 10;

    private final SynergiaSession session;
    private final String noAccessMarker;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean open;

    MessagesSessionOpener(SynergiaSession session, String noAccessMarker) {
        this.session = session;
        this.noAccessMarker = noAccessMarker;
    }

    void ensureOpen() {
        lock.lock();
        try {
            if (!open) {
                walk();
                open = true;
            }
        } finally {
            lock.unlock();
        }
    }

    void reopen() {
        lock.lock();
        try {
            open = false;
            walk();
            open = true;
        } finally {
            lock.unlock();
        }
    }

    private void walk() {
        URI current = session.endpoints().synergiaPath("/wiadomosci3");
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            HttpResult result = session.http().get(current);
            if (!result.isRedirect()) {
                if (result.body().contains(noAccessMarker)) {
                    throw new LibrusException.ModuleUnavailable("The messages module is not enabled for this account");
                }
                return;
            }
            URI next = result.location().orElseThrow();
            if (!session.endpoints().isOwnService(next)) {
                throw new LibrusException.ProtocolError("Messages login redirected to an unexpected host");
            }
            current = next;
        }
        throw new LibrusException.ProtocolError("Messages login redirected more than " + MAX_HOPS + " times");
    }
}
