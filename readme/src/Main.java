package tradeoptima;

/** Command-line entry point. The launcher transports CSV files through standard input. */
public final class Main {
    private Main() {}
    public static void main(String[] args) {
        try {
            String mode = args.length == 0 ? "help" : args[0];
            if (mode.equals("test")) {
                boolean a = SelfTest.runAll(), b = IntegrationTest.runAll();
                if (!a || !b) System.exit(1);
            } else if (mode.equals("benchmark")) {
                if (Benchmark.run() >= 2000) System.exit(1);
            } else if (mode.equals("legacy")) LegacyDemo.main(new String[0]);
            else if (mode.equals("history")) {
                String column = args.length > 1 ? args[1] : "Close";
                int k = args.length > 2 ? Integer.parseInt(args[2]) : 3;
                long fee = args.length > 3 ? CsvLoader.cents(args[3]) : 50;
                int cooldown = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                if (k < 0 || k > 1000 || cooldown < 0 || cooldown > 1000 || fee > 1_000_000_000L)
                    throw new IllegalArgumentException("Invalid optimizer settings");
                CsvLoader.Prices p = CsvLoader.prices(readInput(), column);
                if (p.close.length > 100_000) throw new IllegalArgumentException("At most 100000 observations");
                printLedger(column, p, k, fee, cooldown);
            } else if (mode.equals("demo") || mode.equals("replay")) {
                long budget = args.length > 1 ? CsvLoader.cents(args[1]) : 20_000;
                int window = args.length > 2 ? Integer.parseInt(args[2]) : 60;
                int every = args.length > 3 ? Integer.parseInt(args[3]) : 100;
                int limit = args.length > 4 ? Integer.parseInt(args[4]) : 0;
                if (every < 1 || limit < 0) throw new IllegalArgumentException("Every must be positive; Days must be nonnegative");
                String[] parts = unpack(readInput());
                MarketData data = new MarketData(parts[0], parts[1], parts[2], parts[3]);
                System.out.println("TradeOptima | DSA-3 | Modules 2 through 6");
                System.out.println("Historical price observations: " + data.dates.length + "; " + data.dates[0] + " to " + data.dates[data.dates.length - 1]);
                System.out.println("USD; bundled source: Plotly archived stockdata.csv (see data/SOURCES.md).");
                System.out.println("News and venue books are SYNTHETIC fixtures. Paper scenarios only.");
                System.out.println("Each day is an independent one-share-per-asset allocation; budget resets each day.");
                System.out.println("DP measures hindsight profit. Utility = past-window DP profit in cents + news mentions; it is not a return forecast.");
                Pipeline pipeline = new Pipeline(data, budget, window, 3, 50, 1, 2000);
                int total = limit == 0 ? data.dates.length : Math.min(limit, data.dates.length);
                Pipeline.Snapshot last = null;
                for (int day = 0; day < total; day++) {
                    last = pipeline.next();
                    if (mode.equals("replay") && (day == 0 || (day + 1) % every == 0 || day == total - 1 || last.newsCount > 0))
                        printSnapshot(data, last);
                }
                if (mode.equals("demo")) {
                    printSnapshot(data, last);
                    int a = 0;
                    long[] prefix = new long[total]; String[] dates = new String[total];
                    System.arraycopy(data.close[a], 0, prefix, 0, total); System.arraycopy(data.dates, 0, dates, 0, total);
                    printLedger(data.tickers[a], new CsvLoader.Prices(dates, prefix), 3, 50, 1);
                }
                System.out.println("Replay complete: " + total + " observed days; future rows never enter a snapshot.");
            } else if (mode.equals("help")) {
                System.out.println("Use .\\run.ps1 -Mode demo|replay|test|benchmark|history|legacy|help");
                System.out.println("Replay options: -Budget 200 -Window 60 -Every 100 -Days 0 (all days)");
                System.out.println("History: -PriceFile path.csv -PriceColumn Close -Trades 3 -Fee 0.50 -Cooldown 1");
            } else throw new IllegalArgumentException("Unknown mode: " + mode);
        } catch (Exception ex) {
            System.err.println("TradeOptima: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static void printLedger(String name, CsvLoader.Prices p, int k, long fee, int cooldown) {
        TradeOptimizer.Result r = TradeOptimizer.solve(p.close, k, fee, cooldown);
        System.out.println("\nMODULE 3: " + name + " full-history hindsight optimum = " + Ds.money(r.profit) + "; " + r.trades.length + " trades");
        for (TradeOptimizer.Trade t : r.trades) System.out.println("  BUY " + p.dates[t.buyDay] + " @ " + Ds.money(t.buyPrice)
                + " | SELL " + p.dates[t.sellDay] + " @ " + Ds.money(t.sellPrice) + " | net " + Ds.money(t.profit));
        System.out.println("  Ledger legal: " + TradeOptimizer.validate(p.close, r, k, fee, cooldown));
    }

    private static void printSnapshot(MarketData d, Pipeline.Snapshot s) {
        System.out.println("\n" + d.dates[s.day] + " | day " + (s.day + 1) + " | headlines=" + s.newsCount + " | past returns seen=" + s.returnsSeen);
        for (int a = 0; a < d.tickers.length; a++) System.out.println("  " + d.tickers[a] + " close=" + Ds.money(d.close[a][s.day])
                + " | M2 mentions=" + s.mentions[a] + " | M3 window hindsight=" + Ds.money(s.analysis[a].profit));
        System.out.print("  M5 selected: ");
        for (int a : s.selection.chosen) System.out.print(d.tickers[a] + " ");
        System.out.println("| utility=" + s.selection.value + " | reserved=" + Ds.money(s.selection.cost));
        for (int i = 0; i < s.routes.length; i++) {
            int a = s.selection.chosen[i]; OrderRouter.Venue[] venues = d.venues(a, s.day);
            for (OrderRouter.Fill f : s.routes[i].fills) System.out.println("  M4 " + d.tickers[a] + ": " + f.qty + " share @ "
                    + Ds.money(f.unitCost) + " on " + venues[f.venue].name);
        }
        System.out.println("  M4 actual cost=" + Ds.money(s.spent) + " | basket marked at close=" + Ds.money(s.value));
        if (s.risk == null) System.out.println("  M6 risk unavailable: need a selected basket and at least two observed returns");
        else System.out.println("  M6 1-day 95% bootstrap VaR=" + Ds.fix(s.risk.var, 2) + " | CVaR=" + Ds.fix(s.risk.cvar, 2)
                + " USD | paths=" + s.risk.paths + " (negative loss means a gain)");
    }

    static String readInput() throws Exception {
        byte[] bytes = new byte[8192]; int n = 0, read;
        while (true) {
            if (n == bytes.length) {
                if (n >= 32 * 1024 * 1024) throw new IllegalArgumentException("Input exceeds 32 MB");
                byte[] bigger = new byte[n * 2]; System.arraycopy(bytes, 0, bigger, 0, n); bytes = bigger;
            }
            read = System.in.read(bytes, n, bytes.length - n);
            if (read < 0) break;
            n += read;
        }
        return new String(bytes, 0, n, "UTF-8");
    }

    static String[] unpack(String input) {
        String[] out = new String[4]; int start = 0;
        for (int i = 0; i < out.length; i++) {
            int newline = input.indexOf('\n', start);
            if (newline < 0) throw new IllegalArgumentException("Missing CSV packet; use run.ps1");
            int length = Integer.parseInt(input.substring(start, newline).trim()); start = newline + 1;
            if (length < 0 || length > input.length() - start) throw new IllegalArgumentException("Truncated CSV packet");
            out[i] = input.substring(start, start + length); start += length;
        }
        if (start != input.length()) throw new IllegalArgumentException("Unexpected packet data");
        return out;
    }
}

