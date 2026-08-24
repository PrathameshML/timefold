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


    // ========================================================================================
    // Global-V2 API â€” Slot-based global optimization
    // Uses ShiftSlot entities (one per required slot) instead of EmployeeAssignment
    // (one per employee-day). This dramatically reduces the search space and produces
    // better solutions faster.
    //
    // Same request/response format as solveGlobal() â€” internal model change only.
    // ========================================================================================

    @SuppressWarnings("unchecked")
    public Map<String, Object> solveGlobalV2(Map<String, Object> input) {
        LOG.info("Starting solveGlobalV2 (Slot-Based Global Optimization)");
        long startTime = System.currentTimeMillis();

        if (input == null || !input.containsKey("shifts")) {
            return Map.of("status", "error", "message", "Missing 'shifts' array in request");
        }

        List<Map<String, Object>> shiftsInput = (List<Map<String, Object>>) input.get("shifts");
        if (shiftsInput.isEmpty()) {
            return Map.of("status", "error", "message", "The 'shifts' array cannot be empty");
        }

        String optimization = (String) input.getOrDefault("optimization", "both");

        Set<java.time.LocalDate> allDates = new HashSet<>();
        Set<ShiftDefinition> shiftDefinitions = new LinkedHashSet<>();
        List<ShiftRoleRequirement> shiftRoleRequirements = new ArrayList<>();
        Set<RatingRequirement> ratingRequirements = new LinkedHashSet<>();
        Map<String, EmployeeInfo> employeeInfoMap = new HashMap<>();
        Set<EmployeeAvailability> employeeAvailabilities = new LinkedHashSet<>();

        // ---- Parse input (identical to solveGlobal) ----
        for (Map<String, Object> shiftInput : shiftsInput) {
            String shiftName = (String) shiftInput.get("shift_name");
            String startTimeStr = (String) shiftInput.get("start_time");
            String endTimeStr = (String) shiftInput.get("end_time");
            shiftDefinitions.add(new ShiftDefinition(shiftName, startTimeStr, endTimeStr));

            java.time.LocalDate startDate = java.time.LocalDate.parse((String) shiftInput.get("start_date"));
            java.time.LocalDate endDate = java.time.LocalDate.parse((String) shiftInput.get("end_date"));
            List<java.time.LocalDate> shiftDates = startDate.datesUntil(endDate.plusDays(1)).collect(java.util.stream.Collectors.toList());
            allDates.addAll(shiftDates);

            // Roles -> ShiftRoleRequirement
            List<Map<String, Object>> rolesInput = (List<Map<String, Object>>) shiftInput.get("roles");
            if (rolesInput != null) {
                for (java.time.LocalDate date : shiftDates) {
                    for (Map<String, Object> roleInput : rolesInput) {
                        String roleName = (String) roleInput.get("role_name");
                        int maxWorkers = parseNumber(roleInput.get("max_workers")).intValue();
                        shiftRoleRequirements.add(new ShiftRoleRequirement(date.toString(), shiftName, roleName, maxWorkers));
                    }
                }

                // Parse rating requirements once per role
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
                            ratingRequirements.add(new RatingRequirement(shiftName, roleName, allowedRatings));
                        } else {
                            ratingRequirements.add(new RatingRequirement(shiftName, roleName, List.of(3, 4, 5)));
                        }
                    }
                }
            }

            // Users
            List<Map<String, Object>> usersInput = (List<Map<String, Object>>) shiftInput.get("existing_users");
            if (usersInput != null) {
                for (Map<String, Object> userInput : usersInput) {
                    String empId = (String) userInput.get("employee_id");
                    String role = (String) userInput.get("role");
                    if (!employeeInfoMap.containsKey(empId)) {
                        EmployeeInfo emp = new EmployeeInfo();
                        emp.setId(empId);
                        emp.setName((String) userInput.get("name"));
                        emp.setPosition(role);
                        emp.addRole(role);
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

                        // Parse availability (same array format as batch-assign)
                        if (userInput.containsKey("availability")) {
                            Object availObj = userInput.get("availability");
                            if (availObj instanceof List) {
                                List<Map<String, Object>> availList = (List<Map<String, Object>>) availObj;
                                for (Map<String, Object> entry : availList) {
                                    String dateStr = (String) entry.get("date");
                                    String status = (String) entry.get("status");
                                    if (dateStr == null || status == null) continue;
                                    java.time.LocalTime from = null;
                                    java.time.LocalTime to = null;
                                    if ("partial".equalsIgnoreCase(status)) {
                                        String fromStr = (String) entry.get("from");
                                        String toStr = (String) entry.get("to");
                                        if (fromStr != null) from = java.time.LocalTime.parse(fromStr);
                                        if (toStr != null) {
                                            to = java.time.LocalTime.parse(toStr);
                                        } else if (fromStr != null) {
                                            to = java.time.LocalTime.of(23, 59);
                                        }
                                    }
                                    employeeAvailabilities.add(new EmployeeAvailability(empId, dateStr, status, from, to));
                                }
                            }
                        }
                    } else {
                        employeeInfoMap.get(empId).addRole(role);
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
        int days = allDates.size();

        // ---- Active constraints ----
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

        // ---- Average wage ----
        Map<String, Double> sumPerRole = new HashMap<>();
        Map<String, Integer> countPerRole = new HashMap<>();
        for (EmployeeInfo emp : employeeInfoMap.values()) {
            for (String role : emp.getRoles()) {
                sumPerRole.merge(role, emp.getHourlyWage(), Double::sum);
                countPerRole.merge(role, 1, Integer::sum);
            }
        }
        Map<String, Double> averageWagePerRole = new HashMap<>();
        for (String role : sumPerRole.keySet()) {
            averageWagePerRole.put(role, sumPerRole.get(role) / countPerRole.get(role));
        }
        WageContext wageContext = new WageContext(averageWagePerRole);

        // ---- Time limit (based on SLOT count, not employeeÃ—day) ----
        int totalSlotCount = shiftRoleRequirements.stream().mapToInt(ShiftRoleRequirement::getMaxWorkers).sum();
        long defaultTimeLimit = Math.min(90L, Math.max(5L, Math.round(totalSlotCount * 0.02)));
        long defaultUnimprovedLimit = Math.min(30L, Math.max(5L, Math.round(defaultTimeLimit / 3.0)));

        long timeLimit = input.containsKey("time_limit_seconds") ? parseNumber(input.get("time_limit_seconds")).longValue() : defaultTimeLimit;
        long unimprovedLimit = input.containsKey("unimproved_time_limit_seconds") ? parseNumber(input.get("unimproved_time_limit_seconds")).longValue() : defaultUnimprovedLimit;

        try {
            // ---- Solver config (uses SlotConstraintProvider + ShiftScheduleSlot) ----
            ai.timefold.solver.core.config.solver.SolverConfig solverConfig = new ai.timefold.solver.core.config.solver.SolverConfig()
                    .withSolutionClass(ShiftScheduleSlot.class)
                    .withEntityClasses(ShiftSlot.class)
                    .withConstraintProviderClass(com.scheduler.solver.SlotConstraintProvider.class)
                    .withTerminationConfig(new ai.timefold.solver.core.config.solver.termination.TerminationConfig()
                            .withSpentLimit(java.time.Duration.ofSeconds(timeLimit))
                            .withUnimprovedSpentLimit(java.time.Duration.ofSeconds(unimprovedLimit)));

            ai.timefold.solver.core.api.solver.SolverFactory<ShiftScheduleSlot> solverFactory = ai.timefold.solver.core.api.solver.SolverFactory.create(solverConfig);

            // ---- Existing assignments from DB ----
            Map<String, Set<String>> dbAssignmentsByDate = databaseService.loadAssignmentsForDateRange(minDateStr, maxDateStr);
            List<ExistingAssignment> existingFacts = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : dbAssignmentsByDate.entrySet()) {
                for (String empId : entry.getValue()) {
                    existingFacts.add(new ExistingAssignment(empId, entry.getKey()));
                }
            }

            // ---- Pre-compute eligible employee IDs per role ----
            Map<String, List<String>> eligibleByRole = new HashMap<>();
            for (EmployeeInfo emp : employeeInfoMap.values()) {
                for (String role : emp.getRoles()) {
                    eligibleByRole.computeIfAbsent(role, k -> new ArrayList<>()).add(emp.getId());
                }
            }

            // ---- Create ShiftSlot entities from requirements ----
            List<ShiftSlot> allSlots = new ArrayList<>();
            for (ShiftRoleRequirement req : shiftRoleRequirements) {
                List<String> eligibleForRole = eligibleByRole.getOrDefault(req.getRoleName(), Collections.emptyList());
                for (int i = 1; i <= req.getMaxWorkers(); i++) {
                    ShiftSlot slot = new ShiftSlot(
                            req.getDate() + "_" + req.getShiftName() + "_" + req.getRoleName() + "_" + i,
                            req.getDate(), req.getShiftName(), req.getRoleName(), i
                    );
                    slot.setEligibleEmployeeIds(eligibleForRole);
                    allSlots.add(slot);
                }
            }

            if (allSlots.isEmpty()) {
                return Map.of("status", "error", "message", "No slots to fill. Check roles and date ranges.");
            }

            // ---- Create EmployeeFact problem facts ----
            List<EmployeeFact> employeeFacts = new ArrayList<>();
            for (EmployeeInfo emp : employeeInfoMap.values()) {
                employeeFacts.add(new EmployeeFact(
                        emp.getId(), emp.getName(), emp.getPosition(),
                        emp.getHourlyWage(), emp.getPerformanceRating(),
                        emp.getCategory(), emp.getGender()
                ));
            }

            // ---- Build problem ----
            ShiftScheduleSlot problem = new ShiftScheduleSlot(
                    allSlots,
                    employeeFacts,
                    activeConstraints,
                    existingFacts,
                    new ArrayList<>(ratingRequirements),
                    new ArrayList<>(shiftDefinitions),
                    new ArrayList<>(employeeAvailabilities),
                    wageContext
            );

            ai.timefold.solver.core.api.solver.Solver<ShiftScheduleSlot> solver = solverFactory.buildSolver();
            LOG.info(String.format("Starting Global-V2 solver... Slots: %d, Employees: %d, Dates: %d, TimeLimit: %ds, UnimprovedLimit: %ds",
                    allSlots.size(), employeeInfoMap.size(), days, timeLimit, unimprovedLimit));

            // ---- Solve ----
            ShiftScheduleSlot solution = solver.solve(problem);
            long solverTime = System.currentTimeMillis() - startTime;
            LOG.info("Global-V2 Solver finished in " + solverTime + "ms. Score: " + solution.getScore());

            // ---- Build response (batch-assign compatible format) ----

            // 1. Group solved slots: shiftName → date → assignments, and sync to DB
            Map<String, Map<String, List<Map<String, Object>>>> slotsByShift = new LinkedHashMap<>();

            for (ShiftSlot slot : solution.getSlots()) {
                if (slot.getEmployeeId() != null) {
                    EmployeeInfo emp = employeeInfoMap.get(slot.getEmployeeId());
                    if (emp == null) continue;

                    Map<String, Object> details = new HashMap<>();
                    details.put("employeeId", emp.getId());
                    details.put("employeeName", emp.getName());
                    details.put("role", slot.getRole());
                    details.put("employeeType", emp.getCategory());
                    details.put("gender", emp.getGender());
                    details.put("wage", emp.getHourlyWage());
                    details.put("rating", emp.getPerformanceRating());

                    slotsByShift
                        .computeIfAbsent(slot.getShiftName(), k -> new TreeMap<>())
                        .computeIfAbsent(slot.getDate(), k -> new ArrayList<>())
                        .add(details);

                    // Save to DB (unchanged — uses slot.getRole())
                    ShiftDefinition assignedShiftDef = null;
                    for (ShiftDefinition sd : shiftDefinitions) {
                        if (sd.getShiftName().equals(slot.getShiftName())) {
                            assignedShiftDef = sd;
                            break;
                        }
                    }
                    String st = assignedShiftDef != null ? assignedShiftDef.getStartTime() : "00:00";
                    String et = assignedShiftDef != null ? assignedShiftDef.getEndTime() : "00:00";

                    databaseService.syncAssignment(
                            slot.getDate(), slot.getShiftName(),
                            emp.getId(), emp.getName(), slot.getRole(),
                            emp.getCategory(), emp.getGender(),
                            emp.getPerformanceRating(), st, et,
                            emp.getHourlyWage()
                    );
                }
            }

            // 2. Constraint violations (real analysis, same pattern as solveShiftV2)
            List<String> constraintViolations = new ArrayList<>();
            if (solution.getScore() != null && !solution.getScore().isFeasible()) {
                ai.timefold.solver.core.api.solver.SolutionManager<ShiftScheduleSlot, ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> solutionManager = ai.timefold.solver.core.api.solver.SolutionManager.create(solverFactory);
                ai.timefold.solver.core.api.score.analysis.ScoreAnalysis<ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> scoreAnalysis = solutionManager.analyze(solution);

                for (ai.timefold.solver.core.api.score.analysis.ConstraintAnalysis<ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> ca : scoreAnalysis.constraintMap().values()) {
                    if (ca.score().hardScore() < 0) {
                        constraintViolations.add("Violated Constraint: " + ca.constraintRef().constraintName() +
                                " (Score impact: " + ca.score().hardScore() + ")");
                    }
                }
            }

            // 3. Build per-shift result blocks
            List<Map<String, Object>> shiftResults = new ArrayList<>();
            int totalAssignedCount = 0;
            int totalSkippedCount = 0;
            int shiftIndex = 0;

            for (ShiftDefinition shiftDef : shiftDefinitions) {
                String sn = shiftDef.getShiftName();
                Map<String, List<Map<String, Object>>> dateAssignments =
                    slotsByShift.getOrDefault(sn, Collections.emptyMap());

                // Gather shift-level metadata from ShiftRoleRequirements
                int entitiesPlanned = 0;
                Set<String> shiftDates = new TreeSet<>();
                Map<String, Integer> maxPerDayByRole = new HashMap<>();
                Set<String> shiftRoles = new LinkedHashSet<>();

                for (ShiftRoleRequirement req : shiftRoleRequirements) {
                    if (req.getShiftName().equals(sn)) {
                        entitiesPlanned += req.getMaxWorkers();
                        shiftDates.add(req.getDate());
                        shiftRoles.add(req.getRoleName());
                        maxPerDayByRole.put(req.getRoleName(), req.getMaxWorkers());
                    }
                }

                String sMinDate = shiftDates.isEmpty() ? "" : shiftDates.iterator().next();
                String sMaxDate = shiftDates.isEmpty() ? "" : ((java.util.TreeSet<String>) shiftDates).last();

                // Build daily_summary + accumulate stats for this shift
                List<Map<String, Object>> dailySummary = new ArrayList<>();
                int shiftAssignedCount = 0;
                Map<String, Long> assignmentsByEmpType = new HashMap<>();
                Map<String, Integer> totalRoleAssignments = new HashMap<>();
                Map<String, Double> totalRoleWages = new HashMap<>();

                for (String date : shiftDates) {
                    List<Map<String, Object>> dayAssignments =
                        dateAssignments.getOrDefault(date, Collections.emptyList());

                    Map<String, Object> daySummary = new HashMap<>();
                    daySummary.put("date", date);
                    daySummary.put("assignments", dayAssignments);
                    daySummary.put("count", dayAssignments.size());

                    Map<String, Integer> roleCounts = new HashMap<>();
                    for (Map<String, Object> a : dayAssignments) {
                        String role = (String) a.get("role");
                        roleCounts.merge(role, 1, Integer::sum);
                        totalRoleAssignments.merge(role, 1, Integer::sum);
                        Number wageNum = (Number) a.get("wage");
                        if (wageNum != null) totalRoleWages.merge(role, wageNum.doubleValue(), Double::sum);
                        String empType = (String) a.get("employeeType");
                        assignmentsByEmpType.merge(empType != null ? empType : "Unspecified", 1L, Long::sum);
                    }
                    daySummary.put("role_counts", roleCounts);
                    dailySummary.add(daySummary);
                    shiftAssignedCount += dayAssignments.size();
                }

                int shiftSkipped = entitiesPlanned - shiftAssignedCount;

                // role_statistics
                Map<String, Map<String, Object>> roleStatistics = new HashMap<>();
                for (String role : shiftRoles) {
                    int count = totalRoleAssignments.getOrDefault(role, 0);
                    double totalWage = totalRoleWages.getOrDefault(role, 0.0);
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("assignments", count);
                    stats.put("average_wage", count > 0
                        ? String.format(java.util.Locale.US, "%.2f", totalWage / count) : "0.00");
                    stats.put("max_per_day", maxPerDayByRole.getOrDefault(role, 0));
                    roleStatistics.put(role, stats);
                }

                // message
                String shiftMessage;
                if (!constraintViolations.isEmpty()) {
                    shiftMessage = "Schedule solved but with HARD constraint violations!";
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Successfully assigned shifts for ").append(shiftDates.size()).append(" days. ");
                    sb.append("Total assignments: ").append(shiftAssignedCount).append(". ");
                    if (shiftSkipped > 0) sb.append("Skipped ").append(shiftSkipped).append(" assignments.");
                    shiftMessage = sb.toString();
                }

                // Assemble shift result block
                Map<String, Object> shiftResult = new HashMap<>();
                shiftResult.put("status", "success");
                shiftResult.put("shift_name", sn);
                shiftResult.put("shift_index", shiftIndex++);
                shiftResult.put("period", sMinDate + " to " + sMaxDate);
                shiftResult.put("shift_time", shiftDef.getStartTime() + " - " + shiftDef.getEndTime());
                shiftResult.put("total_working_days", shiftDates.size());
                shiftResult.put("solver_time_seconds", solverTime / 1000.0);
                shiftResult.put("new_assignments_made", shiftAssignedCount);
                shiftResult.put("entities_planned", entitiesPlanned);
                shiftResult.put("total_possible_assignments", entitiesPlanned);
                shiftResult.put("skipped_count", shiftSkipped);
                shiftResult.put("role_statistics", roleStatistics);
                shiftResult.put("assignments_by_employee_type", assignmentsByEmpType);
                shiftResult.put("constraint_violations", constraintViolations);
                shiftResult.put("message", shiftMessage);
                shiftResult.put("daily_summary", dailySummary);

                shiftResults.add(shiftResult);
                totalAssignedCount += shiftAssignedCount;
                totalSkippedCount += shiftSkipped;
            }

            // 4. Build outer wrapper (batch-assign compatible)
            Map<String, Object> overallStats = new HashMap<>();
            overallStats.put("total_shifts_processed", shiftDefinitions.size());
            overallStats.put("successful_shifts", (long) shiftDefinitions.size());
            overallStats.put("failed_shifts", 0L);
            overallStats.put("total_assignments_made", totalAssignedCount);
            overallStats.put("total_working_days", days);
            overallStats.put("total_skipped_assignments", totalSkippedCount);
            overallStats.put("total_solver_time_seconds", solverTime / 1000.0);

            if (solution.getScore() != null) {
                overallStats.put("solver_score", solution.getScore().toString());
                overallStats.put("is_feasible", solution.getScore().isFeasible());
            }

            String summary = String.format(
                "Successfully processed %d shifts. Total assignments: %d across %d days.",
                shiftDefinitions.size(), totalAssignedCount, days
            );

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("status", "completed");
            responseData.put("summary", summary);
            responseData.put("overall_statistics", overallStats);
            responseData.put("shift_results", shiftResults);

            return responseData;
        } catch (Exception e) {
            LOG.error("Global-V2 solver failed", e);
            Map<String, Object> errMap = new HashMap<>();
            errMap.put("status", "error");
            errMap.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error");
            return errMap;
        }
    }

    // ========================================================================================
    // V2 API â€” Availability-aware shift assignment
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

        // Prepare existing users â€” V2: also parse availability
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
                                // "from" provided but "to" missing â†’ default to end of day
                                toTime = java.time.LocalTime.of(23, 59);
                            }
                            // If neither from nor to â†’ AvailabilityEntry.coversShift() returns false (treated as unavailable)
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

            // 3. Build entities â€” V2: skip unavailable/partially-unfit employee-date combinations
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

            List<Map<String, Object>> scoreEvents = new ArrayList<>();
            long startMs = System.currentTimeMillis();
            solver.addEventListener(event -> {
                long elapsedMs = System.currentTimeMillis() - startMs;
                scoreEvents.add(Map.of(
                    "elapsed_ms", elapsedMs,
                    "score", event.getNewBestScore().toString()
                ));
            });

            ShiftSchedule solution = solver.solve(problem);
            totalSolverTimeMs = System.currentTimeMillis() - startMs;

            // 5. Process results â€” group by date
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
                    String empTypeKey = empType != null ? empType : "Unspecified";
                    assignmentsByEmployeeType.put(empTypeKey, assignmentsByEmployeeType.getOrDefault(empTypeKey, 0L) + 1L);
                }
                dayResult.put("role_counts", roleCounts);
                
                assignmentsByDate.add(dayResult);
            }

            responseData.put("score_events", scoreEvents);

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
