$content = Get-Content .\target\test1.txt -Raw
$parts = $content -split "response ->"
$reqStr = $parts[0].Replace("request ->", "").Trim()
$resStr = $parts[1].Trim()

$req = $reqStr | ConvertFrom-Json
$res = $resStr | ConvertFrom-Json

Write-Host "Req optimization: $($req.optimization)"
Write-Host "Roles requested:"
$req.roles | ForEach-Object { Write-Host "$($_.role_name): max=$($_.max_workers) rating=$($_.rating)" }

Write-Host "
Res Score: $($res.solver_score)"
Write-Host "Entities Planned: $($res.entities_planned)"
Write-Host "New Assignments Made: $($res.new_assignments_made)"

# Let's count by date
$totalAssigned = 0
foreach ($date in $res.assignments_by_date.psobject.properties.name) {
    $arr = $res.assignments_by_date.$date
    Write-Host "Date $date assigned: $($arr.Length)"
    $totalAssigned += $arr.Length
}
Write-Host "Total physically assigned in JSON: $totalAssigned"
