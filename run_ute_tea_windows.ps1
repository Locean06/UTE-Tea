param([switch]$Demo)
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
if ($Demo) {
    Write-Host 'UTE Tea - demo H2 (khong su dung SQL Server)' -ForegroundColor Green
    $env:SPRING_PROFILES_ACTIVE='demo'
} else {
    Write-Host 'UTE Tea - SQL Server local / database UTE_Tea' -ForegroundColor Green
    $env:DB_URL='jdbc:sqlserver://localhost:1433;databaseName=UTE_Tea;encrypt=true;trustServerCertificate=true'
    $env:DB_USERNAME=Read-Host 'SQL Server username [sa]'
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) { $env:DB_USERNAME='sa' }
    $secret=Read-Host 'SQL Server password' -AsSecureString
    $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { $env:DB_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    $env:JPA_DDL_AUTO='none'
    $env:SPRING_PROFILES_ACTIVE='local'
}
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    & mvn spring-boot:run "-Dspring-boot.run.profiles=$env:SPRING_PROFILES_ACTIVE"
} elseif (Test-Path '.\mvnw.cmd') {
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=$env:SPRING_PROFILES_ACTIVE"
} else {
    Write-Error 'Khong tim thay Maven hoac Maven Wrapper. Hay cai Maven va JDK 21.'
}
