package tradeoptima;

/**
 * MODULE 4: cross-venue order router.
 *  - route(): min-cost max-flow. S -> order(qty) -> ask-level(price+fee) -> T(size). The optimum fills as much
 *    quantity as liquidity allows at the lowest total cost, splitting orders across venues/levels as needed.
 *  - matchBlocks(): all-or-none block orders (each must fill entirely on ONE venue, one block per venue per
 *    round) via Hopcroft-Karp maximum bipartite matching.
 */
public final class OrderRouter {
    private OrderRouter() {}

    public static final class Venue {
        public final String name; public final long feePerShare; public final long[] price, size;
        public Venue(String name, long feePerShare, long[] price, long[] size) {
            this.name = name; this.feePerShare = feePerShare; this.price = price; this.size = size;
        }
        long liquidity() { long s = 0; for (long x : size) s += x; return s; }
    }

    public static final class Order {
        public final int id; public final long qty; public final long allowedMask;   // bit v = venue v allowed
        public Order(int id, long qty, long allowedMask) { this.id = id; this.qty = qty; this.allowedMask = allowedMask; }
    }

    public static final class Fill {
        public final int order, venue, level; public final long qty, unitCost;
        Fill(int o, int v, int l, long q, long u) { order = o; venue = v; level = l; qty = q; unitCost = u; }
    }

    public static final class Plan {
        public final Fill[] fills; public final long totalCost, filledQty; public final long[] filledPerOrder;
        Plan(Fill[] f, long c, long q, long[] per) { fills = f; totalCost = c; filledQty = q; filledPerOrder = per; }
    }

    public static Plan route(Venue[] venues, Order[] orders) {
        int O = orders.length, L = 0;
        for (Venue v : venues) L += v.price.length;
        int[] levelVenue = new int[L], levelIdx = new int[L];
        int x = 0;
        for (int v = 0; v < venues.length; v++)
            for (int l = 0; l < venues[v].price.length; l++) { levelVenue[x] = v; levelIdx[x] = l; x++; }

        int S = 0, T = 1 + O + L;
        MinCostMaxFlow g = new MinCostMaxFlow(T + 1, O + O * L + L);
        for (int o = 0; o < O; o++) g.addEdge(S, 1 + o, orders[o].qty, 0);
        int[][] eid = new int[O][L];
        for (int o = 0; o < O; o++)
            for (int lv = 0; lv < L; lv++) {
                int v = levelVenue[lv];
                eid[o][lv] = -1;
                if ((orders[o].allowedMask >>> v & 1L) != 0)
                    eid[o][lv] = g.addEdge(1 + o, 1 + O + lv, orders[o].qty,
                                           venues[v].price[levelIdx[lv]] + venues[v].feePerShare);
            }
        for (int lv = 0; lv < L; lv++) g.addEdge(1 + O + lv, T, venues[levelVenue[lv]].size[levelIdx[lv]], 0);

        long[] fc = g.run(S, T);
        Ds.IntList fo = new Ds.IntList(), fv = new Ds.IntList(), fl = new Ds.IntList();
        long[] fq = new long[O * L + 1];
        int cnt = 0;
        long[] per = new long[O];
        for (int o = 0; o < O; o++)
            for (int lv = 0; lv < L; lv++) {
                if (eid[o][lv] < 0) continue;
                long f = g.flowOn(eid[o][lv]);
                if (f > 0) { fo.add(o); fv.add(levelVenue[lv]); fl.add(levelIdx[lv]); fq[cnt++] = f; per[o] += f; }
            }
        Fill[] fills = new Fill[cnt];
        for (int i = 0; i < cnt; i++) {
            Venue v = venues[fv.get(i)];
            fills[i] = new Fill(orders[fo.get(i)].id, fv.get(i), fl.get(i), fq[i], v.price[fl.get(i)] + v.feePerShare);
        }
        return new Plan(fills, fc[1], fc[0], per);
    }

    /** @return venue index per order (-1 = could not be placed whole). */
    public static int[] matchBlocks(Venue[] venues, Order[] orders) {
        int O = orders.length, V = venues.length;
        HopcroftKarp hk = new HopcroftKarp(O, V, Math.max(1, O * V));
        for (int o = 0; o < O; o++)
            for (int v = 0; v < V; v++)
                if ((orders[o].allowedMask >>> v & 1L) != 0 && venues[v].liquidity() >= orders[o].qty) hk.addEdge(o, v);
        hk.run();
        return hk.matchL;
    }
}
