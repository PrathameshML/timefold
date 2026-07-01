$jsonText = Get-Content .\target\test1.txt -Raw
$reqJsonStr = $jsonText.Substring($jsonText.IndexOf("{"), $jsonText.IndexOf("responce ->") - $jsonText.IndexOf("{")).Trim()
$reqJson = $reqJsonStr | ConvertFrom-Json

$roles = @{}
foreach ($r in $reqJson.roles) {
    $roles[$r.role_name] = $r
}

$sumWage = 0
$countWage = 0
foreach ($emp in $reqJson.existing_users) {
    if ($roles.ContainsKey($emp.role)) {
        $sumWage += $emp.rate
        $countWage++
    }
}
$avgWage = if ($countWage -gt 0) { $sumWage / $countWage } else { 1.0 }

$totalAssigned = 0
$totalSoftPenalty = 0

foreach ($roleKey in $roles.Keys) {
    $role = $roles[$roleKey]
    $eligibleEmps = @()
    foreach ($emp in $reqJson.existing_users) {
        if ($emp.role -eq $roleKey -and $emp.rating -ge $role.rating) {
            $eligibleEmps += $emp
        }
    }
    
    $sortedEmps = $eligibleEmps | Sort-Object rate
    $assignedEmps = $sortedEmps | Select-Object -First $role.max_workers
    
    $totalAssigned += $assignedEmps.Count
    foreach ($emp in $assignedEmps) {
        $penalty = [math]::Floor(($emp.rate / $avgWage) * 1000)
        $totalSoftPenalty += $penalty
    }
}

Write-Host "Manual Average Wage: $avgWage"
Write-Host "Manual Total Assigned: $totalAssigned"
Write-Host "Manual Soft Penalty: $totalSoftPenalty"
