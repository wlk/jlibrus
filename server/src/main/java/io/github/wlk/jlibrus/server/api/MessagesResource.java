package io.github.wlk.jlibrus.server.api;

import io.github.wlk.jlibrus.client.messages.Mailbox;
import io.github.wlk.jlibrus.client.messages.Message;
import io.github.wlk.jlibrus.client.messages.MessagePage;
import io.github.wlk.jlibrus.client.messages.OutgoingMessage;
import io.github.wlk.jlibrus.client.messages.Recipient;
import io.github.wlk.jlibrus.client.messages.SentMessage;
import io.github.wlk.jlibrus.server.librus.Children;
import io.github.wlk.jlibrus.server.librus.LibrusData;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Messages of one child: mailboxes, reading, recipients and sending. */
@Path("/api/children/{id}/messages")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class MessagesResource {

    /** Body of a new message or reply. */
    public record SendRequest(List<String> recipientIds, String topic, String text, String replyToId) {
    }

    /** Unread counts per mailbox. */
    public record Unread(Map<String, Integer> counts) {
    }

    /** Download link of an attachment. */
    public record Link(String url) {
    }

    @Inject
    Children children;

    @Inject
    LibrusData data;

    @GET
    public MessagePage list(@PathParam("id") String id, @QueryParam("mailbox") @DefaultValue("inbox") String mailbox,
                            @QueryParam("page") @DefaultValue("1") int page) {
        return data.messages(id, mailbox(mailbox), Math.clamp(page, 1, 1000));
    }

    @GET
    @Path("/unread")
    public Unread unread(@PathParam("id") String id) {
        return new Unread(data.unreadCounts(id));
    }

    /** Opens a message; Librus marks it read, so cached lists are refreshed. */
    @GET
    @Path("/{mailbox}/{messageId}")
    public Message open(@PathParam("id") String id, @PathParam("mailbox") String mailbox,
                        @PathParam("messageId") String messageId) {
        Message message = children.account(id).messages().open(mailbox(mailbox), messageId);
        data.forgetMessages();
        return message;
    }

    /** Librus links attachments by message alone; the mailbox is only checked, like the sibling endpoints do. */
    @GET
    @Path("/{mailbox}/{messageId}/attachments/{attachmentId}")
    public Link attachment(@PathParam("id") String id, @PathParam("mailbox") String mailbox,
                           @PathParam("messageId") String messageId, @PathParam("attachmentId") String attachmentId) {
        mailbox(mailbox);
        return new Link(children.account(id).messages().attachmentLink(attachmentId, messageId));
    }

    @GET
    @Path("/recipients")
    public List<Recipient> recipients(@PathParam("id") String id) {
        return data.recipients(id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public SentMessage send(@PathParam("id") String id, SendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Message body is required");
        }
        OutgoingMessage message = new OutgoingMessage(request.recipientIds() == null ? List.of() : request.recipientIds(),
                request.topic(), request.text(), blankToNull(request.replyToId()));
        SentMessage sent = children.account(id).messages().send(message);
        data.forgetMessages();
        return sent;
    }

    static Mailbox mailbox(String raw) {
        String normalised = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace('/', '_');
        return Mailbox.valueOf(normalised);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
