package io.github.wlk.jlibrus.client.messages;

import io.github.wlk.jlibrus.client.Credentials;
import io.github.wlk.jlibrus.client.LibrusAccount;
import io.github.wlk.jlibrus.client.LibrusEndpoints;
import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.client.ScriptedServer;
import io.github.wlk.jlibrus.client.ScriptedServer.Reply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Send outcomes, attachment links and bootstrap failures, scripted. */
class MessagesSendEdgeCasesTest {

    private final ScriptedServer server = new ScriptedServer();
    private MessagesClient messages;

    @BeforeEach
    void setUp() {
        String b = server.base().toString();
        server.happyLogin("DZIENNIKSID=d; Path=/", "oauth_token=t; Path=/")
                .on("/wiadomosci3", Reply.redirect(b + "/MultiDomainLogon"))
                .on("/MultiDomainLogon", Reply.cookies(200, "ok", "W=1; Path=/"))
                .on("/api/me", Reply.json(200, "{\"accountId\":\"1\"}"));
        messages = LibrusAccount.builder(new Credentials("1u", "p"))
                .endpoints(LibrusEndpoints.singleHost(server.base())).build().messages();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static OutgoingMessage message() {
        return OutgoingMessage.newThread(List.of("11"), "Temat", "Treść");
    }

    @Test
    void clientErrorMeansNotSent() {
        server.on("/api/messages", Reply.json(400, "{}"));

        assertThatThrownBy(() -> messages.send(message())).isInstanceOf(LibrusException.ProtocolError.class)
                .hasMessageContaining("not sent");
        assertThat(server.count("/api/messages")).isEqualTo(1);
    }

    @Test
    void serverErrorMayHaveBeenSentAndIsNotRetried() {
        server.on("/api/messages", Reply.json(500, "{}"));

        assertThatThrownBy(() -> messages.send(message())).isInstanceOf(LibrusException.ServiceUnavailable.class)
                .hasMessageContaining("outbox");
        assertThat(server.count("/api/messages")).isEqualTo(1);
    }

    @Test
    void successWithErrorsIsReported() {
        server.on("/api/messages", Reply.json(200, "{\"errors\":[\"bad\"]}"));

        assertThatThrownBy(() -> messages.send(message())).isInstanceOf(LibrusException.ProtocolError.class);
    }

    @Test
    void emptySuccessBodyIsSentWithoutId() {
        server.on("/api/messages", Reply.html(200, ""));

        assertThat(messages.send(message()).messageId()).isNull();
    }

    @Test
    void bodyCarriesEncodedFieldsAndReplyId() throws Exception {
        String body = messages.body(new OutgoingMessage(List.of("11", "12"), "Re: x", "a<b", "42"));
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);

        assertThat(json.path("receivers").path("schoolReceivers")).hasSize(2);
        assertThat(json.path("originalMessageId").asLong()).isEqualTo(42L);
        assertThat(json.path("category").asText()).isEqualTo("normal");
        assertThat(json.path("storageId").isNull()).isTrue();
        assertThat(json.has("originalMessageId")).isTrue();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(messages.body(message())).has("originalMessageId")).isFalse();
    }

    @Test
    void attachmentLinkMustBeAUrl() {
        server.on("/api/attachments/1/messages/2", Reply.json(200, "{\"data\":{\"downloadLink\":\"javascript:x\"}}"));

        assertThatThrownBy(() -> messages.attachmentLink("1", "2")).isInstanceOf(LibrusException.ProtocolError.class);
    }

    @Test
    void httpAttachmentLinkIsAccepted() {
        server.on("/api/attachments/1/messages/2", Reply.json(200, "{\"data\":{\"downloadLink\":\"http://sandbox/x\"}}"));

        assertThat(messages.attachmentLink("1", "2")).isEqualTo("http://sandbox/x");
    }

    @Test
    void moduleWithoutAccessIsUnavailable() {
        server.on("/wiadomosci3", Reply.html(200, "<h2 class=inside>Brak dostępu</h2>"));

        assertThatThrownBy(() -> messages.unreadCounts()).isInstanceOf(LibrusException.ModuleUnavailable.class);
        assertThat(server.seen()).noneMatch(s -> s.path().startsWith("/api"));
    }

    @Test
    void bootstrapRedirectOutsideLibrusIsRefused() {
        server.on("/wiadomosci3", Reply.redirect("https://evil.example/x"));

        assertThatThrownBy(() -> messages.unreadCounts()).isInstanceOf(LibrusException.ProtocolError.class);
    }

    @Test
    void endlessBootstrapLoopIsCut() {
        server.on("/wiadomosci3", Reply.redirect(server.base() + "/wiadomosci3"));

        assertThatThrownBy(() -> messages.unreadCounts()).isInstanceOf(LibrusException.ProtocolError.class);
        assertThat(server.count("/wiadomosci3")).isEqualTo(10);
    }

    @Test
    void persistentRejectionEndsAsSessionExpired() {
        server.on("/api/inbox/unreadMessagesCount", Reply.json(401, "{}"));

        assertThatThrownBy(() -> messages.unreadCounts()).isInstanceOf(LibrusException.SessionExpired.class);
        assertThat(server.count("/api/inbox/unreadMessagesCount")).isEqualTo(3);
    }

    @Test
    void recipientGroupsThatFailAreSkipped() {
        server.on("/api/receivers/types", Reply.json(200, "{\"data\":{\"list\":[{\"id\":\"teachers\"},{\"id\":\"admin\"},"
                        + "{\"id\":\"sadmin\"}]}}"))
                .on("/api/receivers/groups/school-employees", s -> s.query().contains("teachers")
                        ? Reply.json(200, "{\"receivers\":[{\"accountId\":\"5\",\"label\":\"Nowak Anna\"}]}")
                        : Reply.json(404, "{}"));

        assertThat(messages.allRecipients()).extracting(Recipient::accountId).containsExactly("5");
        assertThat(server.seen()).noneMatch(s -> s.query() != null && s.query().contains("sadmin"));
    }

    @Test
    void listClampsPagingArguments() {
        server.on("/api/inbox/messages", Reply.json(200, "{\"data\":[],\"total\":0}"));

        MessagePage page = messages.list(Mailbox.INBOX, 0, 500);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.limit()).isEqualTo(50);
        assertThat(server.seen().getLast().query()).isEqualTo("page=1&limit=50");
    }
}
