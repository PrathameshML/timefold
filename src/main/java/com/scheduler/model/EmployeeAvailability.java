package com.scheduler.model;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Problem fact representing an employee's availability override for a specific date.
 * Used ONLY by global-optimization constraints — V1/V2 handle availability pre-solve
 * via EmployeeInfo.isAvailableForShift() and never create these facts.
 *
 * Statuses:
 *   - "available"   → employee is fully available for any shift this date (default if no fact exists)
 *   - "unavailable" → employee cannot work this date at all
 *   - "partial"     → employee can only work during the availableFrom–availableTo window
 *
 * The constraint checks: for the assigned shift's time window (from ShiftDefinition),
 * does this employee's availability FULLY COVER the shift? Partial overlap does NOT count.
 */
public class EmployeeAvailability implements Serializable {
    private String employeeId;
    private String date;             // ISO date string, e.g. "2026-08-01"
    private String status;           // "available", "unavailable", or "partial"
    private LocalTime availableFrom; // only meaningful for "partial"
    private LocalTime availableTo;   // only meaningful for "partial"

    public EmployeeAvailability() {}

    public EmployeeAvailability(String employeeId, String date, String status,
                                 LocalTime availableFrom, LocalTime availableTo) {
        this.employeeId = employeeId;
        this.date = date;
        this.status = status;
        this.availableFrom = availableFrom;
        this.availableTo = availableTo;
    }

    /**
     * Checks if this availability entry fully covers the given shift time window.
     * Rule: FULL COVERAGE required — partial overlap does NOT count as available.
     *
     * @param shiftStart the shift's start time
     * @param shiftEnd   the shift's end time
     * @return true if the employee can work the entire shift
     */
    public boolean coversShift(LocalTime shiftStart, LocalTime shiftEnd) {
        if ("unavailable".equalsIgnoreCase(status)) {
            return false;
        }
        if ("partial".equalsIgnoreCase(status)) {
            if (availableFrom == null && availableTo == null) {
                return false; // No window specified → treat as unavailable
            }
            LocalTime effectiveFrom = (availableFrom != null) ? availableFrom : LocalTime.MIN;
            LocalTime effectiveTo = (availableTo != null) ? availableTo : LocalTime.MAX;
            
            // Full coverage: available window must fully contain the shift.
            // If shift ends at 00:00, it means 24:00 (end of day).
            // If effectiveTo is 00:00, it also means 24:00 (end of day).
            boolean coversStart = !effectiveFrom.isAfter(shiftStart);
            
            boolean coversEnd;
            if (shiftEnd.equals(LocalTime.MIDNIGHT)) {
                coversEnd = effectiveTo.equals(LocalTime.MIDNIGHT) || effectiveTo.equals(LocalTime.MAX);
            } else if (effectiveTo.equals(LocalTime.MIDNIGHT)) {
                coversEnd = true;
            } else {
                coversEnd = !effectiveTo.isBefore(shiftEnd);
            }

            return coversStart && coversEnd;
        }
        // "available" or unknown status → fully available
        return true;
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalTime getAvailableFrom() { return availableFrom; }
    public void setAvailableFrom(LocalTime availableFrom) { this.availableFrom = availableFrom; }
    public LocalTime getAvailableTo() { return availableTo; }
    public void setAvailableTo(LocalTime availableTo) { this.availableTo = availableTo; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeAvailability that = (EmployeeAvailability) o;
        return Objects.equals(employeeId, that.employeeId) &&
               Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeId, date);
    }

    @Override
    public String toString() {
        return "EmployeeAvailability{" +
               "employeeId='" + employeeId + '\'' +
               ", date='" + date + '\'' +
               ", status='" + status + '\'' +
               ", availableFrom=" + availableFrom +
               ", availableTo=" + availableTo +
               '}';
    }
}
