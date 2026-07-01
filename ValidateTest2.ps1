$content = Get-Content .\target\test1.txt -Raw
$parts = $content -split "response ->"
$reqStr = $parts[0].Replace("request ->", "").Trim()
$resStr = $parts[1].Trim()
$res = $resStr | ConvertFrom-Json

Write-Host "Score: $($res.solver_score)"
Write-Host "Entities Planned: $($res.entities_planned)"
Write-Host "New Assignments Made: $($res.new_assignments_made)"
Write-Host "Skipped: $($res.skipped_count)"
