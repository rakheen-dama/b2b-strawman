# 626D: legal-za pack content (letterhead, banking, SoA payment instructions, reconcile) — BEFORE

Captured 2026-10-27 between 21:45 and 21:55 UTC on the E2E mock-auth stack (:3001 / :8081). The backend container was started at 20:15:34 UTC from merged 626B code; the frontend was rebuilt at 21:33 UTC from merged 626C code. Worktree HEAD is `cdb3e819f`. Logged in as Alice (owner), org `e2e-test-org`, tenant `tenant_7d218705360b`.

No product code was changed. All SQL was read-only SELECTs. Every write was made through the UI.

Auth for the API: mock-IDP `POST http://localhost:8090/token` with `{"userId":"user_e2e_alice","orgId":"e2e-test-org","orgSlug":"e2e-test-org","orgRole":"owner"}`, then `.access_token` used as a Bearer token.

PDF inspection uses the same recipe as 626B/before:
- Ghostscript `txtwrite` for the text dump;
- `png16m` page images at 80 dpi (110 dpi for the fee note);
- a pixel scan of the `ppmraw` render;
- zlib decompression of the content streams to list the `rg`/`RG` colours and count `/Subtype /Image` XObjects.

## Precondition
- `GET /api/settings/letterhead` returns 200 with 15 of the 16 fields filled (`00a-…json`). `addressLine3` is null, as briefed:
  - 88 Fredman Drive / Illovo Point, 3rd Floor / Sandown / 2196 / South Africa;
  - +27 11 555 0199; accounts@ndlovu-attorneys.co.za; https://www.ndlovu-attorneys.co.za;
  - Standard Bank / 051001 / 0012345678 / Cheque / "Please use your fee note number as the payment reference.";
  - Sipho Ndlovu, Managing Partner.
- `GET /api/settings` (`00b-…json`, presign redacted) shows a `logoUrl` is present.
- **Two deviations from the brief:**
  - **`brandColor` is `#1b3a4b`, not `#0f766e`.** The backend log shows another actor changed it at 21:37:40 to 21:37:46 UTC (`#1a8f6e`, then `#1b3a4b`), along with tax-settings saves and a billing-rate creation. That was before this session started, probably a Playwright spec run.
  - **`taxRegistrationNumber` is null**, so every "VAT Reg"/"VAT Registration No" line below is empty because of the data as well as the template.

## Steps
1. **Engagement letters (UI).** For each letter: matter › Generate Document › template › default clauses › Next: Preview (screenshot) › Download PDF.
   - **E1:** "Engagement Letter — Litigation" on "Ndlovu — transition repro mvr15e76ef9a" (`072a0d56-…`, client Sipho Ndlovu).
   - **E2:** "Engagement Letter — General" on the same matter.
   - **E3:** "Engagement Letter — Conveyancing" on "Khumalo Property Holdings — Erf 1234 Sandton Transfer" (`02b5a264-…`).
2. **Fee note (UI).** Fee note INV-0001 (`ed1c7f17-…`, SENT, 1 counsel-fee disbursement line, R3 500,00 + VAT R525,00 = R4 025,00; the only invoice on the tenant).
   - Generate Document lists "Invoice Cover Letter | Fee Note". Chose **Fee Note**. It opens straight on the preview with no clause step. Screenshot `11-…png`, then Download PDF → `f1-fee-note-INV-0001.pdf`.
   - Then clicked the page's separate **Preview** button. It opens a new tab at `/api/invoices/{id}/preview` (`12-…png`). The same HTML was fetched from the backend → `f2-…html`.
3. **Statement of account (UI).** On "Ndlovu — transition repro": Generate Statement of Account, default period 2026-10-01 to 2026-10-27.
   - "Share on the client portal" and "Email the client now" were both **unchecked**, so it was saved as an internal draft.
   - Clicked Preview & Save (`30-…png`, `31-…png`) and saved the iframe body (`s1-…excerpt.html`).
   - The saved PDF was fetched through `GET /api/generated-documents/abbbb7d4-…/download` → `s1-statement-of-account-ndlovu.pdf`.
4. **Pack/reconcile state.** Read-only SQL (`40-…`, `41-…`), a hash comparison (`42-…`) and the backend startup log (`43-…`).

## Observations (REPRODUCED = defect present)

| # | Check | Observed | Verdict |
|---|---|---|---|
| D1 | Engagement letters carry the firm letterhead | All 3 PDFs (`e1/e2/e3-….txt`, `-p1.png`) start with H1 "E2E Test Organization", then the letter title. The PDFs have **0 image XObjects**, so there is no logo; the templates do not reference `org.logoUrl`. They contain **no** address (Fredman/Illovo/Sandown/2196), phone, email, website or VAT line: a grep of the dumps finds none of these values. The headings are brand `#1b3a4b`, so 626B's brand CSS is live. | **REPRODUCED** |
| D2 | Engagement-letter signatory block | Every letter ends with "Yours faithfully," / "E2E Test Organization" / footer "Test Footer 1793137061590". There is no signatory name or designation, so Sipho Ndlovu, Managing Partner does not appear. In E1 and E2 "Sipho Ndlovu" appears only as the *client*. | **REPRODUCED** |
| F1 | Fee-note letterhead and client block | The PDF has "FEE NOTE", then "From: E2E Test Organization", then "VAT Registration No:" with an empty value, then "To: Apex Holdings mvr15e76ef9a", then "Client VAT Number: 9012345678". There is no firm address, phone, email or website, and **no client address block**, although the customer row has 12 Rivonia Road / Sandton / 2196. `fee-note-za.json` has no `customer.address` variable. The template's variables are: `customer.name customerVatNumber invoice.{currency,dueDate,invoiceNumber,issueDate,subtotal,taxAmount,total} lines org.{documentFooterText,logoUrl,name,taxRegistrationNumber} project.{name,referenceNumber}`. | **REPRODUCED** |
| F2 | Fee-note banking | "Payment Details: Please use the fee note number as your payment reference. **Banking details are available on request** or as provided in your engagement letter. …" This is a literal text node in `fee-note-za.json`. No Standard Bank / 051001 / 0012345678 is printed. | **REPRODUCED** |
| F3 | Other fee-note fields | Due Date **is** present ("26 November 2026"). "Matter Reference:" is printed **empty**. The website is absent. The logo is absent: the template has `org.logoUrl` as its first paragraph, but openhtmltopdf failed 3× with `IO problem for http://localhost:4567/…logo.png … ConnectException` (`f1-logo-backend-log.txt`), the same container-unreachable presigned URL as 626B R5. The PDF has 0 image XObjects. | partial (see notes) |
| M8 | Currency amounts wrap mid-number | **REPRODUCED.** The fee-note line table's VAT cell renders "**R525,**" on one line and "**00**" on the next. Visible in `f1-fee-note-INV-0001-p1.png`; the text dump shows `R525,` then `00`. "R3 500,00" and "R4 025,00" do not wrap. The SoA disbursement table has the same defect: "R150," / "00" and "R0," / "00" in `s1-….txt` lines 21-22. | **REPRODUCED** |
| S1 | SoA Payment Instructions | The preview HTML is `<h3>Payment Instructions</h3><p></p><p>Test Footer …</p>`. The PDF text goes straight from "Payment Instructions" to "Test Footer 1793137061590". No banking details render. The template binds the whole map `{variable org.bankingDetails}`. `StatementOfAccountContextBuilder.java:226-229` has a comment saying it renders "" "until 626D moves it to the leaves". | **REPRODUCED** |
| S2 | SoA disbursement category | The Category column prints the raw enum **`SHERIFF_FEES`**. The loopTable column is `{"key":"category"}` over `DisbursementStatementDto.category` (a `DisbursementCategory` enum), with no label mapping. | **REPRODUCED** |
| S3 | SoA header | H1 "E2E Test Organization", "VAT Reg:" empty, "File reference:" empty. No firm address or contact block. | REPRODUCED (letterhead) |

## Fee-note render paths (which one uses `fee-note-za.json`)
- **Downloaded PDF (exercised, `f1`).** Invoice detail › Generate Document › "Fee Note" renders the Tiptap pack template. The DB proves which one: `generated_documents` row `3e3619ed-86e5-4c81-8ff8-1b4e33f97e0b` points to template slug **`fee-note`**, `pack_template_key` **`fee-note-za`**, entity INVOICE `ed1c7f17-…`. Rendering is Tiptap to HTML, then openhtmltopdf.
- **Emailed PDF attachment (not exercised).** `InvoiceEmailEventListener` calls `GeneratedDocumentService.resolveDefaultInvoiceTemplate()` (`GeneratedDocumentService.java:539-559`). For legal-za that returns only `findByPackIdAndPackTemplateKey("legal-za","fee-note-za")`, so it is the same template and the same renderer.
- **"Preview" button (exercised, `f2`, `12-…png`).** It calls `GET /api/invoices/{id}/preview` → `InvoiceRenderingService.renderPreview()` → **Thymeleaf `templates/invoice-preview.html`** (`InvoiceRenderingService.java:137`). This path is HTML only and does **not** use `fee-note-za.json`; it is the preview 626E retires. It shows "Fee Note: INV-0001", "BILL TO" with the client name, email and matter, the lines and the VAT breakdown, and no banking or letterhead.

## Pack and reconcile state on this tenant
- **`pack_install` legal-za** (DOCUMENT_TEMPLATE):
  - id `c569873d-f993-445a-bd58-6e0597f82138`;
  - **pack_version `7`**, item_count 17, installed 2026-10-27 18:22:45 UTC.
  - `pack_install` has **no hash or content column**: its columns are id, pack_id, pack_type, pack_version, pack_name, installed_at, installed_by_member_id, item_count.
  - Classpath `template-packs/legal-za/pack.json` is also **version 7**, and it has **no `reconcile` block**.
- **Templates** (`41-…`): all 17 legal-za rows have the same shape:
  - `source=PLATFORM`, `pack_id=legal-za`, `source_pack_install_id=c569873d-…`;
  - `css` is null and `content_hash` is set;
  - `created_at = updated_at` (18:22:45, never edited).
  - The fee note's slug is **`fee-note`** (pack key `fee-note-za`).
  - There are **no clones**: `source_template_id` is null everywhere.
  - The only ORG_CUSTOM row is the 626B probe `probe-626b-letterhead-keys`.
- **Pristine check** (`42-…`):
  - The installer's hash is `sha256(canonical JSON of {"content":<content>,"css":<css>})` with sorted keys and compact output (`TemplatePackInstaller.computeContentHash`, `ContentHashUtil`). With css null it is reproduced as `jq -cjS '{content: .}' | shasum -a 256`.
  - For **all 17** templates, the stored `content_hash` equals the hash of the DB's current `content`, which equals the hash of the classpath file. All are **pristine and identical to the v7 classpath JSON**. The jq recipe reproduces the Java hash exactly, which validates the method.
- **Startup log** (`43-…`):
  - `PackReconciliationRunner Running pack reconciliation for 1 tenants`, then `PackInstallService Pack legal-za already installed (internal path), skipping`, then `… checked 1 tenants, 1 succeeded, 0 failed`.
  - `TemplatePackInstaller.reconcile()` returned false because the classpath version is not greater than the installed version (7 ≤ 7).
- **Implication for 626D.** A legal-za bump to `version: 8` will open the gate on this tenant. `refreshIfUnmodified` will then rewrite all 17 pristine templates, including the 3 engagement letters, `fee-note-za` and `statement-of-account`, and re-pin their hashes. Nothing here is tenant-edited, so nothing would be skipped.

## Other observations (not investigated)
- The e2e logo is unreachable from the container: the presigned URL points at `localhost:4567` (626B R5, still present). Any template that adds `org.logoUrl` will show no logo in e2e PDFs, which will matter for 626D's "after" evidence.
- Each first render logs 14 `PDType1Font Using fallback font LiberationSans` WARNs. This is a pre-existing font-fallback message, not an error.
- Download PDF fired a download event on every attempt this time; there was no 626B-style flakiness.

## State left changed on the stack
- New `generated_documents` rows, all created by Download PDF with no `document_id`:
  - `f6f57d62-…` (litigation letter), `b569096b-…` (general letter), `a1800218-…` (conveyancing letter), `3e3619ed-…` (fee note INV-0001).
- New SoA internal draft `abbbb7d4-9a8a-4fbe-95ad-bdfe868c41c9`, saved to the "Ndlovu — transition repro" matter's documents (`document_id cc3f5a05-…`). It was not shared and not emailed; Mailpit's newest message is from 18:41 UTC.
- Settings, letterhead, templates and packs were not changed.

## Files
- `00a-…`, `00b-…`: precondition.
- `01/02/03-…png`, `e1/e2/e3-*.{pdf,txt,-pN.png,-pixel-scan.txt}`: engagement letters.
- `10/11/12-…png`, `f1-fee-note-INV-0001.*`, `f1-logo-backend-log.txt`, `f2-…thymeleaf.html`, `20-backend-log-generation-window.txt`: fee note.
- `30/31-…png`, `s1-*`: statement of account.
- `40-…` to `43-…`: pack and reconcile state.

Presigned `X-Amz-Credential` and `X-Amz-Signature` values are redacted in every saved file; a grep confirms none remain.
