$report = @()
function Run-TestCase {
    param([string]$name, [hashtable]$payload, [scriptblock]$assertion)
    Write-Host "Running Test: $name..."
    $json = $payload | ConvertTo-Json -Depth 10
    
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/batch-assign-v3" -Method Post -Body $json -ContentType "application/json"
        $result = & $assertion $response
        if ($result -eq $true) { Write-Host "[PASS] $name" -ForegroundColor Green }
        else { Write-Host "[FAIL] $name" -ForegroundColor Red }
    } catch {
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $reader.BaseStream.Position = 0
            $responseBody = $reader.ReadToEnd()
            $responseObj = $responseBody | ConvertFrom-Json
            $result = & $assertion $responseObj
            if ($result -eq $true) { Write-Host "[PASS] $name (Handled via Error JSON)" -ForegroundColor Green }
            else { Write-Host "[FAIL] $name" -ForegroundColor Red; Write-Host $responseBody }
        }
    }
}

# 1. optimization = "invalid" -> 400
$payload_opt = @{ shifts = @( @{ start_date="2026-09-01"; end_date="2026-09-01"; shift_name="T1"; start_time="08:00"; end_time="16:00"; optimization="invalid"; roles=@(@{role_name="Chef"; max_workers=1; rating=4}); existing_users=@(@{name="Bob"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="B1"}) } ) }
Run-TestCase "optimization = 'invalid'" $payload_opt { param($res) if ($res.status -eq "error" -and $res.errors[0].missing_fields -contains "optimization (invalid value)") { return $true } return $false }

# 2. Duplicate employee_id in one shift (handled in solveShiftV3)
$payload_dup = @{ shifts = @( @{ start_date="2026-09-01"; end_date="2026-09-01"; shift_name="T2"; start_time="08:00"; end_time="16:00"; optimization="both"; roles=@(@{role_name="Chef"; max_workers=2; rating=4}); existing_users=@(@{name="Bob"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="B1"}, @{name="Bob2"; rate=25; unit="hour"; rating=5; role="Chef"; employee_id="B1"}) } ) }
Run-TestCase "Duplicate employee_id" $payload_dup { param($res) if ($res.shift_results[0].status -eq "error" -and $res.shift_results[0].error_message -match "Duplicate employee IDs") { return $true } return $false }

# 3. Same employee in multiple shifts in payload (Valid scenario, solver overlap checks it)
$payload_multi = @{ shifts = @( @{ start_date="2026-09-01"; end_date="2026-09-01"; shift_name="T3"; start_time="08:00"; end_time="16:00"; optimization="both"; roles=@(@{role_name="Chef"; max_workers=1; rating=4}); existing_users=@(@{name="Bob"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="B1"}) }, @{ start_date="2026-09-01"; end_date="2026-09-01"; shift_name="T4"; start_time="16:00"; end_time="22:00"; optimization="both"; roles=@(@{role_name="Chef"; max_workers=1; rating=4}); existing_users=@(@{name="Bob"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="B1"}) } ) }
Run-TestCase "Same employee in multiple shifts" $payload_multi { param($res) if ($res.overall_statistics.successful_shifts -eq 2) { return $true } return $false }

# 4. Empty roles
$payload_roles = @{ shifts = @( @{ start_date="2026-09-01"; end_date="2026-09-01"; shift_name="T5"; start_time="08:00"; end_time="16:00"; optimization="both"; roles=@(); existing_users=@(@{name="Bob"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="B1"}) } ) }
Run-TestCase "Empty roles" $payload_roles { param($res) if ($res.status -eq "error" -and $res.errors[0].missing_fields -contains "roles (cannot be empty)") { return $true } return $false }

# 5. max_workers = 0
$payload_workers = @{ shifts = @( @{ start_date="2026-09-01"; end_date="2026-09-01"; shift_name="T6"; start_time="08:00"; end_time="16:00"; optimization="both"; roles=@(@{role_name="Chef"; max_workers=0; rating=4}); existing_users=@(@{name="Bob"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="B1"}) } ) }
Run-TestCase "max_workers = 0" $payload_workers { param($res) if ($res.shift_results[0].status -eq "error" -and $res.shift_results[0].error_message -match "Max workers for role") { return $true } return $false }

# 6. Negative wage
$payload_wage = @{ shifts = @( @{ start_date="2026-09-01"; end_date="2026-09-01"; shift_name="T7"; start_time="08:00"; end_time="16:00"; optimization="both"; roles=@(@{role_name="Chef"; max_workers=1; rating=4}); existing_users=@(@{name="Bob"; rate=-5; unit="hour"; rating=5; role="Chef"; employee_id="B1"}) } ) }
Run-TestCase "Negative wage" $payload_wage { param($res) if ($res.shift_results[0].status -eq "error" -and $res.shift_results[0].error_message -match "Wage cannot be negative") { return $true } return $false }

