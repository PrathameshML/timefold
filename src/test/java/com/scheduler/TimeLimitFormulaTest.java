package com.scheduler;

import com.scheduler.service.SolverService;
import com.scheduler.service.DatabaseService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TimeLimitFormulaTest {

    @Inject
    SolverService solverService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    public void cleanDatabase() {
        databaseService.clearAllAssignments();
    }

    @Test
    public void testDynamicTimeLimitFormula1Entity() {
        // 1 employee × 1 day: perDayBudget = 2 + (1/20) = 2, total = max(5, 2*1) = 5s
        Map<String, Object> req = createRequest(1, 1);
        long startTime = System.currentTimeMillis();
        solverService.solveShift(req);
        long endTime = System.currentTimeMillis();
        long durationSec = (endTime - startTime) / 1000;
        
        // Should be roughly 5 seconds (new minimum floor). Allow up to 12s on slow runners.
        assertTrue(durationSec <= 12, "Expected ~5s max time limit, got " + durationSec + "s");
    }

    @Test
    public void testDynamicTimeLimitFormula40Entities() {
        // 40 employees × 1 day: perDayBudget = 2 + (40/20) = 4, total = max(5, 4*1) = 5s
        Map<String, Object> req = createRequest(40, 1);
        long startTime = System.currentTimeMillis();
        solverService.solveShift(req);
        long endTime = System.currentTimeMillis();
        long durationSec = (endTime - startTime) / 1000;
        
        // timeLimit=5s, unimproved=2s. Solver may exit early if converged.
        assertTrue(durationSec >= 1 && durationSec <= 15, "Expected 2-5s time limit, got " + durationSec + "s");
    }

    @Test
    public void testDynamicTimeLimitFormula100Entities() {
        // 100 employees × 1 day: perDayBudget = 2 + (100/20) = 7, total = max(5, 7*1) = 7s
        Map<String, Object> req = createRequest(100, 1);
        long startTime = System.currentTimeMillis();
        solverService.solveShift(req);
        long endTime = System.currentTimeMillis();
        long durationSec = (endTime - startTime) / 1000;
        
        // timeLimit=7s, unimproved=max(2, 7/3)=2s. Solver may exit early if converged.
        assertTrue(durationSec >= 1 && durationSec <= 25, "Expected 2-7s time limit, got " + durationSec + "s");
    }

    @Test
    public void testMultiDayTimeLimitScaling() {
        // 10 employees × 7 days: perDayBudget = 2 + (10/20) = 2, total = max(5, 2*7) = 14s
        Map<String, Object> req = createRequest(10, 7);
        long startTime = System.currentTimeMillis();
        solverService.solveShift(req);
        long endTime = System.currentTimeMillis();
        long durationSec = (endTime - startTime) / 1000;
        
        // With unimproved limit = max(2, 14/3) = 4s, solver may exit early if converged.
        // Total time should be between 4s (unimproved exit) and 20s (full + overhead).
        assertTrue(durationSec >= 3 && durationSec <= 20, 
            "Expected 4-14s time limit for 10 employees × 7 days, got " + durationSec + "s");
    }

    private Map<String, Object> createRequest(int employeeCount, int days) {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "TimeLimit Shift");
        req.put("start_date", "2026-10-01");
        // Calculate end date based on days
        java.time.LocalDate endDate = java.time.LocalDate.parse("2026-10-01").plusDays(days - 1);
        req.put("end_date", endDate.toString());
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "both");

        req.put("roles", List.of(
            Map.of("role_name", "Developer", "max_workers", employeeCount, "rating", 1)
        ));

        List<Map<String, Object>> employees = new ArrayList<>();
        for (int i = 0; i < employeeCount; i++) {
            employees.add(Map.of(
                "employee_id", "EMP_" + i,
                "name", "Emp " + i,
                "role", "Developer",
                "rate", 20,
                "unit", "hour",
                "rating", 3,
                "employeeType", "Perm",
                "gender", "M"
            ));
        }
        req.put("existing_users", employees);
        return req;
    }
}
