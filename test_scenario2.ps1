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
        Write-Host ($response.score_explanation -split "\n" | Select-String -Pattern "constraint" | Select-Object -First 15 | Out-String)
    } catch {}
}

$payload2 = @{
    shift_name = "Morning Shift"
    start_date = "2034-01-01"
    end_date = "2034-01-01"
    start_time = "08:00"
    end_time = "16:00"
    roles = @(
        @{ role_name = "Cook"; rating = 1; max_workers = 2; required_skills = @("grill") }
    )
    existing_users = @(
        @{ employee_id = "s2_e1"; name = "OnlyOneGuy"; role = "Cook"; rate = 10.0; unit = "hour"; rating = 3; employeeType = "Permanent"; skills = @("grill") }
    )
}
$payload2 | ConvertTo-Json -Depth 5 | Out-File "payload2.json" -Encoding utf8
Run-Scenario "Scenario 2: Overlap vs Understaffing" "payload2.json"
