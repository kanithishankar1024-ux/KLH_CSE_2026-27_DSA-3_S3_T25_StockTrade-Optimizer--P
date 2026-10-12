package tradeoptima;

/** Successive-shortest-augmenting-path min-cost max-flow (SPFA handles negative residual costs). */
public final class MinCostMaxFlow {
    public static final long INF = Long.MAX_VALUE / 4;
    private final int n;
    private final int[] head, nxt, to;
    private final long[] cap, cost;
    private int m;

    public MinCostMaxFlow(int nodes, int maxEdges) {
        n = nodes;
        head = new int[n];
        for (int i = 0; i < n; i++) head[i] = -1;
        nxt = new int[2 * maxEdges]; to = new int[2 * maxEdges];
        cap = new long[2 * maxEdges]; cost = new long[2 * maxEdges];
    }

    /** Returns the id of the forward edge; flowOn(id) reads how much went through it. */
    public int addEdge(int u, int v, long capacity, long unitCost) {
        int id = m;
        to[m] = v; cap[m] = capacity; cost[m] = unitCost; nxt[m] = head[u]; head[u] = m++;
        to[m] = u; cap[m] = 0; cost[m] = -unitCost; nxt[m] = head[v]; head[v] = m++;
        return id;
    }

    public long flowOn(int edgeId) { return cap[edgeId ^ 1]; }

    /** @return {maxFlow, minCost} */
    public long[] run(int s, int t) {
        long flow = 0, total = 0;
        long[] dist = new long[n];
        int[] prevE = new int[n];
        boolean[] inQ = new boolean[n];
        int[] q = new int[n + 1];
        while (true) {
            for (int i = 0; i < n; i++) { dist[i] = INF; prevE[i] = -1; }
            int qh = 0, qt = 0;
            dist[s] = 0; q[qt++] = s; inQ[s] = true;
            while (qh != qt) {
                int u = q[qh]; qh = (qh + 1) % q.length; inQ[u] = false;
                for (int e = head[u]; e != -1; e = nxt[e]) {
                    if (cap[e] > 0 && dist[u] + cost[e] < dist[to[e]]) {
                        dist[to[e]] = dist[u] + cost[e];
                        prevE[to[e]] = e;
                        if (!inQ[to[e]]) { inQ[to[e]] = true; q[qt] = to[e]; qt = (qt + 1) % q.length; }
                    }
                }
            }
            if (dist[t] >= INF) break;
            long push = INF;
            for (int v = t; v != s; v = to[prevE[v] ^ 1]) push = Math.min(push, cap[prevE[v]]);
            for (int v = t; v != s; v = to[prevE[v] ^ 1]) { cap[prevE[v]] -= push; cap[prevE[v] ^ 1] += push; }
            flow += push; total += push * dist[t];
        }
        return new long[] {flow, total};
    }
}
