# Timefold V3 Global Solver: Comprehensive Chat Summary

This document serves as a detailed historical record of the work completed to introduce the **V3 Multi-Shift Global Solver** to the Timefold Shift Scheduler. It documents the architectural decisions, constraints, bug fixes, and final benchmark results.

## 1. Project Goal
**Objective**: Upgrade the Timefold Shift Scheduler from a "Sequential" single-shift solver (V1/V2, which solves one shift at a time and loops) to a "Global" multi-shift solver (V3) capable of solving all shifts across an entire date range in a single, unified Timefold run, allowing the solver to make trade-offs across different shifts.

## 2. Phase 1: Architecture & Global Constraints
- **Endpoint Created**: Added `POST /shifts/assign-global` routing to `solveGlobal` in `SolverService.java`.
- **Fact Aggregation**: Unlike V1 which created entities for a single shift, V3 creates a unified `ShiftSchedule` containing every shift definition, role requirement, and rating requirement for the entire date range.
- **Dual-Constraint Isolation**: To ensure 100% backwards compatibility with V1/V2, new global constraints were built alongside the old ones. Global constraints are suffixed with `_global` (e.g., `maxWorkersPerRole_global`, `everyShiftPlanned_global`). V3 problem facts activate the `_global` variants, while V1/V2 remain completely untouched.
- **Role Requirement Matching**: Refactored role requirements to match based on a combination of `shiftName` and `date`. Fixed a bug where `everyShiftPlanned_global` was penalizing missing requirements instead of unassigned employee slots.

## 3. Phase 2 & 3: Eligibility & Availability
- **Per-Shift Eligibility**: Employees are often only eligible for certain shifts (e.g., Morning but not Night). We added an `eligibleShifts` set to `EmployeeAssignment` and used it to filter Timefold's value range dynamically. If an employee is assigned to a shift not in their eligible list, a hard penalty applies (though the value range usually prevents this).
- **Time-Aware Availability Constraint**: Added the `shiftAvailabilityGlobal` hard constraint. When an employee is assigned to a shift, this constraint looks up the shift's exact time window (e.g., 08:00 - 16:00) and cross-references it against the employee's `EmployeeAvailability` (leaves/unavailable periods).
- **Default Availability**: If an employee has no availability record for a given day, they default to **fully available**. Data is only needed to mark them as unavailable.

## 4. Phase 4: Bug Hunts & Troubleshooting
- **LocalTime Parsing Bug**: Encountered `DateTimeParseException` because shift times were strings (some "08:00", some "16:00:00"). Standardized time comparisons in the availability constraint using `LocalTime.parse`.
- **Missing Rating Constraint**: Discovered `solveGlobal` was failing to extract and inject `RatingRequirement` objects from the payload into the `ShiftSchedule`. This effectively disabled the `minimumRatingRequirement` constraint. Fixed by extracting it during payload parsing.

## 5. Phase 5: The "Wage Parsing" Discrepancy & Benchmarking
- **The Mystery**: Initial benchmarks showed Sequential yielding positive soft scores (e.g., `+67,000`), while Global yielded massively negative soft scores (e.g., `-333,000`).
- **Root Cause Analysis**: The `wageOptimization` constraint applies a massive `-1000x` penalty to wages. We discovered the Python benchmark scripts (`benchmark.py`) were sending `"hourly_wage"` in the payload instead of the documented API field `"rate"`. 
- **Why it impacted Sequential**: V1 was strictly built to read `"rate"`. Because the payload sent `"hourly_wage"`, V1 silently defaulted all employee wages to `$0.00`. This completely eliminated the massive cost penalty for V1, leaving only the positive rewards (like `maximizeRating`).
- **The Fix**: 
  - We confirmed V1 was correct and the Python benchmark script was the one making the mistake.
  - Reverted any fallback logic in Java, standardizing V3 to also strictly read `"rate"` to match V1 perfectly.
  - Updated the Python test payloads to correctly send `"rate"`.

## 6. Time Limits & Termination Formula
Both solvers use the exact same dynamic formula to calculate how long to think, based on the payload size:
- **Base Budget**: `2 seconds + (total_employees / 20)` per day.
- **Total Limit**: `Base Budget * number_of_days` (capped between 5s and 300s).
- **Unimproved Limit**: `Total Limit / 3`.

Because Sequential processes a subset of eligible employees per shift, its per-shift limit is smaller (e.g., 49 seconds). Because Global processes all 200 employees at once, its limit hit the calculated ceiling of 84 seconds.

## 7. Final Benchmark Results
Run with a payload of **200 employees, 5 shifts, 7 days (700 total assignments)**:

**Sequential (5 separate shift runs)**
- Total Solver Time: 238.00s (Cumulative)
- Total Assignments: 700
- Feasibility: `0hard / 0medium` (Fully Feasible)
- Net Score: `-321,441 soft`

**Global (1 unified run)**
- Total Solver Time: 84.02s
- Total Assignments: 700
- Feasibility: `0hard / 0medium` (Fully Feasible)
- Net Score: `-336,624 soft`

**Conclusion**: Global achieved a **2.80x speedup** (84s vs 238s). Because Global had a fraction of the cumulative compute time, its soft score is mathematically slightly worse (-336k vs -321k), representing a standard trade-off between blazing-fast execution time across massive datasets and absolute perfection in soft-score optimization. The logic is completely sound, and backwards compatibility remains 100% intact.
