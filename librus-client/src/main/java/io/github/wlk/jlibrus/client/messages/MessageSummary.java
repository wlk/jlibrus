package io.github.wlk.jlibrus.client.messages;

import java.time.LocalDateTime;

/**
 * A message as listed in a mailbox (listing never marks it read).
 *
 * @param counterpart sender for received mail, recipients for sent mail
 * @param preview     beginning of the body as plain text
 */
public record MessageSummary(String id, Mailbox mailbox, String counterpart, String topic, String preview,
                             LocalDateTime sentAt, LocalDateTime readAt, boolean hasAttachments) {

    public boolean isUnread() {
        return readAt == null;
    }
}
