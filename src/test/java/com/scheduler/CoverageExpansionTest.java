package com.scheduler;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
public class CoverageExpansionTest {

    @Test
    public void testShiftResourceMissingRoles() {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Coverage Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        // No roles
        req.put("existing_users", List.of());

        given()
            .contentType(ContentType.JSON)
            .body(req)
            .when().post("/shifts/assign")
            .then()
            .statusCode(400)
            .body("message", containsString("roles"));
    }

    @Test
    public void testShiftResourceMissingEmployees() {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Coverage Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("roles", List.of(Map.of("role_name", "Developer", "max_workers", 1, "rating", 3)));
        // No existing_users
        
        given()
            .contentType(ContentType.JSON)
            .body(req)
            .when().post("/shifts/assign")
            .then()
            .statusCode(400)
            .body("message", containsString("existing_users"));
    }
    
    @Test
    public void testShiftResourceMissingDates() {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Coverage Shift");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("roles", List.of(Map.of("role_name", "Developer", "max_workers", 1, "rating", 3)));
        req.put("existing_users", List.of(Map.of("employee_id", "123", "name", "John")));
        // No start_date / end_date

        given()
            .contentType(ContentType.JSON)
            .body(req)
            .when().post("/shifts/assign")
            .then()
            .statusCode(400)
            .body("message", containsString("start_date"));
    }
}
