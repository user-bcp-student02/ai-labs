package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {
    @Test
    void logsInWithWorkshopUser() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Workshop123!\",\"client\":\"angular-workshop\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("token", not(is("")))
                .body("expiresIn", is(3600))
                .body("user.username", is("student"))
                .body("user.displayName", is("Workshop Student"))
                .body("user.role", is("STUDENT"))
                .body("debug", is("authenticated-with-legacy-flow"));
    }

    @Test
    void rejectsRequestWithoutBody() {
        given()
                .contentType("application/json")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Request is null"));
    }

    @Test
    void rejectsEmptyUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsWhitespaceOnlyUsername() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"   \",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Username is required"));
    }

    @Test
    void rejectsEmptyPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsWhitespaceOnlyPassword() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"   \"}")
                .when().post("/auth/login")
                .then()
                .statusCode(400)
                .body("message", is("Password is required"));
    }

    @Test
    void rejectsIncorrectPasswordWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"Incorrect123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }

    @Test
    void rejectsUnknownUserWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"unknown-user\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }

    @Test
    void rejectsSqlInjectionInPasswordWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"student\",\"password\":\"' OR '1'='1\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }

    @Test
    void rejectsSqlInjectionInUsernameWithCommentWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"' OR 1=1 --\",\"password\":\"cualquiera\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }

    @Test
    void rejectsSqlInjectionInBothCredentialsWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"' OR '1'='1\",\"password\":\"' OR '1'='1\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }

    @Test
    void rejectsNonMatchingSqlLikeUsernameWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"' OR '1'='1\",\"password\":\"cualquiera\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }

    @Test
    void rejectsUsernameContainingApostropheWithoutToken() {
        given()
                .contentType("application/json")
                .body("{\"username\":\"stu'dent\",\"password\":\"Workshop123!\"}")
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("$", not(hasKey("token")));
    }
}
