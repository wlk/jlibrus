package io.github.wlk.jlibrus.server.it;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

/** Logs in through the form endpoint and hands out requests carrying the session cookie. */
final class Session {

    static final String COOKIE = "quarkus-credential";

    private Session() {
    }

    static String login() {
        return RestAssured.given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("j_username", "parent")
                .formParam("j_password", "test-password")
                .post("/j_security_check")
                .then().statusCode(200)
                .extract().cookie(COOKIE);
    }

    static RequestSpecification as(String cookie) {
        return RestAssured.given().cookie(COOKIE, cookie);
    }
}
