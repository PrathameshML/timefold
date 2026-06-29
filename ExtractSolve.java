import java.nio.file.*;
import java.util.*;

public class ExtractSolve {
    public static void main(String[] args) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("src/main/java/com/scheduler/ShiftApp.java"));
        
        // ensure last line is }
        while (lines.size() > 0 && lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (!lines.get(lines.size() - 1).equals("}")) {
            lines.add("}");
        }
        
        int solveStart = 11274; // 0-based index for line 11275
        int solveEnd = 11906;   // 0-based index for line 11907
        
        List<String> v3 = new ArrayList<>();
        
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
