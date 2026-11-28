package io.github.wlk.jlibrus.client.messages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.wlk.jlibrus.client.Identifiers;
import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.http.HttpResult;
import io.github.wlk.jlibrus.client.parse.Json;
import io.github.wlk.jlibrus.client.session.SynergiaSession;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The messages module ("Wiadomości"). It has its own session, opened from a Synergia session via
 * {@code /wiadomosci3}, which expires independently and much more often; reads recover automatically, a send is
 * attempted exactly once after a read has proved the session works.
 */
public final class MessagesClient {

    static final String API = "/api";

    private final SynergiaSession session;
    private final MessagesSessionOpener opener;
    private final ObjectMapper json;

    public MessagesClient(SynergiaSession session, ObjectMapper json) {
        this.session = session;
        this.opener = new MessagesSessionOpener(session);
        this.json = json;
    }

    public MessagePage list(Mailbox mailbox, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.clamp(limit, 1, 50);
        JsonNode root = read("/" + mailbox.path() + "/messages?page=" + safePage + "&limit=" + safeLimit);
        return MessageParser.page(root, mailbox, safePage, safeLimit);
    }

    /** Opens one message. <strong>Librus marks it read.</strong> */
    public Message open(Mailbox mailbox, String messageId) {
        requireNumeric(messageId);
        return MessageParser.message(read("/" + mailbox.path() + "/messages/" + messageId), mailbox);
    }

    public Map<String, Integer> unreadCounts() {
        return MessageParser.unreadCounts(read("/inbox/unreadMessagesCount"));
    }

    public List<RecipientGroup> recipientGroups() {
        return MessageParser.groups(read("/receivers/types?includeClass=true"));
    }

    public List<Recipient> recipients(RecipientGroup group) {
        return MessageParser.recipients(read(recipientsPath(group)), group);
    }

    /** Lists every recipient of every offered staff group, each person once; groups this account may not read are skipped. */
    public List<Recipient> allRecipients() {
        Set<String> seen = new HashSet<>();
        return recipientGroups().stream()
                .filter(group -> !RecipientGroups.SKIPPED.contains(group.id()))
                .flatMap(group -> MessageParser.recipients(Json.orMissing(() -> read(recipientsPath(group))), group).stream())
                .filter(recipient -> seen.add(recipient.accountId()))
                .toList();
    }

    private static String recipientsPath(RecipientGroup group) {
        if (!Identifiers.isGroupId(group.id())) {
            throw new IllegalArgumentException("Invalid recipient group id");
        }
        return "/receivers/groups/school-employees?receiverType=" + group.id();
    }

    /** Download link of an attachment (does not mark the message read). */
    public String attachmentLink(String attachmentId, String messageId) {
        requireNumeric(attachmentId);
        requireNumeric(messageId);
        JsonNode root = read("/attachments/" + attachmentId + "/messages/" + messageId);
        String link = root.path("data").path("downloadLink").asText("");
        if (!link.startsWith("https://") && !link.startsWith("http://")) {
            throw new LibrusException.ProtocolError("Librus returned no attachment link");
        }
        return link;
    }

    /**
     * Sends a message once. Never retried: on an unclear answer the message may have been sent, so the
     * caller should check the outbox before trying again.
     */
    public SentMessage send(OutgoingMessage message) {
        return session.exclusive(() -> sendOnce(message));
    }

    private SentMessage sendOnce(OutgoingMessage message) {
        read("/me");
        String path = message.replyToId() == null ? "/messages" : "/messages/reply";
        HttpResult result = session.http().sendJson("POST",
                session.endpoints().messagesPath(API + path), body(message));
        if (result.status() >= 400 && result.status() < 500) {
            throw new LibrusException.ProtocolError("Librus refused the message (HTTP " + result.status()
                    + "); it was not sent");
        }
        if (!result.isSuccess()) {
            throw new LibrusException.ServiceUnavailable("Librus answered HTTP " + result.status()
                    + "; the message may have been sent - check the outbox");
        }
        JsonNode root = parse(result.body());
        if (MessageParser.reportsFailure(root)) {
            throw new LibrusException.ProtocolError("Librus reported an error; check the outbox before retrying");
        }
        return MessageParser.sent(root);
    }

    String body(OutgoingMessage message) {
        ObjectNode root = json.createObjectNode();
        ArrayNode receivers = root.putObject("receivers").putArray("schoolReceivers");
        message.recipientIds().forEach(id -> receivers.addObject().put("accountId", id));
        root.put("topic", MessageText.encodeTopic(message.topic()));
        root.put("content", MessageText.encodeBody(message.text()));
        root.putNull("storageId");
        root.put("category", "normal");
        if (message.replyToId() != null) {
            // OutgoingMessage guarantees 1-18 digits, which always fit a long
            root.put("originalMessageId", Long.parseLong(message.replyToId()));
        }
        return root.toString();
    }

    private JsonNode read(String path) {
        return session.exclusive(() -> readWithRecovery(path));
    }

    private JsonNode readWithRecovery(String path) {
        int generation = session.ensureLoggedIn();
        opener.ensureOpen();
        HttpResult result = get(path);
        if (needsNewSession(result)) {
            result = reopenAndRetry(path);
        }
        if (result == null || needsNewSession(result)) {
            session.relogin(generation);
            opener.reopen();
            result = get(path);
        }
        return classify(path, result);
    }

    /** A "no access" page right after the messages session died usually means Synergia died too. */
    private HttpResult reopenAndRetry(String path) {
        try {
            opener.reopen();
            return get(path);
        } catch (LibrusException.ModuleUnavailable e) {
            return null;
        }
    }

    private HttpResult get(String path) {
        return session.http().get(session.endpoints().messagesPath(API + path), Map.of("Accept", "application/json"));
    }

    static boolean needsNewSession(HttpResult result) {
        int status = result.status();
        boolean clientError = status >= 400 && status < 500 && status != 404 && status != 429;
        return clientError || (result.isSuccess() && !result.body().isBlank() && !result.looksLikeJson());
    }

    private JsonNode classify(String path, HttpResult result) {
        if (result.isSuccess()) {
            return parse(result.body());
        }
        throw failure(path, result.status());
    }

    static LibrusException failure(String path, int status) {
        if (status == 404) {
            return new LibrusException.ModuleUnavailable("Messages resource not available: " + path);
        }
        if (status >= 500 || status == 429) {
            return new LibrusException.ServiceUnavailable("Messages module unavailable (HTTP " + status + ")");
        }
        return new LibrusException.SessionExpired("Messages module keeps rejecting the session");
    }

    /** A blank answer (some successful calls return no body) parses as missing, which every parser reads as empty. */
    private JsonNode parse(String body) {
        return Json.parse(json, body, e -> new LibrusException.ProtocolError("Malformed JSON from the messages module", e));
    }

    private static void requireNumeric(String id) {
        if (!Identifiers.isNumericId(id)) {
            throw new IllegalArgumentException("Message and attachment ids are numeric");
        }
    }
}
