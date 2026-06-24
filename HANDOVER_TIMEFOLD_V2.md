# Timefold V2 Constraints - Handover Document

**To the AI Assistant:** If you are reading this, we have moved to a new PC. Read this document completely to understand the exact state of our project and what we need to execute immediately.

## Project Context
We are working on a Quarkus Java project using **Timefold** (formerly OptaPlanner) for shift scheduling. 
The codebase is located at the root of this workspace. The primary file we are editing is `src/main/java/com/scheduler/ShiftApp.java` (specifically the `assignShiftsV2` endpoint and the `ShiftConstraintsV2` inner class).

## What We Have Achieved So Far
1. **Ghost Employees Bug Fixed:** We fixed the V2 pipeline so it only injects explicitly requested employees from the JSON payload into the solver, eliminating database cross-contamination.
2. **Clear DB Endpoint:** We built `DELETE /shifts/clear-all` for pristine testing.
3. **Timefold Limits:** We updated `SolverConfig` to use `withTerminationSpentLimit(Duration.ofSeconds(15))` and `withTerminationUnimprovedSpentLimit(Duration.ofSeconds(3))` to allow proper Wage Optimization crunching without HTTP timeouts.
4. **Constraint Audit:** We successfully passed a massive 25-employee "Dinner Shift" JSON payload (`0hard/-25medium/-9455soft`). 

## IMMEDIATE NEXT STEP: Execute the Constraints Rewrite Plan
We have finalized a plan to rewrite the mathematical constraints in `ShiftConstraintsV2` to follow true Timefold industry best practices. 

**Here is the exact Implementation Plan you need to execute now:**

### 1. Every Shift Planned (Constraint #4)
- **Current Bug:** It loops through `EmployeeAssignment` and penalizes employees who don't have a shift, forcing overstaffing.
- **Action:** Rewrite it to `groupBy` Date and Role. Calculate the total assigned workers vs the required `max_workers`. Penalize the solver ONLY if the shift slot is empty (count < max_workers).

### 2. Wage Optimization (Constraint #5)
- **Current Bug:** Arbitrarily penalizes `wage * 5`.
- **Action:** Calculate the actual dollar cost: `(wage * shift_duration)` and apply it as a Soft penalty.

### 3. Unavailable Timeslot (Constraint #3)
- **Current Bug:** Was hijacked to evaluate Performance Ratings.
- **Action:** Keep it as the Rating Matcher (since actual unavailability is handled via pre-filtering), but clean up the Java comments so the intent is clear.

### 4. Break After Hours (Constraint #9)
- **Current Bug:** The business logic is correct (scheduled break before X continuous hours), but it parses `LocalTime` strings *inside* the Timefold scoring loop, crushing performance.
- **Action:** Optimize it to prevent string parsing inside the scoring loop.

### 5. Consecutive Shifts (Constraint #10)
- **Current Bug:** The logic checks for gaps (rewarding consecutive working days). 
- **Action:** Keep the core mathematical logic intact (penalizing gaps), as the user confirmed this is the correct business intent (e.g., schedule Mon, Tue, Wed rather than Mon, Wed, Fri). Just ensure the 1000L penalty multiplier is balanced.

**To the AI Assistant:** Acknowledge that you have read this, and ask the user if you should begin rewriting `ShiftApp.java` immediately!
