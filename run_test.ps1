$ErrorActionPreference = 'Stop'

Write-Host "Starting Quarkus server..."
$process = Start-Process -FilePath "mvn.cmd" -ArgumentList "quarkus:dev" -PassThru -WindowStyle Hidden

Write-Host "Waiting for server to start on port 8083..."
$started = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8083/constraints" -UseBasicParsing -ErrorAction SilentlyContinue
        if ($response.StatusCode -eq 200) {
            $started = $true
            break
        }
    } catch {
        # Ignore and retry
    }
    Start-Sleep -Seconds 2
}

if (-not $started) {
    Write-Host "Server failed to start in time!"
    Stop-Process -Id $process.Id -Force
    exit 1
}

Write-Host "Server started! Sending test payload to V2 API..."
try {
    $json = Get-Content -Raw test_skills_payload.json
    $response = Invoke-WebRequest -Uri "http://localhost:8083/shifts/assign-v2" -Method Post -Body $json -ContentType "application/json" -UseBasicParsing
    
    Write-Host "Response received:"
    Write-Host $response.Content
} catch {
    Write-Host "Error during API call:"
    Write-Host $_.Exception.Message
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $reader.ReadToEnd() | Write-Host
    }
} finally {
    Write-Host "Stopping Quarkus server..."
    Stop-Process -Id $process.Id -Force
}
