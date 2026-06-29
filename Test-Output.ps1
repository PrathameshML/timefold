$users = @(
    @{ employee_id = "EMP-01"; name = "virat"; rate = 10.0; unit = "hour"; rating = 2; role = "Helper" },
    @{ employee_id = "EMP-02"; name = "dhoni"; rate = 25.0; unit = "hour"; rating = 5; role = "Helper" },
    @{ employee_id = "EMP-03"; name = "rohit"; rate = 15.0; unit = "hour"; rating = 3; role = "Helper" }
)
$payload1 = @{
    shift_name = "Morning"
    start_date = "2026-02-19"
    end_date = "2026-02-19"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "cost"
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 1 } )
    existing_users = $users
}
$json = $payload1 | ConvertTo-Json -Depth 10
$response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json"
$response | ConvertTo-Json -Depth 10
