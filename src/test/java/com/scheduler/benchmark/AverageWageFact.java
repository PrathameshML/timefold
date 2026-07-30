package com.scheduler.benchmark;

/**
 * Problem fact providing the average wage for wage normalization.
 * Equivalent to WageContext in the production model.
 */
public class AverageWageFact {
    private double averageWage;

    public AverageWageFact() {}

    public AverageWageFact(double averageWage) {
        this.averageWage = averageWage;
    }

    public double getAverageWage() { return averageWage; }
}
