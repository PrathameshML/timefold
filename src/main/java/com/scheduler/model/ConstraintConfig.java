package com.scheduler.model;

public class ConstraintConfig {
    private int constraintId;
    private String constraintName;
    private String description;
    private boolean enabled = true;
    private String severity = "HARD";
    private Double parameterValue;
    private String parameterName;
    private Double parameterValue2;
    private String parameterName2;

    public ConstraintConfig() {}

    public ConstraintConfig(int id, String name, String desc, String severity, Double value, String paramName) {
        this(id, name, desc, severity, value, paramName, null, null);
    }

    public ConstraintConfig(int id, String name, String desc, String severity, Double value, String paramName, Double value2, String paramName2) {
        this.constraintId = id;
        this.constraintName = name;
        this.description = desc;
        this.severity = severity;
        this.parameterValue = value;
        this.parameterName = paramName;
        this.parameterValue2 = value2;
        this.parameterName2 = paramName2;
    }

    public int getConstraintId() { return constraintId; }
    public void setConstraintId(int constraintId) { this.constraintId = constraintId; }
    public String getConstraintName() { return constraintName; }
    public void setConstraintName(String constraintName) { this.constraintName = constraintName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Double getParameterValue() { return parameterValue; }
    public void setParameterValue(Double parameterValue) { this.parameterValue = parameterValue; }
    public String getParameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = parameterName; }
    public Double getParameterValue2() { return parameterValue2; }
    public void setParameterValue2(Double parameterValue2) { this.parameterValue2 = parameterValue2; }
    public String getParameterName2() { return parameterName2; }
    public void setParameterName2(String parameterName2) { this.parameterName2 = parameterName2; }
}
