package tradeoptima;

/**
 * MODULE 3 (core): constrained multi-trade profit optimizer.
 *
 * Problem: given daily prices p[0..n-1], at most k round-trip trades (buy then sell), a fixed fee per
 * completed trade, and a cooldown of c days after every sell during which no buy is allowed, maximise
 * total profit and return the full Buy/Sell ledger. One share held at a time.
 *
 * State machine (per day i, with j buys used):
 *   HOLD[i][j]     - holding one share at the end of day i
 *   NOSTOCK[i][j]  - flat at the end of day i (covers both "idle" and "cooling down")
 *   NOSTOCK[i][j] = max( NOSTOCK[i-1][j],  HOLD[i-1][j] + p[i] - fee )        // sell today
 *   HOLD[i][j]    = max( HOLD[i-1][j],     NOSTOCK[i-c-1][j-1] - p[i] )       // buy today
 * Reading NOSTOCK from c+1 days back is exactly the cooldown: a buy on day i is only legal if the last
 * sell happened on or before day i-c-1. (c = 1 gives the Idle/Holding/Cooldown machine of the slides.)
 *
 * Complexity: O(n*k) time, but only O(k*(c+2)) words of value memory (ring buffer) plus 2 bits per (i,j)
 * for backtracking: n=1e5, k=1000 -> 2e8 bits = 25 MB. When k >= floor((n+c)/(c+2)) the limit cannot bind,
 * and a plain O(n) DP with parent flags is used instead.
 */
public final class TradeOptimizer {
    private TradeOptimizer() {}

    private static final long NEG = Long.MIN_VALUE / 4;   // "unreachable"

    public static final class Trade {
        public final int buyDay, sellDay;
        public final long buyPrice, sellPrice, profit;   // profit already net of fee
        Trade(int b, int s, long bp, long sp, long fee) {
            buyDay = b; sellDay = s; buyPrice = bp; sellPrice = sp; profit = sp - bp - fee;
        }
    }

    public static final class Result {
        public final long profit;
        public final Trade[] trades;
        Result(long profit, Trade[] trades) { this.profit = profit; this.trades = trades; }
    }

    /** @param prices  prices in integer minor units (cents) - avoids floating point drift
     *  @param k       max number of round-trip trades
     *  @param fee     fee charged per completed trade (on the sell)
     *  @param cooldown days after a sell in which buying is forbidden (>= 0) */
    public static Result solve(long[] prices, int k, long fee, int cooldown) {
        if (cooldown < 0 || fee < 0) throw new IllegalArgumentException("fee and cooldown must be >= 0");
        int n = prices.length;
        if (n < 2 || k <= 0) return new Result(0, new Trade[0]);
        long maxTrades = ((long) n + cooldown) / (cooldown + 2L);
        if (k >= maxTrades) return solveUnlimited(prices, fee, cooldown);
        return solveBounded(prices, k, fee, cooldown);
    }

    // ------------------------------------------------------------------ bounded k : O(n*k)
    private static Result solveBounded(long[] p, int k, long fee, int c) {
        final int n = p.length, W = k + 1, L = c + 2;
        long[][] ns = new long[L][W];                 // ring buffer of NOSTOCK layers, slot = t mod L, t = day+1
        for (int s = 0; s < L; s++) for (int j = 0; j < W; j++) ns[s][j] = NEG;
        ns[0][0] = 0;                                 // t = 0: before any trading
        long[] hold = new long[W];
        for (int j = 0; j < W; j++) hold[j] = NEG;

        Ds.Bits sold = new Ds.Bits((long) n * W);     // sold[i][j]  : NOSTOCK[i][j] came from selling on day i
        Ds.Bits bought = new Ds.Bits((long) n * W);   // bought[i][j]: HOLD[i][j] came from buying on day i

        for (int t = 1; t <= n; t++) {
            int i = t - 1;
            long pi = p[i];
            long[] prev = ns[(t - 1) % L];
            long[] cur = ns[t % L];
            long[] src = ns[Math.max(t - 1 - c, 0) % L];   // NOSTOCK at day i-c-1 (clamped to the start state)
            long base = (long) i * W;
            for (int j = 0; j < W; j++) {
                long h = hold[j];                          // HOLD[i-1][j]
                long a = prev[j];
                if (h > NEG / 2) {
                    long s = h + pi - fee;
                    if (s > a) { a = s; sold.set(base + j); }
                }
                cur[j] = a;
                long hn = h;
                if (j > 0) {
                    long sv = src[j - 1];
                    if (sv > NEG / 2) {
                        long b = sv - pi;
                        if (b > hn) { hn = b; bought.set(base + j); }
                    }
                }
                hold[j] = hn;
            }
        }

        long[] fin = ns[n % L];
        int bj = 0;
        for (int j = 1; j < W; j++) if (fin[j] > fin[bj]) bj = j;   // strict: fewest trades on ties
        long best = fin[bj];

        // ---- backtrack through the decision bits
        Ds.IntList buys = new Ds.IntList(), sells = new Ds.IntList();
        int j = bj, i = n - 1;
        boolean holding = false;
        while (i >= 0) {
            long idx = (long) i * W + j;
            if (!holding) {
                if (sold.get(idx)) { sells.add(i); holding = true; }
                i--;
            } else {
                if (bought.get(idx)) { buys.add(i); j--; holding = false; i = i - c - 1; }
                else i--;
            }
        }
        return new Result(best, ledger(p, buys, sells, fee));
    }

    // ------------------------------------------------------------------ k not binding : O(n)
    private static Result solveUnlimited(long[] p, long fee, int c) {
        int n = p.length;
        long[] ns = new long[n + 1], hold = new long[n + 1];
        boolean[] sold = new boolean[n], bought = new boolean[n];
        ns[0] = 0; hold[0] = NEG;
        for (int t = 1; t <= n; t++) {
            int i = t - 1;
            long a = ns[t - 1];
            if (hold[t - 1] > NEG / 2 && hold[t - 1] + p[i] - fee > a) { a = hold[t - 1] + p[i] - fee; sold[i] = true; }
            ns[t] = a;
            long h = hold[t - 1];
            long b = ns[Math.max(t - 1 - c, 0)] - p[i];
            if (b > h) { h = b; bought[i] = true; }
            hold[t] = h;
        }
        Ds.IntList buys = new Ds.IntList(), sells = new Ds.IntList();
        boolean holding = false;
        int i = n - 1;
        while (i >= 0) {
            if (!holding) { if (sold[i]) { sells.add(i); holding = true; } i--; }
            else { if (bought[i]) { buys.add(i); holding = false; i = i - c - 1; } else i--; }
        }
        return new Result(ns[n], ledger(p, buys, sells, fee));
    }

    private static Trade[] ledger(long[] p, Ds.IntList buys, Ds.IntList sells, long fee) {
        int m = buys.size();                       // collected newest-first -> reverse into chronological order
        Trade[] out = new Trade[m];
        for (int x = 0; x < m; x++) {
            int b = buys.get(m - 1 - x), s = sells.get(m - 1 - x);
            out[x] = new Trade(b, s, p[b], p[s], fee);
        }
        return out;
    }

    /** Independent legality check used by the tests: ordering, cooldown, trade cap and profit total. */
    public static boolean validate(long[] p, Result r, int k, long fee, int c) {
        if (p == null || r == null || r.trades == null || k < 0 || fee < 0 || c < 0 || r.trades.length > k) return false;
        long sum = 0;
        int lastSell = -1 - c;
        for (Trade t : r.trades) {
            if (t == null || t.buyDay < 0 || t.sellDay >= p.length) return false;
            if (t.buyDay < lastSell + c + 1) return false;
            if (t.sellDay <= t.buyDay) return false;
            if (t.buyPrice != p[t.buyDay] || t.sellPrice != p[t.sellDay]) return false;
            if (t.profit != t.sellPrice - t.buyPrice - fee) return false;
            sum += t.profit;
            lastSell = t.sellDay;
        }
        return sum == r.profit;
    }

    /** Exhaustive reference (exponential), intended for at most about 14 observations. */
    public static long bruteForce(long[] p, int k, long fee, int c) {
        return dfs(p, 0, false, -1 - c, k, fee, c);
    }
    private static long dfs(long[] p, int i, boolean holding, int lastSell, int left, long fee, int c) {
        if (i == p.length) return holding ? NEG : 0;
        long best = dfs(p, i + 1, holding, lastSell, left, fee, c);
        if (!holding && left > 0 && i >= lastSell + c + 1) {
            long v = dfs(p, i + 1, true, lastSell, left - 1, fee, c);
            if (v > NEG / 2) best = Math.max(best, v - p[i]);
        }
        if (holding) {
            long v = dfs(p, i + 1, false, i, left, fee, c);
            if (v > NEG / 2) best = Math.max(best, v + p[i] - fee);
        }
        return best;
    }
}
