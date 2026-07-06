package com.scheduler.model;

import java.util.List;

public class RatingRequirement {
    private String roleName;
    private List<Integer> allowedRatings;

    public RatingRequirement() {}

    public RatingRequirement(String roleName, List<Integer> allowedRatings) {
        this.roleName = roleName;
        this.allowedRatings = allowedRatings;
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
