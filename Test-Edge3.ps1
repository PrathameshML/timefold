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
        }
    }
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
            # Valid payload structure, but triggers ClassCastException because rating is a string
            start_date = "2026-09-02"; end_date = "2026-09-02"
            shift_name = "Evening"; start_time = "16:00"; end_time = "22:00"
            optimization = "both"; roles = @( @{ role_name="Chef"; max_workers=2; rating=4 } )
            existing_users = @( @{ name="Alice"; rate=20; unit="hour"; rating="abc"; role="Chef"; employee_id="A1" } )
        }
    )
}
Run-TestCase -name "Edge Case 3: Solver Exception isolation" -payload $payload3 -assertion {
    param($res)
    if ($res.overall_statistics.successful_shifts -eq 1 -and $res.overall_statistics.failed_shifts -eq 1) { return $true }
    return $false
}

