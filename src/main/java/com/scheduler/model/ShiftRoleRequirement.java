package com.scheduler.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Problem fact representing the staffing requirement for a specific (date, shift, role) triple.
 * Validated on test_opt_B branch — the constraints everyShiftPlanned, everyShiftPlannedEmpty,
 * and maxWorkersPerRole all join against this fact class by (date, shiftName, roleName).
 *
 * 4-field version (includes date) — matches the real validated constraint code,
 * NOT the 3-field version from the earlier plan draft.
 */
public class ShiftRoleRequirement implements Serializable {
    private String date;
    private String shiftName;
    private String roleName;
    private int maxWorkers;

    public ShiftRoleRequirement() {}

    public ShiftRoleRequirement(String date, String shiftName, String roleName, int maxWorkers) {
        this.date = date;
        this.shiftName = shiftName;
        this.roleName = roleName;
        this.maxWorkers = maxWorkers;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getShiftName() { return shiftName; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public int getMaxWorkers() { return maxWorkers; }
    public void setMaxWorkers(int maxWorkers) { this.maxWorkers = maxWorkers; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShiftRoleRequirement that = (ShiftRoleRequirement) o;
        return maxWorkers == that.maxWorkers &&
               Objects.equals(date, that.date) &&
               Objects.equals(shiftName, that.shiftName) &&
               Objects.equals(roleName, that.roleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, shiftName, roleName, maxWorkers);
    }

    @Override
    public String toString() {
        return "ShiftRoleRequirement{" +
               "date='" + date + '\'' +
               ", shiftName='" + shiftName + '\'' +
               ", roleName='" + roleName + '\'' +
               ", maxWorkers=" + maxWorkers +
               '}';
    }
}
