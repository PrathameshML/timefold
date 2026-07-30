package com.scheduler.benchmark;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

/**
 * Constraints for the INVERTED (slot-based) model.
 *
 * Mirrors production constraints with equivalent logic:
 *   HARD:  noDoubleBooking   — same employee cannot fill two slots on the same day
 *                               (current model gets this structurally for free)
 *   MEDIUM: everySlotFilled  — penalize unassigned slots (equiv to everyShiftPlanned_global)
 *   SOFT:  wageOptimization  — prefer cheaper employees, normalized by avg wage
 *   SOFT:  maximizeRating    — reward higher-rated employees
 *
 * Uses AverageWageFact (single problem fact) to normalize wages the same way
 * production's WageContext does.
 *
 * Intentionally omitted (not applicable or no-ops):
 *   - maxWorkersPerRole: slot count = maxWorkers, structurally enforced
 *   - minimumRatingRequirement: all employees meet rating >= 3
 */
public class SlotConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[]{
                noDoubleBooking(cf),
                everySlotFilled(cf),
                wageOptimization(cf),
                maximizeRating(cf)
        };
    }

    /**
     * HARD: An employee cannot be assigned to two different slots on the same day.
     * This is the constraint the current model gets "for free" via structure.
     */
    private Constraint noDoubleBooking(ConstraintFactory cf) {
        return cf.forEach(SlotEntity.class)
                .filter(s -> s.getEmployeeId() != null)
                .join(SlotEntity.class,
                        Joiners.equal(SlotEntity::getEmployeeId),
                        Joiners.equal(SlotEntity::getDate),
                        Joiners.lessThan(SlotEntity::getId))
                .penalizeLong(HardMediumSoftLongScore.ONE_HARD, (s1, s2) -> 1L)
                .asConstraint("noDoubleBooking");
    }

    /**
     * MEDIUM: Every slot should be filled. Penalize unassigned slots.
     * Weight = 10000 per missing slot (matches production everyShiftPlanned_global).
     */
    @SuppressWarnings("deprecation")
    private Constraint everySlotFilled(ConstraintFactory cf) {
        return cf.forEachIncludingNullVars(SlotEntity.class)
                .filter(s -> s.getEmployeeId() == null)
                .penalizeLong(HardMediumSoftLongScore.ONE_MEDIUM, s -> 10000L)
                .asConstraint("everySlotFilled");
    }

    /**
     * SOFT: Prefer lower-wage employees, normalized by average wage.
     * Matches production: penalty = (hourlyWage / averageWage) * 1000
     * Joins AverageWageFact to get the average.
     */
    private Constraint wageOptimization(ConstraintFactory cf) {
        return cf.forEach(SlotEntity.class)
                .filter(s -> s.getEmployeeId() != null)
                .join(EmployeeFact.class,
                        Joiners.equal(SlotEntity::getEmployeeId, EmployeeFact::getId))
                .join(AverageWageFact.class)
                .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                        (slot, emp, avgFact) -> {
                            double averageWage = avgFact.getAverageWage();
                            if (averageWage == 0) averageWage = 1.0;
                            double wageRatio = emp.getHourlyWage() / averageWage;
                            return (long) (wageRatio * 1000.0);
                        })
                .asConstraint("wageOptimization");
    }

    /**
     * SOFT: Reward higher-rated employees.
     * reward = rating * 100 (matches production ratingMultiplier default)
     */
    private Constraint maximizeRating(ConstraintFactory cf) {
        return cf.forEach(SlotEntity.class)
                .filter(s -> s.getEmployeeId() != null)
                .join(EmployeeFact.class,
                        Joiners.equal(SlotEntity::getEmployeeId, EmployeeFact::getId))
                .rewardLong(HardMediumSoftLongScore.ONE_SOFT,
                        (slot, emp) -> (long) (emp.getRating() * 100))
                .asConstraint("maximizeRating");
    }
}
