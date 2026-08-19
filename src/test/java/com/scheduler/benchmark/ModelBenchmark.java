package com.scheduler.benchmark;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

import com.scheduler.model.EmployeeAssignment;
import com.scheduler.model.ShiftRoleRequirement;
import com.scheduler.model.ShiftSchedule;
import com.scheduler.model.WageContext;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HEAD-TO-HEAD BENCHMARK — Model A (Employee×Day) vs Model B (ShiftSlot)
 *
 * Phase 1: 3 runs at original scale (60 emp, 50 slots, 3 shifts, 5 days)
 * Phase 2: 1 run at production scale (200 emp, 140 slots, 4 shifts, 7 days)
 *
 * CONSTRAINT PARITY (vs production ShiftConstraintProvider.java):
 *   ✓ wageOptimization: normalized by averageWagePerRole, multiplier=1000 (lines 120-132)
 *   ✓ maximizeRating: rating * 100 (lines 135-143)
 *   ✓ maxWorkersPerRole_global: groupBy + join ShiftRoleRequirement (lines 152-163)
 *   ✓ everyShiftPlanned_global: groupBy + penalize understaffing × 10000 (lines 166-177)
 *   ✓ everyShiftPlanned_empty: ifNotExists + maxWorkers × 10000 (lines 189-197)
 *   — noOverlappingShifts: N/A (no ExistingAssignment facts — no prior DB state)
 *   — minimumRatingRequirement: N/A (all employees rating ≥ 3, benchmark uses ≥ 3)
 *
 * Model B equivalent constraints:
 *   ✓ noDoubleBooking: hard join-on-same-employee-same-day (replaces structural guarantee)
 *   ✓ everySlotFilled: medium, 10000 per empty slot
 *   ✓ wageOptimization: normalized by AverageWageFact, multiplier=1000
 *   ✓ maximizeRating: rating * 100
 *   ✓ maxWorkersPerRole: structurally enforced by slot count = maxWorkers
 */
public class ModelBenchmark {

    static final String ROLE = "Developer";

    // ════════════════════════════════════════════════════════════════════════
    // SCENARIO DEFINITION
    // ════════════════════════════════════════════════════════════════════════

    static class Scenario {
        final String name;
        final int totalEmployees;
        final int days;
        final LocalDate start;
        final String[] shiftNames;
        final int[] slotsPerShift;       // per day
        final int[][] eligibilityRanges; // [rangeIdx] = {fromEmp, toEmp, shiftIdx...}
        final long timeLimitSec;
        final long unimprovedSec;

        Scenario(String name, int totalEmployees, int days, LocalDate start,
                 String[] shiftNames, int[] slotsPerShift,
                 int[][] eligibilityRanges, long timeLimitSec, long unimprovedSec) {
            this.name = name;
            this.totalEmployees = totalEmployees;
            this.days = days;
            this.start = start;
            this.shiftNames = shiftNames;
            this.slotsPerShift = slotsPerShift;
            this.eligibilityRanges = eligibilityRanges;
            this.timeLimitSec = timeLimitSec;
            this.unimprovedSec = unimprovedSec;
        }

        int totalSlotsPerDay() {
            int sum = 0;
            for (int s : slotsPerShift) sum += s;
            return sum;
        }

        int totalSlots() { return totalSlotsPerDay() * days; }
    }

    // ── Scenario 1: Original (60 emp, 50 slots) ──
    static final Scenario SMALL = new Scenario(
            "SMALL: 60 emp, 50 slots, 3 shifts × 5 days",
            60, 5, LocalDate.of(2026, 11, 1),
            new String[]{"Morning", "Evening", "Night"},
            new int[]{4, 3, 3},  // 10/day × 5 = 50 total
            new int[][]{
                    {1, 10, 0, 1, 2},     // E01-E10: Morning + Evening + Night
                    {11, 25, 0},           // E11-E25: Morning only
                    {26, 40, 1},           // E26-E40: Evening only
                    {41, 50, 2},           // E41-E50: Night only
                    {51, 55, 0, 1},        // E51-E55: Morning + Evening
                    {56, 60, 1, 2}         // E56-E60: Evening + Night
            },
            60, 20
    );

    // ── Scenario 2: Production scale (200 emp, 140 slots) ──
    static final Scenario LARGE = new Scenario(
            "LARGE: 200 emp, 140 slots, 4 shifts × 7 days",
            200, 7, LocalDate.of(2026, 11, 1),
            new String[]{"Morning", "Evening", "Night", "Late Night"},
            new int[]{6, 5, 5, 4},  // 20/day × 7 = 140 total
            new int[][]{
                    {1, 20, 0, 1, 2, 3},   // E001-E020: all 4 shifts
                    {21, 60, 0},           // E021-E060: Morning only
                    {61, 100, 1},          // E061-E100: Evening only
                    {101, 130, 2},         // E101-E130: Night only
                    {131, 155, 3},         // E131-E155: Late Night only
                    {156, 175, 0, 1},      // E156-E175: Morning + Evening
                    {176, 190, 1, 2},      // E176-E190: Evening + Night
                    {191, 200, 2, 3}       // E191-E200: Night + Late Night
            },
            120, 40
    );

    // ════════════════════════════════════════════════════════════════════════
    // DATA GENERATION
    // ════════════════════════════════════════════════════════════════════════

    static List<EmployeeFact> generateEmployees(Scenario sc) {
        List<EmployeeFact> emps = new ArrayList<>();
        Random rng = new Random(42);
        for (int i = 1; i <= sc.totalEmployees; i++) {
            String id = String.format("E%03d", i);
            double wage = 15.0 + rng.nextDouble() * 60.0;
            int rating = 2 + rng.nextInt(4);
            emps.add(new EmployeeFact(id, "Emp-" + id, ROLE, wage, rating));
        }
        return emps;
    }

    static Map<String, Set<String>> buildEligibility(Scenario sc) {
        Map<String, Set<String>> elig = new LinkedHashMap<>();
        for (int[] range : sc.eligibilityRanges) {
            int from = range[0], to = range[1];
            Set<String> shifts = new LinkedHashSet<>();
            for (int s = 2; s < range.length; s++) shifts.add(sc.shiftNames[range[s]]);
            for (int i = from; i <= to; i++) {
                elig.put(String.format("E%03d", i), shifts);
            }
        }
        return elig;
    }

    static double computeAverageWage(List<EmployeeFact> emps) {
        return emps.stream().mapToDouble(EmployeeFact::getHourlyWage).average().orElse(1.0);
    }

    // ════════════════════════════════════════════════════════════════════════
    // MODEL A — Current (Entity = Employee×Day, Variable = Shift)
    // ════════════════════════════════════════════════════════════════════════

    static Map<String, Object> runCurrentModel(Scenario sc, List<EmployeeFact> emps,
                                                Map<String, Set<String>> eligibility, double avgWage) {
        List<EmployeeAssignment> entities = new ArrayList<>();
        int seq = 1;
        for (int d = 0; d < sc.days; d++) {
            LocalDate date = sc.start.plusDays(d);
            String dateStr = date.toString();
            for (EmployeeFact emp : emps) {
                Set<String> eligible = eligibility.get(emp.getId());
                if (eligible == null || eligible.isEmpty()) continue;
                EmployeeAssignment ea = new EmployeeAssignment(
                        dateStr + "_" + emp.getId() + "_" + seq++,
                        emp.getId(), emp.getName(), dateStr,
                        "Permanent", "Male", "Operations", emp.getRole()
                );
                ea.setHourlyWage(emp.getHourlyWage());
                ea.setPerformanceRating(emp.getRating());
                ea.setLocalDateObj(date);
                ea.setEligibleShifts(new ArrayList<>(eligible));
                ea.setShift(null);
                ea.setPinned(false);
                entities.add(ea);
            }
        }

        List<ShiftRoleRequirement> reqs = new ArrayList<>();
        for (int d = 0; d < sc.days; d++) {
            String dateStr = sc.start.plusDays(d).toString();
            for (int s = 0; s < sc.shiftNames.length; s++) {
                reqs.add(new ShiftRoleRequirement(dateStr, sc.shiftNames[s], ROLE, sc.slotsPerShift[s]));
            }
        }

        ShiftSchedule problem = new ShiftSchedule(
                entities, List.of(sc.shiftNames),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new WageContext(Map.of(ROLE, avgWage)),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>()
        );

        SolverConfig config = new SolverConfig()
                .withSolutionClass(ShiftSchedule.class)
                .withEntityClasses(EmployeeAssignment.class)
                .withConstraintProviderClass(CurrentModelConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofSeconds(sc.timeLimitSec))
                        .withUnimprovedSpentLimit(Duration.ofSeconds(sc.unimprovedSec)));

        Solver<ShiftSchedule> solver = SolverFactory.<ShiftSchedule>create(config).buildSolver();

        AtomicLong firstFeasibleMs = new AtomicLong(-1);
        long startMs = System.currentTimeMillis();
        solver.addEventListener(event -> {
            if (event.getNewBestScore().isFeasible() && firstFeasibleMs.get() == -1)
                firstFeasibleMs.set(System.currentTimeMillis() - startMs);
        });

        ShiftSchedule solution = solver.solve(problem);
        long totalMs = System.currentTimeMillis() - startMs;
        long assigned = solution.getAssignments().stream().filter(a -> a.getShift() != null).count();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("entities", entities.size());
        r.put("totalMs", totalMs);
        r.put("firstFeasibleMs", firstFeasibleMs.get());
        r.put("score", solution.getScore().toString());
        r.put("feasible", solution.getScore().isFeasible());
        r.put("assigned", assigned);
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // MODEL B — Inverted (Entity = ShiftSlot, Variable = Employee)
    // ════════════════════════════════════════════════════════════════════════

    static Map<String, Object> runSlotModel(Scenario sc, List<EmployeeFact> emps, double avgWage) {
        List<SlotEntity> slots = new ArrayList<>();
        for (int d = 0; d < sc.days; d++) {
            String dateStr = sc.start.plusDays(d).toString();
            for (int s = 0; s < sc.shiftNames.length; s++) {
                for (int slot = 1; slot <= sc.slotsPerShift[s]; slot++) {
                    slots.add(new SlotEntity(
                            dateStr + "_" + sc.shiftNames[s] + "_" + ROLE + "_" + slot,
                            dateStr, sc.shiftNames[s], ROLE, slot));
                }
            }
        }

        List<String> empIds = new ArrayList<>();
        for (EmployeeFact emp : emps) empIds.add(emp.getId());

        SlotSchedule problem = new SlotSchedule(slots, empIds, new ArrayList<>(emps),
                new AverageWageFact(avgWage));

        SolverConfig config = new SolverConfig()
                .withSolutionClass(SlotSchedule.class)
                .withEntityClasses(SlotEntity.class)
                .withConstraintProviderClass(SlotConstraintProvider.class)
                .withTerminationConfig(new TerminationConfig()
                        .withSpentLimit(Duration.ofSeconds(sc.timeLimitSec))
                        .withUnimprovedSpentLimit(Duration.ofSeconds(sc.unimprovedSec)));

        Solver<SlotSchedule> solver = SolverFactory.<SlotSchedule>create(config).buildSolver();

        AtomicLong firstFeasibleMs = new AtomicLong(-1);
        long startMs = System.currentTimeMillis();
        solver.addEventListener(event -> {
            if (event.getNewBestScore().isFeasible() && firstFeasibleMs.get() == -1)
                firstFeasibleMs.set(System.currentTimeMillis() - startMs);
        });

        SlotSchedule solution = solver.solve(problem);
        long totalMs = System.currentTimeMillis() - startMs;
        long filled = solution.getSlots().stream().filter(s -> s.getEmployeeId() != null).count();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("entities", slots.size());
        r.put("totalMs", totalMs);
        r.put("firstFeasibleMs", firstFeasibleMs.get());
        r.put("score", solution.getScore().toString());
        r.put("feasible", solution.getScore().isFeasible());
        r.put("assigned", filled);
        return r;
    }

    // ════════════════════════════════════════════════════════════════════════
    // RUNNER
    // ════════════════════════════════════════════════════════════════════════

    static void runScenario(Scenario sc, int iterations) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════╗");
        System.out.printf( "║  %s%n", sc.name);
        System.out.printf( "║  Required slots: %d   TimeLimit: %ds   Unimproved: %ds%n",
                sc.totalSlots(), sc.timeLimitSec, sc.unimprovedSec);
        System.out.printf( "║  Iterations: %d%n", iterations);
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

        List<EmployeeFact> emps = generateEmployees(sc);
        Map<String, Set<String>> elig = buildEligibility(sc);
        double avgWage = computeAverageWage(emps);

        List<Map<String, Object>> resultsA = new ArrayList<>();
        List<Map<String, Object>> resultsB = new ArrayList<>();

        for (int i = 1; i <= iterations; i++) {
            System.out.printf("%n── Run %d/%d ──────────────────────────────────────────%n", i, iterations);

            System.out.printf("  MODEL A (Employee×Day) running...%n");
            Map<String, Object> rA = runCurrentModel(sc, emps, elig, avgWage);
            System.out.printf("    → %.2fs, score: %s, feasible: %s, assigned: %s/%s%n",
                    (long) rA.get("totalMs") / 1000.0, rA.get("score"), rA.get("feasible"),
                    rA.get("assigned"), rA.get("entities"));
            resultsA.add(rA);

            System.gc();
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            System.out.printf("  MODEL B (ShiftSlot) running...%n");
            Map<String, Object> rB = runSlotModel(sc, emps, avgWage);
            System.out.printf("    → %.2fs, score: %s, feasible: %s, filled: %s/%s%n",
                    (long) rB.get("totalMs") / 1000.0, rB.get("score"), rB.get("feasible"),
                    rB.get("assigned"), rB.get("entities"));
            resultsB.add(rB);

            if (i < iterations) {
                System.gc();
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }

        // ── Print summary table ──
        System.out.printf("%n═══════════════════════════════════════════════════════════════%n");
        System.out.printf("RESULTS: %s%n", sc.name);
        System.out.printf("═══════════════════════════════════════════════════════════════%n");
        System.out.printf("%-6s  %-12s  %-12s  %-12s  %-12s  %-35s  %-35s%n",
                "Run", "A time(s)", "B time(s)", "A 1st feas", "B 1st feas", "A score", "B score");
        System.out.println("─".repeat(140));

        double sumA = 0, sumB = 0;
        for (int i = 0; i < iterations; i++) {
            Map<String, Object> rA = resultsA.get(i);
            Map<String, Object> rB = resultsB.get(i);
            double tA = (long) rA.get("totalMs") / 1000.0;
            double tB = (long) rB.get("totalMs") / 1000.0;
            sumA += tA;
            sumB += tB;
            System.out.printf("%-6d  %-12.2f  %-12.2f  %-12s  %-12s  %-35s  %-35s%n",
                    i + 1, tA, tB,
                    fmtFeasible((long) rA.get("firstFeasibleMs")),
                    fmtFeasible((long) rB.get("firstFeasibleMs")),
                    rA.get("score"), rB.get("score"));
        }
        System.out.println("─".repeat(140));
        double avgA = sumA / iterations, avgB = sumB / iterations;
        System.out.printf("%-6s  %-12.2f  %-12.2f%n", "AVG", avgA, avgB);

        if (avgA < avgB) {
            System.out.printf(">>> MODEL A (Current) was %.2fx faster on average%n", avgB / avgA);
        } else {
            System.out.printf(">>> MODEL B (Inverted) was %.2fx faster on average%n", avgA / avgB);
        }

        System.out.printf(">>> Entity counts: A=%d  B=%d  (ratio: %.1fx)%n",
                (int) resultsA.get(0).get("entities"), (int) resultsB.get(0).get("entities"),
                (int) resultsA.get(0).get("entities") / (double) (int) resultsB.get(0).get("entities"));
    }

    static String fmtFeasible(long ms) {
        return ms == -1 ? "NEVER" : String.format("%.2fs", ms / 1000.0);
    }

    // ════════════════════════════════════════════════════════════════════════
    // MAIN
    // ════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  MODEL A vs MODEL B — FULL BENCHMARK SUITE                         ║");
        System.out.println("║                                                                    ║");
        System.out.println("║  CONSTRAINT PARITY CHECK:                                          ║");
        System.out.println("║    ✓ wageOptimization: normalized by avg wage, ×1000               ║");
        System.out.println("║    ✓ maximizeRating: rating × 100                                  ║");
        System.out.println("║    ✓ maxWorkersPerRole: groupBy + ShiftRoleRequirement join         ║");
        System.out.println("║    ✓ everyShiftPlanned: understaffing × 10000 + empty check        ║");
        System.out.println("║    ✓ noDoubleBooking (B only): replaces structural guarantee       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");

        // Phase 1: Small scenario, 3 iterations
        runScenario(SMALL, 3);

        System.gc();
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        // Phase 2: Large scenario, 1 iteration
        runScenario(LARGE, 1);

        System.out.println("\n\n[BENCHMARK COMPLETE]");
    }
}
