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

# 500 Employees, 7 Days. Need 50 Workers per day (3500 entities total).
$employees500 = Generate-Employees -Count 500

$payload60s = @{
    shift_name = "Morning"
    start_date = "2027-05-01"
    end_date = "2027-05-07"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "cost"
    time_limit_seconds = 60
    unimproved_time_limit_seconds = 15
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 50 } )
    existing_users = $employees500
}
Test-Api -Name "500 Employees, 7 Days (Cost, 60s limit, Fresh Dates)" -Payload $payload60s

$payload120s = @{
    shift_name = "Morning"
    start_date = "2027-05-08"
    end_date = "2027-05-14"
    start_time = "06:00"
    end_time = "14:00"
    optimization = "cost"
    time_limit_seconds = 120
    unimproved_time_limit_seconds = 30
    roles = @( @{ role_name = "Helper"; rating = 1; max_workers = 50 } )
    existing_users = $employees500
}
Test-Api -Name "500 Employees, 7 Days (Cost, 120s limit, Fresh Dates)" -Payload $payload120s

