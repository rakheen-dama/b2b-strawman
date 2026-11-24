# 657D OBS 1b re-run after c20927686 (take-on report PDF: minus signs, source-file SHA-256)

**Verdict: PASS.** The newly rendered take-on report PDF prints an ASCII "-" in the Control totals equation (no "#"), and every source file's full 64-hex SHA-256 sits on its own row inside the page. The downloaded PDF and CSV hash to the `take_on_report_files` rows. The backend logged no `word-break` WARN and 0 ERROR.

- **Head:** PR #1855 @ `c20927686` (branch `epic-657D/take-on-report-files-and-first-month`). The e2e **stack 3** backend (:8081) was rebuilt from the worktree at `c20927686` (container created 14:12:27Z, started 14:12:29Z). The frontend, Postgres, Mailpit and data are unchanged from the first OBS run, so G, H and P were already taken on. Tenant `tenant_7d218705360b`.
- **Why a new take-on:** report files are immutable, so G's report keeps its old PDF. A new account K was taken on to get a newly rendered report.
- **Browser:** headless Chromium from `frontend/node_modules/@playwright/test` on node v20.11.1, run by `obs-rerun.cjs`. This is `../obs.cjs` with account K added: the `accounts` step creates only K, and the `commit` step downloads the files for T=K. Commands: `STEP=accounts|trust|signoff|commit T=K node obs-rerun.cjs`. Log: `obs-log.txt`. State: `state.json`. The Playwright MCP was not used.
- **Window:** 2026-11-24T14:14:08Z → 14:15:10Z (`obs-window-{start,end}.txt`). `expected-figures.txt` was written at 14:13:52Z, before anything was observed.
- **No SQL or REST writes.** psql was read-only `select` with `-e` (`psql-report-files.txt`). REST was used only for the script's own read-only GETs (batch status, report JSON) and the Mailpit API.

## What was done (UI only)

1. **Bob** opened Settings → Trust accounting and created **"OBS 657D Trust banking K"**: General, Standard Bank 051001, account no. 6576001, opened 2026-01-01, not primary (`01-create-account-K.png`, `02-trust-accounts.png`).
2. **Bob** set up the trust take-on on K at **C = 30 September 2026** (`files/`). It reuses clients A/B and matters A1/B1 from the first run:
   - client ledger: A/A1 R30 000,00 and B/B1 R15 000,00
   - unidentified deposit: R500,00, 10 Sep 2026, "EFT K-UNKNOWN"
   - outstanding items: deposit in transit EFT K-0001 R5 000,00 (B/B1, 29 Sep) and unpresented cheque PAY-K-779 R20 000,00 (A/A1, 25 Sep)
   - position: bank R60 500,00, outstanding deposits R5 000,00, outstanding payments R20 000,00, cashbook R45 500,00, Fund ledger R0,00, invested R0,00

   The batch validated with 5/5 rows valid, 0 blocks and all five equations holding at a difference of R 0,00 (`k-11-trust-files.png`, `k-13-validated.png`).
3. **Alice** signed off from Home: the widget showed "OBS 657D Trust banking K at 30 September 2026", and she accepted and signed (`k-20-alice-home.png`, `k-22-alice-signed.png`).
4. **Bob** committed the batch: Created 5, Skipped 0, Failed 0, batch `COMPLETED`, report `991aeddb-a74c-4424-b18d-01479e187867`. On the Results step he used **Download PDF** and **Download CSV** (`k-32-results-report.png`, `k-33-report-downloads.png`). The "Preparing…" state was not seen: both buttons were already there at the first sample. Mailpit stayed at 2 → 2.

## Hashes (download = DB = report JSON)

| File | Downloaded (`shasum -a 256`, `sha256-downloads.txt`) | `take_on_report_files.sha256` (`psql-report-files.txt`) | Size |
|---|---|---|---|
| `take-on-report-obs-657d-trust-banking-k-2026-09-30.pdf` | `96cc3be5e8a56439c98bb5c551072db6098f91fc882c715473d18c6ac95e12f8` | `96cc3be5e8a56439c98bb5c551072db6098f91fc882c715473d18c6ac95e12f8` | 6 750 B, `%PDF-1.6` |
| `take-on-report-obs-657d-trust-banking-k-2026-09-30.csv` | `53c13f35f8f1f5eb707f9cb958921bd02035162466039b8c17904e5959025e48` | `53c13f35f8f1f5eb707f9cb958921bd02035162466039b8c17904e5959025e48` | 981 B |

The report JSON's `files.{pdf,csv}.sha256` are the same values (`obs-log.txt`). `take_on_report_renders` shows 0 attempts, no error and no `failed_at`.

## PDF content (rendered with ghostscript `png16m -r110`, zooms at 250 dpi; text with `txtwrite`)

The PDF has **2 pages**. K's report is short, so Control totals is at the bottom of page 1. In G's report it was on page 2. I looked at each image below.

- **Control totals** (`report-K-pdf-page1.png`, `report-K-pdf-zoom-control-totals.png`):
  - "Bank balance + outstanding deposits **-** outstanding payments = cashbook"
  - "R60 500,00 + R5 000,00 **-** R20 000,00 = R45 500,00 against R45 500,00 — agrees"

  The minus is a plain hyphen-minus, and no "#" appears anywhere. The em dashes render correctly.
- **Source files** (`report-K-pdf-page2.png`, `report-K-pdf-zoom-source-files-sha.png`): three files. Each file row (File, Section, Sheet, Rows, Uploaded) is followed by its own "SHA-256 …" row, and the whole 64-hex value ends well inside the right margin:
  - `trust-client-ledger-K.csv`: `b0cee89c1cc84eaf3f0100b7cffd70c274f5437e383a5424729db338d2649ac8`
  - `trust-outstanding-items-K.csv`: `38182626984ad2cb9187b25bbcba90dfc3857f63e1d03309f1255e9d39b1e5a5`
  - `trust-unidentified-deposits-K.csv`: `95177767f75f5644b14569ea81040dd0da55f8df7f80f5b4b9c36641ba3c479e`

  These equal `take_on_sources.file_sha256` and `shasum` of the files in `files/`. The footer's snapshot SHA-256 `ff619458…67b4e7840` (= `take_on_reports.snapshot_sha256`) is fully visible.
- **Text** (`report-K-pdf-text.txt`): 0 "#" characters and 0 U+2212. The equation line has an ASCII 0x2D. All three source hashes and the snapshot hash appear in full, once each.
- **CSV:** the content is unchanged in kind (the first line is `# Trust take-on report — OBS 657D Trust banking K at 30 September 2026`, then the CREDITOR / UNIDENTIFIED / OUTSTANDING rows). Its two "#" are the comment-line prefixes by design.
- **Observed, not in scope:** in the Outstanding items table the narrow Reference column wraps "EFT K-0001" as "EFT K-" / "0001" (page 1). It is legible and not the 1b defect.

## Backend log over the window (`backend-log-obs-window.txt`, 25 lines; WARN/ERROR in `backend-log-warn-error.txt`)

- **0 `word-break` lines** (also 0 in the container's whole log since the 14:12 restart) and **0 "unrecognized CSS property"**.
- **0 ERROR**, and no "exception" text.
- **19 WARN**, made up of:
  - **16 × pdfbox "Using fallback font LiberationSans for base font …"**: one report render (PDF), the same count per render as the first run. This is pre-existing OBS-657D-2.
  - **3 × pdfbox `FileSystemFontProvider`** font-cache messages ("22 new fonts found…", "Building on-disk font cache…", "Finished building…"). These come from the first PDF render after the container restart.
- **INFO:** 3 job-enqueuer lines.

## Browser

0 console/page errors in all four steps, and no HTTP ≥ 400 or 202 response logged (`obs-log.txt`, `state.json` → `consoleErrors`).

## Housekeeping

Nothing appeared under `documentation/screenshots/legal-vertical/` (it still holds only the tracked `.gitkeep` files and `phase67/README.md`).
