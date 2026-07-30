package com.scheduler.benchmark;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

/**
 * INVERTED MODEL — Entity = required shift slot.
 * The solver assigns an employee (String) to fill each slot.
 * One SlotEntity per (date, shiftName, role, slotIndex).
 */
@PlanningEntity
public class SlotEntity {

    @PlanningId
    private String id;

    private String date;
    private String shiftName;
    private String role;
    private int slotIndex;

    @PlanningVariable(valueRangeProviderRefs = "employeeRange", allowsUnassigned = true)
    private String employeeId;

    public SlotEntity() {}

    public SlotEntity(String id, String date, String shiftName, String role, int slotIndex) {
        this.id = id;
        this.date = date;
        this.shiftName = shiftName;
        this.role = role;
        this.slotIndex = slotIndex;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getShiftName() { return shiftName; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public int getSlotIndex() { return slotIndex; }
    public void setSlotIndex(int slotIndex) { this.slotIndex = slotIndex; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    @Override
    public String toString() {
        return date + "/" + shiftName + "/" + role + "#" + slotIndex + "→" + employeeId;
    }
}
