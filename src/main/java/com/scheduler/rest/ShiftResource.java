package com.scheduler.rest;

import com.scheduler.model.ConstraintConfig;
import com.scheduler.service.DatabaseService;
import com.scheduler.service.SolverService;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ShiftResource {

    private static final Logger LOG = Logger.getLogger(ShiftResource.class);

    @Inject
    SolverService solverService;

    @Inject
    DatabaseService databaseService;

    @POST
    @Path("/shifts/assign")
    public Response assignShift(Map<String, Object> input) {
        LOG.debug("Received shift assignment request");
        try {
            Map<String, Object> result = solverService.solveShift(input);
            if ("error".equals(result.get("status"))) {
                return Response.status(Response.Status.BAD_REQUEST).entity(result).build();
            }
            return Response.ok(result).build();
        } catch (Exception e) {
            LOG.error("Failed to solve shift", e);
            return Response.serverError()
                    .entity(Map.of("status", "error", "message", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/shifts/batch-assign")
    public Response batchAssignShifts(Map<String, Object> input) {
        Object shiftsObj = input.get("shifts");
        if (!(shiftsObj instanceof List)) {
            return Response.status(400).entity(Map.of(
                    "error", "Invalid format",
                    "required", "shifts must be a list of objects"
            )).build();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batchRequests = (List<Map<String, Object>>) shiftsObj;

        if (batchRequests.isEmpty()) {
            return Response.status(400).entity(Map.of(
                    "error", "Missing required field",
                    "required", "shifts array cannot be empty"
            )).build();
        }

        LOG.info("Received batch assignment request with " + batchRequests.size() + " shifts");
        List<Map<String, Object>> results = new ArrayList<>();
        int totalAssignments = 0;
        int totalSkipped = 0;
        double totalSolverTime = 0.0;
        int successfulShifts = 0;
        int failedShifts = 0;
        java.util.Set<String> uniqueDates = new java.util.HashSet<>();

        for (Map<String, Object> request : batchRequests) {
            try {
                Map<String, Object> result = solverService.solveShift(request);
                results.add(result);
                
                if ("error".equals(result.get("status"))) {
                    failedShifts++;
                } else {
                    successfulShifts++;
                    totalAssignments += (int) result.getOrDefault("new_assignments_made", 0);
                    totalSkipped += (int) result.getOrDefault("skipped_count", 0);
                    totalSolverTime += ((Number) result.getOrDefault("solver_time_seconds", 0.0)).doubleValue();
                }
                
                String startDate = (String) request.get("start_date");
                String endDate = (String) request.get("end_date");
                if (startDate != null) {
                    java.time.LocalDate start = java.time.LocalDate.parse(startDate);
                    java.time.LocalDate end = (endDate != null && !endDate.trim().isEmpty()) ? java.time.LocalDate.parse(endDate) : start;
                    for (java.time.LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                        uniqueDates.add(d.toString());
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to solve shift in batch", e);
                failedShifts++;
                results.add(Map.of(
                        "status", "error",
                        "message", e.getMessage(),
                        "shift_name", request.getOrDefault("shift_name", "unknown")
                ));
            }
        }

        Map<String, Object> overallStats = new HashMap<>();
        overallStats.put("total_shifts_processed", batchRequests.size());
        overallStats.put("successful_shifts", successfulShifts);
        overallStats.put("failed_shifts", failedShifts);
        overallStats.put("total_assignments_made", totalAssignments);
        overallStats.put("total_working_days", uniqueDates.size());
        overallStats.put("total_skipped_assignments", totalSkipped);
        overallStats.put("total_solver_time_seconds", totalSolverTime);

        String summary = String.format(
                "Batch assignment completed. Processed %d shifts. Success: %d, Failed: %d. Total assignments: %d across %d days.",
                batchRequests.size(), successfulShifts, failedShifts, totalAssignments, uniqueDates.size()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", "completed");
        response.put("version", "v3");
        response.put("overall_statistics", overallStats);
        response.put("shift_results", results);
        response.put("summary", summary);

        return Response.ok(response).build();
    }



    @GET
    @Path("/constraints")
    public Response getConstraints() {
        return Response.ok(solverService.getAllConstraints()).build();
    }

    @PUT
    @Path("/constraints")
    public Response updateConstraints(List<ConstraintConfig> configs) {
        for (ConstraintConfig config : configs) {
            solverService.updateConstraint(config);
        }
        return Response.ok(Map.of("status", "success", "message", "Constraints updated successfully")).build();
    }

    @DELETE
    @Path("/shifts/clear-all")
    public Response clearAllAssignments() {
        try {
            databaseService.clearAllAssignments();
            return Response.ok(Map.of("status", "success", "message", "All assignments cleared successfully")).build();
        } catch (Exception e) {
            return Response.serverError()
                    .entity(Map.of("status", "error", "message", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/shifts/manual-assign")
    public Response manualAssignShift(Map<String, Object> input) {
        LOG.debug("Received manual shift assignment request");
        try {
            if (input == null) {
                return Response.status(400).entity(Map.of("error", "Request body cannot be empty")).build();
            }
            String date = (String) input.get("date");
            // Match legacy key "shift" but fallback to "shift_name" for flexibility
            String shiftName = (String) input.getOrDefault("shift", input.get("shift_name"));
            boolean overrideExisting = Boolean.TRUE.equals(input.get("overrideExisting"));
            
            if (date == null || shiftName == null) {
                return Response.status(400).entity(Map.of("error", "Missing required fields: date, shift (or shift_name)")).build();
            }

            try {
                java.sql.Date.valueOf(date);
            } catch (IllegalArgumentException e) {
                return Response.status(400).entity(Map.of("error", "Invalid date format: " + date + ". Expected YYYY-MM-DD")).build();
            }

            Object employeesObj = input.get("employees");
            if (!(employeesObj instanceof List)) {
                return Response.status(400).entity(Map.of("error", "employees must be a list of objects")).build();
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> employees = (List<Map<String, Object>>) employeesObj;
            
            // 1. Check for Duplicate Employee IDs in the request body
            java.util.Set<String> uniqueIds = new java.util.HashSet<>();
            List<Map<String, Object>> duplicateDetails = new ArrayList<>();
            for (Map<String, Object> emp : employees) {
                String empId = (String) emp.get("employee_id");
                if (empId != null && !uniqueIds.add(empId)) {
                    duplicateDetails.add(Map.of("employee_id", empId, "name", emp.getOrDefault("name", "Unknown")));
                }
            }
            if (!duplicateDetails.isEmpty()) {
                return Response.status(400).entity(Map.of(
                    "status", "error",
                    "error_type", "DUPLICATE_EMPLOYEE_IDS",
                    "message", "Duplicate employee IDs found in request",
                    "duplicates", duplicateDetails
                )).build();
            }

            // 2. Load existing assignments to prevent double-booking across shifts
            Map<String, String> existingAssignments = databaseService.loadAssignmentsForDate(date);
            
            int successCount = 0;
            int skipCount = 0;
            List<Map<String, Object>> assignedEmployees = new ArrayList<>();
            List<Map<String, Object>> skippedEmployees = new ArrayList<>();

            for (Map<String, Object> emp : employees) {
                String empId = (String) emp.get("employee_id");
                if (empId == null) continue;
                String name = (String) emp.getOrDefault("name", "Unknown");
                
                // If already assigned and we are not forcing an override, skip them!
                if (!overrideExisting && existingAssignments.containsKey(empId)) {
                    skippedEmployees.add(Map.of(
                        "employee_id", empId,
                        "name", name,
                        "reason", "Already assigned to " + existingAssignments.get(empId) + " on " + date
                    ));
                    skipCount++;
                    continue;
                }
                
                String role = (String) emp.getOrDefault("role", "Unknown");
                String category = (String) emp.getOrDefault("employee_category", "Unknown");
                String gender = (String) emp.getOrDefault("gender", "Unknown");
                int rating = emp.containsKey("rating") ? ((Number) emp.get("rating")).intValue() : 0;
                String startTime = (String) emp.get("start_time");
                String endTime = (String) emp.get("end_time");
                
                databaseService.syncAssignment(date, shiftName, empId, name, role, category, gender, rating, startTime, endTime);
                assignedEmployees.add(Map.of("employee_id", empId, "name", name, "gender", gender));
                successCount++;
            }
            
            // 3. Match legacy conflict behavior if everyone was skipped
            if (successCount == 0 && skipCount > 0) {
                return Response.status(409).entity(Map.of( // 409 Conflict
                    "status", "error",
                    "error_type", "ALL_EMPLOYEES_ALREADY_ASSIGNED",
                    "message", "No employees were assigned - all requested employees already have assignments on " + date,
                    "date", date,
                    "shift", shiftName,
                    "total_requested", employees.size(),
                    "skipped_count", skipCount,
                    "skipped_employees", skippedEmployees
                )).build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("Assigned %d out of %d employees", successCount, employees.size()));
            response.put("date", date);
            response.put("shift", shiftName);
            response.put("total_requested", employees.size());
            response.put("assigned_count", successCount);
            response.put("skipped_count", skipCount);
            if (!assignedEmployees.isEmpty()) response.put("assigned_employees", assignedEmployees);
            if (!skippedEmployees.isEmpty()) {
                response.put("skipped_employees", skippedEmployees);
                response.put("warning", skipCount + " employee(s) were skipped because they already have assignments on " + date);
            }
            
            return Response.ok(response).build();
        } catch (Exception e) {
            LOG.error("Failed to manual assign", e);
            return Response.serverError().entity(Map.of("status", "error", "message", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/shifts/clear")
    public Response clearAssignments(Map<String, Object> input) {
        LOG.debug("=== DELETE /shifts/clear ===");
        try {
            if (input == null) {
                return Response.status(400).entity(Map.of("error", "Request body cannot be empty")).build();
            }
            // ============ PARSE INPUT - Handle legacy formats ============
            Object daysObj = input.get("days");
            Object dayObj = input.get("day");
            Object shiftsObj = input.get("shifts");
            Object shiftObj = input.get("shift");
            Object employeesObj = input.get("employees");

            List<String> dates = new ArrayList<>();
            if (daysObj instanceof List) dates.addAll((List<String>) daysObj);
            else if (daysObj instanceof String) dates.add((String) daysObj);
            if (dayObj instanceof String) dates.add((String) dayObj);
            
            // Validate dates early to provide a 400 instead of a 500 error
            for (String d : dates) {
                try {
                    java.sql.Date.valueOf(d);
                } catch (IllegalArgumentException e) {
                    return Response.status(400).entity(Map.of("error", "Invalid date format: " + d + ". Expected YYYY-MM-DD")).build();
                }
            }

            List<String> shifts = new ArrayList<>();
            if (shiftsObj instanceof List) shifts.addAll((List<String>) shiftsObj);
            else if (shiftsObj instanceof String) shifts.add((String) shiftsObj);
            if (shiftObj instanceof String) shifts.add((String) shiftObj);

            List<String> employeeIds = new ArrayList<>();
            if (employeesObj instanceof List) employeeIds.addAll((List<String>) employeesObj);
            else if (employeesObj instanceof String) employeeIds.add((String) employeesObj);

            if (dates.isEmpty() && shifts.isEmpty() && employeeIds.isEmpty()) {
                return Response.status(400).entity(Map.of(
                    "error", "Must provide at least one filter in JSON body (day/days, shift/shifts, employees)"
                )).build();
            }
            
            int deletedCount = databaseService.clearAssignmentsWithFilters(dates, shifts, employeeIds);
            return Response.ok(Map.of(
                "status", "success",
                "message", "Cleared " + deletedCount + " assignments",
                "deleted_count", deletedCount
            )).build();
        } catch (Exception e) {
            LOG.error("Failed to clear assignments", e);
            return Response.serverError().entity(Map.of("status", "error", "message", e.getMessage())).build();
        }
    }
}
