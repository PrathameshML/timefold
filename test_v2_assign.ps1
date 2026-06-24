$body = @{
    shift_name = "Morning"
    start_date = "2026-06-15"
    end_date = "2026-06-15"
    start_time = "08:00"
    end_time = "16:00"
    roles = @(
        @{ role_name = "Bartender"; max_workers = 2; rating = 1 }
    )
    existing_users = @(
        @{ employee_id = "e1"; name = "e1"; role = "Bartender"; rate = 20.0; unit = "hour"; rating = 1; employeeType = "Permanent"; skills = @("Mixology") },
        @{ employee_id = "e2"; name = "e2"; role = "Bartender"; rate = 21.0; unit = "hour"; rating = 2; employeeType = "Permanent"; skills = @("Mixology") }
    )
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v2" -Method POST -Body $body -ContentType "application/json" | ConvertTo-Json -Depth 10
