package com.scheduler.model;

public class RoleRequirement {
    private String roleName;
    private int maxWorkers;

    public RoleRequirement() {}

    public RoleRequirement(String roleName, int maxWorkers) {
        this.roleName = roleName;
        this.maxWorkers = maxWorkers;
    }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public int getMaxWorkers() { return maxWorkers; }
    public void setMaxWorkers(int maxWorkers) { this.maxWorkers = maxWorkers; }
}
