package com.scheduler.model;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import java.util.List;

/**
 * Slot-based Planning Entity for the Global-V2 solver.
 *
 * One instance per required slot: (date × shiftName × role × slotIndex).
 * The solver assigns an employeeId to fill each slot.
 *
 * Value range is per-entity: each slot carries its own list of eligible
 * employee IDs, pre-filtered by role during entity creation. This prevents
 * the solver from even considering a Nurse for a Developer slot.
 *
 * Used ONLY by the /shifts/assign-global-v2 endpoint.
 * V1/V2 endpoints continue to use EmployeeAssignment.
 */
@PlanningEntity
public class ShiftSlot {

    @PlanningId
    private String id;

    private String date;         // ISO date string, e.g. "2026-08-01"
    private String shiftName;    // e.g. "Morning Shift"
    private String role;         // e.g. "Developer"
    private int slotIndex;       // 1, 2, 3... (which of the N required slots for this role)

    // Per-entity value range: only employees whose role matches this slot's role
    private List<String> eligibleEmployeeIds;

    @PlanningVariable(valueRangeProviderRefs = "employeeRange", allowsUnassigned = true)
    private String employeeId;

    @PlanningPin
    private boolean pinned = false;

    public ShiftSlot() {}

    public ShiftSlot(String id, String date, String shiftName, String role, int slotIndex) {
        this.id = id;
        this.date = date;
        this.shiftName = shiftName;
        this.role = role;
        this.slotIndex = slotIndex;
    }

    // Getters and setters
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

    @ValueRangeProvider(id = "employeeRange")
    public List<String> getEligibleEmployeeIds() { return eligibleEmployeeIds; }
    public void setEligibleEmployeeIds(List<String> eligibleEmployeeIds) { this.eligibleEmployeeIds = eligibleEmployeeIds; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    @Override
    public String toString() {
        return date + "/" + shiftName + "/" + role + "#" + slotIndex + "→" + employeeId;
    }
}
