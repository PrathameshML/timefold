package com.scheduler;

import com.scheduler.service.SolverService;
import com.scheduler.service.DatabaseService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag("extreme")
public class SolverQualityBenchmarkTest {
    
    private static final Logger LOG = Logger.getLogger(SolverQualityBenchmarkTest.class);

    @Inject
    SolverService solverService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    public void cleanDatabase() {
        databaseService.clearAllAssignments();
    }

    @Test
    public void runQualityBenchmark() {
        LOG.info("=== Starting Solver Quality Benchmark ===");
        int[] timeLimits = {2, 5, 10, 30, 60};
        
        for (int limit : timeLimits) {
            LOG.info("Testing with Time Limit: " + limit + " seconds");
            
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            System.gc(); // Try to start clean
            MemoryUsage heapStart = memoryBean.getHeapMemoryUsage();
            
            Map<String, Object> req = createBenchmarkRequest(limit);

            long startMs = System.currentTimeMillis();
            Map<String, Object> result = solverService.solveShift(req);
            long endMs = System.currentTimeMillis();
            
            MemoryUsage heapEnd = memoryBean.getHeapMemoryUsage();
            
            assertEquals("success", result.get("status"));
            
            List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("daily_summary");
            Map<String, Object> dayResult = assignments.get(0);
            
            String score = (String) result.get("solver_score");
            int assigned = (Integer) dayResult.get("count");
            int unassigned = 300 - assigned;
            long solveTime = (endMs - startMs);
            long memUsed = (heapEnd.getUsed() - heapStart.getUsed()) / 1024 / 1024;
            
            LOG.info(String.format("BENCHMARK [%ds limit] -> Time: %dms | Mem Delta: %dMB | Score: %s | Assigned: %d | Unassigned: %d", 
                limit, solveTime, memUsed, score, assigned, unassigned));
        }
        LOG.info("=== Completed Solver Quality Benchmark ===");
    }

    private Map<String, Object> createBenchmarkRequest(int timeLimit) {
        int numEmployees = 500;
        int numRequired = 300;
        int numRoles = 5;

        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Benchmark Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "both");
        req.put("time_limit_seconds", timeLimit);

        List<Map<String, Object>> rolesList = new ArrayList<>();
        int workersPerRole = numRequired / numRoles;
        for (int r = 1; r <= numRoles; r++) {
            rolesList.add(Map.of("role_name", "Role_" + r, "max_workers", workersPerRole, "rating", 3));
        }
        req.put("roles", rolesList);

        List<Map<String, Object>> employees = new ArrayList<>();
        for (int i = 0; i < numEmployees; i++) {
            String assignedRole = "Role_" + ((i % numRoles) + 1);
            employees.add(Map.of(
                "employee_id", "EMP_BENCH_" + i,
                "name", "Emp " + i,
                "role", assignedRole,
                "rate", 15 + (i % 10),
                "unit", "hour",
                "rating", 3 + (i % 3),
                "employeeType", "Permanent",
                "gender", (i % 2 == 0) ? "M" : "F"
            ));
        }
        req.put("existing_users", employees);
        return req;
    }
}
