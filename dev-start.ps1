[CmdletBinding()]
param(
    [switch]$NoBrowser,
    [switch]$CheckOnly,
    [int]$DockerTimeoutSeconds = 120,
    [int]$ServiceTimeoutSeconds = 60
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:ComposeFile = Join-Path $script:ProjectRoot 'compose.yml'
$script:FrontendRoot = Join-Path $script:ProjectRoot 'frontend'
$script:RuntimeRoot = Join-Path $script:ProjectRoot '.localrag'
$script:LogRoot = Join-Path $script:RuntimeRoot 'logs'
$script:BackendPort = 18080
$script:FrontendPort = 5173
$script:RequiredModels = @('qwen3:8b', 'qwen3-embedding:0.6b')

function Write-LauncherStep {
    param([string]$Code, [string]$Message)
    Write-Host "[$Code] $Message"
}

function Resolve-StartupAction {
    param([bool]$Ready, [bool]$PortInUse = $false)
    if ($Ready) { return 'REUSE' }
    if ($PortInUse) { return 'PORT_IN_USE' }
    return 'START'
}

function Resolve-DockerAction {
    param([bool]$Ready, [bool]$CheckOnlyMode, [bool]$DesktopAvailable)
    if ($Ready) { return 'REUSE' }
    if ($CheckOnlyMode) { return 'NOT_RUNNING' }
    if (-not $DesktopAvailable) { return 'NOT_RUNNING' }
    return 'START'
}

function Resolve-PostgresAction {
    param([bool]$Ready, [bool]$CheckOnlyMode)
    if ($Ready) { return 'REUSE' }
    if ($CheckOnlyMode) { return 'UNAVAILABLE' }
    return 'START'
}

function Resolve-OllamaAction {
    param([bool]$Ready, [bool]$CheckOnlyMode, [bool]$ExecutableAvailable)
    if ($Ready) { return 'REUSE' }
    if ($CheckOnlyMode) { return 'NOT_RUNNING' }
    if (-not $ExecutableAvailable) { return 'NOT_INSTALLED' }
    return 'START'
}

function Test-TcpPort {
    param([int]$Port)
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        return $task.Wait(400) -and $client.Connected
    }
    catch { return $false }
    finally { $client.Dispose() }
}

function Get-PortOwnerDescription {
    param([int]$Port)
    try {
        $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            Select-Object -First 1
        if (-not $connection) { return "port $Port" }
        $process = Get-Process -Id $connection.OwningProcess -ErrorAction SilentlyContinue
        $name = if ($process) { $process.ProcessName } else { 'unknown process' }
        return "port $Port (PID $($connection.OwningProcess), $name)"
    }
    catch { return "port $Port" }
}

function Get-PortOwnerPid {
    param([int]$Port)
    try {
        return (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            Select-Object -First 1 -ExpandProperty OwningProcess)
    }
    catch { return $null }
}

function Test-HttpEndpoint {
    param([string]$Uri, [int]$TimeoutSeconds = 2)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec $TimeoutSeconds
        return $response.StatusCode -eq 200
    }
    catch { return $false }
}

function Test-LocalRagBackend {
    if (-not (Test-HttpEndpoint 'http://127.0.0.1:18080/actuator/health')) { return $false }
    return Test-HttpEndpoint 'http://127.0.0.1:18080/api/workspaces/discovery' 5
}

function Test-LocalRagFrontend {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5173' -TimeoutSec 2
        return $response.StatusCode -eq 200 -and $response.Content -match '<title>LocalRAG Workbench</title>'
    }
    catch { return $false }
}

function Wait-ForCondition {
    param([scriptblock]$Condition, [int]$TimeoutSeconds, [string]$FailureCode, [string]$FailureMessage)
    $deadline = [DateTimeOffset]::Now.AddSeconds($TimeoutSeconds)
    do {
        if (& $Condition) { return }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::Now -lt $deadline)
    throw "[$FailureCode] $FailureMessage"
}

function Get-DockerDesktopPath {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'),
        (Join-Path $env:LOCALAPPDATA 'Docker\Docker Desktop.exe')
    )
    return $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
}

function Test-DockerReady {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { return $false }
    & docker info --format '{{.ServerVersion}}' *> $null
    return $LASTEXITCODE -eq 0
}

function Ensure-Docker {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw '[DOCKER_NOT_INSTALLED] docker CLI was not found. Check the Docker Desktop installation.'
    }
    $desktop = Get-DockerDesktopPath
    $action = Resolve-DockerAction (Test-DockerReady) ([bool]$CheckOnly) ([bool]$desktop)
    if ($action -eq 'REUSE') {
        Write-LauncherStep 'DOCKER_READY' 'Reusing the running Docker Engine.'
        return
    }
    if ($action -eq 'NOT_RUNNING' -and $CheckOnly) { throw '[DOCKER_NOT_RUNNING] Docker Engine is not running.' }
    if ($action -eq 'NOT_RUNNING') { throw '[DOCKER_NOT_RUNNING] Docker Desktop executable was not found. Start Docker Desktop manually.' }
    Write-LauncherStep 'DOCKER_STARTING' "Starting Docker Desktop: $desktop"
    Start-Process -FilePath $desktop -WindowStyle Hidden | Out-Null
    Wait-ForCondition ${function:Test-DockerReady} $DockerTimeoutSeconds 'DOCKER_NOT_RUNNING' 'Docker Engine did not become ready before the timeout.'
    Write-LauncherStep 'DOCKER_READY' 'Docker Engine is ready.'
}

function Get-PostgresContainerId {
    $id = & docker compose --file $script:ComposeFile ps --quiet postgres 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return ($id | Select-Object -First 1)
}

function Test-PostgresReady {
    $id = Get-PostgresContainerId
    if (-not $id) { return $false }
    $status = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $id 2>$null
    return $LASTEXITCODE -eq 0 -and ($status -eq 'healthy' -or $status -eq 'running')
}

function Get-PostgresStartArguments {
    return @('compose', '--file', $script:ComposeFile, 'up', '--detach', 'postgres')
}

function Ensure-Postgres {
    $action = Resolve-PostgresAction (Test-PostgresReady) ([bool]$CheckOnly)
    if ($action -eq 'REUSE') {
        Write-LauncherStep 'POSTGRES_READY' 'Reusing the LocalRAG PostgreSQL container and volume.'
        return
    }
    if ($action -eq 'UNAVAILABLE') { throw '[POSTGRES_UNAVAILABLE] LocalRAG PostgreSQL is not ready.' }
    Write-LauncherStep 'POSTGRES_STARTING' 'Starting only the LocalRAG postgres Compose service.'
    & docker @(Get-PostgresStartArguments)
    if ($LASTEXITCODE -ne 0) { throw '[POSTGRES_UNAVAILABLE] docker compose up failed.' }
    Wait-ForCondition ${function:Test-PostgresReady} $ServiceTimeoutSeconds 'POSTGRES_UNAVAILABLE' 'PostgreSQL did not become healthy before the timeout.'
    Write-LauncherStep 'POSTGRES_READY' 'PostgreSQL is ready.'
}

function Get-OllamaTags {
    try {
        return Invoke-RestMethod -Uri 'http://127.0.0.1:11434/api/tags' -TimeoutSec 3
    }
    catch { return $null }
}

function Test-OllamaReady { return $null -ne (Get-OllamaTags) }

function Get-OllamaExecutable {
    $command = Get-Command ollama.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $fixedPath = Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe'
    if (Test-Path -LiteralPath $fixedPath) { return $fixedPath }
    return $null
}

function Ensure-Ollama {
    $executable = Get-OllamaExecutable
    $action = Resolve-OllamaAction (Test-OllamaReady) ([bool]$CheckOnly) ([bool]$executable)
    if ($action -eq 'NOT_RUNNING') { throw '[OLLAMA_NOT_RUNNING] Ollama API is not responding.' }
    if ($action -eq 'NOT_INSTALLED') { throw '[OLLAMA_NOT_INSTALLED] ollama.exe was not found.' }
    if ($action -eq 'START') {
        New-Item -ItemType Directory -Force -Path $script:LogRoot | Out-Null
        $stdout = Join-Path $script:LogRoot 'ollama.out.log'
        $stderr = Join-Path $script:LogRoot 'ollama.err.log'
        Write-LauncherStep 'OLLAMA_STARTING' "Starting Ollama. Logs: $script:LogRoot"
        Start-Process -FilePath $executable -ArgumentList 'serve' -WorkingDirectory $script:ProjectRoot `
            -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr | Out-Null
        Wait-ForCondition ${function:Test-OllamaReady} 30 'OLLAMA_NOT_RUNNING' 'Ollama API did not become ready before the timeout.'
    }
    Write-LauncherStep 'OLLAMA_READY' 'Reusing the Ollama API.'
    $tags = Get-OllamaTags
    $installed = @($tags.models | ForEach-Object { $_.name })
    $missing = @($script:RequiredModels | Where-Object { $_ -notin $installed })
    if ($missing.Count -gt 0) {
        Write-Warning "[MODEL_MISSING] Required models are missing: $($missing -join ', '). Automatic pull is disabled."
    }
    else { Write-LauncherStep 'MODELS_READY' "Found all $($script:RequiredModels.Count) required models." }
}

function Start-LoggedProcess {
    param([string]$Name, [string]$FilePath, [string[]]$ArgumentList, [string]$WorkingDirectory)
    New-Item -ItemType Directory -Force -Path $script:LogRoot | Out-Null
    $stdout = Join-Path $script:LogRoot "$Name.out.log"
    $stderr = Join-Path $script:LogRoot "$Name.err.log"
    $process = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    Write-LauncherStep ($Name.ToUpperInvariant() + '_STARTING') "PID $($process.Id), stdout: $stdout, stderr: $stderr"
    return $process
}

function Ensure-Backend {
    $ready = Test-LocalRagBackend
    $portInUse = Test-TcpPort $script:BackendPort
    $action = Resolve-StartupAction $ready $portInUse
    if ($action -eq 'REUSE') {
        Write-LauncherStep 'BACKEND_READY' 'Reusing the existing LocalRAG Backend.'
        return $null
    }
    if ($action -eq 'PORT_IN_USE') {
        throw "[PORT_IN_USE] $(Get-PortOwnerDescription $script:BackendPort) is not an identified LocalRAG Backend."
    }
    if ($CheckOnly) { throw '[BACKEND_NOT_RUNNING] LocalRAG Backend is not running.' }
    $process = Start-LoggedProcess 'backend' (Join-Path $script:ProjectRoot 'gradlew.bat') `
        @('bootRun', '--args=--server.port=18080') $script:ProjectRoot
    try {
        Wait-ForCondition ${function:Test-LocalRagBackend} $ServiceTimeoutSeconds 'BACKEND_START_FAILED' 'Backend health/discovery API did not become ready.'
    }
    catch {
        if ($process.HasExited) { throw "[BACKEND_START_FAILED] Backend exited. Log: $(Join-Path $script:LogRoot 'backend.err.log')" }
        throw
    }
    Write-LauncherStep 'BACKEND_READY' 'LocalRAG Backend is ready.'
    return $process
}

function Ensure-Frontend {
    $ready = Test-LocalRagFrontend
    $portInUse = Test-TcpPort $script:FrontendPort
    $action = Resolve-StartupAction $ready $portInUse
    if ($action -eq 'REUSE') {
        Write-LauncherStep 'FRONTEND_READY' 'Reusing the existing LocalRAG Frontend.'
        return $null
    }
    if ($action -eq 'PORT_IN_USE') {
        throw "[PORT_IN_USE] $(Get-PortOwnerDescription $script:FrontendPort) is not an identified LocalRAG Frontend."
    }
    if ($CheckOnly) { throw '[FRONTEND_NOT_RUNNING] LocalRAG Frontend is not running.' }
    $npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
    if (-not $npm) { throw '[FRONTEND_START_FAILED] npm.cmd was not found.' }
    $process = Start-LoggedProcess 'frontend' $npm.Source `
        @('run', 'dev', '--', '--host', '127.0.0.1', '--port', '5173', '--strictPort') $script:FrontendRoot
    try {
        Wait-ForCondition ${function:Test-LocalRagFrontend} 30 'FRONTEND_START_FAILED' 'Vite Frontend did not become ready.'
    }
    catch {
        if ($process.HasExited) { throw "[FRONTEND_START_FAILED] Frontend exited. Log: $(Join-Path $script:LogRoot 'frontend.err.log')" }
        throw
    }
    Write-LauncherStep 'FRONTEND_READY' 'LocalRAG Frontend is ready.'
    return $process
}

function Save-LauncherState {
    param($BackendProcess, $FrontendProcess)
    New-Item -ItemType Directory -Force -Path $script:RuntimeRoot | Out-Null
    $statePath = Join-Path $script:RuntimeRoot 'launcher-state.json'
    $existing = if (Test-Path -LiteralPath $statePath) {
        try { Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json }
        catch { $null }
    }
    else { $null }
    $backendPid = if ($BackendProcess) { $BackendProcess.Id }
        elseif ($existing -and $existing.backendPid -and (Get-Process -Id $existing.backendPid -ErrorAction SilentlyContinue)) { $existing.backendPid }
        else { Get-PortOwnerPid $script:BackendPort }
    $frontendPid = if ($FrontendProcess) { $FrontendProcess.Id }
        elseif ($existing -and $existing.frontendPid -and (Get-Process -Id $existing.frontendPid -ErrorAction SilentlyContinue)) { $existing.frontendPid }
        else { Get-PortOwnerPid $script:FrontendPort }
    [ordered]@{
        startedAt = [DateTimeOffset]::Now.ToString('o')
        backendPid = $backendPid
        frontendPid = $frontendPid
        note = 'Launcher-started process PIDs; safely reused services record listener PIDs. The launcher never stops processes.'
    } | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
}

function Invoke-LocalRagDevelopment {
    Write-LauncherStep 'START' "LocalRAG development environment: $script:ProjectRoot"
    Ensure-Docker
    Ensure-Postgres
    Ensure-Ollama
    $backend = Ensure-Backend
    $frontend = Ensure-Frontend
    Save-LauncherState $backend $frontend
    Write-LauncherStep 'READY' 'LocalRAG: http://localhost:5173'
    Write-LauncherStep 'LOGS' $script:LogRoot
    if (-not $NoBrowser -and -not $CheckOnly) {
        Start-Process 'http://localhost:5173'
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    try { Invoke-LocalRagDevelopment }
    catch {
        Write-Error $_.Exception.Message
        exit 1
    }
}
