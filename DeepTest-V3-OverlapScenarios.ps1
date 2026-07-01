function Test-ManualAssign {
    param([hashtable]$Payload)
    $json = $Payload | ConvertTo-Json -Depth 10
    $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/manual-assign" -Method Post -Body $json -ContentType "application/json"
    Write-Host "Manual Assign Response: $($response.message)"
}

function Test-Api {
    param([string]$Name, [hashtable]$Payload)
    Write-Host "========================================="
    Write-Host "Running Test: $Name"
    
    $json = $Payload | ConvertTo-Json -Depth 10
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json" -TimeoutSec 300
        
        Write-Host "Score: $($response.solver_score)"
        Write-Host "Assignments Made: $($response.new_assignments_made)"
        
        if ($response.new_assignments_made -gt 0) {
            foreach ($date in $response.assignments_by_date.PSObject.Properties.Name) {
                Write-Host "Date: $date"
                foreach ($a in $response.assignments_by_date."$date") {
                    Write-Host "  - $($a.employee_name) ($($a.employee_id)) -> $($a.shift)"
                }
            }
        }
    } catch {
        Write-Host "Error: $($_.Exception.Message)"
    }
    Write-Host ""
}

$employees = @(
    @{
        employee_id = "EMP-OVERLAP-1"
        name = "Overlap Test 1"
        rate = 15.0
        unit = "hour"
        rating = 5
        role = "Helper"
    },
    @{
        employee_id = "EMP-OVERLAP-2"
        name = "Overlap Test 2"
        rate = 20.0
        unit = "hour"
        rating = 4
        role = "Helper"
    }
)

Write-Host "Injecting Manual Assignment for EMP-OVERLAP-1 on 2028-02-01 (Night shift)..."
Test-ManualAssign -Payload @{
    date = "2028-02-01"
    shift = "Night"
    employees = @(
        @{ employee_id = "EMP-OVERLAP-1"; name = "Overlap Test 1"; rate = 15.0; role = "Helper" }
    )
}
Write-Host ""

# Scenario 1: Same Day Overlap (2028-02-01)
$payloadSameDay = @{
    shift_name = "Morning"
    start_date = "2028-02-01"
    end_date = "2028-02-01"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "both"
    time_limit_seconds = 2
    unimproved_time_limit_seconds = 1
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 2 } )
    existing_users = $employees
}
Test-Api -Name "Scenario 1: Same Day Overlap (Expect Hard Penalty or Skip)" -Payload $payloadSameDay

# Scenario 2: Different Day (2028-02-02)
$payloadDiffDay = @{
    shift_name = "Morning"
    start_date = "2028-02-02"
    end_date = "2028-02-02"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "both"
    time_limit_seconds = 2
    unimproved_time_limit_seconds = 1
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 2 } )
    existing_users = $employees
}
Test-Api -Name "Scenario 2: Different Day (Expect 0 Hard Penalty, both assigned)" -Payload $payloadDiffDay
