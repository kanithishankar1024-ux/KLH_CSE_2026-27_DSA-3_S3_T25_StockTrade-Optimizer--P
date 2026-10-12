package tradeoptima;

/**
 * MODULE 2b: Aho-Corasick multi-pattern matcher. Builds a trie + failure links + dictionary links, then
 * scans text once in O(|text| + matches). Case-insensitive, ASCII alphabet (non-ASCII folds to one slot).
 */
public final class AhoCorasick {
    private static final int A = 128;
    private final int[][] next;
    private final int[] fail, dict, patAt, patLen;

    public static final class Match {
        public final int pattern, start, end;       // [start, end) in the scanned text
        Match(int p, int s, int e) { pattern = p; start = s; end = e; }
    }

    private static int code(char c) { c = StringAlgorithms.fold(c); return c < A ? c : A - 1; }

    public AhoCorasick(String[] patterns) {
        int total = 1;
        for (String p : patterns) total += p.length();
        next = new int[total][A];
        for (int i = 0; i < total; i++) for (int c = 0; c < A; c++) next[i][c] = -1;
        fail = new int[total]; dict = new int[total]; patAt = new int[total];
        patLen = new int[patterns.length];
        for (int i = 0; i < total; i++) { patAt[i] = -1; dict[i] = -1; }

        int nodes = 1;
        for (int id = 0; id < patterns.length; id++) {
            String p = patterns[id];
            patLen[id] = p.length();
            if (p.length() == 0) continue;
            int v = 0;
            for (int i = 0; i < p.length(); i++) {
                int c = code(p.charAt(i));
                if (next[v][c] == -1) next[v][c] = nodes++;
                v = next[v][c];
            }
            if (patAt[v] == -1) patAt[v] = id;          // duplicate pattern strings: first id wins
        }

        // BFS: failure links + complete the automaton so scanning never backtracks.
        int[] queue = new int[nodes];
        int qh = 0, qt = 0;
        for (int c = 0; c < A; c++) {
            int u = next[0][c];
            if (u == -1) next[0][c] = 0;
            else { fail[u] = 0; queue[qt++] = u; }
        }
        while (qh < qt) {
            int v = queue[qh++];
            int f = fail[v];
            dict[v] = patAt[f] >= 0 ? f : dict[f];
            for (int c = 0; c < A; c++) {
                int u = next[v][c];
                if (u == -1) next[v][c] = next[f][c];
                else { fail[u] = next[f][c]; queue[qt++] = u; }
            }
        }
    }

    public Match[] search(String text) {
        Ds.IntList pid = new Ds.IntList(), st = new Ds.IntList(), en = new Ds.IntList();
        int v = 0;
        for (int i = 0; i < text.length(); i++) {
            v = next[v][code(text.charAt(i))];
            int u = patAt[v] >= 0 ? v : dict[v];
            while (u > 0) {
                int id = patAt[u];
                pid.add(id); en.add(i + 1); st.add(i + 1 - patLen[id]);
                u = dict[u];
            }
        }
        Match[] out = new Match[pid.size()];
        for (int x = 0; x < out.length; x++) out[x] = new Match(pid.get(x), st.get(x), en.get(x));
        return out;
    }
}
