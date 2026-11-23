package io.github.wlk.jlibrus.client.messages;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.wlk.jlibrus.client.parse.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Parses the JSON of the messages module ({@code wiadomosci.librus.pl/api}). */
final class MessageParser {

    private static final int PREVIEW_LENGTH = 200;

    private MessageParser() {
    }

    static MessagePage page(JsonNode root, Mailbox mailbox, int page, int limit) {
        JsonNode items = root.isArray() ? root : root.path("data");
        List<MessageSummary> summaries = new ArrayList<>();
        items.forEach(item -> summaries.add(summary(item, mailbox)));
        int total = Json.integer(root, "total").orElse(summaries.size());
        return new MessagePage(summaries, total, page, limit);
    }

    static MessageSummary summary(JsonNode item, Mailbox mailbox) {
        String preview = MessageText.decode(Json.textOr(item, "content", ""));
        return new MessageSummary(
                Json.textOr(item, "messageId", ""),
                mailbox,
                mailbox == Mailbox.OUTBOX ? Json.textOr(item, "receiverName", "") : senderName(item),
                Json.textOr(item, "topic", ""),
                preview.length() > PREVIEW_LENGTH ? preview.substring(0, PREVIEW_LENGTH) + "…" : preview,
                Json.dateTime(item, "sendDate").orElse(null),
                Json.dateTime(item, "readDate").orElse(null),
                Json.flag(item, "isAnyFileAttached"));
    }

    static Message message(JsonNode root, Mailbox mailbox) {
        JsonNode data = root.path("data");
        return new Message(
                Json.textOr(data, "messageId", ""),
                mailbox,
                Json.textOr(data, "senderId", ""),
                senderName(data),
                Json.textOr(data, "topic", ""),
                MessageText.decode(Json.textOr(data, "Message", "")),
                Json.dateTime(data, "sendDate").orElse(null),
                Json.dateTime(data, "readDate").orElse(null),
                !Json.flag(data, "noReply"),
                Json.list(data, "attachments", a -> new MessageAttachment(Json.textOr(a, "id", ""),
                        Json.textOr(a, "filename", "plik"))),
                Json.list(data, "receivers", r -> new MessageReceiver(Json.textOr(r, "receiverId", ""),
                        receiverName(r), Json.dateTime(r, "readed").orElse(null))));
    }

    static Map<String, Integer> unreadCounts(JsonNode root) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        root.path("data").properties().forEach(e -> counts.put(e.getKey(), e.getValue().asInt(0)));
        return Map.copyOf(counts);
    }

    static List<RecipientGroup> groups(JsonNode root) {
        return Json.list(root.path("data"), "list", g -> new RecipientGroup(Json.textOr(g, "id", ""),
                Json.textOr(g, "name", Json.textOr(g, "id", ""))));
    }

    static List<Recipient> recipients(JsonNode root, RecipientGroup group) {
        return Json.list(root, "receivers", r -> new Recipient(Json.textOr(r, "accountId", ""),
                Json.textOr(r, "label", ""), group.id(), group.name()));
    }

    static SentMessage sent(JsonNode root) {
        JsonNode data = root.path("data");
        Optional<Long> id = Json.longValue(data, "messageId")
                .or(() -> Json.longValue(data, "id"))
                .or(() -> Json.longValue(root, "messageId"))
                .filter(v -> v > 0);
        return new SentMessage(id.orElse(null));
    }

    static boolean reportsFailure(JsonNode root) {
        if (root.path("errors").isArray() && !root.path("errors").isEmpty()) {
            return true;
        }
        String status = Json.text(root.path("data"), "status").or(() -> Json.text(root, "status")).orElse("");
        return List.of("error", "failed", "rejected").contains(status.toLowerCase(java.util.Locale.ROOT));
    }

    private static String senderName(JsonNode item) {
        return Json.text(item, "senderName").orElseGet(() ->
                (Json.textOr(item, "senderFirstName", "") + " " + Json.textOr(item, "senderLastName", "")).trim());
    }

    private static String receiverName(JsonNode r) {
        return Json.text(r, "name").orElseGet(() ->
                (Json.textOr(r, "firstName", "") + " " + Json.textOr(r, "lastName", "")).trim());
    }
}
