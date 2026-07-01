function Generate-Employees {
    param([int]$Count)
    $emps = @()
    for ($i=1; $i -le $Count; $i++) {
        $emps += @{ employee_id = "EMP-CACHE3-$i"; name = "Cache Test $i"; rate = 15.0; role = "Helper"; rating = 3 }
    }
    return $emps
}

$employees = Generate-Employees -Count 100
$payload100 = @{
    shift_name = "Morning"
    start_date = "2029-05-01"
    end_date = "2029-05-01"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "cost"
    time_limit_seconds = 2
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 20 } )
    existing_users = $employees
}

$json1 = $payload100 | ConvertTo-Json -Depth 10
$response1 = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json1 -ContentType "application/json"
Write-Host "Run 1 (Cost): Score: $($response1.solver_score) Assignments: $($response1.new_assignments_made)"

$payload100.optimization = "quality"
$json2 = $payload100 | ConvertTo-Json -Depth 10
$response2 = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json2 -ContentType "application/json"
Write-Host "Run 2 (Quality): Score: $($response2.solver_score) Assignments: $($response2.new_assignments_made)"
