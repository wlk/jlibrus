package io.github.wlk.jlibrus.server.api;

import io.github.wlk.jlibrus.client.LibrusException;
import io.github.wlk.jlibrus.server.persistence.PersistenceException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMapperTest {

    private final ErrorMapper mapper = new ErrorMapper();

    private static void assertError(Response response, int status, String code) {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(((ErrorMapper.ApiError) response.getEntity()).error()).isEqualTo(code);
    }

    @Test
    void librusFailuresMapToStatusesCallersCanActOn() {
        assertError(mapper.librus(new LibrusException.InvalidCredentials("x")), 502, "librus_credentials");
        assertError(mapper.librus(new LibrusException.CaptchaRequired("x")), 502, "librus_captcha");
        assertError(mapper.librus(new LibrusException.ModuleUnavailable("x")), 404, "librus_module_unavailable");
        Response unavailable = mapper.librus(new LibrusException.ServiceUnavailable("x"));
        assertError(unavailable, 503, "librus_unavailable");
        assertThat(unavailable.getHeaderString("Retry-After")).isEqualTo("300");
        assertError(mapper.librus(new LibrusException.SessionExpired("x")), 502, "librus_error");
        assertError(mapper.librus(new LibrusException.ProtocolError("x")), 502, "librus_error");
    }

    @Test
    void invalidInputAndDatabaseFailures() {
        Response invalid = mapper.invalid(new IllegalArgumentException("bad email"));
        assertError(invalid, 400, "invalid_request");
        assertThat(((ErrorMapper.ApiError) invalid.getEntity()).message()).isEqualTo("bad email");

        Response db = mapper.database(new PersistenceException("Query failed: SELECT secret", new SQLException("x")));
        assertError(db, 500, "database_error");
        assertThat(((ErrorMapper.ApiError) db.getEntity()).message()).doesNotContain("SELECT");
    }
}
