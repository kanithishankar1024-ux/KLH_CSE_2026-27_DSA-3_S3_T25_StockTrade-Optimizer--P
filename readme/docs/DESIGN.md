# Design and limitations

## Data path

PowerShell local files → UTF-8 standard input → CsvLoader → MarketData → Pipeline.next() → printed snapshot.

The launcher sends four length-prefixed text sections. Java decodes UTF-8 and parses the CSV itself. No CSV library is used. File transport is separate from the Java algorithms to preserve the source restriction.

MarketData validates a complete input file before replay. Pipeline.next() advances one observation at a time. The prefix window ends at the current date. Future prices are stored but never read for the current calculation. Future headlines remain unprocessed. A test mutates later prices/headlines and compares all earlier analysis, allocations and risk results.

## Module 2: ticker and news search

KMP and Z search return exact match positions in O(text + pattern) time. Aho-Corasick builds a trie with failure links and scans many patterns in O(text + reported matches). SearchEngine filters to whole-word matches and maps pattern IDs back to instruments. Fuzzy lookup uses Levenshtein distance.

The replay scans only newly available headlines. Mention counts are simple counts, not sentiment. ASCII folding and first-ID behavior for duplicate patterns remain as in the original source. Avoid ambiguous or duplicate company/ticker aliases. Whole-word boundaries currently treat letters and digits as word characters.

## Module 3: constrained optimizer

One share at a time, fee once on sale, at most k completed trades, and c full no-buy days after a sale.

HOLD[i,j] = max(HOLD[i-1,j], NOSTOCK[i-c-1,j-1] - price[i])  
NOSTOCK[i,j] = max(NOSTOCK[i-1,j], HOLD[i-1,j] + price[i] - fee)

The bounded implementation uses a ring buffer of c+2 value layers and two decision bits per day/state for reconstruction. Time O(Nk); value memory O(k(c+2)); ledger memory O(Nk) bits. At N=100,000 and k=1,000, the two bit tables occupy about 25 MB.

When k cannot bind, an O(N) implementation is used. The ledger records buy/sell days and prices. Days absent from that ledger imply no action; it is not an explicit row for every HOLD day. Ties prefer fewer trades, checked against an independent exhaustive interval-schedule reference.

DP requires the whole analyzed window. A replay result is a hindsight summary of the past window and is not an order signal with predictive validity.

The validator now checks ledger indices, ordering, cooldown, trade cap, prices, fees and total profit. Full brute-force tests are kept small because exhaustive search is exponential.

The two-second target is measured for N=100,000, k=1,000, fee 50 cents, cooldown 1. It is hardware-dependent and is not a guarantee across every cooldown, JVM or concurrent system load. Benchmark input is a seeded random walk, distinct from the real historical demonstration.

## Module 4: cross-venue routing

The network is source → order → ask level → sink. Capacities encode quantities and venue masks restrict eligible edges. SPFA successive shortest augmenting paths maximize filled quantity and minimize cost, including per-share venue fees. Reverse residual edges let the solver undo an earlier allocation when that improves the global solution.

This is not an order-priority or time-priority simulator. Routing is performed separately for each instrument so shares of different companies cannot satisfy one another's orders. The pipeline submits one-share paper orders; standalone tests exercise competing orders, partial fills, venue restrictions and residual rerouting.

Block orders use bipartite matching: one whole block per venue per round, with sufficient venue liquidity. This is the original simplified block model. HopcroftKarp DFS remains recursive and may overflow the stack for very large graphs. The input adapters cap venue counts to fit the venue mask.

## Module 5: portfolio selection

One candidate per instrument represents one share. Cost reserves the highest all-in quote in its fixture, so actual routed spending cannot exceed the selection's reserved budget. This can leave unused budget and is intentionally conservative.

Candidate utility = past-window DP profit in cents + today's mention count. It is an arbitrary teaching score, not expected return. The FPTAS guarantee concerns this score only, not future profit or risk-adjusted performance.

The original value-scaling FPTAS provides at least (1-epsilon) of optimal utility for nonnegative input values. Greedy chooses the better of density selection and the best feasible individual asset, giving at least half of optimal utility. Exact budget DP is a small-input oracle.

With n assets, scaled total value can be O(n²/epsilon). Value DP uses O(n²/epsilon) words; reconstruction stores O(n³/epsilon) bits; runtime is O(n³/epsilon). The earlier shorthand n*(n/epsilon) understates the full reconstruction bound. The replay limits n to 32 and fixes epsilon at 0.1. Do not use the exact oracle on a large monetary budget.

## Module 6: risk

Reservoir sampling retains at most 500 observed return-day indices. At each snapshot, the selected one-share basket is retrospectively valued on those paired dates. Taking each asset from the same date preserves their observed co-movement. The bootstrap samples basket log returns and simulates 2,000 one-day losses. This is a risk estimate for a hypothetical fixed-share basket, not the returns of a previously traded account.

For empirical confidence c and M paths, quickselect locates index ceil(c*M)-1. CVaR is the mean of that order statistic and all larger losses. This is the documented finite-sample convention. Loss = value * (1 - exp(simulated log return)); it may be negative.

The parametric model takes the empirical mean of **log returns** as log drift. Subtracting half the variance again would bias it downward; that bug has been corrected. The independent analytical-moment test detects that error. Risk input validation rejects short/nonfinite series and invalid confidence settings.

Risk uses cents converted to dollar values once at the boundary. Correlation, regime changes, small samples and historical adjustment conventions limit interpretation. It does not model intraday liquidity or forecast the news.

## Tests and source compatibility

The original package and algorithm APIs remain available. Main has a new command dispatcher; LegacyDemo preserves the old demo. SelfTest retains runAll() and adds runModule() for independent JUnit wrappers.

Tests include:
- 3,000 randomized DP comparisons with brute-force profit and ledger validation.
- 2,000 additional small schedules checking profit and the fewest-trades tie rule.
- KMP/Z/Aho-Corasick comparisons, fuzzy lookup and whole-word boundaries.
- Flow versus a cheapest-level reference, matching versus exhaustive reference, and restricted multi-order rerouting.
- FPTAS and greedy guarantees against exact knapsack on small random cases.
- Quickselect, reservoir inclusion frequencies, analytical risk moments and VaR sanity.
- CSV malformed inputs, rounding, quoted fields, dates, and replay isolation.
- An opt-in two-second benchmark assertion with a legal reconstructed ledger.

All pseudorandom checks use fixed seeds. The existing random generator uses modulo reduction for bounded values; it has a very small modulo bias. Reservoir checks are statistical sanity tests rather than a proof of perfectly uniform finite-word sampling. JUnit is isolated in the test folder.
