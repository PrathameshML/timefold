
package com.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.ShiftApp;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

@ApplicationScoped
public class MySQLService {
    @Inject
    DataSource dataSource;
    private static final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public MySQLService() {
        System.out.println("\ud83d\udd04 MySQLService bean created");
    }

    @PostConstruct
    public void init() {
        System.out.println("\ud83d\udd04 MySQLService CDI initialized");
        this.initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        Connection conn = this.dataSource.getConnection();
        return conn;
    }

    public void initializeDatabase() {
        String sqlEmployees = "CREATE TABLE IF NOT EXISTS employees (id VARCHAR(50) PRIMARY KEY,name VARCHAR(100) NOT NULL,position VARCHAR(100) NULL,category VARCHAR(50) NULL,gender VARCHAR(10) NULL,hourly_wage DOUBLE NULL,performance_rating INT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)";
        String sql = "CREATE TABLE IF NOT EXISTS shift_assignments (id INT AUTO_INCREMENT PRIMARY KEY,assignment_date DATE NOT NULL,shift_name VARCHAR(50) NOT NULL,employee_id VARCHAR(50) NOT NULL,employee_name VARCHAR(100) NULL,employee_role VARCHAR(100) NULL,employee_category VARCHAR(50) NULL,gender VARCHAR(10) NULL,rating INT NULL,start_time VARCHAR(5) NULL,end_time VARCHAR(5) NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,UNIQUE KEY unique_assignment (assignment_date, shift_name, employee_id))";
        String sqlConfig = "CREATE TABLE IF NOT EXISTS system_configuration (id INT PRIMARY KEY,config_json JSON NOT NULL,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)";
        String sqlLeaves = "CREATE TABLE IF NOT EXISTS leave_requests (id VARCHAR(50) PRIMARY KEY,employee_id VARCHAR(50) NOT NULL,date DATE NOT NULL,end_date DATE NULL,reason VARCHAR(255) NULL,status VARCHAR(20) NOT NULL,coverage_assigned BOOLEAN DEFAULT FALSE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        String sqlCoverage = "CREATE TABLE IF NOT EXISTS leave_coverage_requests (id VARCHAR(50) PRIMARY KEY,leave_request_id VARCHAR(50) NULL,date DATE NOT NULL,shift VARCHAR(50) NOT NULL,role_needed VARCHAR(50) NOT NULL,requester_id VARCHAR(50) NOT NULL,status VARCHAR(20) NOT NULL,assigned_employee_id VARCHAR(50) NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        String sqlOvertime = "CREATE TABLE IF NOT EXISTS overtime_records (id VARCHAR(50) PRIMARY KEY,employee_id VARCHAR(50) NOT NULL,date DATE NOT NULL,hours DOUBLE NOT NULL,type VARCHAR(50) NULL,status VARCHAR(20) NOT NULL,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        String sqlNotifications = "CREATE TABLE IF NOT EXISTS notifications (id VARCHAR(50) PRIMARY KEY,recipient_id VARCHAR(50) NOT NULL,message VARCHAR(500) NOT NULL,type VARCHAR(50) NULL,is_read BOOLEAN DEFAULT FALSE,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        String sqlConstraintConfig = "CREATE TABLE IF NOT EXISTS constraint_config (constraint_id INT PRIMARY KEY,constraint_name VARCHAR(100) NOT NULL,description VARCHAR(500),enabled BOOLEAN DEFAULT TRUE,severity VARCHAR(10) NOT NULL DEFAULT 'HARD',parameter_value DOUBLE NULL,parameter_name VARCHAR(50) NULL,parameter_value_2 DOUBLE NULL,parameter_name_2 VARCHAR(50) NULL,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)";
        String sqlConstraintConfigV3 = "CREATE TABLE IF NOT EXISTS constraint_config_v3 (constraint_id INT PRIMARY KEY,constraint_name VARCHAR(100) NOT NULL,description VARCHAR(500),enabled BOOLEAN DEFAULT TRUE,severity VARCHAR(10) NOT NULL DEFAULT 'HARD',parameter_value DOUBLE NULL,parameter_name VARCHAR(50) NULL,parameter_value_2 DOUBLE NULL,parameter_name_2 VARCHAR(50) NULL,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();){
            stmt.execute(sqlEmployees);
            stmt.execute(sql);
            stmt.execute(sqlConfig);
            stmt.execute(sqlLeaves);
            stmt.execute(sqlCoverage);
            stmt.execute(sqlOvertime);
            stmt.execute(sqlNotifications);
            stmt.executeUpdate(sqlConstraintConfig);
            stmt.executeUpdate(sqlConstraintConfigV3);
            try {
                stmt.executeUpdate("ALTER TABLE constraint_config ADD COLUMN parameter_value_2 DOUBLE NULL");
                stmt.executeUpdate("ALTER TABLE constraint_config ADD COLUMN parameter_name_2 VARCHAR(50) NULL");
            } catch (SQLException e) {
                // Ignore, columns probably already exist
            }
            try {
                stmt.executeUpdate("UPDATE constraint_config SET parameter_name_2 = 'breakDurationMinutes' WHERE constraint_id = 9 AND parameter_name_2 IS NULL");
            } catch (SQLException e) {
                // Ignore
            }            try {
                stmt.execute("ALTER TABLE shift_assignments ADD COLUMN start_time VARCHAR(5)");
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
            try {
                stmt.execute("ALTER TABLE shift_assignments ADD COLUMN end_time VARCHAR(5)");
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
            try {
                stmt.execute("ALTER TABLE leave_requests ADD COLUMN type VARCHAR(50)");
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
            try {
                stmt.execute("ALTER TABLE overtime_records ADD COLUMN data_json JSON");
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
            try {
                stmt.execute("ALTER TABLE leave_coverage_requests ADD COLUMN data_json JSON");
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
            System.out.println("\u2705 MySQL tables ready (Expanded Schema)");
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to initialize table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void syncEmployee(String id, String name, String position, String category, String gender, double hourlyWage, int performanceRating) {
        String sql = "INSERT INTO employees (id, name, position, category, gender, hourly_wage, performance_rating) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name), position = VALUES(position), category = VALUES(category), gender = VALUES(gender), hourly_wage = VALUES(hourly_wage), performance_rating = VALUES(performance_rating)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, position);
            pstmt.setString(4, category);
            pstmt.setString(5, gender);
            pstmt.setDouble(6, hourlyWage);
            pstmt.setInt(7, performanceRating);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to sync employee: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void clearAllEmployees() {
        String sql = "DELETE FROM employees";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();){
            stmt.executeUpdate(sql);
            System.out.println("\ud83d\uddd1\ufe0f Cleared all employees");
        }
        catch (SQLException e) {
            System.err.println("\u274c Clear employees failed: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void clearAllDatabase() {
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM employees");
            stmt.executeUpdate("DELETE FROM shift_assignments");
            stmt.executeUpdate("DELETE FROM leave_requests");
            stmt.executeUpdate("DELETE FROM leave_coverage_requests");
            stmt.executeUpdate("DELETE FROM notifications");
            stmt.executeUpdate("DELETE FROM overtime_records");
            System.out.println("🗑️ CLEARED ENTIRE DATABASE (Except Configs)");
        } catch (SQLException e) {
            System.err.println("❌ Clear database failed: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void syncTimefoldAssignment(String date, String shift, String employeeId, String employeeName, String employeeRole, String employeeCategory, String gender, int rating, String startTime, String endTime) {
        System.out.println("\u26a1 TIMEFOLD SYNC - " + date + " " + shift + " " + employeeId);
        String sql = "INSERT INTO shift_assignments (assignment_date, shift_name, employee_id, employee_name, employee_role, employee_category, gender, rating, start_time, end_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE employee_name = VALUES(employee_name), employee_role = VALUES(employee_role), employee_category = VALUES(employee_category), gender = VALUES(gender), rating = VALUES(rating), start_time = VALUES(start_time), end_time = VALUES(end_time)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, 1);){
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
            System.out.println("\u2705\u2705\u2705 TIMEFOLD SYNC SUCCESS! " + employeeId);
        }
        catch (SQLException e) {
            System.err.println("\u274c TIMEFOLD SYNC FAILED: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void syncManualAssignment(String date, String shift, String employeeId, String employeeName, String gender, String startTime, String endTime) {
        System.out.println("\ud83d\udcdd MANUAL SYNC (with NULLs) - " + date + " " + shift + " " + employeeId);
        String sql = "INSERT INTO shift_assignments (assignment_date, shift_name, employee_id, employee_name, employee_role, employee_category, gender, rating, start_time, end_time) VALUES (?, ?, ?, ?, NULL, NULL, ?, NULL, ?, ?) ON DUPLICATE KEY UPDATE employee_name = VALUES(employee_name), employee_role = NULL, employee_category = NULL, gender = VALUES(gender), rating = NULL, start_time = VALUES(start_time), end_time = VALUES(end_time)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, 1);){
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
                    System.out.println("\u2705\u2705\u2705 MANUAL SYNC SUCCESS! ID: " + rs.getInt(1) + " (with NULLs)");
                } else {
                    System.out.println("\u2705\u2705\u2705 MANUAL SYNC SUCCESS (updated with NULLs)");
                }
            }
        }
        catch (SQLException e) {
            System.err.println("\u274c MANUAL SYNC FAILED: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void deleteLeaveRequest(String employeeId, String date) {
        String sql = "DELETE FROM leave_requests WHERE employee_id = ? AND date = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, employeeId);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.executeUpdate();
            System.out.println("\u2705 Leave request deleted for " + employeeId + " on " + date);
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to delete leave request: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void saveLeaveRequest(String id, String employeeId, String date, double hours, String type, String status) {
        String sql = "INSERT INTO leave_requests (id, employee_id, date, hours, type, status) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE hours = VALUES(hours), type = VALUES(type), status = VALUES(status)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, id);
            pstmt.setString(2, employeeId);
            pstmt.setDate(3, Date.valueOf(date));
            pstmt.setDouble(4, hours);
            pstmt.setString(5, type);
            pstmt.setString(6, status);
            pstmt.executeUpdate();
            System.out.println("\u2705 Leave request saved: " + id);
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to save leave request: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public Map<String, List<ShiftApp.LeaveRecord>> loadAllLeaveRequests() {
        HashMap<String, List<ShiftApp.LeaveRecord>> leaves = new HashMap<String, List<ShiftApp.LeaveRecord>>();
        String sql = "SELECT employee_id, date, type FROM leave_requests WHERE status != 'REJECTED'";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);){
            while (rs.next()) {
                String empId = rs.getString("employee_id");
                String date = rs.getDate("date").toString();
                String type = rs.getString("type");
                leaves.computeIfAbsent(empId, k -> new ArrayList<>()).add(new ShiftApp.LeaveRecord(date, type));
            }
        }
        catch (SQLException e) {
            System.err.println("\u274c Load leaves failed: " + e.getMessage());
        }
        return leaves;
    }

    public void syncAssignment(String date, String shift, String employeeId, String employeeName, String employeeRole, String employeeCategory, String gender, int rating, String startTime, String endTime) {
        this.syncTimefoldAssignment(date, shift, employeeId, employeeName, employeeRole, employeeCategory, gender, rating, startTime, endTime);
    }

    public void removeAssignment(String date, String shift, String employeeId) {
        String sql = "DELETE FROM shift_assignments WHERE assignment_date = ? AND shift_name = ? AND employee_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setString(2, shift);
            pstmt.setString(3, employeeId);
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                System.out.println("\ud83d\uddd1\ufe0f Removed: " + date + " " + shift + " " + employeeId);
            }
        }
        catch (SQLException e) {
            System.err.println("\u274c Remove failed: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void clearAssignmentsForDate(String date) {
        String sql = "DELETE FROM shift_assignments WHERE assignment_date = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setDate(1, Date.valueOf(date));
            int deleted = pstmt.executeUpdate();
            System.out.println("\ud83d\uddd1\ufe0f Cleared " + deleted + " records for " + date);
        }
        catch (SQLException e) {
            System.err.println("\u274c Clear failed: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void removeEmployee(String employeeId) {
        String sql = "DELETE FROM employees WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, employeeId);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to remove employee from MySQL: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void removeAssignmentsForEmployee(String employeeId) {
        String sql = "DELETE FROM shift_assignments WHERE employee_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, employeeId);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to remove assignments for employee from MySQL: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void clearAssignmentsForDates(List<String> dates) {
        if (dates == null || dates.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM shift_assignments WHERE assignment_date = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            for (String date : dates) {
                pstmt.setDate(1, Date.valueOf(date));
                pstmt.addBatch();
            }
            int[] results = pstmt.executeBatch();
            int totalDeleted = 0;
            for (int result : results) {
                totalDeleted += result;
            }
            System.out.println("\ud83d\uddd1\ufe0f Batch cleared " + totalDeleted + " records across " + dates.size() + " dates");
        }
        catch (SQLException e) {
            System.err.println("\u274c Batch clear failed: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void clearAllAssignments() {
        String sql = "DELETE FROM shift_assignments";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();){
            int deleted = stmt.executeUpdate(sql);
            System.out.println("\ud83d\uddd1\ufe0f Cleared ALL " + deleted + " records");
            stmt.executeUpdate("ALTER TABLE shift_assignments AUTO_INCREMENT = 1");
            System.out.println("\ud83d\udd04 Reset AUTO_INCREMENT to 1");
        }
        catch (SQLException e) {
            System.err.println("\u274c Clear all failed: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void removeAssignmentsByEmployee(String employeeId) {
        String sql = "DELETE FROM shift_assignments WHERE employee_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, employeeId);
            int deleted = pstmt.executeUpdate();
            System.out.println("\ud83d\uddd1\ufe0f Removed " + deleted + " assignments for employee: " + employeeId);
        }
        catch (SQLException e) {
            System.err.println("\u274c Remove by employee failed: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public boolean assignmentExists(String date, String shift, String employeeId) {
        String sql = "SELECT COUNT(*) as count FROM shift_assignments WHERE assignment_date = ? AND shift_name = ? AND employee_id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setString(2, shift);
            pstmt.setString(3, employeeId);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) return false;
            boolean bl = rs.getInt("count") > 0;
            return bl;
        }
        catch (SQLException e) {
            System.err.println("\u274c Check failed: " + e.getMessage());
        }
        return false;
    }

    public List<Map<String, Object>> getAssignmentsForDate(String date) {
        ArrayList<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        String sql = "SELECT * FROM shift_assignments WHERE assignment_date = ? ORDER BY shift_name, employee_id";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setDate(1, Date.valueOf(date));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                HashMap<String, Object> row = new HashMap<String, Object>();
                row.put("id", rs.getInt("id"));
                row.put("date", rs.getDate("assignment_date").toString());
                row.put("shift", rs.getString("shift_name"));
                row.put("employeeId", rs.getString("employee_id"));
                row.put("employeeName", rs.getString("employee_name"));
                row.put("employeeRole", rs.getString("employee_role"));
                row.put("employeeCategory", rs.getString("employee_category"));
                row.put("gender", rs.getString("gender"));
                row.put("rating", rs.getObject("rating"));
                results.add(row);
            }
        }
        catch (SQLException e) {
            System.err.println("\u274c Query failed: " + e.getMessage());
        }
        return results;
    }

    public void saveSystemConfig(ShiftApp.SystemConfig config) {
        String sql = "INSERT INTO system_configuration (id, config_json) VALUES (1, ?) ON DUPLICATE KEY UPDATE config_json = VALUES(config_json)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            String json = mapper.writeValueAsString((Object)config);
            pstmt.setString(1, json);
            pstmt.executeUpdate();
            System.out.println("\ud83d\udcbe Saved SystemConfig to MySQL");
        }
        catch (Exception e) {
            System.err.println("\u274c Failed to save SystemConfig: " + e.getMessage());
            throw new RuntimeException("Database save failed: " + e.getMessage(), e);
        }
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public ShiftApp.SystemConfig loadSystemConfig() {
        String sql = "SELECT config_json FROM system_configuration WHERE id = 1";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery();){
            if (!rs.next()) return null;
            String json = rs.getString("config_json");
            ShiftApp.SystemConfig config = (ShiftApp.SystemConfig)mapper.readValue(json, ShiftApp.SystemConfig.class);
            System.out.println("\ud83d\udce5 Loaded SystemConfig from MySQL");
            ShiftApp.SystemConfig systemConfig = config;
            return systemConfig;
        }
        catch (Exception e) {
            System.err.println("\u274c Failed to load SystemConfig: " + e.getMessage());
        }
        return null;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public int getTotalAssignmentCount() {
        String sql = "SELECT COUNT(*) as total FROM shift_assignments";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);){
            if (!rs.next()) return 0;
            int n = rs.getInt("total");
            return n;
        }
        catch (SQLException e) {
            System.err.println("\u274c Error getting total assignments count: " + e.getMessage());
        }
        return 0;
    }

    public Map<String, ShiftApp.EmployeeInfo> loadAllEmployees() {
        HashMap<String, ShiftApp.EmployeeInfo> employeeInfo = new HashMap<String, ShiftApp.EmployeeInfo>();
        String sql = "SELECT * FROM employees";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);){
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
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to load employees: " + e.getMessage());
        }
        return employeeInfo;
    }

    public Map<String, Map<String, List<String>>> loadAllAssignments() {
        HashMap<String, Map<String, List<String>>> shiftAssignments = new HashMap<String, Map<String, List<String>>>();
        String sql = "SELECT assignment_date, shift_name, employee_id FROM shift_assignments ORDER BY assignment_date, shift_name";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);){
            while (rs.next()) {
                String date = rs.getDate("assignment_date").toString();
                String shift = rs.getString("shift_name");
                String empId = rs.getString("employee_id");
                shiftAssignments.computeIfAbsent(date, k -> new HashMap<>()).computeIfAbsent(shift, k -> new ArrayList<>()).add(empId);
            }
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to load assignments: " + e.getMessage());
        }
        return shiftAssignments;
    }

    public Map<String, Map<String, ShiftApp.ShiftTimes>> loadAllEmployeeShiftTimes() {
        HashMap<String, Map<String, ShiftApp.ShiftTimes>> cache = new HashMap<String, Map<String, ShiftApp.ShiftTimes>>();
        String sql = "SELECT assignment_date, employee_id, start_time, end_time FROM shift_assignments";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);){
            while (rs.next()) {
                String date = rs.getDate("assignment_date").toString();
                String empId = rs.getString("employee_id");
                String startTime = rs.getString("start_time");
                String endTime = rs.getString("end_time");
                if (startTime == null || endTime == null) continue;
                cache.computeIfAbsent(date, k -> new HashMap<>()).put(empId, new ShiftApp.ShiftTimes(startTime, endTime));
            }
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to load employee shift times: " + e.getMessage());
        }
        return cache;
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public int getTotalAssignments() {
        String sql = "SELECT COUNT(*) as total FROM shift_assignments";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);){
            if (!rs.next()) return 0;
            int n = rs.getInt("total");
            return n;
        }
        catch (SQLException e) {
            System.err.println("\u274c Count failed: " + e.getMessage());
        }
        return 0;
    }

    public void saveOvertimeRecord(ShiftApp.OvertimeRecord record) {
        String sql = "INSERT INTO overtime_records (id, employee_id, date, hours, type, status, data_json) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE hours = VALUES(hours), type = VALUES(type), status = VALUES(status), data_json = VALUES(data_json)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, record.getId());
            pstmt.setString(2, record.getEmployeeId());
            pstmt.setDate(3, Date.valueOf(record.getDate()));
            pstmt.setDouble(4, record.getHours());
            pstmt.setString(5, record.getType());
            pstmt.setString(6, record.isApproved() ? "APPROVED" : "PENDING");
            pstmt.setString(7, mapper.writeValueAsString((Object)record));
            pstmt.executeUpdate();
            System.out.println("\u2705 Overtime record saved: " + record.getId());
        }
        catch (Exception e) {
            System.err.println("\u274c Failed to save overtime record: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public Map<String, ShiftApp.OvertimeRecord> loadAllOvertimeRecords() {
        HashMap<String, ShiftApp.OvertimeRecord> records = new HashMap<String, ShiftApp.OvertimeRecord>();
        String sql = "SELECT data_json FROM overtime_records";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);){
            while (rs.next()) {
                String json = rs.getString("data_json");
                if (json == null) continue;
                ShiftApp.OvertimeRecord record = (ShiftApp.OvertimeRecord)mapper.readValue(json, ShiftApp.OvertimeRecord.class);
                records.put(record.getId(), record);
            }
        }
        catch (Exception e) {
            System.err.println("\u274c Failed to load overtime records: " + e.getMessage());
        }
        return records;
    }

    public void deleteOvertimeRecord(String id) {
        String sql = "DELETE FROM overtime_records WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, id);
            pstmt.executeUpdate();
            System.out.println("\u2705 Overtime record deleted: " + id);
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to delete overtime record: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void saveCoverageRequest(ShiftApp.LeaveCoverageRequest request) {
        String sql = "INSERT INTO leave_coverage_requests (id, leave_request_id, date, shift, role_needed, requester_id, status, assigned_employee_id, data_json) VALUES (?, NULL, ?, ?, ?, ?, ?, NULL, ?) ON DUPLICATE KEY UPDATE status = VALUES(status), data_json = VALUES(data_json)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, request.getId());
            pstmt.setDate(2, Date.valueOf(request.getLeaveDate()));
            pstmt.setString(3, request.getShiftName());
            pstmt.setString(4, request.getDepartment() != null ? request.getDepartment() : "ANY");
            pstmt.setString(5, request.getManagerId() != null ? request.getManagerId() : "SYSTEM");
            pstmt.setString(6, request.getStatus());
            pstmt.setString(7, mapper.writeValueAsString((Object)request));
            pstmt.executeUpdate();
            System.out.println("\u2705 Coverage request saved: " + request.getId());
        }
        catch (Exception e) {
            System.err.println("\u274c Failed to save coverage request: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public Map<String, ShiftApp.LeaveCoverageRequest> loadAllCoverageRequests() {
        HashMap<String, ShiftApp.LeaveCoverageRequest> requests = new HashMap<String, ShiftApp.LeaveCoverageRequest>();
        String sql = "SELECT data_json FROM leave_coverage_requests";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);){
            while (rs.next()) {
                String json = rs.getString("data_json");
                if (json == null) continue;
                ShiftApp.LeaveCoverageRequest req = (ShiftApp.LeaveCoverageRequest)mapper.readValue(json, ShiftApp.LeaveCoverageRequest.class);
                requests.put(req.getId(), req);
            }
        }
        catch (Exception e) {
            System.err.println("\u274c Failed to load coverage requests: " + e.getMessage());
        }
        return requests;
    }

    public void deleteCoverageRequest(String id) {
        String sql = "DELETE FROM leave_coverage_requests WHERE id = ?";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, id);
            pstmt.executeUpdate();
            System.out.println("\u2705 Coverage request deleted: " + id);
        }
        catch (SQLException e) {
            System.err.println("\u274c Failed to delete coverage request: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void saveNotification(ShiftApp.Notification notification) {
        String sql = "INSERT INTO notifications (id, recipient_id, message, type, is_read, created_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE is_read = VALUES(is_read)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);){
            pstmt.setString(1, notification.getId());
            pstmt.setString(2, notification.getRecipientId());
            pstmt.setString(3, notification.getMessage());
            pstmt.setString(4, notification.getType());
            pstmt.setBoolean(5, notification.isRead());
            pstmt.setTimestamp(6, Timestamp.valueOf(notification.getTimestamp()));
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            System.err.println("\u274c Failed to save notification: " + e.getMessage());
        }
    }

    public Map<String, ShiftApp.Notification> loadAllNotifications() {
        HashMap<String, ShiftApp.Notification> notifications = new HashMap<String, ShiftApp.Notification>();
        String sql = "SELECT id, recipient_id, message, type, is_read, created_at FROM notifications";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);){
            while (rs.next()) {
                ShiftApp.Notification notif = new ShiftApp.Notification(rs.getString("id"), rs.getString("recipient_id"), rs.getString("type"), rs.getString("message"), rs.getTimestamp("created_at").toLocalDateTime());
                notif.setRead(rs.getBoolean("is_read"));
                notifications.put(notif.getId(), notif);
            }
        }
        catch (Exception e) {
            System.err.println("\u274c Failed to load notifications: " + e.getMessage());
        }
        return notifications;
    }

    // ============ CONSTRAINT CONFIG METHODS ============

    public List<ShiftApp.ConstraintConfig> loadAllConstraintConfigs() {
        List<ShiftApp.ConstraintConfig> configs = new ArrayList<>();
        String sql = "SELECT constraint_id, constraint_name, description, enabled, severity, parameter_value, parameter_name, parameter_value_2, parameter_name_2 FROM constraint_config ORDER BY constraint_id";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ShiftApp.ConstraintConfig config = new ShiftApp.ConstraintConfig();
                config.setConstraintId(rs.getInt("constraint_id"));
                config.setConstraintName(rs.getString("constraint_name"));
                config.setDescription(rs.getString("description"));
                config.setEnabled(rs.getBoolean("enabled"));
                config.setSeverity(rs.getString("severity"));
                double paramVal = rs.getDouble("parameter_value");
                config.setParameterValue(rs.wasNull() ? null : paramVal);
                config.setParameterName(rs.getString("parameter_name"));
                
                double paramVal2 = rs.getDouble("parameter_value_2");
                config.setParameterValue2(rs.wasNull() ? null : paramVal2);
                config.setParameterName2(rs.getString("parameter_name_2"));
                
                configs.add(config);
            }
            System.out.println("📋 Loaded " + configs.size() + " constraint configs from MySQL");
        } catch (SQLException e) {
            System.err.println("❌ Failed to load constraint configs: " + e.getMessage());
        }
        return configs;
    }

    public void saveConstraintConfig(ShiftApp.ConstraintConfig config) {
        String sql = "INSERT INTO constraint_config (constraint_id, constraint_name, description, enabled, severity, parameter_value, parameter_name, parameter_value_2, parameter_name_2) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "constraint_name = VALUES(constraint_name), description = VALUES(description), " +
                "enabled = VALUES(enabled), severity = VALUES(severity), " +
                "parameter_value = VALUES(parameter_value), parameter_name = VALUES(parameter_name), " +
                "parameter_value_2 = VALUES(parameter_value_2), parameter_name_2 = VALUES(parameter_name_2)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, config.getConstraintId());
            pstmt.setString(2, config.getConstraintName());
            pstmt.setString(3, config.getDescription());
            pstmt.setBoolean(4, config.isEnabled());
            pstmt.setString(5, config.getSeverity());
            if (config.getParameterValue() != null) {
                pstmt.setDouble(6, config.getParameterValue());
            } else {
                pstmt.setNull(6, java.sql.Types.DOUBLE);
            }
            pstmt.setString(7, config.getParameterName());
            
            if (config.getParameterValue2() != null) {
                pstmt.setDouble(8, config.getParameterValue2());
            } else {
                pstmt.setNull(8, java.sql.Types.DOUBLE);
            }
            pstmt.setString(9, config.getParameterName2());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Failed to save constraint config: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void insertDefaultConstraints(List<ShiftApp.ConstraintConfig> defaults) {
        for (ShiftApp.ConstraintConfig config : defaults) {
            saveConstraintConfig(config);
        }
        System.out.println("✅ Inserted " + defaults.size() + " default constraint configs");
    }

    public List<ShiftApp.ConstraintConfig> loadAllConstraintConfigsV3() {
        List<ShiftApp.ConstraintConfig> configs = new ArrayList<>();
        String sql = "SELECT constraint_id, constraint_name, description, enabled, severity, parameter_value, parameter_name, parameter_value_2, parameter_name_2 FROM constraint_config_v3 ORDER BY constraint_id";
        try (Connection conn = this.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ShiftApp.ConstraintConfig config = new ShiftApp.ConstraintConfig();
                config.setConstraintId(rs.getInt("constraint_id"));
                config.setConstraintName(rs.getString("constraint_name"));
                config.setDescription(rs.getString("description"));
                config.setEnabled(rs.getBoolean("enabled"));
                config.setSeverity(rs.getString("severity"));
                double paramVal = rs.getDouble("parameter_value");
                config.setParameterValue(rs.wasNull() ? null : paramVal);
                config.setParameterName(rs.getString("parameter_name"));
                
                double paramVal2 = rs.getDouble("parameter_value_2");
                config.setParameterValue2(rs.wasNull() ? null : paramVal2);
                config.setParameterName2(rs.getString("parameter_name_2"));
                
                configs.add(config);
            }
        } catch (SQLException e) {
            System.err.println("❌ Failed to load V3 constraint configs: " + e.getMessage());
        }
        return configs;
    }

    public void saveConstraintConfigV3(ShiftApp.ConstraintConfig config) {
        String sql = "INSERT INTO constraint_config_v3 (constraint_id, constraint_name, description, enabled, severity, parameter_value, parameter_name, parameter_value_2, parameter_name_2) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "constraint_name = VALUES(constraint_name), description = VALUES(description), " +
                "enabled = VALUES(enabled), severity = VALUES(severity), " +
                "parameter_value = VALUES(parameter_value), parameter_name = VALUES(parameter_name), " +
                "parameter_value_2 = VALUES(parameter_value_2), parameter_name_2 = VALUES(parameter_name_2)";
        try (Connection conn = this.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, config.getConstraintId());
            pstmt.setString(2, config.getConstraintName());
            pstmt.setString(3, config.getDescription());
            pstmt.setBoolean(4, config.isEnabled());
            pstmt.setString(5, config.getSeverity());
            if (config.getParameterValue() != null) {
                pstmt.setDouble(6, config.getParameterValue());
            } else {
                pstmt.setNull(6, java.sql.Types.DOUBLE);
            }
            pstmt.setString(7, config.getParameterName());
            
            if (config.getParameterValue2() != null) {
                pstmt.setDouble(8, config.getParameterValue2());
            } else {
                pstmt.setNull(8, java.sql.Types.DOUBLE);
            }
            pstmt.setString(9, config.getParameterName2());
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Failed to save V3 constraint config: " + e.getMessage());
        }
    }

    public void insertDefaultConstraintsV3(List<ShiftApp.ConstraintConfig> defaults) {
        for (ShiftApp.ConstraintConfig config : defaults) {
            saveConstraintConfigV3(config);
        }
        System.out.println("✅ Inserted " + defaults.size() + " default V3 constraint configs");
    }
}
