package com.scheduler.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Problem Fact representing an eligible employee's metadata.
 *
 * Used by the slot-based constraint provider (SlotConstraintProvider) to
 * join against ShiftSlot entities and access wage, rating, category, gender.
 *
 * Used ONLY by the /shifts/assign-global-v2 endpoint.
 * V1/V2 endpoints embed employee data directly on EmployeeAssignment.
 */
public class EmployeeFact implements Serializable {
    private String id;
    private String name;
    private String role;          // "Developer", "Nurse", etc.
    private double hourlyWage;
    private int rating;
    private String category;      // "Permanent" / "Contract"
    private String gender;

    public EmployeeFact() {}

    public EmployeeFact(String id, String name, String role,
                        double hourlyWage, int rating,
                        String category, String gender) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.hourlyWage = hourlyWage;
        this.rating = rating;
        this.category = category;
        this.gender = gender;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public double getHourlyWage() { return hourlyWage; }
    public void setHourlyWage(double hourlyWage) { this.hourlyWage = hourlyWage; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeFact that = (EmployeeFact) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeeFact{id='" + id + "', name='" + name + "', role='" + role + "'}";
    }
}
