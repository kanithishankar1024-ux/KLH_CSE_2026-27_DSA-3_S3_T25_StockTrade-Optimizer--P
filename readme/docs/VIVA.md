# Five-minute demonstration and viva notes

## Demonstration order

1. Run `run.ps1 -Mode replay -Days 10 -Every 1`. Explain that each printed day is an independent classroom allocation.
2. Point to M2 mention counts, M3 past-window optimum, M5 selected assets, M4 cheapest eligible venue and M6 risk.
3. Run `run.ps1 -Mode demo`. Show the dated full-history ledger and its legality check.
4. Run `test.ps1 -IncludeBenchmark`. Show independent tests for Modules 2–6 and the measured runtime.
5. Open docs/api/index.html for generated Javadoc.

## Questions you should be ready to answer

**Why does the DP not trade live?**  
It optimizes after seeing all prices in its input window. The full-history result is a hindsight bound. Replay restricts that window to already observed dates.

**How is cooldown enforced?**  
Buying on day i reads the flat state from i-c-1, so the preceding sale must be far enough in the past.

**Why integer cents?**  
They avoid accumulated decimal rounding errors in profit and routing cost. External decimal prices are rounded once when loaded.

**How does the ledger fit in memory?**  
The DP stores only recent value layers, plus two bits per state recording whether a buy or sale improved that state. Backtracking follows those bits.

**Why network flow?**  
Greedy routing can block restricted orders. Residual edges allow the solver to reassign earlier flow to satisfy more quantity at minimum total cost.

**Why is portfolio selection hard?**  
It is 0/1 knapsack: each candidate is taken once or skipped. An exact budget DP is pseudo-polynomial. Scaling utility gives a controllable approximation.

**What does the approximation guarantee mean here?**  
It guarantees the chosen portfolio's classroom utility relative to the optimal utility under the budget. It does not guarantee market returns.

**What makes risk randomized?**  
Reservoir sampling picks past observations, Monte Carlo draws possible returns, and randomized quickselect finds a quantile without sorting all losses.

**What is real data here?**  
The price observations come from Plotly's archived stock dataset. Headlines, venue quotes, liquidity and the selection score are synthetic teaching inputs. The dataset is old and its adjustment methodology is not independently verified.

**Is this a profitable trading strategy?**  
No claim of that kind is made. It is a platform demonstrating advanced algorithms. Replay reports independent paper scenarios, not cumulative investment performance.

**Where are file imports?**  
PowerShell reads local files and passes text through System.in. Java performs CSV parsing and all algorithmic work using arrays and the allowed language classes.

**Why is JUnit allowed?**  
It is isolated test tooling in tests/, explicitly approved separately. The algorithm source in src/ remains free of library imports.

**What changed from the original?**  
CSV inputs, replay, pipeline, CLI commands, standalone and JUnit checks, source validation, launcher and documentation. The original APIs remain. Fee validation and log-return drift were corrected.

**What would you improve next?**  
A persistent cash/position simulator with an explicitly causal strategy, real timestamped news/book data, iterative matching DFS for huge graphs, and resource guards for unusually large FPTAS settings.
