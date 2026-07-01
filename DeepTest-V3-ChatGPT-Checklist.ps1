function Generate-Employees {
    param([int]$Count)
    $employees = @()
    for ($i = 1; $i -le $Count; $i++) {
        $wage = [math]::Round((Get-Random -Minimum 10.0 -Maximum 40.1), 1)
        $rating = Get-Random -Minimum 1 -Maximum 6
        $employees += @{
            employee_id = "EMP-$i"
            name = "TestEmp$i"
            rate = $wage
            unit = "hour"
            rating = $rating
            role = "Helper"
        }
    }
    return $employees
}

function Test-Api {
    param([string]$Name, [hashtable]$Payload, [int]$ExpectedStatus = 200)
    Write-Host "========================================="
    Write-Host "Testing: $Name"
    
    $json = $Payload | ConvertTo-Json -Depth 10
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json" -TimeoutSec 300
        
        # In PowerShell 7, Invoke-RestMethod with still returns the response object if successful, but we don't have StatusCode easily. 
        # Actually is PS7+. Let's use a try/catch instead for PS5 compatibility.
    } catch {
        $response = $_.Exception.Response
        if ($response) {
            $statusCode = $response.StatusCode
            Write-Host "Expected Error Caught: $statusCode"
            return
        }
        Write-Host "Error: $($_.Exception.Message)"
        return
    }
    Write-Host "Score: $($response.solver_score)"
    Write-Host "Assignments Made: $($response.new_assignments_made)"
    
    if ($response.new_assignments_made -gt 0) {
        $sumWage = 0
        $sumRating = 0
        foreach ($date in $response.assignments_by_date.PSObject.Properties.Name) {
            foreach ($a in $response.assignments_by_date."$date") {
                $sumWage += $a.wage
                $sumRating += $a.rating
            }
        }
        $avgWage = [math]::Round($sumWage / $response.new_assignments_made, 2)
        $avgRating = [math]::Round($sumRating / $response.new_assignments_made, 2)
        Write-Host "Average Assigned Wage: $$$avgWage"
        Write-Host "Average Assigned Rating: $avgRating"
    }
    Write-Host ""
}

$emps100 = Generate-Employees -Count 100

# 1. Cost Mode Works
$payloadCost = @{ shift_name = "Morning"; start_date = "2030-01-01"; end_date = "2030-01-01"; start_time = "06:00"; end_time = "14:00"; optimization = "cost"; time_limit_seconds = 2; roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 20 } ); existing_users = $emps100 }
Test-Api -Name "Cost Mode Works (Expect low wage)" -Payload $payloadCost

# 2. Quality Mode Works
$payloadQuality = @{ shift_name = "Morning"; start_date = "2030-01-02"; end_date = "2030-01-02"; start_time = "06:00"; end_time = "14:00"; optimization = "quality"; time_limit_seconds = 2; roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 20 } ); existing_users = $emps100 }
Test-Api -Name "Quality Mode Works (Expect high rating)" -Payload $payloadQuality

# 3. Both Mode Balances
$payloadBoth = @{ shift_name = "Morning"; start_date = "2030-01-03"; end_date = "2030-01-03"; start_time = "06:00"; end_time = "14:00"; optimization = "both"; time_limit_seconds = 2; roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 20 } ); existing_users = $emps100 }
Test-Api -Name "Both Mode Balances (Expect medium wage/rating)" -Payload $payloadBoth

# 4. Invalid Optimization -> 400
try {
    $payloadInvalid = @{ shift_name = "Morning"; start_date = "2030-01-04"; end_date = "2030-01-04"; start_time = "06:00"; end_time = "14:00"; optimization = "magic"; time_limit_seconds = 1; roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 1 } ); existing_users = $emps100 }
    $json = $payloadInvalid | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json" | Out-Null
    Write-Host "FAILED: Invalid Optimization should have thrown 400!"
} catch {
    Write-Host "? Invalid Optimization threw error: $($_.Exception.Message)"
}

# 5. Missing Optimization -> Defaults to "both"
$payloadMissingOpt = @{ shift_name = "Morning"; start_date = "2030-01-05"; end_date = "2030-01-05"; start_time = "06:00"; end_time = "14:00"; time_limit_seconds = 2; roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 20 } ); existing_users = $emps100 }
Test-Api -Name "Missing Optimization (Expect successful both mode)" -Payload $payloadMissingOpt

# 6. Duplicate Employee IDs
try {
    $dupEmps = @( @{ employee_id = "EMP-1"; name = "Bob"; rate = 15; role = "Helper" }, @{ employee_id = "EMP-1"; name = "Bob Clone"; rate = 15; role = "Helper" } )
    $payloadDup = @{ shift_name = "Morning"; start_date = "2030-01-06"; end_date = "2030-01-06"; start_time = "06:00"; end_time = "14:00"; optimization = "cost"; time_limit_seconds = 1; roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 1 } ); existing_users = $dupEmps }
    $json = $payloadDup | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json" | Out-Null
    Write-Host "FAILED: Duplicate IDs should have thrown 400!"
} catch {
    Write-Host "? Duplicate IDs threw error: $($_.Exception.Message)"
}

# 7. Same Day Overlap (Morning + Evening)
Write-Host "Injecting Manual Assignment for EMP-1 on 2030-01-07 (Evening)..."
$jsonManual = @{ date = "2030-01-07"; shift = "Evening"; employees = @( @{ employee_id = "EMP-1"; name = "Overlap"; rate = 15.0; role = "Helper" } ) } | ConvertTo-Json -Depth 10
Invoke-RestMethod -Uri "http://localhost:8083/shifts/manual-assign" -Method Post -Body $jsonManual -ContentType "application/json" | Out-Null

$payloadOverlap = @{ shift_name = "Morning"; start_date = "2030-01-07"; end_date = "2030-01-07"; start_time = "06:00"; end_time = "14:00"; optimization = "cost"; time_limit_seconds = 2; roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 2 } ); existing_users = @( @{ employee_id = "EMP-1"; name = "Overlap"; rate = 15.0; role = "Helper" }, @{ employee_id = "EMP-2"; name = "Safe"; rate = 15.0; role = "Helper" } ) }
Test-Api -Name "Same Day Overlap (Expect EMP-2 assigned, EMP-1 skipped)" -Payload $payloadOverlap

# 8. Next Day Assignment
$payloadNextDay = @{ shift_name = "Morning"; start_date = "2030-01-08"; end_date = "2030-01-08"; start_time = "06:00"; end_time = "14:00"; optimization = "cost"; time_limit_seconds = 2; roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 2 } ); existing_users = @( @{ employee_id = "EMP-1"; name = "Overlap"; rate = 15.0; role = "Helper" }, @{ employee_id = "EMP-2"; name = "Safe"; rate = 15.0; role = "Helper" } ) }
Test-Api -Name "Next Day Assignment (Expect BOTH assigned)" -Payload $payloadNextDay

