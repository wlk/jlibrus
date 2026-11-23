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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validation before requests, session recovery steps and lock release of the messages module. */
class MessagesEdgesTest {

    private final ScriptedServer server = new ScriptedServer();
    private MessagesClient messages;

    @BeforeEach
    void setUp() {
        String b = server.base().toString();
        server.happyLogin("DZIENNIKSID=d; Path=/", "oauth_token=t; Path=/")
                .on("/wiadomosci3", Reply.redirect(b + "/MultiDomainLogon"))
                .on("/MultiDomainLogon", Reply.cookies(200, "ok", "W=1; Path=/"))
                .on("/api/me", Reply.json(200, "{}"));
        messages = LibrusAccount.builder(new Credentials("1u", "p"))
                .endpoints(LibrusEndpoints.singleHost(server.base())).build().messages();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void invalidIdsFailBeforeAnyRequest() {
        assertThatThrownBy(() -> messages.attachmentLink("x", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> messages.attachmentLink("1", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> messages.open(Mailbox.INBOX, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(server.seen()).isEmpty();
    }

    @Test
    void nonHttpDownloadLinkIsRejected() {
        server.on("/api/attachments/1/messages/2", Reply.json(200, "{\"data\":{\"downloadLink\":\"ftp://x/y\"}}"));

        assertThatThrownBy(() -> messages.attachmentLink("1", "2")).isInstanceOf(LibrusException.ProtocolError.class);
    }

    @Test
    void reopeningTheModuleRecoversWithoutPasswordLogin() {
        AtomicInteger calls = new AtomicInteger();
        server.on("/api/inbox/unreadMessagesCount", s -> calls.incrementAndGet() == 1
                ? Reply.json(401, "{}") : Reply.json(200, "{\"data\":{\"inbox\":4}}"));

        assertThat(messages.unreadCounts()).containsEntry("inbox", 4);
        assertThat(server.count("/wiadomosci3")).isEqualTo(2);
        assertThat(server.seen().stream().filter(s -> "POST".equals(s.method()) && s.path().startsWith("/OAuth"))).hasSize(1);
    }

    @Test
    void htmlInsteadOfJsonMeansNewSessionButEmptySuccessIsFine() {
        AtomicInteger calls = new AtomicInteger();
        server.on("/api/inbox/unreadMessagesCount", s -> calls.incrementAndGet() == 1
                ? Reply.html(200, "<html>login</html>") : Reply.html(200, ""));

        assertThat(messages.unreadCounts()).isEmpty();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void serverErrorsAreNotTreatedAsExpiredSessions() {
        server.on("/api/inbox/unreadMessagesCount", Reply.json(503, "{}"));

        assertThatThrownBy(messages::unreadCounts).isInstanceOf(LibrusException.ServiceUnavailable.class);
        assertThat(server.count("/wiadomosci3")).isEqualTo(1);
    }

    @Test
    void sendWithEmptyJsonObjectIsAcceptedWithoutId() {
        server.on("/api/messages", Reply.json(200, "{}"));

        assertThat(messages.send(OutgoingMessage.newThread(List.of("1"), "t", "x")).messageId()).isNull();
        assertThat(server.seen().getLast().header("Content-Type")).startsWith("application/json");
    }

    @Test
    void locksAreReleasedForOtherThreads() throws Exception {
        server.on("/api/inbox/unreadMessagesCount", Reply.json(200, "{\"data\":{}}"));
        messages.unreadCounts();

        CompletableFuture<Integer> other = CompletableFuture.supplyAsync(() -> messages.unreadCounts().size());

        assertThat(other.get(5, TimeUnit.SECONDS)).isZero();
    }
}
