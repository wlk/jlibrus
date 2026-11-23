package io.github.wlk.jlibrus.mock.fixtures;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Messages-module data: a few received messages relative to today, recipient groups and staff. */
public final class MessageFixtures {

    /** A received message of the fictional inbox. */
    public record Received(String id, long senderId, String topic, String text, LocalDateTime sentAt,
                           boolean attachment) {
    }

    private final LocalDate today;

    public MessageFixtures(LocalDate today) {
        this.today = today;
    }

    public List<Received> inbox() {
        return List.of(
                new Received("7003", 1001, "Zebranie z rodzicami",
                        "Szanowni Państwo,\nzapraszam na zebranie w przyszłym tygodniu o 17:00.\nPozdrawiam\nAnna Nowak",
                        today.atTime(7, 45), true),
                new Received("7002", 1005, "Wycieczka do muzeum",
                        "Proszę o zgody na wycieczkę do piątku.", today.minusDays(2).atTime(16, 20), false),
                new Received("7001", 1011, "Ubezpieczenie uczniów",
                        "Przypominamy o opłacie ubezpieczenia.", today.minusDays(9).atTime(9, 0), false));
    }

    public Optional<Received> find(String id) {
        return inbox().stream().filter(m -> m.id().equals(id)).findFirst();
    }

    public ObjectNode inboxPage(int page, int limit, Predicate<String> isRead) {
        List<Received> all = inbox();
        ArrayNode data = Nodes.array();
        all.stream().skip((long) (page - 1) * limit).limit(limit).forEach(m -> {
            SchoolFixtures.Staff sender = SchoolFixtures.staff(m.senderId());
            ObjectNode item = data.addObject().put("messageId", m.id())
                    .put("senderFirstName", sender.first()).put("senderLastName", sender.last())
                    .put("senderName", sender.first() + " " + sender.last())
                    .put("topic", m.topic()).put("content", base64(m.text()))
                    .put("sendDate", Nodes.timestamp(m.sentAt())).put("isAnyFileAttached", m.attachment());
            if (isRead.test(m.id())) {
                item.put("readDate", Nodes.timestamp(m.sentAt().plusHours(1)));
            } else {
                item.putNull("readDate");
            }
            item.putArray("tags");
        });
        ObjectNode root = Nodes.object();
        root.set("data", data);
        root.put("total", all.size());
        return root;
    }

    public ObjectNode detail(Received m, LocalDateTime readAt) {
        SchoolFixtures.Staff sender = SchoolFixtures.staff(m.senderId());
        String wrapped = "<Message><Content><![CDATA[" + m.text().replace("\n", "<br>") + "]]></Content></Message>";
        ObjectNode data = Nodes.object().put("messageId", m.id()).put("senderId", String.valueOf(m.senderId()))
                .put("senderFirstName", sender.first()).put("senderLastName", sender.last())
                .put("senderName", sender.first() + " " + sender.last()).put("topic", m.topic())
                .put("Message", base64(wrapped)).put("sendDate", Nodes.timestamp(m.sentAt()))
                .put("readDate", Nodes.timestamp(readAt)).put("noReply", m.senderId() == 1011 ? 1 : 0);
        ArrayNode attachments = data.putArray("attachments");
        if (m.attachment()) {
            attachments.addObject().put("id", "55").put("filename", "plan-zebrania.pdf");
        }
        data.putArray("receivers");
        ObjectNode root = Nodes.object();
        root.set("data", data);
        return root;
    }

    public static ObjectNode receiverTypes() {
        ArrayNode list = Nodes.array();
        list.addObject().put("id", "tutors").put("name", "Wychowawcy");
        list.addObject().put("id", "teachers").put("name", "Nauczyciele");
        list.addObject().put("id", "secretary").put("name", "Sekretariat");
        list.addObject().put("id", "parentsCouncil").put("name", "Rada rodziców");
        ObjectNode root = Nodes.object();
        root.putObject("data").set("list", list);
        return root;
    }

    public static ObjectNode receivers(String group) {
        ArrayNode receivers = Nodes.array();
        List<SchoolFixtures.Staff> staff = switch (group) {
            case "tutors" -> List.of(SchoolFixtures.staff(1001));
            case "secretary" -> List.of(SchoolFixtures.staff(1011));
            case "teachers" -> SchoolFixtures.STAFF.stream().filter(s -> s.id() != 1011).toList();
            default -> List.of();
        };
        staff.forEach(s -> receivers.addObject().put("accountId", String.valueOf(s.id()))
                .put("label", s.last() + " " + s.first()));
        ObjectNode root = Nodes.object();
        root.set("receivers", receivers);
        return root;
    }

    static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
