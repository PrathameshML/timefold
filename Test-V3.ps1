function Test-Scenario {
    param([string]$Name, [hashtable]$Payload, [string]$Expected)
    Write-Host "========================================="
    Write-Host "Running Test: $Name"
    Write-Host "Expected: $Expected"
    
    $json = $Payload | ConvertTo-Json -Depth 10
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json"
        
        Write-Host "Score: " $response.solver_score
        Write-Host "Assignments:"
        if ($response.assignments_by_date) {
            foreach ($date in $response.assignments_by_date.PSObject.Properties.Name) {
                foreach ($a in $response.assignments_by_date."$date") {
                    Write-Host "  -> $($a.employee_name) ($($a.employee_id)) assigned to $($a.role) (Wage: $($a.wage), Rating: $($a.rating))"
                }
            }
        } else {
            Write-Host "  -> NO ASSIGNMENTS"
        }
        if ($response.skipped_count -gt 0) {
            Write-Host "Skipped: $($response.skipped_count)"
        }
        Write-Host ""
    } catch {
        Write-Host "Error: $($_.Exception.Message)"
    }
}

$users = @(
    @{ employee_id = "EMP-01"; name = "virat"; rate = 10.0; unit = "hour"; rating = 2; role = "Helper" },
    @{ employee_id = "EMP-02"; name = "dhoni"; rate = 25.0; unit = "hour"; rating = 5; role = "Helper" },
    @{ employee_id = "EMP-03"; name = "rohit"; rate = 15.0; unit = "hour"; rating = 3; role = "Helper" }
)

$payload1 = @{
    shift_name = "Morning"
    start_date = "2027-03-01"
    end_date = "2027-03-01"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "cost"
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 1 } )
    existing_users = $users
}
Test-Scenario -Name "Optimization = COST" -Payload $payload1 -Expected "Should pick Virat (cheapest, wage 10)"

$payload2 = @{
    shift_name = "Morning"
    start_date = "2027-03-02"
    end_date = "2027-03-02"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "quality"
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 1 } )
    existing_users = $users
}
Test-Scenario -Name "Optimization = QUALITY" -Payload $payload2 -Expected "Should pick Dhoni (highest rating 5)"

$payload3 = @{
    shift_name = "Morning"
    start_date = "2027-03-03"
    end_date = "2027-03-03"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "both"
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 1 } )
    existing_users = $users
}
Test-Scenario -Name "Optimization = BOTH" -Payload $payload3 -Expected "Should balance. Since weight is 1000 for wage and 100 for rating, wage dominates, so likely Virat or Rohit."

$usersSingle = @(
    @{ employee_id = "EMP-01"; name = "virat"; rate = 10.0; unit = "hour"; rating = 2; role = "Helper" }
)
$payload4 = @{
    shift_name = "Morning"
    start_date = "2027-03-04"
    end_date = "2027-03-04"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "cost"
    roles = @( 
        @{ role_name = "Helper"; rating = 1; max_workers = 2 }
    )
    existing_users = $usersSingle
}
Test-Scenario -Name "Double Booking / Overlapping" -Payload $payload4 -Expected "Should assign Virat to ONE slot, and leave the other slot empty (1 Medium penalty). It MUST NOT double-book Virat due to HARD penalty."
