param([switch]$CheckLayout, [switch]$SmokeTest)
if (!$PSScriptRoot) { $PSScriptRoot = $global:TradeOptimaRoot }
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationFramework,PresentationCore,WindowsBase
$script:ProjectRoot = $PSScriptRoot
$script:Invariant = [Globalization.CultureInfo]::InvariantCulture
$script:Ui = @{}
$script:Page = 'history'
$script:History = $null
$script:Replay = $null
$script:CurrentJob = $null
$script:AnalyzedCurrency = 'USD'
$script:LedgerRows = @()
$script:SourceFile = Join-Path $PSScriptRoot 'data\prices.csv'
$script:WorkDir = Join-Path $PSScriptRoot 'work\gui'
New-Item -ItemType Directory -Path $script:WorkDir -Force | Out-Null
try {
    [xml]$markup = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'ui\main.xaml'))
    $script:GuiWindow = [Windows.Markup.XamlReader]::Load([Xml.XmlNodeReader]::new($markup))
    foreach ($node in $markup.SelectNodes('//*[@*[local-name()="Name"]]')) {
        $name = $node.GetAttribute('Name','http://schemas.microsoft.com/winfx/2006/xaml')
        if ($name) { $script:Ui[$name] = $script:GuiWindow.FindName($name) }
    }
    if ($CheckLayout) { Write-Output "Layout loaded: $($script:Ui.Count) named controls"; exit 0 }
} catch {
    [void][Windows.MessageBox]::Show($_.Exception.Message,'TradeOptima could not start','OK','Error')
    exit 1
}

function Set-Status([string]$text, [bool]$isError=$false) {
    $script:Ui.StatusText.Text = $text
    $script:Ui.StatusText.Foreground = if ($isError) { '#AC3F30' } else { '#64748B' }
}
function Money($cents) { ([double]$cents / 100).ToString('N2',$script:Invariant) }
function Decimal-Text($value) { ([double]$value).ToString('N2',$script:Invariant) }
function Set-Busy([bool]$busy) {
    foreach ($name in @('RunButton','ImportButton','SampleButton','TestsButton','BenchmarkButton','TradesInput','FeeInput','CooldownInput','BudgetInput','WindowInput','ColumnCombo','CurrencyCombo','DropBrowseButton','LabButton','SearchQuery','SearchText')) {
        $script:Ui[$name].IsEnabled = !$busy
    }
    $script:Ui.BusyBar.Visibility = if ($busy) { 'Visible' } else { 'Collapsed' }
}
function Set-Source([string]$path) {
    if ($script:CurrentJob) { throw 'Wait for the current analysis to finish before importing another file.' }
    $file = Get-Item -LiteralPath $path
    if ($file.PSIsContainer -or $file.Extension -ine '.csv') { throw 'Drop one .csv file, or use Browse files.' }
    if ($file.Length -gt 32MB) { throw 'Please choose a CSV smaller than 32 MB.' }
    $preview = @(Import-Csv -LiteralPath $path | Select-Object -First 5)
    if (!$preview.Count) { throw 'The file has no data rows. It needs a header and at least one dated price.' }
    $columns = @($preview[0].PSObject.Properties.Name)
    if ($columns -cnotcontains 'Date') { throw 'The CSV needs a Date column with YYYY-MM-DD dates.' }
    $prices = @($columns | Where-Object { $_ -cne 'Date' })
    if ($prices.Count -eq 0) { throw 'The CSV needs a numeric price column, such as Close.' }
    $script:SourceFile = $file.FullName
    $script:Ui.ColumnCombo.Items.Clear()
    foreach ($column in $prices) { [void]$script:Ui.ColumnCombo.Items.Add($column) }
    if ($prices -contains 'Close') { $script:Ui.ColumnCombo.SelectedItem = 'Close' }
    elseif ($prices -contains 'AAPL') { $script:Ui.ColumnCombo.SelectedItem = 'AAPL' }
    else { $script:Ui.ColumnCombo.SelectedIndex = 0 }
    $script:Ui.FileLabel.Text = $file.Name; $script:Ui.FileLabel.ToolTip = $file.FullName
    $script:Ui.DropTitle.Text = 'CSV ready to analyze'
    $script:Ui.DropHint.Text = 'Drop another CSV here to replace the selected file'
    $script:Ui.FileMeta.Text = "$($file.Name)  /  $([Math]::Round($file.Length/1KB,1)) KB  /  $($prices.Count) available columns"
    $script:Ui.PreviewGrid.ItemsSource = [object[]]$preview
    $script:History = $null; $script:LedgerRows = @()
    $script:Ui.LedgerGrid.ItemsSource = $null; $script:Ui.ExportButton.IsEnabled = $false
    foreach ($name in @('ProfitText','TradesText','CloseText','RiskText')) { $script:Ui[$name].Text = '—' }
    $script:Ui.LegalText.Text = 'Awaiting analysis'; $script:Ui.LastDateText.Text = 'From your dataset'
    $script:Ui.CvarText.Text = 'For one share'; $script:Ui.LedgerNote.Text = 'Analyze the selected CSV to build its trade ledger.'
    $script:Ui.DataSummary.Text = 'CSV preview loaded. Java will validate all dates and selected prices when you analyze.'
    $script:Ui.ResultState.Text = 'New source selected · choose a price column, then Analyze stock'
    Draw-Chart
    Set-Status 'CSV selected. Choose its price column and display currency, then Analyze stock.'
}
function Select-Source {
    $dialog=New-Object Microsoft.Win32.OpenFileDialog
    $dialog.Filter='CSV price files (*.csv)|*.csv'; $dialog.Title='Choose a historical price CSV'
    if($dialog.ShowDialog($script:GuiWindow)) {
        try { Set-Source $dialog.FileName } catch { Set-Status $_.Exception.Message $true }
    }
}
function Get-DroppedCsv($eventArgs) {
    if ($script:CurrentJob -or !$eventArgs.Data.GetDataPresent([Windows.DataFormats]::FileDrop)) { return $null }
    $files = @($eventArgs.Data.GetData([Windows.DataFormats]::FileDrop))
    if ($files.Count -ne 1 -or [IO.Path]::GetExtension($files[0]) -ine '.csv') { return $null }
    return [string]$files[0]
}
function Set-Page([string]$page) {
    $script:Page = $page
    $script:PlayTimer.Stop(); $script:Ui.PlayButton.Content = 'Play'
    $script:Ui.AnalysisPage.Visibility = if ($page -eq 'history') { 'Visible' } else { 'Collapsed' }
    $script:Ui.ReplayPage.Visibility = if ($page -eq 'replay') { 'Visible' } else { 'Collapsed' }
    $script:Ui.LabPage.Visibility = if ($page -eq 'lab') { 'Visible' } else { 'Collapsed' }
    $script:Ui.VerificationPage.Visibility = if ($page -eq 'verify') { 'Visible' } else { 'Collapsed' }
    $script:Ui.FilePanel.Visibility = if ($page -eq 'history') { 'Visible' } else { 'Collapsed' }
    $script:Ui.BudgetPanel.Visibility = if ($page -eq 'replay') { 'Visible' } else { 'Collapsed' }
    $script:Ui.SettingsPanel.Visibility = if ($page -in @('verify','lab')) { 'Collapsed' } else { 'Visible' }
    $script:Ui.RunButton.Visibility = if ($page -in @('verify','lab')) { 'Collapsed' } else { 'Visible' }
    $script:Ui.RunButton.Content = if ($page -eq 'history') { 'Analyze stock' } else { 'Load market replay' }
    $script:Ui.PageTitle.Text = switch ($page) { history { 'Stock analysis' } replay { 'Market replay' } lab { 'Algorithm workspace' } default { 'Verification' } }
    $script:Ui.PageSubtitle.Text = switch ($page) {
        history { 'Explore historical prices, optimal trades and risk in one place.' }
        replay { 'Step through the five-module platform without terminal commands.' }
        lab { 'See which algorithm runs and compare its results with alternatives.' }
        default { 'Check correctness and performance using the Java engine.' }
    }
    $script:Ui.AnalysisNav.Background = if ($page -eq 'history') { '#2563EB' } else { '#1B2B46' }
    $script:Ui.ReplayNav.Background = if ($page -eq 'replay') { '#2563EB' } else { '#1B2B46' }
    $script:Ui.LabNav.Background = if ($page -eq 'lab') { '#2563EB' } else { '#1B2B46' }
    $script:Ui.VerifyNav.Background = if ($page -eq 'verify') { '#2563EB' } else { '#1B2B46' }
}
function Add-ChartText([string]$text,[double]$left,[double]$top,[string]$color='#809397') {
    $label = New-Object Windows.Controls.TextBlock
    $label.Text=$text; $label.FontSize=10; $label.Foreground=$color
    [Windows.Controls.Canvas]::SetLeft($label,$left); [Windows.Controls.Canvas]::SetTop($label,$top)
    [void]$script:Ui.PriceChart.Children.Add($label)
}
function Draw-Chart {
    $canvas=$script:Ui.PriceChart; $canvas.Children.Clear()
    if (!$script:History) { Add-ChartText 'Your price chart will appear here.' 24 100; return }
    $prices=$script:History.prices; $dates=$script:History.dates
    $width=[Math]::Max(300,$canvas.ActualWidth); $height=$canvas.Height
    $left=62.0; $right=$width-20; $top=16.0; $bottom=$height-32
    $min=[double]$prices[0]; $max=$min
    foreach($p in $prices) { if($p -lt $min){$min=[double]$p}; if($p -gt $max){$max=[double]$p} }
    $padding=[Math]::Max(1,($max-$min)*0.08); $min-=$padding; $max+=$padding
    $denom=[Math]::Max(1,$prices.Count-1)
    for($i=0;$i -le 4;$i++) {
        $y=$top+($bottom-$top)*$i/4
        $line=New-Object Windows.Shapes.Line
        $line.X1=$left; $line.X2=$right; $line.Y1=$y; $line.Y2=$y; $line.Stroke='#E9EFF0'
        [void]$canvas.Children.Add($line)
        Add-ChartText (Money ($max-($max-$min)*$i/4)) 0 ($y-7)
    }
    $line=New-Object Windows.Shapes.Polyline; $line.Stroke='#2563EB'; $line.StrokeThickness=1.8
    $line.StrokeLineJoin='Round'
    $step=[Math]::Max(1,[int][Math]::Ceiling($prices.Count/900.0))
    for($i=0;$i -lt $prices.Count;$i+=$step) {
        $x=$left+($right-$left)*$i/$denom; $y=$bottom-($bottom-$top)*([double]$prices[$i]-$min)/($max-$min)
        $line.Points.Add([Windows.Point]::new($x,$y))
    }
    $line.Points.Add([Windows.Point]::new($right,$bottom-($bottom-$top)*([double]$prices[-1]-$min)/($max-$min)))
    $fill=New-Object Windows.Shapes.Polygon; $fill.Fill='#EAF0FF'
    $fill.Points.Add([Windows.Point]::new($left,$bottom))
    foreach($point in $line.Points) { $fill.Points.Add($point) }
    $fill.Points.Add([Windows.Point]::new($right,$bottom))
    [void]$canvas.Children.Add($fill); [void]$canvas.Children.Add($line)
    Add-ChartText $dates[0] $left ($bottom+9)
    Add-ChartText $dates[-1] ([Math]::Max($left,$right-65)) ($bottom+9)
    foreach($trade in $script:History.trades) {
        foreach($side in @('buy','sell')) {
            $day=if($side -eq 'buy'){$trade.buyDay}else{$trade.sellDay}
            $price=if($side -eq 'buy'){$trade.buyPrice}else{$trade.sellPrice}
            $marker=New-Object Windows.Shapes.Ellipse
            $marker.Width=9; $marker.Height=9; $marker.Stroke='White'; $marker.StrokeThickness=1.7
            $marker.Fill=if($side -eq 'buy'){'#2563EB'}else{'#F59E0B'}
            $marker.ToolTip="$side $($dates[$day]) at $(Money $price) $($script:AnalyzedCurrency)"
            [Windows.Controls.Canvas]::SetLeft($marker,($left+($right-$left)*$day/$denom-4.5))
            [Windows.Controls.Canvas]::SetTop($marker,($bottom-($bottom-$top)*($price-$min)/($max-$min)-4.5))
            [void]$canvas.Children.Add($marker)
        }
    }
}
function Show-History($result,$job) {
    $script:Ui.ResultState.Text = 'Analysis complete · valid ledger · hindsight result, not a forecast'
    $script:History=$result; $script:AnalyzedCurrency=$job.currency
    $script:Ui.ProfitText.Text=Money $result.profit
    $script:Ui.ProfitUnit.Text="$($job.currency) / one share at a time"
    $script:Ui.TradesText.Text=@($result.trades).Count.ToString()
    $script:Ui.LegalText.Text='Ledger validated'
    $script:Ui.CloseText.Text=Money $result.prices[-1]
    $script:Ui.LastDateText.Text="$($result.dates[-1]) / $($job.currency)"
    $script:Ui.RiskText.Text=if($result.risk){Decimal-Text $result.risk.var}else{'N/A'}
    $script:Ui.CvarText.Text=if($result.risk){"CVaR $(Decimal-Text $result.risk.cvar) $($job.currency)"}else{'Need at least 3 prices'}
    $script:Ui.DataSummary.Text="$($result.column)  /  $($result.dates.Count) observations  /  $($result.dates[0]) to $($result.dates[-1])  /  $($job.fileName)"
    $script:LedgerRows=@(foreach($t in $result.trades) {
        [pscustomobject]@{BuyDate=$t.buyDate;BuyPrice=(Money $t.buyPrice);SellDate=$t.sellDate;SellPrice=(Money $t.sellPrice);NetProfit=(Money $t.profit)}
    })
    $script:Ui.LedgerGrid.ItemsSource=[object[]]$script:LedgerRows
    $script:Ui.ExportButton.IsEnabled=$script:LedgerRows.Count -gt 0
    $script:Ui.LedgerNote.Text="Prices and profits in $($job.currency). Fee: $($job.fee) per completed trade. Cooldown: $($job.cooldown) observation days. Java optimizer: $($result.durationMs) ms."
    if($script:LedgerRows.Count -eq 0) { $script:Ui.LedgerNote.Text+=' No profitable completed trades under these settings.' }
    Draw-Chart
    Set-Status 'Analysis complete. Blue markers indicate buys; amber markers indicate sells. The ledger includes fees.'
}
function Show-ReplayDay {
    if(!$script:Replay){return}
    $index=[int]$script:Ui.DaySlider.Value
    $day=$script:Replay.days[$index]
    $script:Ui.ReplayDate.Text=$day.date
    $script:Ui.ReplayPosition.Text="Observation $($index+1) of $($script:Replay.days.Count)  /  $($day.returnsSeen) past returns observed"
    $script:Ui.SpendText.Text=Money $day.spent; $script:Ui.BasketText.Text=Money $day.value
    $script:Ui.ReplayRiskText.Text=if($day.risk){Decimal-Text $day.risk.var}else{'N/A'}
    $script:Ui.NewsText.Text=$day.newsCount.ToString()
    $script:Ui.AssetsGrid.ItemsSource=[object[]]@(foreach($a in $day.assets){
        [pscustomobject]@{Stock=$a.ticker;Close=(Money $a.price);Mentions=$a.mentions;Profit=(Money $a.profit);Selection=$(if($a.selected){'Selected'}else{'Not selected'})}
    })
    $script:Ui.FillsGrid.ItemsSource=[object[]]@(foreach($f in $day.fills){
        [pscustomobject]@{Stock=$f.ticker;Venue=$f.venue;Shares=$f.quantity;UnitCost=(Money $f.unitCost)}
    })
    $script:Ui.SelectionNote.Text="Utility: $($day.utility)  /  Reserved: $(Money $day.reserved) USD  /  Daily budget: $($script:ReplayBudget) USD. Utility is a classroom score."
    $script:Ui.ReplayRiskNote.Text=if($day.risk){"One-day 95% bootstrap risk: CVaR $(Decimal-Text $day.risk.cvar) USD, $($day.risk.paths) paths. Negative losses indicate gains."}else{'Risk unavailable: need a selected basket and at least two observed returns.'}
    $script:Ui.PrevButton.IsEnabled=$index -gt 0
    $script:Ui.NextButton.IsEnabled=$index -lt ($script:Replay.days.Count-1)
}
function Start-Engine([string]$mode) {
    if($script:CurrentJob){return}
    $script:PlayTimer.Stop(); $script:Ui.PlayButton.Content='Play'
    try {
        $request=@{mode=$mode}
        if ($mode -eq 'lab') {
            if (!$script:Ui.SearchQuery.Text.Trim()) { throw 'Enter a search query first.' }
            $request.query = $script:Ui.SearchQuery.Text; $request.text = $script:Ui.SearchText.Text
        }
        if($mode -in @('history','replay')) {
            $trades=0; $cooldown=0
            if(![int]::TryParse($script:Ui.TradesInput.Text,[ref]$trades) -or $trades -lt 0 -or $trades -gt 1000){throw 'Maximum trades must be a whole number from 0 to 1000.'}
            if(![int]::TryParse($script:Ui.CooldownInput.Text,[ref]$cooldown) -or $cooldown -lt 0 -or $cooldown -gt 1000){throw 'Cooldown must be a whole number from 0 to 1000.'}
            $fee=$script:Ui.FeeInput.Text.Trim()
            if($fee -notmatch '^\d+(\.\d+)?$'){throw 'Enter a nonnegative fee, for example 0.50.'}
            $request.trades=$trades; $request.cooldown=$cooldown; $request.fee=$fee
            if($mode -eq 'history') {
                if(!$script:Ui.ColumnCombo.SelectedItem){throw 'Choose a price column first.'}
                $request.file=$script:SourceFile; $request.column=[string]$script:Ui.ColumnCombo.SelectedItem
            } else {
                $days=0
                if(![int]::TryParse($script:Ui.WindowInput.Text,[ref]$days) -or $days -lt 2 -or $days -gt 1000){throw 'Analysis window must be from 2 to 1000 observations.'}
                $budget=$script:Ui.BudgetInput.Text.Trim()
                if($budget -notmatch '^\d+(\.\d+)?$'){throw 'Enter a nonnegative daily budget, for example 200.00.'}
                $request.budget=$budget; $request.window=$days
            }
        }
        $id=[Guid]::NewGuid().ToString('N')
        $requestPath=Join-Path $script:WorkDir "$id-request.json"; $responsePath=Join-Path $script:WorkDir "$id-response.json"
        [IO.File]::WriteAllText($requestPath,($request | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
        $settings=New-Object Diagnostics.ProcessStartInfo
        $settings.FileName="$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
        $settings.Arguments="-NoProfile -ExecutionPolicy Bypass -File `"$($script:ProjectRoot)\tools\gui-worker.ps1`" -RequestFile `"$requestPath`" -ResponseFile `"$responsePath`""
        $settings.WorkingDirectory=$script:ProjectRoot; $settings.UseShellExecute=$false; $settings.CreateNoWindow=$true
        $settings.RedirectStandardOutput=$true; $settings.RedirectStandardError=$true
        $process=New-Object Diagnostics.Process; $process.StartInfo=$settings
        [void]$process.Start()
        $script:CurrentJob=@{process=$process;stdout=$process.StandardOutput.ReadToEndAsync();stderr=$process.StandardError.ReadToEndAsync();response=$responsePath;mode=$mode;
            currency=[string]$script:Ui.CurrencyCombo.SelectedItem.Content;fileName=[IO.Path]::GetFileName($script:SourceFile);
            fee=$request.fee;cooldown=$request.cooldown;budget=$request.budget}
        Set-Busy $true
        Set-Status "Preparing the Java engine and running $mode. You can keep browsing the interface."
        $script:PollTimer.Start()
    } catch { Set-Status $_.Exception.Message $true }
}
$script:PollTimer=New-Object Windows.Threading.DispatcherTimer
$script:PollTimer.Interval=[TimeSpan]::FromMilliseconds(200)
$script:PollTimer.Add_Tick({
    if(!$script:CurrentJob -or !$script:CurrentJob.process.HasExited){return}
    $script:PollTimer.Stop()
    $job=$script:CurrentJob; $script:CurrentJob=$null
    try {
        if(!(Test-Path -LiteralPath $job.response)) {
            $details=$job.stderr.GetAwaiter().GetResult()
            throw "The analysis could not finish. $details"
        }
        $response=[IO.File]::ReadAllText($job.response) | ConvertFrom-Json
        if(!$response.ok){throw $response.error}
        switch($job.mode) {
            history { Show-History $response.result $job }
            replay {
                $script:Replay=$response.result; $script:ReplayBudget=$job.budget
                $script:Ui.DaySlider.Maximum=$script:Replay.days.Count-1
                $script:Ui.DaySlider.Value=0; $script:Ui.DaySlider.IsEnabled=$true
                $script:Ui.PlayButton.IsEnabled=$true
                Show-ReplayDay
                Set-Status 'Replay ready. Use Play, Next or the date slider. Each snapshot uses only observations available by that day.'
            }
            lab { $script:Ui.LabOutput.Text=$response.result.text; Set-Status 'Algorithm comparisons ready. Search uses your text; the other scenarios are synthetic.' }
            default { $script:Ui.TestOutput.Text=$response.result.text; Set-Status 'Verification finished. Read the results above.' }
        }
    } catch {
        Set-Status $_.Exception.Message $true
        if($job.mode -in @('tests','benchmark')){$script:Ui.TestOutput.Text=$_.Exception.Message}
    } finally { $job.process.Dispose(); Set-Busy $false }
})
$script:PlayTimer=New-Object Windows.Threading.DispatcherTimer
$script:PlayTimer.Interval=[TimeSpan]::FromMilliseconds(450)
$script:PlayTimer.Add_Tick({
    if($script:Ui.DaySlider.Value -ge $script:Ui.DaySlider.Maximum){$script:PlayTimer.Stop();$script:Ui.PlayButton.Content='Play';return}
    $script:Ui.DaySlider.Value++
})
$script:Ui.AnalysisNav.Add_Click({Set-Page 'history'})
$script:Ui.ReplayNav.Add_Click({Set-Page 'replay'})
$script:Ui.LabNav.Add_Click({Set-Page 'lab'})
$script:Ui.LabButton.Add_Click({Start-Engine 'lab'})
$script:Ui.VerifyNav.Add_Click({Set-Page 'verify'})
$script:Ui.RunButton.Add_Click({Start-Engine $script:Page})
$script:Ui.TestsButton.Add_Click({Start-Engine 'tests'})
$script:Ui.BenchmarkButton.Add_Click({Start-Engine 'benchmark'})
$script:Ui.ImportButton.Add_Click({Select-Source})
$script:Ui.DropBrowseButton.Add_Click({Select-Source})
$script:Ui.DropZone.Add_PreviewDragOver({
    param($sender,$eventArgs)
    $path = Get-DroppedCsv $eventArgs
    $eventArgs.Effects = if ($path) { [Windows.DragDropEffects]::Copy } else { [Windows.DragDropEffects]::None }
    $eventArgs.Handled = $true
    $script:Ui.DropZone.BorderBrush = if ($path) { '#2563EB' } else { '#EF4444' }
    $script:Ui.DropZone.Background = if ($path) { '#EAF0FF' } else { '#FFF1F2' }
    $script:Ui.DropHint.Text = if ($path) { 'Release to preview this CSV' } else { 'Drop one .csv file; wait if analysis is running' }
})
$script:Ui.DropZone.Add_DragLeave({
    $script:Ui.DropZone.BorderBrush = '#C6D5F5'; $script:Ui.DropZone.Background = 'White'
    $script:Ui.DropHint.Text = 'One .csv file · up to 32 MB · Date + a price column'
})
$script:Ui.DropZone.Add_PreviewDrop({
    param($sender,$eventArgs)
    $eventArgs.Handled = $true
    $script:Ui.DropZone.BorderBrush = '#C6D5F5'; $script:Ui.DropZone.Background = 'White'
    $script:Ui.DropHint.Text = 'One .csv file · up to 32 MB · Date + a price column'
    try {
        $path = Get-DroppedCsv $eventArgs
        if (!$path) { throw 'Drop one .csv file after the current analysis finishes.' }
        Set-Source $path
    } catch { Set-Status $_.Exception.Message $true }
})
$script:Ui.SampleButton.Add_Click({
    try { Set-Source (Join-Path $script:ProjectRoot 'data\prices.csv'); $script:Ui.CurrencyCombo.SelectedIndex=0 } catch {Set-Status $_.Exception.Message $true}
})
$script:Ui.ExportButton.Add_Click({
    $dialog=New-Object Microsoft.Win32.SaveFileDialog
    $dialog.Filter='CSV trade ledger (*.csv)|*.csv';$dialog.FileName='TradeOptima-ledger.csv';$dialog.Title='Save the trade ledger';$dialog.OverwritePrompt=$true
    if($dialog.ShowDialog($script:GuiWindow)){
        try{
            $rows=@(foreach($row in $script:LedgerRows){[pscustomobject]@{Currency=$script:AnalyzedCurrency;BuyDate=$row.BuyDate;BuyPrice=$row.BuyPrice;SellDate=$row.SellDate;SellPrice=$row.SellPrice;NetProfit=$row.NetProfit}})
            $rows | Export-Csv -LiteralPath $dialog.FileName -NoTypeInformation -Encoding UTF8
            Set-Status "Ledger saved to $($dialog.FileName)"
        }catch{Set-Status $_.Exception.Message $true}
    }
})
$script:Ui.AlgorithmGuideButton.Add_Click({Start-Process -FilePath 'notepad.exe' -ArgumentList "`"$script:ProjectRoot\docs\FEATURE-ALGORITHM-GUIDE.md`""})
$script:Ui.GuideButton.Add_Click({Start-Process -FilePath 'notepad.exe' -ArgumentList "`"$script:ProjectRoot\README.md`""})
$script:Ui.DaySlider.Add_ValueChanged({Show-ReplayDay})
$script:Ui.PrevButton.Add_Click({$script:PlayTimer.Stop();$script:Ui.PlayButton.Content='Play';if($script:Ui.DaySlider.Value -gt 0){$script:Ui.DaySlider.Value--}})
$script:Ui.NextButton.Add_Click({$script:PlayTimer.Stop();$script:Ui.PlayButton.Content='Play';if($script:Ui.DaySlider.Value -lt $script:Ui.DaySlider.Maximum){$script:Ui.DaySlider.Value++}})
$script:Ui.PlayButton.Add_Click({
    if($script:PlayTimer.IsEnabled){$script:PlayTimer.Stop();$script:Ui.PlayButton.Content='Play'}
    else{if($script:Ui.DaySlider.Value -ge $script:Ui.DaySlider.Maximum){$script:Ui.DaySlider.Value=0};$script:PlayTimer.Start();$script:Ui.PlayButton.Content='Pause'}
})
$script:Ui.PriceChart.Add_SizeChanged({Draw-Chart})
foreach($name in @('TradesInput','FeeInput','CooldownInput','BudgetInput','WindowInput')){
    $script:Ui[$name].Add_TextChanged({if(!$script:CurrentJob){$script:Ui.ResultState.Text='Settings changed · analyze again to update results'; Set-Status 'Settings changed. Run the analysis again to update the displayed results.'}})
}
$script:Ui.ColumnCombo.Add_SelectionChanged({if(!$script:CurrentJob){$script:Ui.ResultState.Text='Price column changed · analyze again to update results'; Set-Status 'Price column selected. Analyze stock to update the results.'}})
$script:Ui.CurrencyCombo.Add_SelectionChanged({if(!$script:CurrentJob){$script:Ui.ResultState.Text='Currency label changed · analyze again to update results'; Set-Status 'Currency is a display label, not a conversion. Analyze again to apply it.'}})
$script:GuiWindow.Add_Closed({
    $script:PollTimer.Stop();$script:PlayTimer.Stop()
    if($script:CurrentJob -and !$script:CurrentJob.process.HasExited){
        Start-Process -FilePath "$env:SystemRoot\System32\taskkill.exe" -ArgumentList "/PID $($script:CurrentJob.process.Id) /T /F" -WindowStyle Hidden
    }
})
$script:GuiWindow.Dispatcher.Add_UnhandledException({
    param($sender,$eventArgs)
    $eventArgs.Handled=$true
    Set-Status ("Interface error: "+$eventArgs.Exception.Message) $true
    [IO.File]::AppendAllText((Join-Path $script:WorkDir 'interface-errors.log'),$eventArgs.Exception.ToString()+[Environment]::NewLine)
})
Set-Source $script:SourceFile
if ($SmokeTest) {
    function Assert-Ui($condition, [string]$message) { if (!$condition) { throw $message } }
    function Render-Page([string]$page) {
        Set-Page $page
        $root = $script:GuiWindow.Content
        $root.Background = $script:GuiWindow.Background
        $size = [Windows.Size]::new(1380,900)
        $root.Measure($size); $root.Arrange([Windows.Rect]::new(0,0,1380,900)); $root.UpdateLayout()
        if ($page -eq 'history') { Draw-Chart }; $root.UpdateLayout()
        $bitmap = [Windows.Media.Imaging.RenderTargetBitmap]::new(1380,900,96,96,[Windows.Media.PixelFormats]::Pbgra32)
        $bitmap.Render($root)
        $encoder = [Windows.Media.Imaging.PngBitmapEncoder]::new()
        $encoder.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
        $stream = [IO.File]::Create((Join-Path $script:ProjectRoot "reports\gui-$page.png"))
        try { $encoder.Save($stream) } finally { $stream.Dispose() }
    }
    function Wait-UiJob {
        $until = [DateTime]::UtcNow.AddSeconds(45)
        $timer = [Windows.Threading.DispatcherTimer]::new()
        $timer.Interval = [TimeSpan]::FromMilliseconds(30)
        $timer.Add_Tick({$script:SmokeFrame.Continue=$false})
        $timer.Start()
        try {
            while ($script:CurrentJob -and [DateTime]::UtcNow -lt $until) {
                $script:SmokeFrame = [Windows.Threading.DispatcherFrame]::new()
                [Windows.Threading.Dispatcher]::PushFrame($script:SmokeFrame)
            }
        } finally { $timer.Stop() }
        Assert-Ui (!$script:CurrentJob) 'GUI job timed out'
    }
    New-Item -ItemType Directory -Path (Join-Path $script:ProjectRoot 'reports') -Force | Out-Null
    function New-TestDrag($payload) {
        $ctor = [Windows.DragEventArgs].GetConstructors([Reflection.BindingFlags]'Instance,NonPublic')[0]
        return $ctor.Invoke([object[]]@($payload,[Windows.DragDropKeyStates]::None,[Windows.DragDropEffects]::Copy,$script:Ui.DropZone,[Windows.Point]::new(20,20)))
    }
    # Routed drag/drop events exercise the actual handlers without moving the desktop mouse.
    $sample = Join-Path $script:ProjectRoot 'data\aapl-close.csv'
    $payload = [Windows.DataObject]::new([Windows.DataFormats]::FileDrop,[string[]]@($sample))
    $drag = New-TestDrag $payload
    $drag.RoutedEvent = [Windows.DragDrop]::PreviewDragOverEvent
    $script:Ui.DropZone.RaiseEvent($drag)
    Assert-Ui ($drag.Effects -eq [Windows.DragDropEffects]::Copy) 'CSV drag must be accepted'
    $drop = New-TestDrag $payload
    $drop.RoutedEvent = [Windows.DragDrop]::PreviewDropEvent
    $script:Ui.DropZone.RaiseEvent($drop)
    Assert-Ui ($script:SourceFile -eq $sample -and $script:Ui.PreviewGrid.Items.Count -eq 5) 'Drop must select CSV and show preview'
    $bad = [Windows.DataObject]::new([Windows.DataFormats]::FileDrop,[string[]]@($sample,$sample))
    $badDrag = New-TestDrag $bad
    $badDrag.RoutedEvent = [Windows.DragDrop]::PreviewDragOverEvent
    $script:Ui.DropZone.RaiseEvent($badDrag)
    Assert-Ui ($badDrag.Effects -eq [Windows.DragDropEffects]::None) 'Multiple files must be rejected'
    $script:Ui.DropZone.Background='White'; $script:Ui.DropZone.BorderBrush='#C6D5F5'
    $script:Ui.DropHint.Text='Drop another CSV here to replace the selected file'
    Start-Engine 'history'; Wait-UiJob
    Assert-Ui ($script:History -and $script:Ui.ExportButton.IsEnabled) ('History failed: '+$script:Ui.StatusText.Text)
    Render-Page 'history'
    Set-Page 'lab'; Start-Engine 'lab'; Wait-UiJob
    Assert-Ui ($script:Ui.LabOutput.Text.Contains('KMP:') -and $script:Ui.LabOutput.Text.Contains('GBM Monte Carlo')) ('Lab failed: '+$script:Ui.StatusText.Text)
    Render-Page 'lab'
    Set-Page 'replay'; Start-Engine 'replay'; Wait-UiJob
    Assert-Ui ($script:Replay.days.Count -eq 2306) ('Replay failed: '+$script:Ui.StatusText.Text)
    $script:Ui.DaySlider.Value=$script:Ui.DaySlider.Maximum
    Render-Page 'replay'; Render-Page 'verify'
    $script:PollTimer.Stop(); $script:PlayTimer.Stop()
    Write-Output 'PASS: CSV drag/drop preview, multi-file rejection, asynchronous history/lab/replay jobs and four page renders.'
    exit 0
}
$script:GuiWindow.Add_ContentRendered({Draw-Chart;Start-Engine 'history'})
[void]$script:GuiWindow.ShowDialog()
