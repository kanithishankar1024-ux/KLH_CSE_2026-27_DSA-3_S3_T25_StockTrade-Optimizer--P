package tradeoptima;

/** Reproducible slide-target benchmark, separate from historical inputs. */
public final class Benchmark {
    private Benchmark() {}
    public static double run() {
        long[] prices = new long[100_000]; prices[0] = 100_000;
        Ds.Rng random = new Ds.Rng(7);
        for (int i = 1; i < prices.length; i++) prices[i] = Math.max(100, prices[i - 1] + random.range(-150, 150));
        long start = System.nanoTime();
        TradeOptimizer.Result result = TradeOptimizer.solve(prices, 1000, 50, 1);
        double ms = (System.nanoTime() - start) / 1e6;
        if (!TradeOptimizer.validate(prices, result, 1000, 50, 1)) throw new AssertionError("Benchmark ledger invalid");
        System.out.println("N=100000, k=1000, fee=0.50, cooldown=1: " + Ds.fix(ms, 1)
                + " ms; trades=" + result.trades.length + "; legal ledger=true; target under 2000 ms=" + (ms < 2000));
        return ms;
    }
}
