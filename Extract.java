import java.nio.file.*;
import java.util.*;

public class Extract {
    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("src/main/java/com/scheduler/ShiftApp.java"));
        
        // ensure last line is }
        while (lines.size() > 0 && lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (!lines.get(lines.size() - 1).equals("}")) {
            lines.add("}");
        }
        
        int empStart = -1, empEnd = -1, schedStart = -1, schedEnd = -1, solveStart = -1, solveEnd = -1;
        
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("public static class EmployeeAssignment {")) empStart = i;
            if (lines.get(i).contains("public static class ShiftSchedule {")) empEnd = i - 1;
            if (lines.get(i).contains("public static class ShiftScheduleV2 {")) schedStart = i;
            if (lines.get(i).contains("public static class ShiftConstraintsV2 implements ConstraintProvider {")) schedEnd = i - 1;
            if (lines.get(i).contains("private Map<String, Object> solveShiftV2")) solveStart = i;
            if (lines.get(i).contains("// ============ POST /shifts/generate-all ============")) solveEnd = i - 1;
        }
        
        System.out.println(empStart + " " + empEnd + " " + schedStart + " " + schedEnd + " " + solveStart + " " + solveEnd);
        
        List<String> v3 = new ArrayList<>();
        
        // EMP
        for (int i = empStart; i <= empEnd; i++) {
            v3.add(lines.get(i).replace("EmployeeAssignment", "EmployeeAssignmentV3").replace("Scheduler.EmployeeAssignmentV3", "EmployeeAssignmentV3"));
        }
        v3.add("");
        
        // SCHED
        for (int i = schedStart; i <= schedEnd; i++) {
            v3.add(lines.get(i).replace("ShiftScheduleV2", "ShiftScheduleV3").replace("EmployeeAssignment", "EmployeeAssignmentV3").replace("Scheduler.EmployeeAssignmentV3", "EmployeeAssignmentV3"));
        }
        v3.add("");
        
        // CONSTRAINTS
        v3.addAll(Files.readAllLines(Paths.get("v3_constraints.txt")));
        v3.add("");
        
        // ENDPOINTS
        v3.addAll(Files.readAllLines(Paths.get("v3_endpoints.txt")));
        v3.add("");
        
        // SOLVE
        for (int i = solveStart; i <= solveEnd; i++) {
            String line = lines.get(i).replace("solveShiftV2", "solveShiftV3")
                                      .replace("ShiftScheduleV2", "ShiftScheduleV3")
                                      .replace("EmployeeAssignment", "EmployeeAssignmentV3")
                                      .replace("Scheduler.EmployeeAssignmentV3", "EmployeeAssignmentV3")
                                      .replace("ShiftConstraintsV2", "ShiftConstraintsV3");
            if (line.contains("ShiftConstraintsV3.setConfiguration")) {
                v3.add("        String optimization = input.containsKey(\"optimization\") ? (String) input.get(\"optimization\") : \"both\";");
                v3.add("        optimization = optimization.toLowerCase();");
                v3.add("        List<ConstraintConfig> runConstraints = new ArrayList<>();");
                v3.add("        for (ConstraintConfig cc : constraintConfigsV3) {");
                v3.add("            ConstraintConfig copy = new ConstraintConfig(cc.getConstraintId(), cc.getConstraintName(), cc.getDescription(), cc.getSeverity(), cc.getParameterValue(), cc.getParameterName());");
                v3.add("            copy.setEnabled(cc.isEnabled());");
                v3.add("            if (optimization.equals(\"cost\") && copy.getConstraintName().equals(\"v3MaximizeRating\")) copy.setEnabled(false);");
                v3.add("            else if (optimization.equals(\"quality\") && copy.getConstraintName().equals(\"v3WageOptimization\")) copy.setEnabled(false);");
                v3.add("            runConstraints.add(copy);");
                v3.add("        }");
                v3.add("        ShiftConstraintsV3.setConfiguration(roleLimits, ratingRequirements, requiredSkillsMap, runConstraints, averageWage);");
            } else if (line.contains("ShiftConstraintsV3.clearConfiguration")) {
                v3.add("        ShiftConstraintsV3.clearConfiguration();");
            } else {
                v3.add(line);
            }
        }
        
        lines.addAll(lines.size() - 1, v3); // append right before the last closing brace
        Files.write(Paths.get("src/main/java/com/scheduler/ShiftApp.java"), lines);
        System.out.println("DONE");
    }
}
