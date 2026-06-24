$ErrorActionPreference = 'Stop'

function Run-Scenario {
    param([string]$ScenarioName, [string]$PayloadFile)
    Write-Host "`n==============================================="
    Write-Host "Running: $ScenarioName"
    Write-Host "==============================================="
    try {
        $json = Get-Content -Raw $PayloadFile
        $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v2" -Method Post -Body $json -ContentType "application/json"
        
        Write-Host "Score: $($response.solver_score)"
        Write-Host "Time: $($response.solver_time_seconds)s"
        Write-Host "Entities Planned: $($response.entities_planned)"
        Write-Host "Score Explanation:"
        Write-Host ($response.score_explanation -split "`n" | Select-String -Pattern "constraint" | Select-Object -First 20 | Out-String)
    } catch {
        Write-Host "ERROR: $($_.Exception.Message)"
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            Write-Host $reader.ReadToEnd()
        }
    }
}

$users = @()
for ($i=1; $i -le 5; $i++) {
    $users += @{ employee_id = "e$i"; name = "Manager $i"; role = "Manager"; rate = 50.0; unit = "hour"; rating = 5; employeeType = "Permanent"; skills = @("management") }
}
for ($i=6; $i -le 15; $i++) {
    $rating = ($i % 4) + 2 # Ratings 2 to 5
    $skills = if ($i % 2 -eq 0) { @("grill", "prep") } else { [string[]]@("grill") }
    $type = if ($i % 3 -eq 0) { "Contractor" } else { "Permanent" }
    $users += @{ employee_id = "e$i"; name = "Cook $i"; role = "Cook"; rate = 25.0; unit = "hour"; rating = $rating; employeeType = $type; skills = $skills }
}
for ($i=16; $i -le 25; $i++) {
    $rating = ($i % 5) + 1 # Ratings 1 to 5
    $skills = if ($i -eq 25) { @() } else { [string[]]@("service") } # e25 has no skills
    $users += @{ employee_id = "e$i"; name = "Waiter $i"; role = "Waiter"; rate = 12.0; unit = "hour"; rating = $rating; employeeType = "Permanent"; skills = $skills }
}

$roles = @(
    @{ role_name = "Manager"; rating = 4; max_workers = 1; required_skills = @("management") },
    @{ role_name = "Cook"; rating = 3; max_workers = 3; required_skills = @("grill", "prep") },
    @{ role_name = "Waiter"; rating = 1; max_workers = 5; required_skills = @("service") }
)

$payloadMorning = @{
    shift_name = "Morning Shift"
    start_date = "2035-05-01"
    end_date = "2035-05-07"
    start_time = "08:00"
    end_time = "16:00"
    prioritize_permanent = $true
    roles = $roles
    existing_users = $users
}
$payloadMorning | ConvertTo-Json -Depth 5 | Out-File "payload_morning.json" -Encoding utf8

$payloadEvening = @{
    shift_name = "Evening Shift"
    start_date = "2035-05-01"
    end_date = "2035-05-07"
    start_time = "16:00"
    end_time = "23:59"
    prioritize_permanent = $true
    roles = $roles
    existing_users = $users
}
$payloadEvening | ConvertTo-Json -Depth 5 | Out-File "payload_evening.json" -Encoding utf8

Run-Scenario "Morning Shift (7 Days, 25 Employees)" "payload_morning.json"
Run-Scenario "Evening Shift (7 Days, 25 Employees) - Stresses Overlap/Max Hours" "payload_evening.json"
