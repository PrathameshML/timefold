package com.scheduler.model;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;

import java.util.ArrayList;
import java.util.List;

@PlanningSolution
public class ShiftSchedule {
    @PlanningEntityCollectionProperty
    private List<EmployeeAssignment> assignments;

    // No longer a @ValueRangeProvider — the VRP has moved to EmployeeAssignment.eligibleShifts
    // (entity-level, per-entity value range). This field is retained as a convenience
    // for service-layer logic (e.g., response filtering) but is NOT annotated.
    private List<String> shiftTypes;

    @ProblemFactCollectionProperty
    private List<RoleRequirement> roleRequirements;

    @ProblemFactCollectionProperty
    private List<RatingRequirement> ratingRequirements;

    @ProblemFactCollectionProperty
    private List<ConstraintConfig> constraintConfigs;

    @ProblemFactCollectionProperty
    private List<ExistingAssignment> existingAssignments;

    @ProblemFactCollectionProperty
    private List<ShiftRoleRequirement> shiftRoleRequirements;

    @ProblemFactCollectionProperty
    private List<ShiftDefinition> shiftDefinitions;

    @ProblemFactCollectionProperty
    private List<EmployeeAvailability> employeeAvailabilities;

    @ProblemFactProperty
    private WageContext wageContext;

    @PlanningScore
    private HardMediumSoftLongScore score;

    private String requestedShiftName;

    public ShiftSchedule() {}

    // Backward-compatible constructor (V1/V2 callers) — delegates to the full constructor
    // with empty shiftRoleRequirements, shiftDefinitions, and employeeAvailabilities lists.
    public ShiftSchedule(List<EmployeeAssignment> assignments, List<String> shiftTypes,
                         List<RoleRequirement> roleRequirements,
                         List<RatingRequirement> ratingRequirements,
                         List<ConstraintConfig> constraintConfigs,
                         WageContext wageContext,
                         List<ExistingAssignment> existingAssignments) {
        this(assignments, shiftTypes, roleRequirements, ratingRequirements, constraintConfigs,
             wageContext, existingAssignments, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    // Full constructor (global optimization callers)
    public ShiftSchedule(List<EmployeeAssignment> assignments, List<String> shiftTypes,
                         List<RoleRequirement> roleRequirements,
                         List<RatingRequirement> ratingRequirements,
                         List<ConstraintConfig> constraintConfigs,
                         WageContext wageContext,
                         List<ExistingAssignment> existingAssignments,
                         List<ShiftRoleRequirement> shiftRoleRequirements,
                         List<ShiftDefinition> shiftDefinitions,
                         List<EmployeeAvailability> employeeAvailabilities) {
        this.assignments = assignments;
        this.shiftTypes = shiftTypes;
        this.roleRequirements = roleRequirements;
        this.ratingRequirements = ratingRequirements;
        this.constraintConfigs = constraintConfigs;
        this.wageContext = wageContext;
        this.existingAssignments = existingAssignments;
        this.shiftRoleRequirements = shiftRoleRequirements;
        this.shiftDefinitions = shiftDefinitions;
        this.employeeAvailabilities = employeeAvailabilities;
    }

    public List<EmployeeAssignment> getAssignments() { return assignments; }
    public void setAssignments(List<EmployeeAssignment> assignments) { this.assignments = assignments; }
    public List<String> getShiftTypes() { return shiftTypes; }
    public void setShiftTypes(List<String> shiftTypes) { this.shiftTypes = shiftTypes; }
    public List<RoleRequirement> getRoleRequirements() { return roleRequirements; }
    public void setRoleRequirements(List<RoleRequirement> roleRequirements) { this.roleRequirements = roleRequirements; }
    public List<RatingRequirement> getRatingRequirements() { return ratingRequirements; }
    public void setRatingRequirements(List<RatingRequirement> ratingRequirements) { this.ratingRequirements = ratingRequirements; }
    public WageContext getWageContext() { return wageContext; }
    public void setWageContext(WageContext wageContext) { this.wageContext = wageContext; }
    public List<ConstraintConfig> getConstraintConfigs() { return constraintConfigs; }
    public void setConstraintConfigs(List<ConstraintConfig> constraintConfigs) { this.constraintConfigs = constraintConfigs; }
    public List<ExistingAssignment> getExistingAssignments() { return existingAssignments; }
    public void setExistingAssignments(List<ExistingAssignment> existingAssignments) { this.existingAssignments = existingAssignments; }
    public List<ShiftRoleRequirement> getShiftRoleRequirements() { return shiftRoleRequirements; }
    public void setShiftRoleRequirements(List<ShiftRoleRequirement> shiftRoleRequirements) { this.shiftRoleRequirements = shiftRoleRequirements; }
    public List<ShiftDefinition> getShiftDefinitions() { return shiftDefinitions; }
    public void setShiftDefinitions(List<ShiftDefinition> shiftDefinitions) { this.shiftDefinitions = shiftDefinitions; }
    public List<EmployeeAvailability> getEmployeeAvailabilities() { return employeeAvailabilities; }
    public void setEmployeeAvailabilities(List<EmployeeAvailability> employeeAvailabilities) { this.employeeAvailabilities = employeeAvailabilities; }
    public HardMediumSoftLongScore getScore() { return score; }
    public void setScore(HardMediumSoftLongScore score) { this.score = score; }
    public String getRequestedShiftName() { return requestedShiftName; }
    public void setRequestedShiftName(String requestedShiftName) { this.requestedShiftName = requestedShiftName; }
}
