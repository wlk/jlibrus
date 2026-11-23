package io.github.wlk.jlibrus.server.api;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.server.persistence.PersistenceException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Maps failures to JSON {@code {"error": code, "message": text}} with a fitting status; never leaks internals. */
public class ErrorMapper {

    private static final Logger LOG = Logger.getLogger(ErrorMapper.class);
    static final String RETRY_AFTER_SECONDS = "300";

    /** Error body. */
    public record ApiError(String error, String message) {
    }

    @ServerExceptionMapper
    public Response librus(LibrusException e) {
        LOG.warnf("Librus call failed: %s", e.getMessage());
        return switch (e) {
            case LibrusException.InvalidCredentials invalid -> error(502, "librus_credentials", invalid.getMessage());
            case LibrusException.CaptchaRequired captcha -> error(502, "librus_captcha", captcha.getMessage());
            case LibrusException.ModuleUnavailable module -> error(404, "librus_module_unavailable", module.getMessage());
            case LibrusException.ServiceUnavailable down -> Response.fromResponse(
                    error(503, "librus_unavailable", down.getMessage())).header("Retry-After", RETRY_AFTER_SECONDS).build();
            default -> error(502, "librus_error", e.getMessage());
        };
    }

    @ServerExceptionMapper
    public Response invalid(IllegalArgumentException e) {
        return error(400, "invalid_request", e.getMessage());
    }

    @ServerExceptionMapper
    public Response database(PersistenceException e) {
        LOG.error("Database failure", e);
        return error(500, "database_error", "Database operation failed");
    }

    static Response error(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).entity(new ApiError(code, message)).build();
    }
}
