package com.scheduler;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.scheduler.model.*;
import com.scheduler.solver.ShiftConstraintProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

/**
 * ConstraintVerifier unit tests for the global-only shiftAvailability_global constraint.
 *
 * Uses verifyThat() (all constraints) with carefully curated given() facts so that
 * ONLY shiftAvailability_global and maximizeRating (unavoidable — no fact join) fire.
 * The expected scores account for maximizeRating's contribution:
 *   rating=3 × multiplier=100 = 300 soft reward (when shift is assigned).
 *
 * Test matrix:
 *  1. Fully available employee → no hard penalty (0hard)
 *  2. Unavailable employee → hard penalty (-1hard)
 *  3. Partial overlap (does NOT fully cover shift) → hard penalty (-1hard)
 *  4. No EmployeeAvailability fact → no penalty (assumed available)
 *  5. Unassigned employee (shift=null) → no penalty at all (0/0/0)
 *  6. Exact coverage → no hard penalty
 *  7. Late start → hard penalty
 *  8. "available" status → no hard penalty
 *  9. Wrong shift name → no penalty from availability (no join match)
 */
class ShiftAvailabilityConstraintTest {

    private ConstraintVerifier<ShiftConstraintProvider, ShiftSchedule> constraintVerifier;

    // maximizeRating fires for any assigned entity: rating(3) * multiplier(100) = +300 soft
    private static final long RATING_REWARD = 300L;

    @BeforeEach
    void setUp() {
        constraintVerifier = ConstraintVerifier.build(
                new ShiftConstraintProvider(),
                ShiftSchedule.class,
                EmployeeAssignment.class);
    }

    // --- Helpers ---

    private EmployeeAssignment makeAssignment(String id, String employeeId, String date,
                                                String shift, String position) {
        EmployeeAssignment a = new EmployeeAssignment(id, employeeId, "TestEmployee", date,
                "Full-Time", "Male", "Engineering", position);
        a.setShift(shift);
        a.setPinned(false);
        a.setHourlyWage(25.0);
        a.setPerformanceRating(3);
        a.setEligibleShifts(shift != null ? List.of(shift) : List.of());
        return a;
    }

    private ShiftDefinition makeShiftDef(String name, String start, String end) {
        return new ShiftDefinition(name, start, end);
    }

    private EmployeeAvailability makeAvail(String empId, String date, String status,
                                            LocalTime from, LocalTime to) {
        return new EmployeeAvailability(empId, date, status, from, to);
    }

    // ==========================================================================
    // Test 1: Fully available → NO hard penalty
    // Available 06:00-18:00, shift 08:00-16:00 → fully covered
    // ==========================================================================
    @Test
    void fullyAvailable_noHardPenalty() {
        var a = makeAssignment("a1", "EMP01", "2026-08-01", "Morning", "Dev");
        var sd = makeShiftDef("Morning", "08:00", "16:00");
        var av = makeAvail("EMP01", "2026-08-01", "partial",
                LocalTime.of(6, 0), LocalTime.of(18, 0));

        constraintVerifier.verifyThat()
                .given(a, sd, av)
                .scores(HardMediumSoftLongScore.of(0, 0, RATING_REWARD));
    }

    // ==========================================================================
    // Test 2: Unavailable → HARD penalty
    // ==========================================================================
    @Test
    void unavailable_hardPenalty() {
        var a = makeAssignment("a1", "EMP01", "2026-08-01", "Morning", "Dev");
        var sd = makeShiftDef("Morning", "08:00", "16:00");
        var av = makeAvail("EMP01", "2026-08-01", "unavailable", null, null);

        constraintVerifier.verifyThat()
                .given(a, sd, av)
                .scores(HardMediumSoftLongScore.of(-1, 0, RATING_REWARD));
    }

    // ==========================================================================
    // Test 3: Partial overlap — NOT full coverage → HARD penalty
    // Available 08:00-12:00, shift 08:00-16:00 → overlap exists but not full
    // ==========================================================================
    @Test
    void partialOverlap_hardPenalty() {
        var a = makeAssignment("a1", "EMP01", "2026-08-01", "Morning", "Dev");
        var sd = makeShiftDef("Morning", "08:00", "16:00");
        var av = makeAvail("EMP01", "2026-08-01", "partial",
                LocalTime.of(8, 0), LocalTime.of(12, 0));

        constraintVerifier.verifyThat()
                .given(a, sd, av)
                .scores(HardMediumSoftLongScore.of(-1, 0, RATING_REWARD));
    }

    // ==========================================================================
    // Test 4: No EmployeeAvailability fact → assumed available → NO penalty
    // ==========================================================================
    @Test
    void noAvailabilityFact_noPenalty() {
        var a = makeAssignment("a1", "EMP01", "2026-08-01", "Morning", "Dev");
        var sd = makeShiftDef("Morning", "08:00", "16:00");

        constraintVerifier.verifyThat()
                .given(a, sd)
                .scores(HardMediumSoftLongScore.of(0, 0, RATING_REWARD));
    }

    // ==========================================================================
    // Test 5: Unassigned (shift=null) → no constraints fire → 0/0/0
    // ==========================================================================
    @Test
    void unassigned_noPenalty() {
        var a = makeAssignment("a1", "EMP01", "2026-08-01", null, "Dev");
        var sd = makeShiftDef("Morning", "08:00", "16:00");
        var av = makeAvail("EMP01", "2026-08-01", "unavailable", null, null);

        constraintVerifier.verifyThat()
                .given(a, sd, av)
                .scores(HardMediumSoftLongScore.of(0, 0, 0));
    }

    // ==========================================================================
    // Test 6: Exact coverage → NO hard penalty
    // Available 08:00-16:00, shift 08:00-16:00
    // ==========================================================================
    @Test
    void exactCoverage_noPenalty() {
        var a = makeAssignment("a1", "EMP01", "2026-08-01", "Morning", "Dev");
        var sd = makeShiftDef("Morning", "08:00", "16:00");
        var av = makeAvail("EMP01", "2026-08-01", "partial",
                LocalTime.of(8, 0), LocalTime.of(16, 0));

        constraintVerifier.verifyThat()
                .given(a, sd, av)
                .scores(HardMediumSoftLongScore.of(0, 0, RATING_REWARD));
    }

    // ==========================================================================
    // Test 7: Late start → HARD penalty
    // Available 10:00-18:00, shift 08:00-16:00
    // ==========================================================================
    @Test
    void lateStart_hardPenalty() {
        var a = makeAssignment("a1", "EMP01", "2026-08-01", "Morning", "Dev");
        var sd = makeShiftDef("Morning", "08:00", "16:00");
        var av = makeAvail("EMP01", "2026-08-01", "partial",
                LocalTime.of(10, 0), LocalTime.of(18, 0));

        constraintVerifier.verifyThat()
                .given(a, sd, av)
                .scores(HardMediumSoftLongScore.of(-1, 0, RATING_REWARD));
    }

    // ==========================================================================
    // Test 8: "available" status → NO hard penalty
    // ==========================================================================
    @Test
    void availableStatus_noPenalty() {
        var a = makeAssignment("a1", "EMP01", "2026-08-01", "Morning", "Dev");
        var sd = makeShiftDef("Morning", "08:00", "16:00");
        var av = makeAvail("EMP01", "2026-08-01", "available", null, null);

        constraintVerifier.verifyThat()
                .given(a, sd, av)
                .scores(HardMediumSoftLongScore.of(0, 0, RATING_REWARD));
    }

    // ==========================================================================
    // Test 9: Wrong shift name → ShiftDefinition doesn't match → NO penalty
    // (Entity assigned to "Evening" but only "Morning" ShiftDefinition exists)
    // ==========================================================================
    @Test
    void wrongShiftName_noPenalty() {
        var a = makeAssignment("a1", "EMP01", "2026-08-01", "Evening", "Dev");
        var sd = makeShiftDef("Morning", "08:00", "16:00");
        var av = makeAvail("EMP01", "2026-08-01", "unavailable", null, null);

        // No ShiftDefinition for "Evening" → availability constraint join doesn't match
        // Only maximizeRating fires
        constraintVerifier.verifyThat()
                .given(a, sd, av)
                .scores(HardMediumSoftLongScore.of(0, 0, RATING_REWARD));
    }
}
