param([switch]$IncludeBenchmark)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\tools\java-env.ps1"
& "$PSScriptRoot\build.ps1"
$jar = Join-Path $PSScriptRoot 'lib\junit-platform-console-standalone-1.11.4.jar'
$base = 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar'
New-Item -ItemType Directory -Path "$PSScriptRoot\lib","$PSScriptRoot\out-test","$PSScriptRoot\reports\junit" -Force | Out-Null
if (!(Test-Path -LiteralPath $jar)) {
    Invoke-WebRequest -Uri $base -OutFile $jar
    Invoke-WebRequest -Uri "$base.sha256" -OutFile "$jar.sha256"
}
if (!(Test-Path -LiteralPath "$jar.sha256")) { Invoke-WebRequest -Uri "$base.sha256" -OutFile "$jar.sha256" }
$expected = ([IO.File]::ReadAllText("$jar.sha256")).Trim()
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $expected) { throw 'JUnit checksum mismatch' }
$source = @(Get-ChildItem -LiteralPath "$PSScriptRoot\tests\tradeoptima" -Filter '*.java' | ForEach-Object FullName)
if (Select-String -LiteralPath $source -Pattern 'java\.util|String\.format') { throw 'Prohibited API in tests' }
& "$TradeOptimaJdk\bin\javac.exe" --release 17 -encoding UTF-8 -cp "$PSScriptRoot\out;$jar" -d "$PSScriptRoot\out-test" @source
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
$options = @('execute','--class-path',"$PSScriptRoot\out;$PSScriptRoot\out-test",'--scan-class-path','--fail-if-no-tests','--disable-ansi-colors','--reports-dir',"$PSScriptRoot\reports\junit")
if (!$IncludeBenchmark) { $options += @('--exclude-tag','performance') }
& "$TradeOptimaJdk\bin\java.exe" -Xmx512m -jar $jar @options
if ($LASTEXITCODE -ne 0) { throw 'JUnit tests failed' }
