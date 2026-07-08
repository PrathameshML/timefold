package com.scheduler;

import com.scheduler.service.DatabaseService;
import com.scheduler.service.SolverService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class DatabaseIntegrityTest {

    @Inject
    SolverService solverService;

    @Inject
    DatabaseService databaseService;
    
    @Inject
    javax.sql.DataSource dataSource;

    @Test
    public void testOverrideExistingTrueClearsAssignments() {
        // First solve
        Map<String, Object> req1 = createBasicRequest("DB Shift");
        solverService.solveShift(req1);

        // Verify it was saved
        Map<String, String> saved = databaseService.loadAssignmentsForDate("2026-10-01");
        assertFalse(saved.isEmpty());
        int initialSize = saved.size();

        // Solve again with override=true
        Map<String, Object> req2 = createBasicRequest("DB Shift");
        req2.put("overrideExisting", true);
        solverService.solveShift(req2);

        Map<String, String> savedAfterOverride = databaseService.loadAssignmentsForDate("2026-10-01");
        assertEquals(initialSize, savedAfterOverride.size(), "Size should be the same as it was overridden, not duplicated");
    }

    @Test
    public void testOverrideExistingFalseAppendsAssignments() {
        // Clear all first to be safe
        databaseService.clearAllAssignments();

        Map<String, Object> req1 = createBasicRequest("DB Shift 1");
        solverService.solveShift(req1);

        Map<String, String> saved = databaseService.loadAssignmentsForDate("2026-10-01");
        int size1 = saved.size();
        assertTrue(size1 > 0);

        // Solve again with override=false and a DIFFERENT employee
        Map<String, Object> req2 = createBasicRequest("DB Shift 2", "E2");
        req2.put("overrideExisting", false);
        solverService.solveShift(req2);

        Map<String, String> savedAfterAppend = databaseService.loadAssignmentsForDate("2026-10-01");
        assertTrue(savedAfterAppend.size() > size1, "New assignments should be appended without clearing");
    }

    private Map<String, Object> createBasicRequest(String name) {
        return createBasicRequest(name, "E1");
    }

    private Map<String, Object> createBasicRequest(String name, String empId) {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", name);
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "both");
        
        req.put("roles", List.of(
            Map.of("role_name", "Developer", "max_workers", 1, "rating", 3)
        ));

        req.put("existing_users", List.of(
            Map.of("employee_id", empId, "name", empId, "role", "Developer", "rate", 20, "unit", "hour", "rating", 5, "employeeType", "Perm", "gender", "M")
        ));
        return req;
    }
    
    @Test
    public void testNoOverlappingShiftsIsMathematicallyDead() throws Exception {
        databaseService.clearAllAssignments();

        // 1. Assign E1 to Morning Shift
        Map<String, Object> req1 = createBasicRequest("Morning Shift", "E1");
        req1.put("overrideExisting", true); // Bypass the solver pool filter
        solverService.solveShift(req1);
        
        // 2. Assign E1 to Evening Shift on the SAME DATE
        Map<String, Object> req2 = createBasicRequest("Evening Shift", "E1");
        req2.put("overrideExisting", true); // Bypass the solver pool filter
        solverService.solveShift(req2);

        // 3. Prove they are both saved in the database!
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT shift_name FROM shift_assignments WHERE employee_id='E1' AND assignment_date='2026-10-01'")) {
            
            int count = 0;
            while(rs.next()) {
                count++;
                System.out.println("Found shift in DB for E1: " + rs.getString("shift_name"));
            }
            assertEquals(2, count, "Employee E1 should have 2 overlapping shifts on the same day in the DB, proving the constraint never stopped it!");
        }
    }
}
