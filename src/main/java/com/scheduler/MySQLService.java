package com.scheduler;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MySQLService {

    @Inject
    DataSource dataSource;

    public MySQLService() {
        System.out.println("🔄 MySQLService bean created");
    }

    @PostConstruct
    public void init() {
        System.out.println("🔄 MySQLService CDI initialized");
        initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        Connection conn = dataSource.getConnection();
        // System.out.println("✅ MySQL connection retrieved from pool"); // Optional, but let's keep it quiet to match Quarkus norm
        return conn;
    }

    public void initializeDatabase() {
        String sqlEmployees = "CREATE TABLE IF NOT EXISTS employees (" +
                "id VARCHAR(50) PRIMARY KEY," +
                "name VARCHAR(100) NOT NULL," +
                "position VARCHAR(100) NULL," +
                "category VARCHAR(50) NULL," +
                "gender VARCHAR(10) NULL," +
                "hourly_wage DOUBLE NULL," +
                "performance_rating INT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")";

        String sql = "CREATE TABLE IF NOT EXISTS shift_assignments (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "assignment_date DATE NOT NULL," +
                "shift_name VARCHAR(50) NOT NULL," +
                "employee_id VARCHAR(50) NOT NULL," +
                "employee_name VARCHAR(100) NULL," +
                "employee_role VARCHAR(100) NULL," +
                "employee_category VARCHAR(50) NULL," +
                "gender VARCHAR(10) NULL," +
                "rating INT NULL," +
                "start_time VARCHAR(5) NULL," +
                "end_time VARCHAR(5) NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY unique_assignment (assignment_date, shift_name, employee_id)" +
                ")";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sqlEmployees);
            stmt.execute(sql);
            
            try {
                stmt.execute("ALTER TABLE shift_assignments ADD COLUMN start_time VARCHAR(5)");
            } catch (SQLException e) { /* Ignore if already exists */ }
            
            try {
                stmt.execute("ALTER TABLE shift_assignments ADD COLUMN end_time VARCHAR(5)");
            } catch (SQLException e) { /* Ignore if already exists */ }
            
            System.out.println("✅ MySQL tables ready");

        } catch (SQLException e) {
            System.err.println("❌ Failed to initialize table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void syncEmployee(String id, String name, String position, String category, String gender, double hourlyWage, int performanceRating) {
        String sql = "INSERT INTO employees " +
                "(id, name, position, category, gender, hourly_wage, performance_rating) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "name = VALUES(name), " +
                "position = VALUES(position), " +
                "category = VALUES(category), " +
                "gender = VALUES(gender), " +
                "hourly_wage = VALUES(hourly_wage), " +
                "performance_rating = VALUES(performance_rating)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, position);
            pstmt.setString(4, category);
            pstmt.setString(5, gender);
            pstmt.setDouble(6, hourlyWage);
            pstmt.setInt(7, performanceRating);

            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("❌ Failed to sync employee: " + e.getMessage());
        }
    }

    public void clearAllEmployees() {
        String sql = "DELETE FROM employees";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("🗑️ Cleared all employees");
        } catch (SQLException e) {
            System.err.println("❌ Clear employees failed: " + e.getMessage());
        }
    }

    /**
     * SYNC FOR TIMEFOLD - All fields provided
     */
    public void syncTimefoldAssignment(String date, String shift, String employeeId,
                                       String employeeName, String employeeRole,
                                       String employeeCategory, String gender, int rating,
                                       String startTime, String endTime) {

        System.out.println("⚡ TIMEFOLD SYNC - " + date + " " + shift + " " + employeeId);

        String sql = "INSERT INTO shift_assignments " +
                "(assignment_date, shift_name, employee_id, employee_name, " +
                "employee_role, employee_category, gender, rating, start_time, end_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "employee_name = VALUES(employee_name), " +
                "employee_role = VALUES(employee_role), " +
                "employee_category = VALUES(employee_category), " +
                "gender = VALUES(gender), " +
                "rating = VALUES(rating), " +
                "start_time = VALUES(start_time), " +
                "end_time = VALUES(end_time)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setString(2, shift);
            pstmt.setString(3, employeeId);
            pstmt.setString(4, employeeName);
            pstmt.setString(5, employeeRole);
            pstmt.setString(6, employeeCategory);
            pstmt.setString(7, gender);
            pstmt.setInt(8, rating);
            pstmt.setString(9, startTime);
            pstmt.setString(10, endTime);

            pstmt.executeUpdate();
            System.out.println("✅✅✅ TIMEFOLD SYNC SUCCESS! " + employeeId);

        } catch (SQLException e) {
            System.err.println("❌ TIMEFOLD SYNC FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * SYNC FOR MANUAL - Only basic fields, others set to NULL
     */
    public void syncManualAssignment(String date, String shift, String employeeId,
                                     String employeeName, String gender,
                                     String startTime, String endTime) {

        System.out.println("📝 MANUAL SYNC (with NULLs) - " + date + " " + shift + " " + employeeId);

        String sql = "INSERT INTO shift_assignments " +
                "(assignment_date, shift_name, employee_id, employee_name, " +
                "employee_role, employee_category, gender, rating, start_time, end_time) " +
                "VALUES (?, ?, ?, ?, NULL, NULL, ?, NULL, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "employee_name = VALUES(employee_name), " +
                "employee_role = NULL, " +
                "employee_category = NULL, " +
                "gender = VALUES(gender), " +
                "rating = NULL, " +
                "start_time = VALUES(start_time), " +
                "end_time = VALUES(end_time)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setString(2, shift);
            pstmt.setString(3, employeeId);
            pstmt.setString(4, employeeName != null ? employeeName : "Unknown");
            pstmt.setString(5, gender != null ? gender : "Unknown");
            pstmt.setString(6, startTime);
            pstmt.setString(7, endTime);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    System.out.println("✅✅✅ MANUAL SYNC SUCCESS! ID: " + rs.getInt(1) + " (with NULLs)");
                } else {
                    System.out.println("✅✅✅ MANUAL SYNC SUCCESS (updated with NULLs)");
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ MANUAL SYNC FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Generic sync - kept for backward compatibility
     */
    public void syncAssignment(String date, String shift, String employeeId,
                               String employeeName, String employeeRole,
                               String employeeCategory, String gender, int rating,
                               String startTime, String endTime) {
        syncTimefoldAssignment(date, shift, employeeId, employeeName,
                employeeRole, employeeCategory, gender, rating, startTime, endTime);
    }

    /**
     * Remove a specific assignment
     */
    public void removeAssignment(String date, String shift, String employeeId) {
        String sql = "DELETE FROM shift_assignments WHERE assignment_date = ? AND shift_name = ? AND employee_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setString(2, shift);
            pstmt.setString(3, employeeId);

            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                System.out.println("🗑️ Removed: " + date + " " + shift + " " + employeeId);
            }

        } catch (SQLException e) {
            System.err.println("❌ Remove failed: " + e.getMessage());
        }
    }

    /**
     * Clear all assignments for a specific date
     */
    public void clearAssignmentsForDate(String date) {
        String sql = "DELETE FROM shift_assignments WHERE assignment_date = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(date));
            int deleted = pstmt.executeUpdate();
            System.out.println("🗑️ Cleared " + deleted + " records for " + date);

        } catch (SQLException e) {
            System.err.println("❌ Clear failed: " + e.getMessage());
        }
    }

    public void removeEmployee(String employeeId) {
        String sql = "DELETE FROM employees WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, employeeId);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("❌ Failed to remove employee from MySQL: " + e.getMessage());
        }
    }

    public void removeAssignmentsForEmployee(String employeeId) {
        String sql = "DELETE FROM shift_assignments WHERE employee_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, employeeId);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("❌ Failed to remove assignments for employee from MySQL: " + e.getMessage());
        }
    }

    /**
     * Clear all assignments for multiple dates
     */
    public void clearAssignmentsForDates(List<String> dates) {
        if (dates == null || dates.isEmpty()) return;

        String sql = "DELETE FROM shift_assignments WHERE assignment_date = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (String date : dates) {
                pstmt.setDate(1, Date.valueOf(date));
                pstmt.addBatch();
            }

            int[] results = pstmt.executeBatch();
            int totalDeleted = 0;
            for (int result : results) {
                totalDeleted += result;
            }
            System.out.println("🗑️ Batch cleared " + totalDeleted + " records across " + dates.size() + " dates");

        } catch (SQLException e) {
            System.err.println("❌ Batch clear failed: " + e.getMessage());
        }
    }

    /**
     * Clear all assignments
     */
    public void clearAllAssignments() {
        String sql = "DELETE FROM shift_assignments";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            int deleted = stmt.executeUpdate(sql);
            System.out.println("🗑️ Cleared ALL " + deleted + " records");

            // Reset auto-increment
            stmt.executeUpdate("ALTER TABLE shift_assignments AUTO_INCREMENT = 1");
            System.out.println("🔄 Reset AUTO_INCREMENT to 1");

        } catch (SQLException e) {
            System.err.println("❌ Clear all failed: " + e.getMessage());
        }
    }

    /**
     * Remove assignments by employee ID
     */
    public void removeAssignmentsByEmployee(String employeeId) {
        String sql = "DELETE FROM shift_assignments WHERE employee_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, employeeId);
            int deleted = pstmt.executeUpdate();
            System.out.println("🗑️ Removed " + deleted + " assignments for employee: " + employeeId);

        } catch (SQLException e) {
            System.err.println("❌ Remove by employee failed: " + e.getMessage());
        }
    }

    /**
     * Check if assignment exists
     */
    public boolean assignmentExists(String date, String shift, String employeeId) {
        String sql = "SELECT COUNT(*) as count FROM shift_assignments WHERE assignment_date = ? AND shift_name = ? AND employee_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setString(2, shift);
            pstmt.setString(3, employeeId);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count") > 0;
            }

        } catch (SQLException e) {
            System.err.println("❌ Check failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get assignments for a specific date
     */
    public List<Map<String, Object>> getAssignmentsForDate(String date) {
        List<Map<String, Object>> results = new ArrayList<>();
        String sql = "SELECT * FROM shift_assignments WHERE assignment_date = ? ORDER BY shift_name, employee_id";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDate(1, Date.valueOf(date));
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> row = new java.util.HashMap<>();
                row.put("id", rs.getInt("id"));
                row.put("date", rs.getDate("assignment_date").toString());
                row.put("shift", rs.getString("shift_name"));
                row.put("employeeId", rs.getString("employee_id"));
                row.put("employeeName", rs.getString("employee_name"));
                row.put("employeeRole", rs.getString("employee_role")); // Will be NULL for manual
                row.put("employeeCategory", rs.getString("employee_category")); // Will be NULL for manual
                row.put("gender", rs.getString("gender"));
                row.put("rating", rs.getObject("rating")); // Use getObject to handle NULL
                results.add(row);
            }

        } catch (SQLException e) {
            System.err.println("❌ Query failed: " + e.getMessage());
        }

        return results;
    }

    /**
     * Get total count of records
     */
    public int getTotalAssignmentCount() {
        String sql = "SELECT COUNT(*) as total FROM shift_assignments";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("total");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error getting total assignments count: " + e.getMessage());
        }
        return 0;
    }

    public Map<String, ShiftApp.EmployeeInfo> loadAllEmployees() {
        Map<String, ShiftApp.EmployeeInfo> employeeInfo = new HashMap<>();
        String sql = "SELECT * FROM employees";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                ShiftApp.EmployeeInfo emp = new ShiftApp.EmployeeInfo();
                String id = rs.getString("id");
                emp.setId(id);
                emp.setName(rs.getString("name"));
                emp.setPosition(rs.getString("position"));
                emp.setCategory(rs.getString("category"));
                emp.setGender(rs.getString("gender"));
                emp.setHourlyWage(rs.getDouble("hourly_wage"));
                emp.setPerformanceRating(rs.getInt("performance_rating"));
                employeeInfo.put(id, emp);
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to load employees: " + e.getMessage());
        }
        return employeeInfo;
    }

    public Map<String, Map<String, List<String>>> loadAllAssignments() {
        Map<String, Map<String, List<String>>> shiftAssignments = new HashMap<>();
        String sql = "SELECT assignment_date, shift_name, employee_id FROM shift_assignments ORDER BY assignment_date, shift_name";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String date = rs.getDate("assignment_date").toString();
                String shift = rs.getString("shift_name");
                String empId = rs.getString("employee_id");

                shiftAssignments.computeIfAbsent(date, k -> new HashMap<>())
                                .computeIfAbsent(shift, k -> new ArrayList<>())
                                .add(empId);
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to load assignments: " + e.getMessage());
        }
        return shiftAssignments;
    }

    public Map<String, Map<String, ShiftApp.ShiftTimes>> loadAllShiftTimes() {
        Map<String, Map<String, ShiftApp.ShiftTimes>> shiftTimesCache = new HashMap<>();
        String sql = "SELECT assignment_date, shift_name, start_time, end_time FROM shift_assignments GROUP BY assignment_date, shift_name, start_time, end_time";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String date = rs.getDate("assignment_date").toString();
                String shift = rs.getString("shift_name");
                String startTime = rs.getString("start_time");
                String endTime = rs.getString("end_time");

                if (startTime != null && endTime != null) {
                    shiftTimesCache.computeIfAbsent(date, k -> new HashMap<>())
                            .put(shift, new ShiftApp.ShiftTimes(startTime, endTime));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to load shift times: " + e.getMessage());
        }
        return shiftTimesCache;
    }

    public int getTotalAssignments() {
        String sql = "SELECT COUNT(*) as total FROM shift_assignments";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            System.err.println("❌ Count failed: " + e.getMessage());
        }

        return 0;
    }
}