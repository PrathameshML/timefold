package com.scheduler.benchmark;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;

import java.util.List;

/**
 * INVERTED MODEL solution — entities are required slots, value range is employee IDs.
 */
@PlanningSolution
public class SlotSchedule {

    @PlanningEntityCollectionProperty
    private List<SlotEntity> slots;

    @ValueRangeProvider(id = "employeeRange")
    private List<String> employeeIds;

    @ProblemFactCollectionProperty
    private List<EmployeeFact> employees;

    @ProblemFactProperty
    private AverageWageFact averageWageFact;

    @PlanningScore
    private HardMediumSoftLongScore score;

    public SlotSchedule() {}

    public SlotSchedule(List<SlotEntity> slots, List<String> employeeIds,
                        List<EmployeeFact> employees, AverageWageFact averageWageFact) {
        this.slots = slots;
        this.employeeIds = employeeIds;
        this.employees = employees;
        this.averageWageFact = averageWageFact;
    }

    public List<SlotEntity> getSlots() { return slots; }
    public void setSlots(List<SlotEntity> slots) { this.slots = slots; }
    public List<String> getEmployeeIds() { return employeeIds; }
    public void setEmployeeIds(List<String> employeeIds) { this.employeeIds = employeeIds; }
    public List<EmployeeFact> getEmployees() { return employees; }
    public void setEmployees(List<EmployeeFact> employees) { this.employees = employees; }
    public AverageWageFact getAverageWageFact() { return averageWageFact; }
    public void setAverageWageFact(AverageWageFact averageWageFact) { this.averageWageFact = averageWageFact; }
    public HardMediumSoftLongScore getScore() { return score; }
    public void setScore(HardMediumSoftLongScore score) { this.score = score; }
}
