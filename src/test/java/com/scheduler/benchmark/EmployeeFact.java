package com.scheduler.benchmark;

import java.util.Objects;

/**
 * Problem fact: employee data (rate, rating) for the inverted model.
 * NOT a planning entity — just a fact the constraints read from.
 */
public class EmployeeFact {
    private String id;
    private String name;
    private String role;
    private double hourlyWage;
    private int rating;

    public EmployeeFact() {}

    public EmployeeFact(String id, String name, String role, double hourlyWage, int rating) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.hourlyWage = hourlyWage;
        this.rating = rating;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public double getHourlyWage() { return hourlyWage; }
    public int getRating() { return rating; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmployeeFact)) return false;
        return Objects.equals(id, ((EmployeeFact) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
