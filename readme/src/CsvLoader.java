package tradeoptima;

/** Hand-written CSV reader. File transport belongs to the PowerShell launcher. */
public final class CsvLoader {
    private CsvLoader() {}

    /** Parsed rows, including the header. Supports quoted commas, escaped quotes and CRLF. */
    public static String[][] parse(String text) {
        String[][] rows = new String[32][];
        String[] cells = new String[16];
        int nr = 0, nc = 0;
        StringBuilder field = new StringBuilder();
        boolean quoted = false, closed = false, touched = false;
        int start = text.length() > 0 && text.charAt(0) == '\ufeff' ? 1 : 0;
        for (int i = start; i <= text.length(); i++) {
            boolean eof = i == text.length();
            char ch = eof ? '\n' : text.charAt(i);
            if (quoted) {
                if (eof) throw bad("unclosed quoted field");
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else { quoted = false; closed = true; }
                } else field.append(ch);
                continue;
            }
            if (ch == ',' || ch == '\n' || ch == '\r') {
                if (nc == cells.length) {
                    String[] bigger = new String[nc * 2];
                    System.arraycopy(cells, 0, bigger, 0, nc); cells = bigger;
                }
                cells[nc++] = field.toString(); field.setLength(0); closed = false;
                if (ch == ',') { touched = true; continue; }
                if (touched || nc > 1 || cells[0].trim().length() > 0) {
                    if (nr == rows.length) {
                        String[][] bigger = new String[nr * 2][];
                        System.arraycopy(rows, 0, bigger, 0, nr); rows = bigger;
                    }
                    rows[nr] = new String[nc]; System.arraycopy(cells, 0, rows[nr++], 0, nc);
                }
                nc = 0; touched = false;
                if (!eof && ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
            } else if (ch == '"') {
                if (field.length() != 0 || closed) throw bad("quote inside unquoted field");
                quoted = true; touched = true;
            } else {
                if (closed) throw bad("unexpected character after closing quote");
                field.append(ch); touched = true;
            }
        }
        if (nr == 0) throw bad("empty CSV");
        String[][] out = new String[nr][]; System.arraycopy(rows, 0, out, 0, nr);
        for (int i = 1; i < nr; i++) if (out[i].length != out[0].length) throw bad("column count at row " + (i + 1));
        for (int i = 0; i < out[0].length; i++) {
            out[0][i] = out[0][i].trim();
            if (out[0][i].length() == 0) throw bad("empty header");
            for (int j = 0; j < i; j++) if (out[0][i].equals(out[0][j])) throw bad("duplicate header " + out[0][i]);
        }
        return out;
    }

    public static int column(String[][] rows, String name) {
        for (int i = 0; i < rows[0].length; i++) if (rows[0][i].equals(name)) return i;
        throw bad("missing column " + name);
    }

    /** Exact decimal-to-cents conversion, half-up rounding beyond two decimal places. */
    public static long cents(String text) {
        String s = text.trim();
        if (s.length() == 0) throw bad("empty price");
        long whole = 0, fraction = 0;
        int decimals = -1, digits = 0;
        boolean round = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.' && decimals == -1) { decimals = 0; continue; }
            if (c < '0' || c > '9') throw bad("invalid nonnegative decimal: " + s);
            digits++;
            if (decimals == -1) {
                if (whole > (Long.MAX_VALUE / 100 - 10) / 10) throw bad("price too large");
                whole = whole * 10 + c - '0';
            } else {
                decimals++;
                if (decimals <= 2) fraction = fraction * 10 + c - '0';
                if (decimals == 3) round = c >= '5';
            }
        }
        if (digits == 0) throw bad("missing decimal digits");
        if (decimals == 1) fraction *= 10;
        return whole * 100 + fraction + (round ? 1 : 0);
    }

    /** Validate Gregorian dates without a date library. Lexicographic order is chronological. */
    public static String date(String s) {
        if (s.length() != 10 || s.charAt(4) != '-' || s.charAt(7) != '-') throw bad("date must be YYYY-MM-DD: " + s);
        for (int i = 0; i < s.length(); i++) if (i != 4 && i != 7 && (s.charAt(i) < '0' || s.charAt(i) > '9')) throw bad("invalid date " + s);
        int year = Integer.parseInt(s.substring(0, 4)), month = Integer.parseInt(s.substring(5, 7)), day = Integer.parseInt(s.substring(8, 10));
        int[] days = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        if (year < 1 || month < 1 || month > 12) throw bad("invalid date " + s);
        if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) days[1] = 29;
        if (day < 1 || day > days[month - 1]) throw bad("invalid date " + s);
        return s;
    }

    public static final class Prices {
        public final String[] dates;
        public final long[] close;
        Prices(String[] dates, long[] close) { this.dates = dates; this.close = close; }
    }

    /** Accepts Yahoo-style Date,Close CSV or any explicitly named price column. */
    public static Prices prices(String csv, String priceColumn) {
        String[][] r = parse(csv);
        int dc = column(r, "Date"), pc = column(r, priceColumn), n = r.length - 1;
        if (n == 0) throw bad("price CSV has no observations");
        String[] dates = new String[n]; long[] close = new long[n];
        for (int i = 0; i < n; i++) {
            dates[i] = date(r[i + 1][dc]); close[i] = cents(r[i + 1][pc]);
            if (close[i] <= 0 || close[i] > 1_000_000_000L) throw bad("price outside supported range at row " + (i + 2));
            if (i > 0 && dates[i].compareTo(dates[i - 1]) <= 0) throw bad("dates must be strictly increasing");
        }
        return new Prices(dates, close);
    }

    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException("CSV: " + message); }
}
