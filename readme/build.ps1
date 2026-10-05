$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\tools\java-env.ps1"
$source = @(Get-ChildItem -LiteralPath "$PSScriptRoot\src\tradeoptima" -Filter '*.java' | ForEach-Object FullName)
$forbidden = Select-String -LiteralPath $source -Pattern 'java\.util|String\.format|\.split\(|^\s*import\s+(?!java\.lang\.)'
if ($forbidden) { throw "Restricted API found: $($forbidden -join [Environment]::NewLine)" }
New-Item -ItemType Directory -Path "$PSScriptRoot\out" -Force | Out-Null
& "$TradeOptimaJdk\bin\javac.exe" --release 17 -encoding UTF-8 -Xlint:all -d "$PSScriptRoot\out" @source
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
Write-Host 'Build passed; project source uses no prohibited imports or formatting helpers.'
