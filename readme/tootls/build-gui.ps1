$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$compiler = "$env:WINDIR\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$assembly = "$env:WINDIR\Microsoft.Net\assembly\GAC_MSIL\System.Management.Automation\v4.0_3.0.0.0__31bf3856ad364e35\System.Management.Automation.dll"
if (!(Test-Path -LiteralPath $compiler) -or !(Test-Path -LiteralPath $assembly)) { throw 'The Windows .NET Framework and Windows PowerShell 5.1 are required for the desktop launcher.' }
& $compiler /nologo /target:winexe /platform:anycpu "/out:$projectRoot\TradeOptima.exe" "/reference:$assembly" /reference:System.Windows.Forms.dll "$projectRoot\ui\Launcher.cs"
if ($LASTEXITCODE -ne 0) { throw 'Desktop launcher compilation failed' }
Write-Output 'TradeOptima.exe is ready. Double-click it to open the app.'
