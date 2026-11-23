package io.github.wlk.jlibrus.client.messages;

/** Librus accepted the message; replies come back without an id. */
public record SentMessage(Long messageId) {
}
