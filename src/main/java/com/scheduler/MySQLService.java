package com.scheduler;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MySQLService {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/Suyash_mysql?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "root1234";

    public MySQLService() {
        System.out.println("🔄 MySQLService initialized");
        initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            System.out.println("✅ MySQL connection established");
            return conn;
        } catch (ClassNotFoundException e) {
            System.err.println("❌ MySQL JDBC Driver not found");
            e.printStackTrace();
            throw new SQLException("MySQL Driver not found", e);
        }
    }

    public void initializeDatabase() {
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
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY unique_assignment (assignment_date, shift_name, employee_id)" +
                ")";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            System.out.println("✅ MySQL table ready");

        } catch (SQLException e) {
            System.err.println("❌ Failed to initialize table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * SYNC FOR TIMEFOLD - All fields provided
     */
    public void syncTimefoldAssignment(String date, String shift, String employeeId,
                                       String employeeName, String employeeRole,
                                       String employeeCategory, String gender, int rating) {

        System.out.println("⚡ TIMEFOLD SYNC - " + date + " " + shift + " " + employeeId);

        String sql = "INSERT INTO shift_assignments " +
                "(assignment_date, shift_name, employee_id, employee_name, " +
                "employee_role, employee_category, gender, rating) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "employee_name = VALUES(employee_name), " +
                "employee_role = VALUES(employee_role), " +
                "employee_category = VALUES(employee_category), " +
                "gender = VALUES(gender), " +
                "rating = VALUES(rating)";

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
                                     String employeeName, String gender) {

        System.out.println("📝 MANUAL SYNC (with NULLs) - " + date + " " + shift + " " + employeeId);

        String sql = "INSERT INTO shift_assignments " +
                "(assignment_date, shift_name, employee_id, employee_name, " +
                "employee_role, employee_category, gender, rating) " +
                "VALUES (?, ?, ?, ?, NULL, NULL, ?, NULL) " +
                "ON DUPLICATE KEY UPDATE " +
                "employee_name = VALUES(employee_name), " +
                "employee_role = NULL, " +
                "employee_category = NULL, " +
                "gender = VALUES(gender), " +
                "rating = NULL";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Set ONLY the 5 parameters we have (the NULLs are hardcoded in SQL)
            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setString(2, shift);
            pstmt.setString(3, employeeId);
            pstmt.setString(4, employeeName != null ? employeeName : "Unknown");
            pstmt.setString(5, gender != null ? gender : "Unknown");
            // Note: We only set 5 parameters because employee_role, employee_category, and rating are hardcoded as NULL

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
                               String employeeCategory, String gender, int rating) {
        syncTimefoldAssignment(date, shift, employeeId, employeeName,
                employeeRole, employeeCategory, gender, rating);
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
            System.err.println("❌ Count failed: " + e.getMessage());
        }

        return 0;
    }
}