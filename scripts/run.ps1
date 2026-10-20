param([switch]$SkipBuild, [switch]$StartDatabase, [switch]$DemoCashIn, [int]$Port = 7070)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
if (-not (Test-Path -LiteralPath '.env')) {
    $bytes = New-Object byte[] 32
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $random.GetBytes($bytes)
    $secret = [Convert]::ToBase64String($bytes)
    $random.GetBytes($bytes)
    $password = [Convert]::ToBase64String($bytes)
    $random.Dispose()
    $example = Get-Content -LiteralPath '.env.example' -Raw
    $example = $example.Replace('DB_PASSWORD=change_me', "DB_PASSWORD=$password").Replace('JWT_SECRET=change_me_to_a_random_string_of_32_or_more_characters', "JWT_SECRET=$secret")
    [IO.File]::WriteAllText((Join-Path $projectRoot '.env'), $example)
    Write-Host 'Created .env with random local credentials. Use -StartDatabase for a new Docker database, or set credentials for your existing PostgreSQL server.'
}
if ($StartDatabase) {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) { throw 'Docker is required for -StartDatabase. Otherwise start your existing PostgreSQL server.' }
    & $docker.Source compose up -d --wait db
    if ($LASTEXITCODE -ne 0) { throw 'Could not start PostgreSQL. Check Docker Desktop and port 5433.' }
}
if (-not $SkipBuild) {
    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Install Node.js 24 LTS, then reopen your terminal.' }
    Push-Location frontend
    try {
        if (-not (Test-Path -LiteralPath 'node_modules')) { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' } }
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }
    $maven = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
    if (-not $maven) {
        $candidates = Get-ChildItem -Path "$env:ProgramFiles/JetBrains/IntelliJ IDEA */plugins/maven*/lib/maven3/bin/mvn.cmd" -File -ErrorAction SilentlyContinue
        $maven = ($candidates | Select-Object -First 1).FullName
    }
    if (-not $maven) { throw 'Install Maven 3.9+ and add it to PATH, or run Maven package from IntelliJ.' }
    & $maven "-Dmaven.repo.local=$projectRoot/.cache/m2" package
    if ($LASTEXITCODE -ne 0) { throw 'Java build failed.' }
}
if (-not (Test-Path -LiteralPath 'target/furocash.jar')) { throw 'No runnable jar found. Run without -SkipBuild first.' }
Write-Host "Starting FuroCash at http://localhost:$Port. Verification codes are sent to your phone by SMS. Press Ctrl+C to stop."
$javaArgs = @("-DPORT=$Port")
if ($DemoCashIn) { $javaArgs += '-DDEMO_CASH_IN=true' }
& java @javaArgs -jar target/furocash.jar
if ($LASTEXITCODE -ne 0) { throw 'Server stopped with an error. Check .env, PostgreSQL, and whether the port is already occupied.' }
