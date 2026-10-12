package tradeoptima;

/** Structured output adapter for the Windows interface. Algorithms stay in their original classes. */
public final class GuiBridge {
    private GuiBridge() {}

    public static void main(String[] args) {
        try {
            if (args.length == 0) throw new IllegalArgumentException("Missing GUI action");
            String input = Main.readInput();
            if (args[0].equals("history")) System.out.print(history(input, args[1], Integer.parseInt(args[2]), CsvLoader.cents(args[3]), Integer.parseInt(args[4])));
            else if (args[0].equals("replay")) System.out.print(replay(input, CsvLoader.cents(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), CsvLoader.cents(args[4]), Integer.parseInt(args[5])));
            else throw new IllegalArgumentException("Unknown GUI action");
        } catch (Exception ex) { System.err.println(ex.getMessage()); System.exit(1); }
    }

    static String history(String csv, String column, int k, long fee, int cooldown) {
        if (k < 0 || k > 1000 || fee < 0 || fee > 1_000_000_000L || cooldown < 0 || cooldown > 1000)
            throw new IllegalArgumentException("Trades and cooldown must be between 0 and 1000; fee must be nonnegative and within the supported range.");
        CsvLoader.Prices p = CsvLoader.prices(csv, column);
        if (p.close.length > 100_000) throw new IllegalArgumentException("At most 100000 observations");
        long start = System.nanoTime();
        TradeOptimizer.Result result = TradeOptimizer.solve(p.close, k, fee, cooldown);
        double ms = (System.nanoTime() - start) / 1e6;
        boolean legal = TradeOptimizer.validate(p.close, result, k, fee, cooldown);
        if (!legal) throw new IllegalStateException("Trade ledger did not pass validation");
        StringBuilder out = new StringBuilder("{\"kind\":\"history\",\"column\":");
        quote(out, column);
        out.append(",\"profit\":").append(result.profit).append(",\"durationMs\":").append(Ds.fix(ms, 3));
        out.append(",\"legal\":true,\"dates\":"); strings(out, p.dates);
        out.append(",\"prices\":"); numbers(out, p.close);
        out.append(",\"trades\":[");
        for (int i = 0; i < result.trades.length; i++) {
            if (i > 0) out.append(',');
            TradeOptimizer.Trade t = result.trades[i];
            out.append("{\"buyDay\":").append(t.buyDay).append(",\"sellDay\":").append(t.sellDay);
            out.append(",\"buyDate\":"); quote(out, p.dates[t.buyDay]);
            out.append(",\"sellDate\":"); quote(out, p.dates[t.sellDay]);
            out.append(",\"buyPrice\":").append(t.buyPrice).append(",\"sellPrice\":").append(t.sellPrice).append(",\"profit\":").append(t.profit).append('}');
        }
        out.append("],\"risk\":");
        RiskEngine.Risk risk = null;
        if (p.close.length >= 3) {
            RiskEngine.Reservoir sample = new RiskEngine.Reservoir(500, new Ds.Rng(7));
            for (double r : RiskEngine.logReturns(p.close)) sample.add(r);
            risk = RiskEngine.bootstrapVaR(sample.sample(), p.close[p.close.length - 1] / 100.0, 1, 5000, 0.95, new Ds.Rng(17));
        }
        risk(out, risk); return out.append('}').toString();
    }

    static String replay(String packet, long budget, int window, int k, long fee, int cooldown) {
        String[] sections = Main.unpack(packet);
        MarketData data = new MarketData(sections[0], sections[1], sections[2], sections[3]);
        Pipeline pipeline = new Pipeline(data, budget, window, k, fee, cooldown, 2000);
        StringBuilder out = new StringBuilder("{\"kind\":\"replay\",\"tickers\":"); strings(out, data.tickers);
        out.append(",\"dates\":"); strings(out, data.dates);
        out.append(",\"days\":[");
        for (int day = 0; day < data.dates.length; day++) {
            if (day > 0) out.append(',');
            Pipeline.Snapshot s = pipeline.next();
            out.append("{\"date\":"); quote(out, data.dates[day]);
            out.append(",\"newsCount\":").append(s.newsCount).append(",\"spent\":").append(s.spent).append(",\"value\":").append(s.value);
            out.append(",\"reserved\":").append(s.selection.cost).append(",\"utility\":").append(s.selection.value).append(",\"returnsSeen\":").append(s.returnsSeen);
            out.append(",\"assets\":[");
            for (int a = 0; a < data.tickers.length; a++) {
                if (a > 0) out.append(',');
                boolean chosen = false;
                for (int x : s.selection.chosen) if (x == a) chosen = true;
                out.append("{\"ticker\":"); quote(out, data.tickers[a]);
                out.append(",\"price\":").append(data.close[a][day]).append(",\"mentions\":").append(s.mentions[a]);
                out.append(",\"profit\":").append(s.analysis[a].profit).append(",\"selected\":").append(chosen).append('}');
            }
            out.append("],\"fills\":["); int count = 0;
            for (int i = 0; i < s.routes.length; i++) {
                int a = s.selection.chosen[i]; OrderRouter.Venue[] venues = data.venues(a, day);
                for (OrderRouter.Fill f : s.routes[i].fills) {
                    if (count++ > 0) out.append(',');
                    out.append("{\"ticker\":"); quote(out, data.tickers[a]);
                    out.append(",\"venue\":"); quote(out, venues[f.venue].name);
                    out.append(",\"quantity\":").append(f.qty).append(",\"unitCost\":").append(f.unitCost).append('}');
                }
            }
            out.append("],\"risk\":"); risk(out, s.risk); out.append('}');
        }
        return out.append("]}").toString();
    }

    private static void risk(StringBuilder out, RiskEngine.Risk risk) {
        if (risk == null) { out.append("null"); return; }
        out.append("{\"var\":").append(Ds.fix(risk.var, 6)).append(",\"cvar\":").append(Ds.fix(risk.cvar, 6));
        out.append(",\"paths\":").append(risk.paths).append('}');
    }
    private static void numbers(StringBuilder out, long[] values) {
        out.append('[');
        for (int i = 0; i < values.length; i++) { if (i > 0) out.append(','); out.append(values[i]); }
        out.append(']');
    }
    private static void strings(StringBuilder out, String[] values) {
        out.append('[');
        for (int i = 0; i < values.length; i++) { if (i > 0) out.append(','); quote(out, values[i]); }
        out.append(']');
    }
    /** JSON string escaping without a serialization library. */
    static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32) {
                out.append("\\u00"); String hex = "0123456789abcdef";
                out.append(hex.charAt(c / 16)).append(hex.charAt(c % 16));
            } else out.append(c);
        }
        out.append('"');
    }
}
