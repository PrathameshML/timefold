$url = "http://localhost:8083/shifts/assign-v3"
$headers = @{ "Content-Type" = "application/json" }

function Test-Scenario {
    param([string]$name, [string]$body)
    Write-Host "`n============================================="
    Write-Host "SCENARIO: $name"
    Write-Host "============================================="
    try {
        $response = Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body $body
        Write-Host "Solver Score: $($response.solver_score)"
        Write-Host "Assigned Entities:"
        foreach ($date in $response.assignments_by_date.PSObject.Properties.Name) {
            foreach ($assignment in $response.assignments_by_date.$date) {
                Write-Host "  - $($assignment.employeeName) (EMP-ID: $($assignment.employeeId)) - Role: $($assignment.role) | Wage: $($assignment.wage) | Rating: $($assignment.rating)"
            }
        }
        if ($response.debug_log) {
            Write-Host "Debug Log:"
            $response.debug_log | ForEach-Object { Write-Host "  $_" }
        }
    } catch {
        Write-Host "ERROR: $_"
    }
}

# Scenario 1: Cost Optimization
$json1 = '{
  "shift_name": "Morning",
  "start_date": "2026-05-01", "end_date": "2026-05-01",
  "start_time": "06:00", "end_time": "14:00",
  "optimization": "cost",
  "time_limit_seconds": 3, "unimproved_time_limit_seconds": 1,
  "roles": [{ "role_name": "Helper", "max_workers": 1 }],
  "existing_users": [
    { "employee_id": "EMP-CHEAP", "name": "Cheap Worker", "rate": 10.0, "rating": 2, "role": "Helper", "employeeType": "Permanent", "skills": [] },
    { "employee_id": "EMP-EXPENSIVE", "name": "Expensive Worker", "rate": 30.0, "rating": 5, "role": "Helper", "employeeType": "Permanent", "skills": [] }
  ]
}'
Test-Scenario -name "1. Cost Optimization (Should pick Cheap Worker)" -body $json1

# Scenario 2: Quality Optimization
$json2 = '{
  "shift_name": "Morning",
  "start_date": "2026-05-02", "end_date": "2026-05-02",
  "start_time": "06:00", "end_time": "14:00",
  "optimization": "quality",
  "time_limit_seconds": 3, "unimproved_time_limit_seconds": 1,
  "roles": [{ "role_name": "Helper", "max_workers": 1 }],
  "existing_users": [
    { "employee_id": "EMP-CHEAP", "name": "Cheap Worker", "rate": 10.0, "rating": 2, "role": "Helper", "employeeType": "Permanent", "skills": [] },
    { "employee_id": "EMP-EXPENSIVE", "name": "Expensive Worker", "rate": 30.0, "rating": 5, "role": "Helper", "employeeType": "Permanent", "skills": [] }
  ]
}'
Test-Scenario -name "2. Quality Optimization (Should pick Expensive Worker)" -body $json2

# Scenario 3: Hard Constraints (Skill Match)
$json3 = '{
  "shift_name": "Morning",
  "start_date": "2026-05-03", "end_date": "2026-05-03",
  "start_time": "06:00", "end_time": "14:00",
  "optimization": "cost",
  "time_limit_seconds": 3, "unimproved_time_limit_seconds": 1,
  "roles": [{ "role_name": "Helper", "max_workers": 1, "required_skills": ["Forklift"] }],
  "existing_users": [
    { "employee_id": "EMP-CHEAP-NOSKILL", "name": "Cheap No Skill", "rate": 10.0, "rating": 5, "role": "Helper", "employeeType": "Permanent", "skills": [] },
    { "employee_id": "EMP-EXPENSIVE-SKILL", "name": "Expensive With Skill", "rate": 40.0, "rating": 2, "role": "Helper", "employeeType": "Permanent", "skills": ["Forklift"] }
  ]
}'
Test-Scenario -name "3. Hard Constraint - Skill Match (Should pick Expensive With Skill)" -body $json3

# Scenario 4a: Max Workers (Capacity) Run 1
$json4a = '{
  "shift_name": "Morning",
  "start_date": "2026-05-04", "end_date": "2026-05-04",
  "start_time": "06:00", "end_time": "14:00",
  "optimization": "cost",
  "time_limit_seconds": 3, "unimproved_time_limit_seconds": 1,
  "roles": [{ "role_name": "Helper", "max_workers": 2 }],
  "existing_users": [
    { "employee_id": "EMP-A", "name": "Worker A", "rate": 10.0, "rating": 3, "role": "Helper", "employeeType": "Permanent", "skills": [] },
    { "employee_id": "EMP-B", "name": "Worker B", "rate": 10.0, "rating": 3, "role": "Helper", "employeeType": "Permanent", "skills": [] }
  ]
}'
Test-Scenario -name "4a. Capacity - Run 1 (Assigns A and B)" -body $json4a

# Scenario 4b: Max Workers (Capacity) Run 2 - Verifying Bug Fix!
# Now A and B are in the database. We ask for 2 helpers from C and D.
$json4b = '{
  "shift_name": "Morning",
  "start_date": "2026-05-04", "end_date": "2026-05-04",
  "start_time": "06:00", "end_time": "14:00",
  "optimization": "cost",
  "time_limit_seconds": 3, "unimproved_time_limit_seconds": 1,
  "roles": [{ "role_name": "Helper", "max_workers": 2 }],
  "existing_users": [
    { "employee_id": "EMP-C", "name": "Worker C", "rate": 10.0, "rating": 3, "role": "Helper", "employeeType": "Permanent", "skills": [] },
    { "employee_id": "EMP-D", "name": "Worker D", "rate": 10.0, "rating": 3, "role": "Helper", "employeeType": "Permanent", "skills": [] }
  ]
}'
Test-Scenario -name "4b. Capacity - Run 2 (Should assign C and D, ignoring pinned A and B)" -body $json4b

