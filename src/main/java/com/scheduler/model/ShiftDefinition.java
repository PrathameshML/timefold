package com.scheduler.model;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Problem fact defining a shift's time window.
 * Used by the global-optimization availability constraint to check whether
 * an employee's available hours fully cover a specific shift's time range.
 * Also used by the service layer to persist per-shift start/end times.
 *
 * Stores both String and LocalTime representations for convenience:
 *  - String versions (startTime/endTime) for JSON serialization and display
 *  - LocalTime versions (startLocalTime/endLocalTime) for constraint comparisons
 */
public class ShiftDefinition implements Serializable {
    private String shiftName;        // e.g. "Morning"
    private String startTime;        // e.g. "08:00"
    private String endTime;          // e.g. "16:00"
    private LocalTime startLocalTime;
    private LocalTime endLocalTime;

    public ShiftDefinition() {}

    public ShiftDefinition(String shiftName, String startTime, String endTime) {
        this.shiftName = shiftName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.startLocalTime = LocalTime.parse(startTime);
        this.endLocalTime = LocalTime.parse(endTime);
    }

    public String getShiftName() { return shiftName; }
    public void setShiftName(String shiftName) { this.shiftName = shiftName; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) {
        this.startTime = startTime;
        this.startLocalTime = (startTime != null) ? LocalTime.parse(startTime) : null;
    }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) {
        this.endTime = endTime;
        this.endLocalTime = (endTime != null) ? LocalTime.parse(endTime) : null;
    }
    public LocalTime getStartLocalTime() { return startLocalTime; }
    public LocalTime getEndLocalTime() { return endLocalTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShiftDefinition that = (ShiftDefinition) o;
        return Objects.equals(shiftName, that.shiftName) &&
               Objects.equals(startTime, that.startTime) &&
               Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shiftName, startTime, endTime);
    }

    @Override
    public String toString() {
        return "ShiftDefinition{" +
               "shiftName='" + shiftName + '\'' +
               ", startTime='" + startTime + '\'' +
               ", endTime='" + endTime + '\'' +
               '}';
    }
}
