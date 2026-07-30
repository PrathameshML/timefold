package com.scheduler.benchmark;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

import com.scheduler.model.EmployeeAssignment;
import com.scheduler.model.ShiftRoleRequirement;
import com.scheduler.model.WageContext;

/**
 * Constraint provider for the CURRENT (employee-based) model.
 *
 * FIXED to exactly match production ShiftConstraintProvider.java:
 *   - wageOptimization now joins WageContext and normalizes by averageWagePerRole
 *     (production lines 120-132)
 *   - maximizeRating uses rating * 100 (production lines 135-143)
 *   - maxWorkersPerRoleGlobal matches production lines 152-163
 *   - everyShiftPlannedGlobal matches production lines 166-177
 *   - everyShiftPlannedEmpty matches production lines 189-197
 *
 * Intentionally omitted (no-ops in this benchmark):
 *   - noOverlappingShifts: no ExistingAssignment facts provided
 *   - minimumRatingRequirement: all employees meet rating >= 3
 *   - isConstraintActive checks: all constraints are active (no ConstraintConfig overrides)
 *   - V1/V2 constraints (maxWorkersPerRole, everyShiftPlanned): no RoleRequirement facts
 */
public class CurrentModelConstraintProvider implements ConstraintProvider {

    record ShiftRoleKey(String date, String shift, String position) {}

    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[]{
                maxWorkersPerRoleGlobal(cf),
                everyShiftPlannedGlobal(cf),
                everyShiftPlannedEmpty(cf),
                wageOptimization(cf),
                maximizeRating(cf)
        };
    }

    // HARD: count per (date, shift, role) must not exceed maxWorkers
    // Matches production ShiftConstraintProvider.java lines 152-163
    private Constraint maxWorkersPerRoleGlobal(ConstraintFactory cf) {
        return cf.forEach(EmployeeAssignment.class)
                .filter(a -> a.getShift() != null)
                .groupBy(a -> new ShiftRoleKey(a.getDate(), a.getShift(), a.getPosition()),
                         ConstraintCollectors.count())
                .join(ShiftRoleRequirement.class,
                        Joiners.equal((key, count) -> key.date(), ShiftRoleRequirement::getDate),
                        Joiners.equal((key, count) -> key.shift(), ShiftRoleRequirement::getShiftName),
                        Joiners.equal((key, count) -> key.position(), ShiftRoleRequirement::getRoleName))
                .filter((key, count, req) -> count > req.getMaxWorkers())
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                        (key, count, req) -> (long) (count - req.getMaxWorkers()))
                .asConstraint("maxWorkersPerRole_global");
    }

    // MEDIUM: penalize understaffing. Matches production lines 166-177
    private Constraint everyShiftPlannedGlobal(ConstraintFactory cf) {
        return cf.forEach(EmployeeAssignment.class)
                .filter(a -> a.getShift() != null)
                .groupBy(a -> new ShiftRoleKey(a.getDate(), a.getShift(), a.getPosition()),
                         ConstraintCollectors.count())
                .join(ShiftRoleRequirement.class,
                        Joiners.equal((key, count) -> key.date(), ShiftRoleRequirement::getDate),
                        Joiners.equal((key, count) -> key.shift(), ShiftRoleRequirement::getShiftName),
                        Joiners.equal((key, count) -> key.position(), ShiftRoleRequirement::getRoleName))
                .filter((key, count, req) -> count < req.getMaxWorkers())
                .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM,
                        (key, count, req) -> (long) (req.getMaxWorkers() - count) * 10000L)
                .asConstraint("everyShiftPlanned_global");
    }

    // MEDIUM: penalize completely empty shift-role-date triples. Matches production lines 189-197
    private Constraint everyShiftPlannedEmpty(ConstraintFactory cf) {
        return cf.forEach(ShiftRoleRequirement.class)
                .ifNotExists(EmployeeAssignment.class,
                        Joiners.equal(ShiftRoleRequirement::getDate, EmployeeAssignment::getDate),
                        Joiners.equal(ShiftRoleRequirement::getShiftName, EmployeeAssignment::getShift),
                        Joiners.equal(ShiftRoleRequirement::getRoleName, EmployeeAssignment::getPosition))
                .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM,
                        req -> (long) req.getMaxWorkers() * 10000L)
                .asConstraint("everyShiftPlanned_empty");
    }

    // SOFT: Prefer lower-wage employees, normalized by average wage per role.
    // Matches production lines 120-132 EXACTLY:
    //   wageRatio = hourlyWage / averageWageForRole
    //   penalty = wageRatio * 1000 (wageMultiplier default)
    private Constraint wageOptimization(ConstraintFactory cf) {
        return cf.forEach(EmployeeAssignment.class)
                .filter(a -> a.getShift() != null)
                .join(WageContext.class)
                .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                        (a, wageCtx) -> {
                            double averageWage = wageCtx.getAverageWagePerRole()
                                    .getOrDefault(a.getPosition(), 1.0);
                            if (averageWage == 0) averageWage = 1.0;
                            double wageRatio = a.getHourlyWage() / averageWage;
                            return (long) (wageRatio * 1000.0);
                        })
                .asConstraint("wageOptimization");
    }

    // SOFT: Reward higher-rated employees. Matches production lines 135-143
    //   reward = rating * 100 (ratingMultiplier default)
    private Constraint maximizeRating(ConstraintFactory cf) {
        return cf.forEach(EmployeeAssignment.class)
                .filter(a -> a.getShift() != null)
                .rewardLong(HardMediumSoftLongScore.ONE_SOFT,
                        a -> (long) (a.getPerformanceRating() * 100))
                .asConstraint("maximizeRating");
    }
}
