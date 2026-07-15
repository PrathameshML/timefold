package com.scheduler.model;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;

import java.util.List;

@PlanningSolution
public class ShiftSchedule {
    @PlanningEntityCollectionProperty
    private List<EmployeeAssignment> assignments;

    @ValueRangeProvider(id = "shiftRange")
    private List<String> shiftTypes;

    @ProblemFactCollectionProperty
    private List<RoleRequirement> roleRequirements;

    @ProblemFactCollectionProperty
    private List<RatingRequirement> ratingRequirements;

    @ProblemFactCollectionProperty
    private List<ConstraintConfig> constraintConfigs;

    @ProblemFactCollectionProperty
    private List<ExistingAssignment> existingAssignments;

    @ProblemFactProperty
    private WageContext wageContext;

    @PlanningScore
    private HardMediumSoftLongScore score;

    private String requestedShiftName;

    public ShiftSchedule() {}

    public ShiftSchedule(List<EmployeeAssignment> assignments, List<String> shiftTypes,
                         List<RoleRequirement> roleRequirements,
                         List<RatingRequirement> ratingRequirements,
                         List<ConstraintConfig> constraintConfigs,
                         WageContext wageContext,
                         List<ExistingAssignment> existingAssignments) {
        this.assignments = assignments;
        this.shiftTypes = shiftTypes;
        this.roleRequirements = roleRequirements;
        this.ratingRequirements = ratingRequirements;
        this.constraintConfigs = constraintConfigs;
        this.wageContext = wageContext;
        this.existingAssignments = existingAssignments;
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
    public HardMediumSoftLongScore getScore() { return score; }
    public void setScore(HardMediumSoftLongScore score) { this.score = score; }
    public String getRequestedShiftName() { return requestedShiftName; }
    public void setRequestedShiftName(String requestedShiftName) { this.requestedShiftName = requestedShiftName; }
}
