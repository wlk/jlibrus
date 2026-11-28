package io.github.wlk.jlibrus.client.messages;

import io.github.wlk.jlibrus.client.LibrusEndpoints;
import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.http.HttpResult;
import io.github.wlk.jlibrus.client.session.SynergiaSession;

import java.util.concurrent.locks.ReentrantLock;

/** Opens the messages-module session from a Synergia session by walking the {@code /wiadomosci3} redirects. */
final class MessagesSessionOpener {

    /** Page Librus shows instead of the module when the school switched messages off for this account. */
    private static final String NO_ACCESS_MARKER = "Brak dostępu";

    private final SynergiaSession session;
    private final ReentrantLock lock = new ReentrantLock();
    private boolean open;

    MessagesSessionOpener(SynergiaSession session) {
        this.session = session;
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
        LibrusEndpoints endpoints = session.endpoints();
        HttpResult landing = session.http().followRedirects(
                endpoints.synergiaPath("/wiadomosci3"), endpoints::isOwnService, "Messages login");
        if (landing.body().contains(NO_ACCESS_MARKER)) {
            throw new LibrusException.ModuleUnavailable("The messages module is not enabled for this account");
        }
    }
}
