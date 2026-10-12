package tradeoptima;

/** One independent paper-allocation scenario per observed day. No persistent trading account. */
public final class Pipeline {
    private final MarketData data;
    private final SearchEngine search;
    private final RiskEngine.Reservoir days;
    private final long budget, fee;
    private final int window, k, cooldown, paths;
    private int nextDay, nextNews;

    public static final class Snapshot {
        public final int day, newsCount;
        public final int[] mentions;
        public final TradeOptimizer.Result[] analysis;
        public final PortfolioSelector.Selection selection;
        public final OrderRouter.Plan[] routes;
        public final RiskEngine.Risk risk;
        public final long spent, value, returnsSeen;
        Snapshot(int d, int nc, int[] m, TradeOptimizer.Result[] a, PortfolioSelector.Selection s,
                 OrderRouter.Plan[] r, RiskEngine.Risk risk, long spent, long value, long seen) {
            day = d; newsCount = nc; mentions = m; analysis = a; selection = s; routes = r;
            this.risk = risk; this.spent = spent; this.value = value; returnsSeen = seen;
        }
    }

    public Pipeline(MarketData data, long budget, int window, int k, long fee, int cooldown, int paths) {
        if (budget < 0 || budget > 1_000_000_000_000L || window < 2 || window > 1000 || k < 0 || k > 1000
                || fee < 0 || fee > 1_000_000_000L || cooldown < 0 || cooldown > 1000 || paths < 100 || paths > 1_000_000)
            throw new IllegalArgumentException("Invalid pipeline settings");
        this.data = data; this.budget = budget; this.window = window; this.k = k;
        this.fee = fee; this.cooldown = cooldown; this.paths = paths;
        search = new SearchEngine(data.tickers, data.names);
        days = new RiskEngine.Reservoir(500, new Ds.Rng(7));
    }

    /** Advance exactly once. Every array used for analysis ends at this observed day. */
    public Snapshot next() {
        if (nextDay == data.dates.length) throw new IllegalStateException("Replay finished");
        int day = nextDay++, n = data.tickers.length, newsCount = 0;
        int[] mentions = new int[n];
        while (nextNews < data.headlines.length && data.newsDates[nextNews].compareTo(data.dates[day]) <= 0) {
            int[] found = search.mentions(data.headlines[nextNews++]); newsCount++;
            for (int a = 0; a < n; a++) mentions[a] += found[a];
        }
        if (day > 0) days.add(day);
        TradeOptimizer.Result[] analysis = new TradeOptimizer.Result[n];
        PortfolioSelector.Asset[] assets = new PortfolioSelector.Asset[n];
        int begin = Math.max(0, day - window + 1);
        for (int a = 0; a < n; a++) {
            long[] history = new long[day - begin + 1];
            System.arraycopy(data.close[a], begin, history, 0, history.length);
            analysis[a] = TradeOptimizer.solve(history, k, fee, cooldown);
            long reservedCost = 0;
            for (OrderRouter.Venue v : data.venues(a, day)) reservedCost = Math.max(reservedCost, v.price[0] + v.feePerShare);
            // Teaching utility only: past-window oracle gain plus one point per current news mention.
            assets[a] = new PortfolioSelector.Asset(data.tickers[a], reservedCost, analysis[a].profit + mentions[a]);
        }
        PortfolioSelector.Selection selected = PortfolioSelector.fptas(assets, budget, 0.1);
        OrderRouter.Plan[] routes = new OrderRouter.Plan[selected.chosen.length];
        long spent = 0, value = 0;
        for (int i = 0; i < selected.chosen.length; i++) {
            int a = selected.chosen[i];
            routes[i] = OrderRouter.route(data.venues(a, day), new OrderRouter.Order[] {new OrderRouter.Order(a + 1, 1, -1L)});
            if (routes[i].filledQty != 1) throw new IllegalStateException("Fixture did not fill one share");
            spent += routes[i].totalCost; value += data.close[a][day];
        }
        if (spent > budget) throw new IllegalStateException("Allocation exceeded budget");
        RiskEngine.Risk risk = null;
        double[] sampledDays = days.sample();
        if (selected.chosen.length > 0 && sampledDays.length >= 2) {
            double[] returns = new double[sampledDays.length];
            for (int i = 0; i < returns.length; i++) {
                int d = (int) sampledDays[i];
                long previous = 0, current = 0;
                for (int a : selected.chosen) { previous += data.close[a][d - 1]; current += data.close[a][d]; }
                returns[i] = Math.log((double) current / previous);
            }
            // Joint dates preserve cross-asset co-movement for this fixed one-share basket.
            risk = RiskEngine.bootstrapVaR(returns, value / 100.0, 1, paths, 0.95, new Ds.Rng(1000L + day));
        }
        return new Snapshot(day, newsCount, mentions, analysis, selected, routes, risk, spent, value, days.seen());
    }
}
