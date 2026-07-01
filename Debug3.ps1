$emp1 = @()
for ($i=1; $i -le 10; $i++) {
    $emp1 += @{ name="R5"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="E_R5_$i" }
    $emp1 += @{ name="R2"; rate=20; unit="hour"; rating=2; role="Chef"; employee_id="E_R2_$i" }
}
$payload2 = @{
    start_date = "2026-06-01"; end_date = "2026-06-01"
    shift_name = "Morning"; start_time = "08:00"; end_time = "16:00"
    optimization = "both"; existing_users = $emp1 # Has 20 chefs
    roles = @( @{ role_name="Chef"; max_workers=30; rating=1 } ) # Wants 30
}
$json = $payload2 | ConvertTo-Json -Depth 10
$response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign-v3" -Method Post -Body $json -ContentType "application/json"
$response | ConvertTo-Json -Depth 3
