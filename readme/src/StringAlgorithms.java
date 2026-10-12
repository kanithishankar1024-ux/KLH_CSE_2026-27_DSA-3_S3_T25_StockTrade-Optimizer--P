package tradeoptima;

/** MODULE 2a: KMP, Z-function and edit distance (all hand-built). */
public final class StringAlgorithms {
    private StringAlgorithms() {}

    public static char fold(char c) { return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c; }

    public static String lower(String s) {
        char[] a = s.toCharArray();
        for (int i = 0; i < a.length; i++) a[i] = fold(a[i]);
        return new String(a);
    }

    /** prefix[i] = length of the longest proper prefix of s[0..i] that is also a suffix. */
    public static int[] prefixFunction(String s) {
        int n = s.length();
        int[] pi = new int[n];
        for (int i = 1; i < n; i++) {
            int q = pi[i - 1];
            while (q > 0 && s.charAt(i) != s.charAt(q)) q = pi[q - 1];
            if (s.charAt(i) == s.charAt(q)) q++;
            pi[i] = q;
        }
        return pi;
    }

    /** All start indices of pat in text, O(n+m). */
    public static int[] kmpSearch(String text, String pat) {
        Ds.IntList out = new Ds.IntList();
        int m = pat.length();
        if (m == 0 || m > text.length()) return out.toArray();
        int[] pi = prefixFunction(pat);
        int q = 0;
        for (int i = 0; i < text.length(); i++) {
            while (q > 0 && text.charAt(i) != pat.charAt(q)) q = pi[q - 1];
            if (text.charAt(i) == pat.charAt(q)) q++;
            if (q == m) { out.add(i - m + 1); q = pi[q - 1]; }
        }
        return out.toArray();
    }

    /** z[i] = length of the longest common prefix of s and s[i..]. */
    public static int[] zFunction(String s) {
        int n = s.length();
        int[] z = new int[n];
        if (n == 0) return z;
        z[0] = n;
        int l = 0, r = 0;
        for (int i = 1; i < n; i++) {
            if (i < r) z[i] = Math.min(r - i, z[i - l]);
            while (i + z[i] < n && s.charAt(z[i]) == s.charAt(i + z[i])) z[i]++;
            if (i + z[i] > r) { l = i; r = i + z[i]; }
        }
        return z;
    }

    /** Pattern search via Z on pat + '\0' + text (pattern must not contain NUL). */
    public static int[] zSearch(String text, String pat) {
        Ds.IntList out = new Ds.IntList();
        int m = pat.length();
        if (m == 0 || m > text.length()) return out.toArray();
        int[] z = zFunction(pat + '\0' + text);
        for (int i = m + 1; i < z.length; i++) if (z[i] >= m) out.add(i - m - 1);
        return out.toArray();
    }

    /** Levenshtein distance with two rolling rows, O(|a|*|b|) time, O(|b|) memory. */
    public static int editDistance(String a, String b) {
        int n = a.length(), m = b.length();
        int[] prev = new int[m + 1], cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int sub = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                cur[j] = Math.min(sub, Math.min(prev[j] + 1, cur[j - 1] + 1));
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[m];
    }
}
