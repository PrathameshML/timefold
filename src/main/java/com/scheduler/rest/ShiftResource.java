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
}
