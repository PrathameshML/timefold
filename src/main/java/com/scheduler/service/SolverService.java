package com.scheduler.service;

import com.scheduler.model.*;
import com.scheduler.solver.ShiftConstraintProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.*;

@ApplicationScoped
public class SolverService {
    private static final Logger LOG = Logger.getLogger(SolverService.class);

    @Inject
    DatabaseService databaseService;

    // Cache of constraint configurations
    private List<ConstraintConfig> constraintConfigs = new ArrayList<>();

    @PostConstruct
    public void init() {
        LOG.debug("SolverService initialized");
        loadConstraintConfigs();
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

        // Parse shifts
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
            
            if (roleObj.containsKey("rating")) {
                Object ratingObj = roleObj.get("rating");
                List<Integer> allowedRatings = new ArrayList<>();
                if (ratingObj instanceof Number) {
                    int min = ((Number) ratingObj).intValue();
                    for (int r = min; r <= 5; r++) allowedRatings.add(r);
                } else if (ratingObj instanceof String) {
                    String s = ((String) ratingObj).toLowerCase();
                    if (s.contains("any") || s.contains("all")) {
                        for (int r = 1; r <= 5; r++) allowedRatings.add(r);
                    } else {
                        try { 
                            int min = Integer.parseInt(s); 
                            for (int r = min; r <= 5; r++) allowedRatings.add(r); 
                        } catch (NumberFormatException e) { 
                            for (int r = 1; r <= 5; r++) allowedRatings.add(r); 
                        }
                    }
                } else {
                    for (int r = 1; r <= 5; r++) allowedRatings.add(r);
                }
                ratingRequirements.add(new RatingRequirement(roleName, allowedRatings));
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
            String empId = (String) u.get("employee_id");
            if (empId == null || empId.trim().isEmpty()) continue;
            
            String name = (String) u.get("name");
            String category = (String) u.get("employeeType");
            String role = (String) u.get("role");
            String gender = (String) u.get("gender");
            
            double hourlyWage = parseNumber(u.get("rate")).doubleValue();
            String unit = (String) u.get("unit");
            if ("day".equalsIgnoreCase(unit)) {
                hourlyWage = hourlyWage / 8.0;
            } else if ("month".equalsIgnoreCase(unit)) {
                hourlyWage = hourlyWage / (22.0 * 8.0);
            }
            
            EmployeeInfo emp = new EmployeeInfo(empId, name, category, gender, hourlyWage, "Operations", role);
            emp.setPerformanceRating(parseRating(u.get("rating")));
            employeeInfoMap.put(empId, emp);
        }

        // Optimization Parsing
        String optimization = input.containsKey("optimization") ? (String) input.get("optimization") : "both";
        optimization = optimization.toLowerCase();
        if (!optimization.equals("cost") && !optimization.equals("quality") && !optimization.equals("both")) {
            optimization = "both";
        }

        // Setup Active Constraints dynamically
        List<ConstraintConfig> activeConstraints = new ArrayList<>();
        for (ConstraintConfig cc : constraintConfigs) {
            ConstraintConfig copy = new ConstraintConfig(cc.getConstraintId(), cc.getConstraintName(), cc.getDescription(), cc.getSeverity(), cc.getParameterValue(), cc.getParameterName());
            copy.setEnabled(cc.isEnabled());
            if (optimization.equals("cost") && copy.getConstraintName().equals("maximizeRating")) {
                copy.setEnabled(false);
            } else if (optimization.equals("quality") && copy.getConstraintName().equals("wageOptimization")) {
                copy.setEnabled(false);
            }
            activeConstraints.add(copy);
        }

        // Average Wage Calculations
        Map<String, Double> sumPerRole = new HashMap<>();
        Map<String, Integer> countPerRole = new HashMap<>();
        for (EmployeeInfo emp : employeeInfoMap.values()) {
            sumPerRole.merge(emp.getPosition(), emp.getHourlyWage(), Double::sum);
            countPerRole.merge(emp.getPosition(), 1, Integer::sum);
        }
        Map<String, Double> averageWagePerRole = new HashMap<>();
        for (String role : sumPerRole.keySet()) {
            averageWagePerRole.put(role, sumPerRole.get(role) / countPerRole.get(role));
        }
        WageContext wageContext = new WageContext(averageWagePerRole);

        // Time Limit Formula (tuned for multi-day single-solver architecture)
        // The old day-by-day loop gave each day: (2 + employees/20) seconds.
        // We match that total budget so multi-day solves get equivalent thinking time.
        int employees = employeeInfoMap.size();
        int days = dateRange.size();
        long perDayBudget = 2L + ((long) employees / 20L);
        long defaultTimeLimit = Math.max(5L, perDayBudget * days);
        defaultTimeLimit = Math.min(defaultTimeLimit, 300L); // cap at 5 minutes
        long defaultUnimprovedLimit = Math.max(2L, defaultTimeLimit / 3L);

        long timeLimit = input.containsKey("time_limit_seconds") ? ((Number) input.get("time_limit_seconds")).longValue() : defaultTimeLimit;
        long unimprovedLimit = input.containsKey("unimproved_time_limit_seconds") ? ((Number) input.get("unimproved_time_limit_seconds")).longValue() : defaultUnimprovedLimit;

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("status", "success");
        responseData.put("message", "Assignments generated successfully");
        responseData.put("shift_name", targetShift);

        // Ensure we actually have employees to schedule
        if (employeeInfoMap.isEmpty()) {
            return Map.of("status", "error", "message", "No valid employees provided in existing_users");
        }

        int totalAssignedCount = 0;
        long totalSolverTimeMs = 0;

        try {
            ai.timefold.solver.core.config.solver.SolverConfig solverConfig = new ai.timefold.solver.core.config.solver.SolverConfig()
                    .withSolutionClass(ShiftSchedule.class)
                    .withEntityClasses(EmployeeAssignment.class)
                    .withConstraintProviderClass(ShiftConstraintProvider.class)
                    .withTerminationConfig(new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                            .withSpentLimit(java.time.Duration.ofSeconds(timeLimit))
                            .withUnimprovedSpentLimit(java.time.Duration.ofSeconds(unimprovedLimit)));

            ai.timefold.solver.core.api.solver.SolverFactory<ShiftSchedule> solverFactory = ai.timefold.solver.core.api.solver.SolverFactory.create(solverConfig);

            // 1. Fetch ALL existing assignments for the entire date range in ONE query
            Map<String, Set<String>> dbAssignmentsByDate = databaseService.loadAssignmentsForDateRange(
                startDate.toString(), endDate.toString());

            // 2. Convert DB assignments into ProblemFacts for the solver
            List<ExistingAssignment> existingFacts = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : dbAssignmentsByDate.entrySet()) {
                for (String empId : entry.getValue()) {
                    existingFacts.add(new ExistingAssignment(empId, entry.getKey()));
                }
            }

            // 3. Build ALL entities across ALL days — NO continue trick
            //    The noOverlappingShifts constraint handles preventing double-booking
            List<EmployeeAssignment> allEntities = new ArrayList<>();
            int assignmentIdSeq = 1;

            for (LocalDate currentDate : dateRange) {
                String dateStr = currentDate.toString();

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
                    entity.setActiveConfigs(activeConstraints);

                    entity.setShift(null); // Let Timefold assign
                    entity.setPinned(false);
                    allEntities.add(entity);
                }
            }

            if (allEntities.isEmpty()) {
                return Map.of(
                    "status", "error",
                    "error_code", 409,
                    "message", "No employees available for assignment."
                );
            }

            // 4. Single solver run for ALL days — pass existingFacts to the problem
            ShiftSchedule problem = new ShiftSchedule(allEntities, shiftTypes, roleRequirements, ratingRequirements, activeConstraints, wageContext, existingFacts);
            problem.setRequestedShiftName(targetShift);

            ai.timefold.solver.core.api.solver.Solver<ShiftSchedule> solver = solverFactory.buildSolver();

            long startMs = System.currentTimeMillis();
            ShiftSchedule solution = solver.solve(problem);
            totalSolverTimeMs = System.currentTimeMillis() - startMs;

            // 5. Process results — group by date (TreeMap guarantees chronological sorting)
            Map<String, List<Map<String, Object>>> resultsByDate = new TreeMap<>();

            for (EmployeeAssignment assignment : solution.getAssignments()) {
                if (targetShift.equals(assignment.getShift())) {
                    String dateStr = assignment.getDate();

                    // Save to database
                    databaseService.syncAssignment(
                        dateStr, targetShift, assignment.getEmployeeId(), assignment.getEmployeeName(),
                        assignment.getPosition(), assignment.getCategory(), assignment.getGender(),
                        assignment.getPerformanceRating(), startTimeStr, endTimeStr, assignment.getHourlyWage()
                    );

                    Map<String, Object> empData = new HashMap<>();
                    empData.put("id", assignment.getEmployeeId());
                    empData.put("name", assignment.getEmployeeName());
                    empData.put("assigned_shift", targetShift);
                    empData.put("role", assignment.getPosition());
                    empData.put("rating", assignment.getPerformanceRating());
                    empData.put("hourly_wage", assignment.getHourlyWage());
                    empData.put("gender", assignment.getGender());

                    resultsByDate.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(empData);
                    totalAssignedCount++;
                }
            }

            // 6. Build assignments_by_date response (SAME FORMAT as before)
            List<Map<String, Object>> assignmentsByDate = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : resultsByDate.entrySet()) {
                Map<String, Object> dayResult = new HashMap<>();
                dayResult.put("date", entry.getKey());
                dayResult.put("total_assigned", entry.getValue().size());
                dayResult.put("assigned_employees", entry.getValue());
                dayResult.put("score", solution.getScore() != null ? solution.getScore().toString() : "Unknown");
                assignmentsByDate.add(dayResult);
            }

            responseData.put("assignments_by_date", assignmentsByDate);
        } catch (Exception e) {
            LOG.error("Solving failed", e);
            return Map.of("status", "error", "message", "Solving failed: " + e.getMessage());
        }

        responseData.put("new_assignments_made", totalAssignedCount);
        responseData.put("solver_time_seconds", totalSolverTimeMs / 1000.0);
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
