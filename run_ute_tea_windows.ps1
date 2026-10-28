<#
UTE Tea local launcher - Java 21, Maven, SQL Server or H2 demo.
Workaround for paths with Vietnamese/non-ASCII characters on Windows:
copy source into a temporary ASCII-only build folder, then run the executable JAR.
This launcher does not modify the original project or database schema.
#>
[CmdletBinding()]
param(
    [switch]$Demo,
    [switch]$Neon,
    [switch]$KeepBuild
)
$ErrorActionPreference = 'Stop'
$projectDir = (Resolve-Path -LiteralPath $PSScriptRoot).Path

# Load local .env into the current PowerShell process before building and launching
# Spring Boot. This includes Cloudinary, email and payment configuration.
# Never print secrets, and never commit the .env file to Git.
$envFile = Join-Path $projectDir '.env'
if (Test-Path -LiteralPath $envFile) {
    foreach ($rawLine in (Get-Content -LiteralPath $envFile -Encoding UTF8)) {
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }
        $separator = $line.IndexOf('=')
        if ($separator -le 0) { continue }
        $name = $line.Substring(0, $separator).Trim()
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') { continue }
        $value = $line.Substring($separator + 1).Trim()
        if ($value.Length -ge 2 -and (
                ($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))
        )) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
    Write-Host 'Da nap bien moi truong tu file .env (khong hien thi gia tri bi mat).' -ForegroundColor Cyan
} else {
    Write-Warning 'Khong thay file .env o thu muc goc; can thiet lap bien moi truong truoc khi chay.'
}

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

if ($Demo -and $Neon) {
    Fail 'Chi chon mot che do -Demo hoac -Neon.'
}

$port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '8080' }
if ($port -notmatch '^\d+) { Fail 'SERVER_PORT phai la so nguyen.' }
$env:SERVER_PORT = $port
$env:APP_BASE_URL = "http://localhost:$port"
$env:WS_ALLOWED_ORIGIN = "http://localhost:$port"
$env:JWT_COOKIE_SECURE = 'false'

# Generate per-run local signing secrets if none were explicitly provided.
# Never store them in source control and never print their values.
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET_BASE64) -or $env:JWT_SECRET_BASE64 -eq 'GENERATE_ON_START') {
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
}
if ([string]::IsNullOrWhiteSpace($env:OTP_SECRET) -or $env:OTP_SECRET -eq 'GENERATE_ON_START') {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:OTP_SECRET = [Convert]::ToBase64String($bytes)
}

if ($Demo) {
    $env:SPRING_PROFILES_ACTIVE = 'demo'
    Write-Host 'UTE Tea - H2 demo trong bo nho, du lieu reset khi tat ung dung.' -ForegroundColor Cyan
} elseif ($Neon -or $env:DB_URL -match '^jdbc:postgresql:') {
    if ($env:DB_URL -notmatch '^jdbc:postgresql://[^/]+/[^?]+' -or $env:DB_URL -match 'YOUR_NEON|REPLACE|HOST') {
        Fail 'DB_URL khong hop le. Lay PostgreSQL JDBC URL tai Neon > Connect; thay placeholder trong .env.'
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
        Fail 'Chua co DB_USERNAME trong .env.'
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD) -or $env:DB_PASSWORD -match '^(CHANGE_ME|REPLACE_ME|YOUR_PASSWORD)$') {
        $secure = Read-Host 'Neon database password' -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
        Fail 'Khong duoc de trong mat khau Neon.'
    }

    # Local Neon MUST NOT auto-create, update or drop production database tables.
    $env:JPA_DDL_AUTO = 'validate'
    $env:DEMO_SEED_ENABLED = 'false'
    $env:SPRING_PROFILES_ACTIVE = 'local,local-neon'
    Write-Host 'UTE Tea - Localhost ket noi Neon PostgreSQL (JPA validate, khong seed).' -ForegroundColor Cyan
    Write-Warning 'Moi thao tac them/sua/xoa tren web LOCAL se ghi vao Neon DB that. De tach biet, hay dung mot Neon dev branch.'
} else {
    $env:SPRING_PROFILES_ACTIVE = 'local'
    if (-not $env:DB_URL) {
        $env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=UTE_Tea;encrypt=true;trustServerCertificate=true'
    }
    if ($env:DB_URL -notmatch '^jdbc:sqlserver:') {
        Fail 'Che do SQL Server can DB_URL bat dau bang jdbc:sqlserver:. Neu su dung Neon, them -Neon.'
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
    $env:DEMO_SEED_ENABLED = 'false'
    Write-Host 'UTE Tea - SQL Server local (JPA none, can co schema truoc).' -ForegroundColor Cyan
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

Write-Host "Dang khoi dong UTE Tea ($env:SPRING_PROFILES_ACTIVE). Mo http://localhost:$port sau khi thay Started ..." -ForegroundColor Green
Write-Host 'De web tiep tuc chay, khong dong cua so Terminal. Bam Ctrl+C de dung.'
& java -jar $jarPath "--spring.profiles.active=$env:SPRING_PROFILES_ACTIVE"
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    Fail "Spring Boot da dung (exit code $exitCode). Doc dong Caused by dau tien trong log."
}
) { Fail 'SERVER_PORT phai la so nguyen.' }
$env:SERVER_PORT = $port
$env:APP_BASE_URL = "http://localhost:$port"
$env:WS_ALLOWED_ORIGIN = "http://localhost:$port"
$env:JWT_COOKIE_SECURE = 'false'

# Generate per-run local signing secrets if none were explicitly provided.
# Never store them in source control and never print their values.
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET_BASE64) -or $env:JWT_SECRET_BASE64 -eq 'GENERATE_ON_START') {
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
}
if ([string]::IsNullOrWhiteSpace($env:OTP_SECRET) -or $env:OTP_SECRET -eq 'GENERATE_ON_START') {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $env:OTP_SECRET = [Convert]::ToBase64String($bytes)
}

if ($Demo) {
    $env:SPRING_PROFILES_ACTIVE = 'demo'
    Write-Host 'UTE Tea - H2 demo trong bo nho, du lieu reset khi tat ung dung.' -ForegroundColor Cyan
} elseif ($Neon -or $env:DB_URL -match '^jdbc:postgresql:') {
    if ($env:DB_URL -notmatch '^jdbc:postgresql://[^/]+/[^?]+' -or $env:DB_URL -match 'YOUR_NEON|REPLACE|HOST') {
        Fail 'DB_URL khong hop le. Lay PostgreSQL JDBC URL tai Neon > Connect; thay placeholder trong .env.'
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
        Fail 'Chua co DB_USERNAME trong .env.'
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD) -or $env:DB_PASSWORD -match '^(CHANGE_ME|REPLACE_ME|YOUR_PASSWORD)$') {
        $secure = Read-Host 'Neon database password' -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
        Fail 'Khong duoc de trong mat khau Neon.'
    }

    # Local Neon MUST NOT auto-create, update or drop production database tables.
    $env:JPA_DDL_AUTO = 'validate'
    $env:DEMO_SEED_ENABLED = 'false'
    $env:SPRING_PROFILES_ACTIVE = 'local,local-neon'
    Write-Host 'UTE Tea - Localhost ket noi Neon PostgreSQL (JPA validate, khong seed).' -ForegroundColor Cyan
    Write-Warning 'Moi thao tac them/sua/xoa tren web LOCAL se ghi vao Neon DB that. De tach biet, hay dung mot Neon dev branch.'
} else {
    $env:SPRING_PROFILES_ACTIVE = 'local'
    if (-not $env:DB_URL) {
        $env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=UTE_Tea;encrypt=true;trustServerCertificate=true'
    }
    if ($env:DB_URL -notmatch '^jdbc:sqlserver:') {
        Fail 'Che do SQL Server can DB_URL bat dau bang jdbc:sqlserver:. Neu su dung Neon, them -Neon.'
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
    $env:DEMO_SEED_ENABLED = 'false'
    Write-Host 'UTE Tea - SQL Server local (JPA none, can co schema truoc).' -ForegroundColor Cyan
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

Write-Host "Dang khoi dong UTE Tea ($env:SPRING_PROFILES_ACTIVE). Mo http://localhost:$port sau khi thay Started ..." -ForegroundColor Green
Write-Host 'De web tiep tuc chay, khong dong cua so Terminal. Bam Ctrl+C de dung.'
& java -jar $jarPath "--spring.profiles.active=$env:SPRING_PROFILES_ACTIVE"
$exitCode = $LASTEXITCODE
if ($exitCode -ne 0) {
    Fail "Spring Boot da dung (exit code $exitCode). Doc dong Caused by dau tien trong log."
}
