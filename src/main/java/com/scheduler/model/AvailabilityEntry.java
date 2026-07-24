package com.scheduler.model;

import java.time.LocalTime;

/**
 * Represents an employee's availability override for a specific date.
 * Used by the V2 assign API to support per-day availability.
 *
 * Statuses:
 *   - "unavailable" → employee cannot work this date at all
 *   - "partial"     → employee can only work during the from–to window
 *
 * If status is "partial" and only "from" is provided, "to" defaults to end of day (23:59).
 * If status is "partial" and neither "from" nor "to" is provided, treated as "unavailable".
 */
public class AvailabilityEntry {
    private String status;       // "unavailable" or "partial"
    private LocalTime from;      // only for partial
    private LocalTime to;        // only for partial

    public AvailabilityEntry() {}

    public AvailabilityEntry(String status, LocalTime from, LocalTime to) {
        this.status = status;
        this.from = from;
        this.to = to;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalTime getFrom() { return from; }
    public void setFrom(LocalTime from) { this.from = from; }
    public LocalTime getTo() { return to; }
    public void setTo(LocalTime to) { this.to = to; }

    /**
     * Checks if this availability entry fully covers the given shift window.
     * For "unavailable" status, always returns false.
     * For "partial" status, returns true only if the available window fully contains the shift.
     */
    public boolean coversShift(LocalTime shiftStart, LocalTime shiftEnd) {
        if ("unavailable".equalsIgnoreCase(status)) {
            return false;
        }
        if ("partial".equalsIgnoreCase(status)) {
            if (from == null && to == null) {
                return false; // No window specified → treat as unavailable
            }
            LocalTime effectiveFrom = (from != null) ? from : LocalTime.MIN;
            LocalTime effectiveTo = (to != null) ? to : LocalTime.of(23, 59);
            // Strict fit: available window must fully contain the shift
            return !effectiveFrom.isAfter(shiftStart) && !effectiveTo.isBefore(shiftEnd);
        }
        // Unknown status → treat as available (fail-open for forward compat)
        return true;
    }
}
