package com.scheduler.model;

import java.util.Map;

public class WageContext {
    private Map<String, Double> averageWagePerRole;

    public WageContext() {}

    public WageContext(Map<String, Double> averageWagePerRole) {
        this.averageWagePerRole = averageWagePerRole;
    }

    public Map<String, Double> getAverageWagePerRole() {
        return averageWagePerRole;
    }

    public void setAverageWagePerRole(Map<String, Double> averageWagePerRole) {
        this.averageWagePerRole = averageWagePerRole;
    }
}
