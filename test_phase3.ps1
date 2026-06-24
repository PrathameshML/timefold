$body = @{
    shiftName = "Morning"
    workingDates = @("2026-06-15")
    prioritizePermanent = $true
    scheduleBreaks = $true
    roleLimits = @(
        @{
            roleName = "Doctor"
            maxWorkers = 1
        }
    )
    ratingRequirements = @()
} | ConvertTo-Json -Depth 5

$response = Invoke-RestMethod -Uri "http://localhost:8083/shifts/assign" -Method Post -Headers @{"Content-Type" = "application/json"} -Body $body

$response | ConvertTo-Json -Depth 5
