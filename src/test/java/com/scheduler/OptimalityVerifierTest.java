package com.scheduler;

import com.scheduler.service.SolverService;
import com.scheduler.service.DatabaseService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class OptimalityVerifierTest {

    @Inject
    SolverService solverService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    public void cleanDatabase() {
        databaseService.clearAllAssignments();
    }

    @Test
    public void testBruteForceOptimalitySmallDataset() {
        // Brute force verification for 4 choose 2 in "both" optimization mode
        // Permutations of 4 choose 2 is 6.
        // E1: Rate 20, Rating 5 (Best value)
        // E2: Rate 20, Rating 4
        // E3: Rate 30, Rating 5
        // E4: Rate 15, Rating 3
        // Optimal pick in "both" mode (which balances cost and quality equally)
        // Since quality has multiplier 100 and wage has multiplier 1000:
        // E1 Score: Rating (5*100 = 500) - Wage (20*10 = 200) = 300
        // E2 Score: Rating (4*100 = 400) - Wage (20*10 = 200) = 200
        // E3 Score: Rating (5*100 = 500) - Wage (30*10 = 300) = 200
        // E4 Score: Rating (3*100 = 300) - Wage (15*10 = 150) = 150
        // The two highest scored individuals are E1 (300) and E2 (200) or E3 (200).
        
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Optimum Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "both");
        
        req.put("roles", List.of(
            Map.of("role_name", "Developer", "max_workers", 2, "rating", 3)
        ));

        req.put("existing_users", List.of(
            Map.of("employee_id", "E1", "name", "E1", "role", "Developer", "rate", 20, "unit", "hour", "rating", 5, "employeeType", "Perm", "gender", "M"),
            Map.of("employee_id", "E2", "name", "E2", "role", "Developer", "rate", 20, "unit", "hour", "rating", 4, "employeeType", "Perm", "gender", "M"),
            Map.of("employee_id", "E3", "name", "E3", "role", "Developer", "rate", 30, "unit", "hour", "rating", 5, "employeeType", "Perm", "gender", "M"),
            Map.of("employee_id", "E4", "name", "E4", "role", "Developer", "rate", 15, "unit", "hour", "rating", 3, "employeeType", "Perm", "gender", "M")
        ));

        Map<String, Object> result = solverService.solveShift(req);
        assertEquals("success", result.get("status"));
        
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("daily_summary");
        List<Map<String, Object>> assignedEmployees = (List<Map<String, Object>>) assignments.get(0).get("assignments");
        assertEquals(2, assignedEmployees.size());
        
        boolean hasE1 = assignedEmployees.stream().anyMatch(e -> "E1".equals(e.get("employeeId")));
        boolean hasE4 = assignedEmployees.stream().anyMatch(e -> "E4".equals(e.get("employeeId")));
        
        assertTrue(hasE1, "Solver MUST select the absolute mathematically best entity (E1)");
        assertTrue(hasE4, "Solver MUST select the second mathematically best entity (E4)");
    }
}
