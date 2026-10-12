package tradeoptima;

/**
 * Hand-built support structures, so the whole platform runs with no collection-library imports:
 * growable int list, long-indexed bit set, xoshiro256** PRNG, money formatting.
 */
public final class Ds {
    private Ds() {}

    /** Growable array of primitive integers. */
    public static final class IntList {
        private int[] a = new int[16];
        private int n;

        public void add(int v) {
            if (n == a.length) {
                int[] b = new int[n * 2];
                System.arraycopy(a, 0, b, 0, n);
                a = b;
            }
            a[n++] = v;
        }
        public int get(int i) { return a[i]; }
        public int size() { return n; }
        public int[] toArray() {
            int[] r = new int[n];
            System.arraycopy(a, 0, r, 0, n);
            return r;
        }
    }

    /** Bit set addressed by a long index (needed: N*k bits can exceed 2^31). */
    public static final class Bits {
        private final long[] w;
        public Bits(long nbits) {
            long words = (nbits + 63) >>> 6;
            if (words > Integer.MAX_VALUE - 8) throw new IllegalArgumentException("bit set too large");
            w = new long[(int) words];
        }
        public void set(long i) { w[(int) (i >>> 6)] |= 1L << (i & 63); }
        public boolean get(long i) { return (w[(int) (i >>> 6)] >>> (i & 63) & 1L) != 0; }
    }

    /** xoshiro256** seeded through splitmix64. Deterministic for a given seed. */
    public static final class Rng {
        private long s0, s1, s2, s3;
        private boolean haveSpare;
        private double spare;

        private static long mix(long z) {
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
        public Rng(long seed) {
            long x = seed;
            x += 0x9E3779B97F4A7C15L; s0 = mix(x);
            x += 0x9E3779B97F4A7C15L; s1 = mix(x);
            x += 0x9E3779B97F4A7C15L; s2 = mix(x);
            x += 0x9E3779B97F4A7C15L; s3 = mix(x);
        }
        public long nextLong() {
            long r = Long.rotateLeft(s1 * 5, 7) * 9;
            long t = s1 << 17;
            s2 ^= s0; s3 ^= s1; s1 ^= s2; s0 ^= s3; s2 ^= t;
            s3 = Long.rotateLeft(s3, 45);
            return r;
        }
        /** Uniform in [0, bound). */
        public int nextInt(int bound) { return (int) ((nextLong() >>> 1) % bound); }
        /** Uniform in [lo, hi]. */
        public long range(long lo, long hi) { return lo + (nextLong() >>> 1) % (hi - lo + 1); }
        /** Uniform in [0, 1). */
        public double nextDouble() { return (nextLong() >>> 11) * 0x1.0p-53; }
        /** Standard normal via Box-Muller (two variates per pair of uniforms). */
        public double nextGaussian() {
            if (haveSpare) { haveSpare = false; return spare; }
            double u = 1.0 - nextDouble();               // (0, 1]
            double v = nextDouble();
            double r = Math.sqrt(-2.0 * Math.log(u));
            spare = r * Math.sin(2 * Math.PI * v);
            haveSpare = true;
            return r * Math.cos(2 * Math.PI * v);
        }
    }

    /** Cents -> "-1234.05" without library formatters. */
    public static String money(long cents) {
        boolean neg = cents < 0;
        long c = Math.abs(cents);
        long frac = c % 100;
        return (neg ? "-" : "") + (c / 100) + "." + (frac < 10 ? "0" : "") + frac;
    }

    /** Fixed decimals for doubles, no library formatter. */
    public static String fix(double x, int decimals) {
        double m = 1;
        for (int i = 0; i < decimals; i++) m *= 10;
        long r = Math.round(Math.abs(x) * m);
        String s = Long.toString(r / (long) m);
        if (decimals > 0) {
            String f = Long.toString(r % (long) m);
            while (f.length() < decimals) f = "0" + f;
            s += "." + f;
        }
        return (x < 0 && r != 0 ? "-" : "") + s;
    }
}
