package io.github.wlk.jlibrus.client.messages;

import java.util.List;

/** One page of a mailbox; {@code total} counts every message in the mailbox. */
public record MessagePage(List<MessageSummary> items, int total, int page, int limit) {

    public MessagePage {
        items = List.copyOf(items);
    }

    public boolean hasNext() {
        return (long) page * limit < total;
    }
}
