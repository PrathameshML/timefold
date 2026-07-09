package com.scheduler.service;

import com.scheduler.model.ConstraintConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DatabaseService {

    private static final Logger LOG = Logger.getLogger(DatabaseService.class);

    @Inject
    DataSource dataSource;

    @PostConstruct
    public void init() {
        LOG.debug("Initializing V3 DatabaseService");
        initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void initializeDatabase() {
        String sqlAssignments = "CREATE TABLE IF NOT EXISTS shift_assignments (" +
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
                "UNIQUE KEY unique_assignment (assignment_date, shift_name, employee_id))";

        String sqlConstraintConfig = "CREATE TABLE IF NOT EXISTS constraint_config (" +
                "constraint_id INT PRIMARY KEY," +
                "constraint_name VARCHAR(100) NOT NULL," +
                "description VARCHAR(500)," +
                "enabled BOOLEAN DEFAULT TRUE," +
                "severity VARCHAR(10) NOT NULL DEFAULT 'HARD'," +
                "parameter_value DOUBLE NULL," +
                "parameter_name VARCHAR(50) NULL," +
                "parameter_value_2 DOUBLE NULL," +
                "parameter_name_2 VARCHAR(50) NULL," +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlAssignments);
            stmt.executeUpdate(sqlConstraintConfig);
            
            // Add columns safely if migrating from older versions
            try { stmt.execute("ALTER TABLE shift_assignments ADD COLUMN start_time VARCHAR(5)"); } catch (SQLException e) {}
            try { stmt.execute("ALTER TABLE shift_assignments ADD COLUMN end_time VARCHAR(5)"); } catch (SQLException e) {}
            
            LOG.debug("V3 Database tables ready");
        } catch (SQLException e) {
            LOG.error("Failed to initialize V3 tables: " + e.getMessage(), e);
        }
    }

    // --- ASSIGNMENTS ---

    public void syncAssignment(String date, String shift, String employeeId, String employeeName, 
                               String employeeRole, String employeeCategory, String gender, 
                               int rating, String startTime, String endTime) {
        String sql = "INSERT INTO shift_assignments (assignment_date, shift_name, employee_id, employee_name, employee_role, employee_category, gender, rating, start_time, end_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE employee_name = VALUES(employee_name), employee_role = VALUES(employee_role), " +
                "employee_category = VALUES(employee_category), gender = VALUES(gender), rating = VALUES(rating), " +
                "start_time = VALUES(start_time), end_time = VALUES(end_time)";
        
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
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
        } catch (SQLException e) {
            LOG.error("Failed to sync assignment: " + e.getMessage(), e);
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void clearAllAssignments() {
        String sql = "DELETE FROM shift_assignments";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            int deleted = stmt.executeUpdate(sql);
            stmt.executeUpdate("ALTER TABLE shift_assignments AUTO_INCREMENT = 1");
            LOG.debug("Cleared ALL " + deleted + " records");
        } catch (SQLException e) {
            LOG.error("Clear all failed: " + e.getMessage(), e);
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public void clearAssignmentsForDate(String date) {
        String sql = "DELETE FROM shift_assignments WHERE assignment_date = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, Date.valueOf(date));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Clear for date failed: " + e.getMessage(), e);
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public int clearAssignmentsWithFilters(List<String> dates, List<String> shifts, List<String> employeeIds) {
        StringBuilder sql = new StringBuilder("DELETE FROM shift_assignments WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (dates != null && !dates.isEmpty()) {
            sql.append(" AND assignment_date IN (");
            for (int i = 0; i < dates.size(); i++) {
                sql.append(i == 0 ? "?" : ", ?");
                params.add(Date.valueOf(dates.get(i)));
            }
            sql.append(")");
        }

        if (shifts != null && !shifts.isEmpty()) {
            sql.append(" AND shift_name IN (");
            for (int i = 0; i < shifts.size(); i++) {
                sql.append(i == 0 ? "?" : ", ?");
                params.add(shifts.get(i));
            }
            sql.append(")");
        }

        if (employeeIds != null && !employeeIds.isEmpty()) {
            sql.append(" AND employee_id IN (");
            for (int i = 0; i < employeeIds.size(); i++) {
                sql.append(i == 0 ? "?" : ", ?");
                params.add(employeeIds.get(i));
            }
            sql.append(")");
        }

        // If no filters provided, prevent accidental clear-all (use clearAllAssignments instead)
        if (params.isEmpty()) {
            throw new IllegalArgumentException("Must provide at least one filter (date, shift, or employeeId) to clear specific assignments.");
        }

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i) instanceof Date) {
                    pstmt.setDate(i + 1, (Date) params.get(i));
                } else {
                    pstmt.setString(i + 1, (String) params.get(i));
                }
            }
            int deletedCount = pstmt.executeUpdate();
            LOG.debug("Cleared " + deletedCount + " assignments using filters.");
            return deletedCount;
        } catch (SQLException e) {
            LOG.error("Failed to clear assignments with filters: " + e.getMessage(), e);
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches all assignments for a specific date across all shifts.
     * Returns: Map<employeeId, shiftName>
     */
    public Map<String, String> loadAssignmentsForDate(String date) {
        Map<String, String> assignments = new HashMap<>();
        String sql = "SELECT employee_id, shift_name FROM shift_assignments WHERE assignment_date = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, Date.valueOf(date));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    assignments.put(rs.getString("employee_id"), rs.getString("shift_name"));
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to load assignments for date: " + date, e);
        }
        return assignments;
    }

    // --- CONSTRAINTS ---

    public List<ConstraintConfig> loadAllConstraintConfigs() {
        List<ConstraintConfig> configs = new ArrayList<>();
        String sql = "SELECT constraint_id, constraint_name, description, enabled, severity, " +
                "parameter_value, parameter_name, parameter_value_2, parameter_name_2 " +
                "FROM constraint_config ORDER BY constraint_id";
        
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ConstraintConfig config = new ConstraintConfig();
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
            LOG.error("Failed to load constraint configs: " + e.getMessage(), e);
        }
        return configs;
    }

    public void saveConstraintConfig(ConstraintConfig config) {
        String sql = "INSERT INTO constraint_config (constraint_id, constraint_name, description, enabled, " +
                "severity, parameter_value, parameter_name, parameter_value_2, parameter_name_2) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "constraint_name = VALUES(constraint_name), description = VALUES(description), " +
                "enabled = VALUES(enabled), severity = VALUES(severity), " +
                "parameter_value = VALUES(parameter_value), parameter_name = VALUES(parameter_name), " +
                "parameter_value_2 = VALUES(parameter_value_2), parameter_name_2 = VALUES(parameter_name_2)";
        
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, config.getConstraintId());
            pstmt.setString(2, config.getConstraintName());
            pstmt.setString(3, config.getDescription());
            pstmt.setBoolean(4, config.isEnabled());
            pstmt.setString(5, config.getSeverity());
            
            if (config.getParameterValue() != null) pstmt.setDouble(6, config.getParameterValue());
            else pstmt.setNull(6, Types.DOUBLE);
            
            pstmt.setString(7, config.getParameterName());
            
            if (config.getParameterValue2() != null) pstmt.setDouble(8, config.getParameterValue2());
            else pstmt.setNull(8, Types.DOUBLE);
            
            pstmt.setString(9, config.getParameterName2());
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to save constraint config: " + e.getMessage(), e);
        }
    }

    public void insertDefaultConstraints(List<ConstraintConfig> defaults) {
        for (ConstraintConfig config : defaults) {
            saveConstraintConfig(config);
        }
        LOG.debug("Inserted " + defaults.size() + " default constraint configs");
    }
}
