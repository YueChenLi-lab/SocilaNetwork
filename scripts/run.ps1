param(
    [string]$CsvPath = "data/sample_edges.csv",
    [int]$Port = 8080,
    [switch]$Verify
)
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path $PSScriptRoot -Parent
# Resolve an explicitly supplied relative CSV path from the caller's directory.
if (-not $Verify) {
    if ($PSBoundParameters.ContainsKey('CsvPath')) {
        $CsvPath = (Resolve-Path -LiteralPath $CsvPath).ProviderPath
    } else {
        $CsvPath = Join-Path $projectRoot 'data/sample_edges.csv'
    }
}
# IDEA and the scripts use the same JDK when JAVA_HOME is configured.
$javaCommand = 'java'
if ($env:JAVA_HOME) {
    $javaBinary = if ($env:OS -eq 'Windows_NT') { 'bin/java.exe' } else { 'bin/java' }
    $javaCommand = Join-Path $env:JAVA_HOME $javaBinary
    if (-not (Test-Path -LiteralPath $javaCommand -PathType Leaf)) {
        throw 'JAVA_HOME must point to an installed JDK 17 or newer (not its bin directory).'
    }
}
Push-Location -LiteralPath $projectRoot
try {
    New-Item -ItemType Directory -Force out | Out-Null
    $sourceFiles = @(Get-ChildItem src -Filter *.java | ForEach-Object { $_.FullName })
    & $javaCommand -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -d out @sourceFiles
    if ($LASTEXITCODE -ne 0) { throw "Java compilation failed. Install JDK 17 or newer." }
    if ($Verify) {
        $testFiles = @(Get-ChildItem tests -Filter *.java | ForEach-Object { $_.FullName })
        & $javaCommand -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -cp out -d out @testFiles
        if ($LASTEXITCODE -ne 0) { throw "Test compilation failed." }
        & $javaCommand -cp out RegressionTests
    } else {
        & $javaCommand -cp out WebMain $CsvPath $Port
    }
    if ($LASTEXITCODE -ne 0) { throw "Java process failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
