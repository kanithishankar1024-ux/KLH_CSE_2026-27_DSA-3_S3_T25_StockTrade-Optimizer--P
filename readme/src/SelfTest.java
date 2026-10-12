package tradeoptima;

/** Randomized cross-checks of every module against a slow-but-obviously-correct reference. */
public final class SelfTest {
    private static int failures;

    private static void check(boolean ok, String msg) {
        if (!ok) { failures++; System.out.println("  FAIL: " + msg); }
    }

    public static boolean runAll() {
        failures = 0;
        Ds.Rng r = new Ds.Rng(2026);
        testOptimizer(r); testStrings(r); testRouter(r); testPortfolio(r); testRisk(r);
        System.out.println(failures == 0 ? "ALL SELF-TESTS PASSED" : failures + " FAILURE(S)");
        return failures == 0;
    }

    /** Independent deterministic entry point used by the separate test runner. */
    public static boolean runModule(String module) {
        failures = 0;
        Ds.Rng r = new Ds.Rng(2026);
        if (module.equals("optimizer")) testOptimizer(r);
        else if (module.equals("strings")) testStrings(r);
        else if (module.equals("router")) testRouter(r);
        else if (module.equals("portfolio")) testPortfolio(r);
        else if (module.equals("risk")) testRisk(r);
        else throw new IllegalArgumentException("Unknown test module: " + module);
        return failures == 0;
    }

    private static void testOptimizer(Ds.Rng r) {
        int cases = 3000;
        for (int t = 0; t < cases; t++) {
            int n = 1 + r.nextInt(12);
            long[] p = new long[n];
            for (int i = 0; i < n; i++) p[i] = 1 + r.nextInt(30);
            int k = r.nextInt(7), c = r.nextInt(4);
            long fee = r.nextInt(6);
            TradeOptimizer.Result res = TradeOptimizer.solve(p, k, fee, c);
            long ref = TradeOptimizer.bruteForce(p, k, fee, c);
            check(res.profit == ref, "DP " + res.profit + " != brute " + ref + " (n=" + n + ",k=" + k + ",c=" + c + ",fee=" + fee + ")");
            check(TradeOptimizer.validate(p, res, k, fee, c), "illegal ledger (n=" + n + ",k=" + k + ",c=" + c + ")");
        }
        System.out.println("[optimizer] " + cases + " random cases vs brute force done");
    }

    private static void testStrings(Ds.Rng r) {
        for (int t = 0; t < 2000; t++) {
            StringBuilder tx = new StringBuilder(), pt = new StringBuilder();
            int n = r.nextInt(40), m = 1 + r.nextInt(4);
            for (int i = 0; i < n; i++) tx.append((char) ('a' + r.nextInt(2)));
            for (int i = 0; i < m; i++) pt.append((char) ('a' + r.nextInt(2)));
            String text = tx.toString(), pat = pt.toString();
            Ds.IntList naive = new Ds.IntList();
            for (int i = 0; i + m <= n; i++) if (text.startsWith(pat, i)) naive.add(i);
            int[] want = naive.toArray();
            check(same(want, StringAlgorithms.kmpSearch(text, pat)), "KMP mismatch " + text + " / " + pat);
            check(same(want, StringAlgorithms.zSearch(text, pat)), "Z mismatch " + text + " / " + pat);
        }
        // Aho-Corasick vs naive occurrence count over several patterns
        for (int t = 0; t < 500; t++) {
            int np = 1 + r.nextInt(4);
            String[] pats = new String[np];
            for (int i = 0; i < np; i++) {
                StringBuilder b = new StringBuilder();
                int len = 1 + r.nextInt(3);
                for (int x = 0; x < len; x++) b.append((char) ('a' + r.nextInt(2)));
                pats[i] = b.toString();
            }
            for (int i = 0; i < np; i++)                       // make patterns distinct (duplicates: first id wins)
                for (int j = 0; j < i; j++) if (pats[i].equals(pats[j])) pats[i] = pats[i] + "b" + i;
            StringBuilder tx = new StringBuilder();
            for (int i = 0; i < 30; i++) tx.append((char) ('a' + r.nextInt(2)));
            String text = tx.toString();
            int want = 0;
            for (String p : pats) for (int i = 0; i + p.length() <= text.length(); i++) if (text.startsWith(p, i)) want++;
            check(new AhoCorasick(pats).search(text).length == want, "Aho-Corasick count mismatch");
        }
        check(StringAlgorithms.editDistance("kitten", "sitting") == 3, "edit distance");
        System.out.println("[strings] KMP / Z / Aho-Corasick / edit distance cross-checked");
    }

    private static boolean same(int[] a, int[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static void testRouter(Ds.Rng r) {
        // single order, all venues allowed: min-cost flow must equal sweeping the cheapest levels
        for (int t = 0; t < 300; t++) {
            int V = 1 + r.nextInt(3);
            OrderRouter.Venue[] vs = new OrderRouter.Venue[V];
            int L = 0;
            for (int v = 0; v < V; v++) {
                int lv = 1 + r.nextInt(3);
                long[] pr = new long[lv], sz = new long[lv];
                for (int i = 0; i < lv; i++) { pr[i] = 100 + r.nextInt(50); sz[i] = 1 + r.nextInt(20); }
                vs[v] = new OrderRouter.Venue("V" + v, r.nextInt(3), pr, sz);
                L += lv;
            }
            long[] unit = new long[L], size = new long[L];
            int x = 0;
            for (OrderRouter.Venue v : vs) for (int i = 0; i < v.price.length; i++) { unit[x] = v.price[i] + v.feePerShare; size[x++] = v.size[i]; }
            long qty = 1 + r.nextInt(60);
            long left = qty, cost = 0;
            while (left > 0) {
                int b = -1;
                for (int i = 0; i < L; i++) if (size[i] > 0 && (b == -1 || unit[i] < unit[b])) b = i;
                if (b == -1) break;
                long q = Math.min(left, size[b]);
                cost += q * unit[b]; size[b] -= q; left -= q;
            }
            OrderRouter.Plan plan = OrderRouter.route(vs, new OrderRouter.Order[] {new OrderRouter.Order(1, qty, -1L)});
            check(plan.totalCost == cost && plan.filledQty == qty - left, "router cost " + plan.totalCost + " vs sweep " + cost);
        }
        // matching vs brute force
        for (int t = 0; t < 300; t++) {
            int O = 1 + r.nextInt(5), V = 1 + r.nextInt(5);
            boolean[][] adj = new boolean[O][V];
            HopcroftKarp hk = new HopcroftKarp(O, V, O * V);
            for (int o = 0; o < O; o++) for (int v = 0; v < V; v++) if (r.nextInt(3) == 0) { adj[o][v] = true; hk.addEdge(o, v); }
            check(hk.run() == bruteMatch(adj, 0, new boolean[V]), "matching size");
        }
        System.out.println("[router] min-cost flow vs greedy sweep, Hopcroft-Karp vs brute force done");
    }

    private static int bruteMatch(boolean[][] adj, int o, boolean[] used) {
        if (o == adj.length) return 0;
        int best = bruteMatch(adj, o + 1, used);
        for (int v = 0; v < used.length; v++)
            if (adj[o][v] && !used[v]) { used[v] = true; best = Math.max(best, 1 + bruteMatch(adj, o + 1, used)); used[v] = false; }
        return best;
    }

    private static void testPortfolio(Ds.Rng r) {
        double eps = 0.1;
        for (int t = 0; t < 500; t++) {
            int n = 1 + r.nextInt(12);
            PortfolioSelector.Asset[] a = new PortfolioSelector.Asset[n];
            for (int i = 0; i < n; i++) a[i] = new PortfolioSelector.Asset("A" + i, 1 + r.nextInt(40), 1 + r.nextInt(500));
            long budget = 10 + r.nextInt(120);
            long opt = PortfolioSelector.exact(a, budget).value;
            PortfolioSelector.Selection f = PortfolioSelector.fptas(a, budget, eps);
            PortfolioSelector.Selection g = PortfolioSelector.greedy2Approx(a, budget);
            check(f.cost <= budget && g.cost <= budget, "budget violated");
            check(f.value <= opt && f.value >= (1 - eps) * opt - 1e-9, "FPTAS ratio: " + f.value + " vs OPT " + opt);
            check(g.value <= opt && 2 * g.value >= opt, "greedy ratio: " + g.value + " vs OPT " + opt);
        }
        System.out.println("[portfolio] FPTAS >= (1-eps)*OPT and greedy >= OPT/2 verified against exact DP");
    }

    private static void testRisk(Ds.Rng r) {
        for (int t = 0; t < 200; t++) {
            int n = 1 + r.nextInt(60);
            double[] a = new double[n], b = new double[n];
            for (int i = 0; i < n; i++) a[i] = b[i] = r.nextInt(10);
            int k = r.nextInt(n);
            for (int i = 1; i < n; i++) { double v = b[i]; int j = i - 1; while (j >= 0 && b[j] > v) { b[j + 1] = b[j]; j--; } b[j + 1] = v; }
            check(RiskEngine.quickselect(a, k, r) == b[k], "quickselect");
        }
        // reservoir: each of 10 items should be kept ~ cap/10 of the time
        int[] hits = new int[10];
        for (int t = 0; t < 20000; t++) {
            RiskEngine.Reservoir rs = new RiskEngine.Reservoir(3, r);
            for (int i = 0; i < 10; i++) rs.add(i);
            for (double x : rs.sample()) hits[(int) x]++;
        }
        for (int h : hits) check(Math.abs(h - 6000) < 400, "reservoir not uniform: " + h);
        // VaR(95%) of N(0, 1%) 1-day: loss ~ V*(1-exp(-1.645*0.01)) ~ 1.63% of V
        double[] ret = new double[2000];
        for (int i = 0; i < ret.length; i++) ret[i] = 0.01 * r.nextGaussian();
        RiskEngine.Risk k = RiskEngine.parametricVaR(ret, 1_000_000, 1, 400_000, 0.95, r);
        check(Math.abs(k.var - 16300) < 1500, "parametric VaR out of range: " + k.var);
        check(k.cvar >= k.var, "CVaR must be >= VaR");
        System.out.println("[risk] quickselect, reservoir uniformity and VaR sanity checked");
    }
}
