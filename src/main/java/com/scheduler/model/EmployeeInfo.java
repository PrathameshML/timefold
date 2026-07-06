package com.scheduler.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

/**
 * Ultra-lean EmployeeInfo class that holds exactly what is needed for V3 Shift assignment
 * Based on the input JSON from the request.
 */
public class EmployeeInfo {
    private String id;
    private String name;
    private String category;
    private String gender;
    private double hourlyWage;
    private Set<String> skills = new HashSet<>();
    private String department;
    private String position;
    private int performanceRating;
    
    public EmployeeInfo() {}

    public EmployeeInfo(String id, String name, String category, String gender,
                        double hourlyWage, String department, String position) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.gender = gender;
        this.hourlyWage = hourlyWage;
        this.department = department;
        this.position = position;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public double getHourlyWage() { return hourlyWage; }
    public void setHourlyWage(double hourlyWage) { this.hourlyWage = hourlyWage; }

    public Set<String> getSkills() { return skills; }
    public void setSkills(Set<String> skills) { this.skills = skills; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    @JsonProperty("performanceRating")
    public int getPerformanceRating() { return performanceRating; }
    public void setPerformanceRating(int rating) { this.performanceRating = Math.max(1, Math.min(5, rating)); }

    public void addSkill(String skill) { skills.add(skill); }
}
