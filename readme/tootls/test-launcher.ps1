$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
# Capture the script's output stream. Inherited child-process console output
# bypassed this stream before the launcher explicitly forwarded Java's output.
$output = & "$projectRoot\run.ps1" -Mode demo
$text = $output -join "`n"
foreach ($expected in @('TradeOptima | DSA-3', 'M2 mentions=', 'M3 window hindsight=',
    'M4 actual cost=', 'M5 selected:', 'M6 1-day', 'Ledger legal: true', 'Replay complete: 2306')) {
    if (!$text.Contains($expected)) { throw "Missing launcher output: $expected" }
}
Write-Output 'PASS: all five module results and the ledger reach the PowerShell output stream.'
