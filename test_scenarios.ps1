$ErrorActionPreference = 'Stop'
function Run-Scenario {
    param([string]$ScenarioName, [string]$PayloadFile)
    Write-Host "`n==============================================="
    Write-Host "Running Scenario: $ScenarioName"
    Write-Host "==============================================="
    try {
        $json = Get-Content -Raw $PayloadFile
        $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v2" -Method Post -Body $json -ContentType "application/json"
        
        Write-Host "Score: $($response.solver_score)"
        
        # Display assignments by filtering assignments_by_date logic, but API v2 returns raw response
        # We can just look at score_explanation
        Write-Host ($response.score_explanation -split "\n" | Select-String -Pattern "constraint" | Select-Object -First 15 | Out-String)
    } catch {
        Write-Host "ERROR: $($_.Exception.Message)"
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            Write-Host $reader.ReadToEnd()
        }
    }
}

$payload1 = @{
    shift_name = "Morning Shift"
    start_date = "2031-01-01"
    end_date = "2031-01-01"
    start_time = "08:00"
    end_time = "16:00"
    roles = @(
        @{ role_name = "Cook"; rating = 1; max_workers = 1; required_skills = @("grill", "prep") }
    )
    existing_users = @(
        @{ employee_id = "s1_e1"; name = "MissingSkillBob"; role = "Cook"; rate = 10.0; unit = "hour"; rating = 3; employeeType = "Permanent"; skills = @("grill") }
    )
}
$payload1 | ConvertTo-Json -Depth 5 | Out-File "payload1.json" -Encoding utf8
Run-Scenario "Scenario 1: Skill Mismatch vs Understaffing" "payload1.json"

$payload3 = @{
    shift_name = "Morning Shift"
    start_date = "2032-01-01"
    end_date = "2032-01-01"
    start_time = "08:00"
    end_time = "16:00"
    roles = @(
        @{ role_name = "Cook"; rating = 1; max_workers = 1; required_skills = @("grill", "prep") }
    )
    existing_users = @(
        @{ employee_id = "s3_e1"; name = "ExpensiveExpert"; role = "Cook"; rate = 50.0; unit = "hour"; rating = 5; employeeType = "Permanent"; skills = @("grill", "prep") },
        @{ employee_id = "s3_e2"; name = "CheapAverage"; role = "Cook"; rate = 10.0; unit = "hour"; rating = 3; employeeType = "Permanent"; skills = @("grill", "prep") }
    )
}
$payload3 | ConvertTo-Json -Depth 5 | Out-File "payload3.json" -Encoding utf8
Run-Scenario "Scenario 3: Expensive Expert vs Cheap Average" "payload3.json"

$payload4 = @{
    shift_name = "Morning Shift"
    start_date = "2033-01-01"
    end_date = "2033-01-01"
    start_time = "08:00"
    end_time = "16:00"
    prioritize_permanent = $true
    roles = @(
        @{ role_name = "Cook"; rating = 1; max_workers = 1; required_skills = @("grill", "prep") }
    )
    existing_users = @(
        @{ employee_id = "s4_e1"; name = "Contractor"; role = "Cook"; rate = 10.0; unit = "hour"; rating = 3; employeeType = "Contractor"; skills = @("grill", "prep") },
        @{ employee_id = "s4_e2"; name = "Permanent"; role = "Cook"; rate = 10.0; unit = "hour"; rating = 3; employeeType = "Permanent"; skills = @("grill", "prep") }
    )
}
$payload4 | ConvertTo-Json -Depth 5 | Out-File "payload4.json" -Encoding utf8
Run-Scenario "Scenario 4: Contractor vs Permanent" "payload4.json"

