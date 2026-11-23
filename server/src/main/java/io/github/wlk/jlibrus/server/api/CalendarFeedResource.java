package io.github.wlk.jlibrus.server.api;

import io.github.wlk.jlibrus.server.service.FeedService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * Public, read-only iCalendar export. The unguessable token in the URL is the only credential, so calendar apps
 * can subscribe without logging in; resetting the token in settings revokes the old URL.
 */
@Path("/ical")
public class CalendarFeedResource {

    static final String TEXT_CALENDAR = "text/calendar; charset=utf-8";

    @Inject
    FeedService feeds;

    @GET
    @Path("/{token}.ics")
    @Produces(TEXT_CALENDAR)
    public Response feed(@PathParam("token") String token) {
        return feeds.render(token)
                .map(ics -> Response.ok(ics, TEXT_CALENDAR)
                        .header("Cache-Control", "private, max-age=300")
                        .header("Content-Disposition", "inline; filename=\"librus.ics\"")
                        .build())
                .orElseGet(() -> Response.status(404).type("text/plain").entity("Not found").build());
    }
}
