$body1 = @{
    shift_name = "Morning"
    start_date = "2026-06-15"
    end_date = "2026-06-15"
    start_time = "08:00"
    end_time = "16:00"
    roles = @(
        @{ role_name = "Bartender"; max_workers = 2; required_skills = @("Mixology", "Customer Service"); rating = 1 }
    )
    existing_users = @(
        @{ employee_id = "e1"; name = "e1"; role = "Bartender"; rate = 20.0; unit = "hour"; rating = 1; skills = @("Mixology", "Customer Service") },
        @{ employee_id = "e2"; name = "e2"; role = "Bartender"; rate = 21.0; unit = "hour"; rating = 2; skills = @("Mixology", "Customer Service") }
    )
} | ConvertTo-Json -Depth 10

Write-Host "`n=== TEST 3: Perfect Skill Match ===" -ForegroundColor Cyan
$res1 = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v2" -Method POST -Body $body1 -ContentType "application/json"
$res1 | ConvertTo-Json -Depth 10


$body2 = @{
    shift_name = "Morning"
    start_date = "2026-06-16"
    end_date = "2026-06-16"
    start_time = "08:00"
    end_time = "16:00"
    roles = @(
        @{ role_name = "Bartender"; max_workers = 2; required_skills = @("Mixology", "Customer Service"); rating = 1 }
    )
    existing_users = @(
        @{ employee_id = "e3"; name = "e3"; role = "Bartender"; rate = 22.0; unit = "hour"; rating = 3; skills = @("Customer Service") }
    )
} | ConvertTo-Json -Depth 10

Write-Host "`n=== TEST 4: Partial Skill Match (Missing Mixology) ===" -ForegroundColor Cyan
$res2 = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v2" -Method POST -Body $body2 -ContentType "application/json"
$res2 | ConvertTo-Json -Depth 10
