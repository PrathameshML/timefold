package com.scheduler.model;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;

import java.util.ArrayList;
import java.util.List;

/**
 * Planning Solution for the slot-based Global-V2 solver.
 *
 * Separate from ShiftSchedule (used by V1/V2) to avoid touching stable code.
 * Entity collection is List<ShiftSlot> instead of List<EmployeeAssignment>.
 * Value range is per-entity (on ShiftSlot.eligibleEmployeeIds), not solution-level.
 */
@PlanningSolution
public class ShiftScheduleSlot {

    @PlanningEntityCollectionProperty
    private List<ShiftSlot> slots;

    @ProblemFactCollectionProperty
    private List<EmployeeFact> employees;

    @ProblemFactCollectionProperty
    private List<ConstraintConfig> constraintConfigs;

    @ProblemFactCollectionProperty
    private List<ExistingAssignment> existingAssignments;

    @ProblemFactCollectionProperty
    private List<RatingRequirement> ratingRequirements;

    @ProblemFactCollectionProperty
    private List<ShiftDefinition> shiftDefinitions;

    @ProblemFactCollectionProperty
    private List<EmployeeAvailability> employeeAvailabilities;

    @ProblemFactProperty
    private WageContext wageContext;

    @PlanningScore
    private HardMediumSoftLongScore score;

    public ShiftScheduleSlot() {}

    public ShiftScheduleSlot(List<ShiftSlot> slots,
                             List<EmployeeFact> employees,
                             List<ConstraintConfig> constraintConfigs,
                             List<ExistingAssignment> existingAssignments,
                             List<RatingRequirement> ratingRequirements,
                             List<ShiftDefinition> shiftDefinitions,
                             List<EmployeeAvailability> employeeAvailabilities,
                             WageContext wageContext) {
        this.slots = slots;
        this.employees = employees;
        this.constraintConfigs = constraintConfigs;
        this.existingAssignments = existingAssignments;
        this.ratingRequirements = ratingRequirements;
        this.shiftDefinitions = shiftDefinitions;
        this.employeeAvailabilities = employeeAvailabilities;
        this.wageContext = wageContext;
    }

    public List<ShiftSlot> getSlots() { return slots; }
    public void setSlots(List<ShiftSlot> slots) { this.slots = slots; }

    public List<EmployeeFact> getEmployees() { return employees; }
    public void setEmployees(List<EmployeeFact> employees) { this.employees = employees; }

    public List<ConstraintConfig> getConstraintConfigs() { return constraintConfigs; }
    public void setConstraintConfigs(List<ConstraintConfig> constraintConfigs) { this.constraintConfigs = constraintConfigs; }

    public List<ExistingAssignment> getExistingAssignments() { return existingAssignments; }
    public void setExistingAssignments(List<ExistingAssignment> existingAssignments) { this.existingAssignments = existingAssignments; }

    public List<RatingRequirement> getRatingRequirements() { return ratingRequirements; }
    public void setRatingRequirements(List<RatingRequirement> ratingRequirements) { this.ratingRequirements = ratingRequirements; }

    public List<ShiftDefinition> getShiftDefinitions() { return shiftDefinitions; }
    public void setShiftDefinitions(List<ShiftDefinition> shiftDefinitions) { this.shiftDefinitions = shiftDefinitions; }

    public List<EmployeeAvailability> getEmployeeAvailabilities() { return employeeAvailabilities; }
    public void setEmployeeAvailabilities(List<EmployeeAvailability> employeeAvailabilities) { this.employeeAvailabilities = employeeAvailabilities; }

    public WageContext getWageContext() { return wageContext; }
    public void setWageContext(WageContext wageContext) { this.wageContext = wageContext; }

    public HardMediumSoftLongScore getScore() { return score; }
    public void setScore(HardMediumSoftLongScore score) { this.score = score; }
}
