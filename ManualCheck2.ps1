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
$totalSoftScore = 0

foreach ($roleKey in $roles.Keys) {
    $role = $roles[$roleKey]
    $eligibleEmps = @()
    foreach ($emp in $reqJson.existing_users) {
        if ($emp.role -eq $roleKey -and $emp.rating -ge $role.rating) {
            $eligibleEmps += $emp
        }
    }
    
    # In 'both' mode, we maximize (rating * 100) - (wageRatio * 1000)
    # Sort by NetScore descending
    $eligibleEmps = $eligibleEmps | Sort-Object -Property @{
        Expression={ ($_.rating * 100) - [math]::Floor(($_.rate / $avgWage) * 1000) }; 
        Descending=$true 
    }
    
    $assignedEmps = $eligibleEmps | Select-Object -First $role.max_workers
    
    $totalAssigned += $assignedEmps.Count
    foreach ($emp in $assignedEmps) {
        $reward = $emp.rating * 100
        $penalty = [math]::Floor(($emp.rate / $avgWage) * 1000)
        $totalSoftScore += ($reward - $penalty)
    }
}

Write-Host "Manual Average Wage: $avgWage"
Write-Host "Manual Assigned (per day): $totalAssigned"
Write-Host "Manual Soft Score (per day): $totalSoftScore"
Write-Host "Manual Soft Score (9 days): $($totalSoftScore * 9)"
