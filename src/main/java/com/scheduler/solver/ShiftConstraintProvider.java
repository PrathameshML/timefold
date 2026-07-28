package com.scheduler.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.scheduler.model.ConstraintConfig;
import com.scheduler.model.EmployeeAssignment;
import com.scheduler.model.EmployeeAvailability;
import com.scheduler.model.ExistingAssignment;
import com.scheduler.model.RatingRequirement;
import com.scheduler.model.RoleRequirement;
import com.scheduler.model.ShiftDefinition;
import com.scheduler.model.ShiftRoleRequirement;

import java.util.List;

public class ShiftConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[]{
                // === V1/V2 constraints (original, untouched — join RoleRequirement) ===
                maxWorkersPerRole(constraintFactory),
                minimumRatingRequirement(constraintFactory),
                everyShiftPlanned(constraintFactory),
                noOverlappingShifts(constraintFactory),
                wageOptimization(constraintFactory),
                maximizeRating(constraintFactory),

                // === Global optimization constraints (join ShiftRoleRequirement/ShiftDefinition/EmployeeAvailability) ===
                // These fire only when the respective global-only facts are present (global endpoint).
                // They are no-ops for V1/V2 which never create these fact types.
                maxWorkersPerRoleGlobal(constraintFactory),
                everyShiftPlannedGlobal(constraintFactory),
                everyShiftPlannedEmpty(constraintFactory),
                shiftAvailabilityGlobal(constraintFactory)
        };
    }
    
    private boolean isConstraintActive(EmployeeAssignment assignment, String constraintName) {
        List<ConstraintConfig> configs = assignment.getActiveConfigs();
        if (configs == null) return true;
        
        for (ConstraintConfig config : configs) {
            if (constraintName.equals(config.getConstraintName())) {
                return config.isEnabled();
            }
        }
        return true;
    }
    
    private double getParamValue(EmployeeAssignment assignment, String constraintName, double defaultVal) {
        List<ConstraintConfig> configs = assignment.getActiveConfigs();
        if (configs == null) return defaultVal;
        
        for (ConstraintConfig config : configs) {
            if (constraintName.equals(config.getConstraintName())) {
                if (config.getParameterValue() != null) {
                    return config.getParameterValue();
                }
            }
        }
        return defaultVal;
    }

    // Record used to wrap 3 keys into 1, avoiding Timefold's 4-variable stream limit (PentaConstraintStream error)
    record ShiftRoleKey(String date, String shift, String position) {}

    // =========================================================================================
    // V1/V2 constraints — ORIGINAL, UNTOUCHED from main branch
    // These join against RoleRequirement (the existing V1/V2 fact type).
    // =========================================================================================

    private Constraint maxWorkersPerRole(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(EmployeeAssignment.class)
                .filter(assignment -> isConstraintActive(assignment, "maxWorkersPerRole") && assignment.getShift() != null)
                .groupBy(a -> new ShiftRoleKey(a.getDate(), a.getShift(), a.getPosition()), ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count())
                .join(RoleRequirement.class,
                        Joiners.equal((key, count) -> key.position(), RoleRequirement::getRoleName))
                .filter((key, count, roleLimit) -> count > roleLimit.getMaxWorkers())
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                        (key, count, roleLimit) -> (long) (count - roleLimit.getMaxWorkers()))
                .asConstraint("maxWorkersPerRole");
    }

    private Constraint minimumRatingRequirement(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(EmployeeAssignment.class)
                .filter(assignment -> isConstraintActive(assignment, "minimumRatingRequirement") && assignment.getShift() != null)
                .join(RatingRequirement.class,
                        Joiners.equal(EmployeeAssignment::getPosition, RatingRequirement::getRoleName))
                .filter((assignment, ratingReq) -> !ratingReq.getAllowedRatings().contains(assignment.getPerformanceRating()))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                        (assignment, ratingReq) -> 1L)
                .asConstraint("minimumRatingRequirement");
    }

    @SuppressWarnings("deprecation")
    private Constraint everyShiftPlanned(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingNullVars(EmployeeAssignment.class)
                .groupBy(EmployeeAssignment::getDate, EmployeeAssignment::getPosition, ai.timefold.solver.core.api.score.stream.ConstraintCollectors.sumLong(a -> a.getShift() != null ? 1L : 0L))
                .join(RoleRequirement.class, Joiners.equal((date, pos, count) -> pos, RoleRequirement::getRoleName))
                .filter((date, pos, count, req) -> count < req.getMaxWorkers())
                .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM, 
                        (date, pos, count, req) -> (long)(req.getMaxWorkers() - count) * 10000L)
                .asConstraint("everyShiftPlanned");
    }

    private Constraint noOverlappingShifts(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(EmployeeAssignment.class)
                .filter(a -> isConstraintActive(a, "noOverlappingShifts") && a.getShift() != null)
                .join(ExistingAssignment.class,
                        Joiners.equal(EmployeeAssignment::getEmployeeId, ExistingAssignment::getEmployeeId),
                        Joiners.equal(EmployeeAssignment::getDate, ExistingAssignment::getDate))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD, (a, existing) -> 1L)
                .asConstraint("noOverlappingShifts");
    }

    private Constraint wageOptimization(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(EmployeeAssignment.class)
                .filter(assignment -> isConstraintActive(assignment, "wageOptimization") && assignment.getShift() != null)
                .join(com.scheduler.model.WageContext.class)
                .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                        (assignment, wageCtx) -> {
                            double wageMultiplier = getParamValue(assignment, "wageOptimization", 1000.0);
                            double averageWage = wageCtx.getAverageWagePerRole().getOrDefault(assignment.getPosition(), 1.0);
                            if (averageWage == 0) averageWage = 1.0;
                            double wageRatio = assignment.getHourlyWage() / averageWage;
                            return (long) (wageRatio * wageMultiplier);
                        })
                .asConstraint("wageOptimization");
    }

    private Constraint maximizeRating(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(EmployeeAssignment.class)
                .filter(assignment -> isConstraintActive(assignment, "maximizeRating") && assignment.getShift() != null)
                .rewardLong(HardMediumSoftLongScore.ONE_SOFT,
                        assignment -> {
                            double ratingMultiplier = getParamValue(assignment, "maximizeRating", 100.0);
                            return (long) (assignment.getPerformanceRating() * ratingMultiplier);
                        })
                .asConstraint("maximizeRating");
    }

    // =========================================================================================
    // Global optimization constraints — validated on test_opt_B branch
    // These join against ShiftRoleRequirement (the global optimization fact type).
    // They are no-ops for V1/V2 solves which don't create ShiftRoleRequirement facts.
    // =========================================================================================

    private Constraint maxWorkersPerRoleGlobal(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(EmployeeAssignment.class)
                .filter(assignment -> isConstraintActive(assignment, "maxWorkersPerRole") && assignment.getShift() != null)
                .groupBy(a -> new ShiftRoleKey(a.getDate(), a.getShift(), a.getPosition()), ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count())
                .join(ShiftRoleRequirement.class,
                        Joiners.equal((key, count) -> key.date(), ShiftRoleRequirement::getDate),
                        Joiners.equal((key, count) -> key.shift(), ShiftRoleRequirement::getShiftName),
                        Joiners.equal((key, count) -> key.position(), ShiftRoleRequirement::getRoleName))
                .filter((key, count, req) -> count > req.getMaxWorkers())
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                        (key, count, req) -> (long) (count - req.getMaxWorkers()))
                .asConstraint("maxWorkersPerRole_global");
    }

    private Constraint everyShiftPlannedGlobal(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(EmployeeAssignment.class)
                .filter(assignment -> isConstraintActive(assignment, "everyShiftPlanned") && assignment.getShift() != null)
                .groupBy(a -> new ShiftRoleKey(a.getDate(), a.getShift(), a.getPosition()), ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count())
                .join(ShiftRoleRequirement.class,
                        Joiners.equal((key, count) -> key.date(), ShiftRoleRequirement::getDate),
                        Joiners.equal((key, count) -> key.shift(), ShiftRoleRequirement::getShiftName),
                        Joiners.equal((key, count) -> key.position(), ShiftRoleRequirement::getRoleName))
                .filter((key, count, req) -> count < req.getMaxWorkers())
                .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM,
                        (key, count, req) -> (long) (req.getMaxWorkers() - count) * 10000L)
                .asConstraint("everyShiftPlanned_global");
    }

    /**
     * Companion to everyShiftPlannedGlobal — handles the edge case where NO employee is assigned
     * to a (date, shift, role) triple at all. everyShiftPlannedGlobal only fires when at least one
     * assignment exists (due to groupBy + count > 0). This constraint catches the zero case
     * by starting from ShiftRoleRequirement facts and checking ifNotExists.
     *
     * Validated on test_opt_B — confirmed to correctly penalize understaffing at 0 assigned.
     * No-op for V1/V2 which don't create ShiftRoleRequirement facts.
     */
    private Constraint everyShiftPlannedEmpty(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(ShiftRoleRequirement.class)
                .ifNotExists(EmployeeAssignment.class,
                        Joiners.equal(ShiftRoleRequirement::getDate, EmployeeAssignment::getDate),
                        Joiners.equal(ShiftRoleRequirement::getShiftName, EmployeeAssignment::getShift),
                        Joiners.equal(ShiftRoleRequirement::getRoleName, EmployeeAssignment::getPosition))
                .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM,
                        req -> (long) req.getMaxWorkers() * 10000L)
                .asConstraint("everyShiftPlanned_empty");
    }

    /**
     * Global-only: penalizes assigning an employee to a shift when their availability
     * does NOT fully cover the shift's time window.
     *
     * Joins: EmployeeAssignment (shift assigned) → ShiftDefinition (shift's time window)
     *        → EmployeeAvailability (employee's availability for that date).
     *
     * Rule: FULL COVERAGE required. An employee available 08:00-12:00 is NOT eligible
     * for an 08:00-16:00 shift, even though there's overlap. Only if their available
     * window fully contains [shiftStart, shiftEnd] is the assignment penalty-free.
     *
     * No-op for V1/V2 which don't create EmployeeAvailability or ShiftDefinition facts.
     * (If no EmployeeAvailability fact exists for an employee+date, the join doesn't
     * match and no penalty is applied — the employee is assumed fully available.)
     */
    private Constraint shiftAvailabilityGlobal(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(EmployeeAssignment.class)
                .filter(a -> a.getShift() != null)
                .join(ShiftDefinition.class,
                        Joiners.equal(EmployeeAssignment::getShift, ShiftDefinition::getShiftName))
                .join(EmployeeAvailability.class,
                        Joiners.equal((a, sd) -> a.getEmployeeId(), EmployeeAvailability::getEmployeeId),
                        Joiners.equal((a, sd) -> a.getDate(), EmployeeAvailability::getDate))
                .filter((a, sd, avail) -> !avail.coversShift(sd.getStartLocalTime(), sd.getEndLocalTime()))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD, (a, sd, avail) -> 1L)
                .asConstraint("shiftAvailability_global");
    }
}
