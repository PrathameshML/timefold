package com.scheduler;

import ai.timefold.solver.benchmark.api.PlannerBenchmark;
import ai.timefold.solver.benchmark.api.PlannerBenchmarkFactory;
import ai.timefold.solver.benchmark.config.PlannerBenchmarkConfig;
import ai.timefold.solver.benchmark.config.SolverBenchmarkConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import ai.timefold.solver.core.config.phase.PhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class BenchmarkRunner {

    public static void main(String[] args) {
        System.out.println("🚀 Starting Timefold Benchmark Module (Phase 1)");

        // 1. Configure the base SolverConfig (Current V2)
        SolverConfig baseSolverConfig = new SolverConfig()
                .withSolutionClass(ShiftApp.ShiftScheduleV2.class)
                .withEntityClasses(ShiftApp.Scheduler.EmployeeAssignment.class)
                .withConstraintProviderClass(ShiftApp.ShiftConstraintsV2.class)
                .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofMinutes(1)));

        // 2. Build Benchmark Config programmatically
        PlannerBenchmarkConfig benchmarkConfig = new PlannerBenchmarkConfig()
                .withBenchmarkDirectory(new File("target/benchmarks"));

        // Strategy 1: Current V2 (Default Timefold behavior)
        SolverBenchmarkConfig currentV2 = new SolverBenchmarkConfig()
                .withName("Current_V2")
                .withSolverConfig(new SolverConfig(baseSolverConfig));

        // Strategy 2: Late Acceptance
        SolverConfig laConfig = new SolverConfig(baseSolverConfig);
        laConfig.withPhaseList(Arrays.asList(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig().withAcceptorConfig(
                        new LocalSearchAcceptorConfig().withLateAcceptanceSize(400)
                )
        ));
        SolverBenchmarkConfig lateAcceptance = new SolverBenchmarkConfig()
                .withName("Late_Acceptance_400")
                .withSolverConfig(laConfig);

        // Strategy 3: Simulated Annealing
        SolverConfig saConfig = new SolverConfig(baseSolverConfig);
        saConfig.withPhaseList(Arrays.asList(
                new ConstructionHeuristicPhaseConfig(),
                new LocalSearchPhaseConfig().withAcceptorConfig(
                        new LocalSearchAcceptorConfig().withSimulatedAnnealingStartingTemperature("0hard/10000medium/10000soft")
                )
        ));
        SolverBenchmarkConfig simulatedAnnealing = new SolverBenchmarkConfig()
                .withName("Simulated_Annealing")
                .withSolverConfig(saConfig);

        benchmarkConfig.withSolverBenchmarkConfigList(Arrays.asList(currentV2, lateAcceptance, simulatedAnnealing));

        // 3. Load Datasets
        List<ShiftApp.ShiftScheduleV2> datasets = new ArrayList<>();
        datasets.add(loadProblem("test_payload.json", "Small_Dataset"));
        datasets.add(loadProblem("dataset_500_convergence.json", "Stress_Test_500_Employees"));

        datasets.removeIf(Objects::isNull);

        if (datasets.isEmpty()) {
            System.err.println("❌ No datasets loaded. Please ensure JSON files are in the working directory.");
            return;
        }

        System.out.println("✅ Loaded " + datasets.size() + " datasets. Building benchmark...");

        // Evaluate initial score
        try {
            ai.timefold.solver.core.api.score.ScoreManager<ShiftApp.ShiftScheduleV2, ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore> scoreManager = 
                ai.timefold.solver.core.api.score.ScoreManager.create(ai.timefold.solver.core.api.solver.SolverFactory.create(baseSolverConfig));
            for (ShiftApp.ShiftScheduleV2 prob : datasets) {
                System.out.println("Initial score for " + prob.getRequestedShiftName() + ": " + scoreManager.updateScore(prob));
            }
        } catch (Exception e) {
            System.err.println("Failed to calculate initial score: " + e.getMessage());
        }

        // 4. Run Benchmark
        PlannerBenchmarkFactory benchmarkFactory = PlannerBenchmarkFactory.create(benchmarkConfig);
        PlannerBenchmark benchmark = benchmarkFactory.buildPlannerBenchmark(datasets.toArray(new ShiftApp.ShiftScheduleV2[0]));

        System.out.println("⏳ Running benchmark. This will take a while...");
        benchmark.benchmark(); // Don't show report in browser if not running in UI
        System.out.println("🎉 Benchmark complete! Report generated in target/benchmarks/index.html");
    }

    private static ShiftApp.ShiftScheduleV2 loadProblem(String fileName, String problemName) {
        try {
            File file = new File(fileName);
            if (!file.exists()) {
                System.out.println("⚠️ Dataset not found: " + fileName);
                return null;
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> input = mapper.readValue(file, new TypeReference<Map<String, Object>>() {});

            ShiftApp.ShiftScheduleV2 problem = parseProblem(input);
            problem.setRequestedShiftName(problemName); // Used for benchmark naming
            return problem;
        } catch (Exception e) {
            System.err.println("❌ Failed to load dataset " + fileName + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static ShiftApp.ShiftScheduleV2 parseProblem(Map<String, Object> input) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> existingUsers = (List<Map<String, Object>>) input.get("existing_users");

        String shiftName = (String) input.get("shift_name");
        String startDateStr = (String) input.get("start_date");
        String endDateStr = (String) input.get("end_date");
        String startTime = (String) input.get("start_time");
        String endTime = (String) input.get("end_time");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) input.get("roles");
        boolean prioritizePermanent = Boolean.TRUE.equals(input.getOrDefault("prioritize_permanent", true));

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        List<LocalDate> workingDates = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            workingDates.add(current);
            current = current.plusDays(1);
        }

        LocalTime startLocalTime = LocalTime.parse(startTime);
        LocalTime endLocalTime = LocalTime.parse(endTime);

        double shiftDurationHours;
        if (endLocalTime.isBefore(startLocalTime)) {
            long minutesToMidnight = Duration.between(startLocalTime, LocalTime.MAX).toMinutes() + 1;
            long minutesFromMidnight = Duration.between(LocalTime.MIN, endLocalTime).toMinutes();
            shiftDurationHours = (minutesToMidnight + minutesFromMidnight) / 60.0;
        } else {
            shiftDurationHours = Duration.between(startLocalTime, endLocalTime).toMinutes() / 60.0;
        }

        int empCounter = 1;
        List<ShiftApp.Scheduler.EmployeeAssignment> planningEntities = new ArrayList<>();
        Map<String, ShiftApp.EmployeeInfo> allEmployees = new HashMap<>();

        List<ShiftApp.Scheduler.ShiftSchedule.RoleLimit> roleLimits = new ArrayList<>();
        List<ShiftApp.Scheduler.ShiftSchedule.RatingRequirement> ratingRequirements = new ArrayList<>();
        Map<String, List<String>> requiredSkillsMap = new HashMap<>();

        for (Map<String, Object> roleSpec : roles) {
            String roleName = (String) roleSpec.get("role_name");
            Object ratingObj = roleSpec.get("rating");
            Number maxWorkersObj = (Number) roleSpec.get("max_workers");

            roleLimits.add(new ShiftApp.Scheduler.ShiftSchedule.RoleLimit(roleName, maxWorkersObj.intValue()));

            List<Integer> allowedRatings = new ArrayList<>();
            if (ratingObj instanceof Number) {
                int min = ((Number) ratingObj).intValue();
                for (int r = min; r <= 5; r++) allowedRatings.add(r);
            } else {
                for (int r = 1; r <= 5; r++) allowedRatings.add(r);
            }
            ratingRequirements.add(new ShiftApp.Scheduler.ShiftSchedule.RatingRequirement(roleName, allowedRatings));

            @SuppressWarnings("unchecked")
            List<String> reqSkills = (List<String>) roleSpec.get("required_skills");
            if (reqSkills != null && !reqSkills.isEmpty()) {
                requiredSkillsMap.put(roleName, reqSkills);
            }
        }

        if (existingUsers != null) {
            for (Map<String, Object> user : existingUsers) {
                String name = (String) user.get("name");
                Number rateObj = (Number) user.get("rate");
                String unit = (String) user.get("unit");
                Object ratingObj = user.get("rating");
                String role = (String) user.get("role");
                String existingEmployeeId = (String) user.get("employee_id");
                String employeeType = user.containsKey("employeeType") ? (String) user.get("employeeType") : "Permanent";

                String employeeId = (existingEmployeeId != null && !existingEmployeeId.trim().isEmpty()) ? existingEmployeeId : "EMP" + String.format("%03d", empCounter++);

                double hourlyWage = rateObj != null ? rateObj.doubleValue() : 10.0;
                if ("day".equalsIgnoreCase(unit)) hourlyWage = hourlyWage / 8.0;
                else if ("month".equalsIgnoreCase(unit)) hourlyWage = hourlyWage / (22.0 * 8.0);

                int performanceRating = 3;
                if (ratingObj instanceof Number) performanceRating = ((Number) ratingObj).intValue();

                ShiftApp.EmployeeInfo empInfo = new ShiftApp.EmployeeInfo(employeeId, name, employeeType, "Male", hourlyWage, "MGR001", "Operations", role, "email@co.com", "+919999999999");
                empInfo.setPerformanceRating(performanceRating);

                @SuppressWarnings("unchecked")
                List<String> skillsList = (List<String>) user.get("skills");
                if (skillsList != null) empInfo.getSkills().addAll(skillsList);

                allEmployees.put(employeeId, empInfo);
            }
        }

        for (LocalDate date : workingDates) {
            String dateStr = date.toString();
            for (Map.Entry<String, ShiftApp.EmployeeInfo> entry : allEmployees.entrySet()) {
                String empId = entry.getKey();
                ShiftApp.EmployeeInfo emp = entry.getValue();

                boolean roleRequested = roleLimits.stream().anyMatch(rl -> rl.getRoleName().equals(emp.getPosition()));
                if (!roleRequested) continue;

                ShiftApp.Scheduler.EmployeeAssignment entity = new ShiftApp.Scheduler.EmployeeAssignment(
                        empId + "_" + dateStr, empId, emp.getName(), dateStr,
                        emp.getCategory(), emp.getGender(), emp.getDepartment(), emp.getPosition()
                );
                entity.setHourlyWage(emp.getHourlyWage());
                entity.setPerformanceRating(emp.getPerformanceRating());
                entity.setShiftStartStr(startTime);
                entity.setShiftStartTimeObj(startLocalTime);
                entity.setShiftEndStr(endTime);
                entity.setShiftDurationHours(shiftDurationHours);
                entity.setIsoWeekNum(date.get(java.time.temporal.WeekFields.ISO.weekOfYear()));
                entity.setLocalDateObj(date);
                entity.setSkills(emp.getSkills());
                entity.setEmployeeType(emp.getEmployeeType());
                entity.setPrioritizePermanent(prioritizePermanent);
                entity.setPermanentEmployee("Permanent".equalsIgnoreCase(emp.getEmployeeType()));
                entity.setRequestedShift(shiftName);

                long missing = requiredSkillsMap.getOrDefault(emp.getPosition(), List.of()).stream()
                        .filter(reqSkill -> emp.getSkills().stream().noneMatch(s -> s.equalsIgnoreCase(reqSkill)))
                        .count();
                entity.setMissingSkillCount(missing);

                planningEntities.add(entity);
            }
        }

        List<String> possibleShifts = Arrays.asList(shiftName);

        List<ShiftApp.ConstraintConfig> constraintConfigs = new ArrayList<>();
        constraintConfigs.add(new ShiftApp.ConstraintConfig(1, "skillMatch", "Employee skills should match with shift required skills", "HARD", 100.0, "skillMatchPercentage"));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(2, "noOverlappingShifts", "No overlapping/concurrent shifts per employee", "HARD", null, null));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(3, "unavailableTimeslot", "Shift not planned on unavailable timeslot", "HARD", null, null));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(4, "everyShiftPlanned", "Every shift should be planned/assigned", "MEDIUM", null, null));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(5, "wageOptimization", "Assign employees preferring lower wages", "SOFT", null, null));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(6, "maxDailyHours", "Maximum working hours per day", "MEDIUM", 8.0, "maxHoursPerDay"));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(7, "maxWeeklyHours", "Maximum working hours per week", "MEDIUM", 40.0, "maxHoursPerWeek"));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(8, "overtimeThreshold", "Hours beyond threshold counted as overtime", "SOFT", 8.0, "otThresholdHours"));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(9, "breakAfterHours", "Mandatory break after specified hours", "HARD", 4.0, "breakAfterHours"));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(10, "consecutiveShifts", "Minimize gaps in schedule", "HARD", null, null));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(11, "permanentPriority", "Priority to permanent employees over contract", prioritizePermanent ? "SOFT" : "DISABLED", null, null));
        constraintConfigs.add(new ShiftApp.ConstraintConfig(12, "maximizeRating", "Reward higher-rated employees", "SOFT", null, null));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activeOverrides = (List<Map<String, Object>>) input.get("active_constraints");
        if (activeOverrides != null) {
            constraintConfigs.forEach(c -> c.setEnabled(false));
            for (Map<String, Object> override : activeOverrides) {
                String name = (String) override.get("name");
                String severity = (String) override.get("severity");
                Number value = (Number) override.get("value");
                constraintConfigs.stream().filter(c -> c.getConstraintName().equals(name)).findFirst().ifPresent(c -> {
                    c.setEnabled(true);
                    if (severity != null) c.setSeverity(severity);
                    if (value != null) c.setParameterValue(value.doubleValue());
                });
            }
        }

        double averageWage = allEmployees.values().stream()
                .mapToDouble(ShiftApp.EmployeeInfo::getHourlyWage)
                .average()
                .orElse(1.0);

        ShiftApp.ShiftConstraintsV2.setConfiguration(roleLimits, ratingRequirements, requiredSkillsMap, constraintConfigs, averageWage);

        ShiftApp.ShiftScheduleV2 problem = new ShiftApp.ShiftScheduleV2(planningEntities, possibleShifts, roleLimits, ratingRequirements);
        problem.setRequestedShiftName(shiftName);
        problem.setPrioritizePermanent(prioritizePermanent);

        return problem;
    }
}
