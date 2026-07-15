package com.scheduler;

import com.scheduler.service.DatabaseService;
import com.scheduler.service.SolverService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    public void cleanDatabase() {
        databaseService.clearAllAssignments();
    }

    @Test
    public void testNoOverlappingShiftsConstraintPreventsDoubleBooking() throws Exception {
        databaseService.clearAllAssignments();

        // 1. Assign E1 to Morning Shift
        Map<String, Object> req1 = createBasicRequest("Morning Shift", "E1");
        solverService.solveShift(req1);

        // Verify E1 was assigned to Morning
        Map<String, String> savedAfterMorning = databaseService.loadAssignmentsForDate("2026-10-01");
        assertTrue(savedAfterMorning.containsKey("E1"), "E1 should be assigned to Morning Shift");

        // 2. Now try to assign E1 to Evening Shift on the SAME DATE
        //    The noOverlappingShifts constraint (via ExistingAssignment ProblemFact)
        //    should prevent E1 from being assigned again.
        Map<String, Object> req2 = createBasicRequest("Evening Shift", "E1");
        solverService.solveShift(req2);

        // 3. Verify E1 is NOT double-booked
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT shift_name FROM shift_assignments WHERE employee_id='E1' AND assignment_date='2026-10-01'")) {
            
            int count = 0;
            while(rs.next()) {
                count++;
            }
            assertEquals(1, count, "Employee E1 should have exactly 1 shift — the noOverlappingShifts constraint should prevent double-booking!");
        }
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
}
