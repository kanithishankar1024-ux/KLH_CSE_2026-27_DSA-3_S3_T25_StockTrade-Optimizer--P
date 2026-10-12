# Verification record

Verified on 2026-10-01 on Satyan's Windows PC using the installed Temurin JDK 21.0.12.1. Compilation targets Java 17.

- Original source: all 12 Java files in the first-path source match their counterparts in TradeOptima.zip. The continuation lives in the second path.
- Original baseline: compiled and all existing self-tests passed before changes.
- JUnit run after GUI addition: 16 tests found, 16 passed, zero skipped or failed. Includes thousands of randomized reference comparisons and four adapter tests.
- Performance check after GUI addition: 564.7 ms for N=100,000, k=1,000, fee 50 cents, cooldown 1; 1,000 trades; ledger legal. This is one measured run on this machine, not a portable runtime guarantee.
- Full historical demo: all 2,306 observations processed, ending 2016-03-01.
- Full-history AAPL result under k=3, fee 50 cents and cooldown 1: 178.94 USD hindsight profit, three trades, legal ledger.
- Final paper basket at budget 200 USD: IBM and SBUX; actual synthetic routed cost 194.43 USD, below the 194.47 USD reservation.
- CSV history command: Date,Close input produced the same dated AAPL ledger.
- Replay: first ten days displayed independently in Windows PowerShell 5.1. The complete demo also succeeded through that launcher.
- CSV cases: quoted fields, escaped quotes, embedded newlines, CRLF, BOM, exact rounding, malformed rows, invalid dates, duplicate dates and bad prices.
- Future isolation: modifying later prices/news left earlier replay results unchanged.
- Java source inspection: zero library imports in src; JUnit imports appear only in tests; no prohibited collection-package references or String.format in any Java file.
- Javadoc generated successfully with syntax/HTML validation enabled.
- Local Git initialized on main. No remote, upload or author identity was configured. No initial commit was fabricated.

Detailed captured output is in reports/verification.txt and reports/demo.txt. Machine-generated JUnit XML reports are in reports/junit/. Generated reports and API pages are ignored by Git but included in the handoff archive.

The replay is a sequence of independent paper allocations. News and book inputs are synthetic fixtures. It does not report realized trading performance.

## Launcher output repair

After the initial handoff, Satyan reported seeing only the build confirmation in an interactive terminal. The launcher now redirects Java standard output and standard error explicitly, forwards output lines to PowerShell, and drains errors concurrently. It no longer depends on inherited console handles for the hidden Java process.

The full demo was verified again in Windows PowerShell 5.1. tools/test-launcher.ps1 captures the script's own output stream and checks that all five module results, the legal ledger and the replay-completion message are present. This specifically checks the output path that the earlier tests missed.

## Desktop interface

TradeOptima.exe was launched as a native window, inspected visually, and used to load the historical analysis and market replay. The stock chart and table showed the original three AAPL trades and 178.94 profit. Advancing the replay changed its date and tables to the next observation. No interface exceptions were logged during inspection.

tools/test-gui-bridge.ps1 verified the actual background-worker path with an imported Date,Close CSV whose filename contains spaces, an invalid price column, and the complete 2,306-day replay. First-day routed cost was 35.06 and final-day routed cost was 194.43, matching the existing pipeline. Structured errors were preserved. JUnit additionally checks the adapter's fee-inclusive ledger, short-history risk handling, invalid settings and JSON escaping.

The stock-analysis screen uses Modules 3 and 6. The market-replay screen displays all five modules. The GUI does not download data, send orders or replace the source restrictions.
