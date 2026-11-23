package io.github.wlk.jlibrus.server.it;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@QuarkusTestResource(MockLibrusResource.class)
class AuthIT {

    @Test
    void apiRequiresLogin() {
        RestAssured.get("/api/children").then().statusCode(401);
        RestAssured.get("/api/auth/me").then().statusCode(401);
    }

    @Test
    void wrongPasswordIsRejected() {
        RestAssured.given().contentType("application/x-www-form-urlencoded")
                .formParam("j_username", "parent").formParam("j_password", "nope")
                .post("/j_security_check").then().statusCode(401).header("Set-Cookie", not(containsString(Session.COOKIE)));
    }

    @Test
    void loginSessionAndLogout() {
        String cookie = Session.login();

        Session.as(cookie).get("/api/auth/me").then().statusCode(200).body("username", equalTo("parent"));
        Session.as(cookie).post("/api/auth/logout").then().statusCode(204)
                .header("Set-Cookie", containsString(Session.COOKIE + "=;"));
    }

    @Test
    void publicResourcesNeedNoLogin() {
        RestAssured.get("/q/health/ready").then().statusCode(200);
        RestAssured.get("/").then().statusCode(200).body(containsString("<html"));
        RestAssured.get("/manifest.webmanifest").then().statusCode(200);
        RestAssured.get("/ical/not-a-token.ics").then().statusCode(404);
        RestAssured.get("/ical/" + "a".repeat(43) + ".ics").then().statusCode(404);
    }

    @Test
    void securityHeadersAreSet() {
        RestAssured.get("/").then()
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("Content-Security-Policy", containsString("default-src 'self'"));
    }
}
