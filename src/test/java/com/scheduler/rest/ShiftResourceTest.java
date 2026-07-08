package com.scheduler.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class ShiftResourceTest {

    @Test
    public void testMalformedJsonBatchAssign() {
        given()
          .contentType(ContentType.JSON)
          .body("{ \"shifts\": [ { \"invalid\": json ] }") // Malformed
        .when()
          .post("/shifts/batch-assign")
        .then()
          .statusCode(400); // Quarkus Jackson handles this automatically
    }

    @Test
    public void testEmptyShiftsArrayBatchAssign() {
        given()
          .contentType(ContentType.JSON)
          .body(Map.of("shifts", Collections.emptyList()))
        .when()
          .post("/shifts/batch-assign")
        .then()
          .statusCode(400)
          .body("error", equalTo("Missing required field"));
    }

    @Test
    public void testMissingShiftsKeyBatchAssign() {
        given()
          .contentType(ContentType.JSON)
          .body(Map.of("some_other_key", List.of(Map.of("shift_name", "test"))))
        .when()
          .post("/shifts/batch-assign")
        .then()
          .statusCode(400)
          .body("error", equalTo("Invalid format"));
    }

    @Test
    public void testClearAllAssignments() {
        given()
        .when()
          .delete("/shifts/clear-all")
        .then()
          .statusCode(200)
          .body("status", equalTo("success"));
    }

    @Test
    public void testGetConstraints() {
        given()
        .when()
          .get("/constraints")
        .then()
          .statusCode(200)
          .body("$", not(empty()));
    }

    @Test
    public void testSingleAssignMissingFields() {
        Map<String, Object> payload = new HashMap<>();
        given()
          .contentType(ContentType.JSON)
          .body(payload)
        .when()
          .post("/shifts/assign")
        .then()
          .statusCode(400)
          .body("status", equalTo("error"));
    }
}
