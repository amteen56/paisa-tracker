# Sample data

Two ways to get a populated app, for trying it out, for screenshots, and for reproducing a bug.

---

## From inside the app

**Settings → Sample data → Add sample data.**

Generates roughly six months of plausible history plus three budgets. Adding it **merges** — if
you have already recorded something real, it stays. Removing it takes out only what it added,
matched on the `sample-` id prefix, so this is never a one-way door.

The generator is **seeded**, so the same version of the app always produces the same ledger. That
is the whole point: a screenshot is repeatable, and "the daily average looks wrong" is only
actionable if the data behind it can be regenerated exactly.

---

## From a file

`transactions.csv` in this directory imports through **More → Import & Export → Add from CSV**.
It is small and hand-editable, which makes it the better choice when you want to reproduce one
specific case rather than a whole ledger.

---

## Why the shape matters

The generator does not produce uniform noise. It produces:

| Pattern | Why |
|---|---|
| Salary on the 1st, once a month | Gives the trend chart two distinguishable series |
| Rent on the 3rd, bills mid-month | Large, regular, and dominate the category donut |
| Groceries clustered at weekends | Makes the calendar's per-day bars have a visible rhythm |
| Roughly a fifth of days empty | The calendar needs gaps, or every cell looks alike |
| One outlier a month | Gives "biggest expenses" and the busiest-day marker something real |
| Nothing dated in the future | A forward-dated record skews the rolling average and reads as a bug |

Uniform random data makes every chart in the app look identical, which hides exactly the bugs a
chart has — a flat trend line, a donut of equal slices, and a calendar with no gaps would all look
fine while being wrong.

---

## CSV format

The importer reads columns **by name**, so order does not matter and extra columns are ignored.
Only `date` and `amountMinor` are required.

| Column | Required | Notes |
|---|---|---|
| `date` | yes | `YYYY-MM-DD` |
| `amountMinor` | yes | **Whole paisa**, always positive. `35050` is Rs. 350.50 |
| `type` | no | `EXPENSE` or `INCOME`; anything else is treated as `EXPENSE` |
| `time` | no | `HH:mm`; a missing time becomes midday, not midnight |
| `category` | no | Matched on name, case-insensitively. Created if unknown |
| `subcategory` | no | Matched within the category |
| `paymentMethod` | no | Matched on name, case-insensitively. Created if unknown |
| `description` | no | |
| `notes` | no | |
| `currency` | no | Read and ignored — every amount is PKR |
| `id` | no | Present in Paisa's own exports, which is what makes re-importing a no-op |

**Amounts are minor units on purpose.** A spreadsheet that helpfully reformats `350.50` is exactly
the drift the `Long` money model exists to prevent, so the column is an integer count of paisa.

**Rows without an `id`** get one derived from their own date, amount and description, so importing
a hand-made file twice does not duplicate it.

**Bad rows are reported, not fatal.** A row with an unreadable date or a non-positive amount is
listed by line number in the import preview and skipped; everything else still imports.
