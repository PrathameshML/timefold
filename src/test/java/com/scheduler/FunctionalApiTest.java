package com.scheduler;

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
public class FunctionalApiTest {

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
    public void testMalformedJsonReturns400() {
        given()
          .contentType(ContentType.JSON)
          .body("{ \"shifts\": [ { \"invalid\": json ] }") 
        .when()
          .post("/shifts/batch-assign")
        .then()
          .statusCode(400);
    }

    @Test
    public void testEmptyShiftsArrayReturns400() {
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
    public void testSingleAssignMissingFieldsReturnsError() {
        given()
          .contentType(ContentType.JSON)
          .body(new HashMap<>()) 
        .when()
          .post("/shifts/assign")
        .then()
          .statusCode(400)
          .body("status", equalTo("error"));
    }

    @Test
    public void testBatchAssignWithEmptyExistingUsersReturnsError() {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Test");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("existing_users", Collections.emptyList());
        req.put("roles", List.of(Map.of("role_name", "Dev", "max_workers", 1)));

        given()
          .contentType(ContentType.JSON)
          .body(Map.of("shifts", List.of(req)))
        .when()
          .post("/shifts/batch-assign")
        .then()
          .statusCode(200)
          .body("shift_results[0].status", equalTo("error"));
    }
}
