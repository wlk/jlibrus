package io.github.wlk.jlibrus.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.wlk.jlibrus.mock.fixtures.MessageFixtures;
import io.github.wlk.jlibrus.mock.http.MockExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The messages module: its own session bootstrap plus the JSON API under {@code /api}. */
final class MessagesHandler {

    static final String SESSION_COOKIE = "WIADOMOSCI_SID";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DETAIL = Pattern.compile("/api/(inbox|outbox)/messages/(\\d+)");
    private static final Pattern ATTACHMENT = Pattern.compile("/api/attachments/(\\d+)/messages/(\\d+)");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MockState state;
    private final String base;

    MessagesHandler(MockState state, String base) {
        this.state = state;
        this.base = base;
    }

    void bootstrap(MockExchange ex) {
        Optional<MockAccount> account = state.synergiaLogin(ex.cookie("DZIENNIKSID").orElse(null)).flatMap(state::account);
        if (account.isEmpty()) {
            ex.html(200, "<html><img src=stop.png>Brak dostępu</html>");
            return;
        }
        if (!account.get().messagesEnabled()) {
            ex.html(200, "<html><h2 class=\"inside\">Brak dostępu</h2></html>");
            return;
        }
        ex.redirect(base + "/MultiDomainLogon?login=" + account.get().login());
    }

    void multiDomainLogon(MockExchange ex) {
        String login = ex.query().getOrDefault("login", "");
        if (state.account(login).isEmpty()) {
            ex.html(400, "<html>error</html>");
            return;
        }
        ex.setCookie(SESSION_COOKIE, state.newMessageSession(login), "Path=/; HttpOnly").redirect(base + "/messages-app");
    }

    void app(MockExchange ex) {
        ex.html(200, "<html><body>Wiadomości</body></html>");
    }

    void api(MockExchange ex) {
        Optional<String> login = state.messageLogin(ex.cookie(SESSION_COOKIE).orElse(null));
        if (login.isEmpty()) {
            ex.json(401, "{\"error\":\"unauthorized\"}");
            return;
        }
        LocalDate today = LocalDate.now(state.clock().withZone(MockLibrus.ZONE));
        MessageFixtures fixtures = new MessageFixtures(today);
        if ("POST".equals(ex.method())) {
            send(ex, login.get());
            return;
        }
        route(ex, login.get(), fixtures);
    }

    private void route(MockExchange ex, String login, MessageFixtures fixtures) {
        String path = ex.path();
        Matcher detail = DETAIL.matcher(path);
        Matcher attachment = ATTACHMENT.matcher(path);
        if ("/api/me".equals(path)) {
            ex.json(200, "{\"accountId\":\"" + (50_000 + state.account(login).orElseThrow().seed()) + "\"}");
        } else if ("/api/inbox/unreadMessagesCount".equals(path)) {
            long unread = fixtures.inbox().stream().filter(m -> !state.isRead(login, m.id())).count();
            ex.json(200, "{\"data\":{\"inbox\":" + unread + ",\"notes\":0,\"alerts\":0,\"substitutions\":0}}");
        } else if ("/api/inbox/messages".equals(path)) {
            ex.json(200, fixtures.inboxPage(page(ex), limit(ex), id -> state.isRead(login, id)).toString());
        } else if ("/api/outbox/messages".equals(path)) {
            ex.json(200, outbox(login).toString());
        } else if (detail.matches()) {
            detail(ex, login, fixtures, detail.group(1), detail.group(2));
        } else if ("/api/receivers/types".equals(path)) {
            ex.json(200, MessageFixtures.receiverTypes().toString());
        } else if ("/api/receivers/groups/school-employees".equals(path)) {
            ex.json(200, MessageFixtures.receivers(ex.query().getOrDefault("receiverType", "")).toString());
        } else if (attachment.matches()) {
            ex.json(200, "{\"data\":{\"status\":\"ok\",\"downloadLink\":\"" + base + "/GetFile/" + attachment.group(1) + "\"}}");
        } else {
            ex.json(404, "{\"error\":\"not found\"}");
        }
    }

    private void detail(MockExchange ex, String login, MessageFixtures fixtures, String mailbox, String id) {
        if ("inbox".equals(mailbox)) {
            Optional<MessageFixtures.Received> message = fixtures.find(id);
            if (message.isEmpty()) {
                ex.json(404, "{\"error\":\"not found\"}");
                return;
            }
            state.markRead(login, id);
            ex.json(200, fixtures.detail(message.get(), LocalDateTime.now(state.clock().withZone(MockLibrus.ZONE))).toString());
            return;
        }
        state.sentMessages().stream()
                .filter(s -> s.login().equals(login) && String.valueOf(s.id()).equals(id))
                .findFirst()
                .ifPresentOrElse(s -> ex.json(200, sentDetail(s).toString()), () -> ex.json(404, "{}"));
    }

    private void send(MockExchange ex, String login) {
        JsonNode body;
        try {
            body = MAPPER.readTree(ex.body());
        } catch (IOException e) {
            ex.json(400, "{\"errors\":[\"invalid json\"]}");
            return;
        }
        List<String> recipients = new ArrayList<>();
        body.path("receivers").path("schoolReceivers").forEach(r -> recipients.add(r.path("accountId").asText()));
        String topic = decode(body.path("topic").asText(""));
        String text = decode(body.path("content").asText(""));
        if (recipients.isEmpty() || topic.isBlank() || text.isBlank()) {
            ex.json(400, "{\"errors\":[\"missing fields\"]}");
            return;
        }
        boolean reply = ex.path().endsWith("/reply");
        String replyTo = reply ? body.path("originalMessageId").asText(null) : null;
        long id = state.recordSent(login, recipients, topic, text, replyTo);
        ex.json(200, reply ? "{\"data\":{\"status\":\"sent\"}}" : "{\"data\":{\"messageId\":" + id + ",\"status\":\"sent\"}}");
    }

    private ObjectNode outbox(String login) {
        ArrayNode data = MAPPER.createArrayNode();
        state.sentMessages().stream().filter(s -> s.login().equals(login))
                .forEach(s -> data.addObject().put("messageId", String.valueOf(s.id()))
                        .put("receiverName", s.recipients().toString())
                        .put("topic", s.topic()).put("content", encode(s.text()))
                        .put("sendDate", TS.format(LocalDateTime.ofInstant(s.at(), MockLibrus.ZONE)))
                        .putNull("readDate").put("isAnyFileAttached", false));
        ObjectNode root = MAPPER.createObjectNode();
        root.set("data", data);
        root.put("total", data.size());
        return root;
    }

    private ObjectNode sentDetail(MockState.SentRecord s) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode data = root.putObject("data").put("messageId", String.valueOf(s.id())).put("topic", s.topic())
                .put("Message", encode(s.text())).put("sendDate", TS.format(LocalDateTime.ofInstant(s.at(), MockLibrus.ZONE)))
                .put("noReply", 0);
        data.putArray("attachments");
        ArrayNode receivers = data.putArray("receivers");
        s.recipients().forEach(r -> receivers.addObject().put("receiverId", r).put("name", "Odbiorca " + r).put("readed", ""));
        return root;
    }

    private static int page(MockExchange ex) {
        return positive(ex, "page", 1);
    }

    private static int limit(MockExchange ex) {
        return positive(ex, "limit", 10);
    }

    /** A malformed paging parameter falls back to its default instead of failing the request. */
    private static int positive(MockExchange ex, String name, int fallback) {
        String raw = ex.query().get(name);
        if (raw == null) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String decode(String base64) {
        try {
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
