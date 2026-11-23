package io.github.wlk.jlibrus.client.messages;

/** Mailboxes of the messages module and their API path segment. */
public enum Mailbox {
    INBOX("inbox"),
    OUTBOX("outbox"),
    ARCHIVE_INBOX("archive/inbox"),
    SUBSTITUTIONS("substitutions"),
    ALERTS("alerts"),
    JUSTIFICATIONS("justifications");

    private final String path;

    Mailbox(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
