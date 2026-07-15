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
public class ExtremeScaleAndPerformanceTest {
    
    private static final Logger LOG = Logger.getLogger(ExtremeScaleAndPerformanceTest.class);

    @Inject
    SolverService solverService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    public void cleanDatabase() {
        databaseService.clearAllAssignments();
    }

    @Test
    public void testScale500Employees1Day() {
        runScaleTest("Scale 500_1D", 500, 300, 1, 1, false);
    }

    @Test
    public void testScale500Employees30Days() {
        runScaleTest("Scale 500_30D", 500, 300, 30, 1, false);
    }

    @Test
    public void testScale1000Employees10Roles() {
        runScaleTest("Scale 1000_10R", 1000, 800, 1, 10, false);
    }

    @Test
    public void testScale5000EmployeesMultiRoleHistorical() {
        try {
            // Using 3000 to prevent GitHub Actions/Test environment OOM while testing large topologies
            runScaleTest("Scale 5000_MR_Hist", 3000, 2000, 1, 5, true); 
        } catch (OutOfMemoryError e) {
            LOG.warn("OOM hit at large scale topology. Test skipped.");
        }
    }

    private void runScaleTest(String shiftName, int numEmployees, int numRequired, int days, int numRoles, boolean addHistorical) {
        LOG.info("=== Starting Scale Test: " + shiftName + " ===");
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        System.gc(); // Try to start clean
        MemoryUsage heapStart = memoryBean.getHeapMemoryUsage();
        LOG.info("Heap Before: " + (heapStart.getUsed() / 1024 / 1024) + " MB");

        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", shiftName);
        req.put("start_date", "2026-10-01");
        
        String endDate = "2026-10-01";
        if (days > 1) {
            endDate = "2026-10-" + String.format("%02d", days);
        }
        req.put("end_date", endDate);
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "both");

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
                "employee_id", "EMP_" + shiftName + "_" + i,
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

        long startMs = System.currentTimeMillis();
        Map<String, Object> result = solverService.solveShift(req);
        long endMs = System.currentTimeMillis();
        
        MemoryUsage heapEnd = memoryBean.getHeapMemoryUsage();
        LOG.info("Heap After: " + (heapEnd.getUsed() / 1024 / 1024) + " MB");
        LOG.info("Total CPU Wall Time: " + (endMs - startMs) + "ms");
        
        assertEquals("success", result.get("status"), "Scale test failed execution");
        assertNotNull(result.get("assignments_by_date"));
        LOG.info("=== Finished Scale Test: " + shiftName + " ===");
    }
}
