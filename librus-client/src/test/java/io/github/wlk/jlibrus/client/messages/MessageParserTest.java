package io.github.wlk.jlibrus.client.messages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Payload shapes of the messages module, including the variants seen live. */
class MessageParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void summaryFallsBackToFirstAndLastNameAndTruncatesPreview() throws Exception {
        MessageSummary summary = MessageParser.summary(json("{\"messageId\":\"5\",\"senderFirstName\":\"Anna\","
                + "\"senderLastName\":\"Nowak\",\"topic\":\"T\",\"content\":\"" + b64("x".repeat(201)) + "\","
                + "\"sendDate\":\"2026-11-20 14:05:31\",\"readDate\":\"2026-11-20T15:00:00\",\"isAnyFileAttached\":1}"),
                Mailbox.INBOX);

        assertThat(summary.counterpart()).isEqualTo("Anna Nowak");
        assertThat(summary.preview()).hasSize(201).endsWith("…");
        assertThat(summary.sentAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 14, 5, 31));
        assertThat(summary.isUnread()).isFalse();
        assertThat(summary.hasAttachments()).isTrue();
    }

    @Test
    void previewOfExactly200CharactersIsKept() throws Exception {
        MessageSummary summary = MessageParser.summary(json("{\"content\":\"" + b64("y".repeat(200)) + "\"}"), Mailbox.INBOX);

        assertThat(summary.preview()).hasSize(200).doesNotContain("…");
    }

    @Test
    void outboxUsesReceiverName() throws Exception {
        MessageSummary summary = MessageParser.summary(json("{\"receiverName\":\"Jan Kowal\",\"senderName\":\"Me\"}"),
                Mailbox.OUTBOX);

        assertThat(summary.counterpart()).isEqualTo("Jan Kowal");
    }

    @Test
    void pageAcceptsBareArrayAndCountsWhenTotalMissing() throws Exception {
        MessagePage page = MessageParser.page(json("[{\"messageId\":\"1\"},{\"messageId\":\"2\"}]"), Mailbox.ALERTS, 1, 1);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).extracting(MessageSummary::mailbox).containsOnly(Mailbox.ALERTS);
    }

    @Test
    void messageReadsReceiversAndReplyFlag() throws Exception {
        Message message = MessageParser.message(json("{\"data\":{\"messageId\":\"9\",\"senderId\":\"13\","
                + "\"senderFirstName\":\"A\",\"senderLastName\":\"B\",\"noReply\":1,\"Message\":\"" + b64("Treść") + "\","
                + "\"receivers\":[{\"receiverId\":\"1\",\"firstName\":\"Jan\",\"lastName\":\"Kowal\",\"readed\":\"\"},"
                + "{\"receiverId\":\"2\",\"name\":\"Rada\",\"readed\":\"2026-11-21 12:30:00\"}]}}"), Mailbox.OUTBOX);

        assertThat(message.replyAllowed()).isFalse();
        assertThat(message.senderName()).isEqualTo("A B");
        assertThat(message.body()).isEqualTo("Treść");
        assertThat(message.receivers()).extracting(MessageReceiver::name).containsExactly("Jan Kowal", "Rada");
        assertThat(message.receivers().getFirst().readAt()).isNull();
        assertThat(message.receivers().get(1).readAt()).isEqualTo(LocalDateTime.of(2026, 11, 21, 12, 30));
    }

    @Test
    void sentIdComesFromDataOrRoot() throws Exception {
        assertThat(MessageParser.sent(json("{\"data\":{\"messageId\":123,\"status\":\"sent\"}}")).messageId()).isEqualTo(123L);
        assertThat(MessageParser.sent(json("{\"data\":{\"id\":\"77\"}}")).messageId()).isEqualTo(77L);
        assertThat(MessageParser.sent(json("{\"messageId\":5}")).messageId()).isEqualTo(5L);
        assertThat(MessageParser.sent(json("{\"data\":{\"messageId\":0}}")).messageId()).isNull();
        assertThat(MessageParser.sent(json("{\"data\":{\"messageId\":1}}")).messageId()).isEqualTo(1L);
        assertThat(MessageParser.sent(json("{}")).messageId()).isNull();
    }

    @Test
    void failureReports() throws Exception {
        assertThat(MessageParser.reportsFailure(json("{\"errors\":[\"x\"]}"))).isTrue();
        assertThat(MessageParser.reportsFailure(json("{\"errors\":[]}"))).isFalse();
        assertThat(MessageParser.reportsFailure(json("{\"errors\":\"x\"}"))).isFalse();
        assertThat(MessageParser.reportsFailure(json("{\"data\":{\"status\":\"Rejected\"}}"))).isTrue();
        assertThat(MessageParser.reportsFailure(json("{\"status\":\"failed\"}"))).isTrue();
        assertThat(MessageParser.reportsFailure(json("{\"data\":{\"status\":\"sent\"}}"))).isFalse();
        assertThat(MessageParser.reportsFailure(json("{}"))).isFalse();
    }

    @Test
    void groupsUseIdAsNameFallbackAndUnreadCountsMap() throws Exception {
        assertThat(MessageParser.groups(json("{\"data\":{\"list\":[{\"id\":\"tutors\"}]}}")))
                .containsExactly(new RecipientGroup("tutors", "tutors"));
        assertThat(MessageParser.unreadCounts(json("{\"data\":{\"inbox\":2,\"notes\":\"x\"}}")))
                .containsEntry("inbox", 2).containsEntry("notes", 0);
    }
}
