# TradeOptima

DSA-3 (Advanced Algorithms), KL University Hyderabad  
Course 25CS2103E, Section 3  
Team: V. Rohan (2520030558), J. Satyan (2520030088), K. Shankar (2520030063)

A Java teaching platform with five hand-built algorithmic subsystems, numbered **Modules 2 through 6**.

## Start on this PC

**Double-click TradeOptima.exe in this folder.** This opens the Windows desktop interface, with no terminal window. It automatically loads and analyzes the bundled AAPL sample.

- **Stock analysis:** import a CSV, select its price column and currency label, adjust trade limits/fee/cooldown, view the price chart and dated ledger, and export the ledger.
- **Market replay:** load the bundled four-stock scenario, then use Play, Previous, Next or the date slider to inspect the five modules together.
- **Verification:** run built-in correctness tests or the 100,000-price benchmark with a button.

Read [docs/GUI.md](docs/GUI.md) for the desktop guide. START-GUI.vbs is an alternative windowless launcher. Keep the executable inside the project folder with gui.ps1, ui/, src/ and tools/. No installation or additional download is required on this PC.

The following terminal workflow is still available if you prefer it.

Open PowerShell:

```powershell
cd "C:\COLLEGE\DSA-3\2nd path\TradeOptima"
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1
```

Or double-click **START-DEMO.cmd**. The launcher finds JDK 17+ (JDK 21 is installed on this PC), compiles, and runs the historical demo. It does not depend on the old Java 8 entry in the system PATH. No JDK installation or system PATH changes are needed.

The first-path source copy is preserved. This is the working continuation.

## Commands

Run these from the project folder. If PowerShell blocks scripts, use the same `powershell -NoProfile -ExecutionPolicy Bypass -File` prefix shown above. It applies only to that invocation.

| Command | Purpose |
| --- | --- |
| `.\run.ps1 -Mode demo` | Process all historical observations and show the final pipeline snapshot and dated AAPL ledger |
| `.\run.ps1 -Mode replay -Days 10 -Every 1` | Show each of the first ten observations |
| `.\run.ps1 -Mode replay -Every 250` | Replay all 2,306 dates, printing periodic snapshots and news events |
| `.\run.ps1 -Mode test` | Original randomized self-tests plus CSV and pipeline checks, without JUnit |
| `.\test.ps1` | Separate JUnit 5 tests for each subsystem and integration |
| `.\test.ps1 -IncludeBenchmark` | Include the machine-dependent two-second performance assertion |
| `.\run.ps1 -Mode benchmark` | Run the slide-target benchmark alone |
| `.\run.ps1 -Mode history -PriceFile .\data\aapl-close.csv -PriceColumn Close` | Analyze a Date,Close CSV and print dated trades |
| `.\run.ps1 -Mode legacy` | Original generated-data demonstration |
| `.\docs.ps1` | Generate Javadoc into docs/api/index.html |

Optional settings: `-Budget 200.00 -Window 60 -Every 100 -Days 0`. Zero days means all observations. Pipeline defaults: three trades, USD 0.50 fee, one-day cooldown, FPTAS epsilon 0.1, 2,000 risk paths. History mode also accepts `-Trades 3 -Fee 0.50 -Cooldown 1`.

To use direct Java commands in the current terminal, run `. .\tools\java-env.ps1` first, then `javac -d out src\tradeoptima\*.java` and `java -Xmx512m -cp out tradeoptima.Main legacy`. The historical pipeline uses the launcher to transport CSV files.

## What the demonstration means

- **Real historical observations:** four equities, AAPL, MSFT, IBM and SBUX, from Plotly's archived stock dataset, 2007-01-03 through 2016-03-01. This is an old teaching dataset, not current market data.
- **Synthetic inputs:** headlines and venue books are labelled classroom fixtures. They are not historical news or exchange liquidity. Source details and assumptions are in [data/SOURCES.md](data/SOURCES.md).
- **Hindsight optimization:** the DP finds the best profit attainable with complete knowledge of its input window. The full-history ledger uses all dates. Each replay window uses only dates already observed.
- **Paper allocation snapshots:** the budget resets each day. There is no persistent account, no cumulative trading return, and no orders sent to a broker.
- **Selection utility:** past-window DP profit in cents plus one point per current news mention. This deliberately simple classroom score is not a forecast or sentiment model.
- **Risk:** a one-day, 95% bootstrap loss estimate for the currently selected fixed-share basket. Historical joint dates preserve co-movement across its assets. Loss excludes routing charges. Negative VaR indicates a gain at that quantile.

## Source rule and test tooling

All Java files in `src/tradeoptima/` use no imported library classes. No collection utilities, regex splitting, or library formatting helpers appear in project source. Arrays and the helpers in Ds.java implement the data structures. Standard input/output is accessed through System.

PowerShell reads local files and forwards UTF-8 text to Java. CsvLoader parses and validates the text itself. This separation permits local data loading without adding file-library imports to the Java source.

With Satyan's approval, only `tests/tradeoptima/` uses JUnit imports. JUnit is external test tooling; its internal dependencies are not part of the hand-built algorithms. The first test run downloads the pinned JUnit Console 1.11.4 / Jupiter 5.11.4 jar from Maven Central and verifies its SHA-256. Later test runs work offline with that jar present. The application itself needs no network.

## Files to study

| File | Role |
| --- | --- |
| Ds.java | Arrays, bit storage, random generator and numeric formatting |
| StringAlgorithms.java, AhoCorasick.java, SearchEngine.java | Module 2 |
| TradeOptimizer.java | Module 3: original DP and ledger API |
| MinCostMaxFlow.java, HopcroftKarp.java, OrderRouter.java | Module 4 |
| PortfolioSelector.java | Module 5 |
| RiskEngine.java | Module 6 |
| CsvLoader.java, MarketData.java | CSV parsing, validation and fixture adapters |
| Pipeline.java | Ordered replay and module integration |
| Main.java | Commands and readable output |
| SelfTest.java, IntegrationTest.java | Reference-based tests without external dependencies |

See [docs/DESIGN.md](docs/DESIGN.md) for algorithms and limitations, and [docs/VIVA.md](docs/VIVA.md) for a presentation guide.

## Local Git

A local Git repository is initialized on branch main. No remote is configured and nothing has been uploaded. Git author information was not configured on this machine, so an initial commit was not created using an invented identity. To record your first commit, use your own Git name and email:

```powershell
& "C:\Program Files\Git\cmd\git.exe" config user.name "Your real name"
& "C:\Program Files\Git\cmd\git.exe" config user.email "Your Git email"
& "C:\Program Files\Git\cmd\git.exe" add .
& "C:\Program Files\Git\cmd\git.exe" commit -m "Add historical TradeOptima platform"
```

Generated class files, downloaded test tooling, test reports and generated Javadoc are ignored. The source, fixtures, scripts and design notes belong in version control.


## October GUI and algorithm review

Stock analysis now supports drag-and-drop of one CSV, drag feedback, a five-row preview and explicit stale-result notices. The Algorithm workspace exposes KMP, Z-search, Aho-Corasick, edit distance, whole-block matching, portfolio alternatives and two risk models through the Java engine. Replay still uses the original integrated pipeline.

See [docs/FEATURE-ALGORITHM-GUIDE.md](docs/FEATURE-ALGORITHM-GUIDE.md) for the exact feature-to-algorithm mapping, run instructions, complexities, trader uses and limitations. [docs/ALGORITHM-REVIEW.md](docs/ALGORITHM-REVIEW.md) records the review and verification evidence.
