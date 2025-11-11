package com.scheduler;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.api.score.stream.*;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonIgnore;

@QuarkusMain
@Path("/")
public class ShiftApp {

    // Store employee shifts and leaves - make them static so they persist
    private static Map<String, String> employeeShifts = new HashMap<>();
    private static Map<String, Set<String>> employeeLeaves = new HashMap<>();

    // Track if we need to generate new shifts
    private static boolean shiftsGenerated = false;

    // Shift configuration
    private static final Map<String, ShiftTime> SHIFT_TIMES = Map.of(
            "Morning", new ShiftTime(LocalTime.of(9, 0), LocalTime.of(16, 0)),
            "Afternoon", new ShiftTime(LocalTime.of(16, 0), LocalTime.of(21, 0)),
            "Night", new ShiftTime(LocalTime.of(21, 0), LocalTime.of(6, 0))
    );

    // Shift time configuration class
    public static class ShiftTime {
        private LocalTime startTime;
        private LocalTime endTime;

        public ShiftTime(LocalTime startTime, LocalTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public LocalTime getStartTime() { return startTime; }
        public LocalTime getEndTime() { return endTime; }
    }

    public static void main(String[] args) {
        System.setProperty("quarkus.http.port", "8083");
        Quarkus.run(args);
    }

    // Attendance Service with shift validation
    public static class AttendanceService {
        private static Map<String, List<AttendanceRecord>> attendanceRecords = new HashMap<>();
        private static Map<String, Boolean> employeeClockStatus = new HashMap<>();

        // NEW METHOD: Clear all clock status and attendance records when generating new shifts
        public static void clearAllClockStatusAndAttendance() {
            employeeClockStatus.clear();
            attendanceRecords.clear();
            System.out.println("🔄 Cleared all clock status and attendance records for new shift generation");
        }

        public static AttendanceRecord clockIn(String employeeId, LocalDateTime clockInTime, String scheduledShift) {
            // Check if employee is already clocked in
            if (isClockedIn(employeeId)) {
                throw new IllegalStateException("Employee " + employeeId + " is already clocked in");
            }

            // Validate if employee has a scheduled shift for today
            if (scheduledShift == null || scheduledShift.isEmpty()) {
                throw new IllegalStateException("Employee " + employeeId + " has no scheduled shift for today");
            }

            // Validate clock-in time against scheduled shift
            validateClockInTime(clockInTime, scheduledShift, employeeId);

            AttendanceRecord record = new AttendanceRecord(employeeId, clockInTime);
            record.setScheduledShift(scheduledShift, getShiftStartTime(clockInTime.toLocalDate(), scheduledShift));

            // Calculate lateness
            calculateLateness(record, clockInTime, scheduledShift);

            attendanceRecords.computeIfAbsent(employeeId, k -> new ArrayList<>()).add(record);
            employeeClockStatus.put(employeeId, true);

            System.out.println("✅ Clock IN - " + employeeId + " at " + clockInTime +
                    " for " + scheduledShift + " shift" +
                    (record.isLate() ? " (LATE: " + record.getLateMinutes() + " minutes)" : ""));

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

            lastRecord.clockOut(clockOutTime);
            employeeClockStatus.put(employeeId, false);

            System.out.println("✅ Clock OUT - " + employeeId + " at " + clockOutTime);
            System.out.println("📊 Hours worked: " + lastRecord.getHoursWorked() + " hours");

            return lastRecord;
        }

        private static void validateClockInTime(LocalDateTime clockInTime, String scheduledShift, String employeeId) {
            LocalDate today = clockInTime.toLocalDate();
            ShiftTime shiftTime = SHIFT_TIMES.get(scheduledShift);

            if (shiftTime == null) {
                throw new IllegalStateException("Invalid shift type: " + scheduledShift);
            }

            LocalDateTime shiftStart = LocalDateTime.of(today, shiftTime.getStartTime());
            LocalDateTime shiftEnd = LocalDateTime.of(today, shiftTime.getEndTime());

            // Handle night shift (crosses midnight)
            if (scheduledShift.equals("Night")) {
                shiftEnd = shiftEnd.plusDays(1); // Night shift ends next day at 6 AM
            }

            // Allow clock-in 30 minutes before shift start and up to 2 hours after shift end
            LocalDateTime earliestClockIn = shiftStart.minusMinutes(30);
            LocalDateTime latestClockIn = shiftEnd.plusHours(2);

            if (clockInTime.isBefore(earliestClockIn)) {
                throw new IllegalStateException("Cannot clock in more than 30 minutes before shift start. " +
                        "Shift starts at " + shiftTime.getStartTime() + ". Earliest clock-in: " + earliestClockIn.toLocalTime());
            }

            if (clockInTime.isAfter(latestClockIn)) {
                throw new IllegalStateException("Cannot clock in more than 2 hours after shift end. " +
                        "Shift ended at " + shiftTime.getEndTime() + ". Latest clock-in: " + latestClockIn.toLocalTime());
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

            // Minimum work duration check (optional - 15 minutes)
            Duration minWorkDuration = Duration.ofMinutes(15);
            if (Duration.between(clockInTime, clockOutTime).compareTo(minWorkDuration) < 0) {
                throw new IllegalStateException("Minimum work duration is 15 minutes");
            }
        }

        private static void calculateLateness(AttendanceRecord record, LocalDateTime clockInTime, String scheduledShift) {
            ShiftTime shiftTime = SHIFT_TIMES.get(scheduledShift);
            LocalDateTime scheduledStart = LocalDateTime.of(clockInTime.toLocalDate(), shiftTime.getStartTime());

            // 5-minute grace period
            LocalDateTime gracePeriodEnd = scheduledStart.plusMinutes(5);

            if (clockInTime.isAfter(gracePeriodEnd)) {
                long lateMinutes = Duration.between(scheduledStart, clockInTime).toMinutes();
                record.setLateMinutes(lateMinutes);
                record.setLate(true);
            } else {
                record.setLateMinutes(0);
                record.setLate(false);
            }
        }

        // Get today's scheduled shift for employee
        public static String getTodayScheduledShift(String employeeId) {
            LocalDate today = LocalDate.now();
            String todayStr = today.toString();

            // Check if employee is on leave today
            if (employeeLeaves.containsKey(employeeId) &&
                    employeeLeaves.get(employeeId).contains(todayStr)) {
                return null; // Employee is on leave
            }

            // Return the scheduled shift from the optimization
            return employeeShifts.get(employeeId);
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
            int lateArrivals = 0;
            int missingClockOuts = 0;
            double totalLateMinutes = 0;

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
                        recordMap.put("status", record.getClockOutTime() == null ? "CLOCKED_IN" : "CLOCKED_OUT");

                        todayRecords.add(recordMap);

                        if (record.getClockOutTime() == null) {
                            clockedInCount++;
                        }
                        if (record.isLate()) {
                            lateArrivals++;
                            totalLateMinutes += record.getLateMinutes();
                        }
                    }
                }
            }

            summary.put("date", today.toString());
            summary.put("totalRecords", todayRecords.size());
            summary.put("currentlyClockedIn", clockedInCount);
            summary.put("lateArrivals", lateArrivals);
            summary.put("missingClockOuts", missingClockOuts);
            summary.put("totalLateMinutes", totalLateMinutes);
            summary.put("averageLateMinutes", lateArrivals > 0 ? totalLateMinutes / lateArrivals : 0);
            summary.put("records", todayRecords);

            return summary;
        }

        private static LocalDateTime getShiftStartTime(LocalDate date, String shiftName) {
            ShiftTime shiftTime = SHIFT_TIMES.get(shiftName);
            if (shiftTime == null) {
                throw new IllegalArgumentException("Invalid shift name: " + shiftName);
            }
            return LocalDateTime.of(date, shiftTime.getStartTime());
        }
    }

    // Enhanced Attendance Record Class
    public static class AttendanceRecord {
        private String employeeId;
        private LocalDateTime clockInTime;
        private LocalDateTime clockOutTime;
        private String scheduledShift;
        private LocalDateTime scheduledStart;
        private boolean isLate = false;
        private long lateMinutes = 0;

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
            if (clockOutTime == null) return 0;
            Duration duration = Duration.between(clockInTime, clockOutTime);
            return duration.toMinutes() / 60.0;
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

        @JsonIgnore
        public boolean isEarlyClockOut() {
            if (scheduledStart == null || clockOutTime == null) return false;
            LocalDateTime scheduledEnd = scheduledStart.plusHours(8); // Assume 8-hour shifts
            return clockOutTime.isBefore(scheduledEnd.minusMinutes(5));
        }

        @JsonIgnore
        public long getEarlyDepartureMinutes() {
            if (scheduledStart == null || clockOutTime == null || !isEarlyClockOut()) return 0;
            LocalDateTime scheduledEnd = scheduledStart.plusHours(8);
            return Duration.between(clockOutTime, scheduledEnd).toMinutes();
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

            // Get today's scheduled shift for the employee
            String scheduledShift = AttendanceService.getTodayScheduledShift(employeeId);

            if (scheduledShift == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Employee has no scheduled shift for today or is on leave"))
                        .build();
            }

            AttendanceRecord record = AttendanceService.clockIn(employeeId, clockInTime, scheduledShift);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("employeeId", employeeId);
            response.put("clockInTime", clockInTime.toString());
            response.put("scheduledShift", scheduledShift);
            response.put("isLate", record.isLate());
            response.put("lateMinutes", record.getLateMinutes());

            if (record.isLate()) {
                response.put("message", "Clocked in successfully (LATE: " + record.getLateMinutes() + " minutes)");
            } else {
                response.put("message", "Clocked in successfully (On time)");
            }

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
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

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("employeeId", employeeId);
            response.put("clockOutTime", clockOutTime.toString());
            response.put("hoursWorked", record.getHoursWorked());
            response.put("isLate", record.isLate());
            response.put("lateMinutes", record.getLateMinutes());
            response.put("scheduledShift", record.getScheduledShift());
            response.put("message", "Clocked out successfully");

            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
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
                        "message", "No scheduled shift for today or employee is on leave"
                )).build();
            }

            ShiftTime shiftTime = SHIFT_TIMES.get(scheduledShift);
            Map<String, Object> shiftInfo = new HashMap<>();
            shiftInfo.put("employeeId", employeeId);
            shiftInfo.put("hasShift", true);
            shiftInfo.put("shiftName", scheduledShift);
            shiftInfo.put("startTime", shiftTime.getStartTime().toString());
            shiftInfo.put("endTime", shiftTime.getEndTime().toString());
            shiftInfo.put("allowedClockInStart", shiftTime.getStartTime().minusMinutes(30).toString());
            shiftInfo.put("allowedClockInEnd", shiftTime.getEndTime().plusHours(2).toString());

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

                Map<String, Object> employeeStatus = new HashMap<>();
                employeeStatus.put("isClockedIn", entry.getValue());
                employeeStatus.put("scheduledShift", scheduledShift);
                employeeStatus.put("hasShift", scheduledShift != null);

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

            return Response.ok(Map.of(
                    "employeeId", employeeId,
                    "isClockedIn", isClockedIn,
                    "scheduledShift", scheduledShift,
                    "hasShift", scheduledShift != null
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
                return recordMap;
            }).collect(Collectors.toList());

            return Response.ok(Map.of(
                    "employeeId", employeeId,
                    "records", serializableRecords,
                    "totalRecords", serializableRecords.size()
            )).build();
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

    @GET
    @Path("shifts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSchedule() {
        try {
            System.out.println("=== GET /shifts called ===");

            // Only generate new shifts if they haven't been generated yet or if explicitly requested
            if (!shiftsGenerated || employeeShifts.isEmpty()) {
                System.out.println("🔄 Generating initial shifts...");
                Scheduler.ScheduleResponse response = Scheduler.generateOptimizedSchedule();
                shiftsGenerated = true;
                System.out.println("Generated optimized schedule for " + response.getEmployees().size() + " employees");
                return Response.ok(response).build();
            } else {
                System.out.println("📋 Returning existing shifts (no regeneration)");
                // Return existing shifts without regenerating
                Scheduler.ScheduleResponse response = Scheduler.getExistingSchedule();
                return Response.ok(response).build();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to generate schedule: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("shifts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response reoptimize() {
        try {
            System.out.println("=== POST /shifts called - Generating new optimized shifts ===");

            // Clear existing clock status and attendance records when generating new shifts
            AttendanceService.clearAllClockStatusAndAttendance();

            // Clear existing shifts to generate new optimized ones
            employeeShifts.clear();
            Scheduler.ScheduleResponse response = Scheduler.generateOptimizedSchedule();
            shiftsGenerated = true;

            System.out.println("✅ New shifts generated successfully");
            return Response.ok(response).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to reoptimize: " + e.getMessage()))
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

        if (employeeId == null || startDate == null || endDate == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing required fields"))
                    .build();
        }

        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);

            Set<String> leaveDates = new HashSet<>();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    leaveDates.add(date.toString());
                }
            }

            employeeLeaves.computeIfAbsent(employeeId, k -> new HashSet<>()).addAll(leaveDates);

            System.out.println("✅ Applied leave for " + employeeId + ": " + leaveDates.size() + " days - " + leaveDates);

            // Regenerate schedule with new leave information
            Scheduler.ScheduleResponse response = Scheduler.generateOptimizedSchedule();
            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid date format: " + e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/shifts/remove-leave")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeLeave(Map<String, Object> input) {
        String employeeId = (String) input.get("employeeId");

        if (employeeId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Missing employeeId"))
                    .build();
        }

        employeeLeaves.remove(employeeId);
        System.out.println("🗑️ Removed ALL leaves for: " + employeeId);

        try {
            // Regenerate schedule after removing leaves
            Scheduler.ScheduleResponse response = Scheduler.generateOptimizedSchedule();
            return Response.ok(response).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to generate schedule after removing leave: " + e.getMessage()))
                    .build();
        }
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
            LocalDate.parse(leaveDate);

            if (employeeLeaves.containsKey(employeeId)) {
                Set<String> leaves = employeeLeaves.get(employeeId);
                boolean removed = leaves.remove(leaveDate);

                if (removed) {
                    System.out.println("✅ Revoked leave for " + employeeId + " on " + leaveDate);
                    if (leaves.isEmpty()) {
                        employeeLeaves.remove(employeeId);
                    }
                } else {
                    System.out.println("⚠️ No leave found for " + employeeId + " on " + leaveDate);
                }
            } else {
                System.out.println("⚠️ No leaves found for employee: " + employeeId);
            }

            // Regenerate schedule after revoking leave
            Scheduler.ScheduleResponse response = Scheduler.generateOptimizedSchedule();
            return Response.ok(response).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid date format: " + e.getMessage()))
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

            for (Map.Entry<String, Set<String>> entry : employeeLeaves.entrySet()) {
                String employeeId = entry.getKey();
                Set<String> leaveDates = entry.getValue();

                for (String leaveDate : leaveDates) {
                    Map<String, Object> leaveInfo = new HashMap<>();
                    leaveInfo.put("employeeId", employeeId);
                    leaveInfo.put("leaveDate", leaveDate);
                    allLeaves.add(leaveInfo);
                }
            }

            System.out.println("Returning " + allLeaves.size() + " leave records");
            return Response.ok(Map.of("leaves", allLeaves)).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to get leaves: " + e.getMessage()))
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

            @PlanningVariable(valueRangeProviderRefs = "shiftRange")
            private String shift;

            public EmployeeAssignment() {}

            public EmployeeAssignment(String id, String employeeId, String employeeName, String date) {
                this.id = id;
                this.employeeId = employeeId;
                this.employeeName = employeeName;
                this.date = date;
            }

            // Getters and setters
            public String getId() { return id; }
            public String getEmployeeId() { return employeeId; }
            public String getEmployeeName() { return employeeName; }
            public String getDate() { return date; }
            public String getShift() { return shift; }
            public void setShift(String shift) { this.shift = shift; }
        }

        // TIMEFOLD PLANNING SOLUTION
        @PlanningSolution
        public static class ShiftSchedule {
            @PlanningEntityCollectionProperty
            private List<EmployeeAssignment> assignments;

            @ValueRangeProvider(id = "shiftRange")
            private List<String> shiftTypes;

            @PlanningScore
            private HardSoftScore score;

            public ShiftSchedule() {}

            public ShiftSchedule(List<EmployeeAssignment> assignments, List<String> shiftTypes) {
                this.assignments = assignments;
                this.shiftTypes = shiftTypes;
            }

            // Getters and setters
            public List<EmployeeAssignment> getAssignments() { return assignments; }
            public List<String> getShiftTypes() { return shiftTypes; }
            public HardSoftScore getScore() { return score; }
            public void setScore(HardSoftScore score) { this.score = score; }
        }

        // TIMEFOLD CONSTRAINTS
        public static class ShiftConstraints implements ConstraintProvider {
            @Override
            public Constraint[] defineConstraints(ConstraintFactory factory) {
                return new Constraint[] {
                        factory.forEach(EmployeeAssignment.class)
                                .groupBy(EmployeeAssignment::getEmployeeId, EmployeeAssignment::getDate, ConstraintCollectors.count())
                                .filter((employeeId, date, count) -> count > 1)
                                .penalize(HardSoftScore.ONE_HARD, (employeeId, date, count) -> count * 10)
                                .asConstraint("oneShiftPerDay"),

                        factory.forEach(EmployeeAssignment.class)
                                .groupBy(EmployeeAssignment::getShift, ConstraintCollectors.count())
                                .filter((shift, count) -> Math.abs(count - 33) > 10)
                                .penalize(HardSoftScore.ONE_SOFT, (shift, count) -> Math.abs(count - 33) * 2)
                                .asConstraint("balancedShifts"),

                        factory.forEach(EmployeeAssignment.class)
                                .join(EmployeeAssignment.class)
                                .filter((assignment1, assignment2) ->
                                        assignment1.getEmployeeId().equals(assignment2.getEmployeeId()) &&
                                                !assignment1.getDate().equals(assignment2.getDate()) &&
                                                !assignment1.getShift().equals(assignment2.getShift()))
                                .penalize(HardSoftScore.ONE_SOFT, (a1, a2) -> 1)
                                .asConstraint("consistentShifts")
                };
            }
        }

        // RESPONSE FOR BRYNTUM
        public static class ScheduleResponse {
            private List<Map<String, Object>> employees = new ArrayList<>();
            private List<Map<String, Object>> slots = new ArrayList<>();
            private List<Map<String, Object>> leaves = new ArrayList<>();

            public ScheduleResponse() {}

            public ScheduleResponse(List<Map<String, Object>> employees, List<Map<String, Object>> slots, List<Map<String, Object>> leaves) {
                this.employees = employees;
                this.slots = slots;
                this.leaves = leaves;
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
        }

        // NEW METHOD: Get existing schedule without regenerating
        public static ScheduleResponse getExistingSchedule() {
            System.out.println("📋 Returning existing schedule without regeneration");

            List<Employee> employees = createEmployees();
            List<Map<String, Object>> employeeAssignments = new ArrayList<>();
            List<Map<String, Object>> slotData = new ArrayList<>();
            List<Map<String, Object>> leaveEvents = new ArrayList<>();

            LocalDate startDate = LocalDate.of(2025, 11, 10);

            // Group existing shifts by date and shift
            Map<String, Map<String, List<String>>> shiftsByDateAndShift = new HashMap<>();

            for (int day = 0; day < 19; day++) {
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

                // Add employees to their respective shifts based on existing assignments
                for (Employee emp : employees) {
                    if (emp.isOnLeave(dateStr)) {
                        continue;
                    }

                    String shift = employeeShifts.get(emp.getId());
                    if (shift != null && shiftsByDateAndShift.get(dateStr).containsKey(shift)) {
                        shiftsByDateAndShift.get(dateStr).get(shift).add(emp.getId());
                    }
                }
            }

            // Convert to Bryntum format
            for (int day = 0; day < 19; day++) {
                LocalDate date = startDate.plusDays(day);
                String dateStr = date.toString();

                List<Employee> employeesOnLeave = employees.stream()
                        .filter(emp -> emp.isOnLeave(dateStr))
                        .collect(Collectors.toList());

                for (Employee emp : employeesOnLeave) {
                    Map<String, Object> leaveEvent = new HashMap<>();
                    leaveEvent.put("id", "leave-" + emp.getId() + "-" + dateStr);
                    leaveEvent.put("employeeId", emp.getId());
                    leaveEvent.put("employeeName", emp.getName());
                    leaveEvent.put("date", dateStr);
                    leaveEvent.put("type", "leave");
                    leaveEvents.add(leaveEvent);
                }

                if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    for (String shift : Arrays.asList("Morning", "Afternoon", "Night")) {
                        Map<String, Object> slot = new HashMap<>();
                        slot.put("date", dateStr);
                        slot.put("name", shift);

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
                                            Map<String, Object> empMap = new HashMap<>();
                                            empMap.put("id", emp.getId());
                                            empMap.put("name", emp.getName());
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
                            assignmentMap.put("shift", shift);
                            assignmentMap.put("date", dateStr);
                            assignmentMap.put("leaveDates", new ArrayList<>());
                            employeeAssignments.add(assignmentMap);
                        }
                    }
                }
            }

            System.out.println("📊 Converted existing schedule to Bryntum format:");
            System.out.println("   - Assignments: " + employeeAssignments.size());
            System.out.println("   - Slots: " + slotData.size());
            System.out.println("   - Leave days: " + leaveEvents.size());

            return new ScheduleResponse(employeeAssignments, slotData, leaveEvents);
        }

        // GENERATE OPTIMIZED SCHEDULE USING TIMEFOLD
        public static ScheduleResponse generateOptimizedSchedule() {
            System.out.println("🔧 Generating optimized schedule with Timefold for 3 weeks...");

            List<Employee> employees = createEmployees();
            List<String> shiftTypes = Arrays.asList("Morning", "Afternoon", "Night");

            List<EmployeeAssignment> assignments = new ArrayList<>();
            LocalDate startDate = LocalDate.of(2025, 11, 10);
            Random rand = new Random();

            for (int day = 0; day < 19; day++) {
                LocalDate date = startDate.plusDays(day);

                if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    continue;
                }

                String dateStr = date.toString();

                for (Employee emp : employees) {
                    if (emp.isOnLeave(dateStr)) {
                        System.out.println("SKIPPING - " + emp.getName() + " on leave: " + dateStr);
                        continue;
                    }

                    String assignmentId = emp.getId() + "-" + dateStr;
                    EmployeeAssignment assignment = new EmployeeAssignment(assignmentId, emp.getId(), emp.getName(), dateStr);

                    // Only use existing shift if we have one and we're not regenerating
                    String existingShift = employeeShifts.get(emp.getId());
                    if (existingShift != null) {
                        assignment.setShift(existingShift);
                    } else {
                        assignment.setShift(shiftTypes.get(rand.nextInt(shiftTypes.size())));
                    }

                    assignments.add(assignment);
                }
            }

            System.out.println("Created " + assignments.size() + " assignments for optimization");

            SolverConfig solverConfig = new SolverConfig()
                    .withSolutionClass(ShiftSchedule.class)
                    .withEntityClasses(EmployeeAssignment.class)
                    .withConstraintProviderClass(ShiftConstraints.class)
                    .withTerminationSpentLimit(java.time.Duration.ofSeconds(5));

            SolverFactory<ShiftSchedule> solverFactory = SolverFactory.create(solverConfig);
            Solver<ShiftSchedule> solver = solverFactory.buildSolver();

            ShiftSchedule problem = new ShiftSchedule(assignments, shiftTypes);
            ShiftSchedule solution = solver.solve(problem);

            System.out.println("✅ Optimization completed with score: " + solution.getScore());

            // Store optimized shifts for attendance validation
            for (EmployeeAssignment assignment : solution.getAssignments()) {
                employeeShifts.put(assignment.getEmployeeId(), assignment.getShift());
            }

            return convertToBryntumFormatWithLeaves(solution.getAssignments(), employees);
        }

        // CONVERT TIMEFOLD SOLUTION TO BRYNTUM FORMAT WITH LEAVES
        private static ScheduleResponse convertToBryntumFormatWithLeaves(List<EmployeeAssignment> assignments, List<Employee> employees) {
            List<Map<String, Object>> employeeAssignments = new ArrayList<>();
            List<Map<String, Object>> slotData = new ArrayList<>();
            List<Map<String, Object>> leaveEvents = new ArrayList<>();

            LocalDate startDate = LocalDate.of(2025, 11, 10);

            Map<String, Map<String, List<EmployeeAssignment>>> assignmentsByDateAndShift = new HashMap<>();

            for (EmployeeAssignment assignment : assignments) {
                assignmentsByDateAndShift
                        .computeIfAbsent(assignment.getDate(), k -> new HashMap<>())
                        .computeIfAbsent(assignment.getShift(), k -> new ArrayList<>())
                        .add(assignment);
            }

            for (int day = 0; day < 19; day++) {
                LocalDate date = startDate.plusDays(day);
                String dateStr = date.toString();

                List<Employee> employeesOnLeave = employees.stream()
                        .filter(emp -> emp.isOnLeave(dateStr))
                        .collect(Collectors.toList());

                for (Employee emp : employeesOnLeave) {
                    Map<String, Object> leaveEvent = new HashMap<>();
                    leaveEvent.put("id", "leave-" + emp.getId() + "-" + dateStr);
                    leaveEvent.put("employeeId", emp.getId());
                    leaveEvent.put("employeeName", emp.getName());
                    leaveEvent.put("date", dateStr);
                    leaveEvent.put("type", "leave");
                    leaveEvents.add(leaveEvent);
                }

                if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    for (String shift : Arrays.asList("Morning", "Afternoon", "Night")) {
                        Map<String, Object> slot = new HashMap<>();
                        slot.put("date", dateStr);
                        slot.put("name", shift);

                        List<Map<String, Object>> assignedEmployees = new ArrayList<>();

                        if (assignmentsByDateAndShift.containsKey(dateStr) &&
                                assignmentsByDateAndShift.get(dateStr).containsKey(shift)) {

                            assignedEmployees = assignmentsByDateAndShift.get(dateStr).get(shift).stream()
                                    .map(assignment -> {
                                        Map<String, Object> empMap = new HashMap<>();
                                        empMap.put("id", assignment.getEmployeeId());
                                        empMap.put("name", assignment.getEmployeeName());
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
                            assignmentMap.put("shift", shift);
                            assignmentMap.put("date", dateStr);
                            assignmentMap.put("leaveDates", new ArrayList<>());
                            employeeAssignments.add(assignmentMap);
                        }
                    }
                }
            }

            System.out.println("📊 Converted to Bryntum format:");
            System.out.println("   - Assignments: " + employeeAssignments.size());
            System.out.println("   - Slots: " + slotData.size());
            System.out.println("   - Leave days: " + leaveEvents.size());

            return new ScheduleResponse(employeeAssignments, slotData, leaveEvents);
        }

        private static List<Employee> createEmployees() {
            List<Employee> employees = new ArrayList<>();
            String[] names = {
                    "Alice", "Bob", "Charlie", "Diana", "Ethan", "Fiona", "George", "Hannah", "Isaac", "Julia",
                    "Kevin", "Laura", "Mike", "Nina", "Oscar", "Paula", "Geralt", "Ryan", "Sophia", "Tom",
                    "Uma", "Victor", "Wendy", "Xavier", "Yara", "Zack", "Aria", "Ben", "Cora", "Dylan",
                    "Eva", "Finn", "Gina", "Hank", "Iris", "Jack", "Kara", "Leo", "Maya", "Noah",
                    "Olivia", "Paul", "Quinn", "Rose", "Sam", "Tara", "Umar", "Vera", "Will", "Zoe"
            };

            for (int i = 0; i < 50; i++) {
                Employee e = new Employee("E" + (i + 1), names[i]);

                if (employeeLeaves.containsKey(e.getId())) {
                    employeeLeaves.get(e.getId()).forEach(e::addLeave);
                }

                employees.add(e);
            }

            System.out.println("Created " + employees.size() + " employees");
            return employees;
        }
    }

    // SIMPLE EMPLOYEE CLASS
    public static class Employee {
        private String id;
        private String name;
        private Set<String> leaveDates = new HashSet<>();

        public Employee() {}
        public Employee(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public void addLeave(String date) { leaveDates.add(date); }
        public Set<String> getLeaveDates() { return leaveDates; }
        public boolean isOnLeave(String date) { return leaveDates.contains(date); }

        public String getId() { return id; }
        public String getName() { return name; }

        public void setId(String id) { this.id = id; }
        public void setName(String name) { this.name = name; }
    }
}