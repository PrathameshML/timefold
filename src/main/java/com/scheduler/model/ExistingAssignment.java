package com.scheduler.model;

/**
 * A read-only ProblemFact representing an employee already assigned on a given date.
 * The solver uses this to prevent assigning the same employee to another shift
 * on the same day, via the noOverlappingShifts constraint.
 */
public class ExistingAssignment {
    private String employeeId;
    private String date;

    public ExistingAssignment() {}

    public ExistingAssignment(String employeeId, String date) {
        this.employeeId = employeeId;
        this.date = date;
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
