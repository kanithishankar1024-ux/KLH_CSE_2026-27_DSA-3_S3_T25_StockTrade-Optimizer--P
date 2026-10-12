package tradeoptima;

/**
 * MODULE 2: ticker and news search. Exact multi-pattern scan of headlines (Aho-Corasick, whole-word only)
 * plus fuzzy ticker/company lookup (edit distance) for typo-tolerant search boxes.
 */
public final class SearchEngine {
    private final String[] tickers, names;
    private final AhoCorasick ac;
    private final int n;

    public static final class Hit {
        public final String ticker, name; public final int distance;
        Hit(String t, String nm, int d) { ticker = t; name = nm; distance = d; }
    }

    public SearchEngine(String[] tickers, String[] names) {
        if (tickers.length != names.length) throw new IllegalArgumentException("length mismatch");
        this.tickers = tickers; this.names = names; this.n = tickers.length;
        String[] pats = new String[2 * n];                // pattern p maps to instrument p % n
        for (int i = 0; i < n; i++) { pats[i] = tickers[i]; pats[n + i] = names[i]; }
        ac = new AhoCorasick(pats);
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /** Number of whole-word mentions of every instrument in one headline. */
    public int[] mentions(String headline) {
        int[] cnt = new int[n];
        for (AhoCorasick.Match m : ac.search(headline)) {
            boolean left = m.start == 0 || !isWordChar(headline.charAt(m.start - 1));
            boolean right = m.end == headline.length() || !isWordChar(headline.charAt(m.end));
            if (left && right) cnt[m.pattern % n]++;
        }
        return cnt;
    }

    /** Aggregate mention counts over a whole news feed. */
    public int[] scanFeed(String[] headlines) {
        int[] total = new int[n];
        for (String h : headlines) {
            int[] c = mentions(h);
            for (int i = 0; i < n; i++) total[i] += c[i];
        }
        return total;
    }

    /** Instruments within maxDist edits of the query (ticker or name), best first. */
    public Hit[] fuzzy(String query, int maxDist) {
        String q = StringAlgorithms.lower(query);
        Hit[] tmp = new Hit[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            int d = Math.min(StringAlgorithms.editDistance(q, StringAlgorithms.lower(tickers[i])),
                             StringAlgorithms.editDistance(q, StringAlgorithms.lower(names[i])));
            if (d <= maxDist) {
                Hit h = new Hit(tickers[i], names[i], d);
                int x = m++;                                   // insertion sort by distance
                while (x > 0 && tmp[x - 1].distance > d) { tmp[x] = tmp[x - 1]; x--; }
                tmp[x] = h;
            }
        }
        Hit[] out = new Hit[m];
        System.arraycopy(tmp, 0, out, 0, m);
        return out;
    }
}
