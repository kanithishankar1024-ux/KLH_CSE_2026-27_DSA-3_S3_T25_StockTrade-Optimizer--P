$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$testDir = Join-Path $projectRoot 'work\gui-smoke'
New-Item -ItemType Directory -Path $testDir -Force | Out-Null
function Invoke-Bridge($request) {
    $key = [Guid]::NewGuid().ToString('N')
    $inputFile = Join-Path $testDir "$key-input.json"
    $outputFile = Join-Path $testDir "$key-output.json"
    [IO.File]::WriteAllText($inputFile,($request | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
    & "$PSScriptRoot\gui-worker.ps1" -RequestFile $inputFile -ResponseFile $outputFile
    return ([IO.File]::ReadAllText($outputFile) | ConvertFrom-Json)
}
$csv = Join-Path $testDir 'my stock prices.csv'
[IO.File]::WriteAllText($csv,"Date,Close`r`n2024-01-01,10`r`n2024-01-02,12`r`n2024-01-03,11`r`n2024-01-04,14",[Text.UTF8Encoding]::new($false))
$history = Invoke-Bridge @{mode='history'; file=$csv; column='Close'; trades=1; fee='0.50'; cooldown=0}
if (!$history.ok -or $history.result.profit -ne 350 -or $history.result.trades.Count -ne 1 -or !$history.result.legal) { throw 'Imported CSV bridge failed' }
$invalid = Invoke-Bridge @{mode='history'; file=$csv; column='MissingColumn'; trades=1; fee='0.50'; cooldown=0}
if ($invalid.ok -or $invalid.error -notmatch 'missing column') { throw 'Invalid CSV must report a visible error' }
$replay = Invoke-Bridge @{mode='replay'; budget='200.00'; window=60; trades=3; fee='0.50'; cooldown=1}
if (!$replay.ok -or $replay.result.days.Count -ne 2306) { throw 'Replay adapter failed' }
if ($replay.result.days[0].spent -ne 3506 -or $replay.result.days[-1].spent -ne 19443) { throw 'Replay differs from the existing pipeline' }
if ($replay.result.days[0].risk -ne $null -or $replay.result.days[-1].risk.var -le 0) { throw 'Replay risk availability mismatch' }
Write-Output 'PASS: imported CSV, structured errors, all 2306 replay days and existing pipeline amounts verified.'
