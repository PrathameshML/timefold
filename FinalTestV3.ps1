$report = @()
$report += "# V3 Final Rigorous Test Report
"

function Run-TestCase {
    param([string]$name, [hashtable]$payload, [scriptblock]$assertion)
    Write-Host "Running Test: $name..."
    $json = $payload | ConvertTo-Json -Depth 10
    
    $start = Get-Date
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json"
        $end = Get-Date
        $duration = ($end - $start).TotalSeconds
        
        $result = & $assertion $response
        if ($result -eq $true) {
            Write-Host "[PASS] $name" -ForegroundColor Green
            $global:report += "### ? PASS: $name"
            $global:report += "- Duration: $($duration.ToString('0.00'))s"
            $global:report += "- Score: $($response.solver_score)"
        } else {
            Write-Host "[FAIL] $name" -ForegroundColor Red
            Write-Host "Response score was: $($response.solver_score)"
            $global:report += "### ? FAIL: $name"
            $global:report += "- Duration: $($duration.ToString('0.00'))s"
            $global:report += "- Score: $($response.solver_score)"
        }
    } catch {
        Write-Host "[ERROR] $name - $($_.Exception.Message)" -ForegroundColor Red
        $global:report += "### ? ERROR: $name"
        $global:report += "- Exception: $($_.Exception.Message)"
    }
}

# SCENARIO 1: Basic Hard Constraints (Rating & Limits)
$emp1 = @()
for ($i=1; $i -le 10; $i++) {
    $emp1 += @{ name="R5"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="E_R5_$i" }
    $emp1 += @{ name="R2"; rate=20; unit="hour"; rating=2; role="Chef"; employee_id="E_R2_$i" }
}
$payload1 = @{
    start_date = "2026-07-01"; end_date = "2026-07-01"
    shift_name = "Morning"; start_time = "08:00"; end_time = "16:00"
    optimization = "both"; existing_users = $emp1
    roles = @( @{ role_name="Chef"; max_workers=5; rating=4 } )
}
Run-TestCase -name "Hard Constraints: Rating Minimum & Max Workers" -payload $payload1 -assertion {
    param($res)
    $assignments = $res.assignments_by_date."2026-07-01"
    if ($assignments.Count -ne 5) { return $false }
    foreach ($a in $assignments) { if ($a.rating -lt 4) { return $false } }
    return $true
}

# SCENARIO 2: Short-Staffed Medium Score Penalty
$payload2 = @{
    start_date = "2026-07-02"; end_date = "2026-07-02"
    shift_name = "Morning"; start_time = "08:00"; end_time = "16:00"
    optimization = "cost"; existing_users = $emp1 # Has 20 chefs
    roles = @( @{ role_name="Chef"; max_workers=30; rating=1 } ) # Wants 30
}
Run-TestCase -name "Medium Score: Short-Staffed Penalty" -payload $payload2 -assertion {
    param($res)
    $assignments = $res.assignments_by_date."2026-07-02"
    if ($assignments.Count -ne 20) { return $false } # Can only assign 20
    if ($res.solver_score -notmatch "-100000medium") { return $false } # 10 missing * 10,000
    return $true
}

# SCENARIO 3: Per-Role Optimization 'Both'
$emp3 = @()
for ($i=1; $i -le 5; $i++) {
    $emp3 += @{ name="Chef Cheap"; rate=10; unit="hour"; rating=1; role="Chef"; employee_id="C_C_$i" }
    $emp3 += @{ name="Chef Expensive"; rate=12; unit="hour"; rating=5; role="Chef"; employee_id="C_E_$i" }
}
$payload3 = @{
    start_date = "2026-07-03"; end_date = "2026-07-03"
    shift_name = "Morning"; start_time = "08:00"; end_time = "16:00"
    optimization = "both"; existing_users = $emp3
    roles = @( @{ role_name="Chef"; max_workers=5; rating=1 } )
}
Run-TestCase -name "Per-Role Average: High Rating justifies slight wage bump" -payload $payload3 -assertion {
    param($res)
    $assignments = $res.assignments_by_date."2026-07-03"
    $expCount = 0
    foreach ($a in $assignments) { if ($a.employeeName -like "*Expensive*") { $expCount++ } }
    return ($expCount -eq 5)
}

# SCENARIO 4: Time Limit Formula Scaling
$emp4 = @()
for ($i=1; $i -le 100; $i++) {
    $emp4 += @{ name="W_$i"; rate=20; unit="hour"; rating=5; role="Waiter"; employee_id="W_$i" }
}
$payload4 = @{
    start_date = "2026-07-04"; end_date = "2026-07-08" # 5 days * 100 emp = 500 entities
    shift_name = "Morning"; start_time = "08:00"; end_time = "16:00"
    optimization = "cost"; existing_users = $emp4
    roles = @( @{ role_name="Waiter"; max_workers=50; rating=1 } )
}
Run-TestCase -name "Time Limit Formula Scaling (500 entities)" -payload $payload4 -assertion {
    param($res)
    # Total entities = 500. Limit = 2 + (500/20) = 27s. Unimproved = 27/4 = 6s.
    if ($res.solver_time_seconds -lt 4 -or $res.solver_time_seconds -gt 15) { return $false }
    return $true
}

$global:report | Set-Content .\V3_Final_Test_Report.md
