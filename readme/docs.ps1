$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\tools\java-env.ps1"
$source = @(Get-ChildItem -LiteralPath "$PSScriptRoot\src\tradeoptima" -Filter '*.java' | ForEach-Object FullName)
& "$TradeOptimaJdk\bin\javadoc.exe" -quiet -encoding UTF-8 '-Xdoclint:all,-missing' -d "$PSScriptRoot\docs\api" @source
if ($LASTEXITCODE -ne 0) { throw 'Javadoc generation failed' }
Write-Host "Javadoc: $PSScriptRoot\docs\api\index.html"
