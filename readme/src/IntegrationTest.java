package tradeoptima;

/** Additional boundary and replay checks, runnable without an external test library. */
public final class IntegrationTest {
    private IntegrationTest() {}
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        boolean failed = false;
        try { action.run(); } catch (IllegalArgumentException ex) { failed = true; }
        check(failed, "Expected invalid input to be rejected");
    }
    public static boolean runAll() {
        try {
            csv(); replay(); optimizerEdges(); routingEdges(); riskEdges();
            System.out.println("CSV, replay isolation, optimizer boundaries and venue restrictions PASSED");
            return true;
        } catch (AssertionError ex) { System.out.println("FAIL: " + ex.getMessage()); return false; }
    }
    public static void csv() {
        String[][] rows = CsvLoader.parse("\ufeffDate,Headline\r\n2024-02-29,\"Apple, says \"\"hello\"\"\nagain\"\r\n");
        check(rows.length == 2 && rows[1][1].equals("Apple, says \"hello\"\nagain"), "Quoted CSV");
        check(CsvLoader.cents("10.999") == 1100 && CsvLoader.cents("1.005") == 101
                && CsvLoader.cents("1.004") == 100 && CsvLoader.cents("0.1") == 10, "Decimal rounding");
        CsvLoader.Prices p = CsvLoader.prices("Date,Close,Adj Close\n2024-02-28,1.234,8\n2024-02-29,2.00,9", "Close");
        check(p.close[0] == 123 && p.close[1] == 200, "Select Close explicitly");
        rejects(() -> CsvLoader.parse("a,b\n1"));
        rejects(() -> CsvLoader.parse("a,a\n1,2"));
        rejects(() -> CsvLoader.parse("a\n\"open"));
        rejects(() -> CsvLoader.parse("a\n\"closed\"x"));
        rejects(() -> CsvLoader.cents("NaN")); rejects(() -> CsvLoader.cents("-1"));
        rejects(() -> CsvLoader.cents("9223372036854775807"));
        rejects(() -> CsvLoader.prices("Date,Close\n2024-01-01,0", "Close"));
        rejects(() -> CsvLoader.prices("Date,Close\n2024-01-01,null", "Close"));
        rejects(() -> CsvLoader.prices("Date,Close\n2024-01-02,1\n2024-01-01,2", "Close"));
        rejects(() -> CsvLoader.prices("Date,Close\n2024-01-01,1\n2024-01-01,2", "Close"));
        rejects(() -> CsvLoader.date("2023-02-29")); rejects(() -> CsvLoader.date("2024-04-31"));
        check(CsvLoader.date("2000-02-29").equals("2000-02-29"), "Leap century");
    }

    private static MarketData fixture(boolean changedFuture) {
        return new MarketData("Ticker,Name\nA,Alpha\nB,Beta",
            "Date,A,B\n2024-01-01,10,20\n2024-01-02,12,19\n2024-01-03,11,23\n2024-01-04,"
                + (changedFuture ? "900,1" : "13,24"),
            "Date,Headline\n2024-01-01,Alpha\n2024-01-04," + (changedFuture ? "Beta Beta Beta" : "Alpha"),
            "Ticker,Venue,OffsetCents,FeeCents,Size\nA,SIM-A,2,1,5\nA,SIM-B,1,0,5\nB,SIM-A,2,1,5\nB,SIM-B,1,0,5");
    }

    public static void replay() {
        Pipeline a = new Pipeline(fixture(false), 4000, 3, 2, 1, 1, 1000);
        Pipeline b = new Pipeline(fixture(true), 4000, 3, 2, 1, 1, 1000);
        for (int day = 0; day < 3; day++) {
            Pipeline.Snapshot x = a.next(), y = b.next();
            check(x.returnsSeen == day && x.returnsSeen == y.returnsSeen, "Reservoir sees only observed returns");
            check(x.spent <= 4000 && x.selection.cost <= 4000, "Paper budget");
            check(x.spent == y.spent && x.value == y.value && x.selection.value == y.selection.value, "Future changed allocation");
            for (int i = 0; i < x.analysis.length; i++) {
                check(x.analysis[i].profit == y.analysis[i].profit && x.mentions[i] == y.mentions[i], "Future leaked into analysis");
                for (TradeOptimizer.Trade t : x.analysis[i].trades) check(t.sellDay <= day, "Future trade");
            }
            check((x.risk == null) == (y.risk == null), "Risk availability");
            if (x.risk != null) check(x.risk.var == y.risk.var && x.risk.cvar == y.risk.cvar, "Future leaked into risk");
        }
        Pipeline.Snapshot finalDay = a.next();
        check(finalDay.newsCount == 1 && finalDay.mentions[0] == 1, "News processed once on its day");
        boolean finished = false;
        try { a.next(); } catch (IllegalStateException ex) { finished = true; }
        check(finished, "Replay end");
        Pipeline empty = new Pipeline(fixture(false), 0, 3, 2, 1, 1, 100);
        for (int i = 0; i < 4; i++) check(empty.next().selection.chosen.length == 0, "Zero budget");
    }

    public static void optimizerEdges() {
        check(TradeOptimizer.solve(new long[0], 3, 0, 0).profit == 0, "Empty history");
        long[] known = {100, 200};
        TradeOptimizer.Trade wrongFee = new TradeOptimizer.Trade(0, 1, 100, 200, 0);
        check(!TradeOptimizer.validate(known, new TradeOptimizer.Result(100, new TradeOptimizer.Trade[] {wrongFee}), 1, 50, 0), "Validator must check charged fee");
        Ds.Rng random = new Ds.Rng(92);
        for (int run = 0; run < 2000; run++) {
            long[] p = new long[2 + random.nextInt(7)];
            for (int i = 0; i < p.length; i++) p[i] = 1 + random.nextInt(5);
            int c = random.nextInt(3), k = 1 + random.nextInt(6); long fee = random.nextInt(3);
            long[] ref = reference(p, 0, k, fee, c);
            TradeOptimizer.Result actual = TradeOptimizer.solve(p, k, fee, c);
            check(actual.profit == ref[0] && actual.trades.length == ref[1], "Profit/fewest-trades tie differs from exhaustive schedule");
            check(TradeOptimizer.validate(p, actual, k, fee, c), "Ledger invalid");
        }
    }

    // Enumerate complete buy/sell intervals, independent of the production state machine.
    private static long[] reference(long[] p, int start, int left, long fee, int c) {
        long best = 0, count = 0;
        if (left == 0) return new long[] {best, count};
        for (int buy = start; buy < p.length; buy++) for (int sell = buy + 1; sell < p.length; sell++) {
            long[] rest = reference(p, sell + c + 1, left - 1, fee, c);
            long profit = p[sell] - p[buy] - fee + rest[0], trades = 1 + rest[1];
            if (profit > best || (profit == best && trades < count)) { best = profit; count = trades; }
        }
        return new long[] {best, count};
    }

    public static void routingEdges() {
        OrderRouter.Venue[] v = {
            new OrderRouter.Venue("cheap", 0, new long[] {100}, new long[] {1}),
            new OrderRouter.Venue("other", 0, new long[] {110}, new long[] {1})};
        OrderRouter.Order[] o = {new OrderRouter.Order(10, 1, 3), new OrderRouter.Order(20, 1, 1)};
        OrderRouter.Plan p = OrderRouter.route(v, o);
        check(p.filledQty == 2 && p.totalCost == 210, "Residual flow must reroute the flexible order");
        for (OrderRouter.Fill f : p.fills) if (f.order == 20) check(f.venue == 0, "Allowed mask");
        p = OrderRouter.route(v, new OrderRouter.Order[] {new OrderRouter.Order(1, 5, 1)});
        check(p.filledQty == 1 && p.totalCost == 100, "Partial liquidity");
        int[] block = OrderRouter.matchBlocks(v, new OrderRouter.Order[] {new OrderRouter.Order(1, 2, 3)});
        check(block[0] == -1, "An indivisible block cannot span venues");
    }

    public static void riskEdges() {
        check(RiskEngine.logReturns(new long[0]).length == 0, "Empty return series");
        rejects(() -> RiskEngine.logReturns(new long[] {0, 10}));
        rejects(() -> RiskEngine.quickselect(new double[0], 0, new Ds.Rng(1)));
        rejects(() -> RiskEngine.parametricVaR(new double[] {0.1}, 1000, 1, 100, 0.95, new Ds.Rng(1)));
        rejects(() -> RiskEngine.bootstrapVaR(new double[] {0.1, 0.2}, 1000, 1, 100, 1, new Ds.Rng(1)));
        // For normal log returns with mean zero and sample variance 0.02, E[exp(X)] = exp(0.01).
        RiskEngine.Risk r = RiskEngine.parametricVaR(new double[] {-0.1, 0.1}, 1000, 1, 200_000, 0.95, new Ds.Rng(17));
        double expectedMeanLoss = 1000 * (1 - Math.exp(0.01));
        check(Math.abs(r.meanLoss - expectedMeanLoss) < 2.0, "Log-return drift should not subtract variance twice");
    }
}
