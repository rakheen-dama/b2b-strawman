# 632C — UI observation (observer agent, task 632.16)

Observed: 2026-11-05 02:09–02:15 SAST. PR #1729 head `3761be4fd` (worktree `worktree-epic-632C`).
Stack: e2e mock-auth stack built from the PR head — firm frontend :3001 (container `e2e-frontend`), backend :8081, mock IDP :8090, LocalStack :4567 (presigner endpoint `http://localhost:4567`).
Portal: production build of `worktree-epic-632C/portal` (`pnpm build && pnpm start`, `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`) on :3002, stopped after the walk.
Login: firm = mock-login "Sign In" (Alice, owner); portal = magic link for `contact@acme.example.com` / `e2e-test-org` (request-link → `/auth/exchange?token=…`, no 429).
Viewports: 1440x900 and 1024x800. Browser: Playwright MCP (Chromium). PDF text: `gs -sDEVICE=txtwrite` (pdftotext not installed) → `*.txt` next to each PDF.
Subject: fee note **INV-0002** (Acme Corp, notes "Portal receipt case (632C)", PARTIALLY_PAID, total R2 300,00, one EFT payment R500,00 ref `RCPT-632C`, payment event `8f809715-…`, listener receipt `INV-0002/R1`).

## A. Firm (:3001, Alice)

- [x] **A1 Payment History row + receipt link — PASS.** Fee notes list → INV-0002. Banner "Part payment received · Paid R 500,00 · Balance due R 1 800,00". Payment History row: `Completed · Manual · EFT · RCPT-632C · R 500,00 · 5 Nov 2026 · Receipt INV-0002/R1` (button). No horizontal overflow at either width (doc scrollWidth == clientWidth). At 1024 the Reference (`RCPT-` / `632C`) and Date cells wrap to two lines — cosmetic only.
  Evidence: `firm-1440-payment-history.png`, `firm-1024-payment-history.png`.
- [ ] **A2 Click receipt link → PDF download — FAIL (environmental, not observed in browser).** Clicking "Receipt INV-0002/R1" fires the server action (POST 200) but no download; the row shows inline error "Failed to download document." The **pre-existing** Generated Documents → Payment Receipt → "Download PDF" button fails identically on the same page (control).
  Root cause (observed): `downloadPaymentReceiptAction` → `downloadGeneratedDocumentAction` → server-side `fetch(${API_BASE}/api/generated-documents/{id}/download, redirect:"follow")`. Backend returns **302 → `http://localhost:4567/docteams-dev/…/payment-receipt-inv-0002-2026-11-05.pdf?X-Amz-…`** (AWS_S3_PRESIGNER_ENDPOINT in `compose/docker-compose.e2e.yml`, since #488). Inside the `e2e-frontend` container `localhost:4567` is refused (`wget http://localhost:4567/` → Connection refused; `http://localstack:4566` → 200). So every firm-side generated-document download is broken on the e2e Docker stack, independent of this PR (PR does not touch the compose file, `lib/api/generated-documents.ts`, or `template-generation-actions.ts`). No backend ERROR lines; no browser console error for the click.
  Supplementary (API-level, NOT a UI PASS): Alice's token → `GET /api/generated-documents/39990f61-…/download` → 302; the presigned URL fetched from the host → 200 `application/pdf` = `api-firm-receipt-INV-0002-R1.pdf`: "E2E Test Organization · PAYMENT RECEIPT · Receipt INV-0002/R1 · To: Acme Corp · Fee Note Number: INV-0002 · Matter: Website Redesign · Amount received: R500,00 · Date received: 5 November 2026 · Method: EFT · Reference: RCPT-632C · Balance after this payment: R1 800,00 · [table] 5 November 2026 EFT RCPT-632C R500,00 R1 800,00 · Fee note total: R2 300,00 · Amount paid to date: R500,00 · Balance due: R1 800,00".
  Evidence: `firm-1440-receipt-download-error.png`, `api-firm-receipt-INV-0002-R1.pdf/.txt`.
- [x] **A3 "Generate receipt" path — PASS (state transitions observed; firm download blocked by the same env issue as A2).** UI path exists: Generated Documents → "Payment Receipt" row → Delete → confirm dialog "Delete Generated Document" → Delete. After reload the payment row shows **"Generate receipt"** (DB: `receipt_generated_document_id` NULL, `receipt_number` still `INV-0002/R1`). Click "Generate receipt" → row becomes **"Receipt INV-0002/R2"** (DB: `receipt_number = INV-0002/R2`, new document `29b8e0ed-…`, S3 key `…/bc5477d3-…/payment-receipt-inv-0002-2026-11-05.pdf`). Clicking "Receipt INV-0002/R2" in the firm → same "Failed to download document." (env, see A2). The regenerated R2 PDF was downloaded through the **portal** Download receipt button instead: `portal-receipt-INV-0002-R2.pdf` → "PAYMENT RECEIPT · Receipt INV-0002/R2 · … Amount received: R500,00".
  Note for orchestrator: the receipt number for the same payment changes R1 → R2 after delete + regenerate (the stored `receipt_number` is overwritten, not reused).
  Evidence: `firm-1440-after-delete-generate-receipt.png`, `firm-1024-after-delete-generate-receipt.png`, `firm-1440-after-generate-receipt.png`, `portal-receipt-INV-0002-R2.pdf/.txt`.
- Console (firm): React minified error **#418** (hydration text mismatch) on each reload of the fee-note detail page — not triaged (likely relative-time audit-trail text, e.g. "13 minutes ago"); not obviously related to the receipt cell.

## B. Portal (:3002 production build → :8081)

- [x] **B3 Payments list — PASS.** Fee notes list shows INV-0002 "Partially paid · R 2 300,00 · Balance R 1 800,00". Detail: Total R 2 300,00 / Amount paid R 500,00 / Balance due R 1 800,00; "Payments" section: `R 500,00 · 05 Nov 2026 · EFT · RCPT-632C` + **"Download receipt"** button. No overflow at 1440 or 1024.
  Evidence: `portal-1440-INV-0002-payments.png`, `portal-1024-INV-0002-payments.png`.
- [x] **B4 Download receipt — PASS.** Click opens a new tab (about:blank → presigned `…/payment-receipt-inv-0002-2026-11-05.pdf`); fetched → 200 `application/pdf` 2017 B = `portal-receipt-INV-0002-R1.pdf`: "PAYMENT RECEIPT · Receipt INV-0002/R1 · Fee Note Number: INV-0002 · Amount received: R500,00 · Method: EFT · Reference: RCPT-632C · Balance after this payment: R1 800,00 · Balance due: R1 800,00".
- [x] **B5 Download PDF serves the fee note — PASS.** Click "Download PDF" → new tab → presigned `…/fee-note-inv-0002-2026-11-05.pdf` → 200 `application/pdf` 2542 B = `portal-feenote-INV-0002.pdf`: "FEE NOTE · To: Acme Corp, 1 Long Street, Cape Town 8001 · Fee Note Number: INV-0002 · Issue Date: 5 November 2026 · Due Date: 5 December 2026 · Consultation — portal receipt case 1 R2 000,00 R300,00 R2 300,00 · Total Due: R2 300,00 · …tax invoice… section 20 of the VAT Act". Distinguishing text: fee note = "FEE NOTE"/"Total Due"/VAT s20 notice, no "Receipt"; receipt = "PAYMENT RECEIPT"/"Receipt INV-0002/R1"/"Balance after this payment".
- [x] **B6 SENT fee note has no payments block — PASS.** INV-0001 (Sent): no "Payments" heading, no "Download receipt" button; shows "Contact E2E Test Organization to arrange payment".
  Evidence: `portal-1440-INV-0001-sent-no-payments.png`.
- Console (portal): only `favicon.ico` 404.

## Verdicts

- `G88-F32 firm receipt link: FAIL` — link renders correctly (PASS at both viewports) but the click → PDF download was not observed in the browser: e2e-stack topology (presigned `localhost:4567` unreachable from the `e2e-frontend` container) breaks all firm generated-document downloads, pre-existing and unrelated to the PR's code. Needs re-observation on the Keycloak dev stack (host-run frontend :3000) or an e2e presigner fix.
- `G88-F32 portal Download receipt: PASS`
- `fee-note download serves fee note: PASS`
- `SENT shows no payments block: PASS`
- `Generate receipt: PASS` (Delete → "Generate receipt" → "Receipt INV-0002/R2" observed; R2 PDF content observed via portal; firm-side download of R2 blocked by the same env issue)

## Side effects / cleanup

- Walk mutated e2e data: listener receipt R1 document deleted; INV-0002 payment now carries `INV-0002/R2`.
- Observer portal on :3002 killed; Keycloak-stack portal restored via `svc.sh start portal` (status: healthy, :3002). Its `next dev` start rewrote `portal/next-env.d.ts` in the main checkout (`.next/types` → `.next/dev/types`, Next auto-generated) — left in place.
- Backend container `e2e-backend` was (re)started at 00:07:54Z before this walk began, by something other than this observer; a foreign portal magic-link exchange for the same contact appears in its log at 00:08:14Z.

## A2 (re-observed on the Keycloak dev stack, post-merge main 51f3e7719)

- **Observed:** 2026-11-05, 02:18–02:22 SAST, by the post-merge observer.
- **Head:** `51f3e7719` on `main` (632C #1729 on top of 632B `971e3af1d`).
- **Stack:**
  - Ran `./mvnw -q clean compile -DskipTests` (exit 0).
  - Killed the old backend :8080 listener, PID 12410 (etime 3h22m, the 632A-era process), then ran `svc.sh start backend`. The new :8080 listener is **PID 83650**, started 02:19:09 (svc PID 83495).
  - Killed the old frontend, PID 13275 (svc 13231), then ran `svc.sh start frontend`. The new :3000 listener is **PID 83754**, started 02:19:27 (svc PID 83710).
  - Gateway (334) and portal (81764) were not touched.
  - `svc.sh status`: all four healthy, before and after the walk. The backend log has 0 ERROR lines.
- **Boot (`kc-boot-log.txt`):** both tenant schemas moved from 9 to 10:
  - `Migrating schema "tenant_56399d06a773" to version "10 - phase89 go live"`
  - `Migrating schema "tenant_5039f2d497cf" to version "10 - phase89 go live"`
  
  The pack reconciled on both tenants:
  - `Reconciled template pack legal-za to v12 for tenant tenant_56399d06a773 (1 new templates, 0 refreshed, …)`
  - `Reconciled template pack legal-za to v12 for tenant tenant_5039f2d497cf (1 new templates, 0 refreshed, …)`
  
  Public schema stays at 2.
- **DB, read-only SELECTs (`kc-migrations.txt`):** `flyway_schema_history` top row = `10 phase89 go live` on both `tenant_56399d06a773` and `tenant_5039f2d497cf`. `pack_install` legal-za `pack_version = 12` on both.
- **Login:** `thandi@mathebula-test.local` (owner, Mathebula & Partners, `tenant_5039f2d497cf`), Keycloak on :3000, driven by the Playwright MCP.
- **Port hook:** I created `/tmp/.e2e-keycloak-mode` for the walk and removed it afterwards.
- **Viewports:** 1440x900 and 1024x800.
- **Subject:** fee note **INV-0001**:
  - Sipho Dlamini, PAID, total R 4 585,00.
  - Completed manual EFT payment of R 4 585,00, ref `EFT/SIPHO-001`, 29 Oct 2026, payment event `625aed8d-…`. This payment pre-dates the phase, so it had no receipt. The other fee note with a completed payment is INV-0002 (Moroka Family Trust, mock provider); it was not touched.
  - The other tenant (`tenant_56399d06a773`) has no payments.

### Generate receipt on a pre-phase payment
1. On the fee note detail page, the Payment History row read `Completed · Manual · EFT · EFT/SIPHO-001 · R 4 585,00 · 29 Oct 2026 ·` **[Generate receipt]**. The Cancelled mock row shows `—`. There is no horizontal overflow (scrollWidth 1440 == clientWidth). Screenshot: `kc-firm-1440-INV-0001-generate-receipt.png`.
2. Clicking "Generate receipt" changed the row to the link **"Receipt INV-0001/R1"**, with no error alert. Screenshot: `kc-firm-1440-INV-0001-after-generate-receipt.png`.
   - Backend log at 00:20:33Z: `Generated PDF: template=payment-receipt … size=3070bytes`, then `Generated payment receipt INV-0001/R1 for invoice=4a10f00a-… paymentEvent=625aed8d-… (lazy, posting_member)`.
   - DB (read-only): `receipt_number = INV-0001/R1`, `receipt_generated_document_id = d511c17b-…`, `payment-receipt-inv-0001-2026-11-05.pdf`.

### Firm receipt link → PDF
1. At 1440x900, clicking "Receipt INV-0001/R1" fired a browser download: `blob:http://localhost:3000/…`, suggested filename `INV-0001-R1.pdf`. Saved as `kc-firm-receipt-INV-0001-R1-1440.pdf` (PDF 1.6, 3.1 KB). The page shows no error after the click (`kc-firm-1440-INV-0001-after-receipt-download.png`).
2. Text (`gs -sDEVICE=txtwrite` → `kc-firm-receipt-INV-0001-R1-1440.txt`):
   ```
   Mathebula & Partners
   12 Rissik Street, 3rd Floor, Johannesburg 2001, South Africa
   Tel: +27 11 555 0100
   Email: accounts@mathebula-test.local
   Web: https://mathebula-test.local
   PAYMENT RECEIPT
   Receipt INV-0001/R1
   To: Sipho Dlamini
   Fee Note Number: INV-0001
   Matter: Sipho Dlamini v. Standard Bank (civil)
   Payment received
   Amount received: R4 585,00
   Date received: 29 October 2026
   Method: EFT
   Reference: EFT/SIPHO-001
   Balance after this payment: R0,00
   Payments on this fee note
   Date Method Reference Amount Balance after
   29 October 2026 EFT EFT/SIPHO-001 R4 585,00 R0,00
   Fee note total: R4 585,00
   Amount paid to date: R4 585,00
   Balance due: R0,00
   ```
   Page 1 is rendered in `kc-firm-receipt-INV-0001-R1-p1.png`. It has the letterhead logo block and firm name, contact lines, "PAYMENT RECEIPT", the payment row table and the balance.
3. At 1024x800 I reloaded the page. The Payment History row shows "Receipt INV-0001/R1" with no page overflow (1024 == 1024). The Reference and Date cells wrap onto 2–3 lines, which is cosmetic (`kc-firm-1024-INV-0001-receipt-link.png`). Clicking the link downloaded `INV-0001-R1.pdf` again, saved as `kc-firm-receipt-INV-0001-R1-1024.pdf`. Its extracted text is identical to the 1440 PDF (`.txt` alongside).
4. The only browser console error during the walk was `favicon.ico` 404 on Keycloak :8180. The React #418 hydration error seen on the e2e stack did **not** appear here.

### Settings › Documents
- Settings › Documents › Templates lists **"Payment Receipt"** in the "OTHER" group: "Client-facing receipt for one payment on a fee note, with the payments to date and the balance due." The row reads Tiptap · Fee note · Platform · Active. Template id `a7bb45d8-…`.
- Evidence: `kc-settings-documents-payment-receipt-1440.png`, `kc-settings-documents-feenote-group-1440.png`, and `kc-settings-documents-payment-receipt-cols-1440.png` (scrolled right to show the columns).
- Cosmetic issue, not caused by this PR: the "OTHER" templates table is 1621 px wide inside an 856 px container. The long Offer to Purchase description forces it to scroll horizontally, so Format/Entity/Source/Status/Actions sit off-screen at 1440 until you scroll.

### Side effects
- Through the UI, I created one receipt document (`d511c17b-…`, INV-0001/R1) on the KC tenant `tenant_5039f2d497cf` and stamped `receipt_number` on payment `625aed8d-…`. This is the intended lazy-generate path.
- No SQL writes, no source edits, nothing committed.

### Verdicts (KC)
- `G88-F32 firm receipt link → PDF (KC): PASS`
- `Generate receipt on a pre-phase payment (KC): PASS`
- `payment-receipt-za under Settings › Documents: PASS`
- `KC tenants at tenant V10: tenant_56399d06a773 = 10, tenant_5039f2d497cf = 10 (public = 2); legal-za pack v12 on both`
