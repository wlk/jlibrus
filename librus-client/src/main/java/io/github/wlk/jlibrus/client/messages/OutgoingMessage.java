package io.github.wlk.jlibrus.client.messages;

import io.github.wlk.jlibrus.client.Identifiers;

import java.util.List;
import java.util.Objects;

/**
 * A message to send, validated on construction.
 *
 * @param recipientIds account ids from {@link Recipient#accountId()} or {@link Message#senderAccountId()}
 * @param replyToId    id of the message being answered, or {@code null} for a new thread
 */
public record OutgoingMessage(List<String> recipientIds, String topic, String text, String replyToId) {

    public static final int MAX_RECIPIENTS = 50;
    public static final int MAX_TOPIC_LENGTH = 200;
    public static final int MAX_TEXT_LENGTH = 10_000;

    public OutgoingMessage {
        recipientIds = recipients(recipientIds);
        topic = bounded(topic, MAX_TOPIC_LENGTH, "Topic");
        text = bounded(text, MAX_TEXT_LENGTH, "Text");
        if (replyToId != null && !Identifiers.isNumericId(replyToId)) {
            throw new IllegalArgumentException("replyToId must be numeric");
        }
    }

    private static List<String> recipients(List<String> raw) {
        Objects.requireNonNull(raw, "recipientIds");
        List<String> ids = raw.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (ids.isEmpty() || ids.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException("A message needs 1-" + MAX_RECIPIENTS + " recipients");
        }
        if (!ids.stream().allMatch(Identifiers::isNumericId)) {
            throw new IllegalArgumentException("Recipient ids must be numeric");
        }
        return ids;
    }

    private static String bounded(String raw, int max, String label) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty() || value.length() > max) {
            throw new IllegalArgumentException(label + " must be 1-" + max + " characters");
        }
        return value;
    }

    public static OutgoingMessage newThread(List<String> recipientIds, String topic, String text) {
        return new OutgoingMessage(recipientIds, topic, text, null);
    }
}
