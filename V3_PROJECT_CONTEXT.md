# Timefold V3 Engine — Complete Project Context

> **Purpose:** This document captures the entire context of the V3 engine development, testing, and approved refactoring plan. Feed this to a new chat session to resume work with full continuity.

---

## 1. Project Overview

**What this is:** A Quarkus-based employee shift scheduling microservice that uses the Timefold (formerly OptaPlanner) constraint solver to optimally assign employees to shifts.

**How it's called:** This is an **internal backend microservice** invoked by another backend service. It is NOT a user-facing frontend app.

**Repository:** `c:\Users\user\Downloads\timefold\timefold`

**Key Files:**
- `src/main/java/com/scheduler/ShiftApp.java` — **13,348 lines.** The monolith. Contains ALL REST endpoints, ALL solver logic (V1, V2, V3), ALL inner model classes, ALL constraint providers.
- `src/main/java/com/scheduler/MySQLService.java` — ~852 lines. Database access layer. Handles table creation, CRUD for assignments, constraint configs, employees, etc.
- `src/main/resources/application.properties` — Server config, database connection, scheduler mode.

---

## 2. Architecture Versions

### V1 (Legacy)
- Hard-coded 3-second solver time limit.
- Used `Scheduler.ShiftSchedule` and `Scheduler.EmployeeAssignment` inner classes.
- Very basic constraints.

### V2 (Legacy)
- Hard-coded 60-second time limit (overridable via API).
- Used `ShiftScheduleV2` and `ShiftConstraintsV2`.
- More constraints but still tightly coupled to the monolith.

### V3 (Current / Production-Ready)
- **Dynamic time limit formula:** `2s + (totalEntities / 20)` where `totalEntities = employees x days`.
- **Unimproved time limit:** `25% of total time limit` (minimum 1 second).
- Both limits are **overridable via API** using `time_limit_seconds` and `unimproved_time_limit_seconds` in the JSON payload.
- Uses `ShiftScheduleV3` (`@PlanningSolution`), `EmployeeAssignmentV3` (`@PlanningEntity`), and `ShiftConstraintsV3` (`ConstraintProvider`).
- **Completely stateless regarding employees** — takes the employee pool directly from the JSON payload (no database dependency for employee data).
- Only saves final results to the `shift_assignments` table via `mysqlService.syncAssignment()`.
- Uses `parseNumber()` utility (line ~55 in ShiftApp.java) to safely handle Jackson's inconsistent Integer/Double deserialization from JSON.

---

## 3. Scheduler Mode Isolation

**Property:** `scheduler.mode` in `application.properties`
- `full` — Creates ALL tables (employees, leave_requests, system_configuration, shift_assignments, constraint_config, constraint_config_v3, etc.). Runs full V1/V2/V3 initialization including `syncEmployee()`.
- `v3-only` — Creates ONLY `shift_assignments` and `constraint_config_v3` tables. Skips all legacy table creation and employee sync. **This is the current setting.**

**Location in code:** `MySQLService.java` reads `@ConfigProperty(name = "scheduler.mode", defaultValue = "full")` and uses `boolean v3Only = "v3-only".equalsIgnoreCase(schedulerMode)` to gate table creation.

---

## 4. V3 API Endpoints (Current)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/shifts/assign-v3` | Solve a single shift assignment |
| `POST` | `/shifts/batch-assign-v3` | Solve multiple shifts sequentially (the primary production endpoint) |
| `GET` | `/constraints-v3` | Retrieve current V3 constraint configs from `constraint_config_v3` table |
| `PUT` | `/constraints-v3` | Update V3 constraint configs |

**Missing (needs to be created during refactoring):**
| Method | Path | Purpose |
|--------|------|---------|
| `DELETE` | `/shifts/assign-v3/clear-all` | Safely clear only `shift_assignments` table |

**Known Danger:** The legacy `DELETE /shifts/clear-all` endpoint calls `mysqlService.clearAllDatabase()` which tries to wipe `employees`, `leave_requests`, `notifications`, `overtime_records`, `leave_coverage_requests` — tables that don't exist in `v3-only` mode, causing a 500 crash. It also dangerously deletes HR data that has nothing to do with shift schedules.

---

## 5. V3 Constraint System

The V3 engine uses `ShiftConstraintsV3` (inner class inside `ShiftApp.java`, line ~12377) implementing Timefold's `ConstraintProvider`. Constraints use a `ThreadLocal<Context>` pattern to pass dynamic parameters (wages, ratings, constraint configs) into the constraint stream.

### Complete List of V3 Constraints (verified from source code):

#### Always-On Hard Constraints (cannot be toggled off):
1. **`maxWorkersPerRole`** (HARD, line ~12441) — Each role cannot exceed its `max_workers` limit per date. Penalizes by the overflow count.
2. **`minimumRatingRequirement`** (HARD, line ~12457) — Employees whose rating is NOT in the `allowedRatings` list for their role get penalized. This ensures only qualified employees are assigned.

#### Toggleable Hard Constraint (can be enabled/disabled via constraint_config_v3):
3. **`v3NoOverlappingShifts_HARD`** (HARD, line ~12486) — No employee can work two shifts on the same date. Uses `isConstraintActive()` check.

#### Medium Constraint:
4. **`v3EveryShiftPlanned`** (MEDIUM, line ~12469) — Penalizes unfilled slots. For each role on each date, if the assigned count is less than `max_workers`, it penalizes by `(max_workers - count) * 10000`. This strongly encourages the solver to fill all required positions but doesn't absolutely force it (which allows graceful degradation when there aren't enough employees).

#### Soft Constraints (toggleable):
5. **`v3WageOptimization_SOFT`** (SOFT, line ~12496) — Penalizes higher-wage employees relative to the average wage for their role. Uses `wageRatio * multiplier` formula. Active in `cost` and `both` modes.
6. **`v3MaximizeRating_SOFT`** (SOFT, line ~12510) — Rewards employees with higher ratings. Uses `rating * multiplier` formula. Active in `quality` and `both` modes.

### Optimization Modes (passed in JSON payload as `"optimization"`):
- `"cost"` — Only `v3WageOptimization_SOFT` is active.
- `"quality"` — Only `v3MaximizeRating_SOFT` is active.
- `"both"` — Both `v3WageOptimization_SOFT` and `v3MaximizeRating_SOFT` are active (most complex).

---

## 6. MySQLService Methods Used by V3

V3 only uses these specific methods from `MySQLService.java`:
- `loadAllConstraintConfigsV3()` — Reads from `constraint_config_v3` table.
- `saveConstraintConfigV3()` — Writes to `constraint_config_v3` table.
- `insertDefaultConstraintsV3()` — Seeds default V3 constraints.
- `syncAssignment()` — Writes solved assignments to `shift_assignments` table.
- `initializeDatabase()` — Creates tables on startup (respects `v3-only` mode).

V3 does NOT use any of these legacy methods:
- `syncEmployee()`, `clearAllEmployees()`, `clearAllDatabase()`, `loadAllEmployees()`, `loadAllLeaveRequests()`, `saveSystemConfig()`, `loadSystemConfig()`, etc.

---

## 7. Key Code Locations in ShiftApp.java

> **CRITICAL:** These line numbers are approximate and may shift slightly. Always verify before editing.

| What | Approximate Lines |
|------|------------------|
| `parseNumber()` utility | ~55-65 |
| `ShiftConstraintsV3` inner class (ConstraintProvider) | ~12377-12525 |
| `ShiftScheduleV3` inner class (@PlanningSolution) | Search for `class ShiftScheduleV3` (before 12377) |
| `EmployeeAssignmentV3` inner class (@PlanningEntity) | Search for `class EmployeeAssignmentV3` (before ShiftScheduleV3) |
| V3 Endpoints (`/constraints-v3` GET/PUT) | ~12527 onwards |
| `batchAssignShiftsV3()` endpoint | Search for `batch-assign-v3` |
| `solveShiftV3()` method (the core engine) | ~12800-13348 |
| Dynamic time limit formula | ~13188: `long defaultTimeLimit = 2L + (totalEntities / 20L)` |
| Score explanation logging | ~13214: `LOG.debug("Score Explanation:\n" + scoreExplanation)` |
| Result processing + `mysqlService.syncAssignment()` | ~13280 |

---

## 8. Testing Results Summary

All tests passed. The engine is verified production-ready.

### Heavy Load Test (500 employees, 100 required, 21 shifts, 1 week):
- **Cost mode:** Filled all required workers, prioritized cheapest employees.
- **Quality mode:** Filled all required workers, prioritized highest-rated employees.
- **Both mode:** Balanced wage penalty vs rating reward.
- 0 Hard Constraint violations in all modes.

### Massive Scale Test (500 employees, 300 required per shift, 21 shifts, 1 week):
- **Result:** 3,800 assignments in 308 seconds (~5 minutes).
- **Why not 6,300?** Because 3 shifts x 300 workers = 900 daily slots, but only 500 employees exist. The solver correctly maxed out at 500 assignments/day (hard constraint: no overlapping shifts).

### Edge Cases Verified:
- Empty employee list -> graceful error response.
- String vs Number casting -> handled by `parseNumber()`.
- Missing optional fields (gender, employeeType) -> defaults applied.
- Overlapping shift times -> hard constraint blocks double-booking.
- Fresh database with `v3-only` mode -> boots and creates only required tables.

---

## 9. Approved Refactoring Plan

**Strategy:** Strangler Fig Pattern — extract V3 code into new files, verify it compiles and works, then MARK (not delete) the old V3 code in `ShiftApp.java` for future deletion.

### Target Folder Structure:
```
src/main/java/com/scheduler/
  ShiftApp.java              <- Legacy V1/V2 stays. V3 code gets MARKED for deletion (not removed yet).
  MySQLService.java          <- Stays as shared DB layer. Legacy methods untouched.
  model/
    v3/
      EmployeeAssignmentV3.java    <- @PlanningEntity (extracted from ShiftApp inner class)
      ShiftScheduleV3.java         <- @PlanningSolution (extracted from ShiftApp inner class)
  solver/
    ShiftConstraintsV3.java        <- ConstraintProvider + ThreadLocal Context (extracted from ShiftApp inner class)
  service/
    V3MySQLService.java            <- NEW dedicated V3 database service (only shift_assignments + constraint_config_v3)
    SolverServiceV3.java           <- solveShiftV3(), parseNumber(), business logic (extracted from ShiftApp)
  rest/
    ShiftResourceV3.java           <- All V3 REST endpoints + new DELETE clear-all (extracted from ShiftApp)
```

### Key Design Decisions:
1. **Separate `V3MySQLService.java`** — V3 gets its own dedicated database service class, NOT sharing the legacy `MySQLService.java`. This ensures V3 is fully independent and only touches `shift_assignments` and `constraint_config_v3`.
2. **Do NOT delete V3 code from ShiftApp.java** — After extraction, only MARK the old V3 code with comments like `// TODO: DELETE AFTER V3 REFACTORING VERIFIED`. Once everything is confirmed working in the new structure, the user will approve deletion.
3. **Legacy V1/V2 code in ShiftApp.java remains completely untouched.**

### Execution Order:
1. **Phase 1: Models** — Extract `EmployeeAssignmentV3`, `ShiftScheduleV3` inner classes into `model/v3/`.
2. **Phase 2: Constraints** — Extract `ShiftConstraintsV3` + `Context` ThreadLocal into `solver/`.
3. **Phase 3: Services** — Create `V3MySQLService.java` with only V3 database methods. Extract `solveShiftV3()` logic + `parseNumber()` into `service/SolverServiceV3.java`.
4. **Phase 4: REST** — Extract JAX-RS endpoints into `rest/ShiftResourceV3.java`. Add new safe `DELETE /shifts/assign-v3/clear-all`.
5. **Phase 5: Mark for Deletion** — Comment-mark all V3 code in `ShiftApp.java` but DO NOT delete it yet.

### Verification After Each Phase:
- `mvn compile quarkus:dev` must succeed.
- Run batch V3 test to verify endpoints still work.
- Run heavy load test to verify solver still produces correct results.

---

## 10. Important User Preferences and Rules

- "Don't trust [ChatGPT] blindly" — Always verify before making changes.
- "Don't change code unless confirmed" — Never make code changes without explicit approval.
- "Do rigorous and deep, optimization, testing" — Be thorough.
- "Take your time, do all type of testing, edge case, everything" — Don't rush.
- Score explanation logging stays at `LOG.debug` — Too verbose for production at INFO level.
- Do NOT implement: JWT/OIDC authentication, CORS whitelisting, or any security hardening unless explicitly asked.
- Timefold is a heuristic solver — Never say "optimal." Say "best solution found within the configured time limit" or "high-quality feasible solution."
- Do NOT delete V3 code from ShiftApp.java during refactoring — only MARK it for deletion.
- V3 should have its own dedicated MySQLService (V3MySQLService.java), not share the legacy one.

---

## 11. Database Configuration

### Local Dev:
```properties
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/timefold
quarkus.datasource.username=root
quarkus.datasource.password=root123
```

### Production (Environment Variables):
```properties
%prod.quarkus.datasource.jdbc.url=${DB_URL}
%prod.quarkus.datasource.username=${DB_USER}
%prod.quarkus.datasource.password=${DB_PASSWORD}
```

### Tables Created in v3-only Mode:
- `shift_assignments` — Stores solver output (date, shift, employee_id, employee_name, employee_role, employee_category, gender, rating, start_time, end_time).
- `constraint_config_v3` — Stores V3 constraint toggle/weight configuration (constraint_id, constraint_name, description, enabled, severity, parameter_value, parameter_name, parameter_value_2, parameter_name_2).

---

## 12. Current application.properties State

```properties
quarkus.http.port=8083
scheduler.mode=v3-only
quarkus.datasource.jdbc.url=jdbc:mysql://localhost:3306/timefold
quarkus.datasource.username=root
quarkus.datasource.password=root123
```

---

## 13. Conversation ID for Reference

This context was generated from conversation: `c1a82214-a31f-4201-9a24-ad75d480b6c5`
