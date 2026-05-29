package com.scheduler;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.api.score.stream.*;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;

import static com.scheduler.ShiftApp.AttendanceService.sendNotification;

@QuarkusMain
@Path("/")
public class ShiftApp {
    private static void loadAssignments() {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File(ASSIGNMENTS_FILE);

        if (file.exists()) {
            try {
                System.out.println("📂 Loading assignments from file: " + ASSIGNMENTS_FILE);

                // Try to read as generic Map first to check format
                Map<String, Object> data = mapper.readValue(file, new TypeReference<Map<String, Object>>() {});

                if (data.containsKey("assignments") && data.containsKey("employeeInfo")) {
                    // New format with employee info
                    System.out.println("📦 Loading new format (with employee info)");

                    // Load assignments
                    @SuppressWarnings("unchecked")
                    Map<String, Map<String, List<String>>> assignments =
                            (Map<String, Map<String, List<String>>>) data.get("assignments");
                    shiftAssignments.clear();
                    shiftAssignments.putAll(assignments);

                    // Load employee info
                    @SuppressWarnings("unchecked")
                    Map<String, Map<String, Object>> employeeInfoData =
                            (Map<String, Map<String, Object>>) data.get("employeeInfo");

                    employeeInfo.clear();
                    for (Map.Entry<String, Map<String, Object>> entry : employeeInfoData.entrySet()) {
                        String empId = entry.getKey();
                        Map<String, Object> empData = entry.getValue();

                        EmployeeInfo emp = new EmployeeInfo();
                        emp.setId(empId);
                        emp.setName((String) empData.getOrDefault("name", "Employee " + empId));
                        emp.setCategory((String) empData.getOrDefault("category", "Regular"));
                        emp.setGender((String) empData.getOrDefault("gender", "Male"));
                        emp.setHourlyWage(((Number) empData.getOrDefault("hourlyWage", 25.0)).doubleValue());
                        emp.setPosition((String) empData.getOrDefault("position", "Worker"));
                        emp.setPerformanceRating(((Number) empData.getOrDefault("performanceRating", 3)).intValue());

                        employeeInfo.put(empId, emp);
                    }

                    System.out.println("✅ Loaded " + shiftAssignments.size() + " days of assignments and " +
                            employeeInfo.size() + " employee records");

                } else {
                    // Legacy format (assignments only)
                    System.out.println("📦 Loading legacy format (assignments only)");
                    TypeReference<HashMap<String, HashMap<String, List<String>>>> typeRef =
                            new TypeReference<>() {};
                    HashMap<String, HashMap<String, List<String>>> loaded = mapper.readValue(file, typeRef);
                    shiftAssignments.clear();
                    shiftAssignments.putAll(loaded);
                    System.out.println("✅ Successfully loaded " + shiftAssignments.size() + " days of assignments");
                }

                // Count total assignments
                int totalAssignments = 0;
                for (Map<String, List<String>> dayAssignments : shiftAssignments.values()) {
                    for (List<String> employees : dayAssignments.values()) {
                        totalAssignments += employees.size();
                    }
                }
                System.out.println("📊 Total individual assignments: " + totalAssignments);

            } catch (IOException e) {
                System.err.println("❌ Failed to load assignments: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("📄 No assignments file found, starting fresh");
        }
    }


    // Initialize employees from assignments when loading legacy format
    private static void initializeEmployeesFromAssignments() {
        System.out.println("🔄 Initializing employees from assignments...");

        // Collect all employee IDs from shiftAssignments
        Set<String> employeesNeeded = new HashSet<>();
        for (Map<String, List<String>> dayAssignments : shiftAssignments.values()) {
            for (List<String> employeeIds : dayAssignments.values()) {
                employeesNeeded.addAll(employeeIds);
            }
        }

        System.out.println("📋 Employee IDs found in assignments: " + employeesNeeded.size());

        // Only create employees for IDs that don't exist yet
        int created = 0;
        for (String empId : employeesNeeded) {
            if (!employeeInfo.containsKey(empId)) {
                // Create a basic employee with this ID
                EmployeeInfo emp = createBasicEmployee(empId);
                employeeInfo.put(empId, emp);
                created++;
                System.out.println("   ✅ Created basic employee: " + emp.getName() + " (" + empId + ")");
            } else {
                System.out.println("   ℹ️ Employee already exists: " + employeeInfo.get(empId).getName() + " (" + empId + ")");
            }
        }

        System.out.println("✅ Created " + created + " new employees, total: " + employeeInfo.size());
    }

    private static void saveAssignments() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            File file = new File(ASSIGNMENTS_FILE);

            // Convert employeeInfo to a serializable map
            Map<String, Map<String, Object>> employeeInfoSerializable = new HashMap<>();
            for (Map.Entry<String, EmployeeInfo> entry : employeeInfo.entrySet()) {
                EmployeeInfo emp = entry.getValue();
                Map<String, Object> empData = new HashMap<>();
                empData.put("id", emp.getId());
                empData.put("name", emp.getName());
                empData.put("category", emp.getCategory());
                empData.put("gender", emp.getGender());
                empData.put("hourlyWage", emp.getHourlyWage());
                empData.put("position", emp.getPosition());
                empData.put("performanceRating", emp.getPerformanceRating());

                employeeInfoSerializable.put(entry.getKey(), empData);
            }

            // Create a complete data structure
            Map<String, Object> completeData = new HashMap<>();
            completeData.put("assignments", shiftAssignments);
            completeData.put("employeeInfo", employeeInfoSerializable);
            completeData.put("savedAt", LocalDateTime.now().toString());
            completeData.put("version", "1.2");

            mapper.writerWithDefaultPrettyPrinter().writeValue(file, completeData);

            System.out.println("💾 Saved " + shiftAssignments.size() + " days of assignments");
            System.out.println("💾 Saved " + employeeInfo.size() + " employee records");
            System.out.println("📁 Saved to: " + ASSIGNMENTS_FILE);

            // Count total assignments
            int totalAssignments = 0;
            for (Map<String, List<String>> dayAssignments : shiftAssignments.values()) {
                for (List<String> employees : dayAssignments.values()) {
                    totalAssignments += employees.size();
                }
            }
            System.out.println("📊 Total individual assignments: " + totalAssignments);

        } catch (IOException e) {
            System.err.println("❌ Failed to save assignments: " + e.getMessage());
            e.printStackTrace();
        }
    }
    @PostConstruct
    public void init() {
        // Load assignments from file FIRST
        loadAssignments();

        System.out.println("🚀 ShiftApp started - Manual shift assignment mode enabled");
        System.out.println("📊 Loaded assignments summary:");
        System.out.println("   - Days with assignments: " + shiftAssignments.size());
        System.out.println("   - Employee records loaded: " + employeeInfo.size());

        // Display loaded assignments
        displayLoadedAssignments();

        System.out.println("✅ System ready. Use GET /shifts to view assignments.");
    }

    private void displayLoadedAssignments() {
        System.out.println("\n📊 Loaded Assignments Summary:");
        System.out.println("==============================");

        if (shiftAssignments.isEmpty()) {
            System.out.println("   No assignments loaded");
            return;
        }

        int totalAssignments = 0;
        Map<String, Integer> assignmentsByShift = new HashMap<>();

        // Sort dates for display
        List<String> sortedDates = new ArrayList<>(shiftAssignments.keySet());
        Collections.sort(sortedDates);

        for (String date : sortedDates) {
            Map<String, List<String>> dayAssignments = shiftAssignments.get(date);

            if (dayAssignments == null || dayAssignments.isEmpty()) {
                continue;
            }

            System.out.println("\n📅 Date: " + date);

            for (Map.Entry<String, List<String>> shiftEntry : dayAssignments.entrySet()) {
                String shift = shiftEntry.getKey();
                List<String> employees = shiftEntry.getValue();

                System.out.println("    " + shift + " shift: " + employees.size() + " employees");

                // Count by shift
                assignmentsByShift.put(shift, assignmentsByShift.getOrDefault(shift, 0) + employees.size());
                totalAssignments += employees.size();

                // Display first few employees
                int displayLimit = 3;
                for (int i = 0; i < Math.min(employees.size(), displayLimit); i++) {
                    String empId = employees.get(i);
                    EmployeeInfo emp = employeeInfo.get(empId);
                    if (emp != null) {
                        System.out.println("      • " + emp.getName() + " (" + empId + ") - " +
                                emp.getPosition() + " - " + emp.getGender());
                    } else {
                        System.out.println("      • " + empId + " (Employee not found in system)");
                    }
                }

                if (employees.size() > displayLimit) {
                    System.out.println("      ... and " + (employees.size() - displayLimit) + " more");
                }
            }
        }

        System.out.println("\n📈 Summary:");
        System.out.println("   Total days with assignments: " + shiftAssignments.size());
        System.out.println("   Total individual assignments: " + totalAssignments);
        System.out.println("   Total employee records: " + employeeInfo.size());

        for (Map.Entry<String, Integer> entry : assignmentsByShift.entrySet()) {
            System.out.println("   " + entry.getKey() + " shift assignments: " + entry.getValue());
        }
    }
    private static void initializeEmployees() {
        System.out.println("🔄 Initializing employees on startup...");

        // Check if we already have employees loaded from assignments
        Set<String> employeesNeeded = new HashSet<>();

        // Collect all employee IDs from shiftAssignments
        for (Map<String, List<String>> dayAssignments : shiftAssignments.values()) {
            for (List<String> employeeIds : dayAssignments.values()) {
                employeesNeeded.addAll(employeeIds);
            }
        }

        System.out.println("📋 Employee IDs found in assignments: " + employeesNeeded.size());

        // Only create employees for IDs that don't exist yet
        int created = 0;
        for (String empId : employeesNeeded) {
            if (!employeeInfo.containsKey(empId)) {
                // Create a basic employee with this ID
                EmployeeInfo emp = createBasicEmployee(empId);
                employeeInfo.put(empId, emp);
                created++;
                System.out.println("   ✅ Created employee: " + emp.getName() + " (" + empId + ") - " + emp.getGender());
            } else {
                System.out.println("   ℹ️ Employee already exists: " + employeeInfo.get(empId).getName() + " (" + empId + ")");
            }
        }

        System.out.println("✅ Created " + created + " new employees, total: " + employeeInfo.size());

        // Display employee summary
        System.out.println("\n👥 Employee Summary:");
        for (EmployeeInfo emp : employeeInfo.values()) {
            System.out.println("   " + emp.getId() + ": " + emp.getName() +
                    " (" + emp.getGender() + ") - " + emp.getPosition());
        }
    }
    private static EmployeeInfo createBasicEmployee(String employeeId) {
        // Parse employee number from ID (E001 -> 1)
        int empNum = 1;
        try {
            String numStr = employeeId.substring(1); // Remove 'E'
            empNum = Integer.parseInt(numStr);
        } catch (Exception e) {
            empNum = 1;
        }

        // Determine gender based on number (odd = Male, even = Female)
        String gender = (empNum % 2 == 1) ? "Male" : "Female";

        // Create name based on gender and number
        String[][] nameArray = gender.equals("Male") ? INDIAN_MALE_NAMES : INDIAN_FEMALE_NAMES;
        String[] namePair = nameArray[empNum % nameArray.length];
        String name = namePair[0] + " " + namePair[1];

        // Assign department based on number
        String[] departments = {"Development", "Testing", "DevOps", "Support", "Management"};
        String department = departments[empNum % departments.length];

        // Assign position based on department
        String position;
        switch (department) {
            case "Development": position = "Software Developer"; break;
            case "Testing": position = "QA Engineer"; break;
            case "DevOps": position = "DevOps Engineer"; break;
            case "Support": position = "Support Engineer"; break;
            default: position = "Manager";
        }

        // Create employee info
        EmployeeInfo emp = new EmployeeInfo(
                employeeId,
                name,
                "Regular",
                gender,
                25.0 + (empNum % 20), // Wage between 25-45
                "MGR001",
                department,
                position,
                name.toLowerCase().replace(" ", ".") + "@company.com",
                "+91 9" + String.format("%09d", empNum * 1234567)
        );

        // Add skills based on position
        addSkillsBasedOnRole(emp, position);

        return emp;
    }
    // Configuration Management
    public static class SystemConfig {
        // Attendance Rules
        private int minHoursForPresent = 4;
        private int halfDayHours = 4;
        private int gracePeriodMinutes = 5;
        private int maxLateAllowedMinutes = 480;
        private int roundingIntervalMinutes = 5;
        private boolean requireBothPunchInOut = true;
        private int maxEarlyOutMinutes = 30;

        // Overtime Rules
        private double otRateNormal = 1.5;
        private double otRateHoliday = 2.0;
        private int maxDailyOTHours = 3;
        private int maxWeeklyOTHours = 15;
        private boolean autoApproveOT = false;
        private boolean paidOT = true;
        private boolean generateCompOff = false;

        // Shift Types
        private Map<String, ShiftType> shiftTypes = new HashMap<>();

        // Shift Constraints
        private int minGapBetweenShiftsHours = 11;
        private int maxWorkingHoursPerDay = 10;
        private int maxConsecutiveWorkingDays = 6;
        private int maxConsecutiveNightShifts = 3;
        private int mandatoryRestAfterNightShiftHours = 12;

        // Government Compliance
        private int maxDailyHoursLaw = 9;
        private int maxWeeklyHoursLaw = 48;
        private boolean femaleShiftRestrictions = true;
        private LocalTime femaleShiftStartRestriction = LocalTime.of(19, 0);
        private LocalTime femaleShiftEndRestriction = LocalTime.of(6, 0);
        private boolean requireTransportForLateNight = true;
        private LocalTime transportRequiredAfter = LocalTime.of(22, 0);
        private int minimumHourlyWage = 15;

        // Leave Policies
        private int annualLeaveDays = 15;
        private int sickLeaveDays = 10;
        private int casualLeaveDays = 7;
        private boolean leaveEncashmentAllowed = true;
        private double leaveEncashmentRate = 1.0;

        // Break Rules
        private boolean autoLunchBreakDeduction = true;
        private int lunchBreakMinutes = 30;
        private boolean paidBreaks = false;
        private int breakDurationMinutes = 15;
        private int breakFrequencyHours = 4;

        // Notification Rules
        private boolean notifyShiftChange = true;
        private boolean notifyOTApproval = true;
        private boolean notifyLeaveApproval = true;
        private boolean notifyComplianceViolation = true;
        private boolean notifyLeaveCoverageRequired = true;


        // Employee Categories
        private Map<String, EmployeeCategory> categories = new HashMap<>();

        public SystemConfig() {
            // Initialize default categories
            categories.put("Regular", new EmployeeCategory("Regular", 40, 8, true, 30, 12));
            categories.put("Contractor", new EmployeeCategory("Contractor", 48, 10, false, 15, 8));
            categories.put("Manager", new EmployeeCategory("Manager", 45, 9, true, 60, 10));

            // Initialize shift types
            shiftTypes.put("Fixed", new ShiftType("Fixed", "09:00-18:00", 8, false));
            shiftTypes.put("Rotational", new ShiftType("Rotational", "Variable", 8, true));
            shiftTypes.put("Split", new ShiftType("Split", "09:00-13:00,16:00-20:00", 8, false));
            shiftTypes.put("Night", new ShiftType("Night", "21:00-06:00", 8, true));
        }

        // Getters and Setters
        public int getMinHoursForPresent() { return minHoursForPresent; }
        public void setMinHoursForPresent(int minHoursForPresent) { this.minHoursForPresent = minHoursForPresent; }

        public int getHalfDayHours() { return halfDayHours; }
        public void setHalfDayHours(int halfDayHours) { this.halfDayHours = halfDayHours; }

        public int getGracePeriodMinutes() { return gracePeriodMinutes; }
        public void setGracePeriodMinutes(int gracePeriodMinutes) { this.gracePeriodMinutes = gracePeriodMinutes; }

        public int getRoundingIntervalMinutes() { return roundingIntervalMinutes; }
        public void setRoundingIntervalMinutes(int roundingIntervalMinutes) { this.roundingIntervalMinutes = roundingIntervalMinutes; }

        public boolean isRequireBothPunchInOut() { return requireBothPunchInOut; }
        public void setRequireBothPunchInOut(boolean requireBothPunchInOut) { this.requireBothPunchInOut = requireBothPunchInOut; }

        public double getOtRateNormal() { return otRateNormal; }
        public void setOtRateNormal(double otRateNormal) { this.otRateNormal = otRateNormal; }

        public double getOtRateHoliday() { return otRateHoliday; }
        public void setOtRateHoliday(double otRateHoliday) { this.otRateHoliday = otRateHoliday; }

        public int getMaxDailyOTHours() { return maxDailyOTHours; }
        public void setMaxDailyOTHours(int maxDailyOTHours) { this.maxDailyOTHours = maxDailyOTHours; }

        public int getMaxWeeklyOTHours() { return maxWeeklyOTHours; }
        public void setMaxWeeklyOTHours(int maxWeeklyOTHours) { this.maxWeeklyOTHours = maxWeeklyOTHours; }

        public boolean isAutoApproveOT() { return autoApproveOT; }
        public void setAutoApproveOT(boolean autoApproveOT) { this.autoApproveOT = autoApproveOT; }

        public boolean isPaidOT() { return paidOT; }
        public void setPaidOT(boolean paidOT) { this.paidOT = paidOT; }

        public Map<String, ShiftType> getShiftTypes() { return shiftTypes; }
        public void setShiftTypes(Map<String, ShiftType> shiftTypes) { this.shiftTypes = shiftTypes; }

        public int getMinGapBetweenShiftsHours() { return minGapBetweenShiftsHours; }
        public void setMinGapBetweenShiftsHours(int minGapBetweenShiftsHours) { this.minGapBetweenShiftsHours = minGapBetweenShiftsHours; }

        public int getMaxWorkingHoursPerDay() { return maxWorkingHoursPerDay; }
        public void setMaxWorkingHoursPerDay(int maxWorkingHoursPerDay) { this.maxWorkingHoursPerDay = maxWorkingHoursPerDay; }

        public int getMaxConsecutiveWorkingDays() { return maxConsecutiveWorkingDays; }
        public void setMaxConsecutiveWorkingDays(int maxConsecutiveWorkingDays) { this.maxConsecutiveWorkingDays = maxConsecutiveWorkingDays; }

        public int getMaxConsecutiveNightShifts() { return maxConsecutiveNightShifts; }
        public void setMaxConsecutiveNightShifts(int maxConsecutiveNightShifts) { this.maxConsecutiveNightShifts = maxConsecutiveNightShifts; }

        public int getMandatoryRestAfterNightShiftHours() { return mandatoryRestAfterNightShiftHours; }
        public void setMandatoryRestAfterNightShiftHours(int mandatoryRestAfterNightShiftHours) { this.mandatoryRestAfterNightShiftHours = mandatoryRestAfterNightShiftHours; }

        public int getMaxDailyHoursLaw() { return maxDailyHoursLaw; }
        public void setMaxDailyHoursLaw(int maxDailyHoursLaw) { this.maxDailyHoursLaw = maxDailyHoursLaw; }

        public int getMaxWeeklyHoursLaw() { return maxWeeklyHoursLaw; }
        public void setMaxWeeklyHoursLaw(int maxWeeklyHoursLaw) { this.maxWeeklyHoursLaw = maxWeeklyHoursLaw; }

        public boolean isFemaleShiftRestrictions() { return femaleShiftRestrictions; }
        public void setFemaleShiftRestrictions(boolean femaleShiftRestrictions) { this.femaleShiftRestrictions = femaleShiftRestrictions; }

        public LocalTime getFemaleShiftStartRestriction() { return femaleShiftStartRestriction; }
        public void setFemaleShiftStartRestriction(LocalTime femaleShiftStartRestriction) { this.femaleShiftStartRestriction = femaleShiftStartRestriction; }

        public LocalTime getFemaleShiftEndRestriction() { return femaleShiftEndRestriction; }
        public void setFemaleShiftEndRestriction(LocalTime femaleShiftEndRestriction) { this.femaleShiftEndRestriction = femaleShiftEndRestriction; }

        public boolean isRequireTransportForLateNight() { return requireTransportForLateNight; }
        public void setRequireTransportForLateNight(boolean requireTransportForLateNight) { this.requireTransportForLateNight = requireTransportForLateNight; }

        public LocalTime getTransportRequiredAfter() { return transportRequiredAfter; }
        public void setTransportRequiredAfter(LocalTime transportRequiredAfter) { this.transportRequiredAfter = transportRequiredAfter; }

        public int getMinimumHourlyWage() { return minimumHourlyWage; }
        public void setMinimumHourlyWage(int minimumHourlyWage) { this.minimumHourlyWage = minimumHourlyWage; }

        public int getAnnualLeaveDays() { return annualLeaveDays; }
        public void setAnnualLeaveDays(int annualLeaveDays) { this.annualLeaveDays = annualLeaveDays; }

        public int getSickLeaveDays() { return sickLeaveDays; }
        public void setSickLeaveDays(int sickLeaveDays) { this.sickLeaveDays = sickLeaveDays; }

        public int getCasualLeaveDays() { return casualLeaveDays; }
        public void setCasualLeaveDays(int casualLeaveDays) { this.casualLeaveDays = casualLeaveDays; }

        public boolean isLeaveEncashmentAllowed() { return leaveEncashmentAllowed; }
        public void setLeaveEncashmentAllowed(boolean leaveEncashmentAllowed) { this.leaveEncashmentAllowed = leaveEncashmentAllowed; }

        public double getLeaveEncashmentRate() { return leaveEncashmentRate; }
        public void setLeaveEncashmentRate(double leaveEncashmentRate) { this.leaveEncashmentRate = leaveEncashmentRate; }

        public boolean isAutoLunchBreakDeduction() { return autoLunchBreakDeduction; }
        public void setAutoLunchBreakDeduction(boolean autoLunchBreakDeduction) { this.autoLunchBreakDeduction = autoLunchBreakDeduction; }

        public int getLunchBreakMinutes() { return lunchBreakMinutes; }
        public void setLunchBreakMinutes(int lunchBreakMinutes) { this.lunchBreakMinutes = lunchBreakMinutes; }

        public boolean isPaidBreaks() { return paidBreaks; }
        public void setPaidBreaks(boolean paidBreaks) { this.paidBreaks = paidBreaks; }

        public int getBreakDurationMinutes() { return breakDurationMinutes; }
        public void setBreakDurationMinutes(int breakDurationMinutes) { this.breakDurationMinutes = breakDurationMinutes; }

        public int getBreakFrequencyHours() { return breakFrequencyHours; }
        public void setBreakFrequencyHours(int breakFrequencyHours) { this.breakFrequencyHours = breakFrequencyHours; }

        public boolean isNotifyShiftChange() { return notifyShiftChange; }
        public void setNotifyShiftChange(boolean notifyShiftChange) { this.notifyShiftChange = notifyShiftChange; }

        public boolean isNotifyOTApproval() { return notifyOTApproval; }
        public void setNotifyOTApproval(boolean notifyOTApproval) { this.notifyOTApproval = notifyOTApproval; }

        public boolean isNotifyLeaveApproval() { return notifyLeaveApproval; }
        public void setNotifyLeaveApproval(boolean notifyLeaveApproval) { this.notifyLeaveApproval = notifyLeaveApproval; }

        public boolean isNotifyComplianceViolation() { return notifyComplianceViolation; }
        public void setNotifyComplianceViolation(boolean notifyComplianceViolation) { this.notifyComplianceViolation = notifyComplianceViolation; }

        public Map<String, EmployeeCategory> getCategories() { return categories; }
        public void setCategories(Map<String, EmployeeCategory> categories) { this.categories = categories; }

        public int getMaxLateAllowedMinutes() {
            return maxLateAllowedMinutes;
        }

        public void setMaxLateAllowedMinutes(int maxLateAllowedMinutes) {
            this.maxLateAllowedMinutes = maxLateAllowedMinutes;
        }

        public int getMaxEarlyOutMinutes() {
            return maxEarlyOutMinutes;
        }

        public void setMaxEarlyOutMinutes(int maxEarlyOutMinutes) {
            this.maxEarlyOutMinutes = maxEarlyOutMinutes;
        }

        public boolean isGenerateCompOff() {
            return generateCompOff;
        }

        public void setGenerateCompOff(boolean generateCompOff) {
            this.generateCompOff = generateCompOff;
        }

        public int getMaxEarlyAllowedMinutes() {
            return 30;
        }

        public boolean isNotifyLeaveCoverageRequired() {
            return notifyLeaveCoverageRequired;
        }

        public void setNotifyLeaveCoverageRequired(boolean notifyLeaveCoverageRequired) {
            this.notifyLeaveCoverageRequired = notifyLeaveCoverageRequired;
        }
    }

    public static class ShiftType {
        private String name;
        private String hours;
        private int durationHours;
        private boolean nightShift;

        public ShiftType() {}

        public ShiftType(String name, String hours, int durationHours, boolean nightShift) {
            this.name = name;
            this.hours = hours;
            this.durationHours = durationHours;
            this.nightShift = nightShift;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getHours() { return hours; }
        public void setHours(String hours) { this.hours = hours; }

    }

    public static class EmployeeCategory {
        private String name;
        private int weeklyHoursLimit;
        private final int maxShiftLengthHours;
        private final boolean paidBreaks;
        private final int breakDurationMinutes;
        private final int mandatoryRestHours;



        public EmployeeCategory(String name, int weeklyHoursLimit, int maxShiftLengthHours,
                                boolean paidBreaks, int breakDurationMinutes, int mandatoryRestHours) {
            this.name = name;
            this.weeklyHoursLimit = weeklyHoursLimit;
            this.maxShiftLengthHours = maxShiftLengthHours;
            this.paidBreaks = paidBreaks;
            this.breakDurationMinutes = breakDurationMinutes;
            this.mandatoryRestHours = mandatoryRestHours;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getWeeklyHoursLimit() { return weeklyHoursLimit; }
        public void setWeeklyHoursLimit(int weeklyHoursLimit) { this.weeklyHoursLimit = weeklyHoursLimit; }

    }

    // Store employee shifts and leaves - make them static so they persist
//    private static final Map<String, String> employeeShifts = new HashMap<>();
    private static final Map<String, Map<String, List<String>>> shiftAssignments = new HashMap<>();  // day -> shift -> list of employee IDs
    private static String ASSIGNMENTS_FILE = System.getProperty("app.data.dir", System.getProperty("user.dir")) + "/shift_assignments.json";
    private static final Map<String, List<LeaveRecord>> employeeLeaves = new HashMap<>();
    private static final Map<String, EmployeeInfo> employeeInfo = new HashMap<>();

    // Track if we need to generate new shifts
    private static boolean shiftsGenerated = false;

    // System configuration
    private static SystemConfig systemConfig = new SystemConfig();

    // Store attendance configurations
    private static final Map<String, AttendanceConfig> attendanceConfigs = new HashMap<>();

    // Overtime tracking
    private static final Map<String, OvertimeRecord> overtimeRecords = new HashMap<>();

    // Compliance violations tracking
    private static final List<ComplianceViolation> complianceViolations = new ArrayList<>();

    // NEW: Leave Coverage Requests tracking
    private static final Map<String, LeaveCoverageRequest> leaveCoverageRequests = new HashMap<>();

    // NEW: OT Assignments for leave coverage
    private static final Map<String, OTCoverageAssignment> otCoverageAssignments = new HashMap<>();

    // NEW: MySQL Service for database synchronization
    private static final MySQLService mysqlService = new MySQLService();
    public static void setDataDirectory(String path) {
        ASSIGNMENTS_FILE = path + "/shift_assignments.json";
        System.out.println(" Set data directory to: " + ASSIGNMENTS_FILE);
    }
    // Shift time configuration class
    public static class ShiftTime {
        private final LocalTime startTime;
        private final LocalTime endTime;

        public ShiftTime(LocalTime startTime, LocalTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public LocalTime getStartTime() { return startTime; }
        public LocalTime getEndTime() { return endTime; }
    }
    public static class LeaveRecord {
        private String date;
        private String leaveType;

        public LeaveRecord(String date, String leaveType) {
            this.date = date;
            this.leaveType = leaveType;
        }

        public String getDate() { return date; }
        public String getLeaveType() { return leaveType; }
    }
    @JsonPropertyOrder({
            "employeeId",
            "employeeName",
            "date",
            "shift",
            "breakStart",
            "breakEnd",
            "breakSlot",
            "breakDuration",
            "breakType"
    })
    public static class BreakSchedule {
        private String employeeId;
        private String employeeName;
        private String date;
        private String shiftName;
        private LocalTime shiftStartTime;
        private LocalTime shiftEndTime;
        private LocalTime breakStartTime;
        private LocalTime breakEndTime;
        private int breakDurationMinutes = 30;
        private String breakType = "MANDATORY";

        public BreakSchedule() {}

        public BreakSchedule(String employeeId, String employeeName, String date,
                             String shiftName, LocalTime shiftStartTime, LocalTime shiftEndTime)
        {
            this.employeeId = employeeId;
            this.employeeName = employeeName;
            this.date = date;
            this.shiftName = shiftName;
            this.shiftStartTime = shiftStartTime;
            this.shiftEndTime = shiftEndTime;

            // Calculate break based on shift times
            calculateBreakTime(shiftStartTime, shiftEndTime);
        }
        private void calculateBreakTime(LocalTime shiftStartTime, LocalTime shiftEndTime) {
            // Check if it's a night shift (crosses midnight)
            boolean isNightShift = shiftEndTime.isBefore(shiftStartTime) ||
                    "Night".equalsIgnoreCase(this.shiftName);

            if (isNightShift) {
                // For night shift, schedule break around midnight or early morning
                // Example: If shift is 21:00-05:00, break at 01:00-01:30
                LocalTime midnight = LocalTime.of(0, 0);
                this.breakStartTime = midnight.plusHours(1); // 01:00 AM
                this.breakEndTime = breakStartTime.plusMinutes(this.breakDurationMinutes);
            } else {
                // For day shifts: 4 hours after start
                this.breakStartTime = shiftStartTime.plusHours(4);
                this.breakEndTime = breakStartTime.plusMinutes(this.breakDurationMinutes);

                // Ensure break doesn't exceed shift end
                if (this.breakEndTime.isAfter(shiftEndTime)) {
                    this.breakEndTime = shiftEndTime;
                    this.breakStartTime = shiftEndTime.minusMinutes(this.breakDurationMinutes);
                }
            }
        }
        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public String getShiftName() { return shiftName; }
        public void setShiftName(String shiftName) { this.shiftName = shiftName; }

        public LocalTime getShiftStartTime() { return shiftStartTime; }
        public void setShiftStartTime(LocalTime shiftStartTime) { this.shiftStartTime = shiftStartTime; }

        public LocalTime getShiftEndTime() { return shiftEndTime; }
        public void setShiftEndTime(LocalTime shiftEndTime) { this.shiftEndTime = shiftEndTime; }

        public LocalTime getBreakStartTime() { return breakStartTime; }
        public void setBreakStartTime(LocalTime breakStartTime) {
            this.breakStartTime = breakStartTime;
            // Update breakEndTime to maintain duration
            this.breakEndTime = breakStartTime.plusMinutes(this.breakDurationMinutes);
        }

        public LocalTime getBreakEndTime() { return breakEndTime; }
        public void setBreakEndTime(LocalTime breakEndTime) {
            this.breakEndTime = breakEndTime;
        }

        public int getBreakDurationMinutes() { return breakDurationMinutes; }
        public void setBreakDurationMinutes(int breakDurationMinutes) {
            this.breakDurationMinutes = breakDurationMinutes;
            // Update breakEndTime to maintain duration
            if (this.breakStartTime != null) {
                this.breakEndTime = this.breakStartTime.plusMinutes(breakDurationMinutes);
            }
        }

        public String getBreakType() { return breakType; }
        public void setBreakType(String breakType) { this.breakType = breakType; }

        // CORRECTED: Ensure correct order - breakStart first, breakEnd last
        public String getBreakStart() {
            // Default
            return String.format("%02d:%02d", breakStartTime.getHour(), breakStartTime.getMinute());
        }

        public String getBreakEnd() {
            return String.format("%02d:%02d", breakEndTime.getHour(), breakEndTime.getMinute());
        }

        public String getFormattedBreakSlot() {
            return getBreakStart() + " - " + getBreakEnd(); // Correct order
        }
    }
    // Employee Information class
    public static class EmployeeInfo {
        private String id;
        private String name;
        private String category;
        private String gender;
        private double hourlyWage;
        private Set<String> skills = new HashSet<>();
        private String managerId;
        private boolean requiresTransport;
        private String emergencyContact;
        private int annualLeaveBalance;
        private int sickLeaveBalance;
        private int casualLeaveBalance;
        private String department;
        private String position;
        private String email;
        private String phone;
        private LocalDate joiningDate;
        private String shiftColor;
        private int performanceRating;
        private String employeeType;
        public EmployeeInfo() {}

        public EmployeeInfo(String id, String name, String employeeType, String gender,
                            double hourlyWage, String managerId, String department,
                            String position, String email, String phone) {
            this.id = id;
            this.name = name;
            this.employeeType = employeeType;
            this.category = category;
            this.gender = gender;
            this.hourlyWage = hourlyWage;
            this.managerId = managerId;
            this.department = department;
            this.position = position;
            this.email = email;
            this.phone = phone;
            this.joiningDate = LocalDate.now().minusDays(new Random().nextInt(365 * 3));
            this.annualLeaveBalance = systemConfig.getAnnualLeaveDays();
            this.sickLeaveBalance = systemConfig.getSickLeaveDays();
            this.casualLeaveBalance = systemConfig.getCasualLeaveDays();

            // Assign shift color based on department
            switch(department) {
                case "Development": this.shiftColor = "#4CAF50"; break; // Green
                case "Testing": this.shiftColor = "#FF9800"; break; // Orange
                case "DevOps": this.shiftColor = "#2196F3"; break; // Blue
                case "Support": this.shiftColor = "#9C27B0"; break; // Purple
                case "Management": this.shiftColor = "#F44336"; break; // Red
                default: this.shiftColor = "#607D8B"; // Grey
            }
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmployeeType() { return employeeType; }
        public void setEmployeeType(String employeeType) { this.employeeType = employeeType; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }

        public double getHourlyWage() { return hourlyWage; }
        public void setHourlyWage(double hourlyWage) { this.hourlyWage = hourlyWage; }

        public Set<String> getSkills() { return skills; }
        public void setSkills(Set<String> skills) { this.skills = skills; }

        public String getManagerId() { return managerId; }
        public void setManagerId(String managerId) { this.managerId = managerId; }

        public boolean isRequiresTransport() { return requiresTransport; }
        public void setRequiresTransport(boolean requiresTransport) { this.requiresTransport = requiresTransport; }

        public String getEmergencyContact() { return emergencyContact; }
        public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

        public int getAnnualLeaveBalance() { return annualLeaveBalance; }
        public void setAnnualLeaveBalance(int annualLeaveBalance) { this.annualLeaveBalance = annualLeaveBalance; }

        public int getSickLeaveBalance() { return sickLeaveBalance; }
        public void setSickLeaveBalance(int sickLeaveBalance) { this.sickLeaveBalance = sickLeaveBalance; }

        public int getCasualLeaveBalance() { return casualLeaveBalance; }
        public void setCasualLeaveBalance(int casualLeaveBalance) { this.casualLeaveBalance = casualLeaveBalance; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public LocalDate getJoiningDate() { return joiningDate; }
        public void setJoiningDate(LocalDate joiningDate) { this.joiningDate = joiningDate; }

        public String getShiftColor() { return shiftColor; }
        public void setShiftColor(String shiftColor) { this.shiftColor = shiftColor; }

        public void addSkill(String skill) { skills.add(skill); }
        public boolean hasSkill(String skill) { return skills.contains(skill); }

        @JsonProperty("performanceRating")
        public int getPerformanceRating() { return performanceRating; }
        public void setPerformanceRating(int rating) { this.performanceRating = Math.max(1, Math.min(5, rating)); }
        // NEW: Check if employee can work shift based on gender restrictions
        public boolean canWorkShift(String shiftName) {
            if (systemConfig.isFemaleShiftRestrictions() && "Female".equalsIgnoreCase(gender)) {
                ShiftTime shiftTime = SHIFT_TIMES.get(shiftName);
                if (shiftTime != null) {
                    LocalTime start = shiftTime.getStartTime();
                    LocalTime end = shiftTime.getEndTime();

                    // Check if shift falls within restricted hours
                    if (shiftName.equals("Night")) {
                        return false; // Females cannot work night shifts
                    }

                    return !start.isAfter(systemConfig.getFemaleShiftStartRestriction()) &&
                            !end.isBefore(systemConfig.getFemaleShiftEndRestriction());
                }
            }
            return true;
        }

        // NEW: Check if employee requires transport for late night shift
        public boolean requiresLateNightTransport(String shiftName) {
            if (!systemConfig.isRequireTransportForLateNight()) return false;

            ShiftTime shiftTime = SHIFT_TIMES.get(shiftName);
            if (shiftTime != null) {
                LocalTime end = shiftTime.getEndTime();
                return end.isAfter(systemConfig.getTransportRequiredAfter()) ||
                        (shiftName.equals("Night") && shiftTime.getStartTime().isAfter(systemConfig.getTransportRequiredAfter()));
            }
            return false;
        }

        // NEW: Calculate skill similarity percentage
        public double calculateSkillSimilarity(EmployeeInfo other) {
            if (other == null || this.skills.isEmpty() || other.getSkills().isEmpty()) {
                return 0.0;
            }

            Set<String> commonSkills = new HashSet<>(this.skills);
            commonSkills.retainAll(other.getSkills());

            Set<String> unionSkills = new HashSet<>(this.skills);
            unionSkills.addAll(other.getSkills());

            if (unionSkills.isEmpty()) {
                return 0.0;
            }

            return (double) commonSkills.size() / unionSkills.size() * 100;
        }

        // NEW: Check if employee can cover based on skill threshold (e.g., 70% match)
        public boolean canCoverFor(EmployeeInfo absentEmployee, String shiftName, double skillThreshold) {
            if (absentEmployee == null) return false;

            // Skip if it's the same employee
            if (this.id.equals(absentEmployee.getId())) {
                return false;
            }

            // Check same department (prefer same department for coverage)
            if (!this.department.equals(absentEmployee.getDepartment())) {
                return false;
            }

            // Check skill similarity
            double skillSimilarity = calculateSkillSimilarity(absentEmployee);
            if (skillSimilarity < skillThreshold) {
                return false;
            }

            // Check gender restrictions for shift
            if (!this.canWorkShift(shiftName)) {
                return false;
            }

            // Check if employee has exceeded OT limits
            if (hasExceededOTLimits()) {
                return false;
            }

            // Check if already assigned for coverage on same day
            LocalDate today = LocalDate.now();
            boolean alreadyCovering = otCoverageAssignments.values().stream()
                    .anyMatch(assignment ->
                            assignment.getAssignedEmployeeId().equals(this.id) &&
                                    assignment.getCoverageDate().equals(today)
                    );

            return !alreadyCovering;
        }

        // NEW: Check if employee has exceeded OT limits
        private boolean hasExceededOTLimits() {
            LocalDate today = LocalDate.now();
            LocalDate weekStart = today.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);

            // Calculate weekly OT hours
            double weeklyOTHours = overtimeRecords.values().stream()
                    .filter(ot -> ot.getEmployeeId().equals(this.id))
                    .filter(ot -> ot.getDate().isAfter(weekStart.minusDays(1)) &&
                            ot.getDate().isBefore(weekEnd.plusDays(1)))
                    .filter(OvertimeRecord::isApproved)
                    .mapToDouble(OvertimeRecord::getHours)
                    .sum();

            // Check against system limits
            return weeklyOTHours >= systemConfig.getMaxWeeklyOTHours();
        }

        // NEW: Calculate OT wage for coverage
        public double calculateCoverageWage(double hours, String coverageType) {
            double baseRate = this.hourlyWage;
            double otMultiplier = switch (coverageType) {
                case "EMERGENCY" -> systemConfig.getOtRateNormal() * 1.5; // 50% extra for emergency
                case "HOLIDAY" -> systemConfig.getOtRateHoliday();
                default -> systemConfig.getOtRateNormal() * 1.2; // 20% extra for coverage
            };

            return baseRate * hours * otMultiplier;
        }

        // NEW: Get employee's weekly OT hours
        public double getWeeklyOTHours() {
            LocalDate today = LocalDate.now();
            LocalDate weekStart = today.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);

            return overtimeRecords.values().stream()
                    .filter(ot -> ot.getEmployeeId().equals(this.id))
                    .filter(ot -> ot.getDate().isAfter(weekStart.minusDays(1)) &&
                            ot.getDate().isBefore(weekEnd.plusDays(1)))
                    .filter(OvertimeRecord::isApproved)
                    .mapToDouble(OvertimeRecord::getHours)
                    .sum();
        }

        // NEW: Get employee's daily OT hours for a specific date
        public double getDailyOTHours(LocalDate date) {
            return overtimeRecords.values().stream()
                    .filter(ot -> ot.getEmployeeId().equals(this.id))
                    .filter(ot -> ot.getDate().equals(date))
                    .filter(OvertimeRecord::isApproved)
                    .mapToDouble(OvertimeRecord::getHours)
                    .sum();
        }

        // NEW: Check if employee can work additional OT hours
        public boolean canWorkAdditionalOT(double additionalHours, LocalDate date) {
            double dailyOT = getDailyOTHours(date) + additionalHours;
            double weeklyOT = getWeeklyOTHours() + additionalHours;

            return dailyOT <= systemConfig.getMaxDailyOTHours() &&
                    weeklyOT <= systemConfig.getMaxWeeklyOTHours();
        }

        // NEW: Get similar employees for coverage
        public List<EmployeeInfo> getSimilarEmployees(double minSkillMatch) {
            List<EmployeeInfo> similar = new ArrayList<>();

            for (EmployeeInfo emp : employeeInfo.values()) {
                if (emp.getId().equals(this.id)) continue;

                double skillSimilarity = calculateSkillSimilarity(emp);
                if (skillSimilarity >= minSkillMatch) {
                    similar.add(emp);
                }
            }

            // Sort by skill similarity (highest first)
            similar.sort((a, b) -> {
                double aScore = calculateSkillSimilarity(a);
                double bScore = calculateSkillSimilarity(b);
                return Double.compare(bScore, aScore);
            });

            return similar;
        }
    }

    // Attendance Configuration
    public static class AttendanceConfig {
        private String employeeId;
        private int roundingInterval = 5; // minutes
        private boolean enableHalfDay = true;
        private int minHoursForHalfDay = 4;
        private int gracePeriod = 5; // minutes
        private int maxLateAllowed = 480; // minutes
        private int maxEarlyOut = 30; // minutes
        private boolean autoBreakDeduction = true;
        private int breakDuration = 30; // minutes

        public AttendanceConfig() {}

        public AttendanceConfig(String employeeId) {
            this.employeeId = employeeId;
        }

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public int getRoundingInterval() { return roundingInterval; }
        public void setRoundingInterval(int roundingInterval) { this.roundingInterval = roundingInterval; }

        public boolean isEnableHalfDay() { return enableHalfDay; }
        public void setEnableHalfDay(boolean enableHalfDay) { this.enableHalfDay = enableHalfDay; }

        public int getMinHoursForHalfDay() { return minHoursForHalfDay; }
        public void setMinHoursForHalfDay(int minHoursForHalfDay) { this.minHoursForHalfDay = minHoursForHalfDay; }

        public int getGracePeriod() { return gracePeriod; }
        public void setGracePeriod(int gracePeriod) { this.gracePeriod = gracePeriod; }

        public int getMaxLateAllowed() { return maxLateAllowed; }
        public void setMaxLateAllowed(int maxLateAllowed) { this.maxLateAllowed = maxLateAllowed; }

        public int getMaxEarlyOut() { return maxEarlyOut; }
        public void setMaxEarlyOut(int maxEarlyOut) { this.maxEarlyOut = maxEarlyOut; }

        public boolean isAutoBreakDeduction() { return autoBreakDeduction; }
        public void setAutoBreakDeduction(boolean autoBreakDeduction) { this.autoBreakDeduction = autoBreakDeduction; }

        public int getBreakDuration() { return breakDuration; }
        public void setBreakDuration(int breakDuration) { this.breakDuration = breakDuration; }
    }

    // Overtime Record
    public static class OvertimeRecord {
        private String notes;
        private String id;
        private String employeeId;
        private LocalDate date;
        private double hours;
        private double rate;
        private String type; // NORMAL, HOLIDAY, COVERAGE
        private boolean approved;
        private boolean paid;
        private LocalDateTime approvalTime;
        private String approvedBy;
        private double amount;
        private String coverageForEmployeeId; // NEW: Who is being covered
        private String coverageRequestId; // NEW: Reference to coverage request

        public OvertimeRecord() {}

        public OvertimeRecord(String employeeId, LocalDate date, double hours, String type) {
            this.id = UUID.randomUUID().toString();
            this.employeeId = employeeId;
            this.date = date;
            this.hours = hours;
            this.type = type;
            this.rate = "HOLIDAY".equals(type) ? systemConfig.getOtRateHoliday() :
                    "COVERAGE".equals(type) ? systemConfig.getOtRateNormal() * 1.2 : // 20% extra for coverage
                            systemConfig.getOtRateNormal();
            this.approved = systemConfig.isAutoApproveOT();
            this.paid = false;
            this.amount = calculateAmount();
        }

        private double calculateAmount() {
            EmployeeInfo emp = employeeInfo.get(employeeId);
            if (emp != null) {
                return emp.getHourlyWage() * hours * rate;
            }
            return 0;
        }

        public boolean approve(String approverId) {
            if (!approved) {
                this.approved = true;
                this.approvalTime = LocalDateTime.now();
                this.approvedBy = approverId;
                return true;
            }
            return false;
        }

        public boolean markAsPaid() {
            if (approved && !paid) {
                this.paid = true;
                return true;
            }
            return false;
        }

        // Getters and Setters
        public String getNotes() {return notes;}
        public void setNotes(String notes) {this.notes=notes;}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public double getHours() { return hours; }
        public void setHours(double hours) { this.hours = hours; }

        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isApproved() { return approved; }
        public void setApproved(boolean approved) { this.approved = approved; }

        public boolean isPaid() { return paid; }
        public void setPaid(boolean paid) { this.paid = paid; }

        public LocalDateTime getApprovalTime() { return approvalTime; }
        public void setApprovalTime(LocalDateTime approvalTime) { this.approvalTime = approvalTime; }

        public String getApprovedBy() { return approvedBy; }
        public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }

        public String getCoverageForEmployeeId() { return coverageForEmployeeId; }
        public void setCoverageForEmployeeId(String coverageForEmployeeId) { this.coverageForEmployeeId = coverageForEmployeeId; }

        public String getCoverageRequestId() { return coverageRequestId; }
        public void setCoverageRequestId(String coverageRequestId) { this.coverageRequestId = coverageRequestId; }
    }
    // OT Request Class
    public static class OTRequest {
        private String id;
        private String employeeId;
        private LocalDate date;
        private double requestedHours;
        private String reason;
        private String type; // NORMAL, HOLIDAY
        private String status; // PENDING, APPROVED, REJECTED
        private String managerId;
        private LocalDateTime requestedAt;
        private LocalDateTime reviewedAt;
        private String reviewNotes;

        public OTRequest() {}

        public OTRequest(String employeeId, LocalDate date, double requestedHours,
                         String reason, String type) {
            this.id = UUID.randomUUID().toString();
            this.employeeId = employeeId;
            this.date = date;
            this.requestedHours = requestedHours;
            this.reason = reason;
            this.type = type;
            this.status = "PENDING";
            this.requestedAt = LocalDateTime.now();
        }

        public boolean approve(String managerId, String notes) {
            if ("PENDING".equals(status)) {
                this.status = "APPROVED";
                this.managerId = managerId;
                this.reviewedAt = LocalDateTime.now();
                this.reviewNotes = notes;
                return true;
            }
            return false;
        }

        public boolean reject(String managerId, String notes) {
            if ("PENDING".equals(status)) {
                this.status = "REJECTED";
                this.managerId = managerId;
                this.reviewedAt = LocalDateTime.now();
                this.reviewNotes = notes;
                return true;
            }
            return false;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public double getRequestedHours() { return requestedHours; }
        public void setRequestedHours(double requestedHours) { this.requestedHours = requestedHours; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getManagerId() { return managerId; }
        public void setManagerId(String managerId) { this.managerId = managerId; }

        public LocalDateTime getRequestedAt() { return requestedAt; }
        public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

        public LocalDateTime getReviewedAt() { return reviewedAt; }
        public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

        public String getReviewNotes() { return reviewNotes; }
        public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    }

    // NEW: Leave Coverage Request Class
    public static class LeaveCoverageRequest {
        private String id;
        private String absentEmployeeId;
        private LocalDate leaveDate;
        private String shiftName;
        private String department;
        private Set<String> requiredSkills = new HashSet<>();
        private String managerId;
        private LocalDateTime requestedAt;
        private String status; // PENDING, ASSIGNED, COMPLETED, CANCELLED
        private List<OTCoverageAssignment> assignments = new ArrayList<>();
        private String notes;
        private double skillThreshold = 50.0; // Minimum skill match percentage

        public LeaveCoverageRequest() {}

        public LeaveCoverageRequest(String absentEmployeeId, LocalDate leaveDate, String shiftName) {
            this.id = UUID.randomUUID().toString();
            this.absentEmployeeId = absentEmployeeId;
            this.leaveDate = leaveDate;
            this.shiftName = shiftName;

            EmployeeInfo emp = employeeInfo.get(absentEmployeeId);
            if (emp != null) {
                this.department = emp.getDepartment();
                this.requiredSkills = new HashSet<>(emp.getSkills());
                this.managerId = emp.getManagerId();
            }

            this.requestedAt = LocalDateTime.now();
            this.status = "PENDING";
            this.notes = "Leave coverage required for " + (emp != null ? emp.getName() : absentEmployeeId);
        }

        // Assign coverage to employee (basic version)
        public boolean assignCoverage(String assignedEmployeeId, double hours, String managerId) {
            if (!"PENDING".equals(status) && !"ASSIGNED".equals(status)) {
                return false;
            }

            EmployeeInfo assignedEmp = employeeInfo.get(assignedEmployeeId);
            if (assignedEmp == null) {
                return false;
            }

            OTCoverageAssignment assignment = new OTCoverageAssignment(
                    this.id, assignedEmployeeId, this.absentEmployeeId,
                    this.leaveDate, hours, this.shiftName, "COVERAGE"
            );

            assignments.add(assignment);
            otCoverageAssignments.put(assignment.getId(), assignment);
            this.status = "ASSIGNED";

            // Create overtime record for coverage
            OvertimeRecord otRecord = new OvertimeRecord(
                    assignedEmployeeId, leaveDate, hours, "COVERAGE"
            );
            otRecord.setCoverageForEmployeeId(this.absentEmployeeId);
            otRecord.setCoverageRequestId(this.id);
            otRecord.setApproved(true); // Auto-approved for coverage assignments
            otRecord.setApprovedBy(managerId);
            otRecord.setApprovalTime(LocalDateTime.now());

            overtimeRecords.put(otRecord.getId(), otRecord);

            return true;
        }

        // NEW: Assign coverage with wage calculation
        public OTCoverageAssignment assignCoverageWithWage(String assignedEmployeeId,
                                                           double hours,
                                                           String managerId,
                                                           String coverageType) {
            if (!"PENDING".equals(status) && !"ASSIGNED".equals(status)) {
                return null;
            }

            EmployeeInfo assignedEmp = employeeInfo.get(assignedEmployeeId);
            if (assignedEmp == null) {
                return null;
            }

            EmployeeInfo absentEmp = employeeInfo.get(absentEmployeeId);
            if (absentEmp == null) {
                return null;
            }

            // Check if employee can cover with minimum skill threshold
            if (!assignedEmp.canCoverFor(absentEmp, shiftName, skillThreshold)) {
                return null;
            }

            OTCoverageAssignment assignment = new OTCoverageAssignment(
                    this.id, assignedEmployeeId, this.absentEmployeeId,
                    this.leaveDate, hours, this.shiftName, coverageType
            );

            assignments.add(assignment);
            otCoverageAssignments.put(assignment.getId(), assignment);
            this.status = "ASSIGNED";

            // Calculate OT wage for coverage
            double otWage = assignedEmp.calculateCoverageWage(hours, coverageType);

            // Create overtime record for coverage with calculated wage
            OvertimeRecord otRecord = new OvertimeRecord(
                    assignedEmployeeId, leaveDate, hours, "COVERAGE"
            );
            otRecord.setCoverageForEmployeeId(this.absentEmployeeId);
            otRecord.setCoverageRequestId(this.id);
            otRecord.setApproved(true);
            otRecord.setApprovedBy(managerId);
            otRecord.setApprovalTime(LocalDateTime.now());
            otRecord.setRate(systemConfig.getOtRateNormal() *
                    ("EMERGENCY".equals(coverageType) ? 1.5 :
                            "HOLIDAY".equals(coverageType) ? systemConfig.getOtRateHoliday() / systemConfig.getOtRateNormal() : 1.2));
            otRecord.setAmount(otWage); // Set calculated amount
            otRecord.setCoverageForEmployeeId(absentEmployeeId);
            otRecord.setCoverageRequestId(this.id);

            overtimeRecords.put(otRecord.getId(), otRecord);

            // Send notification to assigned employee
            if (systemConfig.isNotifyOTApproval()) {
                sendNotification(assignedEmployeeId,
                        "COVERAGE_ASSIGNED",
                        "You've been assigned to cover " + absentEmp.getName() +
                                "'s " + shiftName + " shift on " + leaveDate +
                                " for " + hours + " hours. Estimated OT pay: $" +
                                String.format("%.2f", otWage));
            }

            return assignment;
        }

        // Complete coverage
        public boolean completeCoverage() {
            if ("ASSIGNED".equals(status)) {
                this.status = "COMPLETED";
                return true;
            }
            return false;
        }

        // Cancel coverage
        public boolean cancelCoverage() {
            if ("PENDING".equals(status) || "ASSIGNED".equals(status)) {
                this.status = "CANCELLED";

                // Remove any OT records created for this coverage
                overtimeRecords.values().removeIf(ot ->
                        this.id.equals(ot.getCoverageRequestId())
                );

                // Remove assignments
                assignments.forEach(assignment ->
                        otCoverageAssignments.remove(assignment.getId())
                );
                assignments.clear();

                return true;
            }
            return false;
        }

        // Get suitable employees for coverage (basic version)
        public List<EmployeeInfo> getSuitableEmployees() {
            List<EmployeeInfo> suitable = new ArrayList<>();

            for (EmployeeInfo emp : employeeInfo.values()) {
                // Skip absent employee
                if (emp.getId().equals(absentEmployeeId)) {
                    continue;
                }

                // Check if employee can cover
                EmployeeInfo absentEmp = employeeInfo.get(absentEmployeeId);
                if (absentEmp != null && emp.canCoverFor(absentEmp, shiftName, skillThreshold)) {
                    suitable.add(emp);
                }
            }

            return suitable;
        }
        // NEW: Employee Coverage Match class for ranking suitable employees
        public static class EmployeeCoverageMatch {
            private final EmployeeInfo employee;
            private final double skillScore;
            private final boolean sameDepartment;
            private final boolean nightShiftCertified;
            private final double hourlyWage;
            private final double weeklyOTHours;

            public EmployeeCoverageMatch(EmployeeInfo employee, double skillScore,
                                         boolean sameDepartment, boolean nightShiftCertified,
                                         double hourlyWage, double weeklyOTHours) {
                this.employee = employee;
                this.skillScore = skillScore;
                this.sameDepartment = sameDepartment;
                this.nightShiftCertified = nightShiftCertified;
                this.hourlyWage = hourlyWage;
                this.weeklyOTHours = weeklyOTHours;
            }

            public double getTotalScore() {
                double score = skillScore;

                // Bonus for same department
                if (sameDepartment) score += 20;

                // Bonus for night shift certification (if it's a night shift)
                if (nightShiftCertified) score += 15;

                // Penalty for high weekly OT (to distribute OT evenly)
                if (weeklyOTHours > systemConfig.getMaxWeeklyOTHours() * 0.7) {
                    score -= 30; // Heavy penalty if already worked 70% of max OT
                } else if (weeklyOTHours > systemConfig.getMaxWeeklyOTHours() * 0.5) {
                    score -= 15; // Moderate penalty if worked 50% of max OT
                }

                // Slight preference for lower wage employees (cost optimization)
                if (hourlyWage < systemConfig.getMinimumHourlyWage() * 1.5) {
                    score += 5;
                }

                return Math.max(0, score); // Ensure score doesn't go negative
            }

            // Get coverage suitability level
            public String getSuitabilityLevel() {
                double score = getTotalScore();
                if (score >= 80) return "EXCELLENT";
                if (score >= 60) return "GOOD";
                if (score >= 40) return "AVERAGE";
                return "LOW";
            }

            // Calculate OT wage for different hour options
            public Map<String, Double> getOTWageOptions(String coverageType) {
                Map<String, Double> wages = new HashMap<>();
                EmployeeInfo emp = getEmployee();

                if (emp != null) {
                    wages.put("1h", emp.calculateCoverageWage(1, coverageType));
                    wages.put("2h", emp.calculateCoverageWage(2, coverageType));
                    wages.put("3h", emp.calculateCoverageWage(3, coverageType));
                }

                return wages;
            }

            // Getters
            public EmployeeInfo getEmployee() { return employee; }
            public double getSkillScore() { return skillScore; }
            public boolean isSameDepartment() { return sameDepartment; }
            public boolean isNightShiftCertified() { return nightShiftCertified; }
            public double getHourlyWage() { return hourlyWage; }
            public double getWeeklyOTHours() { return weeklyOTHours; }
        }
        // NEW: Get suitable employees with skill matching and ranking
// In LeaveCoverageRequest class
        public List<EmployeeCoverageMatch> getSuitableEmployeesWithScore(double customSkillThreshold) {
            List<EmployeeCoverageMatch> matches = new ArrayList<>();

            EmployeeInfo absentEmp = employeeInfo.get(absentEmployeeId);
            if (absentEmp == null) {
                return matches;
            }

            // Use custom threshold if provided, otherwise use default
            double thresholdToUse = customSkillThreshold > 0 ? customSkillThreshold : this.skillThreshold;

            System.out.println("🔍 Looking for suitable employees with skill threshold: " + thresholdToUse + "%");
            System.out.println("   Absent employee: " + absentEmp.getName() + " (" + absentEmp.getDepartment() + ")");
            System.out.println("   Required skills: " + absentEmp.getSkills());

            for (EmployeeInfo emp : employeeInfo.values()) {
                // Skip absent employee
                if (emp.getId().equals(absentEmployeeId)) {
                    continue;
                }

                // Calculate skill similarity score
                double skillScore = emp.calculateSkillSimilarity(absentEmp);

                System.out.println("   Checking " + emp.getName() + " (" + emp.getDepartment() + "):");
                System.out.println("     - Skills: " + emp.getSkills());
                System.out.println("     - Skill similarity: " + String.format("%.1f%%", skillScore));
                System.out.println("     - Same department: " + emp.getDepartment().equals(absentEmp.getDepartment()));
                System.out.println("     - Can work shift: " + emp.canWorkShift(shiftName));

                // Check if can cover with the threshold
                if (emp.canCoverFor(absentEmp, shiftName, thresholdToUse)) {
                    System.out.println("     ✅ SUITABLE - Adding to list");

                    EmployeeCoverageMatch match = new EmployeeCoverageMatch(
                            emp,
                            skillScore,
                            emp.getDepartment().equals(absentEmp.getDepartment()),
                            emp.hasSkill("NightShiftCertified") && "Night".equals(shiftName),
                            emp.getHourlyWage(),
                            emp.getWeeklyOTHours()
                    );

                    matches.add(match);
                } else {
                    System.out.println("     ❌ NOT SUITABLE");
                }
            }

            // Sort by total score (highest first)
            matches.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));

            System.out.println("✅ Found " + matches.size() + " suitable employees");
            return matches;
        }

        // Overloaded method with default threshold
        public List<EmployeeCoverageMatch> getSuitableEmployeesWithScore() {
            return getSuitableEmployeesWithScore(this.skillThreshold);
        }

        // NEW: Get coverage assignments summary
        public Map<String, Object> getCoverageSummary() {
            Map<String, Object> summary = new HashMap<>();

            double totalHours = assignments.stream()
                    .mapToDouble(OTCoverageAssignment::getAssignedHours)
                    .sum();

            double totalWage = assignments.stream()
                    .mapToDouble(OTCoverageAssignment::getOtWage)
                    .sum();

            summary.put("totalAssignments", assignments.size());
            summary.put("totalHours", totalHours);
            summary.put("totalWage", totalWage);
            summary.put("averageHours", assignments.isEmpty() ? 0 : totalHours / assignments.size());

            return summary;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getAbsentEmployeeId() { return absentEmployeeId; }
        public void setAbsentEmployeeId(String absentEmployeeId) { this.absentEmployeeId = absentEmployeeId; }

        public LocalDate getLeaveDate() { return leaveDate; }
        public void setLeaveDate(LocalDate leaveDate) { this.leaveDate = leaveDate; }

        public String getShiftName() { return shiftName; }
        public void setShiftName(String shiftName) { this.shiftName = shiftName; }

        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }

        public Set<String> getRequiredSkills() { return requiredSkills; }
        public void setRequiredSkills(Set<String> requiredSkills) { this.requiredSkills = requiredSkills; }

        public String getManagerId() { return managerId; }
        public void setManagerId(String managerId) { this.managerId = managerId; }

        public LocalDateTime getRequestedAt() { return requestedAt; }
        public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public List<OTCoverageAssignment> getAssignments() { return assignments; }
        public void setAssignments(List<OTCoverageAssignment> assignments) { this.assignments = assignments; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public double getSkillThreshold() { return skillThreshold; }
        public void setSkillThreshold(double skillThreshold) { this.skillThreshold = skillThreshold; }
    }

    // NEW: OT Coverage Assignment Class
    public static class OTCoverageAssignment {
        private String id;
        private String coverageRequestId;
        private String assignedEmployeeId;
        private String coveredEmployeeId;
        private LocalDate coverageDate;
        private double assignedHours;
        private String shiftName;
        private LocalDateTime assignedAt;
        private String assignedBy;
        private String status; // ASSIGNED, WORKING, COMPLETED, CANCELLED
        private String coverageType; // COVERAGE, EMERGENCY, HOLIDAY
        private double otWage; // Calculated OT wage
        private String notes;
        private boolean paid;

        public OTCoverageAssignment() {}

        public OTCoverageAssignment(String coverageRequestId, String assignedEmployeeId,
                                    String coveredEmployeeId, LocalDate coverageDate,
                                    double assignedHours, String shiftName) {
            this(coverageRequestId, assignedEmployeeId, coveredEmployeeId,
                    coverageDate, assignedHours, shiftName, "COVERAGE");
        }

        public OTCoverageAssignment(String coverageRequestId, String assignedEmployeeId,
                                    String coveredEmployeeId, LocalDate coverageDate,
                                    double assignedHours, String shiftName, String coverageType) {
            this.id = UUID.randomUUID().toString();
            this.coverageRequestId = coverageRequestId;
            this.assignedEmployeeId = assignedEmployeeId;
            this.coveredEmployeeId = coveredEmployeeId;
            this.coverageDate = coverageDate;
            this.assignedHours = assignedHours;
            this.shiftName = shiftName;
            this.coverageType = coverageType;
            this.assignedAt = LocalDateTime.now();
            this.assignedBy = "MANAGER001";
            this.status = "ASSIGNED";
            this.paid = false;
            this.notes = "OT coverage assignment for leave coverage";

            // Calculate OT wage
            EmployeeInfo emp = employeeInfo.get(assignedEmployeeId);
            if (emp != null) {
                this.otWage = emp.calculateCoverageWage(assignedHours, coverageType);
            }
        }

        // NEW: Mark assignment as working
        public boolean markAsWorking() {
            if ("ASSIGNED".equals(status)) {
                this.status = "WORKING";
                return true;
            }
            return false;
        }

        // NEW: Complete the assignment
        public boolean complete() {
            if ("ASSIGNED".equals(status) || "WORKING".equals(status)) {
                this.status = "COMPLETED";
                return true;
            }
            return false;
        }

        // NEW: Cancel the assignment
        public boolean cancel(String reason) {
            if (!"COMPLETED".equals(status) && !"CANCELLED".equals(status)) {
                this.status = "CANCELLED";
                this.notes = (this.notes != null ? this.notes + " | " : "") +
                        "Cancelled: " + reason;
                return true;
            }
            return false;
        }

        // NEW: Mark as paid
        public boolean markAsPaid() {
            if ("COMPLETED".equals(status) && !paid) {
                this.paid = true;

                // Update corresponding overtime record
                overtimeRecords.values().stream()
                        .filter(ot -> "COVERAGE".equals(ot.getType()) &&
                                ot.getEmployeeId().equals(assignedEmployeeId) &&
                                ot.getDate().equals(coverageDate) &&
                                Math.abs(ot.getHours() - assignedHours) < 0.01)
                        .findFirst()
                        .ifPresent(OvertimeRecord::markAsPaid);

                return true;
            }
            return false;
        }

        // NEW: Get assignment details for frontend
        public Map<String, Object> getAssignmentDetails() {
            Map<String, Object> details = new HashMap<>();

            EmployeeInfo assignedEmp = employeeInfo.get(assignedEmployeeId);
            EmployeeInfo coveredEmp = employeeInfo.get(coveredEmployeeId);

            details.put("id", id);
            details.put("assignedEmployeeName", assignedEmp != null ? assignedEmp.getName() : assignedEmployeeId);
            details.put("coveredEmployeeName", coveredEmp != null ? coveredEmp.getName() : coveredEmployeeId);
            details.put("coverageDate", coverageDate.toString());
            details.put("assignedHours", assignedHours);
            details.put("shiftName", shiftName);
            details.put("coverageType", coverageType);
            details.put("otWage", otWage);
            details.put("formattedWage", String.format("$%.2f", otWage));
            details.put("status", status);
            details.put("paid", paid);
            details.put("assignedAt", assignedAt.toString());
            details.put("assignedBy", assignedBy);

            // Add skill information
            if (assignedEmp != null && coveredEmp != null) {
                double skillMatch = assignedEmp.calculateSkillSimilarity(coveredEmp);
                details.put("skillMatch", skillMatch);
                details.put("skillMatchFormatted", String.format("%.1f%%", skillMatch));
            }

            return details;
        }

        // NEW: Validate if assignment is still valid
        public boolean isValid() {
            // Check if assigned employee still exists
            if (!employeeInfo.containsKey(assignedEmployeeId)) {
                return false;
            }

            // Check if covered employee still exists
            if (!employeeInfo.containsKey(coveredEmployeeId)) {
                return false;
            }

            // Check if assignment date is not in the past (for future assignments)
            if (coverageDate.isBefore(LocalDate.now())) {
                return "COMPLETED".equals(status) || "CANCELLED".equals(status);
            }

            return true;
        }

        // NEW: Calculate overtime multiplier based on coverage type
        public double getOTMultiplier() {
            return switch (coverageType) {
                case "EMERGENCY" -> systemConfig.getOtRateNormal() * 1.5;
                case "HOLIDAY" -> systemConfig.getOtRateHoliday();
                default -> systemConfig.getOtRateNormal() * 1.2;
            };
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getCoverageRequestId() { return coverageRequestId; }
        public void setCoverageRequestId(String coverageRequestId) { this.coverageRequestId = coverageRequestId; }

        public String getAssignedEmployeeId() { return assignedEmployeeId; }
        public void setAssignedEmployeeId(String assignedEmployeeId) { this.assignedEmployeeId = assignedEmployeeId; }

        public String getCoveredEmployeeId() { return coveredEmployeeId; }
        public void setCoveredEmployeeId(String coveredEmployeeId) { this.coveredEmployeeId = coveredEmployeeId; }

        public LocalDate getCoverageDate() { return coverageDate; }
        public void setCoverageDate(LocalDate coverageDate) { this.coverageDate = coverageDate; }

        public double getAssignedHours() { return assignedHours; }
        public void setAssignedHours(double assignedHours) { this.assignedHours = assignedHours; }

        public String getShiftName() { return shiftName; }
        public void setShiftName(String shiftName) { this.shiftName = shiftName; }

        public LocalDateTime getAssignedAt() { return assignedAt; }
        public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

        public String getAssignedBy() { return assignedBy; }
        public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getCoverageType() { return coverageType; }
        public void setCoverageType(String coverageType) { this.coverageType = coverageType; }

        public double getOtWage() { return otWage; }
        public void setOtWage(double otWage) { this.otWage = otWage; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }

        public boolean isPaid() { return paid; }
        public void setPaid(boolean paid) { this.paid = paid; }
    }

    // Compliance Violation
    public static class ComplianceViolation {
        private String id;
        private String employeeId;
        private String violationType;
        private String description;
        private LocalDate date;
        private String shiftName;
        private boolean resolved;
        private String resolutionNotes;
        private LocalDateTime resolvedAt;

        public ComplianceViolation() {}

        public ComplianceViolation(String employeeId, String violationType, String description,
                                   LocalDate date, String shiftName) {
            this.id = UUID.randomUUID().toString();
            this.employeeId = employeeId;
            this.violationType = violationType;
            this.description = description;
            this.date = date;
            this.shiftName = shiftName;
            this.resolved = false;
        }

        public boolean resolve(String notes) {
            if (!resolved) {
                this.resolved = true;
                this.resolutionNotes = notes;
                this.resolvedAt = LocalDateTime.now();
                return true;
            }
            return false;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEmployeeId() { return employeeId; }
        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

        public String getViolationType() { return violationType; }
        public void setViolationType(String violationType) { this.violationType = violationType; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public String getShiftName() { return shiftName; }
        public void setShiftName(String shiftName) { this.shiftName = shiftName; }

        public boolean isResolved() { return resolved; }
        public void setResolved(boolean resolved) { this.resolved = resolved; }

        public String getResolutionNotes() { return resolutionNotes; }
        public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

        public LocalDateTime getResolvedAt() { return resolvedAt; }
        public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    }

    // Shift configuration with exact times
    private static final Map<String, ShiftTime> SHIFT_TIMES = Map.of(
            "Morning", new ShiftTime(LocalTime.of(9, 0), LocalTime.of(17, 0)),
            "Afternoon", new ShiftTime(LocalTime.of(13, 0), LocalTime.of(21, 0)),
            "Night", new ShiftTime(LocalTime.of(21, 0), LocalTime.of(5, 0))
    );

    // Shift colors for UI
    private static final Map<String, String> SHIFT_COLORS = Map.of(
            "Morning", "#4CAF50",    // Green
            "Afternoon", "#FF9800",  // Orange
            "Night", "#F44336"       // Red
    );

    // NEW: OT Coverage color
    private static final String OT_COVERAGE_COLOR = "#FFC107"; // Amber

    // Holidays
    private static final Set<LocalDate> HOLIDAYS = Set.of(
            LocalDate.of(2025, 1, 1),   // New Year
            LocalDate.of(2025, 12, 25), // Christmas
            LocalDate.of(2025, 7, 4)    // Independence Day
    );

    // Indian Names Data
    private static final String[][] INDIAN_MALE_NAMES = {
            {"Aarav", "Sharma"}, {"Vihaan", "Verma"}, {"Aditya", "Singh"}, {"Vivaan", "Patel"},
            {"Arjun", "Kumar"}, {"Sai", "Reddy"}, {"Reyansh", "Gupta"}, {"Ayaan", "Malik"},
            {"Atharv", "Yadav"}, {"Advik", "Jain"}, {"Dhruv", "Chauhan"}, {"Krishna", "Rao"},
            {"Ishaan", "Mishra"}, {"Shaurya", "Das"}, {"Rudra", "Nair"}, {"Aryan", "Joshi"},
            {"Mohammed", "Khan"}, {"Karthik", "Shah"}, {"Pranav", "Mehta"}, {"Rohan", "Choudhary"},
            {"Siddharth", "Bose"}, {"Arnav", "Sinha"}, {"Aarush", "Saxena"}, {"Samar", "Kapoor"},
            {"Veer", "Tripathi"},{"Kabir", "Mehta"}, {"Yug", "Agarwal"}, {"Reyansh", "Jain"}, {"Viaan", "Shah"}, {"Advik", "Gupta"},
            {"Kiaan", "Singh"}, {"Shivansh", "Yadav"}, {"Prithvi", "Reddy"}, {"Aarush", "Patel"}, {"Darsh", "Verma"},
            {"Neil", "Chauhan"}, {"Abeer", "Rao"}, {"Zayan", "Khan"}, {"Ranbir", "Kapoor"}, {"Hrithik", "Malhotra"},
            {"Varun", "Dhawan"}, {"Siddhant", "Chaturvedi"}, {"Ayush", "Sharma"}, {"Manish", "Pandey"}, {"Rakesh", "Mishra"},
            {"Suresh", "Nair"}, {"Anil", "Joshi"}, {"Vikram", "Bhat"}, {"Rajesh", "Iyer"}, {"Deepak", "Pillai"},
            {"Nitin", "Hegde"}, {"Sachin", "Tendulkar"}, {"Rahul", "Dravid"}, {"Virat", "Kohli"}, {"Rohit", "Sharma"},
            {"Ajay", "Devgn"}, {"Salman", "Khan"}, {"Aamir", "Khan"}, {"Shahid", "Kapoor"}, {"Ranveer", "Singh"},
            {"Akshay", "Kumar"}, {"Tiger", "Shroff"}, {"Kartik", "Aaryan"}, {"Sidharth", "Malhotra"}, {"Vicky", "Kaushal"},
            {"Arjun", "Kapoor"}, {"Ishaan", "Khatter"}, {"Rajkummar", "Rao"}, {"Pankaj", "Tripathi"}, {"Nawazuddin", "Siddiqui"},
            {"Manoj", "Bajpayee"}, {"Irrfan", "Khan"}, {"Naseeruddin", "Shah"}, {"Anupam", "Kher"}, {"Boman", "Irani"}

    };

    private static final String[][] INDIAN_FEMALE_NAMES = {
            {"Aaradhya", "Sharma"}, {"Ananya", "Verma"}, {"Diya", "Singh"}, {"Ishita", "Patel"},
            {"Kavya", "Kumar"}, {"Myra", "Reddy"}, {"Navya", "Gupta"}, {"Prisha", "Malik"},
            {"Riya", "Yadav"}, {"Saanvi", "Jain"}, {"Sara", "Chauhan"}, {"Tara", "Rao"},
            {"Veda", "Mishra"}, {"Zara", "Das"}, {"Aanya", "Nair"}, {"Aditi", "Joshi"},
            {"Anika", "Shah"}, {"Avni", "Mehta"}, {"Chhaya", "Choudhary"}, {"Dia", "Bose"},
            {"Esha", "Sinha"}, {"Gauri", "Saxena"}, {"Hansa", "Kapoor"}, {"Ira", "Tripathi"},
            {"Jiya", "Pandey"},{"Aadhya", "Sharma"}, {"Saanvi", "Verma"}, {"Kiara", "Singh"}, {"Aaradhya", "Patel"}, {"Ananya", "Kumar"},
            {"Diya", "Reddy"}, {"Pihu", "Gupta"}, {"Myra", "Malik"}, {"Inaya", "Yadav"}, {"Ahana", "Jain"},
            {"Reyna", "Chauhan"}, {"Zara", "Rao"}, {"Kyra", "Khan"}, {"Viva", "Shah"}, {"Riya", "Mehta"},
            {"Sia", "Agarwal"}, {"Avni", "Joshi"}, {"Ira", "Nair"}, {"Jiya", "Pandey"}, {"Mira", "Sinha"},
            {"Tara", "Kapoor"}, {"Veda", "Tripathi"}, {"Amaira", "Bose"}, {"Niya", "Saxena"}, {"Sana", "Choudhary"},
            {"Deepika", "Padukone"}, {"Priyanka", "Chopra"}, {"Alia", "Bhat"}, {"Katrina", "Kaif"}, {"Anushka", "Sharma"},
            {"Kareena", "Kapoor"}, {"Madhuri", "Dixit"}, {"Aishwarya", "Rai"}, {"Sonam", "Kapoor"}, {"Shraddha", "Kapoor"},
            {"Parineeti", "Chopra"}, {"Taapsee", "Pannu"}, {"Kangana", "Ranaut"}, {"Vidya", "Balan"}, {"Rani", "Mukherjee"},
            {"Kajol", "Devgan"}, {"Juhi", "Chawla"}, {"Sridevi", "Kapoor"}, {"Rekha", "Ganesan"}, {"Hema", "Malini"},
            {"Zeenat", "Aman"}, {"Waheeda", "Rehman"}, {"Nutan", "Behl"}, {"Madhubala", "Khan"}, {"Meena", "Kumari"}

    };

    // Static attendance service instance
    private final static AttendanceService attendanceService = new AttendanceService();

    // Static OT request tracking
    private final static Map<String, OTRequest> otRequests = new HashMap<>();

    public static void main(String[] args) {
        String port = System.getenv("PORT");
        if (port == null || port.isEmpty()) {
            port = "8083";
        }

        // Read data directory from environment
        String dataDir = System.getenv("DATA_DIR");
        if (dataDir == null || dataDir.isEmpty()) {
            dataDir = System.getProperty("user.dir") + "/data";
        }

        // Configure Quarkus to listen on all network interfaces
        System.setProperty("quarkus.http.host", "0.0.0.0");
        System.setProperty("quarkus.http.port", port);
        System.setProperty("app.data.dir", dataDir);

        // Create data directory if it doesn't exist
        new File(dataDir).mkdirs();

        System.out.println("🚀 Starting Shift Scheduler...");
        System.out.println("🌐 Host: 0.0.0.0 (all network interfaces)");
        System.out.println("🔌 Port: " + port);
        System.out.println("📁 Data directory: " + dataDir);

        Quarkus.run(args);
    }

    // Enhanced Attendance Service with all new features
    public static class AttendanceService {
        private final static Map<String, List<AttendanceRecord>> attendanceRecords = new HashMap<>();
        private final static Map<String, Boolean> employeeClockStatus = new HashMap<>();
        private final static Map<String, List<BreakRecord>> employeeBreaks = new HashMap<>();

        // NEW METHOD: Clear all clock status and attendance records when generating new shifts
        public static void clearAllClockStatusAndAttendance() {
            employeeClockStatus.clear();
            attendanceRecords.clear();
            employeeBreaks.clear();
            System.out.println("🔄 Cleared all clock status and attendance records for new shift generation");
        }

        public static AttendanceRecord clockIn(String employeeId, LocalDateTime clockInTime, String scheduledShift) {
            System.out.println("🕒 ATTEMPTING CLOCK-IN FOR: " + employeeId + " at " + clockInTime);

            // 1. Verify employee exists
            if (!employeeInfo.containsKey(employeeId)) {
                System.out.println("❌ CLOCK-IN FAILED: Employee " + employeeId + " not found in system");
                throw new IllegalStateException("Employee " + employeeId + " not found in system");
            }

            // 2. Check if employee is already clocked in
            if (isClockedIn(employeeId)) {
                EmployeeInfo empInfo = employeeInfo.get(employeeId);
                System.out.println("❌ CLOCK-IN FAILED: " + empInfo.getName() + " (" + employeeId + ") is already clocked in");
                throw new IllegalStateException("Employee " + employeeId + " is already clocked in");
            }

            // 3. Validate if employee has a scheduled shift for today
            if (scheduledShift == null || scheduledShift.isEmpty()) {
                EmployeeInfo empInfo = employeeInfo.get(employeeId);
                System.out.println("❌ CLOCK-IN FAILED: " + empInfo.getName() + " has no scheduled shift for today");
                throw new IllegalStateException("Employee " + employeeId + " has no scheduled shift for today");
            }

            // 4. Check if employee is on leave
            LocalDate today = clockInTime.toLocalDate();
            String todayStr = today.toString();

            if (employeeLeaves.containsKey(employeeId) &&
                    employeeLeaves.get(employeeId).contains(todayStr)) {
                EmployeeInfo empInfo = employeeInfo.get(employeeId);
                System.out.println("❌ CLOCK-IN FAILED: " + empInfo.getName() + " is on leave today");
                throw new IllegalStateException("Employee " + employeeId + " is on leave today");
            }

            // 5. Validate clock-in time against scheduled shift
            validateClockInTime(clockInTime, scheduledShift, employeeId);

            // 6. Create attendance record
            AttendanceRecord record = new AttendanceRecord(employeeId, clockInTime);
            record.setScheduledShift(scheduledShift, getShiftStartTime(today, scheduledShift));

            // 7. Calculate lateness with rounding
            calculateLateness(record, clockInTime, scheduledShift);

            // 8. Check compliance
            checkCompliance(record, employeeId);

            // 9. Store the record
            attendanceRecords.computeIfAbsent(employeeId, k -> new ArrayList<>()).add(record);
            employeeClockStatus.put(employeeId, true);

            // 10. Log success
            EmployeeInfo empInfo = employeeInfo.get(employeeId);
            System.out.println("✅ CLOCK-IN SUCCESS: " + empInfo.getName() + " (" + employeeId + ") at " + clockInTime +
                    " for " + scheduledShift + " shift" +
                    (record.isLate() ? " (LATE: " + record.getLateMinutes() + " minutes)" : ""));

            // 11. Send notification
            sendNotification(employeeId, "CLOCK_IN", "Clocked in for " + scheduledShift + " shift");

            return record;
        }

        public static AttendanceRecord clockOut(String employeeId, LocalDateTime clockOutTime) {
            // Check if employee is clocked in
            if (!isClockedIn(employeeId)) {
                throw new IllegalStateException("Employee " + employeeId + " is not clocked in");
            }

            List<AttendanceRecord> employeeRecords = attendanceRecords.get(employeeId);
            if (employeeRecords == null || employeeRecords.isEmpty()) {
                throw new IllegalStateException("No clock-in record found for employee: " + employeeId);
            }

            // Find the most recent clock-in without clock-out
            AttendanceRecord lastRecord = employeeRecords.stream()
                    .filter(record -> record.getClockOutTime() == null)
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new IllegalStateException("No active clock-in found"));

            // Validate clock-out time
            validateClockOutTime(clockOutTime, lastRecord);

            // Apply rounding
            clockOutTime = applyRounding(clockOutTime, getAttendanceConfig(employeeId).getRoundingInterval());

            lastRecord.clockOut(clockOutTime);
            employeeClockStatus.put(employeeId, false);

            // Calculate worked hours with break deduction
            calculateWorkedHours(lastRecord, employeeId);

            // Check for overtime
            checkOvertime(lastRecord, employeeId);

            System.out.println("✅ Clock OUT - " + employeeId + " at " + clockOutTime);
            System.out.println("📊 Hours worked: " + lastRecord.getHoursWorked() + " hours");

            // Send notification
            sendNotification(employeeId, "CLOCK_OUT", "Clocked out after " +
                    String.format("%.2f", lastRecord.getHoursWorked()) + " hours");

            return lastRecord;
        }

        public static BreakRecord startBreak(String employeeId, LocalDateTime breakStartTime) {
            if (!isClockedIn(employeeId)) {
                throw new IllegalStateException("Employee must be clocked in to start break");
            }

            BreakRecord breakRecord = new BreakRecord(employeeId, breakStartTime);
            employeeBreaks.computeIfAbsent(employeeId, k -> new ArrayList<>()).add(breakRecord);

            System.out.println("⏸️ Break started for " + employeeId + " at " + breakStartTime);
            return breakRecord;
        }

        public static BreakRecord endBreak(String employeeId, LocalDateTime breakEndTime) {
            List<BreakRecord> breaks = employeeBreaks.get(employeeId);
            if (breaks == null || breaks.isEmpty()) {
                throw new IllegalStateException("No active break found for employee: " + employeeId);
            }

            BreakRecord lastBreak = breaks.stream()
                    .filter(b -> b.getBreakEndTime() == null)
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new IllegalStateException("No active break found"));

            lastBreak.endBreak(breakEndTime);

            System.out.println("⏸️ Break ended for " + employeeId + " at " + breakEndTime +
                    " (Duration: " + lastBreak.getDurationMinutes() + " minutes)");
            return lastBreak;
        }

        private static void validateClockInTime(LocalDateTime clockInTime, String scheduledShift, String employeeId) {
            LocalDate today = clockInTime.toLocalDate();
            ShiftTime shiftTime = SHIFT_TIMES.get(scheduledShift);

            if (shiftTime == null) {
                throw new IllegalStateException("Invalid shift type: " + scheduledShift);
            }

            // Check female shift restrictions
            EmployeeInfo empInfo = employeeInfo.get(employeeId);
            if (empInfo != null && !empInfo.canWorkShift(scheduledShift)) {
                throw new IllegalStateException("Employee " + employeeId + " cannot work " +
                        scheduledShift + " shift due to gender restrictions");
            }

            LocalDateTime shiftStart = LocalDateTime.of(today, shiftTime.getStartTime());
            LocalDateTime shiftEnd = LocalDateTime.of(today, shiftTime.getEndTime());

            // Handle night shift (crosses midnight)
            if (scheduledShift.equals("Night")) {
                shiftEnd = shiftEnd.plusDays(1); // Night shift ends next day at 5 AM
            }

            AttendanceConfig config = getAttendanceConfig(employeeId);

            // Allow clock-in 30 minutes before shift start and up to maxLateAllowed after shift start
            LocalDateTime earliestClockIn = shiftStart.minusMinutes(30);
            LocalDateTime latestClockIn = shiftStart.plusMinutes(config.getMaxLateAllowed());

            if (clockInTime.isBefore(earliestClockIn)) {
                throw new IllegalStateException("Cannot clock in more than 30 minutes before shift start. " +
                        "Shift starts at " + shiftTime.getStartTime() + ". Earliest clock-in: " + earliestClockIn.toLocalTime());
            }

            if (clockInTime.isAfter(latestClockIn)) {
                throw new IllegalStateException("Cannot clock in more than " + config.getMaxLateAllowed() +
                        " minutes after shift start. Shift started at " + shiftTime.getStartTime() +
                        ". Latest clock-in: " + latestClockIn.toLocalTime());
            }

            // Check if it's the correct day for the shift
            if (scheduledShift.equals("Night")) {
                // For night shift, allow clock-in on the day the shift starts (evening)
                // or early next morning (if it's still within the shift)
                if (clockInTime.toLocalDate().isAfter(today) && clockInTime.isAfter(shiftEnd)) {
                    throw new IllegalStateException("Night shift has already ended. Shift ended at " + shiftTime.getEndTime());
                }
            }
        }

        private static void validateClockOutTime(LocalDateTime clockOutTime, AttendanceRecord record) {
            LocalDateTime clockInTime = record.getClockInTime();

            // Cannot clock out before clocking in
            if (clockOutTime.isBefore(clockInTime)) {
                throw new IllegalStateException("Clock-out time cannot be before clock-in time");
            }

            // Minimum work duration check
            Duration minWorkDuration = Duration.ofMinutes(15);
            if (Duration.between(clockInTime, clockOutTime).compareTo(minWorkDuration) < 0) {
                throw new IllegalStateException("Minimum work duration is 15 minutes");
            }

            // Check early out - get config using record's employeeId
            AttendanceConfig config = getAttendanceConfig(record.getEmployeeId());
            LocalDateTime scheduledEnd = record.getScheduledStart().plusHours(8);
            if (clockOutTime.isBefore(scheduledEnd.minusMinutes(config.getMaxEarlyOut()))) {
                throw new IllegalStateException("Cannot clock out more than " + config.getMaxEarlyOut() +
                        " minutes before shift end. Shift ends at " + scheduledEnd.toLocalTime());
            }
        }

        private static void calculateLateness(AttendanceRecord record, LocalDateTime clockInTime, String scheduledShift) {
            ShiftTime shiftTime = SHIFT_TIMES.get(scheduledShift);
            LocalDateTime scheduledStart = LocalDateTime.of(clockInTime.toLocalDate(), shiftTime.getStartTime());

            AttendanceConfig config = getAttendanceConfig(record.getEmployeeId());

            // Apply grace period
            LocalDateTime gracePeriodEnd = scheduledStart.plusMinutes(config.getGracePeriod());

            // NEW: Only mark as late if more than 15 minutes late
            long lateMinutes;
            if (clockInTime.isAfter(gracePeriodEnd)) {
                lateMinutes = Duration.between(scheduledStart, clockInTime).toMinutes();

                // NEW: Only show lateness if more than 15 minutes late
                if (lateMinutes > 15) {
                    record.setLateMinutes(lateMinutes);
                    record.setLate(true);

                    // Check if lateness exceeds maximum allowed
                    if (lateMinutes > config.getMaxLateAllowed()) {
                        record.setAttendanceStatus("ABSENT");
                    } else {
                        record.setAttendanceStatus("LATE");
                    }
                } else {
                    // NEW: Between 10-15 minutes, don't mark as late in the system
                    record.setLateMinutes(0);
                    record.setLate(false);
                    record.setAttendanceStatus("PRESENT");
                }
            } else {
                record.setLateMinutes(0);
                record.setLate(false);
                record.setAttendanceStatus("PRESENT");
            }
        }

        private static void calculateWorkedHours(AttendanceRecord record, String employeeId) {
            LocalDateTime clockInTime = record.getClockInTime();
            LocalDateTime clockOutTime = record.getClockOutTime();

            if (clockOutTime == null) return;

            Duration totalDuration = Duration.between(clockInTime, clockOutTime);

            // Deduct breaks
            Duration breakDuration = Duration.ZERO;
            List<BreakRecord> breaks = employeeBreaks.getOrDefault(employeeId, new ArrayList<>());
            for (BreakRecord br : breaks) {
                if (br.getBreakStartTime().isAfter(clockInTime) &&
                        br.getBreakEndTime() != null &&
                        br.getBreakEndTime().isBefore(clockOutTime)) {
                    breakDuration = breakDuration.plus(Duration.between(br.getBreakStartTime(), br.getBreakEndTime()));
                }
            }

            // Auto deduct lunch break if enabled
            AttendanceConfig config = getAttendanceConfig(employeeId);
            if (config.isAutoBreakDeduction() && totalDuration.toHours() >= 6) {
                breakDuration = breakDuration.plusMinutes(config.getBreakDuration());
            }

            Duration workedDuration = totalDuration.minus(breakDuration);
            double hoursWorked = workedDuration.toMinutes() / 60.0;

            // Check for half day
            if (hoursWorked >= config.getMinHoursForHalfDay() && hoursWorked < systemConfig.getMinHoursForPresent()) {
                record.setAttendanceStatus("HALF_DAY");
            } else if (hoursWorked < config.getMinHoursForHalfDay()) {
                record.setAttendanceStatus("ABSENT");
            }

            record.setHoursWorked(hoursWorked);
            record.setBreakMinutes(breakDuration.toMinutes());
        }

        private static void checkOvertime(AttendanceRecord record, String employeeId) {
            double hoursWorked = record.getHoursWorked();
            double scheduledHours = 8.0; // Assuming 8-hour shifts

            if (hoursWorked > scheduledHours) {
                double otHours = hoursWorked - scheduledHours;

                // Check daily OT limit
                if (otHours > systemConfig.getMaxDailyOTHours()) {
                    otHours = systemConfig.getMaxDailyOTHours();
                    addComplianceViolation(employeeId, "OT_EXCEEDED",
                            "Exceeded daily OT limit of " + systemConfig.getMaxDailyOTHours() + " hours",
                            record.getClockInTime().toLocalDate(), record.getScheduledShift());
                }

                if (otHours > 0) {
                    String otType = isHoliday(record.getClockInTime().toLocalDate()) ? "HOLIDAY" : "NORMAL";
                    OvertimeRecord otRecord = new OvertimeRecord(employeeId,
                            record.getClockInTime().toLocalDate(), otHours, otType);

                    overtimeRecords.put(otRecord.getId(), otRecord);

                    record.setOvertimeHours(otHours);
                    record.setOvertimeRate(otRecord.getRate());

                    System.out.println("💰 Overtime recorded for " + employeeId + ": " +
                            otHours + " hours at " + otRecord.getRate() + "x rate");

                    if (systemConfig.isNotifyOTApproval() && !systemConfig.isAutoApproveOT()) {
                        sendNotification(employeeInfo.get(employeeId).getManagerId(),
                                "OT_APPROVAL_REQUIRED",
                                employeeId + " worked " + otHours + " hours overtime");
                    }
                }
            }
        }

        private static void checkCompliance(AttendanceRecord record, String employeeId) {
            EmployeeInfo empInfo = employeeInfo.get(employeeId);
            if (empInfo == null) return;

            LocalDate date = record.getClockInTime().toLocalDate();
            String shiftName = record.getScheduledShift();

            // Check consecutive working days
            int consecutiveDays = getConsecutiveWorkingDays(employeeId, date);
            if (consecutiveDays > systemConfig.getMaxConsecutiveWorkingDays()) {
                addComplianceViolation(employeeId, "MAX_CONSECUTIVE_DAYS",
                        "Exceeded maximum consecutive working days: " + consecutiveDays + " > " +
                                systemConfig.getMaxConsecutiveWorkingDays(), date, shiftName);
            }

            // Check consecutive night shifts
            if ("Night".equals(shiftName)) {
                int consecutiveNights = getConsecutiveNightShifts(employeeId, date);
                if (consecutiveNights > systemConfig.getMaxConsecutiveNightShifts()) {
                    addComplianceViolation(employeeId, "MAX_CONSECUTIVE_NIGHTS",
                            "Exceeded maximum consecutive night shifts: " + consecutiveNights + " > " +
                                    systemConfig.getMaxConsecutiveNightShifts(), date, shiftName);
                }
            }

            // Check weekly hours
            double weeklyHours = getWeeklyHours(employeeId, date);
            EmployeeCategory category = systemConfig.getCategories().get(empInfo.getCategory());
            if (category != null && weeklyHours > category.getWeeklyHoursLimit()) {
                addComplianceViolation(employeeId, "WEEKLY_HOURS_LIMIT",
                        "Exceeded weekly hours limit: " + weeklyHours + " > " +
                                category.getWeeklyHoursLimit(), date, shiftName);
            }

            // Check government compliance
            if (weeklyHours > systemConfig.getMaxWeeklyHoursLaw()) {
                addComplianceViolation(employeeId, "GOV_WEEKLY_HOURS",
                        "Exceeded government weekly hours limit: " + weeklyHours + " > " +
                                systemConfig.getMaxWeeklyHoursLaw(), date, shiftName);
            }

            // Check minimum wage compliance
            if (empInfo.getHourlyWage() < systemConfig.getMinimumHourlyWage()) {
                addComplianceViolation(employeeId, "MINIMUM_WAGE",
                        "Hourly wage below minimum: " + empInfo.getHourlyWage() + " < " +
                                systemConfig.getMinimumHourlyWage(), date, shiftName);
            }

            // Check transport requirement
            if (empInfo.requiresLateNightTransport(shiftName) && !empInfo.isRequiresTransport()) {
                addComplianceViolation(employeeId, "TRANSPORT_REQUIRED",
                        "Transport required for late night shift but not arranged", date, shiftName);
            }
        }

        private static void addComplianceViolation(String employeeId, String violationType,
                                                   String description, LocalDate date, String shiftName) {
            ComplianceViolation violation = new ComplianceViolation(employeeId, violationType,
                    description, date, shiftName);
            complianceViolations.add(violation);

            System.out.println("⚠️ Compliance Violation: " + employeeId + " - " + description);

            if (systemConfig.isNotifyComplianceViolation()) {
                EmployeeInfo empInfo = employeeInfo.get(employeeId);
                if (empInfo != null && empInfo.getManagerId() != null) {
                    sendNotification(empInfo.getManagerId(), "COMPLIANCE_VIOLATION", description);
                }
            }
        }

        private static LocalDateTime applyRounding(LocalDateTime time, int roundingInterval) {
            if (roundingInterval <= 0) return time;

            int minute = time.getMinute();
            int remainder = minute % roundingInterval;

            if (remainder >= roundingInterval / 2) {
                return time.plusMinutes(roundingInterval - remainder);
            } else {
                return time.minusMinutes(remainder);
            }
        }

        private static AttendanceConfig getAttendanceConfig(String employeeId) {
            return attendanceConfigs.computeIfAbsent(employeeId, k -> {
                AttendanceConfig config = new AttendanceConfig(employeeId);
                // Set defaults from system config
                config.setRoundingInterval(systemConfig.getRoundingIntervalMinutes());
                config.setGracePeriod(systemConfig.getGracePeriodMinutes());
                config.setMaxLateAllowed(systemConfig.getMaxLateAllowedMinutes());
                config.setMaxEarlyOut(systemConfig.getMaxEarlyAllowedMinutes());
                config.setAutoBreakDeduction(systemConfig.isAutoLunchBreakDeduction());
                config.setBreakDuration(systemConfig.getLunchBreakMinutes());
                return config;
            });
        }

        private static int getConsecutiveWorkingDays(String employeeId, LocalDate currentDate) {
            int consecutive = 0;
            LocalDate date = currentDate;

            while (true) {
                LocalDate checkDate = date.minusDays(1);
                boolean worked = attendanceRecords.getOrDefault(employeeId, new ArrayList<>())
                        .stream()
                        .anyMatch(r -> r.getClockInTime().toLocalDate().equals(checkDate) &&
                                r.getHoursWorked() >= systemConfig.getMinHoursForPresent());

                if (worked) {
                    consecutive++;
                    date = checkDate;
                } else {
                    break;
                }
            }

            return consecutive;
        }

        private static int getConsecutiveNightShifts(String employeeId, LocalDate currentDate) {
            int consecutive = 0;
            LocalDate date = currentDate;

            while (true) {
                LocalDate checkDate = date.minusDays(1);
                boolean workedNight = attendanceRecords.getOrDefault(employeeId, new ArrayList<>())
                        .stream()
                        .anyMatch(r -> r.getClockInTime().toLocalDate().equals(checkDate) &&
                                "Night".equals(r.getScheduledShift()) &&
                                r.getHoursWorked() >= systemConfig.getMinHoursForPresent());

                if (workedNight) {
                    consecutive++;
                    date = checkDate;
                } else {
                    break;
                }
            }

            return consecutive;
        }

        private static double getWeeklyHours(String employeeId, LocalDate currentDate) {
            LocalDate weekStart = currentDate.with(DayOfWeek.MONDAY);
            LocalDate weekEnd = currentDate.with(DayOfWeek.SUNDAY);

            return attendanceRecords.getOrDefault(employeeId, new ArrayList<>())
                    .stream()
                    .filter(r -> {
                        LocalDate recordDate = r.getClockInTime().toLocalDate();
                        return !recordDate.isBefore(weekStart) && !recordDate.isAfter(weekEnd);
                    })
                    .mapToDouble(AttendanceRecord::getHoursWorked)
                    .sum();
        }

        // Get today's scheduled shift for employee
        public static String getTodayScheduledShift(String employeeId) {
            LocalDate today = LocalDate.now();
            String todayStr = today.toString();

            // Check if employee exists
            if (!employeeInfo.containsKey(employeeId)) {
                System.out.println("⚠️ Employee not found: " + employeeId);
                return null;
            }

            EmployeeInfo empInfo = employeeInfo.get(employeeId);

            // Check if employee is on leave today
            if (employeeLeaves.containsKey(employeeId) &&
                    employeeLeaves.get(employeeId).stream().anyMatch(rec -> rec.getDate().equals(todayStr))) {
                System.out.println("❌ Employee on leave: " + employeeId + " (" + empInfo.getName() + ")");
                return null;
            }

            // NEW: Look up today's assignments from shiftAssignments map
            Map<String, List<String>> dayAssignments = shiftAssignments.get(todayStr);
            if (dayAssignments != null) {
                for (Map.Entry<String, List<String>> entry : dayAssignments.entrySet()) {
                    String shift = entry.getKey();           // "Morning", "Afternoon", "Night"
                    List<String> assignedEmployees = entry.getValue();

                    if (assignedEmployees != null && assignedEmployees.contains(employeeId)) {
                        System.out.println("✅ Found scheduled shift for " + empInfo.getName() + ": " + shift);
                        return shift;
                    }
                }
            }

            // If no manual assignment found, fall back to default based on department
            String defaultShift = assignDefaultShiftForToday(empInfo);
            if (defaultShift != null) {
                System.out.println("⚠️ No manual shift assigned for " + empInfo.getName() +
                        " today, using default: " + defaultShift);
                return defaultShift;
            }

            return null;
        }

        // Add this helper method to AttendanceService:
        private static String assignDefaultShiftForToday(EmployeeInfo empInfo) {
            String department = empInfo.getDepartment();
            String shift = "Morning"; // default

            if (department != null) {
                switch(department.toLowerCase()) {
                    case "testing":
                    case "devops":
                    case "sales":
                        shift = "Afternoon";
                        break;
                    case "support":
                        // Check if female (cannot work night shift)
                        if ("Female".equalsIgnoreCase(empInfo.getGender()) &&
                                systemConfig.isFemaleShiftRestrictions()) {
                            shift = "Afternoon";
                        } else {
                            shift = "Night";
                        }
                        break;
                    case "development":
                    case "management":
                    case "hr":
                    case "finance":
                    default:
                        shift = "Morning";
                }
            }

            return shift;
        }

        // Check if employee is currently clocked in
        public static boolean isClockedIn(String employeeId) {
            return employeeClockStatus.getOrDefault(employeeId, false);
        }

        // Get current clock status for all employees
        public static Map<String, Boolean> getAllClockStatus() {
            return new HashMap<>(employeeClockStatus);
        }

        // Get currently clocked-in employees
        public static List<String> getClockedInEmployees() {
            return employeeClockStatus.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        public static List<AttendanceRecord> getEmployeeAttendance(String employeeId) {
            return attendanceRecords.getOrDefault(employeeId, new ArrayList<>());
        }

        public static Map<String, Object> getTodayAttendanceSummary() {
            LocalDate today = LocalDate.now();
            Map<String, Object> summary = new HashMap<>();

            List<Map<String, Object>> todayRecords = new ArrayList<>();
            int clockedInCount = 0;
            int presentCount = 0;
            int lateArrivals = 0;
            int halfDays = 0;
            int absentCount = 0;
            int missingClockOuts = 0;
            double totalLateMinutes = 0;
            double totalOvertimeHours = 0;

            for (Map.Entry<String, List<AttendanceRecord>> entry : attendanceRecords.entrySet()) {
                for (AttendanceRecord record : entry.getValue()) {
                    if (record.getClockInTime().toLocalDate().equals(today)) {
                        Map<String, Object> recordMap = new HashMap<>();
                        recordMap.put("employeeId", entry.getKey());
                        recordMap.put("clockIn", record.getClockInTime().toString());
                        recordMap.put("clockOut", record.getClockOutTime() != null ? record.getClockOutTime().toString() : null);
                        recordMap.put("hoursWorked", record.getHoursWorked());
                        recordMap.put("isLate", record.isLate());
                        recordMap.put("lateMinutes", record.getLateMinutes());
                        recordMap.put("scheduledShift", record.getScheduledShift());
                        recordMap.put("attendanceStatus", record.getAttendanceStatus());
                        recordMap.put("overtimeHours", record.getOvertimeHours());
                        recordMap.put("breakMinutes", record.getBreakMinutes());
                        recordMap.put("status", record.getClockOutTime() == null ? "CLOCKED_IN" : "CLOCKED_OUT");

                        todayRecords.add(recordMap);

                        if (record.getClockOutTime() == null) {
                            clockedInCount++;
                        }

                        switch (record.getAttendanceStatus()) {
                            case "PRESENT":
                                presentCount++;
                                break;
                            case "LATE":
                                lateArrivals++;
                                totalLateMinutes += record.getLateMinutes();
                                break;
                            case "HALF_DAY":
                                halfDays++;
                                break;
                            case "ABSENT":
                                absentCount++;
                                break;
                        }

                        if (record.isLate()) {
                            totalLateMinutes += record.getLateMinutes();
                        }

                        if (record.getOvertimeHours() > 0) {
                            totalOvertimeHours += record.getOvertimeHours();
                        }
                    }
                }
            }

            summary.put("date", today.toString());
            summary.put("totalRecords", todayRecords.size());
            summary.put("present", presentCount);
            summary.put("late", lateArrivals);
            summary.put("halfDays", halfDays);
            summary.put("absent", absentCount);
            summary.put("currentlyClockedIn", clockedInCount);
            summary.put("totalLateMinutes", totalLateMinutes);
            summary.put("totalOvertimeHours", totalOvertimeHours);
            summary.put("averageLateMinutes", lateArrivals > 0 ? totalLateMinutes / lateArrivals : 0);
            summary.put("records", todayRecords);

            return summary;
        }

        public static List<OvertimeRecord> getEmployeeOvertime(String employeeId) {
            return overtimeRecords.values().stream()
                    .filter(ot -> ot.getEmployeeId().equals(employeeId))
                    .collect(Collectors.toList());
        }

        public static List<ComplianceViolation> getEmployeeViolations(String employeeId) {
            return complianceViolations.stream()
                    .filter(v -> v.getEmployeeId().equals(employeeId))
                    .collect(Collectors.toList());
        }

        private static LocalDateTime getShiftStartTime(LocalDate date, String shiftName) {
            ShiftTime shiftTime = SHIFT_TIMES.get(shiftName);
            if (shiftTime == null) {
                throw new IllegalArgumentException("Invalid shift name: " + shiftName);
            }
            return LocalDateTime.of(date, shiftTime.getStartTime());
        }

        private static boolean isHoliday(LocalDate date) {
            return HOLIDAYS.contains(date);
        }

        static void sendNotification(String recipientId, String type, String message) {
            // In a real system, this would send email/SMS/push notifications
            System.out.println("📧 Notification to " + recipientId + " [" + type + "]: " + message);

            // NEW: Store notification for frontend retrieval
            if (recipientId != null) {
                Notification notification = new Notification(
                        UUID.randomUUID().toString(),
                        recipientId,
                        type,
                        message,
                        LocalDateTime.now()
                );
                notifications.put(notification.getId(), notification);
            }
        }
    }

    // NEW: Notification class
    public static class Notification {
        private String id;
        private String recipientId;
        private String type;
        private String message;
        private LocalDateTime timestamp;
        private boolean read;

        public Notification() {}

        public Notification(String id, String recipientId, String type, String message, LocalDateTime timestamp) {
            this.id = id;
            this.recipientId = recipientId;
            this.type = type;
            this.message = message;
            this.timestamp = timestamp;
            this.read = false;
        }

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getRecipientId() { return recipientId; }
        public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public boolean isRead() { return read; }
        public void setRead(boolean read) { this.read = read; }
    }

    // NEW: Store notifications
    private final static Map<String, Notification> notifications = new HashMap<>();

    // Enhanced Attendance Record Class
    public static class AttendanceRecord {
        private String employeeId;
        private LocalDateTime clockInTime;
        private LocalDateTime clockOutTime;
        private String scheduledShift;
        private LocalDateTime scheduledStart;
        private boolean isLate = false;
        private long lateMinutes = 0;
        private double hoursWorked = 0;
        private String attendanceStatus = "PRESENT";
        private double overtimeHours = 0;
        private double overtimeRate = 1.0;
        private long breakMinutes = 0;
        private boolean earlyOut = false;
        private long earlyDepartureMinutes = 0;

        public AttendanceRecord(String employeeId, LocalDateTime clockInTime) {
            this.employeeId = employeeId;
            this.clockInTime = clockInTime;
        }

        public void clockOut(LocalDateTime clockOutTime) {
            this.clockOutTime = clockOutTime;
        }

        public void setScheduledShift(String shiftName, LocalDateTime scheduledStart) {
            this.scheduledShift = shiftName;
            this.scheduledStart = scheduledStart;
        }

        public double getHoursWorked() {
            return hoursWorked;
        }

        public void setHoursWorked(double hoursWorked) {
            this.hoursWorked = hoursWorked;
        }

        public boolean isLate() {
            return isLate;
        }

        public void setLate(boolean late) {
            this.isLate = late;
        }

        public long getLateMinutes() {
            return lateMinutes;
        }

        public void setLateMinutes(long lateMinutes) {
            this.lateMinutes = lateMinutes;
        }

        public String getAttendanceStatus() {
            return attendanceStatus;
        }

        public void setAttendanceStatus(String attendanceStatus) {
            this.attendanceStatus = attendanceStatus;
        }

        public double getOvertimeHours() {
            return overtimeHours;
        }

        public void setOvertimeHours(double overtimeHours) {
            this.overtimeHours = overtimeHours;
        }

        public double getOvertimeRate() {
            return overtimeRate;
        }

        public void setOvertimeRate(double overtimeRate) {
            this.overtimeRate = overtimeRate;
        }

        public long getBreakMinutes() {
            return breakMinutes;
        }

        public void setBreakMinutes(long breakMinutes) {
            this.breakMinutes = breakMinutes;
        }

        public boolean isEarlyOut() {
            return earlyOut;
        }

        public void setEarlyOut(boolean earlyOut) {
            this.earlyOut = earlyOut;
        }

        public long getEarlyDepartureMinutes() {
            return earlyDepartureMinutes;
        }

        public void setEarlyDepartureMinutes(long earlyDepartureMinutes) {
            this.earlyDepartureMinutes = earlyDepartureMinutes;
        }

        @JsonIgnore
        public double getOvertimeAmount() {
            EmployeeInfo emp = employeeInfo.get(employeeId);
            if (emp != null) {
                return overtimeHours * emp.getHourlyWage() * overtimeRate;
            }
            return 0;
        }

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public LocalDateTime getClockInTime() { return clockInTime; }
        public LocalDateTime getClockOutTime() { return clockOutTime; }
        public String getScheduledShift() { return scheduledShift; }
        public LocalDateTime getScheduledStart() { return scheduledStart; }

        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public void setClockInTime(LocalDateTime clockInTime) { this.clockInTime = clockInTime; }
        public void setClockOutTime(LocalDateTime clockOutTime) { this.clockOutTime = clockOutTime; }
        public void setScheduledShift(String scheduledShift) { this.scheduledShift = scheduledShift; }
        public void setScheduledStart(LocalDateTime scheduledStart) { this.scheduledStart = scheduledStart; }
    }

    // Break Record Class
    public static class BreakRecord {
        private String employeeId;
        private LocalDateTime breakStartTime;
        private LocalDateTime breakEndTime;
        private String breakType; // LUNCH, REST, PERSONAL

        public BreakRecord(String employeeId, LocalDateTime breakStartTime) {
            this.employeeId = employeeId;
            this.breakStartTime = breakStartTime;
            this.breakType = "REST";
        }

        public void endBreak(LocalDateTime breakEndTime) {
            this.breakEndTime = breakEndTime;
        }

        public long getDurationMinutes() {
            if (breakEndTime == null) return 0;
            return Duration.between(breakStartTime, breakEndTime).toMinutes();
        }

        // Getters and Setters
        public String getEmployeeId() { return employeeId; }
        public LocalDateTime getBreakStartTime() { return breakStartTime; }
        public LocalDateTime getBreakEndTime() { return breakEndTime; }
        public String getBreakType() { return breakType; }

        public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
        public void setBreakStartTime(LocalDateTime breakStartTime) { this.breakStartTime = breakStartTime; }
        public void setBreakEndTime(LocalDateTime breakEndTime) { this.breakEndTime = breakEndTime; }
        public void setBreakType(String breakType) { this.breakType = breakType; }
    }

    // Improved timestamp parsing that handles timezones properly
    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            // If timestamp is in UTC format (ends with Z), convert to system timezone
            if (timestamp.endsWith("Z")) {
                Instant instant = Instant.parse(timestamp);
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            } else {
                // Handle local timestamp (without timezone)
                if (timestamp.contains(".")) {
                    timestamp = timestamp.split("\\.")[0];
                }
                return LocalDateTime.parse(timestamp);
            }
        } catch (Exception e) {
            try {
                String cleanTimestamp = timestamp.replace("Z", "").split("\\.")[0];
                return LocalDateTime.parse(cleanTimestamp);
            } catch (Exception e2) {
                throw new RuntimeException("Invalid timestamp format: " + timestamp, e2);
            }
        }
    }

    @GET
    @Path("/config")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSystemConfig() {
        try {
            return Response.ok(systemConfig).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get system config"))
                    .build();
        }
    }

    @POST
    @Path("/config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateSystemConfig(SystemConfig newConfig) {
        try {
            systemConfig = newConfig;
            System.out.println("✅ System configuration updated");
            return Response.ok(Map.of("status", "success", "message", "Configuration updated")).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to update config: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/employees")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addEmployee(EmployeeInfo employee) {
        try {
            employeeInfo.put(employee.getId(), employee);
            System.out.println("✅ Added employee: " + employee.getName());
            return Response.ok(Map.of("status", "success", "employee", employee)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to add employee: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/employees")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllEmployees() {
        try {
            List<Map<String, Object>> employeeList = new ArrayList<>();
            for (EmployeeInfo empInfo : employeeInfo.values()) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", empInfo.getId());
                map.put("name", empInfo.getName());
                map.put("category", empInfo.getCategory());
                map.put("gender", empInfo.getGender());
                map.put("hourlyWage", empInfo.getHourlyWage());
                map.put("managerId", empInfo.getManagerId());
                map.put("position", empInfo.getPosition());
                map.put("email", empInfo.getEmail());
                map.put("phone", empInfo.getPhone());
                map.put("skills", new ArrayList<>(empInfo.getSkills()));
                map.put("shiftColor", empInfo.getShiftColor());
                map.put("performanceRating", empInfo.getPerformanceRating()); // ← ADD THIS LINE

                employeeList.add(map);
            }
            return Response.ok(employeeList).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get employees"))
                    .build();
        }
    }

    @POST
    @Path("/attendance/config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAttendanceConfig(AttendanceConfig config) {
        try {
            attendanceConfigs.put(config.getEmployeeId(), config);
            System.out.println("✅ Updated attendance config for: " + config.getEmployeeId());
            return Response.ok(Map.of("status", "success", "config", config)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to update config: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/shifts/available-clockin")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEmployeesAvailableForClockIn() {
        try {
            LocalDate today = LocalDate.now();
            String todayStr = today.toString();
            LocalTime currentTime = LocalTime.now();

            System.out.println("🕒 Current time: " + currentTime);
            System.out.println("📅 Today: " + todayStr);

            List<Map<String, Object>> availableEmployees = new ArrayList<>();

            for (Map.Entry<String, EmployeeInfo> entry : employeeInfo.entrySet()) {
                String employeeId = entry.getKey();
                EmployeeInfo empInfo = entry.getValue();

                if (empInfo == null) {
                    System.out.println("⚠️ Employee info not found for ID: " + employeeId);
                    continue;
                }

                // Check if employee is on leave
                boolean isOnLeave = employeeLeaves.containsKey(employeeId) &&
                        employeeLeaves.get(employeeId).contains(todayStr);

                if (isOnLeave) {
                    System.out.println("❌ " + empInfo.getName() + " (" + employeeId + ") is on leave");
                    continue;
                }

                // Get scheduled shift for today
                String scheduledShift = shiftAssignments.get(employeeId).toString();

                if (scheduledShift == null) {
                    System.out.println("❌ " + empInfo.getName() + " (" + employeeId + ") has no scheduled shift");
                    continue;
                }

                // Check if already clocked in
                boolean isClockedIn = AttendanceService.isClockedIn(employeeId);

                if (isClockedIn) {
                    System.out.println("❌ " + empInfo.getName() + " (" + employeeId + ") is already clocked in");
                    continue;
                }

                // Check gender restrictions
                if (!empInfo.canWorkShift(scheduledShift)) {
                    System.out.println("❌ " + empInfo.getName() + " cannot work " + scheduledShift + " shift due to restrictions");
                    continue;
                }

                // Get shift times
                ShiftTime shiftTime = SHIFT_TIMES.get(scheduledShift);
                if (shiftTime == null) {
                    System.out.println("❌ " + empInfo.getName() + " has invalid shift: " + scheduledShift);
                    continue;
                }

                // Check if current time is within shift window
                LocalTime shiftStart = shiftTime.getStartTime();
                LocalTime shiftEnd = shiftTime.getEndTime();

                boolean isShiftActive;
                LocalTime earliestClockIn = shiftStart.minusMinutes(30);

                if (scheduledShift.equals("Night")) {
                    // Night shift: 9 PM to 5 AM next day
                    isShiftActive = (currentTime.isAfter(earliestClockIn) || currentTime.isBefore(shiftEnd));
                } else {
                    // Regular shifts: allow from 30 minutes before shift until shift end
                    isShiftActive = currentTime.isAfter(earliestClockIn) && currentTime.isBefore(shiftEnd);
                }

                System.out.println("👤 Checking " + empInfo.getName() + " (" + employeeId + ")" +
                        " | Shift: " + scheduledShift +
                        " | Time: " + shiftStart + "-" + shiftEnd +
                        " | Active: " + isShiftActive);

                if (isShiftActive) {
                    Map<String, Object> empData = new HashMap<>();
                    empData.put("id", employeeId);
                    empData.put("name", empInfo.getName());
                    empData.put("shift", scheduledShift);
                    empData.put("shiftStart", shiftStart.toString());
                    empData.put("shiftEnd", shiftEnd.toString());
                    empData.put("category", empInfo.getCategory());

                    empData.put("position", empInfo.getPosition());
                    empData.put("hourlyWage", empInfo.getHourlyWage());
                    empData.put("shiftColor", SHIFT_COLORS.get(scheduledShift));
                    empData.put("employeeColor", empInfo.getShiftColor());
                    empData.put("isShiftActive", true);
                    empData.put("canClockIn", true);
                    empData.put("gender", empInfo.getGender());
                    empData.put("managerId", empInfo.getManagerId());
                    empData.put("email", empInfo.getEmail());
                    empData.put("phone", empInfo.getPhone());

                    // Calculate lateness - NEW LOGIC
                    long lateMinutes = 0;
                    boolean isLate = false;
                    LocalTime gracePeriodEnd = shiftStart.plusMinutes(systemConfig.getGracePeriodMinutes());

                    if (currentTime.isAfter(gracePeriodEnd)) {
                        lateMinutes = Duration.between(shiftStart, currentTime).toMinutes();
                        // Only mark as late if more than 15 minutes
                        isLate = lateMinutes > 15;
                    }

                    empData.put("lateMinutes", lateMinutes);
                    empData.put("isLate", isLate);
                    empData.put("showLateInUI", lateMinutes > 0); // Show in UI if any lateness

                    availableEmployees.add(empData);
                    System.out.println("✅ " + empInfo.getName() + " (" + employeeId + ") is available for clock-in" +
                            (lateMinutes > 0 ? " (Late: " + lateMinutes + " minutes)" : ""));
                }
            }

            System.out.println("📋 Total available employees: " + availableEmployees.size());

            return Response.ok(Map.of(
                    "date", todayStr,
                    "currentTime", currentTime.toString(),
                    "availableEmployees", availableEmployees,
                    "count", availableEmployees.size()
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get available employees: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/shifts/clock-in")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)

    public Response clockIn(Map<String, Object> input) {
        String employeeId = (String) input.get("employeeId");
        String timestamp = (String) input.get("timestamp");

        if (employeeId == null || timestamp == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing employeeId or timestamp"))
                    .build();
        }

        try {
            LocalDateTime clockInTime = parseTimestamp(timestamp);
            LocalDate today = clockInTime.toLocalDate();
            String todayStr = today.toString();

            System.out.println("🔍 Attempting clock-in for: " + employeeId + " on " + todayStr);

            // 1. Check if employee exists
            EmployeeInfo empInfo = employeeInfo.get(employeeId);
            if (empInfo == null) {
                System.out.println("❌ Employee not found: " + employeeId);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Employee not found: " + employeeId))
                        .build();
            }

            // 2. Check if employee is on leave today
            if (employeeLeaves.containsKey(employeeId) &&
                    employeeLeaves.get(employeeId).contains(todayStr)) {
                System.out.println("❌ Employee on leave: " + employeeId + " on " + todayStr);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Employee is on leave today"))
                        .build();
            }

            // 3. Get scheduled shift from employeeShifts map
            String scheduledShift = shiftAssignments.get(employeeId).toString();

            if (scheduledShift == null) {
                // If no shift assigned, check if this is a new day and assign a default shift
                scheduledShift = assignDefaultShift(employeeId, empInfo);
                shiftAssignments.computeIfAbsent(todayStr, k -> new HashMap<>())
                        .computeIfAbsent(scheduledShift, k -> new ArrayList<>())
                        .add(employeeId);

                saveAssignments();
                System.out.println("⚠️ No shift found, assigned default: " + scheduledShift);
            }

            System.out.println("✅ Found scheduled shift for " + employeeId + ": " + scheduledShift);

            // 4. Check if already clocked in
            if (AttendanceService.isClockedIn(employeeId)) {
                System.out.println("❌ Already clocked in: " + employeeId);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Employee is already clocked in"))
                        .build();
            }

            // 5. Validate the shift
            ShiftTime shiftTime = SHIFT_TIMES.get(scheduledShift);
            if (shiftTime == null) {
                System.out.println("❌ Invalid shift type: " + scheduledShift);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid shift type: " + scheduledShift))
                        .build();
            }

            // 6. Check gender restrictions
            if (!empInfo.canWorkShift(scheduledShift)) {
                System.out.println("❌ Employee cannot work this shift due to restrictions");
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Employee cannot work " + scheduledShift + " shift due to restrictions"))
                        .build();
            }

            // 7. Proceed with clock-in
            AttendanceRecord record = AttendanceService.clockIn(employeeId, clockInTime, scheduledShift);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("employeeId", employeeId);
            response.put("employeeName", empInfo.getName());
            response.put("clockInTime", clockInTime.toString());
            response.put("scheduledShift", scheduledShift);
            response.put("shiftStart", shiftTime.getStartTime().toString());
            response.put("shiftEnd", shiftTime.getEndTime().toString());
            response.put("isLate", record.isLate());
            response.put("actualLateMinutes", record.getLateMinutes());
            response.put("showLateInUI", record.getLateMinutes() > 0 && record.getLateMinutes() > 15);
            response.put("attendanceStatus", record.getAttendanceStatus());
            response.put("shiftColor", SHIFT_COLORS.get(scheduledShift));
            response.put("employeeColor", empInfo.getShiftColor());

            response.put("position", empInfo.getPosition());

            String message = record.isLate() ?
                    "Clocked in successfully (LATE: " + record.getLateMinutes() + " minutes)" :
                    "Clocked in successfully (On time)";
            response.put("message", message);

            System.out.println("✅ Clock-in successful for " + employeeId);
            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    // Helper method to assign a default shift
    private String assignDefaultShift(String employeeId, EmployeeInfo empInfo) {
        // Default logic: assign based on department
        String department = empInfo.getDepartment();
        String shift = "Morning"; // default

        if (department != null) {
            switch(department) {
                case "Development":
                case "Management":
                    shift = "Morning";
                    break;
                case "Testing":
                case "DevOps":
                    shift = "Afternoon";
                    break;
                case "Support":
                    // Check if female (cannot work night shift)
                    if ("Female".equalsIgnoreCase(empInfo.getGender()) && systemConfig.isFemaleShiftRestrictions()) {
                        shift = "Afternoon";
                    } else {
                        shift = "Night";
                    }
                    break;
            }
        }

        System.out.println("Assigned default shift " + shift + " to " + employeeId);
        return shift;
    }

    // Helper method to get today's scheduled shift
    private String getTodaysScheduledShiftFromDatabase(String employeeId, LocalDate date) {
        String dateStr = date.toString();

        // First, check if employee is on leave today
        if (employeeLeaves.containsKey(employeeId) &&
                employeeLeaves.get(employeeId).contains(dateStr)) {
            System.out.println("Employee " + employeeId + " is on leave today: " + dateStr);
            return null;
        }

        // Check shiftAssignments for this date
        Map<String, List<String>> dayAssignments = shiftAssignments.get(dateStr);

        if (dayAssignments != null) {
            // Check each shift (Morning, Afternoon, Night)
            for (Map.Entry<String, List<String>> entry : dayAssignments.entrySet()) {
                String shift = entry.getKey();
                List<String> employeesInShift = entry.getValue();

                if (employeesInShift != null && employeesInShift.contains(employeeId)) {
                    System.out.println("Found shift in shiftAssignments for " + employeeId + " on " + dateStr + ": " + shift);
                    return shift;
                }
            }
        }

        System.out.println("No shift found for " + employeeId + " on " + dateStr);
        return null;
    }

    @GET
    @Path("/shifts/employee/{employeeId}/today")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEmployeeTodayShift(@PathParam("employeeId") String employeeId) {
        try {
            LocalDate today = LocalDate.now();
            String todayStr = today.toString();

            // Check if employee is on leave
            if (employeeLeaves.containsKey(employeeId) &&
                    employeeLeaves.get(employeeId).contains(todayStr)) {
                return Response.ok(Map.of(
                        "hasShift", false,
                        "onLeave", true,
                        "message", "Employee is on leave today"
                )).build();
            }

            // Get scheduled shift
            String scheduledShift = shiftAssignments.get(employeeId).toString();
            EmployeeInfo empInfo = employeeInfo.get(employeeId);

            if (scheduledShift == null) {
                return Response.ok(Map.of(
                        "hasShift", false,
                        "onLeave", false,
                        "message", "No shift scheduled for today"
                )).build();
            }

            ShiftTime shiftTime = SHIFT_TIMES.get(scheduledShift);

            Map<String, Object> response = new HashMap<>();
            response.put("hasShift", true);
            response.put("shiftName", scheduledShift);
            response.put("startTime", shiftTime.getStartTime().toString());
            response.put("endTime", shiftTime.getEndTime().toString());
            response.put("employeeName", empInfo != null ? empInfo.getName() : employeeId);

            response.put("canWorkShift", empInfo == null || empInfo.canWorkShift(scheduledShift));

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get shift info"))
                    .build();
        }
    }

    @POST
    @Path("/shifts/clock-out")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)

    public Response clockOut(Map<String, Object> input) {
        String employeeId = (String) input.get("employeeId");
        String timestamp = (String) input.get("timestamp");

        if (employeeId == null || timestamp == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing employeeId or timestamp"))
                    .build();
        }

        try {
            LocalDateTime clockOutTime = parseTimestamp(timestamp);
            AttendanceRecord record = AttendanceService.clockOut(employeeId, clockOutTime);

            // Get employee info for response
            EmployeeInfo empInfo = employeeInfo.get(employeeId);
            String employeeName = empInfo != null ? empInfo.getName() : employeeId;

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("employeeId", employeeId);
            response.put("employeeName", employeeName);
            response.put("clockOutTime", clockOutTime.toString());
            response.put("hoursWorked", record.getHoursWorked());
            response.put("isLate", record.isLate());
            response.put("lateMinutes", record.getLateMinutes());
            response.put("attendanceStatus", record.getAttendanceStatus());
            response.put("overtimeHours", record.getOvertimeHours());
            response.put("overtimeRate", record.getOvertimeRate());
            response.put("breakMinutes", record.getBreakMinutes());
            response.put("scheduledShift", record.getScheduledShift());
            response.put("shiftColor", SHIFT_COLORS.get(record.getScheduledShift()));
            response.put("employeeColor", empInfo != null ? empInfo.getShiftColor() : "#607D8B");

            response.put("position", empInfo != null ? empInfo.getPosition() : "Unknown");
            response.put("message", "Clocked out successfully");

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/shifts/break/start")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startBreak(Map<String, Object> input) {
        String employeeId = (String) input.get("employeeId");
        String timestamp = (String) input.get("timestamp");

        if (employeeId == null || timestamp == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing employeeId or timestamp"))
                    .build();
        }

        try {
            LocalDateTime breakStartTime = parseTimestamp(timestamp);
            BreakRecord record = AttendanceService.startBreak(employeeId, breakStartTime);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("employeeId", employeeId);
            response.put("breakStartTime", breakStartTime.toString());
            response.put("breakType", record.getBreakType());
            response.put("message", "Break started");

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/shifts/break/end")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response endBreak(Map<String, Object> input) {
        String employeeId = (String) input.get("employeeId");
        String timestamp = (String) input.get("timestamp");

        if (employeeId == null || timestamp == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing employeeId or timestamp"))
                    .build();
        }

        try {
            LocalDateTime breakEndTime = parseTimestamp(timestamp);
            BreakRecord record = AttendanceService.endBreak(employeeId, breakEndTime);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("employeeId", employeeId);
            response.put("breakEndTime", breakEndTime.toString());
            response.put("breakDuration", record.getDurationMinutes());
            response.put("breakType", record.getBreakType());
            response.put("message", "Break ended");

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/overtime/{employeeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEmployeeOvertime(@PathParam("employeeId") String employeeId) {
        try {
            List<OvertimeRecord> records = AttendanceService.getEmployeeOvertime(employeeId);

            double totalHours = records.stream().mapToDouble(OvertimeRecord::getHours).sum();
            double totalAmount = records.stream().mapToDouble(OvertimeRecord::getAmount).sum();
            double pendingAmount = records.stream()
                    .filter(ot -> !ot.isPaid())
                    .mapToDouble(OvertimeRecord::getAmount)
                    .sum();

            Map<String, Object> summary = new HashMap<>();
            summary.put("employeeId", employeeId);
            summary.put("records", records);
            summary.put("totalHours", totalHours);
            summary.put("totalAmount", totalAmount);
            summary.put("pendingAmount", pendingAmount);
            summary.put("approvedCount", records.stream().filter(OvertimeRecord::isApproved).count());
            summary.put("paidCount", records.stream().filter(OvertimeRecord::isPaid).count());

            return Response.ok(summary).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get overtime records"))
                    .build();
        }
    }

    @POST
    @Path("/overtime/{otId}/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response approveOvertime(@PathParam("otId") String otId, Map<String, Object> input) {
        String approverId = (String) input.get("approverId");

        if (approverId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing approverId"))
                    .build();
        }

        try {
            OvertimeRecord record = overtimeRecords.get(otId);
            if (record == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Overtime record not found"))
                        .build();
            }

            boolean approved = record.approve(approverId);

            if (approved) {
                System.out.println("✅ Overtime approved: " + otId + " by " + approverId);

                if (systemConfig.isNotifyOTApproval()) {
                    sendNotification(record.getEmployeeId(),
                            "OT_APPROVED",
                            "Your overtime of " + record.getHours() + " hours has been approved");
                }

                return Response.ok(Map.of(
                        "status", "success",
                        "message", "Overtime approved",
                        "record", record
                )).build();
            } else {
                return Response.ok(Map.of(
                        "status", "already_approved",
                        "message", "Overtime was already approved"
                )).build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to approve overtime: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/compliance/violations")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getComplianceViolations() {
        try {
            Map<String, Object> summary = new HashMap<>();

            long totalViolations = complianceViolations.size();
            long resolvedViolations = complianceViolations.stream()
                    .filter(ComplianceViolation::isResolved)
                    .count();
            long pendingViolations = totalViolations - resolvedViolations;

            // Group by violation type
            Map<String, Long> violationsByType = complianceViolations.stream()
                    .collect(Collectors.groupingBy(ComplianceViolation::getViolationType,
                            Collectors.counting()));

            // Group by employee
            Map<String, Long> violationsByEmployee = complianceViolations.stream()
                    .collect(Collectors.groupingBy(ComplianceViolation::getEmployeeId,
                            Collectors.counting()));

            summary.put("total", totalViolations);
            summary.put("resolved", resolvedViolations);
            summary.put("pending", pendingViolations);
            summary.put("byType", violationsByType);
            summary.put("byEmployee", violationsByEmployee);
            summary.put("violations", complianceViolations);

            return Response.ok(summary).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get compliance violations"))
                    .build();
        }
    }

    @POST
    @Path("/compliance/violations/{violationId}/resolve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response resolveViolation(@PathParam("violationId") String violationId,
                                     Map<String, Object> input) {
        String resolutionNotes = (String) input.get("resolutionNotes");

        if (resolutionNotes == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing resolutionNotes"))
                    .build();
        }

        try {
            ComplianceViolation violation = complianceViolations.stream()
                    .filter(v -> v.getId().equals(violationId))
                    .findFirst()
                    .orElse(null);

            if (violation == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Violation not found"))
                        .build();
            }

            boolean resolved = violation.resolve(resolutionNotes);

            if (resolved) {
                System.out.println("✅ Compliance violation resolved: " + violationId);

                if (systemConfig.isNotifyComplianceViolation()) {
                    EmployeeInfo empInfo = employeeInfo.get(violation.getEmployeeId());
                    if (empInfo != null && empInfo.getManagerId() != null) {
                        sendNotification(empInfo.getManagerId(),
                                "VIOLATION_RESOLVED",
                                "Compliance violation resolved: " + violation.getDescription());
                    }
                }

                return Response.ok(Map.of(
                        "status", "success",
                        "message", "Violation resolved",
                        "violation", violation
                )).build();
            } else {
                return Response.ok(Map.of(
                        "status", "already_resolved",
                        "message", "Violation was already resolved"
                )).build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to resolve violation: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/shifts/today-shift/{employeeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTodayShift(@PathParam("employeeId") String employeeId) {
        try {
            String scheduledShift = AttendanceService.getTodayScheduledShift(employeeId);

            if (scheduledShift == null) {
                return Response.ok(Map.of(
                        "employeeId", employeeId,
                        "hasShift", false,
                        "message", "No scheduled shift for today or employee is on leave",
                        "shiftColor", "#cccccc"
                )).build();
            }

            ShiftTime shiftTime = SHIFT_TIMES.get(scheduledShift);
            EmployeeInfo empInfo = employeeInfo.get(employeeId);

            Map<String, Object> shiftInfo = new HashMap<>();
            shiftInfo.put("employeeId", employeeId);
            shiftInfo.put("hasShift", true);
            shiftInfo.put("shiftName", scheduledShift);
            shiftInfo.put("startTime", shiftTime.getStartTime().toString());
            shiftInfo.put("endTime", shiftTime.getEndTime().toString());
            shiftInfo.put("isNightShift", "Night".equals(scheduledShift));
            shiftInfo.put("requiresTransport", empInfo != null && empInfo.requiresLateNightTransport(scheduledShift));
            shiftInfo.put("canWorkShift", empInfo == null || empInfo.canWorkShift(scheduledShift));
            shiftInfo.put("shiftColor", SHIFT_COLORS.get(scheduledShift));
            shiftInfo.put("employeeColor", empInfo != null ? empInfo.getShiftColor() : "#607D8B");

            if (empInfo != null && !empInfo.canWorkShift(scheduledShift)) {
                shiftInfo.put("restrictionReason", "Gender-based shift restriction");
            }

            return Response.ok(shiftInfo).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get today's shift"))
                    .build();
        }
    }

    @GET
    @Path("/shifts/clock-status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClockStatus() {
        try {
            Map<String, Boolean> clockStatus = AttendanceService.getAllClockStatus();
            List<String> clockedInEmployees = AttendanceService.getClockedInEmployees();

            // Add shift information to clock status
            Map<String, Object> enhancedClockStatus = new HashMap<>();
            for (Map.Entry<String, Boolean> entry : clockStatus.entrySet()) {
                String employeeId = entry.getKey();
                String scheduledShift = AttendanceService.getTodayScheduledShift(employeeId);
                EmployeeInfo empInfo = employeeInfo.get(employeeId);

                Map<String, Object> employeeStatus = new HashMap<>();
                employeeStatus.put("isClockedIn", entry.getValue());
                employeeStatus.put("scheduledShift", scheduledShift);
                employeeStatus.put("hasShift", scheduledShift != null);
                employeeStatus.put("employeeName", empInfo != null ? empInfo.getName() : employeeId);
                employeeStatus.put("category", empInfo != null ? empInfo.getCategory() : "Unknown");

                employeeStatus.put("shiftColor", scheduledShift != null ? SHIFT_COLORS.get(scheduledShift) : "#cccccc");
                employeeStatus.put("employeeColor", empInfo != null ? empInfo.getShiftColor() : "#607D8B");

                enhancedClockStatus.put(employeeId, employeeStatus);
            }

            return Response.ok(Map.of(
                    "clockStatus", enhancedClockStatus,
                    "clockedInEmployees", clockedInEmployees,
                    "totalClockedIn", clockedInEmployees.size()
            )).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get clock status"))
                    .build();
        }
    }

    @GET
    @Path("/shifts/clock-status/{employeeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEmployeeClockStatus(@PathParam("employeeId") String employeeId) {
        try {
            boolean isClockedIn = AttendanceService.isClockedIn(employeeId);
            String scheduledShift = AttendanceService.getTodayScheduledShift(employeeId);
            EmployeeInfo empInfo = employeeInfo.get(employeeId);

            assert scheduledShift != null;
            return Response.ok(Map.of(
                    "employeeId", employeeId,
                    "employeeName", empInfo != null ? empInfo.getName() : employeeId,
                    "isClockedIn", isClockedIn,
                    "scheduledShift", scheduledShift,
                    "hasShift", true,
                    "category", empInfo != null ? empInfo.getCategory() : "Unknown",

                    "hourlyWage", empInfo != null ? empInfo.getHourlyWage() : 0,
                    "shiftColor", SHIFT_COLORS.get(scheduledShift),
                    "employeeColor", empInfo != null ? empInfo.getShiftColor() : "#607D8B"
            )).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get employee clock status"))
                    .build();
        }
    }

    @GET
    @Path("/shifts/attendance/{employeeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAttendanceRecords(@PathParam("employeeId") String employeeId) {
        try {
            List<AttendanceRecord> records = AttendanceService.getEmployeeAttendance(employeeId);
            List<OvertimeRecord> otRecords = AttendanceService.getEmployeeOvertime(employeeId);
            List<ComplianceViolation> violations = AttendanceService.getEmployeeViolations(employeeId);
            EmployeeInfo empInfo = employeeInfo.get(employeeId);

            // Calculate summary
            double totalHours = records.stream().mapToDouble(AttendanceRecord::getHoursWorked).sum();
            double totalOvertime = records.stream().mapToDouble(AttendanceRecord::getOvertimeHours).sum();
            long totalLateMinutes = records.stream().mapToLong(AttendanceRecord::getLateMinutes).sum();

            long presentDays = records.stream()
                    .filter(r -> "PRESENT".equals(r.getAttendanceStatus()))
                    .count();
            long lateDays = records.stream()
                    .filter(r -> "LATE".equals(r.getAttendanceStatus()))
                    .count();
            long halfDays = records.stream()
                    .filter(r -> "HALF_DAY".equals(r.getAttendanceStatus()))
                    .count();
            long absentDays = records.stream()
                    .filter(r -> "ABSENT".equals(r.getAttendanceStatus()))
                    .count();

            // Convert to serializable format
            List<Map<String, Object>> serializableRecords = records.stream().map(record -> {
                Map<String, Object> recordMap = new HashMap<>();
                recordMap.put("employeeId", record.getEmployeeId());
                recordMap.put("clockIn", record.getClockInTime().toString());
                recordMap.put("clockOut", record.getClockOutTime() != null ? record.getClockOutTime().toString() : null);
                recordMap.put("hoursWorked", record.getHoursWorked());
                recordMap.put("isLate", record.isLate());
                recordMap.put("lateMinutes", record.getLateMinutes());
                recordMap.put("scheduledShift", record.getScheduledShift());
                recordMap.put("attendanceStatus", record.getAttendanceStatus());
                recordMap.put("overtimeHours", record.getOvertimeHours());
                recordMap.put("overtimeRate", record.getOvertimeRate());
                recordMap.put("breakMinutes", record.getBreakMinutes());
                recordMap.put("earlyOut", record.isEarlyOut());
                recordMap.put("earlyDepartureMinutes", record.getEarlyDepartureMinutes());
                recordMap.put("shiftColor", SHIFT_COLORS.get(record.getScheduledShift()));
                return recordMap;
            }).collect(Collectors.toList());

            Map<String, Object> summary = new HashMap<>();
            summary.put("employeeId", employeeId);
            summary.put("employeeName", empInfo != null ? empInfo.getName() : employeeId);
            summary.put("totalRecords", serializableRecords.size());
            summary.put("totalHours", totalHours);
            summary.put("totalOvertime", totalOvertime);
            summary.put("totalLateMinutes", totalLateMinutes);
            summary.put("presentDays", presentDays);
            summary.put("lateDays", lateDays);
            summary.put("halfDays", halfDays);
            summary.put("absentDays", absentDays);
            summary.put("attendanceRate", !records.isEmpty() ? (double) presentDays / records.size() * 100 : 0);
            summary.put("records", serializableRecords);
            summary.put("overtimeRecords", otRecords);
            summary.put("violations", violations);
            summary.put("leaveBalances", empInfo != null ? Map.of(
                    "annual", empInfo.getAnnualLeaveBalance(),
                    "sick", empInfo.getSickLeaveBalance(),
                    "casual", empInfo.getCasualLeaveBalance()
            ) : Map.of());

            return Response.ok(summary).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get attendance records"))
                    .build();
        }
    }

    @GET
    @Path("/shifts/attendance/today")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTodayAttendance() {
        try {
            Map<String, Object> todaySummary = AttendanceService.getTodayAttendanceSummary();
            return Response.ok(todaySummary).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get today's attendance"))
                    .build();
        }
    }

    private static boolean initialShiftsGenerated = false;
    private static boolean optimizationInProgress = false;

    @DELETE
    @Path("/shifts/clear-all")
    @Produces(MediaType.APPLICATION_JSON)

    public Response clearAllAssignments() {
        try {
            int daysCleared = shiftAssignments.size();

            // Count total assignments before clearing
            int totalAssignments = 0;
            for (Map<String, List<String>> dayAssignments : shiftAssignments.values()) {
                for (List<String> employees : dayAssignments.values()) {
                    totalAssignments += employees.size();
                }
            }

            // Clear JSON in-memory map
            shiftAssignments.clear();

            // Clear MySQL
            mysqlService.clearAllAssignments();

            // Save empty state to JSON file
            saveAssignments();

            System.out.println("🗑️ Cleared all " + daysCleared + " days of assignments (" +
                    totalAssignments + " total assignments) from JSON and MySQL");

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "Cleared all assignments from JSON and MySQL",
                    "days_cleared", daysCleared,
                    "assignments_cleared", totalAssignments,
                    "mysql_cleared", true
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(Map.of(
                    "error", "Failed to clear all assignments: " + e.getMessage()
            )).build();
        }
    }
    @DELETE
    @Path("/shifts/clear")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeAssignment(Map<String, Object> input) {
        try {
            System.out.println("=== DELETE /shifts/clear ===");
            System.out.println("Input: " + input);

            // ============ PARSE INPUT - Handle all formats ============
            Object daysObj = input.get("days");
            Object dayObj = input.get("day");
            Object shiftsObj = input.get("shifts");
            Object shiftObj = input.get("shift");
            Object employeesObj = input.get("employees");

            // ============ HANDLE DAYS (supports both 'day' and 'days') ============
            List<String> days = new ArrayList<>();
            if (daysObj != null) {
                // Has "days" (plural)
                if (daysObj instanceof String) {
                    days.add((String) daysObj);
                } else if (daysObj instanceof List) {
                    days = (List<String>) daysObj;
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid format for 'days'. Must be string or array"))
                            .build();
                }
            } else if (dayObj != null) {
                // Has "day" (singular)
                if (dayObj instanceof String) {
                    days.add((String) dayObj);
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid format for 'day'. Must be string"))
                            .build();
                }
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of(
                                "error", "Missing required field",
                                "message", "Provide either 'day' (singular) or 'days' (plural)"
                        ))
                        .build();
            }

            // ============ HANDLE SHIFTS (supports both 'shift' and 'shifts') ============
            List<String> shifts = new ArrayList<>();
            if (shiftsObj != null) {
                // Has "shifts" (plural)
                if (shiftsObj instanceof String) {
                    shifts.add((String) shiftsObj);
                } else if (shiftsObj instanceof List) {
                    shifts = (List<String>) shiftsObj;
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid format for 'shifts'. Must be string or array"))
                            .build();
                }
            } else if (shiftObj != null) {
                // Has "shift" (singular)
                if (shiftObj instanceof String) {
                    shifts.add((String) shiftObj);
                } else {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid format for 'shift'. Must be string"))
                            .build();
                }
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of(
                                "error", "Missing required field",
                                "message", "Provide either 'shift' (singular) or 'shifts' (plural)"
                        ))
                        .build();
            }

            // ============ HANDLE EMPLOYEES (optional) ============
            List<String> employeesToRemove = new ArrayList<>();
            boolean clearEntireShift = false;

            if (employeesObj == null) {
                // No employees specified = clear entire shift
                clearEntireShift = true;
                System.out.println("📋 No employees specified - will clear entire shift(s)");
            } else if (employeesObj instanceof String) {
                String empStr = (String) employeesObj;
                if ("ALL".equalsIgnoreCase(empStr) || "*".equals(empStr)) {
                    clearEntireShift = true;
                    System.out.println("📋 'ALL' keyword detected - will clear entire shift(s)");
                } else {
                    employeesToRemove.add(empStr);
                }
            } else if (employeesObj instanceof List) {
                employeesToRemove = (List<String>) employeesObj;
                System.out.println("📋 Will remove specific employees: " + employeesToRemove);
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid format for 'employees'. Must be string, array, or omitted"))
                        .build();
            }

            // Validate all shifts exist
//            for (String shift : shifts) {
//                if (!SHIFT_TIMES.containsKey(shift)) {
//                    return Response.status(Response.Status.BAD_REQUEST)
//                            .entity(Map.of(
//                                    "error", "Invalid shift: " + shift,
//                                    "valid_shifts", SHIFT_TIMES.keySet()
//                            ))
//                            .build();
//                }
//            }

            // Validate dates
            for (String day : days) {
                try {
                    LocalDate.parse(day);
                } catch (Exception e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid date format: " + day + ". Use YYYY-MM-DD"))
                            .build();
                }
            }

            // ============ PROCESS REMOVALS ============
            Map<String, Object> results = new HashMap<>();
            int totalRemoved = 0;
            int totalShiftsCleared = 0;
            int totalDaysCleared = 0;

            // Track MySQL deletions
            Set<String> datesToClearFromMySQL = new HashSet<>(); // Use Set to avoid duplicates
            int mysqlRemovedCount = 0;
            Map<String, List<String>> mysqlRemovals = new HashMap<>(); // Track per-date, per-shift removals

            for (String day : days) {
                Map<String, List<String>> dayAssignments = shiftAssignments.get(day);

                if (dayAssignments == null) {
                    // Day doesn't exist in assignments
                    results.put(day, Map.of(
                            "status", "NOT_FOUND",
                            "message", "No assignments found for this day"
                    ));
                    continue;
                }

                Map<String, Object> dayResult = new HashMap<>();
                List<Map<String, Object>> shiftResults = new ArrayList<>();
                boolean dayChanged = false;

                for (String shift : shifts) {
                    if (!dayAssignments.containsKey(shift)) {
                        // Shift doesn't exist for this day
                        shiftResults.add(Map.of(
                                "shift", shift,
                                "status", "NOT_FOUND",
                                "message", "No assignments found for this shift on " + day
                        ));
                        continue;
                    }

                    List<String> currentList = dayAssignments.get(shift);
                    int removedFromShift = 0;
                    List<String> notFound = new ArrayList<>();
                    List<String> actuallyRemoved = new ArrayList<>();
                    boolean shiftCleared = false;

                    // ============ CASE 1: Clear entire shift ============
                    if (clearEntireShift) {
                        // Track for MySQL removal
                        actuallyRemoved.addAll(currentList);

                        removedFromShift = currentList.size();
                        dayAssignments.remove(shift);
                        shiftCleared = true;
                        totalShiftsCleared++;

                        shiftResults.add(Map.of(
                                "shift", shift,
                                "status", "CLEARED",
                                "removed_count", removedFromShift,
                                "removed_employees", actuallyRemoved,
                                "action", "CLEARED_ENTIRE_SHIFT"
                        ));

                        System.out.println("🗑️ Cleared entire " + shift + " shift on " + day +
                                " (" + removedFromShift + " employees) from JSON");
                    }
                    // ============ CASE 2: Remove specific employees ============
                    else {
                        for (String empId : employeesToRemove) {
                            if (currentList.remove(empId)) {
                                removedFromShift++;
                                actuallyRemoved.add(empId);
                                System.out.println("    ✅ Removed employee " + empId + " from " + shift + " on " + day + " (JSON)");
                            } else {
                                notFound.add(empId);
                            }
                        }

                        Map<String, Object> shiftResult = new HashMap<>();
                        shiftResult.put("shift", shift);
                        shiftResult.put("status", "PARTIAL");
                        shiftResult.put("removed_count", removedFromShift);
                        shiftResult.put("removed_employees", actuallyRemoved);
                        shiftResult.put("requested_count", employeesToRemove.size());

                        if (!notFound.isEmpty()) {
                            shiftResult.put("not_found", notFound);
                        }

                        // If shift became empty, track it
                        if (currentList.isEmpty()) {
                            dayAssignments.remove(shift);
                            shiftResult.put("shift_became_empty", true);
                            totalShiftsCleared++;
                        } else {
                            shiftResult.put("remaining_count", currentList.size());
                            shiftResult.put("remaining_employees", new ArrayList<>(currentList));
                        }

                        shiftResults.add(shiftResult);
                    }

                    // ============ TRACK FOR MYSQL DELETION ============
                    if (!actuallyRemoved.isEmpty()) {
                        String key = day + "|" + shift;
                        mysqlRemovals.put(key, actuallyRemoved);
                        mysqlRemovedCount += actuallyRemoved.size();
                    }

                    totalRemoved += removedFromShift;
                    if (removedFromShift > 0) {
                        dayChanged = true;
                    }
                }

                // Clean up day if no shifts left
                if (dayAssignments.isEmpty()) {
                    shiftAssignments.remove(day);
                    totalDaysCleared++;
                    datesToClearFromMySQL.add(day);
                    dayResult.put("day_cleared", true);
                    System.out.println("  Day " + day + " has no shifts left, removed day entry");
                }

                dayResult.put("shifts", shiftResults);
                dayResult.put("day_changed", dayChanged);
                results.put(day, dayResult);
            }

            // ============ SYNC TO MYSQL ============

            // First, handle specific employee removals
            for (Map.Entry<String, List<String>> entry : mysqlRemovals.entrySet()) {
                String[] parts = entry.getKey().split("\\|");
                String day = parts[0];
                String shift = parts[1];

                for (String empId : entry.getValue()) {
                    mysqlService.removeAssignment(day, shift, empId);
                }
            }

            // Then, clear any completely empty dates from MySQL
            if (!datesToClearFromMySQL.isEmpty()) {
                for (String date : datesToClearFromMySQL) {
                    mysqlService.clearAssignmentsForDate(date);
                    System.out.println("🗑️ Cleared MySQL for empty date: " + date);
                }
            }

            // Save changes to file
            saveAssignments();

            // ============ BUILD RESPONSE ============
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("total_removed", totalRemoved);
            response.put("total_shifts_cleared", totalShiftsCleared);
            response.put("total_days_cleared", totalDaysCleared);
            response.put("mysql_records_removed", mysqlRemovedCount);
            response.put("results", results);

            // Build message
            StringBuilder message = new StringBuilder();
            message.append("Removed ").append(totalRemoved).append(" assignments from JSON and ").append(mysqlRemovedCount).append(" from MySQL. ");

            if (totalShiftsCleared > 0) {
                message.append("Cleared ").append(totalShiftsCleared).append(" entire shifts. ");
            }

            if (totalDaysCleared > 0) {
                message.append("Completely cleared ").append(totalDaysCleared).append(" days. ");
            }

            response.put("message", message.toString());

            System.out.println("✅ Removal complete: " + message);
            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to remove assignments: " + e.getMessage()))
                    .build();
        }
    }
    @DELETE
    @Path("/employees")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeEmployees(Map<String, Object> input) {
        Object employeesObj = input.get("employees"); // Can be String (single) or List<String> (multiple)

        System.out.println("🗑️ Removing employee records...");

        // ============ OPTION A: Clear ALL employees (if employees field is omitted) ============
        if (employeesObj == null) {
            int removedCount = employeeInfo.size();
            employeeInfo.clear();
            saveAssignments(); // Save to persist the empty state

            System.out.println("🗑️ Cleared ALL employee records (" + removedCount + " employees)");

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "Cleared all employee records",
                    "action", "CLEARED_ALL",
                    "removedCount", removedCount
            )).build();
        }
        // Also treat "ALL" or "*" as clearing all employees
        else if (employeesObj instanceof String &&
                ("ALL".equalsIgnoreCase((String) employeesObj) || "*".equals(employeesObj))) {
            int removedCount = employeeInfo.size();
            employeeInfo.clear();
            saveAssignments();

            System.out.println("🗑️ Cleared ALL employee records using 'ALL' keyword (" + removedCount + " employees)");

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "Cleared all employee records",
                    "action", "CLEARED_ALL",
                    "removedCount", removedCount
            )).build();
        }

        // ============ OPTION B: Remove specific employees ============

        // Handle both single employee (string) and multiple (list)
        List<String> employeesToRemove;

        if (employeesObj instanceof String) {
            // Single employee as string
            employeesToRemove = Collections.singletonList((String) employeesObj);
            System.out.println("  Removing single employee: " + employeesObj);
        } else if (employeesObj instanceof List) {
            // Multiple employees as list
            employeesToRemove = (List<String>) employeesObj;
            System.out.println("  Removing " + employeesToRemove.size() + " employees: " + employeesToRemove);
        } else {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "Invalid employees format",
                            "message", "employees must be a string (single ID), array of strings, or omitted to clear ALL employees",
                            "received_type", employeesObj.getClass().getSimpleName()
                    ))
                    .build();
        }

        // Validate that all employees exist
        List<String> notFound = new ArrayList<>();
        List<String> removed = new ArrayList<>();

        for (String empId : employeesToRemove) {
            if (employeeInfo.containsKey(empId)) {
                employeeInfo.remove(empId);
                removed.add(empId);
                System.out.println("    ✅ Removed employee: " + empId);
            } else {
                notFound.add(empId);
                System.out.println("    ❌ Employee not found: " + empId);
            }
        }

        // Also remove these employees from any shift assignments
        int removedFromShifts = removeEmployeesFromAllShifts(employeesToRemove);

        // Save changes
        saveAssignments();

        // Build response
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Removed " + removed.size() + " employee(s)");
        response.put("action", "REMOVED_EMPLOYEES");
        response.put("removedCount", removed.size());
        response.put("removed", removed);
        response.put("removedFromShifts", removedFromShifts);

        if (!notFound.isEmpty()) {
            response.put("warning", "These employees were not found: " + notFound);
            response.put("notFoundCount", notFound.size());
        }

        response.put("remainingCount", employeeInfo.size());

        System.out.println("✅ Employee removal complete. Remaining: " + employeeInfo.size());
        return Response.ok(response).build();
    }

    /**
     * Helper method to remove employees from all shift assignments
     */
    private int removeEmployeesFromAllShifts(List<String> employeeIds) {
        int removedCount = 0;

        // Iterate through all dates and shifts
        for (Map.Entry<String, Map<String, List<String>>> dayEntry : shiftAssignments.entrySet()) {
            String date = dayEntry.getKey();
            Map<String, List<String>> shifts = dayEntry.getValue();

            for (Map.Entry<String, List<String>> shiftEntry : shifts.entrySet()) {
                String shift = shiftEntry.getKey();
                List<String> employees = shiftEntry.getValue();

                // Remove any matching employee IDs
                boolean removed = employees.removeAll(employeeIds);
                if (removed) {
                    removedCount++;
                    System.out.println("  Removed employee from " + date + " " + shift + " shift");
                }
            }
        }

        // Clean up empty shifts and days
        cleanupEmptyAssignments();

        return removedCount;
    }

    /**
     * Helper method to clean up empty shifts and days
     */
    private void cleanupEmptyAssignments() {
        List<String> emptyDates = new ArrayList<>();

        for (Map.Entry<String, Map<String, List<String>>> dayEntry : shiftAssignments.entrySet()) {
            String date = dayEntry.getKey();
            Map<String, List<String>> shifts = dayEntry.getValue();

            // Remove empty shifts
            List<String> emptyShifts = new ArrayList<>();
            for (Map.Entry<String, List<String>> shiftEntry : shifts.entrySet()) {
                if (shiftEntry.getValue().isEmpty()) {
                    emptyShifts.add(shiftEntry.getKey());
                }
            }

            for (String emptyShift : emptyShifts) {
                shifts.remove(emptyShift);
                System.out.println("  Removed empty shift: " + emptyShift + " on " + date);
            }

            // If day has no shifts, mark for removal
            if (shifts.isEmpty()) {
                emptyDates.add(date);
            }
        }

        // Remove empty dates
        for (String emptyDate : emptyDates) {
            shiftAssignments.remove(emptyDate);
            System.out.println("  Removed empty date: " + emptyDate);
        }
    }

    @DELETE
    @Path("/employees/all")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearAllEmployees() {
        int removedCount = employeeInfo.size();
        employeeInfo.clear();

        // Also clear all shift assignments since employees are gone
        shiftAssignments.clear();

        saveAssignments();

        System.out.println("🗑️ Cleared ALL employee records and ALL shift assignments (" + removedCount + " employees)");

        return Response.ok(Map.of(
                "status", "success",
                "message", "Cleared all employee records and shift assignments",
                "removedCount", removedCount,
                "assignmentsCleared", true
        )).build();
    }
    // Helper: Get employees who CAN work on this date (respect leave, consecutive days, group, etc.)
    private List<EmployeeInfo> getEligibleEmployeesForDate(LocalDate date) {
        List<EmployeeInfo> eligible = new ArrayList<>();
        String dateStr = date.toString();

        // Check group alternation (assuming you have group logic like odd/even IDs)
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isGroupADay = dayOfWeek == DayOfWeek.MONDAY ||
                dayOfWeek == DayOfWeek.WEDNESDAY ||
                dayOfWeek == DayOfWeek.FRIDAY;

        for (EmployeeInfo emp : employeeInfo.values()) {
            // Skip if on leave
            if (employeeLeaves.containsKey(emp.getId()) &&
                    employeeLeaves.get(emp.getId()).stream().anyMatch(l -> l.getDate().equals(dateStr))) {
                continue;
            }

            // Skip if would cause consecutive working day
            LocalDate lastWork = getLastWorkingDay(emp.getId());
            if (lastWork != null && lastWork.plusDays(1).equals(date)) {
                continue;
            }

            // Group alternation (adjust according to your exact group logic)
            int empNum = Integer.parseInt(emp.getId().substring(1));
            boolean isGroupA = empNum % 2 == 1;
            if ((isGroupA && !isGroupADay) || (!isGroupA && isGroupADay)) {
                continue;
            }

            eligible.add(emp);
        }
        return eligible;
    }

    // Helper: Find last working day for employee (simple version)
    private LocalDate getLastWorkingDay(String employeeId) {
        return LocalDate.now().minusDays(1); // Replace with real logic later
    }

    @POST
    @Path("/shifts/manual-assign")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)

    public Response manualAssignShifts(Map<String, Object> input) {
        try {
            System.out.println("=== POST /shifts/manual-assign ===");
            System.out.println("Input: " + input);

            // ============ VALIDATE REQUIRED FIELDS ============
            String date = (String) input.get("date");
            String shift = (String) input.get("shift");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> employees = (List<Map<String, Object>>) input.get("employees");

            if (date == null || shift == null || employees == null || employees.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of(
                                "error", "Missing required fields",
                                "required", Arrays.asList("date", "shift", "employees")
                        ))
                        .build();
            }

            // Validate date format
            try {
                LocalDate.parse(date);
            } catch (Exception e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid date format. Use YYYY-MM-DD"))
                        .build();
            }

            // Validate shift
//            if (!SHIFT_TIMES.containsKey(shift)) {
//                return Response.status(Response.Status.BAD_REQUEST)
//                        .entity(Map.of("error", "Invalid shift. Must be: Morning, Afternoon, or Night"))
//                        .build();
//            }

            // ============ CHECK FOR DUPLICATE EMPLOYEE IDs IN REQUEST ============
            Map<String, List<String>> duplicateIds = new HashMap<>();
            Set<String> uniqueIds = new HashSet<>();

            for (Map<String, Object> emp : employees) {
                String empId = (String) emp.get("employee_id");
                String empName = (String) emp.getOrDefault("name", "Unknown");

                if (empId == null || empId.trim().isEmpty()) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Each employee must have an employee_id"))
                            .build();
                }

                if (uniqueIds.contains(empId)) {
                    duplicateIds.computeIfAbsent(empId, k -> new ArrayList<>()).add(empName);
                } else {
                    uniqueIds.add(empId);
                }
            }

            if (!duplicateIds.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Duplicate employee IDs found in request");
                errorResponse.put("error_type", "DUPLICATE_EMPLOYEE_IDS");

                List<Map<String, Object>> duplicateDetails = new ArrayList<>();
                for (Map.Entry<String, List<String>> entry : duplicateIds.entrySet()) {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("employee_id", entry.getKey());
                    detail.put("employee_names", entry.getValue());
                    detail.put("count", entry.getValue().size());
                    duplicateDetails.add(detail);
                }
                errorResponse.put("duplicates", duplicateDetails);

                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(errorResponse)
                        .build();
            }

            // ============ CHECK EXISTING ASSIGNMENTS FOR THIS DATE ============
            Map<String, List<String>> dayAssignments = shiftAssignments.get(date);
            Set<String> alreadyAssignedIds = new HashSet<>();

            if (dayAssignments != null) {
                for (List<String> assignedList : dayAssignments.values()) {
                    alreadyAssignedIds.addAll(assignedList);
                }
            }

            // ============ PROCESS EMPLOYEES ============
            List<Map<String, Object>> assignedEmployees = new ArrayList<>();
            List<Map<String, Object>> skippedEmployees = new ArrayList<>();
            List<Map<String, Object>> newEmployees = new ArrayList<>();

            int successCount = 0;
            int skipCount = 0;
            int newEmployeeCount = 0;
            int mysqlSyncCount = 0;

            for (Map<String, Object> empData : employees) {
                String empId = (String) empData.get("employee_id");
                String empName = (String) empData.getOrDefault("name", "Unknown");
                String gender = (String) empData.getOrDefault("gender", "Unknown");

                // Check if already assigned on this date
                if (alreadyAssignedIds.contains(empId)) {
                    Map<String, Object> skipped = new HashMap<>();
                    skipped.put("employee_id", empId);
                    skipped.put("name", empName);
                    skipped.put("reason", "Already assigned on " + date);
                    skippedEmployees.add(skipped);
                    skipCount++;
                    System.out.println("⏭️ Skipping " + empName + " (" + empId + ") - already assigned on " + date);
                    continue;
                }

                // Check if employee exists in employeeInfo
                EmployeeInfo empInfo = employeeInfo.get(empId);

                if (empInfo == null) {
                    // Create new employee record with minimal data
                    empInfo = createMinimalEmployeeFromManualInput(empData);
                    employeeInfo.put(empId, empInfo);
                    newEmployeeCount++;
                    newEmployees.add(Map.of(
                            "employee_id", empId,
                            "name", empInfo.getName()
                    ));
                    System.out.println("🆕 Created new employee: " + empInfo.getName() + " (" + empId + ")");
                }

                // Add to shift assignments (JSON)
                shiftAssignments
                        .computeIfAbsent(date, k -> new HashMap<>())
                        .computeIfAbsent(shift, k -> new ArrayList<>())
                        .add(empId);

                // ============ SYNC TO MYSQL with all fields (including rating/category) ============
                try {
                    mysqlService.syncManualAssignment(
                            date, shift, empId,
                            empName,
                            gender
                    );
                    mysqlSyncCount++;
                    System.out.println("✅ Synced to MySQL with NULLs: " + empId);

                } catch (Exception e) {
                    System.err.println("❌ MySQL sync failed for " + empId + ": " + e.getMessage());
                    e.printStackTrace();
                }


                // ============ BUILD RESPONSE WITHOUT RATING/CATEGORY ============
                Map<String, Object> assigned = new HashMap<>();
                assigned.put("employee_id", empId);
                assigned.put("name", empName);
                assigned.put("gender", gender);
                // NO rating, NO category, NO role in response for manual assignment

                assignedEmployees.add(assigned);

                successCount++;
                System.out.println("✅ Assigned " + empName + " (" + empId + ") to " + shift + " shift on " + date);
            }

            // Save to JSON file
            saveAssignments();

            // ============ RETURN ERROR IF ALL EMPLOYEES WERE SKIPPED ============
            if (successCount == 0 && skipCount > 0) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "No employees were assigned - all requested employees already have assignments on " + date);
                errorResponse.put("error_type", "ALL_EMPLOYEES_ALREADY_ASSIGNED");
                errorResponse.put("date", date);
                errorResponse.put("shift", shift);
                errorResponse.put("total_requested", employees.size());
                errorResponse.put("skipped_count", skipCount);
                errorResponse.put("skipped_employees", skippedEmployees);
                errorResponse.put("note", "Manual mode: Only employee_id, name, and gender are shown in response");

                System.out.println("\n❌ Manual Assignment Failed - All employees already assigned!");
                return Response.status(Response.Status.CONFLICT)
                        .entity(errorResponse)
                        .build();
            }

            // ============ BUILD SUCCESS RESPONSE (NO RATING/CATEGORY) ============
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", String.format("Assigned %d out of %d employees", successCount, employees.size()));
            response.put("date", date);
            response.put("shift", shift);
            response.put("total_requested", employees.size());
            response.put("assigned_count", successCount);
            response.put("skipped_count", skipCount);
            response.put("new_employees_created", newEmployeeCount);

            // MySQL sync info (optional, can be removed if you don't want to show)
            response.put("mysql_sync", mysqlSyncCount > 0 ? "success" : "failed");

            if (!assignedEmployees.isEmpty()) {
                response.put("assigned_employees", assignedEmployees); // These have NO rating/category
            }

            if (!skippedEmployees.isEmpty()) {
                response.put("skipped_employees", skippedEmployees);
                if (skipCount > 0 && successCount > 0) {
                    response.put("warning", skipCount + " employee(s) were skipped because they already have assignments on " + date);
                }
            }

            if (!newEmployees.isEmpty()) {
                response.put("newly_created_employees", newEmployees);
            }

            response.put("note", "Manual mode: Only employee_id, name, and gender are shown. Rating and category are stored in MySQL but not displayed.");

            System.out.println("\n✅ Manual Assignment Complete!");
            System.out.println("   - Total requested: " + employees.size());
            System.out.println("   - Successfully assigned: " + successCount);
            System.out.println("   - Skipped: " + skipCount);
            System.out.println("   - New employees: " + newEmployeeCount);
            System.out.println("   - MySQL sync: " + mysqlSyncCount + " records added");

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "Manual assignment failed: " + e.getMessage()
                    ))
                    .build();
        }
    }

    /**
     * Creates a minimal employee record for manual assignment
     * Role and rating are stored but NOT used for validation in manual mode
     */
    private EmployeeInfo createMinimalEmployeeFromManualInput(Map<String, Object> input) {
        String employeeId = (String) input.get("employee_id");
        String name = (String) input.get("name");
        String role = (String) input.getOrDefault("role", "Employee"); // Just for reference
        String gender = (String) input.getOrDefault("gender", "Male");
        String employeeType = (String) input.getOrDefault("employeeType", "Permanent");

        // Default values - rating NOT used in manual mode
        double hourlyWage = 25.0; // Default
        int rating = 3; // Default, not used for constraints

        // Generate email and phone
        String email = name.toLowerCase().replace(" ", ".") + "@company.com";
        String phone = "+91 9" + String.format("%09d", new Random().nextInt(1000000000));

        // Create EmployeeInfo with minimal data
        EmployeeInfo emp = new EmployeeInfo(
                employeeId, name, employeeType, gender, hourlyWage,
                "MGR001", "Operations", role, email, phone
        );

        emp.setPerformanceRating(rating);

        // Add basic skills (optional)
        emp.addSkill("Communication");
        emp.addSkill("Teamwork");

        return emp;
    }
    @POST
    @Path("/shifts/assign")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response assignShiftsWithBreaks(Map<String, Object> input) {
        try {
            System.out.println("=== POST /shifts/assign  ===");
            System.out.println("Input: " + input);

            // ============ VALIDATION FIRST ============
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> existingUsers = (List<Map<String, Object>>) input.get("existing_users");

            // Validate for duplicate employee IDs
            if (existingUsers != null && !existingUsers.isEmpty()) {
                Map<String, List<String>> duplicateIds = new HashMap<>();
                Set<String> uniqueIds = new HashSet<>();

                for (Map<String, Object> user : existingUsers) {
                    Object empIdObj = user.get("employee_id");
                    if (empIdObj != null) {
                        String empId = empIdObj.toString();
                        String employeeName = (String) user.getOrDefault("name", "Unknown");

                        if (uniqueIds.contains(empId)) {
                            duplicateIds.computeIfAbsent(empId, k -> new ArrayList<>()).add(employeeName);
                        } else {
                            uniqueIds.add(empId);
                        }
                    }
                }

                if (!duplicateIds.isEmpty()) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("status", "error");
                    errorResponse.put("message", "Duplicate employee IDs found in request");
                    errorResponse.put("error_type", "DUPLICATE_EMPLOYEE_IDS");

                    List<Map<String, Object>> duplicateDetails = new ArrayList<>();
                    for (Map.Entry<String, List<String>> entry : duplicateIds.entrySet()) {
                        Map<String, Object> detail = new HashMap<>();
                        detail.put("employee_id", entry.getKey());
                        detail.put("employee_names", entry.getValue());
                        detail.put("count", entry.getValue().size());
                        duplicateDetails.add(detail);
                    }
                    errorResponse.put("duplicates", duplicateDetails);

                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(errorResponse)
                            .build();
                }

                System.out.println("✅ Validation passed: All " + existingUsers.size() + " employee IDs are unique");
            }

            // 1. Parse input
            String shiftName    = (String) input.get("shift_name");
            String startDateStr = (String) input.get("start_date");
            String endDateStr   = (String) input.get("end_date");
            String startTime    = (String) input.get("start_time");
            String endTime      = (String) input.get("end_time");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> roles = (List<Map<String, Object>>) input.get("roles");

            // Break configuration
            boolean scheduleBreaks = Boolean.TRUE.equals(input.getOrDefault("schedule_breaks", true));
            int breakDurationMinutes = 30;
            int breakAfterHours = 4;

            if (input.containsKey("break_duration_minutes")) {
                breakDurationMinutes = ((Number) input.get("break_duration_minutes")).intValue();
            }
            if (input.containsKey("break_after_hours")) {
                breakAfterHours = ((Number) input.get("break_after_hours")).intValue();
            }

            // Validation
            if (shiftName == null || startDateStr == null || endDateStr == null ||
                    startTime == null || endTime == null ||
                    roles == null || roles.isEmpty() || existingUsers == null) {
                return Response.status(400).entity(Map.of(
                        "error", "Missing required fields",
                        "required", Arrays.asList("shift_name", "start_date", "end_date", "start_time", "end_time", "roles", "existing_users")
                )).build();
            }

            LocalDate startDate = LocalDate.parse(startDateStr);
            LocalDate endDate   = LocalDate.parse(endDateStr);

            if (endDate.isBefore(startDate)) {
                return Response.status(400).entity(Map.of("error", "End date must be after start date")).build();
            }

            // Working dates (skip Sundays)
            List<LocalDate> workingDates = new ArrayList<>();
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                if (current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    workingDates.add(current);
                }
                current = current.plusDays(1);
            }

            LocalTime startLocalTime = LocalTime.parse(startTime);
            LocalTime endLocalTime = LocalTime.parse(endTime);

            // Calculate shift duration
            double shiftDurationHours;
            if (endLocalTime.isBefore(startLocalTime)) {
                long minutesToMidnight = Duration.between(startLocalTime, LocalTime.MAX).toMinutes() + 1;
                long minutesFromMidnight = Duration.between(LocalTime.MIN, endLocalTime).toMinutes();
                shiftDurationHours = (minutesToMidnight + minutesFromMidnight) / 60.0;
            } else {
                shiftDurationHours = Duration.between(startLocalTime, endLocalTime).toMinutes() / 60.0;
            }

            System.out.println("Shift: " + startTime + " to " + endTime);
            System.out.println("Calculated duration: " + shiftDurationHours + " hours");

            // Check if shift duration allows for break
            if (scheduleBreaks && shiftDurationHours < (breakAfterHours + (breakDurationMinutes / 60.0))) {
                return Response.status(400).entity(Map.of(
                        "error", "Shift duration too short for scheduled break",
                        "shiftDuration", String.format("%.2f hours", shiftDurationHours),
                        "requiredForBreak", String.format("%.2f hours", breakAfterHours + (breakDurationMinutes / 60.0)),
                        "suggestion", "Increase shift duration or reduce break configuration"
                )).build();
            }

            boolean overrideExisting = Boolean.TRUE.equals(input.get("overrideExisting"));

            // ============ CREATE EMPLOYEE INFO OBJECTS FROM INPUT ============
            Map<String, EmployeeInfo> allEmployees = new HashMap<>();
            Map<String, Double> employeeWages = new HashMap<>();
            Map<String, Integer> employeeRatings = new HashMap<>();
            Map<String, String> employeeRoles = new HashMap<>();
            Map<String, String> employeeGenders = new HashMap<>();
            Map<String, String> employeeTypes = new HashMap<>();

            int empCounter = 1;

            for (Map<String, Object> user : existingUsers) {
                String name     = (String) user.get("name");
                Number rateObj  = (Number) user.get("rate");
                String unit     = (String) user.get("unit");
                Object ratingObj = user.get("rating");
                String role     = (String) user.get("role");
                String existingEmployeeId = (String) user.get("employee_id");

                String gender = user.containsKey("gender") ?
                        (String) user.get("gender") : "Male";

                String employeeType = user.containsKey("employeeType") ?
                        (String) user.get("employeeType") : "Permanent";

                String employeeId;
                if (existingEmployeeId != null && !existingEmployeeId.trim().isEmpty()) {
                    employeeId = existingEmployeeId;
                    System.out.println("📌 Using provided employee ID: " + employeeId + " for " + name);
                } else {
                    EmployeeInfo existing = findExistingEmployee(name, role);
                    employeeId = (existing != null) ? existing.getId() : "EMP" + String.format("%03d", empCounter++);
                    System.out.println("🆕 Generated new employee ID: " + employeeId + " for " + name);
                }

                // Calculate hourly wage
                double hourlyWage = rateObj.doubleValue();
                if ("day".equalsIgnoreCase(unit)) {
                    hourlyWage = hourlyWage / 8.0;
                } else if ("month".equalsIgnoreCase(unit)) {
                    hourlyWage = hourlyWage / (22.0 * 8.0);
                }

                int performanceRating = parseRating(ratingObj);

                String email = name.toLowerCase().replace(" ", ".") + "@company.com";
                String phone = "+91 9" + String.format("%09d", empCounter * 1234567);

                // Store all employee data
                employeeWages.put(employeeId, hourlyWage);
                employeeRatings.put(employeeId, performanceRating);
                employeeRoles.put(employeeId, role);
                employeeGenders.put(employeeId, gender);
                employeeTypes.put(employeeId, employeeType);

                double finalHourlyWage = hourlyWage;

                EmployeeInfo empInfo = employeeInfo.computeIfAbsent(employeeId, k ->
                        new EmployeeInfo(employeeId, name, employeeType, gender, finalHourlyWage,
                                "MGR001", "Operations", role, email, phone)
                );

                empInfo.setPerformanceRating(performanceRating);
                empInfo.setPosition(role);
                empInfo.setHourlyWage(hourlyWage);
                empInfo.setGender(gender);
                empInfo.setEmployeeType(employeeType);
                addSkillsBasedOnRole(empInfo, role);
                empInfo.setName(name);
                empInfo.setDepartment("Operations");
                empInfo.setCategory(employeeType);
                allEmployees.put(employeeId, empInfo);

                System.out.println("👤 Employee: " + name + " (" + employeeId + ") - Type: " + employeeType +
                        ", Rating: " + performanceRating + ", Wage: $" + String.format("%.2f", hourlyWage) + "/hr");
            }

            // ============ CREATE ROLE LIMITS AND RATING REQUIREMENTS ============
            List<Scheduler.ShiftSchedule.RoleLimit> roleLimits = new ArrayList<>();
            List<Scheduler.ShiftSchedule.RatingRequirement> ratingRequirements = new ArrayList<>();

            for (Map<String, Object> roleSpec : roles) {
                String roleName = (String) roleSpec.get("role_name");
                Object ratingObj = roleSpec.get("rating");
                Number maxWorkersObj = (Number) roleSpec.get("max_workers");

                roleLimits.add(new Scheduler.ShiftSchedule.RoleLimit(roleName, maxWorkersObj.intValue()));

                List<Integer> allowedRatings = new ArrayList<>();
                if (ratingObj instanceof Number) {
                    int min = ((Number) ratingObj).intValue();
                    for (int r = min; r <= 5; r++) allowedRatings.add(r);
                } else if (ratingObj instanceof String) {
                    String s = ((String) ratingObj).toLowerCase();
                    if (s.contains("any") || s.contains("all")) {
                        for (int r = 1; r <= 5; r++) allowedRatings.add(r);
                    } else {
                        try {
                            int min = Integer.parseInt(s.replaceAll("[^0-9]", ""));
                            for (int r = min; r <= 5; r++) allowedRatings.add(r);
                        } catch (Exception e) {
                            for (int r = 3; r <= 5; r++) allowedRatings.add(r);
                        }
                    }
                }
                ratingRequirements.add(new Scheduler.ShiftSchedule.RatingRequirement(roleName, allowedRatings));
            }

            // ============ CHECK EXISTING ASSIGNMENTS PER DATE ============
            System.out.println("\n🔍 Checking for existing assignments in date range...");

            // Build a map of employee+date -> shift for quick lookup
            Map<String, String> employeeExistingAssignments = new HashMap<>(); // key: empId-date
            Map<String, Integer> existingAssignmentsCount = new HashMap<>();

            for (LocalDate date : workingDates) {
                String dateStr = date.toString();
                Map<String, List<String>> dayAssignments = shiftAssignments.get(dateStr);

                if (dayAssignments != null) {
                    System.out.println("📅 Found existing assignments for " + dateStr + ":");
                    for (Map.Entry<String, List<String>> entry : dayAssignments.entrySet()) {
                        String shift = entry.getKey();
                        List<String> employees = entry.getValue();

                        System.out.println("   - " + shift + " shift: " + employees.size() + " employees");

                        for (String empId : employees) {
                            String key = empId + "-" + dateStr;
                            employeeExistingAssignments.put(key, shift);
                            existingAssignmentsCount.put(dateStr, existingAssignmentsCount.getOrDefault(dateStr, 0) + 1);
                        }
                    }
                }
            }

            // ============ SORT EMPLOYEES BY PRIORITY ============
            boolean prioritizePermanent = Boolean.TRUE.equals(input.get("prioritizePermanent"));
            List<EmployeeInfo> sortedEmployees = new ArrayList<>(allEmployees.values());

            // Sort by: Permanent first, then higher rating, then lower wage (cost optimization)
            sortedEmployees.sort((a, b) -> {
                // First priority: Permanent employees
                if (prioritizePermanent) {
                    boolean aPerm = "Permanent".equalsIgnoreCase(a.getEmployeeType());
                    boolean bPerm = "Permanent".equalsIgnoreCase(b.getEmployeeType());
                    if (aPerm && !bPerm) return -1;
                    if (!aPerm && bPerm) return 1;
                }

                // Second priority: Higher rating
                int ratingCompare = Integer.compare(b.getPerformanceRating(), a.getPerformanceRating());
                if (ratingCompare != 0) return ratingCompare;

                // Third priority: Lower wage (cost optimization)
                return Double.compare(a.getHourlyWage(), b.getHourlyWage());
            });

            System.out.println("\n📊 Employee priority order (for cost optimization):");
            for (EmployeeInfo emp : sortedEmployees) {
                System.out.println("   " + emp.getName() + " - Type: " + emp.getEmployeeType() +
                        ", Rating: " + emp.getPerformanceRating() + ", Wage: $" + emp.getHourlyWage());
            }

            // ============ BUILD PLANNING ENTITIES - CHECK PER DATE ============
            List<Scheduler.EmployeeAssignment> planningEntities = new ArrayList<>();
            Map<String, List<String>> skippedPerDate = new HashMap<>(); // Track skipped by date
            Map<String, Map<String, Integer>> roleCountsPerDate = new HashMap<>();

            int totalPossibleAssignments = workingDates.size() * sortedEmployees.size();
            int skippedCount = 0;
            int entityCounter = 0;

            // Track which employees are available on each date
            Map<String, Set<String>> availableEmployeesPerDate = new HashMap<>();

            for (LocalDate date : workingDates) {
                String dateStr = date.toString();
                List<String> skippedOnThisDate = new ArrayList<>();
                availableEmployeesPerDate.put(dateStr, new HashSet<>());

                // Track role counts for this day
                Map<String, Integer> dailyRoleCounts = new HashMap<>();
                for (Scheduler.ShiftSchedule.RoleLimit limit : roleLimits) {
                    dailyRoleCounts.put(limit.getRoleName(), 0);
                }
                roleCountsPerDate.put(dateStr, dailyRoleCounts);

                for (EmployeeInfo emp : sortedEmployees) {
                    String empId = emp.getId();
                    String key = empId + "-" + dateStr;

                    // Check if THIS SPECIFIC DATE is already assigned
                    if (employeeExistingAssignments.containsKey(key) && !overrideExisting) {
                        // Only skip for THIS DATE, not all dates!
                        skippedOnThisDate.add(emp.getName() + " (" + empId + ") - Already on " + employeeExistingAssignments.get(key) + " shift");
                        skippedCount++;
                        continue;
                    }

                    // Skip female night shifts
                    if (("Night".equals(shiftName) || "night".equalsIgnoreCase(shiftName))
                            && "Female".equalsIgnoreCase(emp.getGender())) {
                        skippedOnThisDate.add(emp.getName() + " (" + empId + ") - Female cannot work night shift");
                        skippedCount++;
                        continue;
                    }

                    // Check rating requirement for THIS DATE
                    String position = emp.getPosition();
                    boolean ratingOk = false;
                    for (Scheduler.ShiftSchedule.RatingRequirement req : ratingRequirements) {
                        if (req.getRoleName().equals(position)) {
                            ratingOk = req.getAllowedRatings().contains(emp.getPerformanceRating());
                            break;
                        }
                    }
                    if (!ratingOk) {
                        skippedOnThisDate.add(emp.getName() + " (" + empId + ") - Rating " + emp.getPerformanceRating() +
                                " doesn't meet requirement for " + position);
                        skippedCount++;
                        continue;
                    }

                    // This employee is available for this date
                    availableEmployeesPerDate.get(dateStr).add(empId);

                    // Create entity for THIS DATE
                    Scheduler.EmployeeAssignment entity = new Scheduler.EmployeeAssignment(
                            "entity-" + entityCounter++,
                            emp.getId(),
                            emp.getName(),
                            dateStr,
                            emp.getCategory(),
                            emp.getGender(),
                            emp.getDepartment(),
                            emp.getPosition()
                    );

                    entity.setSkills(new HashSet<>(emp.getSkills()));
                    entity.setShiftColor(emp.getShiftColor());
                    entity.setHourlyWage(emp.getHourlyWage());
                    entity.setPerformanceRating(emp.getPerformanceRating());
                    entity.setEmployeeType(emp.getEmployeeType());
                    entity.setPermanentEmployee("Permanent".equalsIgnoreCase(emp.getEmployeeType()));
                    entity.setRequestedShift(shiftName);

                    planningEntities.add(entity);
                }

                if (!skippedOnThisDate.isEmpty()) {
                    skippedPerDate.put(dateStr, skippedOnThisDate);
                }
            }

            System.out.println("\n📊 Planning summary:");
            System.out.println("   Total possible assignments: " + totalPossibleAssignments);
            System.out.println("   Entities to plan: " + planningEntities.size());
            System.out.println("   Skipped due to constraints: " + skippedCount);
            System.out.println("   Schedule Breaks: " + scheduleBreaks);

            // Show available employees per date
            System.out.println("\n✅ Available employees per date:");
            for (LocalDate date : workingDates) {
                String dateStr = date.toString();
                Set<String> available = availableEmployeesPerDate.get(dateStr);
                System.out.println("   " + dateStr + ": " + (available != null ? available.size() : 0) + " employees available");
                if (available != null && !available.isEmpty()) {
                    for (String empId : available) {
                        EmployeeInfo emp = allEmployees.get(empId);
                        System.out.println("      • " + emp.getName() + " (" + empId + ") - " + emp.getPosition());
                    }
                }
            }

            // Show skipped by date
            if (!skippedPerDate.isEmpty()) {
                System.out.println("\n⏭️ Skipped by date:");
                for (Map.Entry<String, List<String>> entry : skippedPerDate.entrySet()) {
                    System.out.println("   " + entry.getKey() + ": " + entry.getValue().size() + " employees skipped");
                    for (String emp : entry.getValue()) {
                        System.out.println("      • " + emp);
                    }
                }
            }

            // Check if ALL entities were skipped
            if (!overrideExisting && planningEntities.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "No employees were assigned - all requested employees already have assignments or don't meet constraints");
                errorResponse.put("error_type", "ALL_EMPLOYEES_SKIPPED");
                errorResponse.put("start_date", startDateStr);
                errorResponse.put("end_date", endDateStr);
                errorResponse.put("shift_name", shiftName);
                errorResponse.put("total_requested", existingUsers.size());
                errorResponse.put("skipped_count", skippedCount);
                errorResponse.put("skipped_by_date", skippedPerDate);
                errorResponse.put("suggestion", "Use overrideExisting=true to force reassignment or choose different employees/dates");

                return Response.status(Response.Status.CONFLICT)
                        .entity(errorResponse)
                        .build();
            }

            // ============ CONFIGURE AND RUN SOLVER ============
            List<String> possibleShifts = Arrays.asList(shiftName);

            // Configure Timefold constraints with our role limits and rating requirements
            Scheduler.ShiftConstraints.setConfiguration(roleLimits, ratingRequirements);

            // Create the problem
            Scheduler.ShiftSchedule problem = new Scheduler.ShiftSchedule(
                    planningEntities,
                    possibleShifts,
                    roleLimits,
                    ratingRequirements
            );
            problem.setRequestedShiftName(shiftName);
            problem.setPrioritizePermanent(prioritizePermanent);

            // Run solver
            SolverConfig solverConfig = new SolverConfig()
                    .withSolutionClass(Scheduler.ShiftSchedule.class)
                    .withEntityClasses(Scheduler.EmployeeAssignment.class)
                    .withConstraintProviderClass(Scheduler.ShiftConstraints.class)
                    .withTerminationSpentLimit(Duration.ofSeconds(20));

            SolverFactory<Scheduler.ShiftSchedule> solverFactory = SolverFactory.create(solverConfig);
            Solver<Scheduler.ShiftSchedule> solver = solverFactory.buildSolver();

            System.out.println("🔧 Starting solver with " + planningEntities.size() + " entities...");
            System.out.println("   Shift: " + shiftName);
            System.out.println("   Working days: " + workingDates.size());
            System.out.println("   Schedule Breaks: " + scheduleBreaks);

            long solverStart = System.currentTimeMillis();
            Scheduler.ShiftSchedule solved = solver.solve(problem);
            long solverTimeMs = System.currentTimeMillis() - solverStart;
            System.out.println("✅ Solver finished in " + (solverTimeMs / 1000.0) + " seconds. Score: " + solved.getScore());

            // ============ APPLY ASSIGNMENTS AND GENERATE BREAK SCHEDULES ============
            int assignedCount = 0;
            Map<String, Map<String, Integer>> finalRoleCounts = new HashMap<>();
            Map<String, List<Map<String, Object>>> assignmentDetails = new HashMap<>();
            Map<String, Long> assignmentsByEmployeeType = new HashMap<>();
            Map<String, Set<String>> assignedEmployeesPerDate = new HashMap<>();

            List<BreakSchedule> breakSchedules = new ArrayList<>();

            for (Scheduler.EmployeeAssignment ea : solved.getAssignments()) {
                if (ea.getShift() == null) continue;

                String d = ea.getDate();
                String s = ea.getShift();
                String eid = ea.getEmployeeId();

                EmployeeInfo emp = allEmployees.get(eid);
                if (emp == null) continue;

                // Skip female night assignments (double-check)
                if ("Night".equals(s) && "Female".equalsIgnoreCase(emp.getGender())) {
                    continue;
                }

                // Check role limits for this date
                String position = emp.getPosition();
                Map<String, Integer> dayRoleCounts = finalRoleCounts.computeIfAbsent(d, k -> new HashMap<>());
                int currentDayCount = dayRoleCounts.getOrDefault(position, 0);

                boolean withinLimit = true;
                for (Scheduler.ShiftSchedule.RoleLimit limit : roleLimits) {
                    if (limit.getRoleName().equals(position)) {
                        withinLimit = currentDayCount < limit.getMaxWorkers();
                        break;
                    }
                }

                if (!withinLimit) continue;

                // Save to shiftAssignments (JSON)
                shiftAssignments
                        .computeIfAbsent(d, k -> new HashMap<>())
                        .computeIfAbsent(s, k -> new ArrayList<>())
                        .add(eid);

                // Generate break schedule if enabled
                String breakStart = null;
                String breakEnd = null;
                String breakSlot = null;

                if (scheduleBreaks) {
                    ShiftTime shiftTime = SHIFT_TIMES.get(s);
                    if (shiftTime == null) {
                        shiftTime = new ShiftTime(startLocalTime, endLocalTime);
                    }

                    BreakSchedule breakSchedule = new BreakSchedule(
                            eid,
                            emp.getName(),
                            d,
                            s,
                            shiftTime.getStartTime(),
                            shiftTime.getEndTime()
                    );

                    if (breakAfterHours != 4) {
                        LocalTime customBreakStart = shiftTime.getStartTime().plusHours(breakAfterHours);
                        LocalTime customBreakEnd = customBreakStart.plusMinutes(breakDurationMinutes);
                        breakSchedule.setBreakStartTime(customBreakStart);
                        breakSchedule.setBreakEndTime(customBreakEnd);
                    }

                    breakSchedules.add(breakSchedule);

                    // Capture break info for MySQL and response
                    breakStart = breakSchedule.getBreakStart();
                    breakEnd = breakSchedule.getBreakEnd();
                    breakSlot = breakSchedule.getFormattedBreakSlot();
                }

                // ============ SYNC TO MYSQL with your exact parameter order ============
                // This matches exactly: syncAssignment(d, s, eid, ea.getEmployeeName(), ea.getPosition())
                mysqlService.syncAssignment(
                        d, s, eid,
                        ea.getEmployeeName(),
                        ea.getPosition(),
                        emp.getEmployeeType(),
                        emp.getGender(),
                        emp.getPerformanceRating()
                );
                // If you have all details available, also call the full version
                if (emp.getEmployeeType() != null) mysqlService.syncAssignment(
                        d, s, eid,
                        ea.getEmployeeName(), ea.getPosition(),
                        emp.getEmployeeType(), emp.getGender(),
                        emp.getPerformanceRating()
                );

                // Track assigned employees per date
                assignedEmployeesPerDate.computeIfAbsent(d, k -> new HashSet<>()).add(eid);

                // Store assignment details for response
                Map<String, Object> detail = new HashMap<>();
                detail.put("employeeId", eid);
                detail.put("employeeName", ea.getEmployeeName());
                detail.put("role", position);
                detail.put("rating", emp.getPerformanceRating());
                detail.put("wage", emp.getHourlyWage());
                detail.put("gender", emp.getGender());
                detail.put("employeeType", emp.getEmployeeType());

                if (breakStart != null) {
                    Map<String, Object> breakInfo = new HashMap<>();
                    breakInfo.put("breakStart", breakStart);
                    breakInfo.put("breakEnd", breakEnd);
                    breakInfo.put("breakDuration", breakDurationMinutes);
                    breakInfo.put("breakType", "MANDATORY");
                    breakInfo.put("breakSlot", breakSlot);
                    detail.put("break", breakInfo);
                }

                assignmentDetails.computeIfAbsent(d, k -> new ArrayList<>()).add(detail);

                // Track by employee type
                String empType = emp.getEmployeeType();
                assignmentsByEmployeeType.put(empType, assignmentsByEmployeeType.getOrDefault(empType, 0L) + 1);

                assignedCount++;
                dayRoleCounts.put(position, currentDayCount + 1);
            }

            // Save JSON to file
            saveAssignments();

            // ============ BUILD RESPONSE ============
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("shift_name", shiftName);
            response.put("period", startDateStr + " to " + endDateStr);
            response.put("shift_time", startTime + " - " + endTime);
            response.put("total_working_days", workingDates.size());
            response.put("solver_score", solved.getScore().toString());
            response.put("solver_time_seconds", solverTimeMs / 1000.0);
            response.put("new_assignments_made", assignedCount);
            response.put("entities_planned", planningEntities.size());
            response.put("total_possible_assignments", totalPossibleAssignments);
            response.put("skipped_count", skippedCount);
            response.put("override_used", overrideExisting);
            response.put("prioritize_permanent", prioritizePermanent);
            response.put("breakSchedulesCount", breakSchedules.size());
            response.put("breakConfiguration", Map.of(
                    "scheduleBreaks", scheduleBreaks,
                    "breakDurationMinutes", breakDurationMinutes,
                    "breakAfterHours", breakAfterHours
            ));

            // Add skipped by date information
            if (!skippedPerDate.isEmpty()) {
                Map<String, Object> skippedInfo = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : skippedPerDate.entrySet()) {
                    skippedInfo.put(entry.getKey(), entry.getValue());
                }
                response.put("skipped_by_date", skippedInfo);
            }

            // Add role statistics
            Map<String, Object> roleStats = new HashMap<>();
            for (String role : roleLimits.stream().map(rl -> rl.getRoleName()).collect(Collectors.toSet())) {
                Map<String, Object> stats = new HashMap<>();
                long roleCount = assignmentDetails.values().stream()
                        .flatMap(List::stream)
                        .filter(a -> role.equals(a.get("role")))
                        .count();

                double totalWage = assignmentDetails.values().stream()
                        .flatMap(List::stream)
                        .filter(a -> role.equals(a.get("role")))
                        .mapToDouble(a -> (Double) a.get("wage"))
                        .sum();

                double avgWage = roleCount > 0 ? totalWage / roleCount : 0;

                stats.put("assignments", roleCount);
                stats.put("average_wage", String.format("%.2f", avgWage));

                int maxWorkers = roleLimits.stream()
                        .filter(rl -> rl.getRoleName().equals(role))
                        .findFirst()
                        .map(Scheduler.ShiftSchedule.RoleLimit::getMaxWorkers)
                        .orElse(0);
                stats.put("max_per_day", maxWorkers);

                roleStats.put(role, stats);
            }
            response.put("role_statistics", roleStats);

            // Add assignments by employee type
            response.put("assignments_by_employee_type", assignmentsByEmployeeType);

            // Add daily summary
            List<Map<String, Object>> dailySummary = new ArrayList<>();

            Map<String, BreakSchedule> breakLookup = new HashMap<>();
            for (BreakSchedule bs : breakSchedules) {
                String key = bs.getEmployeeId() + "-" + bs.getDate();
                breakLookup.put(key, bs);
            }

            for (String date : workingDates.stream().map(LocalDate::toString).sorted().collect(Collectors.toList())) {
                if (assignmentDetails.containsKey(date) || assignedEmployeesPerDate.containsKey(date)) {
                    Map<String, Object> daySummary = new HashMap<>();
                    daySummary.put("date", date);

                    List<Map<String, Object>> assignmentsWithBreaks = new ArrayList<>();
                    if (assignmentDetails.containsKey(date)) {
                        for (Map<String, Object> assignment : assignmentDetails.get(date)) {
                            Map<String, Object> assignmentWithBreak = new LinkedHashMap<>(assignment);

                            String employeeId = (String) assignment.get("employeeId");
                            String lookupKey = employeeId + "-" + date;
                            BreakSchedule breakSchedule = breakLookup.get(lookupKey);

                            if (breakSchedule != null) {
                                Map<String, Object> breakInfo = new LinkedHashMap<>();
                                breakInfo.put("breakStart", breakSchedule.getBreakStart());
                                breakInfo.put("breakEnd", breakSchedule.getBreakEnd());
                                breakInfo.put("breakDuration", breakSchedule.getBreakDurationMinutes());
                                breakInfo.put("breakType", breakSchedule.getBreakType());
                                breakInfo.put("breakSlot", breakSchedule.getFormattedBreakSlot());

                                assignmentWithBreak.put("break", breakInfo);
                            }

                            assignmentsWithBreaks.add(assignmentWithBreak);
                        }
                    }

                    daySummary.put("assignments", assignmentsWithBreaks);
                    daySummary.put("count", assignmentsWithBreaks.size());

                    Map<String, Integer> roleCounts = new HashMap<>();
                    for (Map<String, Object> assignment : assignmentsWithBreaks) {
                        String role = (String) assignment.get("role");
                        roleCounts.put(role, roleCounts.getOrDefault(role, 0) + 1);
                    }
                    daySummary.put("role_counts", roleCounts);

                    dailySummary.add(daySummary);
                } else {
                    // No assignments on this date
                    Map<String, Object> daySummary = new HashMap<>();
                    daySummary.put("date", date);
                    daySummary.put("assignments", new ArrayList<>());
                    daySummary.put("count", 0);
                    daySummary.put("role_counts", new HashMap<>());
                    dailySummary.add(daySummary);
                }
            }

            response.put("daily_summary", dailySummary);

            // Build message
            StringBuilder message = new StringBuilder();
            message.append("Successfully assigned shifts for ").append(workingDates.size()).append(" days. ");
            message.append("Total assignments: ").append(assignedCount).append(". ");

            if (scheduleBreaks && !breakSchedules.isEmpty()) {
                message.append("Scheduled ").append(breakSchedules.size()).append(" breaks (")
                        .append(breakDurationMinutes).append(" mins after ").append(breakAfterHours).append(" hours). ");
            }

            if (skippedCount > 0) {
                message.append("Skipped ").append(skippedCount).append(" assignments due to existing assignments or constraints. ");
            }

            response.put("message", message.toString());

            System.out.println("\n✅ Assignment Complete!");
            System.out.println("Total assignments: " + assignedCount);
            System.out.println("Breaks scheduled: " + breakSchedules.size());
            System.out.println("Skipped: " + skippedCount);

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(Map.of(
                    "error", "Optimization failed: " + e.getMessage(),
                    "stacktrace", Arrays.toString(e.getStackTrace())
            )).build();
        }
    }
    @POST
    @Path("/shifts/batch-assign")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response assignMultipleShifts(Map<String, Object> input) {
        try {
            System.out.println("=== POST /shifts/batch-assign ===");
            System.out.println("Input: " + input);

            // Parse shifts array
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shifts = (List<Map<String, Object>>) input.get("shifts");

            if (shifts == null || shifts.isEmpty()) {
                return Response.status(400).entity(Map.of(
                        "error", "Missing required field",
                        "required", "shifts array cannot be empty"
                )).build();
            }

            // Validate all shifts first
            List<Map<String, Object>> validationErrors = new ArrayList<>();
            for (int i = 0; i < shifts.size(); i++) {
                Map<String, Object> shift = shifts.get(i);
                List<String> missingFields = validateShiftInput(shift);
                if (!missingFields.isEmpty()) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("shift_index", i);
                    error.put("shift_name", shift.get("shift_name"));
                    error.put("missing_fields", missingFields);
                    validationErrors.add(error);
                }
            }

            if (!validationErrors.isEmpty()) {
                return Response.status(400).entity(Map.of(
                        "status", "error",
                        "message", "Validation failed for one or more shifts",
                        "errors", validationErrors
                )).build();
            }

            // Process each shift
            List<Map<String, Object>> shiftResults = new ArrayList<>();
            Map<String, Object> overallStats = new HashMap<>();

            int totalAssignments = 0;
            int totalWorkingDays = 0;
            int totalBreaksScheduled = 0;
            int totalSkipped = 0;
            long totalSolverTime = 0;

            for (int i = 0; i < shifts.size(); i++) {
                Map<String, Object> shift = shifts.get(i);
                System.out.println("\n📋 Processing shift " + (i+1) + "/" + shifts.size());
                System.out.println("   Shift Name: " + shift.get("shift_name"));

                try {
                    // Process individual shift
                    Map<String, Object> shiftResult = processSingleShift(shift);
                    shiftResult.put("shift_index", i);
                    shiftResults.add(shiftResult);

                    // Accumulate statistics
                    if ("success".equals(shiftResult.get("status"))) {
                        totalAssignments += (int) shiftResult.getOrDefault("new_assignments_made", 0);
                        totalWorkingDays += (int) shiftResult.getOrDefault("total_working_days", 0);
                        totalBreaksScheduled += (int) shiftResult.getOrDefault("breakSchedulesCount", 0);
                        totalSkipped += (int) shiftResult.getOrDefault("skipped_count", 0);
                        totalSolverTime += ((Number) shiftResult.getOrDefault("solver_time_seconds", 0.0)).longValue();
                    }

                } catch (Exception e) {
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("shift_index", i);
                    errorResult.put("shift_name", shift.get("shift_name"));
                    errorResult.put("status", "error");
                    errorResult.put("error_message", e.getMessage());
                    shiftResults.add(errorResult);
                    e.printStackTrace();
                }
            }

            // Prepare overall statistics
            overallStats.put("total_shifts_processed", shifts.size());
            overallStats.put("successful_shifts", shiftResults.stream()
                    .filter(r -> "success".equals(r.get("status")))
                    .count());
            overallStats.put("failed_shifts", shiftResults.stream()
                    .filter(r -> "error".equals(r.get("status")))
                    .count());
            overallStats.put("total_assignments_made", totalAssignments);
            overallStats.put("total_working_days", totalWorkingDays);
            overallStats.put("total_breaks_scheduled", totalBreaksScheduled);
            overallStats.put("total_skipped_assignments", totalSkipped);
            overallStats.put("total_solver_time_seconds", totalSolverTime);

            // Build final response
            Map<String, Object> response = new HashMap<>();
            response.put("status", "completed");
            response.put("overall_statistics", overallStats);
            response.put("shift_results", shiftResults);

            // Add summary message
            String summary = String.format(
                    "Batch assignment completed. Processed %d shifts. Success: %d, Failed: %d. " +
                            "Total assignments: %d across %d days. Scheduled %d breaks.",
                    shifts.size(),
                    overallStats.get("successful_shifts"),
                    overallStats.get("failed_shifts"),
                    totalAssignments,
                    totalWorkingDays,
                    totalBreaksScheduled
            );
            response.put("summary", summary);

            System.out.println("\n✅ Batch Assignment Complete!");
            System.out.println(summary);

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(Map.of(
                    "status", "error",
                    "error", "Batch assignment failed: " + e.getMessage(),
                    "stacktrace", Arrays.toString(e.getStackTrace())
            )).build();
        }
    }

    /**
     * Validate required fields for a single shift
     */
    private List<String> validateShiftInput(Map<String, Object> shift) {
        List<String> missingFields = new ArrayList<>();

        String[] requiredFields = {
                "shift_name", "start_date", "end_date",
                "start_time", "end_time", "roles", "existing_users"
        };

        for (String field : requiredFields) {
            if (!shift.containsKey(field) || shift.get(field) == null) {
                missingFields.add(field);
            }
        }

        // Validate roles array is not empty
        if (shift.containsKey("roles")) {
            List<?> roles = (List<?>) shift.get("roles");
            if (roles == null || roles.isEmpty()) {
                missingFields.add("roles (cannot be empty)");
            }
        }

        // Validate existing_users array is not empty
        if (shift.containsKey("existing_users")) {
            List<?> users = (List<?>) shift.get("existing_users");
            if (users == null || users.isEmpty()) {
                missingFields.add("existing_users (cannot be empty)");
            }
        }

        return missingFields;
    }

    /**
     * Process a single shift using the existing assignment logic
     * This extracts the core logic from your existing assignShiftsWithBreaks method
     */
    private Map<String, Object> processSingleShift(Map<String, Object> input) throws Exception {
        // Store original values and create working copy
        Map<String, Object> workingInput = new HashMap<>(input);

        // Set default values if not provided
        workingInput.putIfAbsent("schedule_breaks", true);
        workingInput.putIfAbsent("overrideExisting", false);
        workingInput.putIfAbsent("prioritizePermanent", true);

        if (!workingInput.containsKey("break_duration_minutes")) {
            workingInput.put("break_duration_minutes", 30);
        }
        if (!workingInput.containsKey("break_after_hours")) {
            workingInput.put("break_after_hours", 4);
        }

        System.out.println("   Processing: " + workingInput.get("shift_name"));

        // ============ VALIDATION FIRST ============
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> existingUsers = (List<Map<String, Object>>) workingInput.get("existing_users");

        // Validate for duplicate employee IDs
        if (existingUsers != null && !existingUsers.isEmpty()) {
            Map<String, List<String>> duplicateIds = new HashMap<>();
            Set<String> uniqueIds = new HashSet<>();

            for (Map<String, Object> user : existingUsers) {
                Object empIdObj = user.get("employee_id");
                if (empIdObj != null) {
                    String empId = empIdObj.toString();
                    String employeeName = (String) user.getOrDefault("name", "Unknown");

                    if (uniqueIds.contains(empId)) {
                        duplicateIds.computeIfAbsent(empId, k -> new ArrayList<>()).add(employeeName);
                    } else {
                        uniqueIds.add(empId);
                    }
                }
            }

            if (!duplicateIds.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Duplicate employee IDs found in request");
                errorResponse.put("error_type", "DUPLICATE_EMPLOYEE_IDS");

                List<Map<String, Object>> duplicateDetails = new ArrayList<>();
                for (Map.Entry<String, List<String>> entry : duplicateIds.entrySet()) {
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("employee_id", entry.getKey());
                    detail.put("employee_names", entry.getValue());
                    detail.put("count", entry.getValue().size());
                    duplicateDetails.add(detail);
                }
                errorResponse.put("duplicates", duplicateDetails);

                throw new Exception("Duplicate employee IDs: " + duplicateIds.keySet());
            }

            System.out.println("   ✅ Validation passed: All " + existingUsers.size() + " employee IDs are unique");
        }

        // 1. Parse input
        String shiftName    = (String) workingInput.get("shift_name");
        String startDateStr = (String) workingInput.get("start_date");
        String endDateStr   = (String) workingInput.get("end_date");
        String startTime    = (String) workingInput.get("start_time");
        String endTime      = (String) workingInput.get("end_time");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roles = (List<Map<String, Object>>) workingInput.get("roles");

        // Break configuration
        boolean scheduleBreaks = Boolean.TRUE.equals(workingInput.getOrDefault("schedule_breaks", true));
        int breakDurationMinutes = 30;
        int breakAfterHours = 4;

        if (workingInput.containsKey("break_duration_minutes")) {
            breakDurationMinutes = ((Number) workingInput.get("break_duration_minutes")).intValue();
        }
        if (workingInput.containsKey("break_after_hours")) {
            breakAfterHours = ((Number) workingInput.get("break_after_hours")).intValue();
        }

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate   = LocalDate.parse(endDateStr);

        if (endDate.isBefore(startDate)) {
            throw new Exception("End date must be after start date");
        }

        // Working dates (skip Sundays)
        List<LocalDate> workingDates = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            if (current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workingDates.add(current);
            }
            current = current.plusDays(1);
        }

        LocalTime startLocalTime = LocalTime.parse(startTime);
        LocalTime endLocalTime = LocalTime.parse(endTime);

        // Calculate shift duration
        double shiftDurationHours;
        if (endLocalTime.isBefore(startLocalTime)) {
            long minutesToMidnight = Duration.between(startLocalTime, LocalTime.MAX).toMinutes() + 1;
            long minutesFromMidnight = Duration.between(LocalTime.MIN, endLocalTime).toMinutes();
            shiftDurationHours = (minutesToMidnight + minutesFromMidnight) / 60.0;
        } else {
            shiftDurationHours = Duration.between(startLocalTime, endLocalTime).toMinutes() / 60.0;
        }

        System.out.println("   Shift: " + startTime + " to " + endTime);
        System.out.println("   Calculated duration: " + shiftDurationHours + " hours");

        // Check if shift duration allows for break
        if (scheduleBreaks && shiftDurationHours < (breakAfterHours + (breakDurationMinutes / 60.0))) {
            throw new Exception("Shift duration too short for scheduled break. " +
                    "Shift: " + String.format("%.2f hours", shiftDurationHours) +
                    ", Required: " + String.format("%.2f hours", breakAfterHours + (breakDurationMinutes / 60.0)));
        }

        boolean overrideExisting = Boolean.TRUE.equals(workingInput.get("overrideExisting"));

        // ============ CREATE EMPLOYEE INFO OBJECTS FROM INPUT ============
        Map<String, EmployeeInfo> allEmployees = new HashMap<>();
        Map<String, Double> employeeWages = new HashMap<>();
        Map<String, Integer> employeeRatings = new HashMap<>();
        Map<String, String> employeeRoles = new HashMap<>();
        Map<String, String> employeeGenders = new HashMap<>();
        Map<String, String> employeeTypes = new HashMap<>();

        int empCounter = 1;

        for (Map<String, Object> user : existingUsers) {
            String name     = (String) user.get("name");
            Number rateObj  = (Number) user.get("rate");
            String unit     = (String) user.get("unit");
            Object ratingObj = user.get("rating");
            String role     = (String) user.get("role");
            String existingEmployeeId = (String) user.get("employee_id");

            String gender = user.containsKey("gender") ?
                    (String) user.get("gender") : "Male";

            String employeeType = user.containsKey("employeeType") ?
                    (String) user.get("employeeType") : "Permanent";

            String employeeId;
            if (existingEmployeeId != null && !existingEmployeeId.trim().isEmpty()) {
                employeeId = existingEmployeeId;
                System.out.println("   📌 Using provided employee ID: " + employeeId + " for " + name);
            } else {
                EmployeeInfo existing = findExistingEmployee(name, role);
                employeeId = (existing != null) ? existing.getId() : "EMP" + String.format("%03d", empCounter++);
                System.out.println("   🆕 Generated new employee ID: " + employeeId + " for " + name);
            }

            // Calculate hourly wage
            double hourlyWage = rateObj.doubleValue();
            if ("day".equalsIgnoreCase(unit)) {
                hourlyWage = hourlyWage / 8.0;
            } else if ("month".equalsIgnoreCase(unit)) {
                hourlyWage = hourlyWage / (22.0 * 8.0);
            }

            int performanceRating = parseRating(ratingObj);

            String email = name.toLowerCase().replace(" ", ".") + "@company.com";
            String phone = "+91 9" + String.format("%09d", empCounter * 1234567);

            // Store all employee data
            employeeWages.put(employeeId, hourlyWage);
            employeeRatings.put(employeeId, performanceRating);
            employeeRoles.put(employeeId, role);
            employeeGenders.put(employeeId, gender);
            employeeTypes.put(employeeId, employeeType);

            double finalHourlyWage = hourlyWage;

            EmployeeInfo empInfo = employeeInfo.computeIfAbsent(employeeId, k ->
                    new EmployeeInfo(employeeId, name, employeeType, gender, finalHourlyWage,
                            "MGR001", "Operations", role, email, phone)
            );

            empInfo.setPerformanceRating(performanceRating);
            empInfo.setPosition(role);
            empInfo.setHourlyWage(hourlyWage);
            empInfo.setGender(gender);
            empInfo.setEmployeeType(employeeType);
            addSkillsBasedOnRole(empInfo, role);
            empInfo.setName(name);
            empInfo.setDepartment("Operations");
            empInfo.setCategory(employeeType);
            allEmployees.put(employeeId, empInfo);

            System.out.println("   👤 Employee: " + name + " (" + employeeId + ") - Type: " + employeeType +
                    ", Rating: " + performanceRating + ", Wage: $" + String.format("%.2f", hourlyWage) + "/hr");
        }

        // ============ CREATE ROLE LIMITS AND RATING REQUIREMENTS ============
        List<Scheduler.ShiftSchedule.RoleLimit> roleLimits = new ArrayList<>();
        List<Scheduler.ShiftSchedule.RatingRequirement> ratingRequirements = new ArrayList<>();

        for (Map<String, Object> roleSpec : roles) {
            String roleName = (String) roleSpec.get("role_name");
            Object ratingObj = roleSpec.get("rating");
            Number maxWorkersObj = (Number) roleSpec.get("max_workers");

            roleLimits.add(new Scheduler.ShiftSchedule.RoleLimit(roleName, maxWorkersObj.intValue()));

            List<Integer> allowedRatings = new ArrayList<>();
            if (ratingObj instanceof Number) {
                int min = ((Number) ratingObj).intValue();
                for (int r = min; r <= 5; r++) allowedRatings.add(r);
            } else if (ratingObj instanceof String) {
                String s = ((String) ratingObj).toLowerCase();
                if (s.contains("any") || s.contains("all")) {
                    for (int r = 1; r <= 5; r++) allowedRatings.add(r);
                } else {
                    try {
                        int min = Integer.parseInt(s.replaceAll("[^0-9]", ""));
                        for (int r = min; r <= 5; r++) allowedRatings.add(r);
                    } catch (Exception e) {
                        for (int r = 3; r <= 5; r++) allowedRatings.add(r);
                    }
                }
            }
            ratingRequirements.add(new Scheduler.ShiftSchedule.RatingRequirement(roleName, allowedRatings));
        }

        // ============ CHECK EXISTING ASSIGNMENTS PER DATE ============
        System.out.println("\n   🔍 Checking for existing assignments in date range...");

        // Build a map of employee+date -> shift for quick lookup
        Map<String, String> employeeExistingAssignments = new HashMap<>();
        Map<String, Integer> existingAssignmentsCount = new HashMap<>();

        for (LocalDate date : workingDates) {
            String dateStr = date.toString();
            Map<String, List<String>> dayAssignments = shiftAssignments.get(dateStr);

            if (dayAssignments != null) {
                System.out.println("   📅 Found existing assignments for " + dateStr + ":");
                for (Map.Entry<String, List<String>> entry : dayAssignments.entrySet()) {
                    String shift = entry.getKey();
                    List<String> employees = entry.getValue();

                    System.out.println("      - " + shift + " shift: " + employees.size() + " employees");

                    for (String empId : employees) {
                        String key = empId + "-" + dateStr;
                        employeeExistingAssignments.put(key, shift);
                        existingAssignmentsCount.put(dateStr, existingAssignmentsCount.getOrDefault(dateStr, 0) + 1);
                    }
                }
            }
        }

        // ============ SORT EMPLOYEES BY PRIORITY ============
        boolean prioritizePermanent = Boolean.TRUE.equals(workingInput.get("prioritizePermanent"));
        List<EmployeeInfo> sortedEmployees = new ArrayList<>(allEmployees.values());

        // Sort by: Permanent first, then higher rating, then lower wage (cost optimization)
        sortedEmployees.sort((a, b) -> {
            if (prioritizePermanent) {
                boolean aPerm = "Permanent".equalsIgnoreCase(a.getEmployeeType());
                boolean bPerm = "Permanent".equalsIgnoreCase(b.getEmployeeType());
                if (aPerm && !bPerm) return -1;
                if (!aPerm && bPerm) return 1;
            }

            int ratingCompare = Integer.compare(b.getPerformanceRating(), a.getPerformanceRating());
            if (ratingCompare != 0) return ratingCompare;

            return Double.compare(a.getHourlyWage(), b.getHourlyWage());
        });

        // ============ BUILD PLANNING ENTITIES - CHECK PER DATE ============
        List<Scheduler.EmployeeAssignment> planningEntities = new ArrayList<>();
        Map<String, List<String>> skippedPerDate = new HashMap<>();
        Map<String, Map<String, Integer>> roleCountsPerDate = new HashMap<>();

        int totalPossibleAssignments = workingDates.size() * sortedEmployees.size();
        int skippedCount = 0;
        int entityCounter = 0;

        Map<String, Set<String>> availableEmployeesPerDate = new HashMap<>();

        for (LocalDate date : workingDates) {
            String dateStr = date.toString();
            List<String> skippedOnThisDate = new ArrayList<>();
            availableEmployeesPerDate.put(dateStr, new HashSet<>());

            Map<String, Integer> dailyRoleCounts = new HashMap<>();
            for (Scheduler.ShiftSchedule.RoleLimit limit : roleLimits) {
                dailyRoleCounts.put(limit.getRoleName(), 0);
            }
            roleCountsPerDate.put(dateStr, dailyRoleCounts);

            for (EmployeeInfo emp : sortedEmployees) {
                String empId = emp.getId();
                String key = empId + "-" + dateStr;

                if (employeeExistingAssignments.containsKey(key) && !overrideExisting) {
                    skippedOnThisDate.add(emp.getName() + " (" + empId + ") - Already on " + employeeExistingAssignments.get(key) + " shift");
                    skippedCount++;
                    continue;
                }

                if (("Night".equals(shiftName) || "night".equalsIgnoreCase(shiftName))
                        && "Female".equalsIgnoreCase(emp.getGender())) {
                    skippedOnThisDate.add(emp.getName() + " (" + empId + ") - Female cannot work night shift");
                    skippedCount++;
                    continue;
                }

                String position = emp.getPosition();
                boolean ratingOk = false;
                for (Scheduler.ShiftSchedule.RatingRequirement req : ratingRequirements) {
                    if (req.getRoleName().equals(position)) {
                        ratingOk = req.getAllowedRatings().contains(emp.getPerformanceRating());
                        break;
                    }
                }
                if (!ratingOk) {
                    skippedOnThisDate.add(emp.getName() + " (" + empId + ") - Rating " + emp.getPerformanceRating() +
                            " doesn't meet requirement for " + position);
                    skippedCount++;
                    continue;
                }

                availableEmployeesPerDate.get(dateStr).add(empId);

                Scheduler.EmployeeAssignment entity = new Scheduler.EmployeeAssignment(
                        "entity-" + entityCounter++,
                        emp.getId(),
                        emp.getName(),
                        dateStr,
                        emp.getCategory(),
                        emp.getGender(),
                        emp.getDepartment(),
                        emp.getPosition()
                );

                entity.setSkills(new HashSet<>(emp.getSkills()));
                entity.setShiftColor(emp.getShiftColor());
                entity.setHourlyWage(emp.getHourlyWage());
                entity.setPerformanceRating(emp.getPerformanceRating());
                entity.setEmployeeType(emp.getEmployeeType());
                entity.setPermanentEmployee("Permanent".equalsIgnoreCase(emp.getEmployeeType()));
                entity.setRequestedShift(shiftName);

                planningEntities.add(entity);
            }

            if (!skippedOnThisDate.isEmpty()) {
                skippedPerDate.put(dateStr, skippedOnThisDate);
            }
        }

        System.out.println("\n   📊 Planning summary:");
        System.out.println("      Total possible assignments: " + totalPossibleAssignments);
        System.out.println("      Entities to plan: " + planningEntities.size());
        System.out.println("      Skipped due to constraints: " + skippedCount);

        // Check if ALL entities were skipped
        if (!overrideExisting && planningEntities.isEmpty()) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "No employees were assigned - all requested employees already have assignments or don't meet constraints");
            errorResponse.put("error_type", "ALL_EMPLOYEES_SKIPPED");
            errorResponse.put("start_date", startDateStr);
            errorResponse.put("end_date", endDateStr);
            errorResponse.put("shift_name", shiftName);
            errorResponse.put("total_requested", existingUsers.size());
            errorResponse.put("skipped_count", skippedCount);
            errorResponse.put("skipped_by_date", skippedPerDate);

            throw new Exception("No employees available for assignment");
        }

        // ============ CONFIGURE AND RUN SOLVER ============
        List<String> possibleShifts = Arrays.asList(shiftName);

        Scheduler.ShiftConstraints.setConfiguration(roleLimits, ratingRequirements);

        Scheduler.ShiftSchedule problem = new Scheduler.ShiftSchedule(
                planningEntities,
                possibleShifts,
                roleLimits,
                ratingRequirements
        );
        problem.setRequestedShiftName(shiftName);
        problem.setPrioritizePermanent(prioritizePermanent);

        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(Scheduler.ShiftSchedule.class)
                .withEntityClasses(Scheduler.EmployeeAssignment.class)
                .withConstraintProviderClass(Scheduler.ShiftConstraints.class)
                .withTerminationSpentLimit(Duration.ofSeconds(20));

        SolverFactory<Scheduler.ShiftSchedule> solverFactory = SolverFactory.create(solverConfig);
        Solver<Scheduler.ShiftSchedule> solver = solverFactory.buildSolver();

        System.out.println("   🔧 Starting solver with " + planningEntities.size() + " entities...");

        long solverStart = System.currentTimeMillis();
        Scheduler.ShiftSchedule solved = solver.solve(problem);
        long solverTimeMs = System.currentTimeMillis() - solverStart;
        System.out.println("   ✅ Solver finished in " + (solverTimeMs / 1000.0) + " seconds. Score: " + solved.getScore());

        // ============ APPLY ASSIGNMENTS AND GENERATE BREAK SCHEDULES ============
        int assignedCount = 0;
        Map<String, Map<String, Integer>> finalRoleCounts = new HashMap<>();
        Map<String, List<Map<String, Object>>> assignmentDetails = new HashMap<>();
        Map<String, Long> assignmentsByEmployeeType = new HashMap<>();
        Map<String, Set<String>> assignedEmployeesPerDate = new HashMap<>();

        List<BreakSchedule> breakSchedules = new ArrayList<>();

        for (Scheduler.EmployeeAssignment ea : solved.getAssignments()) {
            if (ea.getShift() == null) continue;

            String d = ea.getDate();
            String s = ea.getShift();
            String eid = ea.getEmployeeId();

            EmployeeInfo emp = allEmployees.get(eid);
            if (emp == null) continue;

            if ("Night".equals(s) && "Female".equalsIgnoreCase(emp.getGender())) {
                continue;
            }

            String position = emp.getPosition();
            Map<String, Integer> dayRoleCounts = finalRoleCounts.computeIfAbsent(d, k -> new HashMap<>());
            int currentDayCount = dayRoleCounts.getOrDefault(position, 0);

            boolean withinLimit = true;
            for (Scheduler.ShiftSchedule.RoleLimit limit : roleLimits) {
                if (limit.getRoleName().equals(position)) {
                    withinLimit = currentDayCount < limit.getMaxWorkers();
                    break;
                }
            }

            if (!withinLimit) continue;

            shiftAssignments
                    .computeIfAbsent(d, k -> new HashMap<>())
                    .computeIfAbsent(s, k -> new ArrayList<>())
                    .add(eid);

            String breakStart = null;
            String breakEnd = null;
            String breakSlot = null;

            if (scheduleBreaks) {
                ShiftTime shiftTime = SHIFT_TIMES.get(s);
                if (shiftTime == null) {
                    shiftTime = new ShiftTime(startLocalTime, endLocalTime);
                }

                BreakSchedule breakSchedule = new BreakSchedule(
                        eid,
                        emp.getName(),
                        d,
                        s,
                        shiftTime.getStartTime(),
                        shiftTime.getEndTime()
                );

                if (breakAfterHours != 4) {
                    LocalTime customBreakStart = shiftTime.getStartTime().plusHours(breakAfterHours);
                    LocalTime customBreakEnd = customBreakStart.plusMinutes(breakDurationMinutes);
                    breakSchedule.setBreakStartTime(customBreakStart);
                    breakSchedule.setBreakEndTime(customBreakEnd);
                }

                breakSchedules.add(breakSchedule);

                breakStart = breakSchedule.getBreakStart();
                breakEnd = breakSchedule.getBreakEnd();
                breakSlot = breakSchedule.getFormattedBreakSlot();
            }

            // Sync to MySQL
            mysqlService.syncAssignment(
                    d, s, eid,
                    ea.getEmployeeName(),
                    ea.getPosition(),
                    emp.getEmployeeType(),
                    emp.getGender(),
                    emp.getPerformanceRating()
            );

            if (emp.getEmployeeType() != null) {
                mysqlService.syncAssignment(
                        d, s, eid,
                        ea.getEmployeeName(), ea.getPosition(),
                        emp.getEmployeeType(), emp.getGender(),
                        emp.getPerformanceRating()
                );
            }

            assignedEmployeesPerDate.computeIfAbsent(d, k -> new HashSet<>()).add(eid);

            Map<String, Object> detail = new HashMap<>();
            detail.put("employeeId", eid);
            detail.put("employeeName", ea.getEmployeeName());
            detail.put("role", position);
            detail.put("rating", emp.getPerformanceRating());
            detail.put("wage", emp.getHourlyWage());
            detail.put("gender", emp.getGender());
            detail.put("employeeType", emp.getEmployeeType());

            if (breakStart != null) {
                Map<String, Object> breakInfo = new HashMap<>();
                breakInfo.put("breakStart", breakStart);
                breakInfo.put("breakEnd", breakEnd);
                breakInfo.put("breakDuration", breakDurationMinutes);
                breakInfo.put("breakType", "MANDATORY");
                breakInfo.put("breakSlot", breakSlot);
                detail.put("break", breakInfo);
            }

            assignmentDetails.computeIfAbsent(d, k -> new ArrayList<>()).add(detail);

            String empType = emp.getEmployeeType();
            assignmentsByEmployeeType.put(empType, assignmentsByEmployeeType.getOrDefault(empType, 0L) + 1);

            assignedCount++;
            dayRoleCounts.put(position, currentDayCount + 1);
        }

        // Save JSON to file
        saveAssignments();

        // ============ BUILD RESPONSE ============
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("shift_name", shiftName);
        response.put("period", startDateStr + " to " + endDateStr);
        response.put("shift_time", startTime + " - " + endTime);
        response.put("total_working_days", workingDates.size());
        response.put("solver_score", solved.getScore().toString());
        response.put("solver_time_seconds", solverTimeMs / 1000.0);
        response.put("new_assignments_made", assignedCount);
        response.put("entities_planned", planningEntities.size());
        response.put("total_possible_assignments", totalPossibleAssignments);
        response.put("skipped_count", skippedCount);
        response.put("override_used", overrideExisting);
        response.put("prioritize_permanent", prioritizePermanent);
        response.put("breakSchedulesCount", breakSchedules.size());
        response.put("breakConfiguration", Map.of(
                "scheduleBreaks", scheduleBreaks,
                "breakDurationMinutes", breakDurationMinutes,
                "breakAfterHours", breakAfterHours
        ));

        // Add skipped by date information
        if (!skippedPerDate.isEmpty()) {
            Map<String, Object> skippedInfo = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : skippedPerDate.entrySet()) {
                skippedInfo.put(entry.getKey(), entry.getValue());
            }
            response.put("skipped_by_date", skippedInfo);
        }

        // Add role statistics
        Map<String, Object> roleStats = new HashMap<>();
        for (String role : roleLimits.stream().map(rl -> rl.getRoleName()).collect(Collectors.toSet())) {
            Map<String, Object> stats = new HashMap<>();
            long roleCount = assignmentDetails.values().stream()
                    .flatMap(List::stream)
                    .filter(a -> role.equals(a.get("role")))
                    .count();

            double totalWage = assignmentDetails.values().stream()
                    .flatMap(List::stream)
                    .filter(a -> role.equals(a.get("role")))
                    .mapToDouble(a -> (Double) a.get("wage"))
                    .sum();

            double avgWage = roleCount > 0 ? totalWage / roleCount : 0;

            stats.put("assignments", roleCount);
            stats.put("average_wage", String.format("%.2f", avgWage));

            int maxWorkers = roleLimits.stream()
                    .filter(rl -> rl.getRoleName().equals(role))
                    .findFirst()
                    .map(Scheduler.ShiftSchedule.RoleLimit::getMaxWorkers)
                    .orElse(0);
            stats.put("max_per_day", maxWorkers);

            roleStats.put(role, stats);
        }
        response.put("role_statistics", roleStats);

        // Add assignments by employee type
        response.put("assignments_by_employee_type", assignmentsByEmployeeType);

        // Add daily summary
        List<Map<String, Object>> dailySummary = new ArrayList<>();

        Map<String, BreakSchedule> breakLookup = new HashMap<>();
        for (BreakSchedule bs : breakSchedules) {
            String key = bs.getEmployeeId() + "-" + bs.getDate();
            breakLookup.put(key, bs);
        }

        for (String date : workingDates.stream().map(LocalDate::toString).sorted().collect(Collectors.toList())) {
            if (assignmentDetails.containsKey(date) || assignedEmployeesPerDate.containsKey(date)) {
                Map<String, Object> daySummary = new HashMap<>();
                daySummary.put("date", date);

                List<Map<String, Object>> assignmentsWithBreaks = new ArrayList<>();
                if (assignmentDetails.containsKey(date)) {
                    for (Map<String, Object> assignment : assignmentDetails.get(date)) {
                        Map<String, Object> assignmentWithBreak = new LinkedHashMap<>(assignment);

                        String employeeId = (String) assignment.get("employeeId");
                        String lookupKey = employeeId + "-" + date;
                        BreakSchedule breakSchedule = breakLookup.get(lookupKey);

                        if (breakSchedule != null) {
                            Map<String, Object> breakInfo = new LinkedHashMap<>();
                            breakInfo.put("breakStart", breakSchedule.getBreakStart());
                            breakInfo.put("breakEnd", breakSchedule.getBreakEnd());
                            breakInfo.put("breakDuration", breakSchedule.getBreakDurationMinutes());
                            breakInfo.put("breakType", breakSchedule.getBreakType());
                            breakInfo.put("breakSlot", breakSchedule.getFormattedBreakSlot());

                            assignmentWithBreak.put("break", breakInfo);
                        }

                        assignmentsWithBreaks.add(assignmentWithBreak);
                    }
                }

                daySummary.put("assignments", assignmentsWithBreaks);
                daySummary.put("count", assignmentsWithBreaks.size());

                Map<String, Integer> roleCounts = new HashMap<>();
                for (Map<String, Object> assignment : assignmentsWithBreaks) {
                    String role = (String) assignment.get("role");
                    roleCounts.put(role, roleCounts.getOrDefault(role, 0) + 1);
                }
                daySummary.put("role_counts", roleCounts);

                dailySummary.add(daySummary);
            } else {
                Map<String, Object> daySummary = new HashMap<>();
                daySummary.put("date", date);
                daySummary.put("assignments", new ArrayList<>());
                daySummary.put("count", 0);
                daySummary.put("role_counts", new HashMap<>());
                dailySummary.add(daySummary);
            }
        }

        response.put("daily_summary", dailySummary);

        // Build message
        StringBuilder message = new StringBuilder();
        message.append("Successfully assigned shifts for ").append(workingDates.size()).append(" days. ");
        message.append("Total assignments: ").append(assignedCount).append(". ");

        if (scheduleBreaks && !breakSchedules.isEmpty()) {
            message.append("Scheduled ").append(breakSchedules.size()).append(" breaks (")
                    .append(breakDurationMinutes).append(" mins after ").append(breakAfterHours).append(" hours). ");
        }

        if (skippedCount > 0) {
            message.append("Skipped ").append(skippedCount).append(" assignments due to existing assignments or constraints. ");
        }

        response.put("message", message.toString());

        System.out.println("\n   ✅ Assignment Complete for " + shiftName + "!");
        System.out.println("      Total assignments: " + assignedCount);
        System.out.println("      Breaks scheduled: " + breakSchedules.size());

        return response;
    }
    // NEW: Helper method to get employee type priority
    private int getEmployeeTypePriority(String employeeType) {
        if (employeeType == null) return 0;

        String type = employeeType.toLowerCase();
        if (type.contains("permanent") || type.contains("full-time")) {
            return 4;
        } else if (type.contains("contractor") || type.contains("contract")) {
            return 3;
        } else if (type.contains("temporary") || type.contains("temp") || type.contains("part-time")) {
            return 2;
        } else if (type.contains("intern") || type.contains("trainee") || type.contains("apprentice")) {
            return 1;
        }
        return 0;
    }

    // Helper method to find existing employee by name and role
    private EmployeeInfo findExistingEmployee(String name, String role) {
        for (EmployeeInfo emp : employeeInfo.values()) {
            if (emp.getName().equalsIgnoreCase(name.trim())) {
                return emp;
            }
        }
        return null;
    }

    // Helper method to save assignment to specific date
    private void saveAssignmentToDate(String dateStr, String shiftName, List<String> employeeIds) {
        Map<String, List<String>> dayAssignments = shiftAssignments.computeIfAbsent(dateStr, k -> new HashMap<>());
        List<String> existingEmployees = dayAssignments.computeIfAbsent(shiftName, k -> new ArrayList<>());

        // Add new employees (avoid duplicates)
        for (String empId : employeeIds) {
            if (!existingEmployees.contains(empId)) {
                existingEmployees.add(empId);
            }
        }

        System.out.println("  Saved to " + dateStr + " - " + shiftName + ": " + existingEmployees.size() + " employees");
    }
    private String getUnassignmentReason(EmployeeInfo emp, String shiftName, boolean isNightShift,
                                         Map<String, List<Integer>> roleRatingRequirements) {

        if ("Night".equals(shiftName) && isNightShift && "Female".equalsIgnoreCase(emp.getGender())) {
            return "Gender restriction: Female employees cannot work night shift";
        }

        String role = emp.getPosition();
        if (!roleRatingRequirements.containsKey(role)) {
            return "No requirement for this role";
        }

        List<Integer> requiredRatings = roleRatingRequirements.get(role);
        if (!requiredRatings.contains(emp.getPerformanceRating())) {
            return "Rating " + emp.getPerformanceRating() + " not in required range: " + requiredRatings;
        }

        return "Maximum workers quota filled for this role";
    }
    // Helper method to calculate shift duration
    private double calculateShiftDuration(LocalTime start, LocalTime end) {
        Duration duration;

        // Check if shift crosses midnight
        if (end.isBefore(start)) {
            // Night shift: end time is next day
            duration = Duration.between(start, end.plusHours(24));
        } else {
            // Day shift
            duration = Duration.between(start, end);
        }

        double hours = duration.toMinutes() / 60.0;
        System.out.println("Shift duration: " + start + " to " + end + " = " + hours + " hours");
        return hours;
    }

    // Helper method to analyze ratings
    private Map<String, Object> analyzeRatings(List<Map<String, Object>> assignedEmployees) {
        Map<String, Object> analysis = new HashMap<>();

        Map<Integer, Integer> ratingCount = new HashMap<>();
        double totalRating = 0;
        double totalWage = 0;

        for (Map<String, Object> emp : assignedEmployees) {
            int rating = (Integer) emp.get("rating");
            double wage = (Double) emp.get("hourly_wage");

            ratingCount.put(rating, ratingCount.getOrDefault(rating, 0) + 1);
            totalRating += rating;
            totalWage += wage;
        }

        double avgRating = assignedEmployees.isEmpty() ? 0 : totalRating / assignedEmployees.size();
        double avgWage = assignedEmployees.isEmpty() ? 0 : totalWage / assignedEmployees.size();

        analysis.put("rating_distribution", ratingCount);
        analysis.put("average_rating", String.format("%.2f", avgRating));
        analysis.put("average_hourly_wage", String.format("$%.2f", avgWage));
        analysis.put("highest_rating", ratingCount.keySet().stream().max(Integer::compare).orElse(0));
        analysis.put("lowest_rating", ratingCount.keySet().stream().min(Integer::compare).orElse(0));

        return analysis;
    }

    // Helper method to add skills
    private static void addSkillsBasedOnRole(EmployeeInfo empInfo, String role) {
        if (role == null) return;

        String roleLower = role.toLowerCase();

        empInfo.addSkill("Communication");
        empInfo.addSkill("Teamwork");

        if (roleLower.contains("cnc") || roleLower.contains("operator")) {
            empInfo.addSkill("CNC Operation");
            empInfo.addSkill("Machine Setup");
            empInfo.addSkill("Quality Control");
            empInfo.addSkill("Precision Work");
            empInfo.addSkill("Safety Procedures");
        } else if (roleLower.contains("helper") || roleLower.contains("assistant")) {
            empInfo.addSkill("Material Handling");
            empInfo.addSkill("Assembly Support");
            empInfo.addSkill("Tool Management");
            empInfo.addSkill("Workshop Assistance");
        } else if (roleLower.contains("office") || roleLower.contains("boy")) {
            empInfo.addSkill("Office Support");
            empInfo.addSkill("Document Handling");
            empInfo.addSkill("Errand Running");
            empInfo.addSkill("Administrative Tasks");
        }
    }

    // Helper method to calculate cumulative hours
    private double calculateCumulativeHours(String employeeId, int dayCount, double hoursPerDay, int workingDaysPerWeek) {
        int currentWeek = (dayCount - 1) / workingDaysPerWeek;
        int daysInCurrentWeek = dayCount - (currentWeek * workingDaysPerWeek);
        return daysInCurrentWeek * hoursPerDay;
    }

    // Helper method to calculate total labor cost
    private double calculateTotalLaborCost(Map<String, String> employeeShiftAssignment,
                                           Map<String, EmployeeInfo> allEmployees,
                                           int totalDays, double hoursPerDay) {
        double totalCost = 0;

        for (Map.Entry<String, String> entry : employeeShiftAssignment.entrySet()) {
            String employeeId = entry.getKey();
            EmployeeInfo emp = allEmployees.get(employeeId);

            if (emp != null) {
                // Calculate days worked (exclude Sundays)
                int workingDays = totalDays;
                totalCost += emp.getHourlyWage() * hoursPerDay * workingDays;
            }
        }

        return totalCost;
    }

    // Helper method to convert assignments to employee list
    private List<Map<String, Object>> convertToEmployeeList(Map<String, String> employeeShiftAssignment,
                                                            Map<String, EmployeeInfo> allEmployees) {
        return employeeShiftAssignment.entrySet().stream()
                .map(entry -> {
                    String employeeId = entry.getKey();
                    String shift = entry.getValue();
                    EmployeeInfo emp = allEmployees.get(employeeId);

                    Map<String, Object> empData = new HashMap<>();
                    empData.put("id", employeeId);
                    empData.put("name", emp.getName());
                    empData.put("assigned_shift", shift);
                    empData.put("role", emp.getPosition());
                    empData.put("rating", emp.getPerformanceRating());
                    empData.put("hourly_wage", emp.getHourlyWage());
                    empData.put("weekly_hours", 48);
                    empData.put("gender", emp.getGender());

                    return empData;
                })
                .sorted((a, b) -> {
                    // Sort by shift, then by rating (desc), then by wage (asc)
                    String shiftA = (String) a.get("assigned_shift");
                    String shiftB = (String) b.get("assigned_shift");
                    int shiftCompare = shiftA.compareTo(shiftB);
                    if (shiftCompare != 0) return shiftCompare;

                    int ratingA = (Integer) a.get("rating");
                    int ratingB = (Integer) b.get("rating");
                    int ratingCompare = Integer.compare(ratingB, ratingA);
                    if (ratingCompare != 0) return ratingCompare;

                    double wageA = (Double) a.get("hourly_wage");
                    double wageB = (Double) b.get("hourly_wage");
                    return Double.compare(wageA, wageB);
                })
                .collect(Collectors.toList());
    }
    // Helper method to determine shift type based on time
    private String determineShiftType(LocalTime startTime, LocalTime endTime) {
        int startHour = startTime.getHour();

        if (startHour >= 6 && startHour < 12) {
            return "Morning";
        } else if (startHour >= 12 && startHour < 18) {
            return "Afternoon";
        } else {
            return "Night";
        }
    }

    // Helper method to calculate hourly wage from different units
    private double calculateHourlyWage(double rate, String unit) {
        if (unit == null) return rate;

        return switch (unit.toLowerCase()) {
            case "hour" -> rate;
            case "day" -> rate / 8; // Assuming 8-hour work day
            case "month" -> rate / (22 * 8); // Assuming 22 working days, 8 hours/day
            default -> rate;
        };
    }

    // Helper method to parse rating from various formats
    private int parseRating(Object ratingObj) {
        if (ratingObj == null) return 3; // Default

        if (ratingObj instanceof Number) {
            return ((Number) ratingObj).intValue();
        } else if (ratingObj instanceof String) {
            String ratingStr = (String) ratingObj;
            if ("Any".equalsIgnoreCase(ratingStr) || "All".equalsIgnoreCase(ratingStr)) {
                return 3; // Default for "Any"
            }
            try {
                return Integer.parseInt(ratingStr);
            } catch (NumberFormatException e) {
                return 3; // Default on error
            }
        }
        return 3;
    }

    // Helper method to parse rating string (e.g., "4+", "3-5", "3,4,5")
    private List<Integer> parseRatingString(String ratingStr) {
        List<Integer> ratings = new ArrayList<>();

        if (ratingStr == null || ratingStr.isEmpty()) {
            ratings.add(3); // Default
            return ratings;
        }

        if (ratingStr.endsWith("+")) {
            // Rating and above: "3+" means 3,4,5
            int baseRating = Integer.parseInt(ratingStr.substring(0, ratingStr.length() - 1));
            for (int i = baseRating; i <= 5; i++) {
                ratings.add(i);
            }
        } else if (ratingStr.contains("-")) {
            // Rating range: "3-4" means 3 and 4
            String[] parts = ratingStr.split("-");
            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);
            for (int i = start; i <= end; i++) {
                ratings.add(i);
            }
        } else if (ratingStr.contains(",")) {
            // Comma-separated list: "3,4,5"
            String[] parts = ratingStr.split(",");
            for (String part : parts) {
                ratings.add(Integer.parseInt(part.trim()));
            }
        } else {
            // Single exact rating: "3"
            ratings.add(Integer.parseInt(ratingStr));
        }

        return ratings;
    }
    @GET
    @Path("/shifts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCurrentSchedule(@QueryParam("date") String dateStr) {
        try {
            System.out.println("=== GET /shifts called ===");
            System.out.println("📊 Total assignments stored: " + shiftAssignments.size() + " days");
            System.out.println("👥 Employee records: " + employeeInfo.size());

            // If specific date requested
            if (dateStr != null) {
                System.out.println("🔍 Looking for date: " + dateStr);
                Map<String, List<String>> dayAssignments = shiftAssignments.get(dateStr);

                if (dayAssignments == null || dayAssignments.isEmpty()) {
                    System.out.println("❌ No assignments found for date: " + dateStr);
                    return Response.ok(Map.of(
                            "date", dateStr,
                            "assignments", Map.of(),
                            "slots", List.of(),
                            "totalAssigned", 0,
                            "message", "No assignments found for this date"
                    )).build();
                }

                System.out.println("✅ Found assignments for " + dateStr + ": " + dayAssignments.size() + " shifts");

                // Convert to frontend format
                List<Map<String, Object>> slots = new ArrayList<>();
                int totalAssigned = 0;
                double totalHourlyCost = 0;

                for (Map.Entry<String, List<String>> entry : dayAssignments.entrySet()) {
                    String shift = entry.getKey();
                    List<String> employeeIds = entry.getValue();

                    Map<String, Object> slot = new HashMap<>();
                    slot.put("date", dateStr);
                    slot.put("name", shift);
                    slot.put("shiftColor", SHIFT_COLORS.getOrDefault(shift, "#607D8B"));

                    List<Map<String, Object>> employees = new ArrayList<>();
                    double shiftHourlyCost = 0;

                    for (String empId : employeeIds) {
                        EmployeeInfo emp = employeeInfo.get(empId);
                        if (emp != null) {
                            Map<String, Object> empMap = new HashMap<>();
                            empMap.put("id", emp.getId());
                            empMap.put("name", emp.getName());
                            empMap.put("role", emp.getPosition()); // Keep actual position from data
                            empMap.put("rating", emp.getPerformanceRating());
                            empMap.put("hourlyWage", emp.getHourlyWage());
                            empMap.put("gender", emp.getGender());


                            employees.add(empMap);
                            totalAssigned++;
                            shiftHourlyCost += emp.getHourlyWage();
                        } else {
                            // Generic handling for unknown employees
                            System.out.println("⚠️ Employee not found: " + empId);
                            Map<String, Object> empMap = new HashMap<>();
                            empMap.put("id", empId);
                            empMap.put("name", generateGenericName(empId, "Employee"));
                            empMap.put("role", "Employee");
                            empMap.put("gender", "Unknown");


                            employees.add(empMap);
                            totalAssigned++;
                        }
                    }

                    // Calculate costs
                    double shiftDailyCost = shiftHourlyCost * 8;

                    slot.put("employees", employees);
                    slot.put("employeeCount", employees.size());
                    slot.put("hourlyCost", shiftHourlyCost);
                    slot.put("dailyCost", shiftDailyCost);

                    totalHourlyCost += shiftHourlyCost;
                    slots.add(slot);
                }

                System.out.println("✅ Returning " + totalAssigned + " assignments");

                return Response.ok(Map.of(
                        "date", dateStr,
                        "slots", slots,
                        "totalAssigned", totalAssigned,
                        "totalHourlyCost", totalHourlyCost,
                        "totalDailyCost", totalHourlyCost * 8,
                        "shiftsCount", dayAssignments.size()
                )).build();

            } else {
                // Return all assignments
                System.out.println("📋 Returning all assignments");

                List<Map<String, Object>> allSlots = new ArrayList<>();
                List<String> sortedDates = new ArrayList<>(shiftAssignments.keySet());
                Collections.sort(sortedDates);

                int totalAssignments = 0;
                Map<String, Integer> shiftCounts = new HashMap<>();

                for (String date : sortedDates) {
                    Map<String, List<String>> dayAssignments = shiftAssignments.get(date);
                    if (dayAssignments == null || dayAssignments.isEmpty()) {
                        continue;
                    }

                    for (Map.Entry<String, List<String>> entry : dayAssignments.entrySet()) {
                        String shift = entry.getKey();
                        List<String> employeeIds = entry.getValue();

                        Map<String, Object> slot = new HashMap<>();
                        slot.put("date", date);
                        slot.put("name", shift);
                        slot.put("shiftColor", SHIFT_COLORS.getOrDefault(shift, "#607D8B"));

                        List<Map<String, Object>> employees = new ArrayList<>();
                        for (String empId : employeeIds) {
                            EmployeeInfo emp = employeeInfo.get(empId);
                            if (emp != null) {
                                Map<String, Object> empMap = new HashMap<>();
                                empMap.put("id", emp.getId());
                                empMap.put("name", emp.getName());
                                empMap.put("role", emp.getPosition());
                                empMap.put("gender", emp.getGender());

                                employees.add(empMap);
                            } else {
                                // Generic handling
                                Map<String, Object> empMap = new HashMap<>();
                                empMap.put("id", empId);
                                empMap.put("name", generateGenericName(empId, "Employee"));
                                empMap.put("role", "Employee");
                                empMap.put("gender", "Unknown");

                                employees.add(empMap);
                            }
                        }

                        slot.put("employees", employees);
                        slot.put("employeeCount", employees.size());
                        allSlots.add(slot);

                        totalAssignments += employees.size();
                        shiftCounts.put(shift, shiftCounts.getOrDefault(shift, 0) + employees.size());
                    }
                }

                // Generate generic statistics
                Map<String, Object> statistics = new HashMap<>();
                statistics.put("totalSlots", allSlots.size());
                statistics.put("totalAssignments", totalAssignments);
                statistics.put("uniqueDates", sortedDates.size());
                statistics.put("assignmentsByShift", shiftCounts);

                // Generic employee count
                statistics.put("totalEmployees", employeeInfo.size());

                System.out.println("✅ Total slots: " + allSlots.size());
                System.out.println("✅ Total assignments: " + totalAssignments);

                return Response.ok(Map.of(
                        "slots", allSlots,
                        "statistics", statistics,
                        "datesWithAssignments", sortedDates,
                        "message", "Schedule retrieved successfully"
                )).build();
            }

        } catch (Exception e) {
            System.err.println("❌ Error in GET /shifts: " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get schedule: " + e.getMessage()))
                    .build();
        }
    }

    private String extractRoleFromId(String empId) {
        if (empId == null || empId.trim().isEmpty()) {
            return "Employee";
        }

        // Simply return "Employee" for everyone - completely generic
        return "Employee";
    }

    // Helper method to generate generic name
    private String generateGenericName(String empId, String role) {
        if (empId == null || empId.trim().isEmpty()) {
            return "Employee";
        }

        try {
            // Just use the ID itself or extract number for generic naming
            String numericPart = empId.replaceAll("[^0-9]", "");
            if (!numericPart.isEmpty()) {
                int empNum = Integer.parseInt(numericPart);
                return "Employee " + empNum;
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }

        // Return the ID as-is if it's alphanumeric
        return empId;
    }
    // Helper method to safely determine group from employee ID
    private String determineGroupFromId(String employeeId) {
        try {
            // Try to extract numeric part from the ID
            String numericPart = employeeId.replaceAll("[^0-9]", "");

            if (numericPart.isEmpty()) {
                return "Unknown"; // No numbers found
            }

            int empNum = Integer.parseInt(numericPart);
            boolean isGroupA = empNum % 2 == 1;
            return isGroupA ? "A" : "B";

        } catch (NumberFormatException e) {
            System.err.println("Error parsing employee ID: " + employeeId + " - " + e.getMessage());
            return "Unknown";
        }
    }

    // Helper method to count assignments by date
    private Map<String, Integer> getAssignmentsCountByDate() {
        Map<String, Integer> counts = new HashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> entry : shiftAssignments.entrySet()) {
            int total = entry.getValue().values().stream().mapToInt(List::size).sum();
            counts.put(entry.getKey(), total);
        }
        return counts;
    }

    // NEW METHOD: Convert shiftAssignments to Bryntum format
    private Map<String, Object> convertShiftAssignmentsToBryntumFormat() {
        System.out.println("📊 Converting shiftAssignments to Bryntum format...");

        List<Map<String, Object>> employeeAssignments = new ArrayList<>();
        List<Map<String, Object>> slotData = new ArrayList<>();
        List<Map<String, Object>> leaveEvents = new ArrayList<>();
        List<Map<String, Object>> otCoverageEvents = new ArrayList<>();

        // Process each day in shiftAssignments
        for (Map.Entry<String, Map<String, List<String>>> dayEntry : shiftAssignments.entrySet()) {
            String dateStr = dayEntry.getKey();
            Map<String, List<String>> dayAssignments = dayEntry.getValue();

            try {
                LocalDate date = LocalDate.parse(dateStr);

                // Skip weekends for slot creation
                if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    // Process each shift
                    for (Map.Entry<String, List<String>> shiftEntry : dayAssignments.entrySet()) {
                        String shift = shiftEntry.getKey();
                        List<String> employeeIds = shiftEntry.getValue();

                        Map<String, Object> slot = new HashMap<>();
                        slot.put("date", dateStr);
                        slot.put("name", shift);
                        slot.put("shiftColor", SHIFT_COLORS.get(shift));

                        List<Map<String, Object>> employees = new ArrayList<>();
                        for (String empId : employeeIds) {
                            EmployeeInfo emp = employeeInfo.get(empId);
                            if (emp != null) {
                                // Add to slot
                                Map<String, Object> empSlot = new HashMap<>();
                                empSlot.put("id", emp.getId());
                                empSlot.put("name", emp.getName());

                                empSlot.put("position", emp.getPosition());
                                empSlot.put("employeeColor", emp.getShiftColor());
                                employees.add(empSlot);

                                // Add to assignments list
                                Map<String, Object> assignment = new HashMap<>();
                                assignment.put("id", emp.getId());
                                assignment.put("name", emp.getName());

                                assignment.put("position", emp.getPosition());
                                assignment.put("shift", shift);
                                assignment.put("date", dateStr);
                                assignment.put("employeeColor", emp.getShiftColor());
                                assignment.put("shiftColor", SHIFT_COLORS.get(shift));
                                employeeAssignments.add(assignment);
                            }
                        }

                        slot.put("employees", employees);
                        slot.put("employeeCount", employees.size());
                        slotData.add(slot);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing date " + dateStr + ": " + e.getMessage());
            }
        }

        // Get leaves
        for (Map.Entry<String, List<LeaveRecord>> entry : employeeLeaves.entrySet()) {
            String employeeId = entry.getKey();
            EmployeeInfo empInfo = employeeInfo.get(employeeId);

            for (LeaveRecord leaveRecord : entry.getValue()) {
                Map<String, Object> leaveEvent = new HashMap<>();
                leaveEvent.put("id", "leave-" + employeeId + "-" + leaveRecord.getDate());
                leaveEvent.put("employeeId", employeeId);
                leaveEvent.put("employeeName", empInfo != null ? empInfo.getName() : employeeId);
                leaveEvent.put("date", leaveRecord.getDate());
                leaveEvent.put("type", "leave");
                leaveEvent.put("employeeColor", empInfo != null ? empInfo.getShiftColor() : "#607D8B");

                leaveEvents.add(leaveEvent);
            }
        }

        System.out.println("📊 Converted shift assignments to Bryntum format:");
        System.out.println("   - Assignments: " + employeeAssignments.size());
        System.out.println("   - Slots: " + slotData.size());
        System.out.println("   - Leave days: " + leaveEvents.size());
        System.out.println("   - OT Coverage assignments: " + otCoverageEvents.size());

        Map<String, Object> response = new HashMap<>();
        response.put("employees", employeeAssignments);
        response.put("slots", slotData);
        response.put("leaves", leaveEvents);
        response.put("otCoverages", otCoverageEvents);

        return response;
    }
    @POST
    @Path("/shifts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)

    public Response reoptimize() {
        try {
            System.out.println("=== POST /shifts called - Refreshing manual schedule ===");

            // Step 1: Clear temporary data only (attendance, OT, coverage)
            otCoverageAssignments.clear();
            AttendanceService.clearAllClockStatusAndAttendance();
            System.out.println("🔄 Cleared temporary data (attendance, OT, coverage)");

            // Step 2: Do NOT clear shiftAssignments - preserve supervisor's manual work!
            // shiftAssignments.clear();  // ← Commented out intentionally
            // saveAssignments();         // ← No need to save empty

            // Step 3: Return current manual schedule (from persisted assignments)
            Scheduler.ScheduleResponse response = Scheduler.getExistingSchedule();

            // Optional: Force frontend to refresh calendar
            System.out.println("📋 Returning current manual assignments");

            return Response.ok(response)
                    .header("Access-Control-Allow-Origin", "*")
                    .build();

        } catch (Exception e) {
            System.err.println("❌ Error refreshing schedule: " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to refresh schedule: " + e.getMessage()))
                    .header("Access-Control-Allow-Origin", "*")
                    .build();
        }
    }

    @GET
    @Path("/shifts/employee-leaves/{employeeId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEmployeeLeaves(@PathParam("employeeId") String employeeId) {
        try {
            List<Map<String, Object>> employeeLeavesList = new ArrayList<>();

            List<LeaveRecord> leaveDates = employeeLeaves.get(employeeId);
            EmployeeInfo empInfo = employeeInfo.get(employeeId);

            for (LeaveRecord leaveDate : leaveDates) {
                Map<String, Object> leaveInfo = new HashMap<>();
                leaveInfo.put("employeeId", employeeId);
                leaveInfo.put("employeeName", empInfo != null ? empInfo.getName() : employeeId);
                leaveInfo.put("leaveDate", leaveDate);

                leaveInfo.put("position", empInfo != null ? empInfo.getPosition() : "Unknown");
                leaveInfo.put("employeeColor", empInfo != null ? empInfo.getShiftColor() : "#607D8B");
                employeeLeavesList.add(leaveInfo);
            }
            if (employeeLeaves.containsKey(employeeId)) {
            }

            return Response.ok(Map.of(
                    "employeeId", employeeId,
                    "leaves", employeeLeavesList,
                    "count", employeeLeavesList.size()
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get employee leaves"))
                    .build();
        }
    }

    @POST
    @Path("/shifts/apply-leave")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response applyLeave(Map<String, Object> input) {
        String employeeId = (String) input.get("employeeId");
        String startDate = (String) input.get("startDate");
        String endDate = (String) input.get("endDate");
        String leaveType = (String) input.get("leaveType");

        if (employeeId == null || startDate == null || endDate == null || leaveType == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing required fields (including leaveType)"))
                    .build();
        }

        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);

            EmployeeInfo empInfo = employeeInfo.get(employeeId);
            if (empInfo == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Employee not found: " + employeeId))
                        .build();
            }

            int leaveDays = 0;
            List<Map<String, Object>> leaveRecords = new ArrayList<>();
            List<Map<String, Object>> coverageRequests = new ArrayList<>();

            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                // Skip weekends
                if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    continue;
                }

                leaveDays++;
                String dateStr = date.toString();

                // Create leave record for frontend response
                Map<String, Object> leaveRecord = new HashMap<>();
                leaveRecord.put("employeeId", employeeId);
                leaveRecord.put("employeeName", empInfo.getName());
                leaveRecord.put("leaveDate", dateStr);

                leaveRecord.put("position", empInfo.getPosition());
                leaveRecord.put("employeeColor", empInfo.getShiftColor());
                leaveRecord.put("leaveType", leaveType);
                leaveRecords.add(leaveRecord);

                // === PERSIST leave with type ===
                employeeLeaves.computeIfAbsent(employeeId, k -> new ArrayList<>())
                        .add(new LeaveRecord(dateStr, leaveType));

                // === Determine scheduled shift for this specific date ===
                String scheduledShift = null;

                // Look for manual assignment: shiftAssignments.get(dateStr) → map of shift → list of employees
                Map<String, List<String>> dayAssignments = shiftAssignments.get(dateStr);
                if (dayAssignments != null) {
                    // Check each shift to see if this employee is assigned
                    for (Map.Entry<String, List<String>> entry : dayAssignments.entrySet()) {
                        String shiftName = entry.getKey();
                        List<String> assignedEmployees = entry.getValue();
                        if (assignedEmployees != null && assignedEmployees.contains(employeeId)) {
                            scheduledShift = shiftName;
                            break;
                        }
                    }
                }

                // If no manual assignment found for this date, fall back to default shift logic
                if (scheduledShift == null) {
                    scheduledShift = assignDefaultShift(employeeId, empInfo);
                }

                // === Create coverage request if employee has a shift on this day ===
                if (scheduledShift != null) {
                    LeaveCoverageRequest coverageRequest = new LeaveCoverageRequest(employeeId, date, scheduledShift);
                    leaveCoverageRequests.put(coverageRequest.getId(), coverageRequest);

                    Map<String, Object> coverageInfo = new HashMap<>();
                    coverageInfo.put("requestId", coverageRequest.getId());
                    coverageInfo.put("leaveDate", dateStr);
                    coverageInfo.put("shiftName", scheduledShift);
                    coverageInfo.put("requiredSkills", new ArrayList<>(empInfo.getSkills()));

                    coverageInfo.put("status", "PENDING");
                    coverageRequests.add(coverageInfo);

                    System.out.println("Created coverage request for " + empInfo.getName() +
                            " on " + dateStr + " (" + scheduledShift + " shift)");
                }
            }

            // === Check and deduct leave balance ===
            int currentBalance = switch (leaveType) {
                case "ANNUAL" -> empInfo.getAnnualLeaveBalance();
                case "SICK" -> empInfo.getSickLeaveBalance();
                case "CASUAL" -> empInfo.getCasualLeaveBalance();
                default -> throw new IllegalStateException("Unexpected value: " + leaveType);
            };

            if (currentBalance < leaveDays) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Insufficient " + leaveType + " leave balance. Required: " + leaveDays +
                                ", Available: " + currentBalance))
                        .build();
            }

            // Deduct balance
            switch (leaveType) {
                case "ANNUAL" -> empInfo.setAnnualLeaveBalance(currentBalance - leaveDays);
                case "SICK" -> empInfo.setSickLeaveBalance(currentBalance - leaveDays);
                case "CASUAL" -> empInfo.setCasualLeaveBalance(currentBalance - leaveDays);
            }

            System.out.println("Leave applied: " + leaveType + " for " + empInfo.getName() +
                    " (" + leaveDays + " days). Remaining " + leaveType + ": " +
                    switch (leaveType) {
                        case "ANNUAL" -> empInfo.getAnnualLeaveBalance();
                        case "SICK" -> empInfo.getSickLeaveBalance();
                        case "CASUAL" -> empInfo.getCasualLeaveBalance();
                        default -> 0;
                    });

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "Leave applied successfully",
                    "employeeId", employeeId,
                    "employeeName", empInfo.getName(),
                    "leaveType", leaveType,
                    "leaveDays", leaveDays,
                    "leaveRecords", leaveRecords,
                    "coverageRequests", coverageRequests,
                    "coverageRequestsCount", coverageRequests.size(),
                    "remainingBalance", Map.of(
                            "annual", empInfo.getAnnualLeaveBalance(),
                            "sick", empInfo.getSickLeaveBalance(),
                            "casual", empInfo.getCasualLeaveBalance()
                    )
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid data: " + e.getMessage()))
                    .build();
        }
    }
    // NEW: Create leave coverage request

    private void createLeaveCoverageRequest(String employeeId, LocalDate leaveDate, EmployeeInfo empInfo) {
        String leaveDateStr = leaveDate.toString();

        // Get the assigned shift for this employee on this specific date
        String scheduledShift = null;

        if (shiftAssignments.containsKey(leaveDateStr)) {
            Map<String, List<String>> dayAssignments = shiftAssignments.get(leaveDateStr);
            // Check Morning, Afternoon, Night to find which shift this employee is in
            for (String shift : Arrays.asList("Morning", "Afternoon", "Night")) {
                List<String> employeesInShift = dayAssignments.get(shift);
                if (employeesInShift != null && employeesInShift.contains(employeeId)) {
                    scheduledShift = shift;
                    break;
                }
            }
        }

        // If no assignment found for this date, fall back to default shift based on department
        if (scheduledShift == null) {
            scheduledShift = assignDefaultShift(employeeId, empInfo);
            System.out.println("⚠️ No manual assignment found for " + empInfo.getName() +
                    " on " + leaveDate + ", using default: " + scheduledShift);
        }

        if (scheduledShift != null) {
            LeaveCoverageRequest coverageRequest = new LeaveCoverageRequest(
                    employeeId, leaveDate, scheduledShift
            );

            leaveCoverageRequests.put(coverageRequest.getId(), coverageRequest);

            System.out.println("Created leave coverage request for " + empInfo.getName() +
                    " on " + leaveDate + " for " + scheduledShift + " shift");

            System.out.println("Coverage request details:");
            System.out.println("  - Request ID: " + coverageRequest.getId());
            System.out.println("  - Absent Employee: " + empInfo.getName() + " (" + employeeId + ")");
            System.out.println("  - Date: " + leaveDate);
            System.out.println("  - Shift: " + scheduledShift);
            System.out.println("  - Department: " + empInfo.getDepartment());
            System.out.println("  - Required Skills: " + empInfo.getSkills());
            System.out.println("  - Manager: " + empInfo.getManagerId());

            // Send notification to manager
            if (systemConfig.isNotifyLeaveCoverageRequired() && empInfo.getManagerId() != null) {
                System.out.println("Sending notification to manager: " + empInfo.getManagerId());
                sendNotification(empInfo.getManagerId(),
                        "LEAVE_COVERAGE_REQUIRED",
                        empInfo.getName() + " is on leave on " + leaveDate +
                                ". " + scheduledShift + " shift coverage required. " +
                                "Required skills: " + String.join(", ", empInfo.getSkills()));
            } else {
                System.out.println("No manager found or notifications disabled");
            }

            // Log suitable employees
            List<EmployeeInfo> suitableEmployees = coverageRequest.getSuitableEmployees();
            System.out.println("Found " + suitableEmployees.size() + " suitable employees:");
            suitableEmployees.forEach(emp ->
                    System.out.println("  - " + emp.getName() + " (" + emp.getId() + ") - Skills: " + emp.getSkills())
            );
        } else {
            System.out.println("Could not determine shift for " + empInfo.getName() + " on " + leaveDate);
        }
    }

    @POST
    @Path("/shifts/remove-leave")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)

    public Response removeLeave(Map<String, Object> input) {
        String employeeId = (String) input.get("employeeId");
        String leaveDate = (String) input.get("leaveDate"); // Optional: specific date to remove

        if (employeeId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing employeeId"))
                    .build();
        }

        try {
            EmployeeInfo empInfo = employeeInfo.get(employeeId);
            if (empInfo == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Employee not found: " + employeeId))
                        .build();
            }

            if (leaveDate != null && !leaveDate.isEmpty()) {
                // Remove specific leave date
                if (employeeLeaves.containsKey(employeeId)) {
                    boolean removed = employeeLeaves.get(employeeId).remove(leaveDate);
                    if (removed) {
                        // Return 1 day of leave balance
                        String leaveType = "ANNUAL"; // Default, you might want to track this separately
                        switch (leaveType) {
                            case "ANNUAL":
                                empInfo.setAnnualLeaveBalance(empInfo.getAnnualLeaveBalance() + 1);
                                break;
                            case "SICK":
                                empInfo.setSickLeaveBalance(empInfo.getSickLeaveBalance() + 1);
                                break;
                            case "CASUAL":
                                empInfo.setCasualLeaveBalance(empInfo.getCasualLeaveBalance() + 1);
                                break;
                        }

                        System.out.println("🗑️ Removed specific leave for " + employeeId +
                                " (" + empInfo.getName() + ") on " + leaveDate);

                        if (employeeLeaves.get(employeeId).isEmpty()) {
                            employeeLeaves.remove(employeeId);
                        }

                        return Response.ok(Map.of(
                                "status", "success",
                                "message", "Specific leave removed",
                                "employeeId", employeeId,
                                "employeeName", empInfo.getName(),
                                "leaveDate", leaveDate,
                                "remainingLeaves", employeeLeaves.containsKey(employeeId) ?
                                        employeeLeaves.get(employeeId).size() : 0
                        )).build();
                    } else {
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(Map.of("error", "Leave not found for " + employeeId + " on " + leaveDate))
                                .build();
                    }
                }
            } else {
                // Remove all leaves for employee
                int removedCount = 0;
                if (employeeLeaves.containsKey(employeeId)) {
                    removedCount = employeeLeaves.get(employeeId).size();
                    // Return leave balance (assuming all were annual for simplicity)
                    empInfo.setAnnualLeaveBalance(empInfo.getAnnualLeaveBalance() + removedCount);
                    employeeLeaves.remove(employeeId);
                }

                System.out.println("🗑️ Removed ALL " + removedCount + " leaves for: " +
                        employeeId + " (" + empInfo.getName() + ")");

                return Response.ok(Map.of(
                        "status", "success",
                        "message", "All leaves removed",
                        "employeeId", employeeId,
                        "employeeName", empInfo.getName(),
                        "removedCount", removedCount
                )).build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to remove leave: " + e.getMessage()))
                    .build();
        }
        return null;
    }

    @POST
    @Path("/shifts/revoke-leave")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)

    public Response revokeLeave(Map<String, Object> input) {
        String employeeId = (String) input.get("employeeId");
        String leaveDate = (String) input.get("leaveDate");

        if (employeeId == null || leaveDate == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing employeeId or leaveDate"))
                    .build();
        }

        try {
            // Validate date
            LocalDate.parse(leaveDate);

            EmployeeInfo empInfo = employeeInfo.get(employeeId);
            if (empInfo == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Employee not found: " + employeeId))
                        .build();
            }

            if (employeeLeaves.containsKey(employeeId)) {
                List<LeaveRecord> leaves = employeeLeaves.get(employeeId);
                boolean removed = leaves.remove(leaveDate);

                if (removed) {
                    // Return 1 day of leave balance (assuming annual)
                    empInfo.setAnnualLeaveBalance(empInfo.getAnnualLeaveBalance() + 1);

                    System.out.println("✅ Revoked leave for " + employeeId +
                            " (" + empInfo.getName() + ") on " + leaveDate);

                    if (leaves.isEmpty()) {
                        employeeLeaves.remove(employeeId);
                    }

                    return Response.ok(Map.of(
                            "status", "success",
                            "message", "Leave revoked successfully",
                            "employeeId", employeeId,
                            "employeeName", empInfo.getName(),
                            "leaveDate", leaveDate,
                            "remainingBalance", empInfo.getAnnualLeaveBalance()
                    )).build();
                } else {
                    System.out.println("⚠️ No leave found for " + employeeId + " (" + empInfo.getName() + ") on " + leaveDate);
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of("error", "No leave found for employee on " + leaveDate))
                            .build();
                }
            } else {
                System.out.println("⚠️ No leaves found for employee: " + employeeId + " (" + empInfo.getName() + ")");
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "No leaves found for employee"))
                        .build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid data: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/shifts/leaves")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLeaves() {
        try {
            System.out.println("=== GET /shifts/leaves called ===");

            List<Map<String, Object>> allLeaves = new ArrayList<>();

            for (Map.Entry<String, List<LeaveRecord>> entry : employeeLeaves.entrySet()) {
                String employeeId = entry.getKey();
                List<LeaveRecord> leaveDates = entry.getValue();

                EmployeeInfo empInfo = employeeInfo.get(employeeId);

                for (LeaveRecord leaveDate : leaveDates) {
                    Map<String, Object> leaveInfo = new HashMap<>();
                    leaveInfo.put("employeeId", employeeId);
                    leaveInfo.put("employeeName", empInfo != null ? empInfo.getName() : employeeId);
                    leaveInfo.put("leaveDate", leaveDate);

                    leaveInfo.put("position", empInfo != null ? empInfo.getPosition() : "Unknown");
                    leaveInfo.put("employeeColor", empInfo != null ? empInfo.getShiftColor() : "#607D8B");

                    allLeaves.add(leaveInfo);
                }
            }

            System.out.println("✅ Returning " + allLeaves.size() + " leave records");
            return Response.ok(Map.of(
                    "leaves", allLeaves,
                    "count", allLeaves.size(),
                    "employeesWithLeaves", employeeLeaves.size()
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get leaves: " + e.getMessage()))
                    .build();
        }
    }
    @GET
    @Path("/shifts/all-leaves-detailed")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllLeavesDetailed() {
        try {
            List<Map<String, Object>> allLeaves = new ArrayList<>();

            for (Map.Entry<String, List<LeaveRecord>> entry : employeeLeaves.entrySet()) {
                String employeeId = entry.getKey();
                EmployeeInfo empInfo = employeeInfo.get(employeeId);
                if (empInfo == null) continue;

                for (LeaveRecord record : entry.getValue()) {
                    Map<String, Object> leaveInfo = new HashMap<>();
                    leaveInfo.put("id", "leave-" + employeeId + "-" + record.getDate());
                    leaveInfo.put("employeeId", employeeId);
                    leaveInfo.put("employeeName", empInfo.getName());
                    leaveInfo.put("leaveDate", record.getDate());
                    leaveInfo.put("leaveType", record.getLeaveType());  // ← NOW CORRECT!

                    leaveInfo.put("position", empInfo.getPosition());
                    leaveInfo.put("employeeColor", empInfo.getShiftColor());
                    leaveInfo.put("category", empInfo.getCategory());
                    leaveInfo.put("gender", empInfo.getGender());
                    leaveInfo.put("email", empInfo.getEmail());
                    leaveInfo.put("phone", empInfo.getPhone());

                    allLeaves.add(leaveInfo);
                }
            }

            // Sort by date descending
            allLeaves.sort((a, b) -> ((String) b.get("leaveDate")).compareTo((String) a.get("leaveDate")));

            return Response.ok(Map.of(
                    "leaves", allLeaves,
                    "count", allLeaves.size()
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get detailed leaves"))
                    .build();
        }
    }
    // NEW OT COVERAGE ENDPOINTS
    @GET
    @Path("/shifts/coverage/suitable-employees/{employeeId}/{date}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSuitableEmployeesForLeaveCoverage(@PathParam("employeeId") String employeeId,
                                                         @PathParam("date") String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr);

            // Check if employee exists
            EmployeeInfo absentEmp = employeeInfo.get(employeeId);
            if (absentEmp == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Employee not found"))
                        .build();
            }

            // Check if employee is on leave on this date
            boolean isOnLeave = employeeLeaves.containsKey(employeeId) &&
                    employeeLeaves.get(employeeId).contains(dateStr);

            if (!isOnLeave) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Employee is not on leave on " + dateStr))
                        .build();
            }

            // Get employee's shift for this day
            String scheduledShift = shiftAssignments.get(employeeId).toString();
            if (scheduledShift == null) {
                scheduledShift = assignDefaultShift(employeeId, absentEmp);
            }

            // Get suitable employees with skill scores
            List<Map<String, Object>> suitableEmployees = new ArrayList<>();
            int skillThreshold = 50; // Minimum skill match percentage

            for (EmployeeInfo emp : employeeInfo.values()) {
                // Skip absent employee
                if (emp.getId().equals(employeeId)) {
                    continue;
                }

                // Check if employee can cover
                if (emp.canCoverFor(absentEmp, scheduledShift, skillThreshold)) {
                    double skillSimilarity = emp.calculateSkillSimilarity(absentEmp);

                    Map<String, Object> empData = new HashMap<>();
                    empData.put("id", emp.getId());
                    empData.put("name", emp.getName());

                    empData.put("position", emp.getPosition());
                    empData.put("skills", new ArrayList<>(emp.getSkills()));
                    empData.put("hourlyWage", emp.getHourlyWage());
                    empData.put("skillSimilarity", skillSimilarity);
                    empData.put("skillMatch", String.format("%.1f%%", skillSimilarity));
                    empData.put("sameDepartment", emp.getDepartment().equals(absentEmp.getDepartment()));
                    empData.put("canWorkShift", emp.canWorkShift(scheduledShift));
                    empData.put("weeklyOTHours", emp.getWeeklyOTHours());
                    empData.put("shiftColor", emp.getShiftColor());
                    empData.put("email", emp.getEmail());
                    empData.put("phone", emp.getPhone());

                    // Calculate OT wages for 1, 2, 3 hours
                    Map<String, Double> otWages = new HashMap<>();
                    otWages.put("1h", emp.calculateCoverageWage(1, "COVERAGE"));
                    otWages.put("2h", emp.calculateCoverageWage(2, "COVERAGE"));
                    otWages.put("3h", emp.calculateCoverageWage(3, "COVERAGE"));
                    empData.put("otWages", otWages);

                    suitableEmployees.add(empData);
                }
            }

            // Sort by skill similarity (highest first)
            suitableEmployees.sort((a, b) -> {
                double aScore = (Double) a.get("skillSimilarity");
                double bScore = (Double) b.get("skillSimilarity");
                return Double.compare(bScore, aScore);
            });

            return Response.ok(Map.of(
                    "absentEmployee", Map.of(
                            "id", absentEmp.getId(),
                            "name", absentEmp.getName(),

                            "skills", new ArrayList<>(absentEmp.getSkills()),
                            "leaveDate", dateStr,
                            "shift", scheduledShift
                    ),
                    "suitableEmployees", suitableEmployees,
                    "count", suitableEmployees.size(),
                    "skillThreshold", skillThreshold,
                    "message", suitableEmployees.size() + " suitable employees found"
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get suitable employees: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/shifts/coverage/quick-assign")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response quickAssignCoverage(Map<String, Object> input) {
        String employeeId = (String) input.get("employeeId");
        String date = (String) input.get("date");
        String assignedEmployeeId = (String) input.get("assignedEmployeeId");
        Object hoursObj = input.get("hours");
        String managerId = (String) input.get("managerId");
        String coverageType = (String) input.get("coverageType");

        if (employeeId == null || date == null || assignedEmployeeId == null ||
                hoursObj == null || managerId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing required fields"))
                    .build();
        }

        try {
            double hours;
            // Fixed for Java 17: Replace pattern matching switch with instanceof checks
            if (hoursObj instanceof Integer) {
                hours = ((Integer) hoursObj).doubleValue();
            } else if (hoursObj instanceof Double) {
                hours = (Double) hoursObj;
            } else if (hoursObj instanceof String) {
                try {
                    hours = Double.parseDouble((String) hoursObj);
                } catch (NumberFormatException e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid hours format"))
                            .build();
                }
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid hours format"))
                        .build();
            }

            // Validate hours
            if (hours != 1.0 && hours != 2.0 && hours != 3.0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "OT hours must be 1, 2, or 3 hours"))
                        .build();
            }

            // Check if coverage request already exists
            LeaveCoverageRequest existingRequest = null;
            for (LeaveCoverageRequest request : leaveCoverageRequests.values()) {
                if (request.getAbsentEmployeeId().equals(employeeId) &&
                        request.getLeaveDate().toString().equals(date)) {
                    existingRequest = request;
                    break;
                }
            }

            // If no existing request, create one
            if (existingRequest == null) {
                EmployeeInfo absentEmp = employeeInfo.get(employeeId);
                if (absentEmp == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of("error", "Employee not found"))
                            .build();
                }

                // Get employee's shift
                String scheduledShift = shiftAssignments.get(employeeId).toString();
                if (scheduledShift == null) {
                    scheduledShift = assignDefaultShift(employeeId, absentEmp);
                }

                LocalDate leaveDate = LocalDate.parse(date);
                existingRequest = new LeaveCoverageRequest(employeeId, leaveDate, scheduledShift);
                leaveCoverageRequests.put(existingRequest.getId(), existingRequest);
                System.out.println("📋 Created new coverage request for quick assignment");
            }

            // Now assign coverage using the existing method
            OTCoverageAssignment assignment = existingRequest.assignCoverageWithWage(
                    assignedEmployeeId,
                    hours,
                    managerId,
                    coverageType != null ? coverageType : "COVERAGE"
            );

            if (assignment != null) {
                EmployeeInfo assignedEmp = employeeInfo.get(assignedEmployeeId);
                EmployeeInfo absentEmp = employeeInfo.get(employeeId);

                System.out.println("✅ Quick coverage assigned: " + assignedEmp.getName() +
                        " covering for " + absentEmp.getName() + " on " + date +
                        " for " + hours + " hours. OT Wage: $" + String.format("%.2f", assignment.getOtWage()));

                return Response.ok(Map.of(
                        "status", "success",
                        "message", "Coverage assigned successfully",
                        "request", existingRequest,
                        "assignment", assignment,
                        "assignmentDetails", assignment.getAssignmentDetails(),
                        "otWage", assignment.getOtWage(),
                        "formattedWage", String.format("$%.2f", assignment.getOtWage())
                )).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Cannot assign coverage. Employee may not meet criteria."))
                        .build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to assign coverage: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/overtime/coverage-requests")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLeaveCoverageRequests(@QueryParam("status") String status,
                                             @QueryParam("managerId") String managerId) {
        try {
            List<LeaveCoverageRequest> filteredRequests = leaveCoverageRequests.values().stream()
                    .filter(request -> {
                        boolean matches = true;

                        if (status != null && !status.isEmpty()) {
                            matches = status.equals(request.getStatus());
                        }

                        if (managerId != null && !managerId.isEmpty()) {
                            matches = matches && managerId.equals(request.getManagerId());
                        }

                        return matches;
                    })
                    .collect(Collectors.toList());

            return Response.ok(Map.of(
                    "requests", filteredRequests,
                    "count", filteredRequests.size(),
                    "pendingCount", filteredRequests.stream().filter(r -> "PENDING".equals(r.getStatus())).count()
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get coverage requests"))
                    .build();
        }
    }

    @GET
    @Path("/overtime/coverage-requests/{requestId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCoverageRequestDetails(@PathParam("requestId") String requestId) {
        try {
            LeaveCoverageRequest request = leaveCoverageRequests.get(requestId);
            if (request == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Coverage request not found"))
                        .build();
            }

            // Get suitable employees for this coverage
            List<EmployeeInfo> suitableEmployees = request.getSuitableEmployees();

            Map<String, Object> response = new HashMap<>();
            response.put("request", request);
            response.put("suitableEmployees", suitableEmployees);
            response.put("suitableCount", suitableEmployees.size());

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get coverage request details"))
                    .build();
        }
    }

    @POST
    @Path("/overtime/coverage-requests/{requestId}/assign")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response assignCoverage(@PathParam("requestId") String requestId,
                                   Map<String, Object> input) {
        String assignedEmployeeId = (String) input.get("assignedEmployeeId");
        Object hoursObj = input.get("hours");
        String managerId = (String) input.get("managerId");
        String coverageType = (String) input.get("coverageType");
        Object skillThresholdObj = input.get("skillThreshold");

        if (assignedEmployeeId == null || hoursObj == null || managerId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing required fields"))
                    .build();
        }

        try {
            double hours;
            // Fixed for Java 17: Replace pattern matching switch with instanceof checks
            if (hoursObj instanceof Integer) {
                hours = ((Integer) hoursObj).doubleValue();
            } else if (hoursObj instanceof Double) {
                hours = (Double) hoursObj;
            } else if (hoursObj instanceof String) {
                try {
                    hours = Double.parseDouble((String) hoursObj);
                } catch (NumberFormatException e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid hours format"))
                            .build();
                }
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid hours format"))
                        .build();
            }

            // Validate hours (1, 2, or 3 hours)
            if (hours != 1.0 && hours != 2.0 && hours != 3.0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "OT hours must be 1, 2, or 3 hours"))
                        .build();
            }

            LeaveCoverageRequest request = leaveCoverageRequests.get(requestId);
            if (request == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Coverage request not found"))
                        .build();
            }

            // Check if employee can be assigned
            EmployeeInfo assignedEmp = employeeInfo.get(assignedEmployeeId);
            if (assignedEmp == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Assigned employee not found"))
                        .build();
            }

            EmployeeInfo absentEmp = employeeInfo.get(request.getAbsentEmployeeId());
            if (absentEmp == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Absent employee not found"))
                        .build();
            }

            // Get skill threshold from request or use default (50%)
            double skillThreshold = 50.0;
            if (skillThresholdObj != null) {
                // Fixed for Java 17: Replace pattern matching switch with instanceof checks
                if (skillThresholdObj instanceof Integer) {
                    skillThreshold = ((Integer) skillThresholdObj).doubleValue();
                } else if (skillThresholdObj instanceof Double) {
                    skillThreshold = (Double) skillThresholdObj;
                } else if (skillThresholdObj instanceof String) {
                    try {
                        skillThreshold = Double.parseDouble((String) skillThresholdObj);
                    } catch (NumberFormatException e) {
                        // Use default value if parsing fails
                    }
                }
            }

            // Check if employee can cover with skill threshold
            if (!assignedEmp.canCoverFor(absentEmp, request.getShiftName(), skillThreshold)) {
                // Get specific reason why employee cannot cover
                StringBuilder errorMessage = new StringBuilder("Employee cannot cover for this position because: ");

                // Check skill similarity
                double skillSimilarity = assignedEmp.calculateSkillSimilarity(absentEmp);
                if (skillSimilarity < skillThreshold) {
                    errorMessage.append("Insufficient skill match (").append(String.format("%.1f%%", skillSimilarity))
                            .append(" < ").append(String.format("%.1f%%", skillThreshold)).append("). ");
                }

                // Check same department
                if (!assignedEmp.getDepartment().equals(absentEmp.getDepartment())) {
                    errorMessage.append("Different department. ");
                }

                // Check gender restrictions
                if (!assignedEmp.canWorkShift(request.getShiftName())) {
                    errorMessage.append("Gender restrictions prevent working ").append(request.getShiftName()).append(" shift. ");
                }

                // Check OT limits
                if (assignedEmp.hasExceededOTLimits()) {
                    errorMessage.append("Employee has exceeded weekly OT limits. ");
                }

                // Check if already covering on same day
                LocalDate today = LocalDate.now();
                boolean alreadyCovering = otCoverageAssignments.values().stream()
                        .anyMatch(assignment ->
                                assignment.getAssignedEmployeeId().equals(assignedEmployeeId) &&
                                        assignment.getCoverageDate().equals(request.getLeaveDate())
                        );

                if (alreadyCovering) {
                    errorMessage.append("Employee already assigned for coverage on ").append(request.getLeaveDate()).append(". ");
                }

                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", errorMessage.toString()))
                        .build();
            }

            // Check if employee can work additional OT hours
            if (!assignedEmp.canWorkAdditionalOT(hours, request.getLeaveDate())) {
                double dailyOT = assignedEmp.getDailyOTHours(request.getLeaveDate()) + hours;
                double weeklyOT = assignedEmp.getWeeklyOTHours() + hours;

                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error",
                                "Cannot assign " + hours + " hours OT. " +
                                        "Daily OT would be " + String.format("%.1f", dailyOT) + "h (max: " +
                                        systemConfig.getMaxDailyOTHours() + "h). " +
                                        "Weekly OT would be " + String.format("%.1f", weeklyOT) + "h (max: " +
                                        systemConfig.getMaxWeeklyOTHours() + "h)."))
                        .build();
            }

            // Use the new method with wage calculation
            OTCoverageAssignment assignment = request.assignCoverageWithWage(
                    assignedEmployeeId,
                    hours,
                    managerId,
                    coverageType != null ? coverageType : "COVERAGE"
            );

            if (assignment != null) {
                System.out.println("✅ Coverage assigned: " + assignedEmp.getName() +
                        " covering for " + absentEmp.getName() + " on " + request.getLeaveDate() +
                        " for " + hours + " hours. OT Wage: $" + String.format("%.2f", assignment.getOtWage()));

                // Send notification to assigned employee with wage details
                if (systemConfig.isNotifyOTApproval()) {
                    sendNotification(assignedEmployeeId,
                            "COVERAGE_ASSIGNED",
                            "You have been assigned to cover " + absentEmp.getName() +
                                    "'s " + request.getShiftName() + " shift on " + request.getLeaveDate() +
                                    " for " + hours + " hours. Estimated OT pay: $" +
                                    String.format("%.2f", assignment.getOtWage()));
                }

                // Also notify manager about successful assignment
                sendNotification(managerId,
                        "COVERAGE_ASSIGNED_CONFIRMATION",
                        assignedEmp.getName() + " has been assigned to cover " +
                                absentEmp.getName() + "'s shift on " + request.getLeaveDate() +
                                ". OT cost: $" + String.format("%.2f", assignment.getOtWage()));

                return Response.ok(Map.of(
                        "status", "success",
                        "message", "Coverage assigned successfully",
                        "request", request,
                        "assignment", assignment,
                        "assignmentDetails", assignment.getAssignmentDetails(),
                        "otWage", assignment.getOtWage(),
                        "formattedWage", String.format("$%.2f", assignment.getOtWage()),
                        "skillMatch", assignedEmp.calculateSkillSimilarity(absentEmp),
                        "skillMatchFormatted", String.format("%.1f%%", assignedEmp.calculateSkillSimilarity(absentEmp))
                )).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Cannot assign coverage to this request. " +
                                "Possible reasons: Request not in PENDING/ASSIGNED status, " +
                                "or employee no longer meets coverage criteria."))
                        .build();
            }

        } catch (NumberFormatException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid number format for hours or skill threshold"))
                    .build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to assign coverage: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/overtime/coverage-requests/{requestId}/assign-with-wage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response assignCoverageWithWage(@PathParam("requestId") String requestId,
                                           Map<String, Object> input) {
        String assignedEmployeeId = (String) input.get("assignedEmployeeId");
        Object hoursObj = input.get("hours");
        String managerId = (String) input.get("managerId");
        String coverageType = (String) input.get("coverageType");

        if (assignedEmployeeId == null || hoursObj == null || managerId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing required fields"))
                    .build();
        }

        try {
            double hours;
            // Fixed for Java 17: Replace pattern matching switch with instanceof checks
            if (hoursObj instanceof Integer) {
                hours = ((Integer) hoursObj).doubleValue();
            } else if (hoursObj instanceof Double) {
                hours = (Double) hoursObj;
            } else if (hoursObj instanceof String) {
                try {
                    hours = Double.parseDouble((String) hoursObj);
                } catch (NumberFormatException e) {
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "Invalid hours format"))
                            .build();
                }
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Invalid hours format"))
                        .build();
            }

            // Validate hours (1, 2, or 3 hours)
            if (hours != 1.0 && hours != 2.0 && hours != 3.0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "OT hours must be 1, 2, or 3 hours"))
                        .build();
            }

            LeaveCoverageRequest request = leaveCoverageRequests.get(requestId);
            if (request == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Coverage request not found"))
                        .build();
            }

            OTCoverageAssignment assignment = request.assignCoverageWithWage(
                    assignedEmployeeId, hours, managerId,
                    coverageType != null ? coverageType : "COVERAGE"
            );

            if (assignment != null) {
                EmployeeInfo assignedEmp = employeeInfo.get(assignedEmployeeId);
                EmployeeInfo absentEmp = employeeInfo.get(request.getAbsentEmployeeId());

                System.out.println("✅ Coverage assigned: " + assignedEmp.getName() +
                        " covering for " + absentEmp.getName() +
                        " for " + hours + " hours. OT Wage: $" + assignment.getOtWage());

                return Response.ok(Map.of(
                        "status", "success",
                        "message", "Coverage assigned successfully",
                        "assignment", assignment,
                        "otWage", assignment.getOtWage(),
                        "estimatedPay", String.format("$%.2f", assignment.getOtWage())
                )).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Cannot assign coverage to this request"))
                        .build();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to assign coverage: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/overtime/coverage-requests/{requestId}/suitable-employees")
    @Produces(MediaType.APPLICATION_JSON)

    public Response getSuitableEmployeesWithScore(@PathParam("requestId") String requestId,
                                                  @QueryParam("skillThreshold") Double skillThreshold) {
        try {
            LeaveCoverageRequest request = leaveCoverageRequests.get(requestId);
            if (request == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Coverage request not found"))
                        .build();
            }

            // Use provided threshold or default
            double thresholdToUse = skillThreshold != null ? skillThreshold : request.getSkillThreshold();

            // Get suitable employees with skill scores
            List<LeaveCoverageRequest.EmployeeCoverageMatch> matches = request.getSuitableEmployeesWithScore(thresholdToUse);

            // Convert to response format
            List<Map<String, Object>> suitableEmployees = matches.stream().map(match -> {
                EmployeeInfo emp = match.getEmployee();
                Map<String, Object> empData = new HashMap<>();
                empData.put("id", emp.getId());
                empData.put("name", emp.getName());

                empData.put("position", emp.getPosition());
                empData.put("skills", new ArrayList<>(emp.getSkills()));
                empData.put("hourlyWage", emp.getHourlyWage());
                empData.put("skillScore", match.getSkillScore());
                empData.put("totalScore", match.getTotalScore());
                empData.put("sameDepartment", match.isSameDepartment());
                empData.put("nightShiftCertified", match.isNightShiftCertified());
                empData.put("suitabilityLevel", match.getSuitabilityLevel());
                empData.put("shiftColor", emp.getShiftColor());
                empData.put("email", emp.getEmail());
                empData.put("phone", emp.getPhone());
                empData.put("weeklyOTHours", match.getWeeklyOTHours());

                // Calculate OT wages for 1, 2, 3 hours for different coverage types
                Map<String, Map<String, Double>> otWages = new HashMap<>();
                for (String type : Arrays.asList("COVERAGE", "EMERGENCY", "HOLIDAY")) {
                    Map<String, Double> wagesForType = match.getOTWageOptions(type);
                    otWages.put(type, wagesForType);
                }
                empData.put("otWages", otWages);

                return empData;
            }).toList();

            // Get absent employee info
            EmployeeInfo absentEmp = employeeInfo.get(request.getAbsentEmployeeId());
            Map<String, Object> absentEmployeeInfo = new HashMap<>();
            if (absentEmp != null) {
                absentEmployeeInfo.put("id", absentEmp.getId());
                absentEmployeeInfo.put("name", absentEmp.getName());

                absentEmployeeInfo.put("skills", new ArrayList<>(absentEmp.getSkills()));
                absentEmployeeInfo.put("shiftName", request.getShiftName());
                absentEmployeeInfo.put("leaveDate", request.getLeaveDate());
            }

            return Response.ok(Map.of(
                    "request", request,
                    "suitableEmployees", suitableEmployees,
                    "absentEmployee", absentEmployeeInfo,
                    "count", suitableEmployees.size(),
                    "skillThresholdUsed", thresholdToUse,
                    "message", suitableEmployees.isEmpty() ?
                            "No suitable employees found with " + thresholdToUse + "% skill threshold. " +
                                    "Try lowering the threshold or check employee skills." :
                            "Found " + suitableEmployees.size() + " suitable employees"
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get suitable employees: " + e.getMessage()))
                    .build();
        }
    }
    @GET
    @Path("/overtime/coverage-assignments")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCoverageAssignments(@QueryParam("employeeId") String employeeId,
                                           @QueryParam("date") String dateStr) {
        try {
            List<OTCoverageAssignment> filteredAssignments = otCoverageAssignments.values().stream()
                    .filter(assignment -> {
                        boolean matches = true;

                        if (employeeId != null && !employeeId.isEmpty()) {
                            matches = employeeId.equals(assignment.getAssignedEmployeeId());
                        }

                        if (dateStr != null && !dateStr.isEmpty()) {
                            LocalDate date = LocalDate.parse(dateStr);
                            matches = matches && date.equals(assignment.getCoverageDate());
                        }

                        return matches;
                    })
                    .toList();

            return Response.ok(Map.of(
                    "assignments", filteredAssignments,
                    "count", filteredAssignments.size()
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get coverage assignments"))
                    .build();
        }
    }

    @GET
    @Path("/notifications/{recipientId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getNotifications(@PathParam("recipientId") String recipientId,
                                     @QueryParam("unread") Boolean unreadOnly) {
        try {
            List<Notification> recipientNotifications = notifications.values().stream()
                    .filter(n -> n.getRecipientId().equals(recipientId))
                    .filter(n -> !Boolean.TRUE.equals(unreadOnly) || !n.isRead())
                    .sorted((n1, n2) -> n2.getTimestamp().compareTo(n1.getTimestamp()))
                    .collect(Collectors.toList());

            return Response.ok(Map.of(
                    "notifications", recipientNotifications,
                    "count", recipientNotifications.size(),
                    "unreadCount", recipientNotifications.stream().filter(n -> !n.isRead()).count()
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get notifications"))
                    .build();
        }
    }

    @POST
    @Path("/notifications/{notificationId}/read")
    @Produces(MediaType.APPLICATION_JSON)
    public Response markNotificationAsRead(@PathParam("notificationId") String notificationId) {
        try {
            Notification notification = notifications.get(notificationId);
            if (notification == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Notification not found"))
                        .build();
            }

            notification.setRead(true);
            System.out.println("✅ Marked notification as read: " + notificationId);

            return Response.ok(Map.of(
                    "status", "success",
                    "message", "Notification marked as read",
                    "notification", notification
            )).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Failed to mark notification as read"))
                    .build();
        }
    }

    // ========================================
    // TIMEFOLD OPTIMIZATION + BRYNTUM DISPLAY
    // ========================================
    public static class Scheduler {


        // TIMEFOLD PLANNING ENTITY
        @PlanningEntity
        public static class EmployeeAssignment {
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
            private double hourlyWage;
            private int performanceRating;
            private String employeeType;
            private boolean permanentEmployee;
            private boolean prioritizePermanent;
            private LocalTime breakStartTime;
            private LocalTime breakEndTime;
            private boolean hasScheduledBreak = false;
            private int breakDurationMinutes = 30;
            public String getRequestedShift() {
                return requestedShift;
            }


            public void setRequestedShift(String requestedShift) {
                this.requestedShift = requestedShift;
            }
            @PlanningVariable(valueRangeProviderRefs = "shiftRange")
            private String shift;

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

            // Getters and setters
            public int getGroup() {
                return group;
            }

            public void setGroup(int group) {
                this.group = group;
            }

            public String getId() {
                return id;
            }

            public String getEmployeeId() {
                return employeeId;
            }

            public String getEmployeeName() {
                return employeeName;
            }

            public String getDate() {
                return date;
            }

            public String getShift() {
                return shift;
            }

            public void setShift(String shift) {
                this.shift = shift;
            }

            public String getCategory() {
                return category;
            }

            public void setCategory(String category) {
                this.category = category;
            }

            public String getGender() {
                return gender;
            }

            public void setGender(String gender) {
                this.gender = gender;
            }

            public Set<String> getSkills() {
                return skills;
            }

            public void setSkills(Set<String> skills) {
                this.skills = skills;
            }

            public String getDepartment() {
                return department;
            }

            public void setDepartment(String department) {
                this.department = department;
            }

            public String getPosition() {
                return position;
            }

            public void setPosition(String position) {
                this.position = position;
            }

            public String getShiftColor() {
                return shiftColor;
            }

            public void setShiftColor(String shiftColor) {
                this.shiftColor = shiftColor;
            }
            public double getHourlyWage() {
                return hourlyWage;
            }

            public void setHourlyWage(double hourlyWage) {
                this.hourlyWage = hourlyWage;
            }

            public int getPerformanceRating() {
                return performanceRating;
            }

            public void setPerformanceRating(int performanceRating) {
                this.performanceRating = performanceRating;
            }

            public String getEmployeeType() { return employeeType; }

            public void setEmployeeType(String employeeType) {
                this.employeeType = employeeType;
                this.permanentEmployee = "Permanent".equalsIgnoreCase(employeeType);
            }

            public boolean isPermanentEmployee() { return permanentEmployee; }

            public void setPermanentEmployee(boolean permanentEmployee) {
                this.permanentEmployee = permanentEmployee;
            }

            public boolean isPrioritizePermanent() { return prioritizePermanent; }

            public void setPrioritizePermanent(boolean prioritizePermanent) {
                this.prioritizePermanent = prioritizePermanent;
            }

            public LocalTime getBreakStartTime() { return breakStartTime; }
            public void setBreakStartTime(LocalTime breakStartTime) {
                this.breakStartTime = breakStartTime;
                this.hasScheduledBreak = (breakStartTime != null);
            }

            public LocalTime getBreakEndTime() { return breakEndTime; }
            public void setBreakEndTime(LocalTime breakEndTime) { this.breakEndTime = breakEndTime; }

            public boolean isHasScheduledBreak() { return hasScheduledBreak; }
            public void setHasScheduledBreak(boolean hasScheduledBreak) {
                this.hasScheduledBreak = hasScheduledBreak;
            }

            public int getBreakDurationMinutes() { return breakDurationMinutes; }
            public void setBreakDurationMinutes(int breakDurationMinutes) {
                this.breakDurationMinutes = breakDurationMinutes;
            }
        }

        // TIMEFOLD PLANNING SOLUTION
        @PlanningSolution
        public static class ShiftSchedule {
            @PlanningEntityCollectionProperty
            private List<EmployeeAssignment> assignments;

            @ValueRangeProvider(id = "shiftRange")
            private List<String> shiftTypes;

            @ProblemFactCollectionProperty
            private List<RoleLimit> roleLimits = new ArrayList<>();

            @ProblemFactCollectionProperty
            private List<RatingRequirement> ratingRequirements;

            @ProblemFactCollectionProperty
            private List<EmployeeTypePriority> employeeTypePriorities = new ArrayList<>();

            @PlanningScore
            private HardSoftLongScore score;

            private String requestedShiftName;

            private boolean prioritizePermanent;

            public ShiftSchedule() {}

            public ShiftSchedule(List<EmployeeAssignment> assignments, List<String> shiftTypes) {
                this.assignments = assignments;
                this.shiftTypes = shiftTypes;
                this.roleLimits = new ArrayList<>();
                this.ratingRequirements = new ArrayList<>();
                this.employeeTypePriorities = new ArrayList<>();
            }

            public ShiftSchedule(List<EmployeeAssignment> assignments, List<String> shiftTypes,
                                 List<RoleLimit> roleLimits, List<RatingRequirement> ratingRequirements) {
                this.assignments = assignments;
                this.shiftTypes = shiftTypes;
                this.roleLimits = roleLimits;
                this.ratingRequirements = ratingRequirements;
                this.employeeTypePriorities = new ArrayList<>();
            }

            // Getters and setters
            public List<EmployeeAssignment> getAssignments() {
                return assignments;
            }

            public void setAssignments(List<EmployeeAssignment> assignments) {
                this.assignments = assignments;
            }

            public List<String> getShiftTypes() {
                return shiftTypes;
            }

            public void setShiftTypes(List<String> shiftTypes) {
                this.shiftTypes = shiftTypes;
            }

            public List<RoleLimit> getRoleLimits() {
                return roleLimits;
            }

            public void setRoleLimits(List<RoleLimit> roleLimits) {
                this.roleLimits = roleLimits;
            }

            public List<RatingRequirement> getRatingRequirements() {
                return ratingRequirements;
            }

            public void setRatingRequirements(List<RatingRequirement> ratingRequirements) {
                this.ratingRequirements = ratingRequirements;
            }

            public HardSoftLongScore getScore() {
                return score;
            }

            public void setScore(HardSoftLongScore score) {
                this.score = score;
            }

            public String getRequestedShiftName() {
                return requestedShiftName;
            }

            public void setRequestedShiftName(String requestedShiftName) {
                this.requestedShiftName = requestedShiftName;
            }
            public boolean isPrioritizePermanent() { return prioritizePermanent; }
            public void setPrioritizePermanent(boolean prioritizePermanent) {
                this.prioritizePermanent = prioritizePermanent;
            }

            // Problem fact classes
            public static class RoleLimit {
                private String roleName;
                private int maxWorkers;

                public RoleLimit() {}
                public RoleLimit(String roleName, int maxWorkers) {
                    this.roleName = roleName;
                    this.maxWorkers = maxWorkers;
                }
                // Getters and setters
                public String getRoleName() { return roleName; }
                public void setRoleName(String roleName) { this.roleName = roleName; }
                public int getMaxWorkers() { return maxWorkers; }
                public void setMaxWorkers(int maxWorkers) { this.maxWorkers = maxWorkers; }
            }

            public static class EmployeeTypePriority {
                private String employeeType;
                private int priorityPenalty;  // Lower penalty = higher priority

                public EmployeeTypePriority() {}
                public EmployeeTypePriority(String employeeType, int priorityPenalty) {
                    this.employeeType = employeeType;
                    this.priorityPenalty = priorityPenalty;
                }
                // Getters and setters
                public String getEmployeeType() { return employeeType; }
                public void setEmployeeType(String employeeType) { this.employeeType = employeeType; }
                public int getPriorityPenalty() { return priorityPenalty; }
                public void setPriorityPenalty(int priorityPenalty) { this.priorityPenalty = priorityPenalty; }
            }

            public static class RatingRequirement {
                private String roleName;
                private List<Integer> allowedRatings;

                public RatingRequirement() {}

                public RatingRequirement(String roleName, List<Integer> allowedRatings) {
                    this.roleName = roleName;
                    this.allowedRatings = allowedRatings;
                }

                public String getRoleName() {
                    return roleName;
                }

                public void setRoleName(String roleName) {
                    this.roleName = roleName;
                }

                public List<Integer> getAllowedRatings() {
                    return allowedRatings;
                }

                public void setAllowedRatings(List<Integer> allowedRatings) {
                    this.allowedRatings = allowedRatings;
                }
            }
        }

        public static class ShiftConstraints implements ConstraintProvider {

            private static Map<String, Integer> maxWorkersPerRole = new HashMap<>();
            private static Map<String, List<Integer>> requiredRatingsPerRole = new HashMap<>();

            public ShiftConstraints() {
                // Required no-arg constructor
            }

            // Updated setConfiguration method to accept RoleLimit and RatingRequirement lists
            public static void setConfiguration(
                    List<Scheduler.ShiftSchedule.RoleLimit> roleLimits,
                    List<Scheduler.ShiftSchedule.RatingRequirement> ratingRequirements) {

                // Clear existing maps
                maxWorkersPerRole.clear();
                requiredRatingsPerRole.clear();

                // Populate from RoleLimit list
                for (Scheduler.ShiftSchedule.RoleLimit limit : roleLimits) {
                    maxWorkersPerRole.put(limit.getRoleName(), limit.getMaxWorkers());
                }

                // Populate from RatingRequirement list
                for (Scheduler.ShiftSchedule.RatingRequirement req : ratingRequirements) {
                    requiredRatingsPerRole.put(req.getRoleName(), req.getAllowedRatings());
                }

                System.out.println("✅ ShiftConstraints configured with " +
                        maxWorkersPerRole.size() + " role limits and " +
                        requiredRatingsPerRole.size() + " rating requirements");
            }

            @Override
            public Constraint[] defineConstraints(ConstraintFactory factory) {
                return new Constraint[] {
                        // ============ HARD CONSTRAINTS ============

                        // 1. No employee works multiple shifts same day
                        factory.forEachUniquePair(Scheduler.EmployeeAssignment.class,
                                        Joiners.equal(Scheduler.EmployeeAssignment::getEmployeeId),
                                        Joiners.equal(Scheduler.EmployeeAssignment::getDate))
                                .filter((a1, a2) -> !a1.getShift().equals(a2.getShift()))
                                .penalizeLong(HardSoftLongScore.ONE_HARD)
                                .asConstraint("oneShiftPerEmployeePerDay"),

                        // 2. Female cannot work night shift
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .filter(assignment -> "Female".equalsIgnoreCase(assignment.getGender()) &&
                                        "Night".equals(assignment.getShift()))
                                .penalizeLong(HardSoftLongScore.ONE_HARD)
                                .asConstraint("femaleCannotWorkNightShift"),

                        // 3. Rating requirements
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .filter(a -> {
                                    List<Integer> allowed = requiredRatingsPerRole.getOrDefault(
                                            a.getPosition(), List.of(1,2,3,4,5));
                                    return !allowed.contains(a.getPerformanceRating());
                                })
                                .penalizeLong(HardSoftLongScore.ONE_HARD)
                                .asConstraint("employeeRatingMatchesRoleRequirement"),

                        // 4. Max workers per role per day
                        factory.forEach(EmployeeAssignment.class)
                                .groupBy(
                                        EmployeeAssignment::getDate,
                                        EmployeeAssignment::getPosition,
                                        ConstraintCollectors.count())
                                .join(ShiftSchedule.RoleLimit.class,
                                        Joiners.equal((date, position, count) -> position,
                                                ShiftSchedule.RoleLimit::getRoleName))
                                .filter((date, position, count, roleLimit) -> count > roleLimit.getMaxWorkers())
                                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                                        (date, position, count, roleLimit) -> {
                                            int excess = count - roleLimit.getMaxWorkers();
                                            // Progressive penalty: higher excess = exponentially higher penalty
                                            return (long) (Math.pow(excess, 2) * 1000); // 1 extra = 1000, 2 extra = 4000, 3 extra = 9000
                                        })
                                .asConstraint("maxWorkersPerRolePerDay"),

                        // ============ SOFT CONSTRAINTS ============

                        // 5. Prefer higher rating
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                                        a -> switch(a.getPerformanceRating()) {
                                            case 5 -> 0L;
                                            case 4 -> 5L;
                                            case 3 -> 15L;
                                            case 2 -> 30L;
                                            case 1 -> 60L;
                                            default -> 90L;
                                        })
                                .asConstraint("preferHigherRating"),

                        // 6. Cost optimization (prefer lower wage)
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                                        a -> (long)(a.getHourlyWage() * 5))
                                .asConstraint("preferLowerWageEmployees"),

                        // 7. Shift consistency
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .groupBy(Scheduler.EmployeeAssignment::getEmployeeId, ConstraintCollectors.toList())
                                .penalizeLong(HardSoftLongScore.ONE_SOFT, (employeeId, list) -> {
                                    if (list.size() <= 1) return 0L;
                                    Map<String, Long> shiftCounts = list.stream()
                                            .filter(a -> a.getShift() != null)
                                            .collect(Collectors.groupingBy(
                                                    Scheduler.EmployeeAssignment::getShift, Collectors.counting()));
                                    long maxCount = shiftCounts.values().stream()
                                            .mapToLong(Long::longValue).max().orElse(0L);
                                    return (list.size() - maxCount) * 10L;
                                })
                                .asConstraint("preferShiftConsistency"),

                        // 8. Prefer permanent employees
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .filter(assignment -> !assignment.isPermanentEmployee())
                                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                                        assignment -> assignment.isPrioritizePermanent() ? 50L : 0L)
                                .asConstraint("prioritizePermanentEmployees"),

                        // 9. Balanced shift distribution
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .groupBy(
                                        Scheduler.EmployeeAssignment::getDate,
                                        Scheduler.EmployeeAssignment::getShift,
                                        ConstraintCollectors.count())
                                .penalizeLong(HardSoftLongScore.ONE_SOFT, (date, shift, count) -> {
                                    long target = switch(shift) {
                                        case "Morning" -> 30L;
                                        case "Afternoon" -> 30L;
                                        case "Night" -> 30L;
                                        default -> 0L;
                                    };
                                    return Math.abs(count - target) * 5L;
                                })
                                .asConstraint("balancedShiftDistributionPerDay"),

                        // 10. Avoid overlapping breaks
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .join(Scheduler.EmployeeAssignment.class,
                                        Joiners.equal(Scheduler.EmployeeAssignment::getDate),
                                        Joiners.filtering((a1, a2) -> {
                                            if (a1.getEmployeeId().equals(a2.getEmployeeId())) {
                                                return false;
                                            }
                                            ShiftApp.ShiftTime shiftTime1 = SHIFT_TIMES.get(a1.getShift());
                                            ShiftApp.ShiftTime shiftTime2 = SHIFT_TIMES.get(a2.getShift());
                                            if (shiftTime1 == null || shiftTime2 == null) return false;

                                            LocalTime breakStart1 = shiftTime1.getStartTime().plusHours(4);
                                            LocalTime breakEnd1 = breakStart1.plusMinutes(30);
                                            LocalTime breakStart2 = shiftTime2.getStartTime().plusHours(4);
                                            LocalTime breakEnd2 = breakStart2.plusMinutes(30);

                                            return !(breakEnd1.isBefore(breakStart2) || breakEnd2.isBefore(breakStart1));
                                        }))
                                .penalizeLong(HardSoftLongScore.ONE_SOFT)
                                .asConstraint("avoidOverlappingBreaks"),

                        // 11. Break scheduling constraint
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .filter(assignment -> assignment.getShift() != null &&
                                        SHIFT_TIMES.containsKey(assignment.getShift()))
                                .penalizeLong(HardSoftLongScore.ONE_SOFT, assignment -> {
                                    ShiftApp.ShiftTime shiftTime = SHIFT_TIMES.get(assignment.getShift());
                                    if (shiftTime == null) return 0L;

                                    if (!assignment.isHasScheduledBreak() || assignment.getBreakStartTime() == null) {
                                        return 100L;
                                    }

                                    LocalTime actualBreakStart = assignment.getBreakStartTime();
                                    LocalTime actualBreakEnd = assignment.getBreakEndTime();
                                    LocalTime expectedBreakStart = shiftTime.getStartTime().plusHours(4);

                                    long penalty = 0L;
                                    long breakDuration = Duration.between(actualBreakStart, actualBreakEnd).toMinutes();
                                    if (breakDuration != 30) {
                                        penalty += Math.abs(breakDuration - 30) * 5;
                                    }

                                    long hoursFromStart = Duration.between(shiftTime.getStartTime(), actualBreakStart).toHours();
                                    if (hoursFromStart < 4) {
                                        penalty += (4 - hoursFromStart) * 50;
                                    } else if (hoursFromStart > 4) {
                                        penalty += (hoursFromStart - 4) * 30;
                                    }

                                    return penalty;
                                })
                                .asConstraint("breakSchedulingConstraint"),

                        // 12. Max 8 hours per day (including OT)
                        factory.forEach(Scheduler.EmployeeAssignment.class)
                                .filter(assignment -> {
                                    double shiftHours = getShiftDuration(assignment.getShift());
                                    return shiftHours > 8;
                                })
                                .penalizeLong(HardSoftLongScore.ONE_HARD,
                                        assignment -> (long)((getShiftDuration(assignment.getShift()) - 8) * 60))
                                .asConstraint("maxEightHoursPerDay"),
                        // Add to your ShiftConstraints class - HARD constraint
                        factory.forEach(EmployeeAssignment.class)
                                .groupBy(EmployeeAssignment::getEmployeeId,
                                        ConstraintCollectors.toList())
                                .filter((employeeId, assignments) -> {
                                    // Sort by date
                                    assignments.sort(Comparator.comparing(EmployeeAssignment::getDate));

                                    // Check for 5+ consecutive days
                                    int consecutiveDays = 1;
                                    for (int i = 1; i < assignments.size(); i++) {
                                        LocalDate prevDate = LocalDate.parse(assignments.get(i-1).getDate());
                                        LocalDate currDate = LocalDate.parse(assignments.get(i).getDate());

                                        if (currDate.equals(prevDate.plusDays(1))) {
                                            consecutiveDays++;
                                            if (consecutiveDays >= 5) {
                                                return true; // Violation found
                                            }
                                        } else {
                                            consecutiveDays = 1; // Reset counter
                                        }
                                    }
                                    return false;
                                })
                                .penalizeLong(HardSoftLongScore.ONE_SOFT,
                                        (employeeId, assignments) -> {
                                            // Penalty increases with more violations
                                            return 1000L * (assignments.size() / 5);
                                        })
                                .asConstraint("maxConsecutiveWorkingDays")
                };
            }

            // Helper method to get shift duration
            private static double getShiftDuration(String shiftName) {
                ShiftApp.ShiftTime shiftTime = SHIFT_TIMES.get(shiftName);
                if (shiftTime == null) return 8.0; // Default

                LocalTime start = shiftTime.getStartTime();
                LocalTime end = shiftTime.getEndTime();

                if (end.isBefore(start)) {
                    // Night shift crossing midnight
                    long minutesToMidnight = Duration.between(start, LocalTime.MAX).toMinutes() + 1;
                    long minutesFromMidnight = Duration.between(LocalTime.MIN, end).toMinutes();
                    return (minutesToMidnight + minutesFromMidnight) / 60.0;
                } else {
                    // Regular shift
                    return Duration.between(start, end).toMinutes() / 60.0;
                }
            }
        }

        private static LocalDate safeParseDate(String dateStr) {
            try {
                return LocalDate.parse(dateStr);
            } catch (Exception e) {
                // Return a default date if parsing fails
                return LocalDate.of(2026, 1, 1);
            }
        }
        // If ShiftTime doesn't exist, create it
        static class ShiftTime {
            private final LocalTime startTime;
            private final LocalTime endTime;

            public ShiftTime(LocalTime startTime, LocalTime endTime) {
                this.startTime = startTime;
                this.endTime = endTime;
            }

            public LocalTime getStartTime() { return startTime; }
            public LocalTime getEndTime() { return endTime; }
        }

        // RESPONSE FOR BRYNTUM
        public static class ScheduleResponse {
            private List<Map<String, Object>> employees = new ArrayList<>();
            private List<Map<String, Object>> slots = new ArrayList<>();
            private List<Map<String, Object>> leaves = new ArrayList<>();
            private List<Map<String, Object>> otCoverages = new ArrayList<>();

            public ScheduleResponse() {
            }

            public ScheduleResponse(List<Map<String, Object>> employees, List<Map<String, Object>> slots,
                                    List<Map<String, Object>> leaves, List<Map<String, Object>> otCoverages) {
                this.employees = employees;
                this.slots = slots;
                this.leaves = leaves;
                this.otCoverages = otCoverages;
            }

            public List<Map<String, Object>> getEmployees() {
                if (employees == null) employees = new ArrayList<>();
                return employees;
            }

            public List<Map<String, Object>> getSlots() {
                if (slots == null) slots = new ArrayList<>();
                return slots;
            }

            public List<Map<String, Object>> getLeaves() {
                if (leaves == null) leaves = new ArrayList<>();
                return leaves;
            }

            public List<Map<String, Object>> getOtCoverages() {
                if (otCoverages == null) otCoverages = new ArrayList<>();
                return otCoverages;
            }
        }

        // NEW METHOD: Get existing schedule without regenerating
        public static ScheduleResponse getExistingSchedule() {
            System.out.println("📋 Returning existing schedule without regeneration");
            List<Employee> employees = createEmployees();
            List<Map<String, Object>> employeeAssignments = new ArrayList<>();
            List<Map<String, Object>> slotData = new ArrayList<>();
            List<Map<String, Object>> leaveEvents = new ArrayList<>();
            List<Map<String, Object>> otCoverageEvents = new ArrayList<>();
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            // Group existing shifts by date and shift
            Map<String, Map<String, List<String>>> shiftsByDateAndShift = new HashMap<>();
            for (int day = 0; day < 31; day++) {
                LocalDate date = startDate.plusDays(day);
                String dateStr = date.toString();
                if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    continue;
                }
                // Initialize the structure for this date
                shiftsByDateAndShift.put(dateStr, new HashMap<>());
                shiftsByDateAndShift.get(dateStr).put("Morning", new ArrayList<>());
                shiftsByDateAndShift.get(dateStr).put("Afternoon", new ArrayList<>());
                shiftsByDateAndShift.get(dateStr).put("Night", new ArrayList<>());
                // Determine if this is a Group A day (Mon/Wed/Fri)
                DayOfWeek dayOfWeek = date.getDayOfWeek();
                boolean isGroupADay = dayOfWeek == DayOfWeek.MONDAY ||
                        dayOfWeek == DayOfWeek.WEDNESDAY ||
                        dayOfWeek == DayOfWeek.FRIDAY;
                // Add employees to their respective shifts based on existing assignments and group rules
                for (Employee emp : employees) {
                    if (emp.isOnLeave(dateStr)) {
                        continue;
                    }
                    // Enforce group rules: Group A (group==0) only on Group A days, Group B (group==1) on non-A days (Tue/Thu)
                    boolean shouldWorkToday = (emp.getGroup() == 0 && isGroupADay) || (emp.getGroup() == 1 && !isGroupADay);
                    if (!shouldWorkToday) {
                        continue; // Skip if not their working day
                    }
                    String shift = null;
                    Map<String, List<String>> dayAssignments = shiftAssignments.get(dateStr);
                    if (dayAssignments != null) {
                        for (Map.Entry<String, List<String>> entry : dayAssignments.entrySet()) {
                            if (entry.getValue().contains(emp.getId())) {
                                shift = entry.getKey();  // Morning/Afternoon/Night
                                break;
                            }
                        }
                    }
                }
            }

            // Convert to Bryntum format
            for (int day = 0; day < 31; day++) {
                LocalDate date = startDate.plusDays(day);
                String dateStr = date.toString();

                List<Employee> employeesOnLeave = employees.stream()
                        .filter(emp -> emp.isOnLeave(dateStr))
                        .toList();

                for (Employee emp : employeesOnLeave) {
                    Map<String, Object> leaveEvent = new HashMap<>();
                    leaveEvent.put("id", "leave-" + emp.getId() + "-" + dateStr);
                    leaveEvent.put("employeeId", emp.getId());
                    leaveEvent.put("employeeName", emp.getName());
                    leaveEvent.put("date", dateStr);
                    leaveEvent.put("type", "leave");
                    leaveEvent.put("employeeColor", emp.getShiftColor());

                    leaveEvents.add(leaveEvent);
                }

                if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    for (String shift : Arrays.asList("Morning", "Afternoon", "Night")) {
                        Map<String, Object> slot = new HashMap<>();
                        slot.put("date", dateStr);
                        slot.put("name", shift);
                        slot.put("shiftColor", SHIFT_COLORS.get(shift));

                        List<Map<String, Object>> assignedEmployees = new ArrayList<>();

                        if (shiftsByDateAndShift.containsKey(dateStr) &&
                                shiftsByDateAndShift.get(dateStr).containsKey(shift)) {

                            assignedEmployees = shiftsByDateAndShift.get(dateStr).get(shift).stream()
                                    .map(employeeId -> {
                                        Employee emp = employees.stream()
                                                .filter(e -> e.getId().equals(employeeId))
                                                .findFirst()
                                                .orElse(null);
                                        if (emp != null) {
                                            EmployeeInfo empInfo = employeeInfo.get(employeeId);
                                            Map<String, Object> empMap = new HashMap<>();
                                            empMap.put("id", emp.getId());
                                            empMap.put("name", emp.getName());
                                            empMap.put("category", emp.getCategory());
                                            empMap.put("gender", emp.getGender());

                                            empMap.put("position", emp.getPosition());
                                            empMap.put("employeeColor", emp.getShiftColor());
                                            empMap.put("email", empInfo != null ? empInfo.getEmail() : "");
                                            empMap.put("phone", empInfo != null ? empInfo.getPhone() : "");
                                            return empMap;
                                        }
                                        return null;
                                    })
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());

                            Collections.shuffle(assignedEmployees);
                        }

                        slot.put("employees", assignedEmployees);
                        slotData.add(slot);

                        for (Map<String, Object> emp : assignedEmployees) {
                            Map<String, Object> assignmentMap = new HashMap<>();
                            assignmentMap.put("id", emp.get("id"));
                            assignmentMap.put("name", emp.get("name"));
                            assignmentMap.put("category", emp.get("category"));
                            assignmentMap.put("gender", emp.get("gender"));

                            assignmentMap.put("position", emp.get("position"));
                            assignmentMap.put("shift", shift);
                            assignmentMap.put("date", dateStr);
                            assignmentMap.put("leaveDates", new ArrayList<>());
                            assignmentMap.put("shiftColor", SHIFT_COLORS.get(shift));
                            assignmentMap.put("employeeColor", emp.get("employeeColor"));
                            employeeAssignments.add(assignmentMap);
                        }
                    }
                }
            }

            // NEW: Add OT Coverage events
            for (OTCoverageAssignment assignment : otCoverageAssignments.values()) {
                Map<String, Object> otEvent = new HashMap<>();
                otEvent.put("id", "ot-coverage-" + assignment.getId());
                otEvent.put("employeeId", assignment.getAssignedEmployeeId());
                otEvent.put("coveredEmployeeId", assignment.getCoveredEmployeeId());
                otEvent.put("date", assignment.getCoverageDate().toString());
                otEvent.put("hours", assignment.getAssignedHours());
                otEvent.put("shiftName", assignment.getShiftName());
                otEvent.put("assignedAt", assignment.getAssignedAt().toString());
                otEvent.put("status", assignment.getStatus());
                otEvent.put("type", "COVERAGE");
                otEvent.put("color", OT_COVERAGE_COLOR);

                EmployeeInfo assignedEmp = employeeInfo.get(assignment.getAssignedEmployeeId());
                EmployeeInfo coveredEmp = employeeInfo.get(assignment.getCoveredEmployeeId());

                if (assignedEmp != null && coveredEmp != null) {
                    otEvent.put("assignedEmployeeName", assignedEmp.getName());
                    otEvent.put("coveredEmployeeName", coveredEmp.getName());
                    otEvent.put("assignedEmployeeColor", assignedEmp.getShiftColor());
                    otEvent.put("coveredEmployeeColor", coveredEmp.getShiftColor());
                }

                otCoverageEvents.add(otEvent);
            }

            System.out.println("📊 Converted existing schedule to Bryntum format:");
            System.out.println("   - Assignments: " + employeeAssignments.size());
            System.out.println("   - Slots: " + slotData.size());
            System.out.println("   - Leave days: " + leaveEvents.size());
            System.out.println("   - OT Coverage assignments: " + otCoverageEvents.size());

            return new ScheduleResponse(employeeAssignments, slotData, leaveEvents, otCoverageEvents);
        }

        // GENERATE OPTIMIZED SCHEDULE - ENSURING PROPER ALTERNATE SCHEDULING

        public static ScheduleResponse generateOptimizedSchedule() {
            System.out.println("🔧 [OPTIMIZATION START] Generating optimized schedule...");
            long startTime = System.currentTimeMillis();

            // First, create employees with proper group assignment
            List<Employee> employees = createEmployees();
            System.out.println("📊 Created " + employees.size() + " employees");

            // Create initial assignments with STRICT group rules
            System.out.println("\n📅 Creating initial assignments with strict alternate day scheduling...");
            List<EmployeeAssignment> assignments = createInitialAssignmentsWithGroups(employees);

            System.out.println("\n📊 [INITIAL ASSIGNMENT SUMMARY]");
            System.out.println("Total assignments created: " + assignments.size());

            // Verify the initial assignment
            verifyInitialAssignment(assignments);

            // Now optimize with Timefold
            System.out.println("\n⚙️ [TIMEFOLD OPTIMIZATION START]");

            List<String> shiftTypes = Arrays.asList("Morning", "Afternoon", "Night");

            // FASTER SOLVER CONFIG
            SolverConfig solverConfig = new SolverConfig()
                    .withSolutionClass(ShiftSchedule.class)
                    .withEntityClasses(EmployeeAssignment.class)
                    .withConstraintProviderClass(ShiftConstraints.class)
                    .withTerminationSpentLimit(Duration.ofSeconds(30));

            SolverFactory<ShiftSchedule> solverFactory = SolverFactory.create(solverConfig);
            Solver<ShiftSchedule> solver = solverFactory.buildSolver();

            ShiftSchedule problem = new ShiftSchedule(assignments, shiftTypes);
            System.out.println("Starting solver with " + assignments.size() + " assignments...");

            ShiftSchedule solution = solver.solve(problem);
            long endTime = System.currentTimeMillis();

            System.out.println("✅ [OPTIMIZATION COMPLETE] Time: " + ((endTime - startTime)/1000.0) + "s");
            System.out.println("Final score: " + solution.getScore());

            // Validate the solution
            validateFinalSolution(solution.getAssignments());

            // Convert to frontend format and return
            // Note: We no longer store in employeeShifts
            return convertToBryntumFormatWithLeaves(solution.getAssignments(), employees);
        }
        private static void validateFinalSolution(List<EmployeeAssignment> assignments) {
            System.out.println("\n🔍 [FINAL SOLUTION VALIDATION]");

            Map<String, List<EmployeeAssignment>> assignmentsByEmployee = new HashMap<>();

            for (EmployeeAssignment assignment : assignments) {
                assignmentsByEmployee.computeIfAbsent(assignment.getEmployeeId(), k -> new ArrayList<>())
                        .add(assignment);
            }

            int consecutiveDayViolations = 0;
            int femaleNightShiftViolations = 0;
            int groupViolations = 0;

            for (Map.Entry<String, List<EmployeeAssignment>> entry : assignmentsByEmployee.entrySet()) {
                String employeeId = entry.getKey();
                List<EmployeeAssignment> empAssignments = entry.getValue();

                // Sort by date
                empAssignments.sort(Comparator.comparing(a -> LocalDate.parse(a.getDate())));

                // Check consecutive days
                for (int i = 1; i < empAssignments.size(); i++) {
                    LocalDate date1 = LocalDate.parse(empAssignments.get(i-1).getDate());
                    LocalDate date2 = LocalDate.parse(empAssignments.get(i).getDate());

                    if (date2.equals(date1.plusDays(1))) {
                        consecutiveDayViolations++;
                        System.out.println("❌ " + empAssignments.get(i).getEmployeeName() +
                                " works consecutive days: " + date1 + " → " + date2);
                    }
                }

                // Check female night shift violations
                EmployeeAssignment firstAssignment = empAssignments.get(0);
                if ("Female".equalsIgnoreCase(firstAssignment.getGender())) {
                    for (EmployeeAssignment assignment : empAssignments) {
                        if ("Night".equals(assignment.getShift())) {
                            femaleNightShiftViolations++;
                            System.out.println("❌ " + assignment.getEmployeeName() +
                                    " (Female) works Night shift on " + assignment.getDate());
                        }
                    }
                }

                // Check group violations
                String idNum = employeeId.substring(1);
                int employeeNum = Integer.parseInt(idNum);
                boolean isGroupA = employeeNum % 2 == 1;

                for (EmployeeAssignment assignment : empAssignments) {
                    LocalDate date = LocalDate.parse(assignment.getDate());
                    DayOfWeek day = date.getDayOfWeek();

                    if (isGroupA && (day == DayOfWeek.TUESDAY || day == DayOfWeek.THURSDAY)) {
                        groupViolations++;
                        System.out.println("❌ " + assignment.getEmployeeName() +
                                " (Group A) works on " + day + " " + date);
                    } else if (!isGroupA && (day == DayOfWeek.MONDAY ||
                            day == DayOfWeek.WEDNESDAY || day == DayOfWeek.FRIDAY)) {
                        groupViolations++;
                        System.out.println("❌ " + assignment.getEmployeeName() +
                                " (Group B) works on " + day + " " + date);
                    }
                }
            }

            System.out.println("\n📊 [VIOLATION SUMMARY]");
            System.out.println("Consecutive day violations: " + consecutiveDayViolations);
            System.out.println("Female night shift violations: " + femaleNightShiftViolations);
            System.out.println("Group violations: " + groupViolations);

            if (consecutiveDayViolations == 0 && femaleNightShiftViolations == 0 && groupViolations == 0) {
                System.out.println("✅ PERFECT SOLUTION - All constraints satisfied!");
            } else {
                System.out.println("⚠️ Solution has " +
                        (consecutiveDayViolations + femaleNightShiftViolations + groupViolations) +
                        " constraint violations");
            }
        }
        // Create initial assignments with strict group rules
        private static List<EmployeeAssignment> createInitialAssignmentsWithGroups(List<Employee> employees) {
            List<EmployeeAssignment> assignments = new ArrayList<>();
            LocalDate startDate = LocalDate.of(2026, 1, 1);

            // Track employee assignments
            Map<String, LocalDate> lastAssignmentDate = new HashMap<>();
            Map<String, String> employeeShiftsMap = new HashMap<>(); // Track each employee's shift

            // Process each day
            for (int day = 0; day < 31; day++) {
                LocalDate date = startDate.plusDays(day);

                // Skip weekends
                if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    continue;
                }

                String dateStr = date.toString();
                DayOfWeek dayOfWeek = date.getDayOfWeek();

                // Determine which group works today
                boolean isGroupADay = dayOfWeek == DayOfWeek.MONDAY ||
                        dayOfWeek == DayOfWeek.WEDNESDAY ||
                        dayOfWeek == DayOfWeek.FRIDAY;
                boolean isGroupBDay = dayOfWeek == DayOfWeek.TUESDAY ||
                        dayOfWeek == DayOfWeek.THURSDAY;

                // Track shift counts for this day
                int morningCount = 0, afternoonCount = 0, nightCount = 0;
                List<EmployeeAssignment> dayAssignments = new ArrayList<>();

                // Process each employee
                for (Employee emp : employees) {
                    // Get employee number
                    String idNum = emp.getId().substring(1);
                    int employeeNum = Integer.parseInt(idNum);
                    boolean isGroupA = employeeNum % 2 == 1;

                    // Check if employee should work today
                    boolean shouldWorkToday = (isGroupA && isGroupADay) || (!isGroupA && isGroupBDay);

                    if (!shouldWorkToday) {
                        continue; // Wrong group for today
                    }

                    // Check if on leave
                    if (employeeLeaves.containsKey(emp.getId()) &&
                            employeeLeaves.get(emp.getId()).contains(dateStr)) {
                        continue; // On leave
                    }

                    // Check for consecutive days
                    LocalDate lastDate = lastAssignmentDate.get(emp.getId());
                    if (date.minusDays(1).equals(lastDate)) {
                        continue; // Would be consecutive day
                    }

                    // Determine shift for this employee
                    String shift = determineEmployeeShift(emp, employeeShiftsMap,
                            morningCount, afternoonCount, nightCount);

                    // Create assignment
                    EmployeeAssignment assignment = createEmployeeAssignment(
                            emp, dateStr, isGroupA, shift
                    );

                    dayAssignments.add(assignment);

                    // Update counters
                    switch (shift) {
                        case "Morning": morningCount++; break;
                        case "Afternoon": afternoonCount++; break;
                        case "Night": nightCount++; break;
                    }

                    // Update tracking
                    lastAssignmentDate.put(emp.getId(), date);
                    employeeShiftsMap.put(emp.getId(), shift);
                }

                // Add day assignments to main list
                assignments.addAll(dayAssignments);

                // Log daily summary
                System.out.println(String.format("  %s (%s): %d employees [M:%d, A:%d, N:%d]",
                        dateStr, dayOfWeek, dayAssignments.size(),
                        morningCount, afternoonCount, nightCount));
            }

            return assignments;
        }

        // Determine employee's shift
        private static String determineEmployeeShift(Employee emp,
                                                     Map<String, String> employeeShiftsMap,
                                                     int morningCount, int afternoonCount, int nightCount) {

            String employeeId = emp.getId();

            // Try to use existing shift if employee already has one
            String existingShift = employeeShiftsMap.get(employeeId);
            if (existingShift != null) {
                // Check if this shift still has capacity
                if (canAssignShift(existingShift, morningCount, afternoonCount, nightCount)) {
                    return existingShift;
                }
            }

            // Determine based on position and gender
            String preferredShift = getPreferredShiftForPosition(emp.getPosition(), emp.getGender());

            // Check capacity for preferred shift
            if (canAssignShift(preferredShift, morningCount, afternoonCount, nightCount)) {
                return preferredShift;
            }

            // Find alternative shift with capacity
            if (canAssignShift("Morning", morningCount, afternoonCount, nightCount)) {
                return "Morning";
            } else if (canAssignShift("Afternoon", morningCount, afternoonCount, nightCount)) {
                return "Afternoon";
            } else if (canAssignShift("Night", morningCount, afternoonCount, nightCount)) {
                return "Night";
            }

            // Default
            return "Morning";
        }

        // Check if shift can be assigned (not at capacity)
        private static boolean canAssignShift(String shift, int morningCount, int afternoonCount, int nightCount) {
            return switch (shift) {
                case "Morning" -> morningCount < 6;  // Max 6
                case "Afternoon" -> afternoonCount < 7;  // Max 7
                case "Night" -> nightCount < 11;  // Max 11
                default -> false;
            };
        }

        // Get preferred shift based on position
        private static String getPreferredShiftForPosition(String position, String gender) {
            if (position == null) return "Morning";

            // Females cannot work night shift
            if ("Female".equalsIgnoreCase(gender)) {
                if (position.contains("Manager") || position.contains("Product")) {
                    return "Afternoon";
                }
                return "Morning";
            }

            // Position-based preferences
            if (position.contains("Cloud") || position.contains("DevOps") ||
                    position.contains("Support") || position.contains("Help Desk")) {
                return "Night";
            } else if (position.contains("Manager") || position.contains("Product")) {
                return "Afternoon";
            } else {
                return "Morning";
            }
        }

        // Create employee assignment
        private static EmployeeAssignment createEmployeeAssignment(Employee emp, String dateStr,
                                                                   boolean isGroupA, String shift) {
            String assignmentId = emp.getId() + "-" + dateStr;
            EmployeeAssignment assignment = new EmployeeAssignment(
                    assignmentId,
                    emp.getId(),
                    emp.getName(),
                    dateStr,
                    emp.getCategory(),
                    emp.getGender(),
                    emp.getDepartment(),
                    emp.getPosition()
            );

            assignment.setGroup(isGroupA ? 0 : 1);
            assignment.setShift(shift);

            // Copy skills
            EmployeeInfo empInfo = employeeInfo.get(emp.getId());
            if (empInfo != null) {
                assignment.setSkills(new HashSet<>(empInfo.getSkills()));
                assignment.setShiftColor(empInfo.getShiftColor());
            }

            return assignment;
        }

        // Verify initial assignment
        private static void verifyInitialAssignment(List<EmployeeAssignment> assignments) {
            Map<String, List<EmployeeAssignment>> byEmployee = new HashMap<>();

            for (EmployeeAssignment assignment : assignments) {
                byEmployee.computeIfAbsent(assignment.getEmployeeId(), k -> new ArrayList<>())
                        .add(assignment);
            }

            int consecutiveIssues = 0;
            int groupIssues = 0;
            int femaleNightIssues = 0;

            for (Map.Entry<String, List<EmployeeAssignment>> entry : byEmployee.entrySet()) {
                List<EmployeeAssignment> empAssignments = entry.getValue();
                empAssignments.sort(Comparator.comparing(EmployeeAssignment::getDate));

                // Check consecutive days
                for (int i = 1; i < empAssignments.size(); i++) {
                    LocalDate date1 = LocalDate.parse(empAssignments.get(i-1).getDate());
                    LocalDate date2 = LocalDate.parse(empAssignments.get(i).getDate());

                    if (date2.equals(date1.plusDays(1))) {
                        consecutiveIssues++;
                        break;
                    }
                }

                // Check group and other issues
                for (EmployeeAssignment assignment : empAssignments) {
                    // Group check
                    String idNum = assignment.getEmployeeId().substring(1);
                    int employeeNum = Integer.parseInt(idNum);
                    boolean isGroupA = employeeNum % 2 == 1;
                    LocalDate date = LocalDate.parse(assignment.getDate());
                    DayOfWeek day = date.getDayOfWeek();

                    if (isGroupA && (day == DayOfWeek.TUESDAY || day == DayOfWeek.THURSDAY)) {
                        groupIssues++;
                    } else if (!isGroupA && (day == DayOfWeek.MONDAY || day == DayOfWeek.WEDNESDAY || day == DayOfWeek.FRIDAY)) {
                        groupIssues++;
                    }

                    // Female night shift check
                    if ("Female".equalsIgnoreCase(assignment.getGender()) &&
                            "Night".equals(assignment.getShift())) {
                        femaleNightIssues++;
                    }
                }
            }

            System.out.println("  Initial verification:");
            System.out.println("    Consecutive day issues: " + consecutiveIssues);
            System.out.println("    Group assignment issues: " + groupIssues);
            System.out.println("    Female night shift issues: " + femaleNightIssues);

            if (consecutiveIssues == 0 && groupIssues == 0 && femaleNightIssues == 0) {
                System.out.println("    ✅ Perfect initial assignment!");
            } else {
                System.out.println("    ⚠️ Some issues in initial assignment");
            }
        }

        // Analyze and fix solution
        private static List<EmployeeAssignment> analyzeAndFixSolution(List<EmployeeAssignment> assignments) {
            System.out.println("\n🔍 [SOLUTION ANALYSIS]");

            Map<String, List<EmployeeAssignment>> byEmployee = new HashMap<>();

            for (EmployeeAssignment assignment : assignments) {
                byEmployee.computeIfAbsent(assignment.getEmployeeId(), k -> new ArrayList<>())
                        .add(assignment);
            }

            List<EmployeeAssignment> fixedAssignments = new ArrayList<>(assignments);
            int fixesApplied = 0;

            // Fix any remaining issues
            for (Map.Entry<String, List<EmployeeAssignment>> entry : byEmployee.entrySet()) {
                List<EmployeeAssignment> empAssignments = entry.getValue();
                if (empAssignments.size() <= 1) continue;

                empAssignments.sort(Comparator.comparing(EmployeeAssignment::getDate));

                // Fix consecutive days
                for (int i = 1; i < empAssignments.size(); i++) {
                    LocalDate date1 = LocalDate.parse(empAssignments.get(i-1).getDate());
                    LocalDate date2 = LocalDate.parse(empAssignments.get(i).getDate());

                    if (date2.equals(date1.plusDays(1))) {
                        // Remove the later assignment
                        fixedAssignments.remove(empAssignments.get(i));
                        fixesApplied++;
                        System.out.println("  🔧 Fixed consecutive days for " + empAssignments.get(i).getEmployeeName());
                        break;
                    }
                }
            }

            System.out.println("  Total fixes applied: " + fixesApplied);
            return fixedAssignments;
        }

        // Print shift consistency report
        private static void printShiftConsistencyReport(List<EmployeeAssignment> assignments) {
            System.out.println("\n📊 [SHIFT CONSISTENCY REPORT]");

            Map<String, List<EmployeeAssignment>> byEmployee = new HashMap<>();

            for (EmployeeAssignment assignment : assignments) {
                byEmployee.computeIfAbsent(assignment.getEmployeeId(), k -> new ArrayList<>())
                        .add(assignment);
            }

            int consistentEmployees = 0;
            int inconsistentEmployees = 0;

            for (Map.Entry<String, List<EmployeeAssignment>> entry : byEmployee.entrySet()) {
                List<EmployeeAssignment> empAssignments = entry.getValue();

                if (empAssignments.size() <= 1) {
                    consistentEmployees++;
                    continue;
                }

                // Check if all shifts are the same
                String firstShift = empAssignments.get(0).getShift();
                boolean consistent = empAssignments.stream()
                        .allMatch(a -> a.getShift().equals(firstShift));

                if (consistent) {
                    consistentEmployees++;
                } else {
                    inconsistentEmployees++;
                    System.out.println("  ⚠️ " + empAssignments.get(0).getEmployeeName() +
                            " has inconsistent shifts: " +
                            empAssignments.stream()
                                    .map(EmployeeAssignment::getShift)
                                    .distinct()
                                    .collect(Collectors.joining(", ")));
                }
            }

            System.out.println("\n  Consistent employees: " + consistentEmployees);
            System.out.println("  Inconsistent employees: " + inconsistentEmployees);
            System.out.println("  Consistency rate: " +
                    String.format("%.1f%%", (consistentEmployees * 100.0 / byEmployee.size())));
        }




        // Helper method to determine initial shift
        private static String determineInitialShift(Employee emp, Random rand) {
            String position = emp.getPosition();
            String gender = emp.getGender();

            // Position-based assignment
            if (position.contains("Cloud") || position.contains("DevOps") ||
                    position.contains("Help Desk") || position.contains("Delivery") ||
                    position.contains("Support")) {
                if ("Female".equalsIgnoreCase(gender)) {
                    // Females cannot work night shift
                    return rand.nextBoolean() ? "Morning" : "Afternoon";
                }
                return "Night";
            } else if (position.contains("Product") || position.contains("Manager")) {
                return "Afternoon";
            } else {
                return "Morning";
            }
        }

        // Helper method to analyze the solution
        private static void analyzeSolution(ShiftSchedule solution) {
            Map<String, List<EmployeeAssignment>> assignmentsByEmployee = new HashMap<>();
            Map<String, Integer> consecutiveDaysByEmployee = new HashMap<>();

            for (EmployeeAssignment assignment : solution.getAssignments()) {
                assignmentsByEmployee.computeIfAbsent(assignment.getEmployeeId(), k -> new ArrayList<>())
                        .add(assignment);
            }

            // Check for consecutive days
            System.out.println("\n🔍 [SOLUTION ANALYSIS]");
            int employeesWithConsecutiveDays = 0;

            for (Map.Entry<String, List<EmployeeAssignment>> entry : assignmentsByEmployee.entrySet()) {
                List<EmployeeAssignment> empAssignments = entry.getValue();

                // Sort by date
                empAssignments.sort(Comparator.comparing(EmployeeAssignment::getDate));

                // Check for consecutive days
                for (int i = 1; i < empAssignments.size(); i++) {
                    LocalDate date1 = LocalDate.parse(empAssignments.get(i-1).getDate());
                    LocalDate date2 = LocalDate.parse(empAssignments.get(i).getDate());

                    if (date2.equals(date1.plusDays(1))) {
                        employeesWithConsecutiveDays++;
                        System.out.println("  ⚠️ " + empAssignments.get(i).getEmployeeName() +
                                " works consecutive days: " + date1 + " and " + date2);
                        break;
                    }
                }
            }

            if (employeesWithConsecutiveDays == 0) {
                System.out.println("  ✅ No employees work consecutive days!");
            } else {
                System.out.println("  ⚠️ " + employeesWithConsecutiveDays + " employees work consecutive days");
            }
        }

        // CONVERT TIMEFOLD SOLUTION TO BRYNTUM FORMAT WITH LEAVES
        private static ScheduleResponse convertToBryntumFormatWithLeaves(List<EmployeeAssignment> assignments, List<Employee> employees) {
            List<Map<String, Object>> employeeAssignments = new ArrayList<>();
            List<Map<String, Object>> slotData = new ArrayList<>();
            List<Map<String, Object>> leaveEvents = new ArrayList<>();
            List<Map<String, Object>> otCoverageEvents = new ArrayList<>();

            LocalDate startDate = LocalDate.of(2026, 1, 1);

            Map<String, Map<String, List<EmployeeAssignment>>> assignmentsByDateAndShift = new HashMap<>();

            for (EmployeeAssignment assignment : assignments) {
                // Check if employee is on leave on this assignment date
                boolean isOnLeave = employeeLeaves.containsKey(assignment.getEmployeeId()) &&
                        employeeLeaves.get(assignment.getEmployeeId()).contains(assignment.getDate());

                if (isOnLeave) {
                    System.out.println("⛔ REMOVING SHIFT - Employee " + assignment.getEmployeeName() +
                            " is on leave on " + assignment.getDate());
                    continue; // Skip this assignment if employee is on leave
                }

                assignmentsByDateAndShift
                        .computeIfAbsent(assignment.getDate(), k -> new HashMap<>())
                        .computeIfAbsent(assignment.getShift(), k -> new ArrayList<>())
                        .add(assignment);
            }

            for (int day = 0; day < 31; day++) {
                LocalDate date = startDate.plusDays(day);
                String dateStr = date.toString();

                List<Employee> employeesOnLeave = employees.stream()
                        .filter(emp -> emp.isOnLeave(dateStr))
                        .toList();

                for (Employee emp : employeesOnLeave) {
                    Map<String, Object> leaveEvent = new HashMap<>();
                    leaveEvent.put("id", "leave-" + emp.getId() + "-" + dateStr);
                    leaveEvent.put("employeeId", emp.getId());
                    leaveEvent.put("employeeName", emp.getName());
                    leaveEvent.put("date", dateStr);
                    leaveEvent.put("type", "leave");
                    leaveEvent.put("employeeColor", emp.getShiftColor());

                    leaveEvents.add(leaveEvent);
                }

                if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    for (String shift : Arrays.asList("Morning", "Afternoon", "Night")) {
                        Map<String, Object> slot = new HashMap<>();
                        slot.put("date", dateStr);
                        slot.put("name", shift);
                        slot.put("shiftColor", SHIFT_COLORS.get(shift));

                        List<Map<String, Object>> assignedEmployees = new ArrayList<>();

                        if (assignmentsByDateAndShift.containsKey(dateStr) &&
                                assignmentsByDateAndShift.get(dateStr).containsKey(shift)) {

                            // Filter out employees who are on leave
                            List<EmployeeAssignment> validAssignments = assignmentsByDateAndShift.get(dateStr).get(shift).stream()
                                    .filter(assignment -> {
                                        boolean onLeave = employeeLeaves.containsKey(assignment.getEmployeeId()) &&
                                                employeeLeaves.get(assignment.getEmployeeId()).contains(dateStr);
                                        return !onLeave;
                                    })
                                    .toList();

                            assignedEmployees = validAssignments.stream()
                                    .map(assignment -> {
                                        EmployeeInfo empInfo = employeeInfo.get(assignment.getEmployeeId());
                                        Map<String, Object> empMap = new HashMap<>();
                                        empMap.put("id", assignment.getEmployeeId());
                                        empMap.put("name", assignment.getEmployeeName());
                                        empMap.put("category", assignment.getCategory());
                                        empMap.put("gender", assignment.getGender());

                                        empMap.put("position", assignment.getPosition());
                                        empMap.put("employeeColor", assignment.getShiftColor());
                                        empMap.put("email", empInfo != null ? empInfo.getEmail() : "");
                                        empMap.put("phone", empInfo != null ? empInfo.getPhone() : "");
                                        empMap.put("isOnLeave", false);
                                        empMap.put("hourlyWage", empInfo != null ? empInfo.getHourlyWage() : 0.0);
                                        empMap.put("performanceRating", empInfo.getPerformanceRating());
                                        return empMap;
                                    })
                                    .collect(Collectors.toList());

                            Collections.shuffle(assignedEmployees);
                        }

                        slot.put("employees", assignedEmployees);
                        slotData.add(slot);

                        for (Map<String, Object> emp : assignedEmployees) {
                            Map<String, Object> assignmentMap = new HashMap<>();
                            assignmentMap.put("id", emp.get("id"));
                            assignmentMap.put("name", emp.get("name"));
                            assignmentMap.put("category", emp.get("category"));
                            assignmentMap.put("gender", emp.get("gender"));

                            assignmentMap.put("position", emp.get("position"));
                            assignmentMap.put("shift", shift);
                            assignmentMap.put("date", dateStr);
                            assignmentMap.put("leaveDates", new ArrayList<>());
                            assignmentMap.put("shiftColor", SHIFT_COLORS.get(shift));
                            assignmentMap.put("employeeColor", emp.get("employeeColor"));
                            assignmentMap.put("isOnLeave", false);
                            employeeAssignments.add(assignmentMap);
                        }
                    }
                }
            }

            // NEW: Add OT Coverage events
            for (OTCoverageAssignment assignment : otCoverageAssignments.values()) {
                Map<String, Object> otEvent = new HashMap<>();
                otEvent.put("id", "ot-coverage-" + assignment.getId());
                otEvent.put("employeeId", assignment.getAssignedEmployeeId());
                otEvent.put("coveredEmployeeId", assignment.getCoveredEmployeeId());
                otEvent.put("date", assignment.getCoverageDate().toString());
                otEvent.put("hours", assignment.getAssignedHours());
                otEvent.put("shiftName", assignment.getShiftName());
                otEvent.put("assignedAt", assignment.getAssignedAt().toString());
                otEvent.put("status", assignment.getStatus());
                otEvent.put("type", "COVERAGE");
                otEvent.put("color", OT_COVERAGE_COLOR);

                EmployeeInfo assignedEmp = employeeInfo.get(assignment.getAssignedEmployeeId());
                EmployeeInfo coveredEmp = employeeInfo.get(assignment.getCoveredEmployeeId());

                if (assignedEmp != null && coveredEmp != null) {
                    otEvent.put("assignedEmployeeName", assignedEmp.getName());
                    otEvent.put("coveredEmployeeName", coveredEmp.getName());
                    otEvent.put("assignedEmployeeColor", assignedEmp.getShiftColor());
                    otEvent.put("coveredEmployeeColor", coveredEmp.getShiftColor());
                }

                otCoverageEvents.add(otEvent);
            }

            System.out.println("📊 Converted to Bryntum format:");
            System.out.println("   - Assignments: " + employeeAssignments.size());
            System.out.println("   - Slots: " + slotData.size());
            System.out.println("   - Leave days: " + leaveEvents.size());
            System.out.println("   - OT Coverage assignments: " + otCoverageEvents.size());

            return new ScheduleResponse(employeeAssignments, slotData, leaveEvents, otCoverageEvents);
        }

        private static boolean employeesInitialized = false;



        private static List<Employee> createEmployees() {
            // If employees already exist in storage, reconstruct them with correct groups and leaves
            if (!employeeInfo.isEmpty()) {
                System.out.println("✅ Reusing existing employees from employeeInfo");
                List<Employee> existingEmployees = new ArrayList<>();
                for (EmployeeInfo empInfo : employeeInfo.values()) {
                    Employee emp = new Employee(
                            empInfo.getId(),
                            empInfo.getName(),
                            empInfo.getGender(),
                            empInfo.getCategory(),
                            empInfo.getManagerId(),
                            empInfo.getDepartment(),
                            empInfo.getPosition()
                    );

                    // Set group based on employee ID
                    int employeeNum = Integer.parseInt(empInfo.getId().substring(1));
                    boolean isGroupA = employeeNum % 2 == 1;
                    emp.setGroup(isGroupA ? 0 : 1);

                    existingEmployees.add(emp);
                }
                return existingEmployees;
            }

            System.out.println("🔄 Creating 50 new employees with proper alternate day group assignment...");
            System.out.println("   → Group A (odd IDs: E001, E003, ...) → Works Mon, Wed, Fri");
            System.out.println("   → Group B (even IDs: E002, E004, ...) → Works Tue, Thu");

            List<Employee> employees = new ArrayList<>();
            Random rand = new Random(50); // Fixed seed for reproducibility

            // Position pools (unchanged from your original)
            String[] softwareDevelopers = {"Software Engineer", "Senior Developer", "Full Stack Developer", "Java Developer",
                    "Backend Developer", "Frontend Developer"};
            String[] productManagers = {"Product Manager", "Product Owner"};
            String[] qaManagers = {"QA Manager", "Test Lead", "QA Lead", "Quality Assurance Manager"};
            String[] technicalSupport = {"Technical Support", "Support Engineer", "IT Support", "Technical Support Engineer"};
            String[] deliveryManagers = {"Delivery Manager", "Project Manager", "Program Manager", "Release Manager"};
            String[] cloudEngineers = {"Cloud Engineer", "DevOps Engineer", "Cloud Architect", "Site Reliability Engineer"};
            String[] helpDesk = {"Help Desk", "Customer Success", "Service Desk", "IT Help Desk"};
            String[] scrumMasters = {"Scrum Master", "Agile Coach", "Agile Project Manager"};
            String[] testEngineers = {"Test Engineer", "QA Engineer", "Automation Engineer", "Manual Tester"};

            int employeeCounter = 1;

            // Create 50 employees (25 per group effectively via odd/even)
            for (int groupIndex = 0; groupIndex < 2; groupIndex++) {
                for (int i = 0; i < 50; i++) {
                    boolean isMale = (groupIndex == 0); // First 25 male, next 25 female (helps balance)
                    String gender = isMale ? "Male" : "Female";

                    String[] namePair = isMale
                            ? INDIAN_MALE_NAMES[(i) % INDIAN_MALE_NAMES.length]
                            : INDIAN_FEMALE_NAMES[(groupIndex * 25 + i) % INDIAN_FEMALE_NAMES.length];
                    String fullName = namePair[0] + " " + namePair[1];

                    String employeeId = "E" + String.format("%03d", employeeCounter);

                    // Determine position, department, category, manager
                    String position, department, category, managerId;

                    // Use index to distribute roles
                    int idx = groupIndex * 50 + i;

                    if (idx < 13) { // Morning/Afternoon preference roles
                        if (i % 2 == 0) {
                            position = softwareDevelopers[i % softwareDevelopers.length];
                            department = "Development";
                            managerId = "MGR001";
                        } else {
                            position = testEngineers[i % testEngineers.length];
                            department = "Quality Assurance";
                            managerId = "MGR003";
                        }
                        category = "Regular";
                    } else { // Night-capable roles (mostly male)
                        if (isMale) {
                            if (i < 15) {
                                position = deliveryManagers[i % deliveryManagers.length];
                                department = "Project Management";
                                managerId = "MGR005";
                                category = "Manager";
                            } else if (i < 20) {
                                position = scrumMasters[i % scrumMasters.length];
                                department = "Project Management";
                                managerId = "MGR005";
                                category = "Manager";
                            } else {
                                position = productManagers[i % productManagers.length];
                                department = "Product Management";
                                managerId = "MGR002";
                                category = "Manager";
                            }
                        } else {
                            // Females get day-shift roles
                            position = testEngineers[i % testEngineers.length];
                            department = "Quality Assurance";
                            managerId = "MGR003";
                            category = "Regular";
                        }
                    }

                    // === CREATE EMPLOYEE INSTANCE (LOCAL VARIABLE - NO STATIC) ===
                    Employee emp = new Employee(employeeId, fullName, gender, category, managerId, department, position);

                    // === CORRECT GROUP ASSIGNMENT: Odd ID → Group A (0), Even → Group B (1) ===
                    int employeeNum = Integer.parseInt(employeeId.substring(1));
                    boolean isGroupA = employeeNum % 2 == 1;
                    emp.setGroup(isGroupA ? 0 : 1);

                    // Email & Phone
                    String[] nameParts = fullName.split(" ");
                    String firstName = nameParts[0].toLowerCase();
                    String lastName = nameParts.length > 1 ? nameParts[1].toLowerCase() : "";
                    String email = firstName + "." + lastName + "@techcompany.in";
                    String phone = "+91 9" + String.format("%09d", employeeCounter * 1234567);

                    // Hourly wage
                    double hourlyWage = calculateWage(position, category, employeeCounter);

                    // Create EmployeeInfo
                    EmployeeInfo empInfo = new EmployeeInfo(
                            employeeId, fullName, category, gender, hourlyWage,
                            managerId, department, position, email, phone
                    );

                    // Add skills
                    addEmployeeSkills(empInfo, position, department, employeeCounter);

                    // Night shift certification for eligible males
                    if (isMale && (position.contains("Cloud") || position.contains("DevOps") ||
                            position.contains("Support") || position.contains("Delivery") ||
                            position.contains("Scrum"))) {
                        empInfo.addSkill("NightShiftCertified");
                    }

                    // Day shift skills for females
                    if (!isMale) {
                        empInfo.addSkill("DayShiftTrained");
                        empInfo.addSkill("Communication");
                    }
                    int performanceRating;

                    if (category.equals("Manager")) {
                        performanceRating = rand.nextInt(3) + 3; // 3–5 stars
                    } else if (position.contains("Senior") || position.contains("Lead")) {
                        performanceRating = rand.nextInt(4) + 2; // 2–5 stars
                    } else if (position.contains("Support") || position.contains("Help Desk")) {
                        performanceRating = rand.nextInt(3) + 1; // 1–3 stars
                    } else {
                        performanceRating = rand.nextInt(5) + 1; // 1–5 balanced
                    }

                    if (hourlyWage > 50 && rand.nextDouble() < 0.7) {
                        performanceRating = Math.min(5, performanceRating + 1);
                    }

                    empInfo.setPerformanceRating(performanceRating);  // ← THIS LINE HERE
                    // Store in global maps
                    employeeInfo.put(employeeId, empInfo);
                    employees.add(emp);
                    employeeCounter++;
                }
            }

            employeesInitialized = true;

            // Final summary and group verification
            System.out.println("\n✅ Successfully created " + employees.size() + " employees");
            System.out.println("   → Group A (Mon/Wed/Fri): " +
                    employees.stream().filter(e -> e.getGroup() == 0).count() + " employees");
            System.out.println("   → Group B (Tue/Thu):     " +
                    employees.stream().filter(e -> e.getGroup() == 1).count() + " employees");
            System.out.println("\n👥 [GROUP VERIFICATION]");

            for (Employee emp : employees) {
                int empNum = Integer.parseInt(emp.getId().substring(1));
                boolean expectedGroupA = empNum % 2 == 1;
                String status = emp.getGroup() == (expectedGroupA ? 0 : 1) ? "correct" : "INCORRECT!";
                System.out.println(" " + emp.getName() + " (" + emp.getId() + ") → Group " +
                        (emp.getGroup() == 0 ? "A" : "B") + " [" + status + "]");
            }

            return employees;
        }

        // HELPER METHOD: Categorize positions
        private static String getPositionCategory(String position) {
            if (position.contains("Developer") || position.contains("Engineer") && !position.contains("QA")) {
                return "Software Developer";
            } else if (position.contains("Product Manager") || position.contains("Product Owner")) {
                return "Product Manager";
            } else if (position.contains("QA") || position.contains("Test")) {
                return "QA/Test";
            } else if (position.contains("Support") || position.contains("Help Desk")) {
                return "Technical Support";
            } else if (position.contains("Delivery Manager") || position.contains("Project Manager")) {
                return "Delivery Manager";
            } else if (position.contains("Cloud") || position.contains("DevOps")) {
                return "Cloud Engineer";
            } else if (position.contains("Scrum Master") || position.contains("Agile Coach")) {
                return "Scrum Master";
            } else if (position.contains("Manager")) {
                return "Other Manager";
            }
            return "Other";
        }

        // HELPER METHOD: Get employees by position keyword
        private static List<Employee> getEmployeesByPosition(List<Employee> employees, String keyword) {
            return employees.stream()
                    .filter(e -> e.getPosition().toLowerCase().contains(keyword.toLowerCase()))
                    .collect(Collectors.toList());
        }

        // HELPER METHOD: Calculate wage based on position, category, and index
        private static double calculateWage(String position, String category, int index) {
            double baseWage = 15.0;

            // More realistic position-based adjustments
            if (position.contains("Senior") || position.contains("Lead") || position.contains("Architect")) {
                baseWage += 20.0;
            } else if (position.contains("Manager")) {
                baseWage += 18.0;  // Reduced from 25
            } else if (position.contains("Engineer") || position.contains("Developer")) {
                baseWage += 15.0;  // Reduced from 20
            } else if (position.contains("Support") || position.contains("Help Desk")) {
                baseWage += 8.0;   // Reduced from 10
            }

            // Remove or reduce category-based adjustment to avoid double counting
            if ("Manager".equals(category) && !position.contains("Manager")) {
                baseWage += 10.0;  // Only add if position doesn't already indicate manager
            } else if ("Contractor".equals(category)) {
                baseWage += 3.0;   // Reduced from 5
            }

            // Experience-based (index as proxy for experience)
            baseWage += (index % 10) * 0.3;  // Reduced from 0.5

            return Math.min(baseWage, 50.0); // Lower cap at $50/hour
        }

        // HELPER METHOD: Add skills to employee
        private static void addEmployeeSkills(EmployeeInfo empInfo, String position, String department, int index) {
            // Department-specific skills
            switch (department) {
                case "Development":
                    empInfo.addSkill("Java");
                    empInfo.addSkill("Spring Boot");
                    empInfo.addSkill("Git");
                    if (index % 3 == 0) empInfo.addSkill("React");
                    if (index % 4 == 0) empInfo.addSkill("Microservices");
                    break;
                case "Quality Assurance":
                    empInfo.addSkill("Testing");
                    empInfo.addSkill("JUnit");
                    empInfo.addSkill("Selenium");
                    if (index % 3 == 0) empInfo.addSkill("TestNG");
                    if (index % 4 == 0) empInfo.addSkill("Automation");
                    break;
                case "Technical Support":
                case "IT Support":
                    empInfo.addSkill("Customer Service");
                    empInfo.addSkill("Troubleshooting");
                    empInfo.addSkill("Communication");
                    if (index % 3 == 0) empInfo.addSkill("Networking");
                    break;
                case "DevOps":
                    empInfo.addSkill("Docker");
                    empInfo.addSkill("Kubernetes");
                    empInfo.addSkill("AWS");
                    empInfo.addSkill("CI/CD");
                    break;
                case "Product Management":
                case "Project Management":
                    empInfo.addSkill("Project Management");
                    empInfo.addSkill("Agile");
                    empInfo.addSkill("Stakeholder Management");
                    if (index % 3 == 0) empInfo.addSkill("Scrum");
                    if (index % 4 == 0) empInfo.addSkill("JIRA");
                    break;
            }

            // Position-specific skills
            if (position.contains("Cloud") || position.contains("DevOps")) {
                empInfo.addSkill("Infrastructure as Code");
                empInfo.addSkill("Monitoring");
            }

            if (position.contains("Scrum Master")) {
                empInfo.addSkill("Facilitation");
                empInfo.addSkill("Coaching");
                empInfo.addSkill("Agile Methodologies");
            }

            if (position.contains("Manager") || position.contains("Lead")) {
                empInfo.addSkill("Leadership");
                empInfo.addSkill("Team Management");
                empInfo.addSkill("Decision Making");
            }

            // Common skills for all
            empInfo.addSkill("Communication");
            empInfo.addSkill("Problem Solving");

            // Add some random specialized skills
            Random rand = new Random(index);
            String[] specialSkills = {"Python", "SQL", "JavaScript", "TypeScript", "API Design",
                    "Security", "Performance Testing", "Cloud Security", "Data Analysis"};
            if (rand.nextBoolean()) {
                empInfo.addSkill(specialSkills[rand.nextInt(specialSkills.length)]);
            }
        }

        // ENHANCED EMPLOYEE CLASS WITH DEPARTMENTS
        public static class Employee {
            private String id;
            private String name;
            private String gender;
            private int group;
            private String category;
            private String managerId;
            private String department;
            private String position;
            private final Set<String> leaveDates = new HashSet<>();
            private String shiftColor;

            public Employee() {
            }

            public Employee(String id, String name, String gender, String category, String managerId,
                            String department, String position) {
                this.id = id;
                this.name = name;
                this.gender = gender;
                this.category = category;
                this.managerId = managerId;
                this.department = department;
                this.position = position;

                // Assign color based on department
                switch (department) {
                    case "Development":
                        this.shiftColor = "#4CAF50";
                        break; // Green
                    case "Testing":
                        this.shiftColor = "#FF9800";
                        break; // Orange
                    case "DevOps":
                        this.shiftColor = "#2196F3";
                        break; // Blue
                    case "Support":
                        this.shiftColor = "#9C27B0";
                        break; // Purple
                    case "Management":
                        this.shiftColor = "#F44336";
                        break; // Red
                    default:
                        this.shiftColor = "#607D8B"; // Grey
                }
            }

            public void addLeave(Object leaveData) {
                String dateStr = null;
                if (leaveData instanceof String) {
                    dateStr = (String) leaveData;
                } else if (leaveData instanceof LeaveRecord) {
                    dateStr = ((LeaveRecord) leaveData).getDate();
                }
                if (dateStr != null) {
                    leaveDates.add(dateStr);
                }
            }

            public Set<String> getLeaveDates() {
                return leaveDates;
            }

            public boolean isOnLeave(String date) {
                return leaveDates.contains(date);
            }

            public boolean canWorkShift(String shiftName) {
                if (systemConfig.isFemaleShiftRestrictions() && "Female".equalsIgnoreCase(gender)) {
                    return !"Night".equals(shiftName);
                }
                return true;
            }

            public String getId() {
                return id;
            }

            public String getName() {
                return name;
            }

            public String getGender() {
                return gender;
            }

            public String getCategory() {
                return category;
            }

            public String getManagerId() {
                return managerId;
            }

            public String getDepartment() {
                return department;
            }

            public String getPosition() {
                return position;
            }

            public String getShiftColor() {
                return shiftColor;
            }

            public void setId(String id) {
                this.id = id;
            }

            public void setName(String name) {
                this.name = name;
            }

            public void setGender(String gender) {
                this.gender = gender;
            }

            public void setCategory(String category) {
                this.category = category;
            }

            public void setManagerId(String managerId) {
                this.managerId = managerId;
            }

            public void setDepartment(String department) {
                this.department = department;
            }

            public void setPosition(String position) {
                this.position = position;
            }

            public void setShiftColor(String shiftColor) {
                this.shiftColor = shiftColor;
            }

            public int getGroup() {
                return group;
            }

            public void setGroup(int group) {
                this.group = group;
            }
        }
    }





}



