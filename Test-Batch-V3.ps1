$emp1 = @()
for ($i=1; $i -le 10; $i++) {
    $emp1 += @{ name="R5"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="E_R5_$i" }
}

$payload = @{
    shifts = @(
        @{
            start_date = "2026-08-01"; end_date = "2026-08-01"
            shift_name = "Morning1"; start_time = "08:00"; end_time = "16:00"
            optimization = "both"; existing_users = $emp1
            roles = @( @{ role_name="Chef"; max_workers=5; rating=4 } )
        },
        @{
            start_date = "2026-08-02"; end_date = "2026-08-02"
            shift_name = "Morning2"; start_time = "08:00"; end_time = "16:00"
            optimization = "both"; existing_users = $emp1
            roles = @( @{ role_name="Chef"; max_workers=2; rating=4 } )
        }
    )
}

$json = $payload | ConvertTo-Json -Depth 10

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/batch-assign-v3" -Method Post -Body $json -ContentType "application/json"
    $response | ConvertTo-Json -Depth 5
} catch {
    Write-Host "$($_.Exception.Message)" -ForegroundColor Red
}
