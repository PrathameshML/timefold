function Generate-Employees {
    param([int]$Count)
    $employees = @()
    for ($i = 1; $i -le $Count; $i++) {
        # Random wage between 10.0 and 40.0
        $wage = [math]::Round((Get-Random -Minimum 10.0 -Maximum 40.1), 1)
        # Random rating between 1 and 5
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
    param([string]$Name, [hashtable]$Payload)
    Write-Host "========================================="
    Write-Host "Running Test: $Name"
    
    $json = $Payload | ConvertTo-Json -Depth 10
    $start = Get-Date
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json" -TimeoutSec 300
        $end = Get-Date
        $elapsed = ($end - $start).TotalSeconds
        
        Write-Host "Time taken (Network + Solve): $elapsed seconds"
        Write-Host "Solver reported time: $($response.solver_time_seconds) seconds"
        Write-Host "Score: $($response.solver_score)"
        Write-Host "Assignments Made: $($response.new_assignments_made)"
        
        # Calculate max, min wage etc if assignments made
        if ($response.new_assignments_made -gt 0) {
            $totalWage = 0
            $totalRating = 0
            $count = 0
            foreach ($date in $response.assignments_by_date.PSObject.Properties.Name) {
                foreach ($a in $response.assignments_by_date."$date") {
                    $totalWage += $a.wage
                    $totalRating += $a.rating
                    $count++
                }
            }
            if ($count -gt 0) {
                Write-Host "Average Assigned Wage: $([math]::Round($totalWage / $count, 2))"
                Write-Host "Average Assigned Rating: $([math]::Round($totalRating / $count, 2))"
            }
        }
        
    } catch {
        Write-Host "Error: $($_.Exception.Message)"
    }
    Write-Host ""
}

# 1. Edge Case: Double booking across dates? (V3 does a single request per API call)
# 2. Medium Scale: 100 Employees, 1 Day, Need 20 Workers. Time: 2 seconds
$employees100 = Generate-Employees -Count 100
$payload100 = @{
    shift_name = "Morning"
    start_date = "2027-05-01"
    end_date = "2027-05-01"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "cost"
    time_limit_seconds = 2
    unimproved_time_limit_seconds = 1
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 20 } )
    existing_users = $employees100
}
Test-Api -Name "100 Employees, 1 Day (Cost, 2s limit)" -Payload $payload100

$payload100.optimization = "quality"
Test-Api -Name "100 Employees, 1 Day (Quality, 2s limit)" -Payload $payload100

# 3. Large Scale: 500 Employees, 1 Day. Need 50 Workers. Time limits: 2s, 5s, 10s
$employees500 = Generate-Employees -Count 500
$payload500 = @{
    shift_name = "Morning"
    start_date = "2027-05-02"
    end_date = "2027-05-02"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "cost"
    time_limit_seconds = 2
    unimproved_time_limit_seconds = 1
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 50 } )
    existing_users = $employees500
}
Test-Api -Name "500 Employees, 1 Day (Cost, 2s limit)" -Payload $payload500

$payload500.time_limit_seconds = 5
$payload500.unimproved_time_limit_seconds = 2
Test-Api -Name "500 Employees, 1 Day (Cost, 5s limit)" -Payload $payload500

$payload500.time_limit_seconds = 10
$payload500.unimproved_time_limit_seconds = 3
Test-Api -Name "500 Employees, 1 Day (Cost, 10s limit)" -Payload $payload500

# 4. Massive Scale: 500 Employees, 7 Days. Need 50 Workers per day (3500 entities total). Time limits: 5s, 15s, 30s
$payloadMassive = @{
    shift_name = "Morning"
    start_date = "2027-05-03"
    end_date = "2027-05-09"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "cost"
    time_limit_seconds = 5
    unimproved_time_limit_seconds = 3
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 50 } )
    existing_users = $employees500
}
Test-Api -Name "500 Employees, 7 Days (Cost, 5s limit)" -Payload $payloadMassive

$payloadMassive.time_limit_seconds = 15
$payloadMassive.unimproved_time_limit_seconds = 5
Test-Api -Name "500 Employees, 7 Days (Cost, 15s limit)" -Payload $payloadMassive

$payloadMassive.time_limit_seconds = 30
$payloadMassive.unimproved_time_limit_seconds = 10
Test-Api -Name "500 Employees, 7 Days (Cost, 30s limit)" -Payload $payloadMassive
