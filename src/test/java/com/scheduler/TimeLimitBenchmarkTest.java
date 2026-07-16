package com.scheduler;

import com.scheduler.service.SolverService;
import com.scheduler.service.DatabaseService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag("benchmark")
public class TimeLimitBenchmarkTest {

    private static final Logger LOG = Logger.getLogger(TimeLimitBenchmarkTest.class);

    @Inject
    SolverService solverService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    public void cleanDatabase() {
        databaseService.clearAllAssignments();
    }

    /**
     * CORE BENCHMARK: Multi-Objective test on a representative medium scenario
     * 150 employees, 100 needed, 7 days.
     * Runs cost, quality, and both to establish floor/ceiling.
     * Uses multiple runs for "both" mode to smooth out solver non-determinism.
     */
    @Test
    public void benchmarkMultiObjective_150emp_100needed_7days() {
        LOG.info("╔══════════════════════════════════════════════════════════════════╗");
        LOG.info("║  BENCHMARK: 150 emps, 100 needed, 7 days (Multi-Objective)       ║");
        LOG.info("╚══════════════════════════════════════════════════════════════════╝");

        int[] timeLimits = {5, 10, 20, 30, 45, 60, 90, 120};

        LOG.info("\n>>> RUNNING MODE: cost (Wage Floor) [1 run]");
        runBenchmarkSuite(150, 100, 7, timeLimits, "cost", 1);

        LOG.info("\n>>> RUNNING MODE: quality (Rating Ceiling) [1 run]");
        runBenchmarkSuite(150, 100, 7, timeLimits, "quality", 1);

        LOG.info("\n>>> RUNNING MODE: both (Balanced) [3 runs for averaging]");
        runBenchmarkSuite(150, 100, 7, timeLimits, "both", 3);
    }

    /**
     * TIGHTLY CONSTRAINED: 60 employees, 50 needed, 7 days
     */
    @Test
    public void benchmarkTimeLimits_60emp_50needed_7days() {
        LOG.info("╔══════════════════════════════════════════════════════════════════╗");
        LOG.info("║  BENCHMARK: 60 emps, 50 needed, 7 days (Tightly Constrained)     ║");
        LOG.info("╚══════════════════════════════════════════════════════════════════╝");

        int[] timeLimits = {5, 10, 20, 30, 60};
        runBenchmarkSuite(60, 50, 7, timeLimits, "both", 1);
    }

    /**
     * LARGE: 300 employees, 200 needed, 14 days
     */
    @Test
    public void benchmarkTimeLimits_300emp_200needed_14days() {
        LOG.info("╔══════════════════════════════════════════════════════════════════╗");
        LOG.info("║  BENCHMARK: 300 emps, 200 needed, 14 days (Large)                ║");
        LOG.info("╚══════════════════════════════════════════════════════════════════╝");

        int[] timeLimits = {20, 60, 120, 180, 240};
        runBenchmarkSuite(300, 200, 14, timeLimits, "both", 1);
    }

    /**
     * ACTUAL FORMULA MECHANISM TEST
     * Tests the formula's early-exit behavior by passing no limits and measuring actual runtimes.
     */
    @Test
    public void benchmarkFormulaMechanism() {
        LOG.info("╔══════════════════════════════════════════════════════════════════╗");
        LOG.info("║  BENCHMARK: Formula Mechanism Verification (Unimproved Limits)   ║");
        LOG.info("╚══════════════════════════════════════════════════════════════════╝");
        
        List<String> results = new ArrayList<>();
        results.add(String.format("%-25s | %-15s | %-15s | %-15s",
                "Scenario", "Budget Limit(s)", "Unimproved(s)", "Actual Run(s)"));

        // Small: 40 emps, 20 needed, 3 days (Should hit unimproved exit early)
        results.add(testMechanism(40, 20, 3, "Small (40E/3D)"));

        // Medium: 150 emps, 100 needed, 7 days
        results.add(testMechanism(150, 100, 7, "Medium (150E/7D)"));

        // Large: 300 emps, 200 needed, 14 days
        results.add(testMechanism(300, 200, 14, "Large (300E/14D)"));

        LOG.info("");
        LOG.info("  === MECHANISM RESULTS ===");
        for (String r : results) {
            LOG.info("  " + r);
        }
    }

    private String testMechanism(int totalEmp, int neededPerDay, int days, String scenarioName) {
        databaseService.clearAllAssignments();
        
        // We omit time limits so the solver uses its internal formula
        Map<String, Object> req = createRequest(totalEmp, neededPerDay, days, -1, -1, "both");
        
        long startMs = System.currentTimeMillis();
        Map<String, Object> result = solverService.solveShift(req);
        long actualMs = System.currentTimeMillis() - startMs;
        double actualSec = actualMs / 1000.0;
        
        // Calculate what the formula should have given
        long perDayBudget = 2L + ((long) totalEmp / 20L);
        long defaultTimeLimit = Math.max(5L, perDayBudget * days);
        defaultTimeLimit = Math.min(defaultTimeLimit, 300L); // cap at 5 minutes
        long defaultUnimprovedLimit = Math.max(2L, defaultTimeLimit / 3L);
        
        String line = String.format("%-25s | %-15d | %-15d | %-15.1f",
                scenarioName, defaultTimeLimit, defaultUnimprovedLimit, actualSec);
        LOG.info("  " + line);
        return line;
    }

    // ==================== HELPERS ====================

    private void runBenchmarkSuite(int totalEmp, int neededPerDay, int days, int[] timeLimits, String optMode, int runs) {
        List<String> results = new ArrayList<>();
        results.add(String.format("%-10s | %-10s | %-10s | %-12s | %-12s | %-12s",
                "Limit(s)", "Actual(s)", "Assigned", "AvgWage", "AvgRating", "TargetCount"));

        int targetTotal = neededPerDay * days;

        for (int tl : timeLimits) {
            double sumActualSec = 0;
            double sumAvgWage = 0;
            double sumAvgRating = 0;
            int finalAssignedCount = 0;
            
            for (int r = 0; r < runs; r++) {
                databaseService.clearAllAssignments();

                int unimproved = Math.max(2, tl / 3);
                // Override seed for each run to avoid exact duplicate runs in multi-run scenarios
                Map<String, Object> req = createRequest(totalEmp, neededPerDay, days, tl, unimproved, optMode, r);

                long startMs = System.currentTimeMillis();
                Map<String, Object> result = solverService.solveShift(req);
                long actualMs = System.currentTimeMillis() - startMs;
                sumActualSec += (actualMs / 1000.0);

                String status = (String) result.get("status");
                int assignedCount = 0;
                double avgWage = 0.0;
                double avgRating = 0.0;

                if ("success".equals(status)) {
                    List<Map<String, Object>> byDate = (List<Map<String, Object>>) result.get("daily_summary");
                    if (byDate != null) {
                        double totalWage = 0;
                        double totalRating = 0;
                        
                        for (Map<String, Object> dateEntry : byDate) {
                            List<Map<String, Object>> assignedEmployees = (List<Map<String, Object>>) dateEntry.get("assignments");
                            if (assignedEmployees != null) {
                                for (Map<String, Object> empData : assignedEmployees) {
                                    assignedCount++;
                                    Number hw = (Number) empData.getOrDefault("wage", 0.0);
                                    Number rt = (Number) empData.getOrDefault("rating", 0.0);
                                    totalWage += hw.doubleValue();
                                    totalRating += rt.doubleValue();
                                }
                            }
                        }
                        
                        if (assignedCount > 0) {
                            avgWage = totalWage / assignedCount;
                            avgRating = totalRating / assignedCount;
                        }
                    }
                }
                
                sumAvgWage += avgWage;
                sumAvgRating += avgRating;
                finalAssignedCount = assignedCount;
            }
            
            double actualSec = sumActualSec / runs;
            double avgWage = sumAvgWage / runs;
            double avgRating = sumAvgRating / runs;

            String line = String.format("%-10d | %-10.1f | %-10d | $%-11.2f | %-12.2f | %-12d",
                    tl, actualSec, finalAssignedCount, avgWage, avgRating, targetTotal);
            results.add(line);
            LOG.info("  [" + tl + "s] (Avg of " + runs + " runs) → Assigned: " + finalAssignedCount + "/" + targetTotal + " | Wage: $" + String.format("%.2f", avgWage) + " | Rating: " + String.format("%.2f", avgRating) + " | Time: " + actualSec + "s");
        }

        LOG.info("");
        LOG.info("  === RESULTS (" + optMode + ") ===");
        for (String r : results) {
            LOG.info("  " + r);
        }
    }

    private Map<String, Object> createRequest(int totalEmployees, int neededPerDay, int days,
                                               int timeLimitSec, int unimprovedLimitSec, String optimization) {
        return createRequest(totalEmployees, neededPerDay, days, timeLimitSec, unimprovedLimitSec, optimization, 0);
    }
    
    private Map<String, Object> createRequest(int totalEmployees, int neededPerDay, int days,
                                               int timeLimitSec, int unimprovedLimitSec, String optimization, int seedOffset) {
        Map<String, Object> req = new HashMap<>();
        req.put("shift_name", "Benchmark Shift");
        req.put("start_date", "2027-01-01"); // Use future dates far from other tests
        java.time.LocalDate endDate = java.time.LocalDate.parse("2027-01-01").plusDays(days - 1);
        req.put("end_date", endDate.toString());
        req.put("start_time", "09:00");
        req.put("end_time", "17:00");
        req.put("optimization", optimization);
        
        if (timeLimitSec > 0) {
            req.put("time_limit_seconds", timeLimitSec);
        }
        if (unimprovedLimitSec > 0) {
            req.put("unimproved_time_limit_seconds", unimprovedLimitSec);
        }

        // Split employees across 4 roles for realism
        String[] roles = {"Operator", "Supervisor", "Technician", "Helper"};
        int perRole = neededPerDay / roles.length;
        int remainder = neededPerDay % roles.length;

        List<Map<String, Object>> rolesList = new ArrayList<>();
        for (int i = 0; i < roles.length; i++) {
            int needed = perRole + (i < remainder ? 1 : 0);
            rolesList.add(Map.of("role_name", roles[i], "max_workers", needed, "rating", 1));
        }
        req.put("roles", rolesList);

        // Create employees with varying wages and ratings for a realistic distribution
        List<Map<String, Object>> employees = new ArrayList<>();
        Random rng = new Random(42 + seedOffset); // Change seed for multi-run scenarios

        for (int i = 0; i < totalEmployees; i++) {
            String role = roles[i % roles.length];
            int rating = 1 + rng.nextInt(5); // 1-5
            int hourlyWage = 15 + rng.nextInt(36); // 15-50
            String gender = (i % 2 == 0) ? "Male" : "Female";

            employees.add(Map.of(
                    "employee_id", "BENCH_" + i,
                    "name", "Employee " + i,
                    "role", role,
                    "rate", hourlyWage,
                    "unit", "hour",
                    "rating", rating,
                    "employeeType", "Permanent",
                    "gender", gender
            ));
        }
        req.put("existing_users", employees);

        return req;
    }
}
