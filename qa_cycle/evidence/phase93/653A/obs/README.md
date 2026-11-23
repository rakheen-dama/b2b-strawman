# 653A OBS: take-on foundation observed in a real browser

- **Stack:** E2E mock-auth stack built from worktree-epic-653A @ `88fb72068` (PR #1846). Frontend :3001, backend :8081.
- **Tenant schema:** `tenant_7d218705360b`. **S3 bucket:** `docteams-dev` (LocalStack).
- **Browser:** headless Chromium launched by `obs.cjs` from `frontend/node_modules/@playwright/test`, run on node v20.11.1. The script is included here byte for byte. Each step was run as `STEP=n node obs.cjs`. Step 2b also set `TAG=b DEBTORS_FILE=/tmp/653obs/variant/debtors-two-sheets-header-row1.xlsx HEADER_ROW=1`.
- **Database checks:** read-only psql `select` statements only. No SQL writes and no REST calls for UI steps.
- **Run time:** 2026-11-23, 12:19–12:22 UTC.

## Verdict: **FAIL** (step 2, as specified, cannot be completed)

| Step | Result |
|---|---|
| 1 Clients CSV (BOM, `;`) | **PASS** |
| 2 Debtors XLSX, header on row 3 (given fixture) | **FAIL**: the upload is refused before the Sheet step |
| 2b Same workbook with the header moved to row 1 (supplementary) | PASS for hidden sheet, error messages and cards = table |
| 3 Bob sees the tab / Carol falls back | **PASS** |
| 4 Cancel purges S3 and rows | **PASS** (done on the 2b debtors batch) |
| 5 Audit details: ids and counts only | **PASS**, with a note below |
| 6 Console errors | 1 known spurious mock-auth error. Nothing take-on related. |

## Step 1: Clients, semicolon CSV with BOM (PASS)

- **Tab visibility.** Alice's Matters tabs are Templates, Naming, Custom fields, Tags, Checklists, Request templates, Request settings and **Take-on**. Clicking Take-on goes to `?tab=take-on` (`s1-01`).
- **Upload.** Start (Clients) → upload `clients-semicolon-bom.csv`. No warnings were shown, and the browser went to the wizard with batch `3bcc5edb-…`. The file is single-sheet, so the wizard went straight to Mapping (`s1-04`).
- **Headers in the Sheet-step preview** (`s1-05`): Client name, Email address, Phone, ID number, Client type, CDD completed date, Beneficial ownership, Screening, CDD records location, Risk rating.
  - Playwright `innerText` shows them in capitals because of CSS `uppercase`.
  - The suggested mapping shows `name → "Client name"`.
  - In the database, `column_mapping.targets.name` = `Client name`, hex `436c69656e74206e616d65`. There is **no EF BB BF (BOM)**.
  - The stored raw keys are clean (`psql-01`).
- **Delimiter.** `parse_options` = `{"delimiter": ";", "encoding": "UTF-8", "dateFormat": "dd/MM/yyyy", "decimalSeparator": "COMMA"}`.
  - row_count 4, malformed 0, blank_skipped 0.
- **Mapping.** 10 required and optional fields were auto-suggested. There were no mapping issues, and Save worked.
- **Dry run.** Cards: Rows 4, Valid 2, Errors 2, Duplicates 0, Blocks 0.
- **Table counts.**
  - With the filter set to All: 4 rows. Pending 0, Valid 2, Error 2.
  - Blocks only: 0 rows ("No rows match").
  - **Cards equal the table for every status.**
- **Row errors shown:**
  - Row 4 (Karoo): `"not-an-email" is not a valid email address.` and `"2026-02-30" is not a date in the format dd/mm/yyyy.`
  - Row 5 (Lindiwe): `CDD completed date is required.`

## Step 2: Debtors XLSX, given fixture (FAIL)

- **Steps.** Start (Debtor opening balances) → cutover date 2026-10-31 → choose `debtors-two-sheets.xlsx` → Upload.
- **Observed.** An inline error is shown and no batch is created (`s2a-02-upload-error.png`):
  > No row of this file could be read. Check that it is a .csv or .xlsx export and that every row has no more cells than the header row.
- **Expected.** The upload succeeds and the Sheet step lets the user pick header row 3.
- **Cause, read from code.** `TakeOnBatchService.readUpload()` (around line 418) reads the default sheet with header row hard-coded to 1:

  ```java
  sourceFiles.read(file.bytes(), file.format(), sheetName, 1, TakeOnParseOptions.defaults());
  ```

  1. The default sheet is the first visible one, `Debtors`.
  2. Row 1 there is the one-cell title, so it becomes the "header".
  3. Every data row has 7 cells, which is more than 1, so each one is counted as malformed.
  4. `TakeOnSheetAssembler.finish()` then throws `ALL_ROWS_MALFORMED`.

- **Impact.** Any export with a title or blank rows above the header can never reach the header-row picker. That is exactly the case the Sheet step exists for.
- **Side effects of the refused upload.** No `takeon.*` audit event, no S3 object, no batch row. The S3 listing before step 2b held only the clients file.

## Step 2b: Supplementary run on a variant fixture (header on row 1)

- **Variant file.** `debtors-two-sheets-header-row1.xlsx` (copy included). It is the same workbook with the title row and blank row removed. The hidden "Old notes" sheet, `1,234` and `#REF!` are kept.
- **This is not a PASS for step 2.** It only exercises the parts that come after the defect.
- **Upload.** Batch `b7cb8bd6-…`. The wizard shows `Cutover date 2026-10-31`.
- **Sheet step** (`s2b-03`):
  - Options: "Old notes" with a **Hidden sheet** badge, and "Debtors", which is pre-selected.
  - Default header row is 1.
  - The preview shows `1,234` and `#REF!` in the Outstanding column.
- **Mapping.** All 7 columns were auto-suggested (Client code→clientRef, Outstanding→outstandingAmount, and so on).
- **Dry run.** Cards: Rows 4, Valid 2, Errors 2, Duplicates 0, Blocks 0.
  - Table: All 4, Pending 0, Valid 2, Error 2, Blocks only 0. **Cards equal the table.**
- **Error messages, exact text:**
  - Row 3 (`1,234`): `"1,234" is ambiguous: it could use a comma for thousands or for decimals. Check the decimal separator chosen for this file.` Database: code `AMOUNT_AMBIGUOUS`, field `outstandingAmount`.
  - Row 4 (`#REF!`): `This cell holds an error (#REF!). Correct it in Excel and upload again.` Database: code `CELL_ERROR`, field `Outstanding`.

## Step 3: Roles (PASS)

- **Bob (admin).** His Matters tabs include **Take-on** (`s3-bob-matters-tabs`).
  - The tab renders the dataset cards and the history (`s3-bob-take-on-tab`).
  - The deep link `?tab=take-on` selects Take-on.
- **Carol (member).** Her Matters tabs are only **Templates** and **Tags**, with no Take-on.
  - The deep link `/org/e2e-test-org/settings/matters?tab=take-on` **falls back to the Templates tab**. The h1 is "Matter Templates".
  - It shows the notice "You don't have access to Take-on — showing Templates instead." The URL keeps `?tab=take-on` (`s3-carol-deeplink-take-on`).

## Step 4: Cancel the debtors batch (PASS)

- **UI.**
  - Clicking Cancel take-on opens the dialog "Cancel this take-on?" (`s4-02`).
  - Confirming changes the status badge to **Cancelled**, with the notice "This take-on was cancelled. Its uploaded files and row values were deleted." (`s4-03`).
  - It is still Cancelled after a reload (`s4-04`).
  - History lists the debtors batch as Cancelled, 4 (2 / 2) (`s4-05`).
- **S3.** The object count went from 21 to 20 (`s3-diff-before-after-cancel.txt`). The deleted object is:

  ```
  < take-on/tenant_7d218705360b/b7cb8bd6-8065-4c47-aefa-ed3c4bd24d79/89bb312a-…/debtors-two-sheets-header-row1.xlsx
  ```

  The clients CSV object is still there.
- **Database** (`psql-03`):
  - **Batch:** status CANCELLED, `cancelled_at` set.
  - **Source:** `s3_key` NULL, `purged_at` set.
  - **All 4 rows:**
    - `raw_values` = `mapped_values` = `{"purged": true}`.
    - `source_ref` NULL, `dedup_key` NULL. Before the cancel they were `row:N` and `number:fn-100N`.
    - `errors` hold only code and field: `[{"code":"AMOUNT_AMBIGUOUS","field":"outstandingAmount"}]` and `[{"code":"CELL_ERROR","field":"Outstanding"}]`.
    - `warnings` is `[]`.

## Step 5: Audit (PASS, with a note)

`psql-04-audit-takeon.txt` holds 9 `takeon.*` events:

| Event type | Count |
|---|---|
| `batch.created` | 2 |
| `source.uploaded` | 2 |
| `source.mapped` | 2 |
| `batch.validated` | 2 |
| `batch.cancelled` | 1 |

- **Fields in `details`:**
  - ids: `batch_id`, `source_id`
  - the dataset or section enum
  - counts: `row_count`, `malformed_count`, `file_size_kb`, `mapped_field_count`, `custom_field_count`, `parked_column_count`, `total_rows`, `valid_rows`, `error_rows`, `duplicate_rows`, `block_count`, `purged_sources`
  - `file_format` and `reason_code`
- A grep for client names, emails, file names, cell values and batch names found **nothing**.
- **Note:** every event also carries `"actor_name": "Alice Owner"`. That is the platform's standard actor enrichment for the staff user, not client data.

## Step 6: Console errors

- **Step 1:** 0.
- **Steps 2a and 2b:** 1 each, `MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch`. It fires right after the post-login redirect. This is the known spurious mock-auth error and is not take-on related.
- **Steps 3 and 4:** 0.
- **Backend log, 12:18–12:22 UTC.** No WARN or ERROR (`backend-log-obs-window.txt` has only job-enqueuer INFO lines). The take-on code does not log.

## Other observations (not failures)

1. **`CELL_ERROR` field naming.**
   - `CELL_ERROR` stores the source column header (`"Outstanding"`) in `field`, while the other errors store the catalogue key (`outstandingAmount`).
   - `numeric_cells` (`["Outstanding"]`) also survives the purge.
   - Both are header names, not cell values.
2. **`file_name` after purge.** `take_on_sources.file_name` is kept after the cancel and purge.
3. **Misleading date message.** For `2026-02-30` the message says "not a date in the format dd/mm/yyyy". The cell is ISO-shaped and ISO is always accepted, so the real problem is an impossible day. The message is misleading but harmless.
4. **Malformed count.** `takeon.source.uploaded` for the debtors file has `malformed_count: 1`, because the `#REF!` cell is counted as a malformed row at read time.
