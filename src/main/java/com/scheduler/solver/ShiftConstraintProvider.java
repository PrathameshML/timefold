package com.scheduler.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.scheduler.model.ConstraintConfig;
import com.scheduler.model.EmployeeAssignment;
import com.scheduler.model.ExistingAssignment;
import com.scheduler.model.RatingRequirement;
import com.scheduler.model.RoleRequirement;

import java.util.List;

public class ShiftConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[]{
                maxWorkersPerRole(constraintFactory),
                minimumRatingRequirement(constraintFactory),
                everyShiftPlanned(constraintFactory),
                noOverlappingShifts(constraintFactory),
                wageOptimization(constraintFactory),
                maximizeRating(constraintFactory)
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
}
