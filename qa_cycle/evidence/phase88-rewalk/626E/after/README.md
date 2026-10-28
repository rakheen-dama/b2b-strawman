# 626E: AFTER (observer), PR #1689 head c46eb4203

**When.** Captured 2026-10-28, 00:26–00:32 UTC, on the E2E mock-auth stack (:3001 / :8081). Tenant `tenant_7d218705360b`, signed in as Alice (owner).

**How the backend was swapped.**
- Only the backend was rebuilt from the worktree: `docker compose -f compose/docker-compose.e2e.yml build backend` (exit 0, log `/tmp/626E/obs-build.log`).
- It was then recreated with `up -d --no-deps backend`. The new image is `sha256:50eb024767fe…`, created 2026-10-28 02:27:26 SAST, and the container started at 00:27:32Z.
- LocalStack, Postgres data and the frontend were not touched.

**Proof the container runs the new code.**
- `app.jar` contains `template-packs/legal-za/pack.json` with `"version": 9`.
- It contains 0 entries matching `templates/invoice-preview`, so the Thymeleaf template is gone.

**Rules followed.**
- SQL was read-only SELECTs.
- No product code was changed.
- Bearer tokens are not stored in this directory. A grep for `eyJ…`, `token=` and `/accept/` finds nothing.

## Results

| # | Item | Status | Observed |
|---|---|---|---|
| O1a | Reconcile log line | PASS | `Reconciled template pack legal-za to v9 for tenant tenant_7d218705360b (0 new templates, 3 refreshed, 0 re-pinned after the reconcile block)` and `Pack legal-za reconciled to v9 … (internal path)` at 00:27:48Z (`01-…`) |
| O1b | `pack_install.pack_version` | PASS | `legal-za` went from **8** before the swap (`00-…`) to **9** after (`02-…`) |
| O1c | Template content markers | PASS | statement-of-account has `categoryLabel` and no `SHERIFF`; matter-closure-letter has `matter.total_vat`; fee-note-za has `DRAFT`. These three were refreshed at 00:27:48; their md5 and content_hash changed (`02-…`) |
| O1d | Firm clone untouched | PASS | `80791878-…` (engagement-letter-litigation-custom, ORG_CUSTOM): md5 `87c8f26bb643fd0324a23ec680c3bce7`, length 3186, updated_at 2026-10-27 22:33:02, all identical before and after. The other 13 untouched PLATFORM templates and the probe clone `6ce14be3` were also unchanged |
| O2a | DRAFT preview over the API | PASS | `GET /api/invoices/852969e7-…/preview` returns 200 `Content-Type: text/html` with `<!DOCTYPE html>` (details below) |
| O2b | DRAFT preview in the browser | PASS | Fee note page › **Preview** opens a new tab at `:3001/api/invoices/852969e7-…/preview` that renders the Tiptap fee note: logo, letterhead, DRAFT banner, R amounts, banking (`11-draft-preview-browser.png`) |
| O3 | SENT preview matches the sent PDF | PASS | Details below |
| O4 | SoA category labels | PASS | Details below |
| O5 | Closure letter arithmetic | PASS | Details below |
| O6a | Trust-gate message in the browser (M1) | PASS | Close-matter dialog row 1 reads `Matter trust balance is R38 000,00. Transfer to client or office before closure.` The `innerText` contains no `&nbsp;` and no `R38000.00`; its only non-ASCII character is U+00A0 (`31-…png`) |
| O6b | Trust-gate message over the API (M1) | PASS | `GET …/closure/evaluate` returns the message `Matter trust balance is R38 000,00. Transfer to client or office before closure.` with `detail.balance: 38000.00` (`30-closure-evaluate-M1.json`). The new persisted closure log `d73016e7` stores the same formatted message (`33-…`) |
| O6c | Zero-trust pass message | PASS | M2 evaluate returns `"passed":true,"message":"Matter trust balance is R0,00."` (`30-…M2.json`). The M2 close dialog shows the same text (`32-…png`); the dialog was then cancelled and M2 is still ACTIVE |
| O7 | 409 when no fee-note template | SKIPPED | Not exercised, to avoid deleting templates on the shared tenant. Covered by `InvoicePreviewIntegrationTest.shouldReturn409WhenNoFeeNoteTemplate` |

### O2a: what the DRAFT preview HTML contains
- **Present:**
  - `FEE NOTE` and `<strong>DRAFT &mdash; not yet issued</strong>`;
  - `R2&nbsp;500,00`, `Subtotal (excl. VAT): R4&nbsp;050,00`, `VAT: R607,50`, `Total Due: R4&nbsp;657,50`;
  - the letterhead: "88 Fredman Drive, Illovo Point, 3rd Floor, Sandown 2196, South Africa", Tel +27 11 555 0199, Email, Web, `VAT Registration No: 4123456789`;
  - the logo as `<img class="letterhead-logo" src="data:image/png;base64,…">`;
  - the client address, matter reference `626E-draft-f`, and banking details.
- **Absent:** `Fee Note Number:` (count 0), `Issue Date`, `Payment reference:`, `status-badge`, `BILL TO`, `Total (ZAR)` (each 0).
- File: `10-draft-…html`.

### O3: SENT preview compared with the sent PDF
- **Preview of INV-0004** (`10-sent-…html`):
  - shows `Fee Note Number: INV-0004`, `Issue Date: 27 October 2026`, `Due Date: 27 November 2026`;
  - has banking details plus `Payment reference: INV-0004`;
  - has **no** DRAFT banner;
  - shows `R2 500,00 / R375,00 / R2 875,00`, `Deeds office fees: …`, `Total Due: R4 125,00`.
- **Sent PDF.** INV-0004 › Generate Document › Fee Note › **Download PDF** (`13-…png`, `14-…pdf/.txt`).
- **Comparison.** The words of the preview and the PDF were compared ignoring whitespace and order: **IDENTICAL word multisets (177 words)** (`16-…`).
- The PDF has 1 image XObject (the logo).
- INV-0003 (PAID) preview: has the number, no DRAFT banner (`10-paid-…`).

### O4: Statement of Account category labels
- M1 (CLOSED) › Generate Statement of Account, period 2026-10-01 to 2026-10-28 › Preview & Save. This created doc `19850c59-…` (`20-…png`, `21-soa-M1.pdf/.txt/-p*.png`).
- The Disbursements Category column reads `Deeds office fees`, wrapped onto two lines in the PDF (the dialog snapshot cell is "Deeds office fees"), and `Sheriff fees`.
- `SHERIFF_FEES` and `DEEDS_OFFICE_FEES` each occur **0** times.
- The closure-time SoA `dc3f788e` reads the same (`41-…`).

### O5: closure letter arithmetic
- **Steps:** M1 reopened in the UI, then Close Matter › Continue › Override (with justification) › Close matter, with the closure letter and SoA left checked. Matter status: CLOSED.
- **New documents:** closure letter `9884ce38-…`, closure-time SoA `dc3f788e-…`, closure log `d73016e7-…`.
- **The PDF prints** (`40-…txt/.png`):
  - `Total fees billed: R10 000,00`
  - `VAT: R1 567,50`
  - `Total disbursements: R1 700,00`
- **Fresh SQL** (`42-…`):
  - TIME excl. VAT 10 000,00; VAT 1 567,50; DISBURSEMENT excl. VAT 1 700,00.
  - 10 000,00 + 1 567,50 + 1 700,00 = **13 267,50**.
  - Σ issued note totals: INV-0003 PAID 9 142,50 + INV-0004 SENT 4 125,00 = **13 267,50**.
  - These match the pre-fix expectation exactly.

## SQL run (all read-only)
- **`00-…`** (pre-swap) and **`02-…`** (post-swap):
  - `select pack_id,pack_type,pack_version,installed_at from tenant_7d218705360b.pack_install`
  - `select id,slug,source,pack_template_key,length(content::text),md5(content::text),content_hash,updated_at from tenant_7d218705360b.document_templates where pack_id='legal-za' or id::text like '80791878%' or source<>'PLATFORM'`
  - a `position(...)` marker query over the three refreshed templates.
- **`42-…`:** three queries over `invoices` / `invoice_lines` for M1, restricted to SENT/PARTIALLY_PAID/PAID; text is in the file.
- **`33-…`:** `matter_closure_log` for M1, giving the TRUST_BALANCE_ZERO message from `gate_report`.
- **Generated documents:** `generated_documents` for M1, ordered by generated_at.

## Data created or changed by this observer
- **M1 `b9174723-…`:** reopened at 00:30:31Z (closure log `cfa6b1be` now has `reopened_at`), then closed again by override at 00:31:07Z with new closure log `d73016e7-4c60-…`. It is left **CLOSED**.
- **New generated documents on M1:**
  - SoA `19850c59-24aa-462e-a766-dcef32da86fb` (manual, internal draft);
  - closure letter `9884ce38-e978-4cab-8f0e-ba23d534b806`;
  - closure SoA `dc3f788e-66a9-4403-ad06-ecdd0aa70482`.
- **INV-0004:** the PDF was downloaded only. "Save to Documents" was not clicked, so no generated_document row was created.
- **M2:** the close dialog was opened and cancelled; M2 is still ACTIVE.

## Incidental observations (not 626E defects; none block)
1. **Preview tab title is blank.** The old Thymeleaf page set `<title>Fee Note INV-0004</title>`; the Tiptap output has no `<title>`, so the browser tab shows the URL.
2. **DRAFT preview payment sentence.** It still prints "Please use your fee note number as the payment reference." although a draft has no number yet. This is template copy; the hidden `Payment reference:` line is correct.
3. **SoA dialog "Download PDF" not clickable.** Playwright reported "element is not stable" (5 s timeout). This is consistent with the dialog clipping noted in `before/`. The PDF was fetched with `GET /api/generated-documents/{id}/download` instead.
4. **Still present from before the fix:**
   - the persisted `detail.balance` is the double `38000.0`;
   - the gate grammar reads "1 tasks remain open.";
   - the closure letter "Date: 2026-10-28" is ISO-formatted;
   - the old closure log `cfa6b1be` keeps its historic unformatted message (expected: it is data written before the fix).
5. **Backend log:** 0 ERROR lines since the restart.
