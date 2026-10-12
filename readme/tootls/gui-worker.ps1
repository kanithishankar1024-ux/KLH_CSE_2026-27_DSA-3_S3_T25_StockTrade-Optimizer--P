param([Parameter(Mandatory=$true)][string]$RequestFile, [Parameter(Mandatory=$true)][string]$ResponseFile)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
try {
    $request = [IO.File]::ReadAllText($RequestFile) | ConvertFrom-Json
    . "$PSScriptRoot\java-env.ps1"
    & "$projectRoot\build.ps1"
    $inputText = ''
    if ($request.mode -eq 'tests') {
        $arguments = '-Xmx512m -cp out tradeoptima.Main test'
    } elseif ($request.mode -eq 'benchmark') {
        $arguments = '-Xmx512m -cp out tradeoptima.Main benchmark'
    } else {
        if ($request.fee -notmatch '^\d+(\.\d+)?$') { throw 'Fee must be a nonnegative decimal.' }
        $trades = [int]$request.trades; $cooldown = [int]$request.cooldown
        if ($request.mode -eq 'history') {
            if ($request.column -notmatch '^[A-Za-z0-9_ .-]+$') { throw 'Choose a price column containing letters, digits, spaces, dots or hyphens.' }
            $arguments = "-Xmx512m -cp out tradeoptima.GuiBridge history `"$($request.column)`" $trades $($request.fee) $cooldown"
            if ((Get-Item -LiteralPath $request.file).Length -gt 32MB) { throw 'CSV files must be smaller than 32 MB.' }
            $inputText = [IO.File]::ReadAllText($request.file)
        } elseif ($request.mode -eq 'replay') {
            if ($request.budget -notmatch '^\d+(\.\d+)?$') { throw 'Budget must be a nonnegative decimal.' }
            $window = [int]$request.window
            $arguments = "-Xmx512m -cp out tradeoptima.GuiBridge replay $($request.budget) $window $trades $($request.fee) $cooldown"
            foreach ($file in @('instruments.csv','prices.csv','headlines.csv','orderbook.csv')) {
                $csv = [IO.File]::ReadAllText((Join-Path "$projectRoot\data" $file))
                $inputText += $csv.Length.ToString([Globalization.CultureInfo]::InvariantCulture) + "`n" + $csv
            }
        } else { throw 'Unknown interface action.' }
    }
    $settings = New-Object Diagnostics.ProcessStartInfo
    $settings.FileName = "$TradeOptimaJdk\bin\java.exe"
    $settings.Arguments = $arguments
    $settings.WorkingDirectory = $projectRoot
    $settings.UseShellExecute = $false; $settings.CreateNoWindow = $true
    $settings.RedirectStandardInput = $true; $settings.RedirectStandardOutput = $true; $settings.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process; $process.StartInfo = $settings
    [void]$process.Start()
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        $bytes = [Text.Encoding]::UTF8.GetBytes($inputText)
        $pipe = $process.StandardInput.BaseStream; $pipe.Write($bytes,0,$bytes.Length); $pipe.Close()
        $process.WaitForExit(); $text = $stdout.GetAwaiter().GetResult(); $errorText = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw ($errorText + "`n" + $text).Trim() }
        if ($request.mode -in @('tests','benchmark')) { $result = @{ kind=$request.mode; text=$text } }
        else { $result = $text | ConvertFrom-Json }
        $response = @{ ok=$true; result=$result }
    } finally { $process.Dispose() }
} catch { $response = @{ ok=$false; error=$_.Exception.Message } }
[IO.File]::WriteAllText($ResponseFile,($response | ConvertTo-Json -Depth 16 -Compress),[Text.UTF8Encoding]::new($false))
