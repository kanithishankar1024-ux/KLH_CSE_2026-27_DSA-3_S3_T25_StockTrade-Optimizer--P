param(
    [ValidateSet('demo','replay','test','benchmark','history','legacy','help')][string]$Mode = 'demo',
    [string]$DataDir = "$PSScriptRoot\data",
    [string]$PriceFile = '',
    [string]$PriceColumn = 'Close',
    [string]$Budget = '200.00',
    [int]$Window = 60, [int]$Every = 100, [int]$Days = 0,
    [int]$Trades = 3, [string]$Fee = '0.50', [int]$Cooldown = 1
)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\tools\java-env.ps1"
& "$PSScriptRoot\build.ps1"
$inputText = ''
$arguments = "-Xmx512m -cp out tradeoptima.Main $Mode"
if ($Mode -in @('demo','replay')) {
    if ($Budget -notmatch '^\d+(\.\d+)?$') { throw 'Budget must be a nonnegative decimal' }
    $arguments += " $Budget $Window $Every $Days"
    foreach ($name in @('instruments.csv','prices.csv','headlines.csv','orderbook.csv')) {
        $csv = [IO.File]::ReadAllText((Join-Path $DataDir $name))
        $inputText += $csv.Length.ToString([Globalization.CultureInfo]::InvariantCulture) + "`n" + $csv
    }
} elseif ($Mode -eq 'history') {
    if (!$PriceFile) { throw 'History mode requires -PriceFile' }
    if ($PriceColumn -notmatch '^[A-Za-z0-9_ .-]+$' -or $Fee -notmatch '^\d+(\.\d+)?$') { throw 'Invalid price column or fee' }
    $arguments += " `"$PriceColumn`" $Trades $Fee $Cooldown"
    $inputText = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $PriceFile).Path)
}
$settings = New-Object Diagnostics.ProcessStartInfo
$settings.FileName = "$TradeOptimaJdk\bin\java.exe"
$settings.Arguments = $arguments
$settings.WorkingDirectory = $PSScriptRoot
$settings.UseShellExecute = $false
$settings.CreateNoWindow = $true
$settings.RedirectStandardInput = $true
$settings.RedirectStandardOutput = $true
$settings.RedirectStandardError = $true
$process = New-Object Diagnostics.Process
$process.StartInfo = $settings
[void]$process.Start()
try {
    # Forward the hidden Java process output through PowerShell explicitly.
    # Read errors concurrently so a full error pipe cannot block the process.
    $errorOutput = $process.StandardError.ReadToEndAsync()
    # Write UTF-8 bytes directly; this also works in Windows PowerShell 5.1.
    $bytes = [Text.Encoding]::UTF8.GetBytes($inputText)
    $pipe = $process.StandardInput.BaseStream
    $pipe.Write($bytes, 0, $bytes.Length)
    $pipe.Close()
    while ($null -ne ($line = $process.StandardOutput.ReadLine())) {
        Write-Output $line
    }
    $process.WaitForExit()
    $errorText = $errorOutput.GetAwaiter().GetResult()
    if ($errorText) { [Console]::Error.Write($errorText) }
    if ($process.ExitCode -ne 0) { throw "TradeOptima exited with code $($process.ExitCode)" }
} finally { $process.Dispose() }
