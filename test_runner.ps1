param($payloadFile)
$uri = "http://localhost:8083/api/v2/shifts/assign"
$headers = @{
    "Content-Type" = "application/json"
}
$body = Get-Content -Raw -Path $payloadFile
$response = Invoke-RestMethod -Uri $uri -Method Post -Headers $headers -Body $body
$response | ConvertTo-Json -Depth 10
