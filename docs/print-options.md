# Print options, validation, discovery and cost

This document is the spec for `core.command`, `core.validation`, `core.discovery`, `core.queue` and
`core.cost`. Unit tests should follow it closely.

## Standard options

| UI control | Model | Values | `lpr` argument(s) | Default |
|---|---|---|---|---|
| Printer | `Printer` | known printer names | `-P <name>` | last used, else `tfppr1` |
| Copies | `Int` | 1 … `MAX_COPIES` (20) | `-# <n>` (only added when n > 1) | 1 (never remembered) |
| Page ranges | `PageRanges?` | see grammar below | `-o page-ranges=<canonical>` (only added when set) | all pages (never remembered) |
| Duplex | `Duplex` | `OFF`, `LONG_EDGE`, `SHORT_EDGE` | `-o sides=one-sided` / `two-sided-long-edge` / `two-sided-short-edge` | `OFF` |
| Pages per sheet | `PagesPerSheet` | 1, 2, 4 | `-o number-up=<n>` (only added when n > 1) | 1 |
| Orientation | `Orientation` | `PORTRAIT`, `LANDSCAPE` | `-o landscape` (only added for landscape) | `PORTRAIT` |
| Fit to page | `Boolean` | on/off | `-o fit-to-page` (only added when on) | off |
| Paper size | `PaperSize` | `A4`, `A3`, `A5`, `LETTER` | `-o media=A4` / `A3` / `A5` / `Letter` | `A4` |

`MAX_COPIES` is a safety cap (6 € buys only 120 b/w pages). It's a constant and can be changed.

`sides` is always sent explicitly, even `one-sided`, so the result doesn't depend on the printer's
server-side default. The same goes for `media`.

## Command shape

```
lpr -P <printer> [-# <copies>] -o sides=<…> [-o number-up=<n>] [-o page-ranges=<r>]
    [-o landscape] [-o fit-to-page] -o media=<size> [-o <Key>=<Value> …discovered]
```

- Every token is shell-quoted individually (see [security.md](security.md#command-construction)), e.g.
  `'lpr' '-P' 'tfppr1' '-o' 'sides=two-sided-long-edge'`.
- **No filename, no `-T` job title, no file argument.** `lpr` reads the document from stdin.
- The option order is fixed, which keeps unit-test expectations exact.

## Validation rules

| Field | Rule | Reject example |
|---|---|---|
| Printer name | Must be in the current printer list (hardcoded ∪ discovered) **and** match `^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$` (no leading `-`) | `tfppr1;rm -rf ~` |
| Copies | Integer, 1 ≤ n ≤ `MAX_COPIES`, parsed from digits only | `2 -o x`, `0`, `1e3` |
| Page ranges | Grammar below; every page ≤ document page count when known | `1-4;id`, `5-3`, `0` |
| Enums | Only the enum's own mapping table produces strings | n/a (no free text) |
| Discovered key | Must match `^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$`, appear in the cached `lpoptions` output for **this** printer, and not collide with a standard option | `ColorModel$(id)` |
| Discovered value | Must be one of that key's parsed allowed values and match `^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$` | `Gray RGB`, `Custom.10x10` |
| Job id (`lprm`) | Digits only, and present in the latest `lpq` result as owned by the user | `12 34`, `*` |

The validators return typed values. The command builders take **only typed values**, never raw strings.

### Page-range grammar

```
ranges := range ("," range)*
range  := page | page "-" page
page   := [1-9][0-9]{0,4}
```

- Spaces are stripped before parsing (`1 - 4, 7` → `1-4,7`). Any other character is an error.
- For `a-b`, `a ≤ b` is required.
- Overlapping, adjacent or unordered ranges are **normalised**: sorted and merged into a canonical form
  (`7,1-4,3-5` → `1-5,7`, `1-2,3-4` → `1-4`). The canonical form is what gets sent and what the cost estimate uses.
- Open-ended ranges (`5-`) are not supported in v1.
- Maximum input length is 200 characters.

## Page-range semantics (important CUPS caveat)

The CUPS documentation says `page-ranges` refers to the **output pages**, i.e. after `number-up`
is applied, not to the document's original pages. Newer cups-filters versions may behave differently.

- The estimator takes a `PageRangeSemantics` setting (`OUTPUT_PAGES` default, `INPUT_PAGES`), together
  with the `BillingUnit`, in a `CostPolicy`. The validator uses the same policy for the highest page it
  accepts: with `OUTPUT_PAGES` that is `ceil(N / up)`, so ranges that are too high for the chosen layout are
  rejected inline ("Pages go up to 4.") and Print is disabled.
- The UI hint for page ranges says what the ranges refer to when pages-per-sheet > 1.
- **Has to be checked manually on the real pool printers.** See [roadmap.md](roadmap.md) and
  [decisions.md](decisions.md#d-07-page-range-semantics-with-number-up).

## Cost estimation

All money is in **integer cents**. Rates: `BW = 5`, `COLOR = 10`, based on **printer type** (not content).

Let `N` = document page count (counted locally), `up` = pages per sheet, `c` = copies.

```
if semantics == INPUT_PAGES:
    selected     = |ranges ∩ [1..N]|          (N if no ranges)
    impressions  = ceil(selected / up)        // printed sides per copy
else  // OUTPUT_PAGES (CUPS docs)
    outputPages  = ceil(N / up)
    impressions  = |ranges ∩ [1..outputPages]| (outputPages if no ranges)

sheets       = duplex == OFF ? impressions : ceil(impressions / 2)
billable     = c * (BILLING_UNIT == PER_SIDE ? impressions : sheets)
costCents    = billable * rate(printer.type)
```

- `BILLING_UNIT` defaults to `PER_SIDE` (one "page" = one printed side). Whether the pool bills
  per side or per sheet with duplex isn't known yet.
  See [decisions.md](decisions.md#d-06-billing-unit-for-duplex).
- Orientation, fit-to-page and paper size don't affect cost.
- The Print button label uses `billable` as "pages": `Print · 12 pages · 0.60 €`.
  Format: two decimals, `€` suffix, singular `1 page`.
- When the page count isn't known (e.g. a corrupt PDF), show `Print · ? pages` and still allow printing.
- Show a warning when `costCents` exceeds `WARNING_THRESHOLD_CENTS` (200: more than 2 €, a third of the
  monthly budget). It is a hint under the options, not a block.
- While the pages are still being counted the button just says `Print`.

Required test cases include: no ranges; ranges past the end of the document; `up` = 2 or 4 with odd page
counts; duplex with odd impressions; copies > 1; both semantics; both billing units; b/w vs colour.

## Discovery (M3)

### `lpstat -p`

Typical CUPS output:

```
printer tfppr1 is idle.  enabled since Mon 06 Oct 2026 09:12:01 CEST
printer tfppr2 now printing tfppr2-4711.  enabled since Mon 06 Oct 2026 09:12:01 CEST
printer tfppr3 disabled since Fri 03 Oct 2026 17:00:00 CEST -
	Paper jam
```

Parser contract:
- Lines starting with `printer <name> ` give a printer and a state: `idle`, `printing`, `disabled`, or
  `unknown`. Indented lines that follow are the status reason.
- Names that fail the printer-name regex are dropped, not escaped.
- A discovered printer that's in the hardcoded table keeps its b/w/colour type. A printer that isn't
  gets type `UNKNOWN` and is billed as colour (the conservative choice), with a note in the UI.
- Locale: the server may print localised text. If parsing finds nothing, fall back.

### `lpoptions -p <printer> -l`

Typical output (PPD-style):

```
PageSize/Media Size: *A4 A5 Letter Legal Custom.WIDTHxHEIGHT
Duplex/2-Sided Printing: *None DuplexNoTumble DuplexTumble
ColorModel/Color Mode: Gray *RGB
HPEconoMode/EconoMode: *False True
```

Parser contract:
- Line format: `<Key>/<Label>: <values…>`, separated by whitespace. A value starting with `*` is the
  default (the `*` is stripped).
- Values starting with `Custom.` are dropped.
- Keys or values that fail the regex are dropped. If no values are left, the key is dropped.
- Unparseable lines are skipped. The parser never throws.
- **Collisions with standard controls are hidden:** `PageSize`, `PageRegion`, `media`, `Duplex`, `sides`,
  `number-up`, `page-ranges`, `copies`, `orientation-requested`, `landscape`, `fit-to-page`. The standard
  control wins.
- Remaining options become generated controls (a dropdown with the label and default value). A
  discovered option is only sent when the user changes it from the default. Options with a single value
  get no control.
- Limits (D-16): 100 printers, 60 options, 40 values per option; labels lose control characters and are cut at 80.
- `LpoptionsParser.parse` returns the options plus `recognizedLines`; zero recognised lines counts as
  "unexpected output" and the cache is left alone.
- Discovered settings are typed `DiscoveredSetting(printerName, key, value)` values made only by
  `Validators.discoveredSetting`; `lpr` sends them last, sorted by key, after `media`.

### Fixtures

The samples are Kotlin string constants in `shared/src/commonTest/kotlin/.../core/discovery/Fixtures.kt`
(`lpstat`, `lpoptions`, `lpqEmpty`, `lpqJobs`); malformed and localised variants are inline in the tests.
Real captures should be added next to them.

**The samples above are synthetic.** Replace them with real captures from the pool once the owner
provides them (see [decisions.md](decisions.md#open-questions)).

## Queue (M3)

### `lpq -P <printer>`

Typical CUPS output:

```
tfppr1 is ready and printing
Rank    Owner   Job     File(s)                         Total Size
active  doej    1234    (stdin)                         482304 bytes
1st     muster  1235    slides.pdf                      1048576 bytes
```

or `tfppr1 is ready` followed by `no entries`.

- Parse the rows after the header into `QueueEntry(rank, owner, jobId, fileLabel, sizeBytes)`. Rows that
  don't have the shape are skipped; text with no status line, header or rows is rejected as unexpected output.
- "Own" means `owner == poolUsername`. Only own entries get a Cancel action.
- Jobs printed through Pool Print show up as `(stdin)` or similar, because no title is sent. That's expected.
- Cancel: `lprm -P <printer> <jobId>`, after confirmation, then refresh.
