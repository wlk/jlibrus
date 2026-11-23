package io.github.wlk.jlibrus.client.messages;

import java.time.LocalDateTime;

/** Recipient of a sent message with read receipt ({@code readAt} null when unread). */
public record MessageReceiver(String accountId, String name, LocalDateTime readAt) {
}
