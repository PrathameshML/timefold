package com.scheduler.model;

import java.util.List;

public class RatingRequirement {
    private String shiftName;  // null for single-shift solvers (V2), set for global
    private String roleName;
    private List<Integer> allowedRatings;

    public RatingRequirement() {}

    /** Backward-compatible constructor for single-shift solvers (solveShiftV2). */
    public RatingRequirement(String roleName, List<Integer> allowedRatings) {
        this.roleName = roleName;
        this.allowedRatings = allowedRatings;
    }

    /** Shift-scoped constructor for global solver (solveGlobalV2). */
    public RatingRequirement(String shiftName, String roleName, List<Integer> allowedRatings) {
        this.shiftName = shiftName;
        this.roleName = roleName;
        this.allowedRatings = allowedRatings;
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public List<Integer> getAllowedRatings() {
        return allowedRatings;
    }

    public void setAllowedRatings(List<Integer> allowedRatings) {
        this.allowedRatings = allowedRatings;
    }
}
