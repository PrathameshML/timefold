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

    public Map<String, Object> solveGlobal(Map<String, Object> input) {
        LOG.debug("Starting solveGlobal (Multi-Shift Optimization)");
        long startTime = System.currentTimeMillis();

        if (input == null || !input.containsKey("shifts")) {
            return Map.of("status", "error", "message", "Missing 'shifts' array in request");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shiftsInput = (List<Map<String, Object>>) input.get("shifts");
        if (shiftsInput.isEmpty()) {
            return Map.of("status", "error", "message", "The 'shifts' array cannot be empty");
        }

        String optimization = (String) input.getOrDefault("optimization", "both");

        Set<java.time.LocalDate> allDates = new HashSet<>();
        List<String> allShiftTypes = new ArrayList<>();
        List<ShiftDefinition> shiftDefinitions = new ArrayList<>();
        List<ShiftRoleRequirement> shiftRoleRequirements = new ArrayList<>();
        List<RatingRequirement> ratingRequirements = new ArrayList<>();
        Map<String, EmployeeInfo> employeeInfoMap = new HashMap<>();
        Map<String, Map<java.time.LocalDate, Set<String>>> employeeDateEligibleShifts = new HashMap<>();
        List<EmployeeAvailability> employeeAvailabilities = new ArrayList<>();

        for (Map<String, Object> shiftInput : shiftsInput) {
            String shiftName = (String) shiftInput.get("shift_name");
            allShiftTypes.add(shiftName);
            String startTimeStr = (String) shiftInput.get("start_time");
            String endTimeStr = (String) shiftInput.get("end_time");
            shiftDefinitions.add(new ShiftDefinition(shiftName, startTimeStr, endTimeStr));

            java.time.LocalDate startDate = java.time.LocalDate.parse((String) shiftInput.get("start_date"));
            java.time.LocalDate endDate = java.time.LocalDate.parse((String) shiftInput.get("end_date"));
            List<java.time.LocalDate> shiftDates = startDate.datesUntil(endDate.plusDays(1)).collect(java.util.stream.Collectors.toList());
            allDates.addAll(shiftDates);

            // Roles -> ShiftRoleRequirement
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rolesInput = (List<Map<String, Object>>) shiftInput.get("roles");
            if (rolesInput != null) {
                for (java.time.LocalDate date : shiftDates) {
                    for (Map<String, Object> roleInput : rolesInput) {
                        String roleName = (String) roleInput.get("role_name");
                        int maxWorkers = parseNumber(roleInput.get("max_workers")).intValue();
                        shiftRoleRequirements.add(new ShiftRoleRequirement(date.toString(), shiftName, roleName, maxWorkers));
                    }
                }
                
                // Parse rating requirements once per role to avoid duplicates
                Set<String> processedRoles = new HashSet<>();
                for (Map<String, Object> roleInput : rolesInput) {
                    String roleName = (String) roleInput.get("role_name");
                    if (roleName != null && processedRoles.add(roleName)) {
                        if (roleInput.containsKey("rating")) {
                            Object ratingObj = roleInput.get("rating");
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
                            ratingRequirements.add(new RatingRequirement(roleName, List.of(3, 4, 5)));
                        }
                    }
                }
            }

            // Users
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> usersInput = (List<Map<String, Object>>) shiftInput.get("existing_users");
            if (usersInput != null) {
                for (Map<String, Object> userInput : usersInput) {
                    String empId = (String) userInput.get("employee_id");

                    if (!employeeInfoMap.containsKey(empId)) {
                        EmployeeInfo emp = new EmployeeInfo();
                        emp.setId(empId);
                        emp.setName((String) userInput.get("name"));
                        emp.setPosition((String) userInput.get("role"));
                        emp.setCategory((String) userInput.get("employeeType"));
                        emp.setGender((String) userInput.get("gender"));
                        
                        double hourlyWage = userInput.containsKey("rate") ? parseNumber(userInput.get("rate")).doubleValue() : 0.0;
                        String unit = (String) userInput.get("unit");
                        if ("day".equalsIgnoreCase(unit)) {
                            hourlyWage = hourlyWage / 8.0;
                        } else if ("month".equalsIgnoreCase(unit)) {
                            hourlyWage = hourlyWage / (22.0 * 8.0);
                        }
                        emp.setHourlyWage(hourlyWage);
                        
                        emp.setPerformanceRating(userInput.containsKey("rating") ? parseNumber(userInput.get("rating")).intValue() : 3);
                        employeeInfoMap.put(empId, emp);

                        // Parse availability
                        if (userInput.containsKey("availability")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Map<String, Object>> availMap = (Map<String, Map<String, Object>>) userInput.get("availability");
                            for (Map.Entry<String, Map<String, Object>> entry : availMap.entrySet()) {
                                String dateStr = entry.getKey();
                                Map<String, Object> availProps = entry.getValue();
                                String status = (String) availProps.get("status");
                                java.time.LocalTime from = availProps.containsKey("from") && availProps.get("from") != null ? java.time.LocalTime.parse((String) availProps.get("from")) : null;
                                java.time.LocalTime to = availProps.containsKey("to") && availProps.get("to") != null ? java.time.LocalTime.parse((String) availProps.get("to")) : null;
                                employeeAvailabilities.add(new EmployeeAvailability(empId, dateStr, status, from, to));
                            }
                        }
                    }

                    // Mark eligibility for this shift on these dates
                    employeeDateEligibleShifts.computeIfAbsent(empId, k -> new HashMap<>());
                    for (java.time.LocalDate date : shiftDates) {
                        employeeDateEligibleShifts.get(empId)
                                .computeIfAbsent(date, k -> new HashSet<>())
                                .add(shiftName);
                    }
                }
            }
        }

        if (allDates.isEmpty()) {
            return Map.of("status", "error", "message", "No valid date ranges provided in shifts");
        }

        List<java.time.LocalDate> sortedDates = new ArrayList<>(allDates);
        Collections.sort(sortedDates);
        String minDateStr = sortedDates.get(0).toString();
        String maxDateStr = sortedDates.get(sortedDates.size() - 1).toString();

        // Active Constraints dynamically
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

        // Time Limit Formula
        int employees = employeeInfoMap.size();
        int days = allDates.size();
        long perDayBudget = 2L + ((long) employees / 20L);
        long defaultTimeLimit = Math.max(5L, perDayBudget * days);
        defaultTimeLimit = Math.min(defaultTimeLimit, 300L); // cap at 5 minutes
        long defaultUnimprovedLimit = Math.max(2L, defaultTimeLimit / 3L);

        long timeLimit = input.containsKey("time_limit_seconds") ? parseNumber(input.get("time_limit_seconds")).longValue() : defaultTimeLimit;
        long unimprovedLimit = input.containsKey("unimproved_time_limit_seconds") ? parseNumber(input.get("unimproved_time_limit_seconds")).longValue() : defaultUnimprovedLimit;

        try {
            ai.timefold.solver.core.config.solver.SolverConfig solverConfig = new ai.timefold.solver.core.config.solver.SolverConfig()
                    .withSolutionClass(ShiftSchedule.class)
                    .withEntityClasses(EmployeeAssignment.class)
                    .withConstraintProviderClass(ShiftConstraintProvider.class)
                    .withTerminationConfig(new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                            .withSpentLimit(java.time.Duration.ofSeconds(timeLimit))
                            .withUnimprovedSpentLimit(java.time.Duration.ofSeconds(unimprovedLimit)));

            ai.timefold.solver.core.api.solver.SolverFactory<ShiftSchedule> solverFactory = ai.timefold.solver.core.api.solver.SolverFactory.create(solverConfig);

            // Fetch ALL existing assignments for the entire date range
            Map<String, Set<String>> dbAssignmentsByDate = databaseService.loadAssignmentsForDateRange(minDateStr, maxDateStr);
            List<ExistingAssignment> existingFacts = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : dbAssignmentsByDate.entrySet()) {
                for (String empId : entry.getValue()) {
                    existingFacts.add(new ExistingAssignment(empId, entry.getKey()));
                }
            }

            // Build ALL entities across ALL days
            List<EmployeeAssignment> allEntities = new ArrayList<>();
            int assignmentIdSeq = 1;

            for (java.time.LocalDate currentDate : sortedDates) {
                String dateStr = currentDate.toString();
                for (EmployeeInfo emp : employeeInfoMap.values()) {
                    Set<String> eligibleShiftsForDate = employeeDateEligibleShifts.getOrDefault(emp.getId(), Collections.emptyMap()).get(currentDate);
                    if (eligibleShiftsForDate == null || eligibleShiftsForDate.isEmpty()) continue;

                    EmployeeAssignment entity = new EmployeeAssignment(
                            dateStr + "_" + emp.getId() + "_" + assignmentIdSeq++,
                            emp.getId(), emp.getName(), dateStr,
                            emp.getCategory(), emp.getGender(), emp.getDepartment(), emp.getPosition()
                    );
                    entity.setHourlyWage(emp.getHourlyWage());
                    entity.setPerformanceRating(emp.getPerformanceRating());
                    entity.setLocalDateObj(currentDate);
                    entity.setActiveConfigs(activeConstraints);
                    entity.setEligibleShifts(new ArrayList<>(eligibleShiftsForDate));
                    entity.setShift(null); // Let Timefold assign
                    entity.setPinned(false);
                    allEntities.add(entity);
                }
            }

            if (allEntities.isEmpty()) {
                return Map.of("status", "error", "message", "No valid employee assignments could be generated. Check date ranges and existing users.");
            }

            ShiftSchedule problem = new ShiftSchedule(
                    allEntities,
                    allShiftTypes,
                    new ArrayList<>(), // roleRequirements (unused in global)
                    ratingRequirements,
                    activeConstraints,
                    wageContext,
                    existingFacts,
                    shiftRoleRequirements,
                    shiftDefinitions,
                    employeeAvailabilities
            );

            ai.timefold.solver.core.api.solver.Solver<ShiftSchedule> solver = solverFactory.buildSolver();
            LOG.info(String.format("Starting global solver... Entities: %d, Dates: %d, TimeLimit: %ds", allEntities.size(), days, timeLimit));

            ShiftSchedule solution = solver.solve(problem);
            long solverTime = System.currentTimeMillis() - startTime;
            LOG.info("Global Solver finished in " + solverTime + "ms. Score: " + solution.getScore());

            // Build response
            int totalAssignedCount = 0;
            Map<String, List<Map<String, Object>>> assignmentsByDate = new TreeMap<>();
            for (EmployeeAssignment assignment : solution.getAssignments()) {
                if (assignment.getShift() != null) {
                    Map<String, Object> details = new HashMap<>();
                    details.put("employeeId", assignment.getEmployeeId());
                    details.put("employeeName", assignment.getEmployeeName());
                    details.put("role", assignment.getPosition());
                    details.put("employeeType", assignment.getCategory());
                    details.put("gender", assignment.getGender());
                    details.put("wage", assignment.getHourlyWage());
                    details.put("rating", assignment.getPerformanceRating());
                    details.put("shift_name", assignment.getShift());
                    
                    assignmentsByDate.computeIfAbsent(assignment.getDate(), k -> new ArrayList<>()).add(details);
                    
                    // Save to DB (mock in test, real in prod)
                    ShiftDefinition assignedShiftDef = null;
                    for (ShiftDefinition sd : shiftDefinitions) {
                        if (sd.getShiftName().equals(assignment.getShift())) {
                            assignedShiftDef = sd;
                            break;
                        }
                    }
                    String st = assignedShiftDef != null ? assignedShiftDef.getStartTime() : "00:00";
                    String et = assignedShiftDef != null ? assignedShiftDef.getEndTime() : "00:00";
                    
                    databaseService.syncAssignment(
                            assignment.getDate(),
                            assignment.getShift(),
                            assignment.getEmployeeId(),
                            assignment.getEmployeeName(),
                            assignment.getPosition(),
                            assignment.getCategory(),
                            assignment.getGender(),
                            assignment.getPerformanceRating(),
                            st, et,
                            assignment.getHourlyWage()
                    );
                    totalAssignedCount++;
                }
            }

            List<Map<String, Object>> dailySummary = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : assignmentsByDate.entrySet()) {
                Map<String, Object> daily = new HashMap<>();
                daily.put("date", entry.getKey());
                daily.put("assignments", entry.getValue());
                daily.put("count", entry.getValue().size());
                
                Map<String, Integer> roleCounts = new HashMap<>();
                for (Map<String, Object> a : entry.getValue()) {
                    String role = (String) a.get("role");
                    roleCounts.put(role, roleCounts.getOrDefault(role, 0) + 1);
                }
                daily.put("role_counts", roleCounts);
                dailySummary.add(daily);
            }

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("status", "success");
            responseData.put("period", minDateStr + " to " + maxDateStr);
            responseData.put("total_working_days", days);
            responseData.put("new_assignments_made", totalAssignedCount);
            responseData.put("skipped_count", 0); // Not skipping during solve, noOverlappingShifts handles it
            responseData.put("solver_time_seconds", solverTime / 1000.0);
            responseData.put("daily_summary", dailySummary);
            
            if (solution.getScore() != null) {
                responseData.put("score", solution.getScore().toString());
                responseData.put("is_feasible", solution.getScore().isFeasible());
            }

            return responseData;
        } catch (Exception e) {
            LOG.error("Global solver failed", e);
            return Map.of("status", "error", "message", e.getMessage());
        }
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
            
            double hourlyWage = u.containsKey("rate") ? parseNumber(u.get("rate")).doubleValue() : 0.0;
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
        responseData.put("shift_name", targetShift);
        responseData.put("period", startDateStr + " to " + endDateStr);
        responseData.put("shift_time", startTimeStr + " - " + endTimeStr);
        responseData.put("total_working_days", dateRange.size());

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
                    entity.setEligibleShifts(shiftTypes); // Per-entity VRP: provide value range

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
                    empData.put("employeeId", assignment.getEmployeeId());
                    empData.put("employeeName", assignment.getEmployeeName());
                    empData.put("role", assignment.getPosition());
                    empData.put("rating", assignment.getPerformanceRating());
                    empData.put("wage", assignment.getHourlyWage());
                    empData.put("gender", assignment.getGender());
                    empData.put("employeeType", assignment.getCategory());

                    resultsByDate.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(empData);
                    totalAssignedCount++;
                }
            }

            responseData.put("entities_planned", allEntities.size());
            responseData.put("total_possible_assignments", allEntities.size());
            int skippedCount = allEntities.size() - totalAssignedCount;
            responseData.put("skipped_count", skippedCount);
            responseData.put("solver_score", solution.getScore() != null ? solution.getScore().toString() : "Unknown");
            responseData.put("score", solution.getScore() != null ? solution.getScore().toString() : "Unknown");
            responseData.put("is_feasible", solution.getScore() != null && solution.getScore().isFeasible());

            // Role Statistics & Assignments by Employee Type
            Map<String, Map<String, Object>> roleStatistics = new HashMap<>();
            Map<String, Long> assignmentsByEmployeeType = new HashMap<>();

            // 6. Build daily_summary response
            List<Map<String, Object>> assignmentsByDate = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : resultsByDate.entrySet()) {
                Map<String, Object> dayResult = new HashMap<>();
                dayResult.put("date", entry.getKey());
                dayResult.put("count", entry.getValue().size());
                dayResult.put("assignments", entry.getValue());
                
                Map<String, Integer> roleCounts = new HashMap<>();
                for (Map<String, Object> empData : entry.getValue()) {
                    String role = (String) empData.get("role");
                    roleCounts.put(role, roleCounts.getOrDefault(role, 0) + 1);

                    String empType = (String) empData.get("employeeType");
                    assignmentsByEmployeeType.put(empType, assignmentsByEmployeeType.getOrDefault(empType, 0L) + 1L);
                }
                dayResult.put("role_counts", roleCounts);
                
                assignmentsByDate.add(dayResult);
            }

            // Compute role statistics
            for (RoleRequirement req : roleRequirements) {
                String role = req.getRoleName();
                int count = 0;
                double totalWage = 0.0;
                for (List<Map<String, Object>> dayAssignments : resultsByDate.values()) {
                    for (Map<String, Object> empData : dayAssignments) {
                        if (role.equals(empData.get("role"))) {
                            count++;
                            Number wageNum = (Number) empData.get("wage");
                            if (wageNum != null) {
                                totalWage += wageNum.doubleValue();
                            }
                        }
                    }
                }
                Map<String, Object> stats = new HashMap<>();
                stats.put("assignments", count);
                stats.put("average_wage", count > 0 ? String.format(java.util.Locale.US, "%.2f", totalWage / count) : "0.00");
                stats.put("max_per_day", req.getMaxWorkers());
                roleStatistics.put(role, stats);
            }

            responseData.put("role_statistics", roleStatistics);
            responseData.put("assignments_by_employee_type", assignmentsByEmployeeType);
            responseData.put("daily_summary", assignmentsByDate);

            // 7. Constraint Violations
            List<String> constraintViolations = new ArrayList<>();
            if (solution.getScore() != null && !solution.getScore().isFeasible()) {
                ai.timefold.solver.core.api.solver.SolutionManager<ShiftSchedule, ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> solutionManager = ai.timefold.solver.core.api.solver.SolutionManager.create(solverFactory);
                ai.timefold.solver.core.api.score.analysis.ScoreAnalysis<ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> scoreAnalysis = solutionManager.analyze(solution);
                
                for (ai.timefold.solver.core.api.score.analysis.ConstraintAnalysis<ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> ca : scoreAnalysis.constraintMap().values()) {
                    if (ca.score().hardScore() < 0) {
                        constraintViolations.add("Violated Constraint: " + ca.constraintRef().constraintName() +
                                " (Score impact: " + ca.score().hardScore() + ")");
                    }
                }
            }
            responseData.put("constraint_violations", constraintViolations);

            if (!constraintViolations.isEmpty()) {
                responseData.put("message", "Schedule solved but with HARD constraint violations!");
            } else {
                StringBuilder message = new StringBuilder();
                message.append("Successfully assigned shifts for ").append(dateRange.size()).append(" days. ");
                message.append("Total assignments: ").append(totalAssignedCount).append(". ");
                if (skippedCount > 0) {
                    message.append("Skipped ").append(skippedCount).append(" assignments.");
                }
                responseData.put("message", message.toString());
            }
        } catch (Exception e) {
            LOG.error("Solving failed", e);
            return Map.of("status", "error", "message", "Solving failed: " + e.getMessage());
        }

        responseData.put("new_assignments_made", totalAssignedCount);
        responseData.put("solver_time_seconds", totalSolverTimeMs / 1000.0);
        return responseData;
    }

    // ========================================================================================
    // V2 API — Availability-aware shift assignment
    // This is a separate method from solveShift() to avoid touching the stable V1 API.
    // Differences from V1:
    //   1. Parses optional "availability" field per employee
    //   2. Skips entity creation for unavailable/partially-unfit employee-date combinations
    //   3. Includes "availability_exclusions" in response for transparency
    // ========================================================================================

    @SuppressWarnings("unchecked")
    public Map<String, Object> solveShiftV2(Map<String, Object> input) {
        List<String> missingFields = validateShiftInput(input);
        if (!missingFields.isEmpty()) {
            return Map.of("status", "error", "message", "Missing required fields: " + String.join(", ", missingFields));
        }

        String targetShift = (String) input.get("shift_name");
        String startDateStr = (String) input.get("start_date");
        String endDateStr = (String) input.get("end_date");
        String startTimeStr = (String) input.get("start_time");
        String endTimeStr = (String) input.get("end_time");

        // Parse shift times for availability checking
        java.time.LocalTime shiftStartTime = java.time.LocalTime.parse(startTimeStr);
        java.time.LocalTime shiftEndTime = java.time.LocalTime.parse(endTimeStr);

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
                ratingRequirements.add(new RatingRequirement(roleName, List.of(3, 4, 5)));
            }
        }

        // Generate dates
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        List<LocalDate> dateRange = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            dateRange.add(d);
        }

        // Prepare existing users — V2: also parse availability
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

            // V2: Parse availability overrides
            if (u.containsKey("availability")) {
                Object availObj = u.get("availability");
                if (availObj instanceof List) {
                    Map<LocalDate, AvailabilityEntry> availMap = new HashMap<>();
                    List<Map<String, Object>> availList = (List<Map<String, Object>>) availObj;
                    for (Map<String, Object> entry : availList) {
                        String dateStr = (String) entry.get("date");
                        String status = (String) entry.get("status");
                        if (dateStr == null || status == null) continue;

                        java.time.LocalTime fromTime = null;
                        java.time.LocalTime toTime = null;

                        if ("partial".equalsIgnoreCase(status)) {
                            String fromStr = (String) entry.get("from");
                            String toStr = (String) entry.get("to");
                            if (fromStr != null) {
                                fromTime = java.time.LocalTime.parse(fromStr);
                            }
                            if (toStr != null) {
                                toTime = java.time.LocalTime.parse(toStr);
                            } else if (fromStr != null) {
                                // "from" provided but "to" missing → default to end of day
                                toTime = java.time.LocalTime.of(23, 59);
                            }
                            // If neither from nor to → AvailabilityEntry.coversShift() returns false (treated as unavailable)
                        }

                        availMap.put(LocalDate.parse(dateStr), new AvailabilityEntry(status, fromTime, toTime));
                    }
                    emp.setAvailabilityMap(availMap);
                }
            }

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
        int employees = employeeInfoMap.size();
        int days = dateRange.size();
        long perDayBudget = 2L + ((long) employees / 20L);
        long defaultTimeLimit = Math.max(5L, perDayBudget * days);
        defaultTimeLimit = Math.min(defaultTimeLimit, 300L);
        long defaultUnimprovedLimit = Math.max(2L, defaultTimeLimit / 3L);

        long timeLimit = input.containsKey("time_limit_seconds") ? ((Number) input.get("time_limit_seconds")).longValue() : defaultTimeLimit;
        long unimprovedLimit = input.containsKey("unimproved_time_limit_seconds") ? ((Number) input.get("unimproved_time_limit_seconds")).longValue() : defaultUnimprovedLimit;

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("status", "success");
        responseData.put("shift_name", targetShift);
        responseData.put("period", startDateStr + " to " + endDateStr);
        responseData.put("shift_time", startTimeStr + " - " + endTimeStr);
        responseData.put("total_working_days", dateRange.size());

        if (employeeInfoMap.isEmpty()) {
            return Map.of("status", "error", "message", "No valid employees provided in existing_users");
        }

        int totalAssignedCount = 0;
        long totalSolverTimeMs = 0;

        // V2: Track availability exclusions for response transparency
        List<Map<String, Object>> availabilityExclusions = new ArrayList<>();
        Map<String, List<Map<String, Object>>> exclusionsByEmployee = new HashMap<>();

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

            // 3. Build entities — V2: skip unavailable/partially-unfit employee-date combinations
            List<EmployeeAssignment> allEntities = new ArrayList<>();
            int assignmentIdSeq = 1;
            int availabilitySkippedCount = 0;

            for (LocalDate currentDate : dateRange) {
                String dateStr = currentDate.toString();

                for (EmployeeInfo emp : employeeInfoMap.values()) {
                    // V2: Check availability before creating entity
                    if (!emp.isAvailableForShift(currentDate, shiftStartTime, shiftEndTime)) {
                        availabilitySkippedCount++;

                        // Track the exclusion for response
                        AvailabilityEntry avEntry = emp.getAvailabilityMap().get(currentDate);
                        String reason;
                        if (avEntry != null && "partial".equalsIgnoreCase(avEntry.getStatus())) {
                            String window = (avEntry.getFrom() != null ? avEntry.getFrom().toString() : "?") 
                                           + "-" + (avEntry.getTo() != null ? avEntry.getTo().toString() : "?");
                            reason = "partial availability (" + window + ") does not cover shift (" + startTimeStr + "-" + endTimeStr + ")";
                        } else {
                            reason = "unavailable";
                        }
                        exclusionsByEmployee.computeIfAbsent(emp.getId(), k -> new ArrayList<>())
                                .add(Map.of("date", dateStr, "reason", reason));

                        continue; // Skip entity creation
                    }

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
                    entity.setEligibleShifts(shiftTypes); // Per-entity VRP: provide value range

                    entity.setShift(null);
                    entity.setPinned(false);
                    allEntities.add(entity);
                }
            }

            if (allEntities.isEmpty()) {
                // Build exclusions for the error response so caller knows why
                buildExclusionsList(exclusionsByEmployee, employeeInfoMap, dateRange, availabilityExclusions);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("error_code", 409);
                errorResponse.put("message", "No employees available for assignment. " + availabilitySkippedCount + " employee-date combinations were excluded by availability rules.");
                errorResponse.put("availability_exclusions", availabilityExclusions);
                return errorResponse;
            }

            // 4. Single solver run for ALL days
            ShiftSchedule problem = new ShiftSchedule(allEntities, shiftTypes, roleRequirements, ratingRequirements, activeConstraints, wageContext, existingFacts);
            problem.setRequestedShiftName(targetShift);

            ai.timefold.solver.core.api.solver.Solver<ShiftSchedule> solver = solverFactory.buildSolver();

            long startMs = System.currentTimeMillis();
            ShiftSchedule solution = solver.solve(problem);
            totalSolverTimeMs = System.currentTimeMillis() - startMs;

            // 5. Process results — group by date
            Map<String, List<Map<String, Object>>> resultsByDate = new TreeMap<>();

            for (EmployeeAssignment assignment : solution.getAssignments()) {
                if (targetShift.equals(assignment.getShift())) {
                    String dateStr = assignment.getDate();

                    databaseService.syncAssignment(
                        dateStr, targetShift, assignment.getEmployeeId(), assignment.getEmployeeName(),
                        assignment.getPosition(), assignment.getCategory(), assignment.getGender(),
                        assignment.getPerformanceRating(), startTimeStr, endTimeStr, assignment.getHourlyWage()
                    );

                    Map<String, Object> empData = new HashMap<>();
                    empData.put("employeeId", assignment.getEmployeeId());
                    empData.put("employeeName", assignment.getEmployeeName());
                    empData.put("role", assignment.getPosition());
                    empData.put("rating", assignment.getPerformanceRating());
                    empData.put("wage", assignment.getHourlyWage());
                    empData.put("gender", assignment.getGender());
                    empData.put("employeeType", assignment.getCategory());

                    resultsByDate.computeIfAbsent(dateStr, k -> new ArrayList<>()).add(empData);
                    totalAssignedCount++;
                }
            }

            responseData.put("entities_planned", allEntities.size());
            responseData.put("total_possible_assignments", allEntities.size());
            int skippedCount = allEntities.size() - totalAssignedCount;
            responseData.put("skipped_count", skippedCount);
            responseData.put("solver_score", solution.getScore() != null ? solution.getScore().toString() : "Unknown");

            // Role Statistics & Assignments by Employee Type
            Map<String, Map<String, Object>> roleStatistics = new HashMap<>();
            Map<String, Long> assignmentsByEmployeeType = new HashMap<>();

            // 6. Build daily_summary response
            List<Map<String, Object>> assignmentsByDate = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : resultsByDate.entrySet()) {
                Map<String, Object> dayResult = new HashMap<>();
                dayResult.put("date", entry.getKey());
                dayResult.put("count", entry.getValue().size());
                dayResult.put("assignments", entry.getValue());
                
                Map<String, Integer> roleCounts = new HashMap<>();
                for (Map<String, Object> empData : entry.getValue()) {
                    String role = (String) empData.get("role");
                    roleCounts.put(role, roleCounts.getOrDefault(role, 0) + 1);

                    String empType = (String) empData.get("employeeType");
                    assignmentsByEmployeeType.put(empType, assignmentsByEmployeeType.getOrDefault(empType, 0L) + 1L);
                }
                dayResult.put("role_counts", roleCounts);
                
                assignmentsByDate.add(dayResult);
            }

            // Compute role statistics
            for (RoleRequirement req : roleRequirements) {
                String role = req.getRoleName();
                int count = 0;
                double totalWage = 0.0;
                for (List<Map<String, Object>> dayAssignments : resultsByDate.values()) {
                    for (Map<String, Object> empData : dayAssignments) {
                        if (role.equals(empData.get("role"))) {
                            count++;
                            Number wageNum = (Number) empData.get("wage");
                            if (wageNum != null) {
                                totalWage += wageNum.doubleValue();
                            }
                        }
                    }
                }
                Map<String, Object> stats = new HashMap<>();
                stats.put("assignments", count);
                stats.put("average_wage", count > 0 ? String.format(java.util.Locale.US, "%.2f", totalWage / count) : "0.00");
                stats.put("max_per_day", req.getMaxWorkers());
                roleStatistics.put(role, stats);
            }

            responseData.put("role_statistics", roleStatistics);
            responseData.put("assignments_by_employee_type", assignmentsByEmployeeType);
            responseData.put("daily_summary", assignmentsByDate);

            // 7. Constraint Violations
            List<String> constraintViolations = new ArrayList<>();
            if (solution.getScore() != null && !solution.getScore().isFeasible()) {
                ai.timefold.solver.core.api.solver.SolutionManager<ShiftSchedule, ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> solutionManager = ai.timefold.solver.core.api.solver.SolutionManager.create(solverFactory);
                ai.timefold.solver.core.api.score.analysis.ScoreAnalysis<ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> scoreAnalysis = solutionManager.analyze(solution);
                
                for (ai.timefold.solver.core.api.score.analysis.ConstraintAnalysis<ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> ca : scoreAnalysis.constraintMap().values()) {
                    if (ca.score().hardScore() < 0) {
                        constraintViolations.add("Violated Constraint: " + ca.constraintRef().constraintName() +
                                " (Score impact: " + ca.score().hardScore() + ")");
                    }
                }
            }
            responseData.put("constraint_violations", constraintViolations);

            if (!constraintViolations.isEmpty()) {
                responseData.put("message", "Schedule solved but with HARD constraint violations!");
            } else {
                StringBuilder message = new StringBuilder();
                message.append("Successfully assigned shifts for ").append(dateRange.size()).append(" days. ");
                message.append("Total assignments: ").append(totalAssignedCount).append(". ");
                if (skippedCount > 0) {
                    message.append("Skipped ").append(skippedCount).append(" assignments.");
                }
                responseData.put("message", message.toString());
            }
        } catch (Exception e) {
            LOG.error("Solving failed (V2)", e);
            return Map.of("status", "error", "message", "Solving failed: " + e.getMessage());
        }

        responseData.put("new_assignments_made", totalAssignedCount);
        responseData.put("solver_time_seconds", totalSolverTimeMs / 1000.0);

        // V2: Add availability exclusions to response
        buildExclusionsList(exclusionsByEmployee, employeeInfoMap, dateRange, availabilityExclusions);
        if (!availabilityExclusions.isEmpty()) {
            responseData.put("availability_exclusions", availabilityExclusions);
        }

        return responseData;
    }

    /**
     * V2 helper: Builds the availability_exclusions response list from tracked exclusion data.
     */
    private void buildExclusionsList(Map<String, List<Map<String, Object>>> exclusionsByEmployee,
                                      Map<String, EmployeeInfo> employeeInfoMap,
                                      List<LocalDate> dateRange,
                                      List<Map<String, Object>> result) {
        for (Map.Entry<String, List<Map<String, Object>>> entry : exclusionsByEmployee.entrySet()) {
            String empId = entry.getKey();
            EmployeeInfo emp = employeeInfoMap.get(empId);
            
            // Compute available dates (dates NOT excluded)
            Set<String> excludedDateSet = new HashSet<>();
            for (Map<String, Object> excl : entry.getValue()) {
                excludedDateSet.add((String) excl.get("date"));
            }
            List<String> availableDates = new ArrayList<>();
            for (LocalDate d : dateRange) {
                if (!excludedDateSet.contains(d.toString())) {
                    availableDates.add(d.toString());
                }
            }

            Map<String, Object> exclusionEntry = new HashMap<>();
            exclusionEntry.put("employee_id", empId);
            exclusionEntry.put("employee_name", emp != null ? emp.getName() : "Unknown");
            exclusionEntry.put("excluded_dates", entry.getValue());
            exclusionEntry.put("available_dates", availableDates);
            result.add(exclusionEntry);
        }
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
