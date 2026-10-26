<#
UTE Tea local launcher - Java 21, Maven, SQL Server or H2 demo.
Workaround for paths with Vietnamese/non-ASCII characters on Windows:
copy source into a temporary ASCII-only build folder, then run the executable JAR.
This launcher does not modify the original project or database schema.
#>
[CmdletBinding()]
param(
    [switch]$Demo,
    [switch]$KeepBuild
)
$ErrorActionPreference = 'Stop'
$projectDir = (Resolve-Path -LiteralPath $PSScriptRoot).Path

function Fail([string]$message) {
    Write-Host "UTE Tea - LOI: $message" -ForegroundColor Red
    exit 1
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Fail 'Khong tim thay Java. Hay cai Java JDK 21 va mo lai Terminal.'
}
$javaInfo = (& java --version | Out-String)
if ($javaInfo -notmatch '(?m)^\S+ 21(?:\.|\s)') {
    Write-Warning 'Project su dung JDK 21. Kiem tra lenh: java -version'
}

$workDir = $projectDir
# Windows JVM/Maven may generate an unusable classpath for a project path
# containing accented Unicode. The user project is never moved or deleted.
if ($projectDir -cmatch '[^\x00-\x7F]') {
    $tempRoot = $env:TEMP
    if ([string]::IsNullOrWhiteSpace($tempRoot)) {
        Fail 'Khong tim thay thu muc TEMP de tao duong dan build khong dau.'
    }
    if ($tempRoot -cmatch '[^\x00-\x7F]') {
        Fail 'TEMP cung co ky tu Unicode. Hay chuyen project den C:\Projects\UTE-Tea va thu lai.'
    }
    $pathBytes = [System.Text.Encoding]::UTF8.GetBytes($projectDir.ToLowerInvariant())
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { $hashBytes = $sha.ComputeHash($pathBytes) }
    finally { $sha.Dispose() }
    $hashPart = ([System.BitConverter]::ToString($hashBytes)).Replace('-', '').Substring(0, 12)
    $workDir = Join-Path $tempRoot ("UTE_Tea_Build_$hashPart")
    New-Item -ItemType Directory -Force -Path $workDir | Out-Null
    Write-Host 'Phat hien duong dan co dau. Dang sao chep source sang duong dan build khong dau:' -ForegroundColor Yellow
    Write-Host "  $workDir"
    # /MIR only synchronizes the temporary folder. /XD protects Maven
    # artifacts in staging from deletion and never touches the project source.
    & robocopy.exe $projectDir $workDir /MIR /XD '.git' 'target' /XF '*.log' '.DS_Store' /R:1 /W:1 /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) {
        Fail "Khong sao chep duoc source sang TEMP (Robocopy code $LASTEXITCODE)."
    }
}

Set-Location -LiteralPath $workDir
if (-not (Test-Path -LiteralPath '.\pom.xml')) {
    Fail 'Khong tim thay pom.xml. Hay mo dung thu muc goc cua project.'
}

if ($Demo) {
    $env:SPRING_PROFILES_ACTIVE = 'demo'
    Write-Host 'UTE Tea - H2 demo, khong can SQL Server.' -ForegroundColor Cyan
} else {
    $env:SPRING_PROFILES_ACTIVE = 'local'
    if (-not $env:DB_URL) {
        $env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=UTE_Tea;encrypt=true;trustServerCertificate=true'
    }
    if (-not $env:DB_USERNAME) {
        $username = Read-Host 'SQL Server username [sa]'
        $env:DB_USERNAME = if ([string]::IsNullOrWhiteSpace($username)) { 'sa' } else { $username }
    }
    if (-not $env:DB_PASSWORD) {
        $secure = Read-Host 'SQL Server password' -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    }
    $env:JPA_DDL_AUTO = 'none'
    Write-Host 'UTE Tea - SQL Server / database UTE_Tea.' -ForegroundColor Cyan
}

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvn) {
    $mavenExe = $mvn.Source
} elseif (Test-Path -LiteralPath '.\mvnw.cmd') {
    $mavenExe = (Resolve-Path -LiteralPath '.\mvnw.cmd').Path
} else {
    Fail 'Khong tim thay Maven va mvnw.cmd.'
}

Write-Host 'Dang build JAR bang Maven (khong dung spring-boot:run)...' -ForegroundColor Cyan
if ($KeepBuild) {
    & $mavenExe '-DskipTests' 'package'
} else {
    & $mavenExe '-DskipTests' 'clean' 'package'
}
if ($LASTEXITCODE -ne 0) {
    Fail 'Maven build that bai. Kiem tra loi [ERROR] dau tien phia tren.'
}

$jars = @(Get-ChildItem -LiteralPath '.\target' -File -Filter '*.jar' |
    Where-Object { $_.Name -notmatch '(\.original$|\.original\.jar$|-sources\.jar$|-javadoc\.jar$)' })
if ($jars.Count -eq 0) {
    Fail 'Khong tim thay JAR trong target. Kiem tra spring-boot-maven-plugin.'
}
$jar = $jars | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$jarPath = $jar.FullName
Write-Host "JAR: $jarPath" -ForegroundColor Cyan

# Verify that Spring Boot has packaged the application class inside the JAR.
if (Get-Command jar -ErrorAction SilentlyContinue) {
    $jarEntry = (& jar tf $jarPath | Select-String -SimpleMatch 'BOOT-INF/classes/vn/edu/ute/MilkteaApplication.class')
    if (-not $jarEntry) {
        Fail 'JAR khong chua BOOT-INF/classes/vn/edu/ute/MilkteaApplication.class.'
    }
}

Write-Host "Dang khoi dong UTE Tea ($env:SPRING_PROFILES_ACTIVE). Mo http://localhost:8080 sau khi thay Started ..." -ForegroundColor Green
Write-Host 'De web tiep tuc chay, khong dong cua so Terminal. Bam Ctrl+C de dung.'
& java -jar $jarPath "--spring.profiles.active=$env:SPRING_PROFILES_ACTIVE"
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    Fail "Spring Boot da dung (exit code $exitCode). Doc dong Caused by dau tien trong log."
}
