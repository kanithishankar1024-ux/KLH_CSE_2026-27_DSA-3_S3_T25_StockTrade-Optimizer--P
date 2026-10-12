package tradeoptima;

/**
 * MODULE 5: budget-constrained portfolio selection = 0/1 knapsack (NP-hard).
 *  - greedy2Approx : value/cost greedy vs best single asset, guaranteed >= OPT/2, O(n log n)
 *  - fptas         : profit-scaling FPTAS, guaranteed >= (1-eps)*OPT, O(n^3/eps) time
 *  - exact         : pseudo-polynomial DP on budget, used as the test oracle
 */
public final class PortfolioSelector {
    private PortfolioSelector() {}

    public static final class Asset {
        public final String name; public final long cost, value;   // cost in cents, value = expected return in cents
        public Asset(String name, long cost, long value) { this.name = name; this.cost = cost; this.value = value; }
    }

    public static final class Selection {
        public final int[] chosen; public final long cost, value;
        Selection(int[] c, long cost, long value) { chosen = c; this.cost = cost; this.value = value; }
    }

    private static Selection make(Asset[] a, boolean[] pick) {
        Ds.IntList l = new Ds.IntList();
        long c = 0, v = 0;
        for (int i = 0; i < a.length; i++) if (pick[i]) { l.add(i); c += a[i].cost; v += a[i].value; }
        return new Selection(l.toArray(), c, v);
    }

    // ---------------------------------------------------------------- 2-approximation
    public static Selection greedy2Approx(Asset[] a, long budget) {
        int n = a.length;
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        double[] ratio = new double[n];
        for (int i = 0; i < n; i++) ratio[i] = a[i].cost == 0 ? Double.MAX_VALUE : (double) a[i].value / a[i].cost;
        mergeSortDesc(idx, new int[n], ratio, 0, n);

        boolean[] pick = new boolean[n];
        long spent = 0;
        for (int k = 0; k < n; k++) {
            int i = idx[k];
            if (a[i].cost <= budget - spent) { pick[i] = true; spent += a[i].cost; }
        }
        Selection greedy = make(a, pick);

        int bestSingle = -1;
        for (int i = 0; i < n; i++)
            if (a[i].cost <= budget && (bestSingle == -1 || a[i].value > a[bestSingle].value)) bestSingle = i;
        if (bestSingle != -1 && a[bestSingle].value > greedy.value) {
            boolean[] one = new boolean[n];
            one[bestSingle] = true;
            return make(a, one);
        }
        return greedy;
    }

    private static void mergeSortDesc(int[] a, int[] tmp, double[] key, int lo, int hi) {
        if (hi - lo < 2) return;
        int mid = (lo + hi) >>> 1;
        mergeSortDesc(a, tmp, key, lo, mid);
        mergeSortDesc(a, tmp, key, mid, hi);
        int i = lo, j = mid, k = lo;
        while (i < mid && j < hi) tmp[k++] = key[a[i]] >= key[a[j]] ? a[i++] : a[j++];
        while (i < mid) tmp[k++] = a[i++];
        while (j < hi) tmp[k++] = a[j++];
        System.arraycopy(tmp, lo, a, lo, hi - lo);
    }

    // ---------------------------------------------------------------- FPTAS
    /**
     * Scale values down: K = eps*vmax/n, v'_i = floor(v_i / K). Rounding loses less than K per item,
     * at most eps*OPT in total. Then solve over scaled value: dp[v] = min cost reaching value v.
     */
    public static Selection fptas(Asset[] a, long budget, double eps) {
        if (!(eps > 0 && eps < 1)) throw new IllegalArgumentException("eps must be in (0,1)");
        int n = a.length;
        long vmax = 0;
        for (Asset s : a) if (s.cost <= budget) vmax = Math.max(vmax, s.value);
        if (vmax <= 0) return new Selection(new int[0], 0, 0);

        double K = eps * vmax / n;
        long[] sv = new long[n];
        long S = 0;
        for (int i = 0; i < n; i++) {
            sv[i] = (a[i].cost <= budget && a[i].value > 0) ? (long) Math.floor(a[i].value / K) : 0;
            S += sv[i];
        }
        long W = S + 1;
        Ds.Bits take = new Ds.Bits((long) n * W);
        long[] dp = new long[(int) W];
        for (int v = 1; v < W; v++) dp[v] = MinCostMaxFlow.INF;

        long top = 0;
        for (int i = 0; i < n; i++) {
            if (sv[i] == 0) continue;
            top += sv[i];
            for (long v = top; v >= sv[i]; v--) {
                long prev = dp[(int) (v - sv[i])];
                if (prev >= MinCostMaxFlow.INF) continue;
                long cand = prev + a[i].cost;
                if (cand < dp[(int) v]) { dp[(int) v] = cand; take.set((long) i * W + v); }
            }
        }
        int bestV = 0;
        for (int v = (int) S; v >= 0; v--) if (dp[v] <= budget) { bestV = v; break; }

        boolean[] pick = new boolean[n];
        long v = bestV, spent = 0;
        for (int i = n - 1; i >= 0; i--)
            if (sv[i] > 0 && take.get((long) i * W + v)) { pick[i] = true; v -= sv[i]; spent += a[i].cost; }

        // free bonus: top up with any untouched asset that still fits (only increases value)
        for (int i = 0; i < n; i++)
            if (!pick[i] && a[i].value > 0 && a[i].cost <= budget - spent) { pick[i] = true; spent += a[i].cost; }
        return make(a, pick);
    }

    // ---------------------------------------------------------------- exact oracle
    public static Selection exact(Asset[] a, long budget) {
        int n = a.length;
        int B = (int) budget;
        long[] dp = new long[B + 1];
        Ds.Bits take = new Ds.Bits((long) n * (B + 1));
        for (int i = 0; i < n; i++) {
            int c = (int) a[i].cost;
            if (c > B) continue;
            for (int w = B; w >= c; w--) {
                long cand = dp[w - c] + a[i].value;
                if (cand > dp[w]) { dp[w] = cand; take.set((long) i * (B + 1) + w); }
            }
        }
        boolean[] pick = new boolean[n];
        int w = B;
        for (int i = n - 1; i >= 0; i--)
            if (take.get((long) i * (B + 1) + w)) { pick[i] = true; w -= (int) a[i].cost; }
        return make(a, pick);
    }
}
