package com.scheduler.service;

import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverJob;
import com.scheduler.model.*;
import com.scheduler.solver.ShiftConstraintProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@ApplicationScoped
public class SolverService {
    private static final Logger LOG = Logger.getLogger(SolverService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private SolverManager<ShiftSchedule, String> solverManager;

    @Inject
    DatabaseService databaseService;

    // Cache of constraint configurations
    private List<ConstraintConfig> constraintConfigs = new ArrayList<>();

    @PostConstruct
    public void init() {
        LOG.debug("SolverService initialized");
        loadConstraintConfigs();
        
        ai.timefold.solver.core.config.solver.SolverConfig solverConfig = new ai.timefold.solver.core.config.solver.SolverConfig()
                .withSolutionClass(ShiftSchedule.class)
                .withEntityClasses(EmployeeAssignment.class)
                .withConstraintProviderClass(ShiftConstraintProvider.class)
                .withTerminationSpentLimit(java.time.Duration.ofSeconds(10));
                
        ai.timefold.solver.core.api.solver.SolverFactory<ShiftSchedule> solverFactory = ai.timefold.solver.core.api.solver.SolverFactory.create(solverConfig);
        this.solverManager = SolverManager.create(solverFactory, new ai.timefold.solver.core.config.solver.SolverManagerConfig());
    }

    public void loadConstraintConfigs() {
        List<ConstraintConfig> configs = databaseService.loadAllConstraintConfigs();
        if (configs.isEmpty()) {
            configs = getDefaultConstraintConfigs();
            databaseService.insertDefaultConstraints(configs);
        }
        constraintConfigs = new ArrayList<>(configs);
        LOG.debug("Loaded " + constraintConfigs.size() + " constraints");
    }

    private List<ConstraintConfig> getDefaultConstraintConfigs() {
        return List.of(
            new ConstraintConfig(1, "noOverlappingShifts", "No overlapping/concurrent shifts per employee", "HARD", null, null),
            new ConstraintConfig(2, "wageOptimization", "Assign employees preferring lower wages", "SOFT", 1000.0, "wageMultiplier"),
            new ConstraintConfig(3, "maximizeRating", "Reward higher-rated employees", "SOFT", 100.0, "ratingMultiplier"),
            new ConstraintConfig(4, "maxWorkersPerRole", "Do not exceed required workers per role", "HARD", null, null),
            new ConstraintConfig(5, "minimumRatingRequirement", "Enforce minimum rating by role", "HARD", null, null),
            new ConstraintConfig(6, "everyShiftPlanned", "All requested shifts must be filled", "HARD", null, null)
        );
    }

    public List<ConstraintConfig> getAllConstraints() {
        return databaseService.loadAllConstraintConfigs();
    }

    public void updateConstraint(ConstraintConfig config) {
        databaseService.saveConstraintConfig(config);
        loadConstraintConfigs(); // Reload cache
    }

    public Map<String, Object> solveShift(Map<String, Object> input) {
        List<String> missingFields = validateShiftInput(input);
        if (!missingFields.isEmpty()) {
            return Map.of("status", "error", "message", "Missing required fields: " + String.join(", ", missingFields));
        }

        String targetShift = (String) input.get("shift_name");
        String startDateStr = (String) input.get("start_date");
        String endDateStr = (String) input.get("end_date");
        String startTimeStr = (String) input.get("start_time");
        String endTimeStr = (String) input.get("end_time");

        // Parse shifts & times
        LocalTime startLocalTime = LocalTime.parse(startTimeStr, TIME_FORMATTER);
        LocalTime endLocalTime = LocalTime.parse(endTimeStr, TIME_FORMATTER);
        List<String> shiftTypes = List.of(targetShift);

        // Parse roles & requirements
        List<Map<String, Object>> rolesList = (List<Map<String, Object>>) input.get("roles");
        List<RoleRequirement> roleRequirements = new ArrayList<>();
        List<RatingRequirement> ratingRequirements = new ArrayList<>();

        for (Map<String, Object> roleObj : rolesList) {
            String roleName = (String) roleObj.get("role_name");
            if (roleName == null || roleName.trim().isEmpty()) continue;

            int maxWorkers = parseNumber(roleObj.get("max_workers")).intValue();
            roleRequirements.add(new RoleRequirement(roleName, maxWorkers));
            
            if (roleObj.containsKey("allowed_ratings")) {
                List<?> rawRatings = (List<?>) roleObj.get("allowed_ratings");
                List<Integer> ratings = rawRatings.stream()
                        .map(this::parseRating)
                        .collect(Collectors.toList());
                ratingRequirements.add(new RatingRequirement(roleName, ratings));
            } else {
                ratingRequirements.add(new RatingRequirement(roleName, List.of(3, 4, 5))); // default allowed ratings
            }
        }

        // Generate dates
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        List<LocalDate> dateRange = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            dateRange.add(d);
        }

        // Prepare existing users
        List<Map<String, Object>> existingUsers = (List<Map<String, Object>>) input.get("existing_users");
        Map<String, EmployeeInfo> employeeInfoMap = new HashMap<>();
        for (Map<String, Object> u : existingUsers) {
            String empId = (String) u.get("id");
            if (empId == null || empId.trim().isEmpty()) continue;
            
            String name = (String) u.get("name");
            String category = (String) u.get("category");
            String role = (String) u.get("role");
            String gender = (String) u.get("gender");
            double hourlyWage = parseNumber(u.get("hourly_wage")).doubleValue();
            
            EmployeeInfo emp = new EmployeeInfo(empId, name, category, gender, hourlyWage, "Operations", role);
            emp.setPerformanceRating(parseRating(u.get("rating")));
            employeeInfoMap.put(empId, emp);
        }

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("status", "success");
        responseData.put("message", "Assignments generated successfully");
        responseData.put("shift_name", targetShift);
        
        List<Map<String, Object>> assignmentsByDate = new ArrayList<>();

        // Ensure we actually have employees to schedule
        if (employeeInfoMap.isEmpty()) {
            return Map.of("status", "error", "message", "No valid employees provided in existing_users");
        }

        // Get the current active constraints to pass to the solver
        List<ConstraintConfig> activeConstraints = new ArrayList<>(constraintConfigs);

        try {
            // Solve day by day
            for (LocalDate currentDate : dateRange) {
                String dateStr = currentDate.toString();
                
                // Fetch historical assignments from DB to avoid overlapping
                Map<String, String> dbAssignments = databaseService.loadAssignmentsForDate(dateStr);
                
                List<EmployeeAssignment> allEntities = new ArrayList<>();
                int assignmentIdSeq = 1;

                for (EmployeeInfo emp : employeeInfoMap.values()) {
                    EmployeeAssignment entity = new EmployeeAssignment(
                            dateStr + "_" + emp.getId() + "_" + assignmentIdSeq++,
                            emp.getId(), emp.getName(), dateStr,
                            emp.getCategory(), emp.getGender(), emp.getDepartment(), emp.getPosition()
                    );
                    entity.setHourlyWage(emp.getHourlyWage());
                    entity.setPerformanceRating(emp.getPerformanceRating());
                    entity.setShiftStartStr(startTimeStr);
                    entity.setShiftEndStr(endTimeStr);
                    entity.setLocalDateObj(currentDate);
                    entity.setActiveConfigs(constraintConfigs);

                    // If they already have a shift today in the database, pin them to that shift 
                    // so Timefold knows they are busy and doesn't assign them to this targetShift
                    if (dbAssignments.containsKey(emp.getId())) {
                        entity.setShift(dbAssignments.get(emp.getId()));
                        entity.setPinned(true);
                    } else {
                        entity.setShift(null); // Let Timefold assign
                        entity.setPinned(false);
                    }
                    allEntities.add(entity);
                }

                ShiftSchedule problem = new ShiftSchedule(allEntities, shiftTypes, roleRequirements, ratingRequirements, activeConstraints);
                problem.setRequestedShiftName(targetShift);

                String jobId = UUID.randomUUID().toString();
                SolverJob<ShiftSchedule, String> solverJob = solverManager.solve(jobId, problem);
                
                ShiftSchedule solution = solverJob.getFinalBestSolution();
                
                List<Map<String, Object>> assignedEmployeesForDay = new ArrayList<>();
                
                for (EmployeeAssignment assignment : solution.getAssignments()) {
                    // Only process assignments for the target shift that were just assigned
                    if (targetShift.equals(assignment.getShift()) && !assignment.isPinned()) {
                        
                        // Save to database
                        databaseService.syncAssignment(
                            dateStr, targetShift, assignment.getEmployeeId(), assignment.getEmployeeName(),
                            assignment.getPosition(), assignment.getCategory(), assignment.getGender(),
                            assignment.getPerformanceRating(), startTimeStr, endTimeStr
                        );
                        
                        Map<String, Object> empData = new HashMap<>();
                        empData.put("id", assignment.getEmployeeId());
                        empData.put("name", assignment.getEmployeeName());
                        empData.put("assigned_shift", targetShift);
                        empData.put("role", assignment.getPosition());
                        empData.put("rating", assignment.getPerformanceRating());
                        empData.put("hourly_wage", assignment.getHourlyWage());
                        empData.put("gender", assignment.getGender());
                        assignedEmployeesForDay.add(empData);
                    }
                }

                Map<String, Object> dayResult = new HashMap<>();
                dayResult.put("date", dateStr);
                dayResult.put("total_assigned", assignedEmployeesForDay.size());
                dayResult.put("assigned_employees", assignedEmployeesForDay);
                dayResult.put("score", solution.getScore() != null ? solution.getScore().toString() : "Unknown");
                assignmentsByDate.add(dayResult);
            }
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Solving failed", e);
            return Map.of("status", "error", "message", "Solving failed: " + e.getMessage());
        }

        responseData.put("assignments_by_date", assignmentsByDate);
        return responseData;
    }

    private List<String> validateShiftInput(Map<String, Object> shift) {
        List<String> missingFields = new ArrayList<>();
        String[] requiredFields = {
                "shift_name", "start_date", "end_date",
                "start_time", "end_time", "roles", "existing_users"
        };

        for (String field : requiredFields) {
            if (!shift.containsKey(field) || shift.get(field) == null) {
                missingFields.add(field);
            }
        }
        
        // Hardening validations
        if (shift.containsKey("roles")) {
            Object roles = shift.get("roles");
            if (!(roles instanceof List) || ((List<?>) roles).isEmpty()) {
                missingFields.add("roles (must be a non-empty list)");
            }
        }
        
        if (shift.containsKey("existing_users")) {
            Object users = shift.get("existing_users");
            if (!(users instanceof List) || ((List<?>) users).isEmpty()) {
                missingFields.add("existing_users (must be a non-empty list)");
            }
        }
        
        return missingFields;
    }

    private Number parseNumber(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) return (Number) val;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseRating(Object ratingObj) {
        if (ratingObj == null) return 3;
        if (ratingObj instanceof Number) return ((Number) ratingObj).intValue();
        if (ratingObj instanceof String) {
            String str = (String) ratingObj;
            if ("Any".equalsIgnoreCase(str) || "All".equalsIgnoreCase(str)) return 3;
            try {
                return Integer.parseInt(str);
            } catch (NumberFormatException e) {
                return 3;
            }
        }
        return 3;
    }
}
