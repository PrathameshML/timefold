$report = @()
$report += "# Batch Assign V3 Rigorous Test Report
"

function Run-TestCase {
    param([string]$name, [hashtable]$payload, [scriptblock]$assertion)
    Write-Host "Running Test: $name..."
    $json = $payload | ConvertTo-Json -Depth 10
    
    $start = Get-Date
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/batch-assign-v3" -Method Post -Body $json -ContentType "application/json"
        $end = Get-Date
        $duration = ($end - $start).TotalSeconds
        
        $result = & $assertion $response
        if ($result -eq $true) {
            Write-Host "[PASS] $name" -ForegroundColor Green
            $global:report += "### ? PASS: $name"
            $global:report += "- Duration: $($duration.ToString('0.00'))s"
        } else {
            Write-Host "[FAIL] $name" -ForegroundColor Red
            $global:report += "### ? FAIL: $name"
        }
    } catch {
        # PowerShell 5.1 WebException handler
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $reader.BaseStream.Position = 0
            $responseBody = $reader.ReadToEnd()
            $responseObj = $responseBody | ConvertFrom-Json
            $end = Get-Date
            $duration = ($end - $start).TotalSeconds

            $result = & $assertion $responseObj
            if ($result -eq $true) {
                Write-Host "[PASS] $name (Handled via Error JSON)" -ForegroundColor Green
                $global:report += "### ? PASS: $name"
                $global:report += "- Duration: $($duration.ToString('0.00'))s"
            } else {
                Write-Host "[FAIL] $name" -ForegroundColor Red
                Write-Host "Response was: $($responseBody)"
                $global:report += "### ? FAIL: $name"
                $global:report += "- Duration: $($duration.ToString('0.00'))s"
            }
        } else {
            Write-Host "[ERROR] $name - $($_.Exception.Message)" -ForegroundColor Red
            $global:report += "### ? ERROR: $name"
        }
    }
}

# Edge Case 1: Empty Batch
$payload1 = @{ shifts = @() }
Run-TestCase -name "Edge Case 1: Empty Batch (Expect 400)" -payload $payload1 -assertion {
    param($res)
    if ($res.error -match "Missing required field") { return $true }
    return $false
}

# Edge Case 2: Validation Failure in one shift (Expect 400 for entire batch)
$payload2 = @{
    shifts = @(
        @{
            start_date = "2026-09-01"; end_date = "2026-09-01"
            shift_name = "Morning"; start_time = "08:00"; end_time = "16:00"
            optimization = "both"; roles = @( @{ role_name="Chef"; max_workers=2; rating=4 } )
            existing_users = @( @{ name="Bob"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="B1" } )
        },
        @{
            # Missing start_date!
            shift_name = "Evening"; start_time = "16:00"; end_time = "22:00"
            optimization = "both"; roles = @( @{ role_name="Chef"; max_workers=2; rating=4 } )
            existing_users = @( @{ name="Bob"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="B1" } )
        }
    )
}
Run-TestCase -name "Edge Case 2: Inner Shift Validation Failure (Expect 400)" -payload $payload2 -assertion {
    param($res)
    if ($res.status -eq "error" -and $res.errors[0].missing_fields -contains "start_date") { return $true }
    return $false
}

# Edge Case 3: Solver Error in one shift, Success in another
$payload3 = @{
    shifts = @(
        @{
            start_date = "2026-09-02"; end_date = "2026-09-02"
            shift_name = "Morning"; start_time = "08:00"; end_time = "16:00"
            optimization = "both"; roles = @( @{ role_name="Chef"; max_workers=2; rating=4 } )
            existing_users = @( @{ name="Bob"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="B1" } )
        },
        @{
            # Valid payload, but 0 eligible employees (no existing_users provided)
            start_date = "2026-09-02"; end_date = "2026-09-02"
            shift_name = "Evening"; start_time = "16:00"; end_time = "22:00"
            optimization = "both"; roles = @( @{ role_name="Chef"; max_workers=2; rating=4 } )
            existing_users = @()
        }
    )
}
Run-TestCase -name "Edge Case 3: Solver Exception isolation" -payload $payload3 -assertion {
    param($res)
    if ($res.overall_statistics.successful_shifts -eq 1 -and $res.overall_statistics.failed_shifts -eq 1) { return $true }
    return $false
}

# Edge Case 4: Overlapping Shifts (Cross-Shift Constraints)
$emp4 = @()
for ($i=1; $i -le 3; $i++) {
    $emp4 += @{ name="C_$i"; rate=20; unit="hour"; rating=5; role="Chef"; employee_id="C_$i" }
}
$payload4 = @{
    shifts = @(
        @{
            start_date = "2026-09-03"; end_date = "2026-09-03"
            shift_name = "Morning"; start_time = "08:00"; end_time = "16:00"
            optimization = "both"; roles = @( @{ role_name="Chef"; max_workers=2; rating=4 } )
            existing_users = $emp4
        },
        @{
            start_date = "2026-09-03"; end_date = "2026-09-03"
            shift_name = "Evening"; start_time = "16:00"; end_time = "22:00"
            optimization = "both"; roles = @( @{ role_name="Chef"; max_workers=2; rating=4 } )
            existing_users = $emp4
        }
    )
}
Run-TestCase -name "Edge Case 4: Cross-Shift Overlap constraints" -payload $payload4 -assertion {
    param($res)
    # 3 total employees available. Morning wants 2. Evening wants 2.
    # Total assigned across batch should be EXACTLY 3! (Because no one can work 2 shifts on the same day).
    if ($res.overall_statistics.total_assignments_made -eq 3) { return $true }
    return $false
}
