package com.scheduler;

import com.scheduler.service.SolverService;
import com.scheduler.service.DatabaseService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jboss.logging.Logger;

import java.util.*;

import org.junit.jupiter.api.Tag;

/**
 * Diagnostic test to answer Claude's questions about wage distribution
 * and constraint scoring behavior.
 */
@QuarkusTest
@Tag("diagnostic")
public class WageDistributionDiagnosticTest {

    private static final Logger LOG = Logger.getLogger(WageDistributionDiagnosticTest.class);

    @Inject
    SolverService solverService;

    @Inject
    DatabaseService databaseService;

    @BeforeEach
    public void cleanDatabase() {
        databaseService.clearAllAssignments();
    }

    @Test
    public void analyzeWageDistribution() {
        LOG.info("╔══════════════════════════════════════════════════════════════════╗");
        LOG.info("║  DIAGNOSTIC: Wage Distribution & Constraint Scoring Analysis     ║");
        LOG.info("╚══════════════════════════════════════════════════════════════════╝");

        // Use our benchmark's exact employee generation logic (seed=42)
        String[] roles = {"Operator", "Supervisor", "Technician", "Helper"};
        int totalEmployees = 300;
        Random rng = new Random(42);

        // Collect wages per role
        Map<String, List<Double>> wagesPerRole = new LinkedHashMap<>();
        for (String r : roles) wagesPerRole.put(r, new ArrayList<>());

        // Collect ratings per role
        Map<String, List<Integer>> ratingsPerRole = new LinkedHashMap<>();
        for (String r : roles) ratingsPerRole.put(r, new ArrayList<>());

        for (int i = 0; i < totalEmployees; i++) {
            String role = roles[i % roles.length];
            int rating = 1 + rng.nextInt(5); // 1-5
            int hourlyWage = 15 + rng.nextInt(36); // 15-50
            wagesPerRole.get(role).add((double) hourlyWage);
            ratingsPerRole.get(role).add(rating);
        }

        LOG.info("");
        LOG.info("=== Q1: Wage/AvgWage Ratio Range Per Role ===");
        LOG.info(String.format("%-15s | %-8s | %-8s | %-8s | %-8s | %-12s | %-12s | %-12s",
                "Role", "Count", "MinWage", "MaxWage", "AvgWage", "MinRatio", "MaxRatio", "StdDev"));

        for (Map.Entry<String, List<Double>> entry : wagesPerRole.entrySet()) {
            String role = entry.getKey();
            List<Double> wages = entry.getValue();

            double sum = wages.stream().mapToDouble(d -> d).sum();
            double avg = sum / wages.size();
            double min = wages.stream().mapToDouble(d -> d).min().orElse(0);
            double max = wages.stream().mapToDouble(d -> d).max().orElse(0);

            double minRatio = min / avg;
            double maxRatio = max / avg;

            // StdDev calculation
            double variance = wages.stream().mapToDouble(d -> Math.pow(d - avg, 2)).sum() / wages.size();
            double stdDev = Math.sqrt(variance);

            LOG.info(String.format("%-15s | %-8d | $%-7.0f | $%-7.0f | $%-7.2f | %-12.4f | %-12.4f | $%-11.2f",
                    role, wages.size(), min, max, avg, minRatio, maxRatio, stdDev));
        }

        LOG.info("");
        LOG.info("=== Q2: Is StdDev ever 0 or near-0? ===");
        for (Map.Entry<String, List<Double>> entry : wagesPerRole.entrySet()) {
            List<Double> wages = entry.getValue();
            double avg = wages.stream().mapToDouble(d -> d).average().orElse(0);
            double variance = wages.stream().mapToDouble(d -> Math.pow(d - avg, 2)).sum() / wages.size();
            double stdDev = Math.sqrt(variance);
            boolean dangerous = stdDev < 1.0;
            LOG.info("  " + entry.getKey() + ": StdDev = $" + String.format("%.2f", stdDev) + (dangerous ? " ⚠️ DANGEROUS (near zero!)" : " ✅ Safe"));
        }

        LOG.info("");
        LOG.info("=== Q3: How wageOptimization and maximizeRating combine ===");
        LOG.info("  CONFIRMED FROM CODE: Both constraints write to HardMediumSoftLongScore.ONE_SOFT.");
        LOG.info("  wageOptimization → penalizeLong(ONE_SOFT, wageRatio * 1000)");
        LOG.info("  maximizeRating   → rewardLong(ONE_SOFT, rating * 100)");
        LOG.info("  They are SUMMED DIRECTLY into the same Soft bucket.");
        LOG.info("  Net Soft = -Σ(wageRatio * 1000) + Σ(rating * 100)");
        LOG.info("");
        LOG.info("  IMBALANCE ANALYSIS:");

        // Calculate what a typical employee contributes to each constraint
        for (Map.Entry<String, List<Double>> entry : wagesPerRole.entrySet()) {
            String role = entry.getKey();
            List<Double> wages = entry.getValue();
            List<Integer> ratings = ratingsPerRole.get(role);

            double avgWage = wages.stream().mapToDouble(d -> d).average().orElse(1);
            double avgRating = ratings.stream().mapToInt(r -> r).average().orElse(1);

            // Current formula: wageRatio * 1000
            double typicalWagePenalty = (avgWage / avgWage) * 1000; // ratio=1.0 for average employee
            double cheapestWagePenalty = (wages.stream().mapToDouble(d -> d).min().orElse(15) / avgWage) * 1000;
            double expensiveWagePenalty = (wages.stream().mapToDouble(d -> d).max().orElse(50) / avgWage) * 1000;

            // Current formula: rating * 100
            double typicalRatingReward = avgRating * 100;
            double bestRatingReward = 5.0 * 100;
            double worstRatingReward = 1.0 * 100;

            LOG.info(String.format("  [%s] Wage penalty range: %,.0f to %,.0f (spread: %,.0f)",
                    role, cheapestWagePenalty, expensiveWagePenalty, expensiveWagePenalty - cheapestWagePenalty));
            LOG.info(String.format("  [%s] Rating reward range: %,.0f to %,.0f (spread: %,.0f)",
                    role, worstRatingReward, bestRatingReward, bestRatingReward - worstRatingReward));
            LOG.info(String.format("  [%s] → Wage spread / Rating spread = %.1fx (wage dominates by this factor)",
                    role, (expensiveWagePenalty - cheapestWagePenalty) / (bestRatingReward - worstRatingReward)));
        }

        LOG.info("");
        LOG.info("=== Q4: Multiplier suggestions for '2x wage priority' ===");
        LOG.info("  For wage to matter ~2x more than rating:");
        LOG.info("  We need: wageSpread ≈ 2 × ratingSpread");
        LOG.info("  Current rating spread = 400 points (rating 1→5 × 100)");
        LOG.info("  Target wage spread = 800 points");
        LOG.info("  With Z-score normalization (range roughly -2 to +2, spread ~4):");
        LOG.info("    wageMultiplier = 800 / 4 = 200");
        LOG.info("    ratingMultiplier = 100 (keep as is)");
        LOG.info("  With Min-Max normalization (range 0 to 1, spread = 1):");
        LOG.info("    wageMultiplier = 800");
        LOG.info("    ratingMultiplier = 100 (keep as is)");
    }
}
