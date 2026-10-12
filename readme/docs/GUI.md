# TradeOptima desktop guide

## Open the application

Double-click **TradeOptima.exe** in `C:\COLLEGE\DSA-3\2nd path\TradeOptima`. Keep it inside this folder: the scripts, Java sources and data beside it are part of the application. The bundled AAPL analysis appears automatically after the Java engine compiles.

There is no terminal window. The status line and progress bar show what is happening. Errors appear in the status line; an unsuccessful analysis does not overwrite the last successful result.

## Analyze a stock

1. Choose **Stock analysis** in the sidebar.
2. Use the bundled sample, or click **Import a CSV...** and choose your downloaded file.
3. Select the actual price column, usually Close or Adj Close. The CSV must have a Date column and strictly increasing YYYY-MM-DD dates. Extra columns are allowed.
4. Choose USD or INR to match the source data. This changes the label only; it does not convert currencies.
5. Set maximum completed trades, fee per completed trade, and cooldown in observation days.
6. Click **Analyze stock**.

The chart shows the selected historical series. Green dots mark optimal buys and orange dots mark sells. Hover over a marker for its date and price. Large series are sampled only for drawing the overview; Java analyzes every input row, and the ledger retains every completed trade.

The result cards show hindsight profit, completed trades, latest price, and a one-day 95% bootstrap VaR estimate for **one share** valued at the final close. CVaR appears beneath VaR. The risk estimate uses up to 500 sampled historical returns and 5,000 simulation paths. Risk needs at least three prices and excludes trading fees. Negative losses indicate gains.

The ledger includes the configured fee in each net profit. **Export ledger...** saves a CSV containing currency, buy/sell dates and prices, and net profit. A result with no profitable completed trades has an empty ledger and a zero hindsight profit.

Changing settings, price column or source file does not automatically recalculate. The status line asks you to analyze again, and the previous result keeps its original labels until replaced.

The app imports local CSV files. It does not automatically download market data from a provider.

## Replay the full platform

Choose **Market replay**, set the daily USD budget and analysis window, and click **Load market replay**. The same trade-limit, fee and cooldown controls apply to its window optimizer.

Java computes each dated snapshot using only that day's available price history and headlines. The interface stores the resulting snapshots in memory so that the slider and playback are responsive. Moving forward or backward displays these already computed snapshots; it does not run an actual market stream.

- Search, analysis and selection table: ticker, close, current headline mentions, hindsight window profit and selected/not-selected state.
- Routing table: selected stocks, fictitious venue, share quantity and all-in price.
- Risk card: one-day 95% loss estimate for the selected basket; CVaR appears below the routing table.

Use **Next**, **Previous**, **Play/Pause**, or the slider. Play advances roughly one observation every 450 milliseconds. The budget resets each day. Replay uses the bundled four-stock dataset; importing a single CSV on the stock-analysis page does not replace this multi-stock scenario.

## Verify the algorithms

The **Verification** page runs the built-in Java self-tests and integration checks without JUnit downloads. The separate benchmark button checks the 100,000-price, 1,000-trade workload against the two-second target. Output appears in the window. The complete JUnit suite remains available through test.ps1 and includes GUI-adapter tests.

## Implementation for your viva

- **Java:** all data validation, CSV parsing, optimization, news matching, portfolio selection, routing and risk calculations.
- **GuiBridge.java:** a small output adapter using the existing algorithm APIs. It writes JSON with a hand-built string escaper; it has no library imports.
- **PowerShell and WPF:** local file picker, settings, layout, charts, tables, export, and background process management.
- **Launcher.cs:** a small Windows executable host that opens the WPF interface without a console window. It contains no trading algorithms.

The original Java restriction still applies. Native Windows libraries belong to the separate interface, not the hand-built Java algorithms. JUnit remains isolated in tests/.

The desktop uses the Windows .NET Framework, Windows PowerShell 5.1 and the installed JDK 17+. These are already present on this PC. The app selects the installed JDK rather than relying on the old Java 8 PATH entry.

To rebuild the executable after changing Launcher.cs, run tools/build-gui.ps1. Changes to gui.ps1 or ui/main.xaml take effect when the app is reopened. Java source is compiled before each analysis job. Scratch requests and responses stay in the ignored work/gui folder. The application has no network listener and sends no data to a server.
