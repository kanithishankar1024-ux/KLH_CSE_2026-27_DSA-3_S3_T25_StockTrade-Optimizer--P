package tradeoptima;

/** Validated historical prices and explicitly synthetic news/order-book fixtures. */
public final class MarketData {
    public final String[] tickers, names, dates;
    public final long[][] close;
    public final String[] newsDates, headlines;
    public final BookLevel[] books;

    public static final class BookLevel {
        public final int instrument;
        public final String venue;
        public final long offset, fee, size;
        BookLevel(int i, String v, long o, long f, long s) { instrument = i; venue = v; offset = o; fee = f; size = s; }
    }

    public MarketData(String instrumentsCsv, String pricesCsv, String newsCsv, String booksCsv) {
        String[][] instruments = CsvLoader.parse(instrumentsCsv);
        int ti = CsvLoader.column(instruments, "Ticker"), ni = CsvLoader.column(instruments, "Name");
        int count = instruments.length - 1;
        if (count < 1 || count > 32) throw new IllegalArgumentException("Use 1..32 instruments");
        tickers = new String[count]; names = new String[count]; close = new long[count][];
        String[] datesFound = null;
        for (int i = 0; i < count; i++) {
            tickers[i] = instruments[i + 1][ti]; names[i] = instruments[i + 1][ni];
            if (tickers[i].length() == 0 || names[i].length() == 0) throw new IllegalArgumentException("Empty instrument");
            for (int j = 0; j < i; j++) if (tickers[i].equals(tickers[j])) throw new IllegalArgumentException("Duplicate ticker");
            CsvLoader.Prices p = CsvLoader.prices(pricesCsv, tickers[i]);
            close[i] = p.close; datesFound = p.dates;
        }
        dates = datesFound;
        if (dates.length > 100_000) throw new IllegalArgumentException("At most 100000 price rows");
        String[][] news = CsvLoader.parse(newsCsv);
        int nd = CsvLoader.column(news, "Date"), nh = CsvLoader.column(news, "Headline");
        newsDates = new String[news.length - 1]; headlines = new String[newsDates.length];
        for (int i = 0; i < newsDates.length; i++) {
            newsDates[i] = CsvLoader.date(news[i + 1][nd]); headlines[i] = news[i + 1][nh];
            if (i > 0 && newsDates[i].compareTo(newsDates[i - 1]) < 0) throw new IllegalArgumentException("News must be date ordered");
            if (newsDates[i].compareTo(dates[0]) < 0 || newsDates[i].compareTo(dates[dates.length - 1]) > 0) throw new IllegalArgumentException("News date outside price history");
        }
        String[][] book = CsvLoader.parse(booksCsv);
        int bt = CsvLoader.column(book, "Ticker"), bv = CsvLoader.column(book, "Venue"), bo = CsvLoader.column(book, "OffsetCents");
        int bf = CsvLoader.column(book, "FeeCents"), bs = CsvLoader.column(book, "Size");
        books = new BookLevel[book.length - 1];
        int[] levels = new int[count];
        for (int i = 0; i < books.length; i++) {
            int instrument = indexOf(book[i + 1][bt]);
            long offset = Long.parseLong(book[i + 1][bo]), fee = Long.parseLong(book[i + 1][bf]), size = Long.parseLong(book[i + 1][bs]);
            if (offset < 0 || offset > 100_000 || fee < 0 || fee > 100_000 || size < 1 || size > 1_000_000) throw new IllegalArgumentException("Invalid synthetic book level");
            String venue = book[i + 1][bv];
            if (venue.length() == 0) throw new IllegalArgumentException("Empty venue");
            for (int j = 0; j < i; j++) if (books[j].instrument == instrument && books[j].venue.equals(venue)) throw new IllegalArgumentException("Use one level per venue in this CSV adapter");
            books[i] = new BookLevel(instrument, venue, offset, fee, size);
            if (++levels[instrument] > 63) throw new IllegalArgumentException("At most 63 venues per instrument");
        }
        for (int n : levels) if (n == 0) throw new IllegalArgumentException("Every instrument needs a book template");
    }

    public int indexOf(String ticker) {
        for (int i = 0; i < tickers.length; i++) if (tickers[i].equals(ticker)) return i;
        throw new IllegalArgumentException("Unknown ticker: " + ticker);
    }

    /** Translate fixture offsets into same-day synthetic asks; never read later prices. */
    public OrderRouter.Venue[] venues(int instrument, int day) {
        int n = 0;
        for (BookLevel b : books) if (b.instrument == instrument) n++;
        OrderRouter.Venue[] v = new OrderRouter.Venue[n]; n = 0;
        for (BookLevel b : books) if (b.instrument == instrument)
            v[n++] = new OrderRouter.Venue(b.venue, b.fee, new long[] {close[instrument][day] + b.offset}, new long[] {b.size});
        return v;
    }
}
