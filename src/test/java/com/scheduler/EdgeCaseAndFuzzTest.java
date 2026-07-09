package com.scheduler;

import com.scheduler.service.SolverService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag("extreme")
public class EdgeCaseAndFuzzTest {

    @Inject
    SolverService solverService;

    @Test
    public void testInsufficientEmployees() {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Edge Case Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "both");
        req.put("overrideExisting", true);
        
        req.put("roles", List.of(
            Map.of("role_name", "Developer", "max_workers", 5, "rating", 3) // Need 5
        ));

        // Only 1 available!
        req.put("existing_users", List.of(
            Map.of("employee_id", "E1", "name", "E1", "role", "Developer", "rate", 20, "unit", "hour", "rating", 5, "employeeType", "Perm", "gender", "M")
        ));

        Map<String, Object> result = solverService.solveShift(req);
        assertEquals("success", result.get("status"));
        
        // Should only assign 1 and soft-penalize (which is a valid solver solution for impossible situations)
        int assigned = (int) result.getOrDefault("new_assignments_made", 0);
        assertEquals(1, assigned);
    }

    @Test
    public void testFuzzRandomizedData() {
        Random rand = new Random(42); // deterministic seed for reproducibility
        for(int iteration = 0; iteration < 10; iteration++) { // run 10 random datasets
            Map<String, Object> req = new HashMap<>();
            req.put("shift_name", "Fuzz Shift " + iteration);
            req.put("start_date", "2026-10-01");
            req.put("end_date", "2026-10-02");
            req.put("start_time", "09:00");
            req.put("end_time", "17:00");
            req.put("optimization", rand.nextBoolean() ? "cost" : "quality");
            req.put("overrideExisting", true);
            
            int maxWorkers = rand.nextInt(10) + 1;
            req.put("roles", List.of(
                Map.of("role_name", "Developer", "max_workers", maxWorkers, "rating", 3)
            ));

            int userCount = rand.nextInt(50) + 5;
            List<Map<String, Object>> employees = new ArrayList<>();
            for (int i = 0; i < userCount; i++) {
                employees.add(Map.of(
                    "employee_id", "F_EMP_" + i,
                    "name", "Fuzz User " + i,
                    "role", "Developer",
                    "rate", rand.nextDouble() * 100, // random float wage
                    "unit", "hour",
                    "rating", rand.nextInt(10), // ratings up to 10
                    "employeeType", "Permanent",
                    "gender", "Male"
                ));
            }
            req.put("existing_users", employees);

            // Execute fuzzed dataset
            Map<String, Object> result = solverService.solveShift(req);
            
            // Assert no exceptions or internal solver crashes
            assertEquals("success", result.get("status"));
        }
    }
}
