package tradeoptima;

/**
 * MODULE 6: randomized risk engine.
 *  - Reservoir (Algorithm R): uniform fixed-size sample of an unbounded return stream in O(1) memory per item.
 *  - parametricVaR: Monte Carlo geometric-Brownian-motion paths.
 *  - bootstrapVaR : resamples historical returns (no normality assumption).
 *  - quickselect with random pivots finds the VaR quantile in expected O(paths), no full sort.
 */
public final class RiskEngine {
    private RiskEngine() {}

    public static final class Reservoir {
        private final double[] buf; private long seen; private final Ds.Rng rng;
        public Reservoir(int capacity, Ds.Rng rng) { buf = new double[capacity]; this.rng = rng; }
        public void add(double x) {
            if (seen < buf.length) buf[(int) seen] = x;
            else {
                long j = (rng.nextLong() >>> 1) % (seen + 1);          // keep with probability cap/(seen+1)
                if (j < buf.length) buf[(int) j] = x;
            }
            seen++;
        }
        public double[] sample() {
            int m = (int) Math.min(seen, buf.length);
            double[] out = new double[m];
            System.arraycopy(buf, 0, out, 0, m);
            return out;
        }
        public long seen() { return seen; }
    }

    public static final class Risk {
        public final double var, cvar, meanLoss; public final int paths; public final double confidence;
        Risk(double v, double c, double m, int p, double conf) { var = v; cvar = c; meanLoss = m; paths = p; confidence = conf; }
    }

    public static double[] logReturns(long[] prices) {
        for (long p : prices) if (p <= 0) throw new IllegalArgumentException("Prices must be positive");
        if (prices.length < 2) return new double[0];
        double[] r = new double[prices.length - 1];
        for (int i = 1; i < prices.length; i++) r[i - 1] = Math.log((double) prices[i] / prices[i - 1]);
        return r;
    }

    public static double mean(double[] x) { double s = 0; for (double v : x) s += v; return s / x.length; }
    public static double stdev(double[] x) {
        double m = mean(x), s = 0;
        for (double v : x) s += (v - m) * (v - m);
        return Math.sqrt(s / (x.length - 1));
    }

    /** Positive values represent losses; negative values represent gains. Input is log returns. */
    public static Risk parametricVaR(double[] returns, double value, int horizon, int paths, double conf, Ds.Rng rng) {
        validateInputs(returns, value, horizon, paths, conf, rng);
        double mu = mean(returns), sigma = stdev(returns);
        double[] loss = new double[paths];
        for (int i = 0; i < paths; i++) {
            // mu already estimates log-return drift; do not subtract half the variance again.
            double lr = mu * horizon + sigma * Math.sqrt(horizon) * rng.nextGaussian();
            loss[i] = value * (1.0 - Math.exp(lr));
        }
        return summarize(loss, conf, rng);
    }

    public static Risk bootstrapVaR(double[] returns, double value, int horizon, int paths, double conf, Ds.Rng rng) {
        validateInputs(returns, value, horizon, paths, conf, rng);
        double[] loss = new double[paths];
        for (int i = 0; i < paths; i++) {
            double lr = 0;
            for (int d = 0; d < horizon; d++) lr += returns[rng.nextInt(returns.length)];
            loss[i] = value * (1.0 - Math.exp(lr));
        }
        return summarize(loss, conf, rng);
    }

    private static void validateInputs(double[] r, double value, int horizon, int paths, double conf, Ds.Rng rng) {
        if (r == null || r.length < 2 || !Double.isFinite(value) || value < 0 || horizon < 1 || paths < 1
                || !(conf > 0 && conf < 1) || rng == null) throw new IllegalArgumentException("Invalid risk inputs");
        for (double x : r) if (!Double.isFinite(x)) throw new IllegalArgumentException("Returns must be finite");
    }

    private static Risk summarize(double[] loss, double conf, Ds.Rng rng) {
        int n = loss.length;
        double mean = mean(loss);
        int k = Math.min(n - 1, Math.max(0, (int) Math.ceil(conf * n) - 1));
        double var = quickselect(loss, k, rng);            // afterwards loss[k+1..] >= loss[k]
        double tail = 0;
        for (int i = k; i < n; i++) tail += loss[i];
        return new Risk(var, tail / (n - k), mean, n, conf);
    }

    /** k-th smallest (0-based), expected O(n), 3-way partition so duplicates are safe. Reorders a. */
    public static double quickselect(double[] a, int k, Ds.Rng rng) {
        if (a == null || k < 0 || k >= a.length || rng == null) throw new IllegalArgumentException("Invalid quickselect inputs");
        int lo = 0, hi = a.length - 1;
        while (lo < hi) {
            double pv = a[lo + rng.nextInt(hi - lo + 1)];
            int lt = lo, i = lo, gt = hi;
            while (i <= gt) {
                if (a[i] < pv) { double t = a[lt]; a[lt++] = a[i]; a[i++] = t; }
                else if (a[i] > pv) { double t = a[gt]; a[gt--] = a[i]; a[i] = t; }
                else i++;
            }
            if (k < lt) hi = lt - 1;
            else if (k > gt) lo = gt + 1;
            else return a[k];
        }
        return a[k];
    }
}
