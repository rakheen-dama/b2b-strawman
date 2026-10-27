# 626B: brand colour and letterhead context keys in generated documents — AFTER

Captured 2026-10-27 between 20:15 and 20:25 UTC on the E2E mock-auth stack (:3001 / :8081). Logged in as Alice (owner), org `e2e-test-org`, tenant `tenant_7d218705360b`. The same matter, probe template and methods as `before/` were used.

No product code was changed. All SQL was read-only SELECTs. Nothing was committed to the PR worktree. The writes listed under "State left changed" were made through the UI or the API.

## Build under test
- Only the e2e backend was rebuilt from the PR worktree `/Users/rakheendama/Projects/2026/worktree-epic-626B` at HEAD **`ea6cd6f67`** (PR #1686) with `docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps backend`.
  - The Dockerfile is multi-stage (`mvnw package` inside the builder) and `.dockerignore` excludes `target`, so the concurrent host `mvn verify` in `backend/target` was not touched.
- **Image `sha256:f4a21c156aac610539470dacde04f0bedf9ed6a825bb40d961179ed134994a60`**, created 20:15:33 UTC. Container `e2e-backend` started 20:15:36 UTC and reported health UP at 20:15:54 UTC. The image before the rebuild was `sha256:21fe1acb47ec…`.
- **How we know it runs the PR code:**
  1. `/app/app.jar` in the container contains `io/b2mash/b2b/b2bstrawman/template/BrandColour.class`, a class the PR adds, timestamped 10-27-2026 20:15.
  2. `GET /api/templates/variables?entityType=PROJECT` lists `org.signatory.name` and the other new keys.
  3. `docker logs e2e-backend` since the container started has **0** lines matching `brand-color` and 0 `css-parse` lines across every render below. Before the fix, every render logged this warning.
- The frontend (`docteams-e2e-frontend` `818b8e518283`) was not rebuilt. 626B is backend-only, and the picker reads the backend metadata endpoint.

## Precondition (unchanged from before/)
- `GET /api/settings/letterhead` returns 200 with all 16 fields filled (12 Rivonia Road … South Africa; +27 11 555 0142; info@ndlovu-attorneys.co.za; https://www.khumalo-attorneys.co.za; First National Bank / 250655 / 62812345678 / Cheque / "Please use your fee note number as the payment reference."; Thandiwe Khumalo, Director). See `00a-…`.
- `GET /api/settings` returns `brandColor: "#0f766e"` (`00b-…`).

## Steps
1. **O1.** Opened the matter "Khumalo Property Holdings — Erf 1234 Sandton Transfer mvr15e76ef9a" (`02b5a264-…`) on :3001.
   - Clicked Generate Document › "Engagement Letter — Conveyancing", kept the 5 default clauses, and clicked Next: Preview.
   - Read `getComputedStyle` for every h1/h2/h3/hr inside the preview iframe (`o1-preview-iframe-computed-styles.txt`) and took a screenshot (`01-…png`).
   - Clicked Download PDF. Playwright captured the download event, saved as `o1-engagement-letter-conveyancing.pdf`.
   - Analysed the PDF with Ghostscript `txtwrite`, `png16m` at 80 dpi and `ppmraw` at 80 dpi, using the same `scan.py` pixel and fill-operator scan as before/. A text-run and colour extractor plus a non-text-operator dump were added.
2. **O2.** On the same matter: Generate Document › "626B probe — letterhead keys" (`6ce14be3-…`).
   - Captured the **UI preview dialog** iframe text (`o2-probe-preview-ui-iframe-text.txt`) and a screenshot (`02-…png`).
   - Clicked Download PDF → `o2-probe-letterhead-keys.pdf`.
   - Separately called **`POST /api/templates/{id}/preview`** with `{"entityId":"02b5a264-…"}` → `o2-probe-preview-api.html` and `-meta.json`.
3. **O3.** Opened Settings › Documents › Templates › New (Primary Entity Type = Project; the form was **not** submitted) › Insert variable.
   - Searched for "org.", "address", "bank", "signatory", "principal", "phone" and "VAT" (`o3-variable-picker-ui-searches.txt`).
   - Took screenshots `03-…`, `03b/c/d-…` (the Organization group scrolled top, middle and end) and `04-…` ("principal" finds nothing).
   - Called `GET /api/templates/variables?entityType=PROJECT|CUSTOMER|INVOICE` (`o3-api-variables-*.json`).
4. **O4.** On the same matter: Generate Statement of Account.
   - Both "share on portal" and "email" were left **unchecked** (internal draft).
   - The dialog's default period was **2026-10-28 → 2026-10-27**, which fails validation; see Other observations. Period start was set to 2026-10-01, the same as before/R4. Period end was 2026-10-27.
   - Clicked Preview & Save. Captured `05-…png`, the iframe HTML (`o4-statement-of-account-preview-body.html`) and computed styles.
   - The backend logged generated document `ece4ac02-139d-41d7-a2a1-715d273d296a`. Its PDF was fetched as in before/: `GET /api/generated-documents/{id}/download` returns a 302 to the presigned URL, which the host followed → `o4-statement-of-account-khumalo.pdf`.
5. **O5.** Called `POST /api/templates/{id}/preview` against the Khumalo matter for the pack "Matter Closure Letter" (`30854651-…`), the same probe as before/R2. As a bonus, also previewed the other two `org.principal_attorney` consumers: deed-of-transfer (`c47c8399-…`) and bond-cancellation-instruction (`629a7ecc-…`). Outputs: `o5-*-preview-api.html`.
6. **O6 was SKIPPED.** The only way to clear the brand colour without SQL is Settings › Firm › General › Branding › Save Settings.
   - That button saves the whole settings form (currency, tax, logo, footer, brand) and writes audit events, so it would leave state changed even after restoring `#0f766e`. This breaks the brief's "without leaving state changed" condition.
   - The no-brand and invalid-brand fallback to `#1a1a2e` is covered only by the PR's unit tests (`TiptapRendererTest`). It was **not observed** on the stack.

## Observations

| # | Check | Observed | Verdict |
|---|---|---|---|
| O1 | Brand colour in a Tiptap PDF (brand `#0f766e`) | All 6 heading runs are filled `0.05882 0.46275 0.43137` = **`#0f766e`**: H1 "E2E Test Organization", H2 "Engagement Letter — Conveyancing", H3 "Transaction Details", "Scope of Services", "Terms and Conditions" and "Acceptance" (page 2). The PDF's only fill colours are brand and `0.2` (`#333`, body text and bullets); `#1a1a2e` is gone. Pixel scan: brand-teal 5,458 / 292 / 1,569 px on pages 1–3, and **0** navy pixels (before: 0 teal, 3,889 + 292 navy). **hr**: on page 1 (y≈708) and page 3 (y≈756), all four border trapezoids are filled in brand, one colour on every side, so the rule is **solid brand**. Before, it was a two-tone inset `#1d1d1d`/`#333`. There are no stroke ops. The preview iframe agrees: h1–h3 `rgb(15,118,110)` and hr `border-top: rgb(15,118,110) solid`. Backend log: **0** `brand-color` / `css-parse` warnings. The engagement letter has no table; table headers were checked on the SoA (O4). | **PASS** |
| O2 | Letterhead `org.*` keys resolve | The UI preview, the API preview HTML and the generated PDF text all show the same values: `org.address` = "12 Rivonia Road, Illovo Point, 3rd Floor, Illovo, Sandton 2196, South Africa"; `org.phone` = "+27 11 555 0142"; `org.email` = "info@ndlovu-attorneys.co.za"; `org.website` = "https://www.khumalo-attorneys.co.za"; `bankingDetails.bankName` = "First National Bank", `.branchCode` = "250655", `.accountNumber` = "62812345678", `.accountType` = "Cheque", `.referenceNote` = "Please use your fee note number as the payment reference."; `signatory.name` = "Thandiwe Khumalo", `.designation` = "Director"; **`org.principal_attorney` = "Thandiwe Khumalo"**. `org.addressLines` renders **`<>`** (empty), and no `{` or `[` text appears anywhere (grep count 0 in the PDF text). The probe's headings are brand-filled in the PDF (4,268 teal px, 0 navy). The probe has no whole-group binding, so the whole-map case was observed on the SoA (O4). The preview `validationResult` is still `{"allPresent":true,"fields":[]}`. | **PASS** |
| O3 | The picker offers the new keys; groups and the alias are hidden | UI (PROJECT) Organization group: the 5 old keys plus **13 new**, each with its label: `org.taxRegistrationNumber` "VAT Registration Number", `org.address` "Firm Address", `org.phone` "Firm Phone", `org.email` "Firm Email", `org.website` "Firm Website", `org.bankingDetails.bankName` "Bank Name", `.branchCode` "Branch Code", `.accountNumber` "Bank Account Number", `.accountType` "Bank Account Type", `.referenceNote` "Payment Reference Note", `org.signatory.name` "Signatory Name", `org.signatory.designation` "Signatory Designation". "principal" finds **No variables found**. "bank" and "signatory" find only the leaf keys, and "address" finds `org.address` but not `org.addressLines`. API: PROJECT, CUSTOMER and INVOICE each return the same 17 org keys (the PROJECT and INVOICE org groups are byte-identical), and **0** occurrences of `org.principal_attorney`, `org.addressLines`, `org.bankingDetails` or `org.signatory`. | **PASS** |
| O4 | SoA Payment Instructions; headings brand-coloured | New SoA `ece4ac02-…`. The HTML ends `<h3>Payment Instructions</h3><p></p><p></p>`, and the PDF text ends at "Payment Instructions". There is **no** `{bankName=` or `{` text (grep count 0), the same as before: it stays empty and does not print a map. All 9 headings (H1, H2 and the H3s To / Matter / Professional Fees / Disbursements / Trust Activity / Summary / Payment Instructions) are filled **`#0f766e`**. Pixel scan: 8,320 + 4,230 teal px and 0 navy (before: 0 teal, 3,090 + 1,092 navy). All 7 hr rules are solid brand (16 + 12 brand trapezoid fills). **Table headers stay neutral**: text `0.2` (`#333`), background `0.96078` (`#f5f5f5`), bottom border `0.86667` (`#ddd`). The preview iframe gives th `color rgb(51,51,51)` and `bg rgb(245,245,245)`. | **PASS** |
| O5 | Matter Closure Letter `org.principal_attorney` | "Yours faithfully," → **"Thandiwe Khumalo"** → "E2E Test Organization". Against before/`r2-matter-closure-letter-preview-api.html`, the body diff is exactly the empty `<p></p>` changing to `<p>Thandiwe Khumalo</p>`, plus the presigned date. Bonus: deed-of-transfer and bond-cancellation-instruction also render "Thandiwe Khumalo" in the signature block. | **PASS** |
| O6 | Default heading colour when no brand is set | Not run; see step 6 for the reason. | **SKIPPED** (not observed) |

## Other observations (outside 626B's scope; listed for triage)
- **Generated-document S3 key collision overwrites earlier PDFs (pre-existing).**
  - The key is `<template-slug>-<matter-slug>-<date>.pdf`, so every generation of the same template on the same matter on the same day writes to one object.
  - Proof: `GET /api/generated-documents/0f59ac2d-…/download` (before/R4's SoA; the DB says 3,345 bytes) now serves **this** run's SoA (3,334 bytes, the same sha256 as `o4-…pdf`). Its `documents` row `eed30b82-…` points to the same key. See `other-s3-key-collision.txt` and `00c-…`.
  - The engagement letter (3 rows) and the probe (2 rows) share keys the same way.
  - Effect: historical generated documents silently change content, and so do saved matter documents.
- **The SoA dialog default period can be invalid.** `statement-of-account-dialog.tsx:134` defaults the start to *latest statement generated date + 1 day*. A second statement on the same day therefore opens with start 2026-10-28 > end 2026-10-27 and the error "Period end must be on or after period start" (the invalid-period state was seen in an intermediate screenshot that the post-save `05-…png` overwrote; the period values `2026-10-28`/`2026-10-27` were read from the inputs via Playwright). The user must edit the start by hand.
- **The picker search does not match labels in every case.** "VAT" does not find `org.taxRegistrationNumber` ("VAT Registration Number"), but fuzzily matches `conveyancing_type` and `advocate_name`. Frontend search behaviour, not 626B.
- **The SoA still prints "VAT Reg:" empty and "File reference:" empty**, as before/ and 626A.
- **The logo is still absent from the PDFs.** openhtmltopdf logs `IO problem for http://localhost:4567/…` (connection refused from inside the container) on the probe and SoA renders. This is out of scope for 626B as briefed and is not claimed.
- **The preview `validationResult` still reports `allPresent: true`** for every template. This is unchanged and harmless now that the keys resolve.
- Browser console during the session: 2× Radix "Missing Description" warnings and 1× tiptap "Duplicate extension names ['link','underline']". There were no errors.

## State left changed on the stack
- **The e2e backend container now runs the PR image** `sha256:f4a21c156aac…` (ea6cd6f67), not merged main.
- **New `generated_documents` rows** on the Khumalo matter:
  - `cff7cec2-a55e-44d3-bdd0-40404ab3b823`: engagement letter, created by Download PDF, no `document_id`;
  - `15fdd06a-33d8-4623-8f3d-d440867838ad`: probe, created by Download PDF, no `document_id`;
  - `ece4ac02-139d-41d7-a2a1-715d273d296a`: statement of account, an internal draft saved to the matter's documents (`document_id 9b415576-1dc9-43d6-b1d5-e6e232e8f9df`), not shared and not emailed.
- Because of the key collision above, the S3 objects behind the before/ rows `62b5c699-…` (engagement letter), `6448a4e4-…` (probe) and `0f59ac2d-…`/`eed30b82-…` (SoA) were **overwritten** with this run's PDFs. The before/ PDFs survive only in the before/ evidence folder.
- Probe template `6ce14be3-…` was left in place. Letterhead, brand colour, logo and settings were **not** changed. The template form was not submitted.
- The Playwright files from this session in the main repo's `.playwright-mcp/` (2 PDFs, 5 snapshots, 1 console log) were deleted.

## Files
- `00a-alice-get-letterhead.txt`, `00b-alice-get-settings.json`, `00c-db-generated-documents-khumalo.txt` (read-only SELECT): precondition and state.
- `01-engagement-letter-preview-dialog.png`, `o1-preview-iframe-computed-styles.txt`, `o1-engagement-letter-conveyancing.pdf`, `.txt`, `-p1..p3.png`, `o1-pixel-scan.txt`, `o1-backend-log.txt`: O1.
- `02-probe-preview-dialog.png`, `o2-probe-preview-ui-iframe-text.txt`, `o2-probe-preview-api.html`, `-meta.json`, `o2-probe-letterhead-keys.pdf`, `.txt`, `-p1.png`, `o2-pixel-scan.txt`, `o2-backend-log.txt`: O2.
- `03-…png`, `03b/03c/03d-…png`, `04-variable-picker-search-principal.png`, `o3-variable-picker-ui-searches.txt`, `o3-api-variables-org-{PROJECT,CUSTOMER,INVOICE}.json`, `o3-api-variables-PROJECT-full.json`: O3.
- `05-soa-preview-dialog.png` (post-save dialog; the iframe area screenshots blank and the dialog content is clipped at the right edge — identical to before/`07-…png`, so pre-existing; colour evidence is the iframe HTML/computed styles + PDF), `o4-statement-of-account-preview-body.html`, `o4-statement-of-account-khumalo.pdf`, `.txt`, `-p1..p2.png`, `o4-pixel-scan.txt`, `o4-backend-log.txt`: O4.
- `o5-matter-closure-letter-preview-api.html`, `o5-deed-of-transfer-preview-api.html`, `o5-bond-cancellation-instruction-preview-api.html`: O5.
- `other-s3-key-collision.txt`: the key-collision finding.

Presigned `X-Amz-Credential` and `X-Amz-Signature` values are redacted in every saved file. A grep confirms only `=REDACTED` remains.
