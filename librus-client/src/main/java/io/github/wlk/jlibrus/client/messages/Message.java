package io.github.wlk.jlibrus.client.messages;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A full message. Fetching it marks it read in Librus.
 *
 * @param senderAccountId recipient id to use when replying
 * @param replyAllowed    false when Librus refuses replies to this message
 */
public record Message(String id, Mailbox mailbox, String senderAccountId, String senderName, String topic,
                      String body, LocalDateTime sentAt, LocalDateTime readAt, boolean replyAllowed,
                      List<MessageAttachment> attachments, List<MessageReceiver> receivers) {

    public Message {
        attachments = List.copyOf(attachments);
        receivers = List.copyOf(receivers);
    }
}
