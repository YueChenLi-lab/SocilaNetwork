param(
    [string]$CsvPath = "data/sample_edges.csv",
    [int]$Port = 8080,
    [switch]$Verify
)
$ErrorActionPreference = "Stop"
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force out | Out-Null
    $sourceFiles = @(Get-ChildItem src -Filter *.java | ForEach-Object { $_.FullName })
    & java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d out @sourceFiles
    if ($LASTEXITCODE -ne 0) { throw "Java compilation failed. Install JDK 17 or newer." }
    if ($Verify) {
        $testFiles = @(Get-ChildItem tests -Filter *.java | ForEach-Object { $_.FullName })
        & java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -cp out -d out @testFiles
        if ($LASTEXITCODE -ne 0) { throw "Test compilation failed." }
        & java -cp out RegressionTests
    } else {
        & java -cp out WebMain $CsvPath $Port
    }
    if ($LASTEXITCODE -ne 0) { throw "Java process failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
