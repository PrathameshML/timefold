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
        if (batchRequests == null || batchRequests.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("status", "error", "message", "Empty batch request list"))
                    .build();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        boolean hasErrors = false;

        for (Map<String, Object> request : batchRequests) {
            try {
                Map<String, Object> result = solverService.solveShift(request);
                results.add(result);
                if ("error".equals(result.get("status"))) {
                    hasErrors = true;
                }
            } catch (Exception e) {
                LOG.error("Failed to solve shift in batch", e);
                hasErrors = true;
                results.add(Map.of(
                        "status", "error",
                        "message", e.getMessage(),
                        "shift_name", request.getOrDefault("shift_name", "unknown")
                ));
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", hasErrors ? "partial_success" : "success");
        response.put("message", "Processed " + batchRequests.size() + " shift requests");
        response.put("results", results);

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
