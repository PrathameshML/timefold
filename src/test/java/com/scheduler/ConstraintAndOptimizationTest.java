package com.scheduler;

import com.scheduler.service.SolverService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ConstraintAndOptimizationTest {

    @Inject
    SolverService solverService;

    @Test
    public void testSolverServiceCostOptimization() {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Cost Test Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "cost");
        
        req.put("roles", List.of(
            Map.of("role_name", "Developer", "max_workers", 1, "rating", 3)
        ));

        req.put("existing_users", List.of(
            Map.of(
                "employee_id", "EMP_EXPENSIVE",
                "name", "Alice Expensive",
                "role", "Developer",
                "rate", 50,
                "unit", "hour",
                "rating", 5,
                "employeeType", "Permanent",
                "gender", "Female"
            ),
            Map.of(
                "employee_id", "EMP_CHEAP",
                "name", "Bob Cheap",
                "role", "Developer",
                "rate", 20,
                "unit", "hour",
                "rating", 3,
                "employeeType", "Contract",
                "gender", "Male"
            )
        ));

        Map<String, Object> result = solverService.solveShift(req);
        assertEquals("success", result.get("status"));
        
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments_by_date");
        assertFalse(assignments.isEmpty());
        
        List<Map<String, Object>> assignedEmployees = (List<Map<String, Object>>) assignments.get(0).get("assigned_employees");
        assertEquals(1, assignedEmployees.size());
        
        // Cost mode should pick the cheaper one (Bob) even though Alice has a higher rating
        assertEquals("EMP_CHEAP", assignedEmployees.get(0).get("id"));
    }
    
    @Test
    public void testSolverServiceQualityOptimization() {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Quality Test Shift");
        req.put("start_date", "2026-10-01");
        req.put("end_date", "2026-10-01");
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", "quality");
        
        req.put("roles", List.of(
            Map.of("role_name", "Developer", "max_workers", 1, "rating", 3)
        ));

        req.put("existing_users", List.of(
            Map.of(
                "employee_id", "EMP_EXPENSIVE",
                "name", "Alice Expensive",
                "role", "Developer",
                "rate", 50,
                "unit", "hour",
                "rating", 5,
                "employeeType", "Permanent",
                "gender", "Female"
            ),
            Map.of(
                "employee_id", "EMP_CHEAP",
                "name", "Bob Cheap",
                "role", "Developer",
                "rate", 20,
                "unit", "hour",
                "rating", 3,
                "employeeType", "Contract",
                "gender", "Male"
            )
        ));

        Map<String, Object> result = solverService.solveShift(req);
        assertEquals("success", result.get("status"));
        
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments_by_date");
        List<Map<String, Object>> assignedEmployees = (List<Map<String, Object>>) assignments.get(0).get("assigned_employees");
        assertEquals(1, assignedEmployees.size());
        
        // Quality mode should pick the higher rated one (Alice) regardless of cost
        assertEquals("EMP_EXPENSIVE", assignedEmployees.get(0).get("id"));
    }
}
