package io.github.wlk.jlibrus.server.api;

import io.github.wlk.jlibrus.server.domain.SessionPrincipal;
import io.github.wlk.jlibrus.server.security.SessionEpoch;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

/** Session info and logout. Login itself is {@code POST /j_security_check} (form auth). */
@Path("/api/auth")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    static final String SESSION_COOKIE = "quarkus-credential";

    /** Who is logged in. */
    public record Me(String username) {
    }

    @Inject
    SecurityIdentity identity;

    @Inject
    SessionEpoch epoch;

    @GET
    @Path("/me")
    public Me me() {
        String name = identity.getPrincipal().getName();
        return new Me(SessionPrincipal.parse(name).map(SessionPrincipal::username).orElse(name));
    }

    /** Ends this session and revokes every other session cookie issued so far. */
    @POST
    @Path("/logout")
    public Response logout() {
        epoch.bump();
        NewCookie expired = new NewCookie.Builder(SESSION_COOKIE).value("").path("/").maxAge(0)
                .httpOnly(true).sameSite(NewCookie.SameSite.STRICT).build();
        return Response.noContent().cookie(expired).build();
    }
}
