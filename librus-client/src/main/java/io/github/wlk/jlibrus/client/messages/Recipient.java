package io.github.wlk.jlibrus.client.messages;

/** Someone a message can be sent to. */
public record Recipient(String accountId, String name, String groupId, String groupName) {
}
