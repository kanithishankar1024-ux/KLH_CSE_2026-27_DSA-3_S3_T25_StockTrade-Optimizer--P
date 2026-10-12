package tradeoptima;

/** End-to-end demo of the TradeOptima platform. Run: java -Xmx512m -cp out tradeoptima.Main */
public final class LegacyDemo {
    public static void main(String[] args) {
        System.out.println("=== SELF TESTS ===");
        boolean ok = SelfTest.runAll();

        Ds.Rng rng = new Ds.Rng(7);
        System.out.println("\n=== MODULE 3: CONSTRAINED TRADE OPTIMIZER ===");
        long[] small = {310, 290, 305, 340, 330, 360, 355, 320, 345, 380};
        TradeOptimizer.Result sr = TradeOptimizer.solve(small, 2, 5, 1);
        System.out.println("10-day example, k=2, fee=0.05, cooldown=1 -> profit " + Ds.money(sr.profit));
        for (TradeOptimizer.Trade t : sr.trades)
            System.out.println("  BUY  day " + t.buyDay + " @ " + Ds.money(t.buyPrice) + "   SELL day " + t.sellDay
                    + " @ " + Ds.money(t.sellPrice) + "   net " + Ds.money(t.profit));

        int N = 100_000, K = 1000;
        long[] px = new long[N];
        px[0] = 100_000;
        for (int i = 1; i < N; i++) px[i] = Math.max(100, px[i - 1] + rng.range(-150, 150));
        long t0 = System.nanoTime();
        TradeOptimizer.Result big = TradeOptimizer.solve(px, K, 50, 1);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.println("N=" + N + ", k=" + K + ", fee=0.50, cooldown=1 -> profit " + Ds.money(big.profit)
                + ", " + big.trades.length + " trades, " + Ds.fix(ms, 0) + " ms, ledger legal = "
                + TradeOptimizer.validate(px, big, K, 50, 1));

        System.out.println("\n=== MODULE 2: TICKER & NEWS SEARCH ===");
        String[] tk = {"TCS", "INFY", "RELIANCE", "HDFCBANK", "WIPRO"};
        String[] nm = {"Tata Consultancy", "Infosys", "Reliance Industries", "HDFC Bank", "Wipro Limited"};
        SearchEngine se = new SearchEngine(tk, nm);
        String[] feed = {"Infosys and TCS lead IT rally as Wipro lags", "RELIANCE Industries posts record profit",
                         "HDFC Bank, Infosys raise guidance", "tcs wins deal; INFY steady"};
        int[] counts = se.scanFeed(feed);
        for (int i = 0; i < tk.length; i++) System.out.println("  " + tk[i] + " mentioned " + counts[i] + "x");
        System.out.print("  fuzzy(\"infsys\", 2): ");
        for (SearchEngine.Hit h : se.fuzzy("infsys", 2)) System.out.print(h.ticker + "(d=" + h.distance + ") ");
        System.out.println();

        System.out.println("\n=== MODULE 4: CROSS-VENUE ORDER ROUTER ===");
        OrderRouter.Venue[] vs = {
            new OrderRouter.Venue("NSE", 1, new long[] {10000, 10005}, new long[] {300, 500}),
            new OrderRouter.Venue("BSE", 0, new long[] {10003, 10010}, new long[] {200, 400}),
            new OrderRouter.Venue("DARK", 2, new long[] {9998}, new long[] {150})};
        OrderRouter.Order[] os = {new OrderRouter.Order(1, 600, 0b111), new OrderRouter.Order(2, 400, 0b011)};
        OrderRouter.Plan plan = OrderRouter.route(vs, os);
        System.out.println("  filled " + plan.filledQty + " shares, total cost " + Ds.money(plan.totalCost));
        for (OrderRouter.Fill f : plan.fills)
            System.out.println("  order " + f.order + " <- " + f.qty + " @ " + Ds.money(f.unitCost) + " on " + vs[f.venue].name);

        System.out.println("\n=== MODULE 5: PORTFOLIO SELECTOR (budget 5,000.00) ===");
        PortfolioSelector.Asset[] as = {
            new PortfolioSelector.Asset("TCS", 120_000, 9_000), new PortfolioSelector.Asset("INFY", 150_000, 11_500),
            new PortfolioSelector.Asset("RELIANCE", 200_000, 14_000), new PortfolioSelector.Asset("HDFCBANK", 90_000, 6_000),
            new PortfolioSelector.Asset("WIPRO", 60_000, 3_800), new PortfolioSelector.Asset("ITC", 40_000, 2_900)};
        PortfolioSelector.Selection fp = PortfolioSelector.fptas(as, 500_000, 0.05);
        PortfolioSelector.Selection gr = PortfolioSelector.greedy2Approx(as, 500_000);
        System.out.print("  FPTAS (eps=0.05): value " + Ds.money(fp.value) + ", cost " + Ds.money(fp.cost) + " -> ");
        for (int i : fp.chosen) System.out.print(as[i].name + " ");
        System.out.println("\n  Greedy 2-approx : value " + Ds.money(gr.value) + ", cost " + Ds.money(gr.cost));

        System.out.println("\n=== MODULE 6: MONTE CARLO RISK ENGINE ===");
        RiskEngine.Reservoir res = new RiskEngine.Reservoir(5000, rng);
        double[] lr = RiskEngine.logReturns(px);
        for (double x : lr) res.add(x);
        double[] sample = res.sample();
        System.out.println("  streamed " + res.seen() + " returns, kept uniform reservoir of " + sample.length);
        RiskEngine.Risk a = RiskEngine.parametricVaR(sample, 1_000_000, 10, 500_000, 0.99, rng);
        RiskEngine.Risk b = RiskEngine.bootstrapVaR(sample, 1_000_000, 10, 500_000, 0.99, rng);
        System.out.println("  10-day 99% VaR on 1,000,000.00:  parametric " + Ds.fix(a.var, 2) + " (CVaR " + Ds.fix(a.cvar, 2)
                + ")   bootstrap " + Ds.fix(b.var, 2) + " (CVaR " + Ds.fix(b.cvar, 2) + ")");
        if (!ok) System.exit(1);
    }
}
