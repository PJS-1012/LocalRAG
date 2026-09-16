$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\dev-start.ps1') -NoBrowser

function Assert-Equal {
    param($Expected, $Actual, [string]$Case)
    if ($Expected -ne $Actual) { throw "$Case expected '$Expected' but got '$Actual'" }
    Write-Host "PASS $Case -> $Actual"
}

Assert-Equal 'REUSE' (Resolve-StartupAction $true $true) 'A running dependency is reused'
Assert-Equal 'START' (Resolve-DockerAction $false $false $true) 'B stopped Docker follows startup flow'
Assert-Equal 'NOT_RUNNING' (Resolve-DockerAction $false $false $false) 'B missing Docker Desktop fails clearly'
Assert-Equal 'START' (Resolve-PostgresAction $false $false) 'C stopped PostgreSQL follows startup flow'
Assert-Equal 'START' (Resolve-OllamaAction $false $false $true) 'D unavailable Ollama follows startup flow'
Assert-Equal 'NOT_INSTALLED' (Resolve-OllamaAction $false $false $false) 'D missing Ollama fails clearly'
Assert-Equal 'PORT_IN_USE' (Resolve-StartupAction $false $true) 'E Backend port conflict fails safely'
Assert-Equal 'PORT_IN_USE' (Resolve-StartupAction $false $true) 'F Frontend port conflict fails safely'

$postgresArguments = Get-PostgresStartArguments
Assert-Equal 'compose --file' (($postgresArguments | Select-Object -First 2) -join ' ') 'Compose command prefix is fixed'
Assert-Equal 'up --detach postgres' (($postgresArguments | Select-Object -Last 3) -join ' ') 'Only postgres is started'

$launcher = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\dev-start.ps1') -Raw
foreach ($forbidden in @('docker rm', 'compose down', 'down --volumes', 'Stop-Process', 'taskkill', 'ollama pull')) {
    if ($launcher -match [regex]::Escape($forbidden)) { throw "Forbidden launcher operation found: $forbidden" }
}
Write-Host 'PASS destructive and model-pull operations are absent'
