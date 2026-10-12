# Data provenance

## prices.csv

- Publisher/distributor: Plotly datasets repository.
- Download: https://raw.githubusercontent.com/plotly/datasets/master/stockdata.csv
- Repository view: https://github.com/plotly/datasets/blob/master/stockdata.csv
- Retrieved: 2026-10-01.
- SHA-256 of the bundled original bytes: `60BF505FEC160CE05BA959382ADEE32EA6E53CE14FE22886DB27C58FE1B0D26B`.
- Observations: 2,306 dated rows, 2007-01-03 through 2016-03-01.
- Original columns: MSFT, IBM, SBUX, AAPL, GSPC, Date. The original CSV is preserved byte-for-byte.
- Selected instruments: AAPL, MSFT, IBM, SBUX. The GSPC index column is retained but not treated as a tradable stock.
- Prices are interpreted in USD and rounded half-up to cents by CsvLoader.

The CSV provides historical stock observations but does not explain the adjustment methodology in its header. Do not describe these numbers as independently verified unadjusted exchange closes or reconstruct corporate actions from them. The project uses the series exactly as published as a historical algorithm dataset. Adjustment conventions and rounding affect the profits reported. Results are idealized model outputs, not proof of an executable historical strategy.

The bundled copy makes the demonstration reproducible without a network connection. The hash identifies the precise downloaded snapshot even if the upstream master branch changes.

## aapl-close.csv

Derived locally from prices.csv by retaining Date and AAPL and renaming AAPL to Close. No prices were invented. Values retain their original decimal text; Java performs the cent rounding.

This demonstrates the **Date,Close schema used by Yahoo-style CSV exports**. It is not a claim that this file was downloaded from Yahoo Finance. Choose Close or Adj Close explicitly for your own CSV.

## headlines.csv

Eight **synthetic classroom headlines**, written for the search/replay demonstration. None claims to reproduce a real company announcement. Each headline begins with SYNTHETIC.

Date is the classroom availability date. News arriving between price observations is processed at the first following observation. This dataset has day-level resolution and does not support intraday timing claims.

## orderbook.csv

Eight **synthetic book templates**, one ask level on each of two fictitious venues per instrument.

Ask price on replay day = that day's observed price + OffsetCents.
All-in price = ask price + FeeCents.
Size is an artificial available share quantity. SIM-A and SIM-B are classroom labels.

These templates are neither downloaded order books nor exchange snapshots. A new independent book is constructed each day. The parser permits one level per instrument/venue; the underlying router retains its original API for multiple levels and competing orders.

## CSV contracts

- instruments.csv: Ticker,Name. One to 32 instruments; unique ticker names. These should be ASCII because the search engine uses an ASCII automaton.
- prices.csv: Date plus a named price column for each instrument. All instruments share the same date rows. Dates must be real YYYY-MM-DD dates in strictly increasing order. Missing, zero, negative, NaN or malformed prices fail with an error.
- headlines.csv: Date,Headline. Dates in nondecreasing order and within the price-history range. Quotes, quoted commas, doubled quotes, embedded newlines, BOM and CRLF are handled by the CSV parser.
- orderbook.csv: Ticker,Venue,OffsetCents,FeeCents,Size. Offsets/fees are nonnegative integers in cents. Sizes are positive integers. One to 63 venues per instrument.
- Supported history: at most 100,000 observations; per-price limit 1,000,000,000 cents. Input transport is capped at 32 MB.

Use a separate folder containing the four required files with `run.ps1 -DataDir "path"`. Use history mode for an individual Date,Close file. CSV errors stop execution instead of silently skipping data.
