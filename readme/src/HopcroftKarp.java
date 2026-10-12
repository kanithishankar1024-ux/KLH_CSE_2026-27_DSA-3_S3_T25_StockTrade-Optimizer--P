package tradeoptima;

/** Maximum bipartite matching in O(E*sqrt(V)). Left = orders, right = venues. */
public final class HopcroftKarp {
    private final int nl, nr;
    private final int[] head, nxt, to;
    private int m;
    public final int[] matchL, matchR;
    private int[] dist;

    public HopcroftKarp(int left, int right, int maxEdges) {
        nl = left; nr = right;
        head = new int[nl]; for (int i = 0; i < nl; i++) head[i] = -1;
        nxt = new int[maxEdges]; to = new int[maxEdges];
        matchL = new int[nl]; matchR = new int[nr];
        for (int i = 0; i < nl; i++) matchL[i] = -1;
        for (int i = 0; i < nr; i++) matchR[i] = -1;
    }

    public void addEdge(int l, int r) { to[m] = r; nxt[m] = head[l]; head[l] = m++; }

    private boolean bfs() {
        dist = new int[nl];
        int[] q = new int[nl];
        int qh = 0, qt = 0;
        for (int u = 0; u < nl; u++) {
            if (matchL[u] == -1) { dist[u] = 0; q[qt++] = u; } else dist[u] = -1;
        }
        boolean found = false;
        while (qh < qt) {
            int u = q[qh++];
            for (int e = head[u]; e != -1; e = nxt[e]) {
                int w = matchR[to[e]];
                if (w == -1) found = true;
                else if (dist[w] == -1) { dist[w] = dist[u] + 1; q[qt++] = w; }
            }
        }
        return found;
    }

    private boolean dfs(int u) {
        for (int e = head[u]; e != -1; e = nxt[e]) {
            int r = to[e], w = matchR[r];
            if (w == -1 || (dist[w] == dist[u] + 1 && dfs(w))) {
                matchL[u] = r; matchR[r] = u;
                return true;
            }
        }
        dist[u] = -1;
        return false;
    }

    public int run() {
        int size = 0;
        while (bfs()) for (int u = 0; u < nl; u++) if (matchL[u] == -1 && dfs(u)) size++;
        return size;
    }
}
