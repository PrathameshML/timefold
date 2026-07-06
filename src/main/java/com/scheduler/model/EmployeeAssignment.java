package com.scheduler.model;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@PlanningEntity
public class EmployeeAssignment {
    
    @PlanningId
    private String id;
    
    private String employeeId;
    private String employeeName;
    private String date;
    private String category;
    private int group;
    private String gender;
    private Set<String> skills = new HashSet<>();
    private String department;
    private String position;
    private String shiftColor;
    private String requestedShift;
    private String shiftStartStr;
    private String shiftEndStr;
    private double hourlyWage;
    private int performanceRating;
    
    // Performance Optimization Fields
    private LocalTime shiftStartTimeObj;
    private double shiftDurationHours = 0.0;
    private long missingSkillCount = 0L;
    private int isoWeekNum = 0;
    private LocalDate localDateObj;
    
    // Dynamic constraints configuration reference
    private transient List<ConstraintConfig> activeConfigs;
    
    @PlanningVariable(valueRangeProviderRefs = "shiftRange", allowsUnassigned = true)
    private String shift;
    
    @PlanningPin
    private boolean pinned = false;

    public EmployeeAssignment() {
    }

    public EmployeeAssignment(String id, String employeeId, String employeeName, String date,
                              String category, String gender, String department, String position) {
        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.date = date;
        this.category = category;
        this.gender = gender;
        this.department = department;
        this.position = position;

        // Assign color based on department
        switch (department) {
            case "Development":
                this.shiftColor = "#4CAF50";
                break;
            case "Testing":
                this.shiftColor = "#FF9800";
                break;
            case "DevOps":
                this.shiftColor = "#2196F3";
                break;
            case "Support":
                this.shiftColor = "#9C27B0";
                break;
            case "Management":
                this.shiftColor = "#F44336";
                break;
            default:
                this.shiftColor = "#607D8B";
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getGroup() { return group; }
    public void setGroup(int group) { this.group = group; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public Set<String> getSkills() { return skills; }
    public void setSkills(Set<String> skills) { this.skills = skills; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getShiftColor() { return shiftColor; }
    public void setShiftColor(String shiftColor) { this.shiftColor = shiftColor; }
    public String getRequestedShift() { return requestedShift; }
    public void setRequestedShift(String requestedShift) { this.requestedShift = requestedShift; }
    public String getShiftStartStr() { return shiftStartStr; }
    public void setShiftStartStr(String shiftStartStr) { this.shiftStartStr = shiftStartStr; }
    public String getShiftEndStr() { return shiftEndStr; }
    public void setShiftEndStr(String shiftEndStr) { this.shiftEndStr = shiftEndStr; }
    public double getHourlyWage() { return hourlyWage; }
    public void setHourlyWage(double hourlyWage) { this.hourlyWage = hourlyWage; }
    public int getPerformanceRating() { return performanceRating; }
    public void setPerformanceRating(int performanceRating) { this.performanceRating = performanceRating; }
    public List<ConstraintConfig> getActiveConfigs() { return activeConfigs; }
    public void setActiveConfigs(List<ConstraintConfig> activeConfigs) { this.activeConfigs = activeConfigs; }
    public LocalTime getShiftStartTimeObj() { return shiftStartTimeObj; }
    public void setShiftStartTimeObj(LocalTime shiftStartTimeObj) { this.shiftStartTimeObj = shiftStartTimeObj; }
    public double getShiftDurationHours() { return shiftDurationHours; }
    public void setShiftDurationHours(double shiftDurationHours) { this.shiftDurationHours = shiftDurationHours; }
    public long getMissingSkillCount() { return missingSkillCount; }
    public void setMissingSkillCount(long missingSkillCount) { this.missingSkillCount = missingSkillCount; }
    public int getIsoWeekNum() { return isoWeekNum; }
    public void setIsoWeekNum(int isoWeekNum) { this.isoWeekNum = isoWeekNum; }
    public LocalDate getLocalDateObj() { return localDateObj; }
    public void setLocalDateObj(LocalDate localDateObj) { this.localDateObj = localDateObj; }
    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}
