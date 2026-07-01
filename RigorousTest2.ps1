$employees = @()
for ($i=1; $i -le 10; $i++) {
    $employees += @{ name="Chef Cheap Low"; rate=40; unit="hour"; rating=1; role="Chef"; employee_id="C_CL_$i" }
    $employees += @{ name="Chef Expensive High"; rate=41; unit="hour"; rating=5; role="Chef"; employee_id="C_EH_$i" }
    $employees += @{ name="Dishwasher Cheap Low"; rate=15; unit="hour"; rating=1; role="Dishwasher"; employee_id="D_CL_$i" }
    $employees += @{ name="Dishwasher Expensive High"; rate=16; unit="hour"; rating=5; role="Dishwasher"; employee_id="D_EH_$i" }
}

$payload = @{
    start_date = "2026-05-01"
    end_date = "2026-05-01"
    shift_name = "Morning"
    start_time = "08:00"
    end_time = "16:00"
    time_limit_seconds = 5
    unimproved_time_limit_seconds = 2
    optimization = "both"
    roles = @(
        @{ role_name="Chef"; max_workers=5; rating=1 }
        @{ role_name="Dishwasher"; max_workers=5; rating=1 }
    )
    existing_users = $employees
}

Write-Host "
=== TESTING OPTIMIZATION: both (Small Wage Diff, Big Rating Diff) ==="
$json = $payload | ConvertTo-Json -Depth 10
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json"
    Write-Host "Score:" $response.solver_score
    
    $chefEH = 0; $chefCL = 0
    $dishEH = 0; $dishCL = 0
    
    foreach ($a in $response.assignments_by_date."2026-05-01") {
        if ($a.employeeName -like "Chef Expensive High") { $chefEH++ }
        if ($a.employeeName -like "Chef Cheap Low") { $chefCL++ }
        if ($a.employeeName -like "Dishwasher Expensive High") { $dishEH++ }
        if ($a.employeeName -like "Dishwasher Cheap Low") { $dishCL++ }
    }
    
    Write-Host "Assignments:"
    Write-Host "  Chefs: $chefEH Expensive/High, $chefCL Cheap/Low"
    Write-Host "  Dishwashers: $dishEH Expensive/High, $dishCL Cheap/Low"
} catch {
    Write-Host "Error:" $_.Exception.Message
}
