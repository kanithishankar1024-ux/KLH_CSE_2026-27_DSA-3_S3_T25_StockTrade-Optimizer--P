$ErrorActionPreference = 'Stop'
function Find-TradeOptimaJdk {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")) {
        if (Test-Path -LiteralPath $base) {
            $candidates += @(Get-ChildItem -LiteralPath $base -Directory | Sort-Object Name -Descending | ForEach-Object FullName)
        }
    }
    $command = Get-Command javac -ErrorAction SilentlyContinue
    if ($command) { $candidates += Split-Path (Split-Path $command.Source -Parent) -Parent }
    foreach ($candidate in $candidates) {
        $compiler = Join-Path $candidate 'bin\javac.exe'
        if (Test-Path -LiteralPath $compiler) {
            $version = (& $compiler -version 2>&1 | Out-String)
            if ($version -match 'javac (\d+)' -and [int]$Matches[1] -ge 17) { return $candidate }
        }
    }
    throw 'JDK 17+ not found. Install a JDK and set JAVA_HOME.'
}
$TradeOptimaJdk = Find-TradeOptimaJdk
$env:JAVA_HOME = $TradeOptimaJdk
$env:PATH = "$TradeOptimaJdk\bin;$env:PATH"
