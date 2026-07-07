package com.scheduler.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.scheduler.model.ConstraintConfig;
import com.scheduler.model.EmployeeAssignment;
import com.scheduler.model.RatingRequirement;
import com.scheduler.model.RoleRequirement;

import java.util.List;
import java.util.Map;

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

    private Constraint maxWorkersPerRole(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(EmployeeAssignment.class)
                .filter(assignment -> isConstraintActive(assignment, "maxWorkersPerRole") && assignment.getShift() != null)
                .groupBy(EmployeeAssignment::getShift, EmployeeAssignment::getPosition, ai.timefold.solver.core.api.score.stream.ConstraintCollectors.count())
                .join(RoleRequirement.class,
                        Joiners.equal((shift, role, count) -> role, RoleRequirement::getRoleName))
                .filter((shift, role, count, roleLimit) -> count > roleLimit.getMaxWorkers())
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                        (shift, role, count, roleLimit) -> (long) (count - roleLimit.getMaxWorkers()))
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
        return constraintFactory.forEachUniquePair(EmployeeAssignment.class,
                        Joiners.equal(EmployeeAssignment::getEmployeeId),
                        Joiners.equal(EmployeeAssignment::getDate))
                .filter((a1, a2) -> isConstraintActive(a1, "noOverlappingShifts") && a1.getShift() != null && a2.getShift() != null &&
                        !a1.getShift().equals(a2.getShift()))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD, (a1, a2) -> 1L)
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
