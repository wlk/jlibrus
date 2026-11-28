package io.github.wlk.jlibrus.client.messages;

import io.github.wlk.jlibrus.client.Credentials;
import io.github.wlk.jlibrus.client.LibrusAccount;
import io.github.wlk.jlibrus.client.LibrusEndpoints;
import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.mock.MockAccount;
import io.github.wlk.jlibrus.mock.MockLibrus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagesClientTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T09:00:00Z"), ZoneOffset.UTC);
    private MockLibrus mock;
    private MessagesClient messages;

    @BeforeEach
    void start() {
        mock = MockLibrus.startLocal(CLOCK);
        messages = client(MockAccount.ola());
    }

    private MessagesClient client(MockAccount who) {
        return LibrusAccount.builder(new Credentials(who.login(), who.password()))
                .endpoints(LibrusEndpoints.singleHost(mock.baseUri())).clock(CLOCK).build().messages();
    }

    @AfterEach
    void stop() {
        mock.close();
    }

    @Test
    void listsInboxWithDecodedPreviewsWithoutMarkingRead() {
        MessagePage page = messages.list(Mailbox.INBOX, 1, 10);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.items().getFirst().preview()).startsWith("Szanowni Państwo,");
        assertThat(page.items()).allSatisfy(m -> assertThat(m.isUnread()).isTrue());
        assertThat(messages.unreadCounts()).containsEntry("inbox", 3);
    }

    @Test
    void openingMarksReadAndUnwrapsBody() {
        Message message = messages.open(Mailbox.INBOX, "7003");

        assertThat(message.body()).isEqualTo("Szanowni Państwo,\nzapraszam na zebranie w przyszłym tygodniu o 17:00.\n"
                + "Pozdrawiam\nAnna Nowak");
        assertThat(message.senderAccountId()).isEqualTo("1001");
        assertThat(message.replyAllowed()).isTrue();
        assertThat(message.attachments()).extracting(MessageAttachment::filename).containsExactly("plan-zebrania.pdf");
        assertThat(messages.unreadCounts()).containsEntry("inbox", 2);
        assertThat(messages.attachmentLink("55", "7003")).endsWith("/GetFile/55");
    }

    @Test
    void recipientsSkipParentCouncilsAndDeduplicate() {
        assertThat(messages.recipientGroups()).extracting(RecipientGroup::id).contains("parentsCouncil");

        List<Recipient> all = messages.allRecipients();

        assertThat(all).extracting(Recipient::accountId).doesNotHaveDuplicates().contains("1001", "1011");
        assertThat(all).filteredOn(r -> r.accountId().equals("1001")).singleElement()
                .satisfies(r -> assertThat(r.groupId()).isEqualTo("tutors"));
        assertThat(messages.recipients(new RecipientGroup("tutors", "Wychowawcy")))
                .extracting(Recipient::accountId).contains("1001");
    }

    @Test
    void sendsNewMessageAndReply() {
        SentMessage sent = messages.send(OutgoingMessage.newThread(List.of("1001"), "Pytanie", "Czy <jutro> jest sprawdzian?"));
        SentMessage reply = messages.send(new OutgoingMessage(List.of("1001"), "Re: Zebranie", "Dziękuję", "7003"));

        assertThat(sent.messageId()).isPositive();
        assertThat(reply.messageId()).isNull();
        assertThat(mock.state().sentMessages()).hasSize(2).first().satisfies(s -> {
            assertThat(s.text()).isEqualTo("Czy &lt;jutro&gt; jest sprawdzian?");
            assertThat(s.topic()).isEqualTo("Pytanie");
        });
        assertThat(mock.state().sentMessages().get(1).replyTo()).isEqualTo("7003");
        assertThat(messages.list(Mailbox.OUTBOX, 1, 10).items()).hasSize(2);
    }

    @Test
    void recoversWhenTheMessagesSessionExpires() {
        messages.list(Mailbox.INBOX, 1, 10);
        mock.state().expireMessageSessions();

        assertThat(messages.list(Mailbox.INBOX, 1, 10).items()).hasSize(3);
        assertThat(mock.state().passwordLogins()).isEqualTo(1);
    }

    @Test
    void recoversWhenEverythingExpires() {
        messages.list(Mailbox.INBOX, 1, 10);
        mock.state().expireMessageSessions();
        mock.state().expireSynergiaSessions();

        assertThat(messages.unreadCounts()).containsKey("inbox");
        assertThat(mock.state().passwordLogins()).isEqualTo(2);
    }

    @Test
    void rejectsNonNumericIds() {
        assertThatThrownBy(() -> messages.open(Mailbox.INBOX, "../x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> messages.recipients(new RecipientGroup("a/b", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingMailboxIsModuleUnavailable() {
        assertThatThrownBy(() -> messages.list(Mailbox.ALERTS, 1, 10))
                .isInstanceOf(LibrusException.ModuleUnavailable.class);
    }
}
