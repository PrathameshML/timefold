param (
    [Parameter(Mandatory=$true)]
    [string]$Ec2UserAndIp,

    [Parameter(Mandatory=$true)]
    [string]$PemKeyPath
)

$SourcePath = ".\target\quarkus-app"
if (-Not (Test-Path $SourcePath)) {
    Write-Host "Error: target/quarkus-app not found. Did the build fail?" -ForegroundColor Red
    exit 1
}

Write-Host "Starting Deployment to $Ec2UserAndIp..." -ForegroundColor Cyan

Write-Host "Uploading files via SCP..."
scp -i "$PemKeyPath" -r "$SourcePath" "$Ec2UserAndIp:~/"

Write-Host "Starting application on the EC2 server..."
$sshCommand = @'
    echo 'Checking for running Java processes...'
    killall java || true
    echo 'Starting Timefold application in the background...'
    cd ~/quarkus-app
    nohup java -jar quarkus-run.jar > timefold.log 2>&1 &
    echo 'Application started! Logs are being written to ~/quarkus-app/timefold.log'
'@

ssh -i "$PemKeyPath" $Ec2UserAndIp $sshCommand

Write-Host "Deployment Complete!" -ForegroundColor Green
Write-Host "The app should now be running on port 8080 (make sure AWS Security Groups allow inbound traffic)." -ForegroundColor Yellow
