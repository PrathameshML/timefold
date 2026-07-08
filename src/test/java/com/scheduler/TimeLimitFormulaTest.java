package com.scheduler;

import com.scheduler.service.SolverService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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

    @Test
    public void testDynamicTimeLimitFormula1Entity() {
        // 1 entity = 2s limit
        Map<String, Object> req = createRequest(1);
        long startTime = System.currentTimeMillis();
        solverService.solveShift(req);
        long endTime = System.currentTimeMillis();
        long durationSec = (endTime - startTime) / 1000;
        
        // Should be roughly 2 seconds
        assertTrue(durationSec >= 1 && durationSec <= 4, "Expected ~2s time limit, got " + durationSec + "s");
    }

    @Test
    public void testDynamicTimeLimitFormula40Entities() {
        // 40 entities = 2 + (40/20) = 4s limit
        Map<String, Object> req = createRequest(40);
        long startTime = System.currentTimeMillis();
        solverService.solveShift(req);
        long endTime = System.currentTimeMillis();
        long durationSec = (endTime - startTime) / 1000;
        
        // Should be roughly 4 seconds
        assertTrue(durationSec >= 3 && durationSec <= 6, "Expected ~4s time limit, got " + durationSec + "s");
    }

    @Test
    public void testDynamicTimeLimitFormula100Entities() {
        // 100 entities = 2 + (100/20) = 7s limit
        Map<String, Object> req = createRequest(100);
        long startTime = System.currentTimeMillis();
        solverService.solveShift(req);
        long endTime = System.currentTimeMillis();
        long durationSec = (endTime - startTime) / 1000;
        
        // Should be roughly 7 seconds
        assertTrue(durationSec >= 6 && durationSec <= 10, "Expected ~7s time limit, got " + durationSec + "s");
    }

    private Map<String, Object> createRequest(int employeeCount) {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "TimeLimit Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "both");
        req.put("overrideExisting", true);
        
        // Disable unimproved limit so the solver is forced to run until the dynamic timeLimit
        req.put("unimproved_time_limit_seconds", 30);

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
