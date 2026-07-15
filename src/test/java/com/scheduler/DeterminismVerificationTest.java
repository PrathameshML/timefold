package com.scheduler;

import com.scheduler.service.SolverService;
import com.scheduler.service.DatabaseService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag("extreme")
public class DeterminismVerificationTest {
    
    private static final Logger LOG = Logger.getLogger(DeterminismVerificationTest.class);

    @Inject
    SolverService solverService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    public void cleanDatabase() {
        databaseService.clearAllAssignments();
    }

    @Test
    public void testAbsoluteDeterminism100Runs() {
        LOG.info("=== Starting 100-Run Determinism Test ===");
        
        String baselineScore = null;
        
        for (int run = 1; run <= 100; run++) {
            Map<String, Object> req = createDeterminismRequest();
            // 1s limit to keep the 100 runs fast, but ensures enough time to reach local optima on tiny dataset
            req.put("time_limit_seconds", 1);
            
            Map<String, Object> result = solverService.solveShift(req);
            assertEquals("success", result.get("status"));
            
            List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments_by_date");
            String currentScore = (String) assignments.get(0).get("score");
            
            // Score must have zero hard violations
            assertTrue(currentScore.startsWith("0hard"), "Run " + run + " had hard constraints: " + currentScore);
            
            if (baselineScore == null) {
                baselineScore = currentScore;
            } else {
                assertEquals(baselineScore, currentScore, "Determinism failed on run " + run + "! Score fluctuated.");
            }
        }
        
        LOG.info("All 100 runs produced perfectly identical scores: " + baselineScore);
        LOG.info("=== Finished 100-Run Determinism Test ===");
    }

    private Map<String, Object> createDeterminismRequest() {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Determinism Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "both");
        
        req.put("roles", List.of(
            Map.of("role_name", "Developer", "max_workers", 3, "rating", 3)
        ));

        // Create 10 employees to fill 3 spots. Many optimal combinations possible.
        List<Map<String, Object>> employees = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            employees.add(Map.of(
                "employee_id", "EMP_DET_" + i,
                "name", "Emp " + i,
                "role", "Developer",
                "rate", 20, 
                "unit", "hour",
                "rating", 5, 
                "employeeType", "Permanent",
                "gender", "M"
            ));
        }
        req.put("existing_users", employees);
        return req;
    }
}
