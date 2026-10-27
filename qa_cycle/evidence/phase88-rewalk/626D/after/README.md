# 626D: legal-za pack content (letterhead, banking, SoA payment instructions, reconcile) — AFTER (task 626.9)

Captured 2026-10-27 between 22:30 and 22:46 UTC on the E2E mock-auth stack (:3001 / :8081), on the existing tenant `tenant_7d218705360b` (org `e2e-test-org`, logged in as Alice, owner).
- The backend image was rebuilt from PR #1688 head **`837e2dcdd`** (worktree-epic-626D) at 22:34 UTC. Only the backend was rebuilt (`build backend`, then `up -d --no-deps backend`), so LocalStack and the logo bytes were kept.
- The frontend container was left as it was (626C build).

No product code was changed. All SQL was read-only SELECTs. Every write was made through the UI (Playwright MCP). The read-only API calls were `GET /api/settings*`, `POST /api/templates/{id}/preview` and `GET /api/generated-documents/{id}/download`.

PDF inspection reuses the before/ recipe through a small script in /tmp (not committed):
- Ghostscript `txtwrite` for the text dump;
- `png16m` page images at 80 dpi (110 dpi for the fee note; 200 dpi for the line-row close-up);
- a pixel scan of the page-1 `ppmraw` render;
- zlib content-stream decode to list the `rg` colours and count `/Subtype /Image` XObjects.

## Steps
1. **Pre-rebuild (v7 backend, UI).**
   - **a.** Settings › Fees & Trust › Tax: set Tax Registration Number `4123456789` and clicked Save Settings. The page showed "Tax settings updated." (`01a-…png`), and `GET /api/settings` returned `taxRegistrationNumber: 4123456789`.
   - **b.** The firm edit of "Engagement Letter — Litigation" — **deviation from the brief:** PLATFORM templates are read-only in the UI (`settings/documents/templates/[id]/edit/page.tsx:38`: `readOnly = !isAdmin || template.source === "PLATFORM"`; the editor is `contenteditable=false` and has no Save). The only firm-edit path in the product is **Clone & Customize**. That created ORG_CUSTOM `80791878-6e4d-47fc-acbc-9ec04a2a1c54` "Engagement Letter — Litigation (Custom)" (`pack_id=legal-za`, `pack_template_key=engagement-letter-litigation`, `source_template_id=bc89ee05-…`, no install id, no hash). I appended " Firm edit 626D." to the Acceptance paragraph and clicked Save (`01b-…png`).
     - This exercises R-8 (a clone present at reconcile time) rather than the "Reconcile skipped (edited)" branch, which a UI-only actor cannot reach.
     - Saving surfaced a **pre-existing defect** (see Defects).
   - **c.** SELECTs (`01c-db-pack-install-v7.txt`, `01c-db-templates-v7.txt`, `01c-db-litigation-platform-and-clone-v7.txt`):
     - legal-za `pack_version 7`;
     - all 8 in-scope PLATFORM rows have `updated_at = created_at` (18:22:45) and no `org.signatory`.
2. **Rebuild.** The build took about 1 min. The container was recreated at 22:34:16 UTC, and health was UP.
   - Image check (`02-image-pack-json-v8.txt`): `template-packs/legal-za/pack.json` in `/app/app.jar` has `"version": 8` and 8 reconcile keys.
   - `TemplatePackSeeder.class` contains `resolveReconcileTarget` and the new WARN strings.
   - `templates/document-default.css` has `white-space: nowrap`.
3. **Startup reconcile** (`log-reconcile.txt`, `03-db-after-reconcile.txt`, `03b-db-vs-classpath-v8.txt`).
4. **Engagement letters** (UI › Generate Document › Next: Preview › Download PDF): general and litigation on "Ndlovu — transition repro", conveyancing on Khumalo, and the Custom clone on Ndlovu.
5. **Fee note INV-0001** (invoice page › Generate Document › "Fee Note" › Download PDF).
6. **SoA** on "Ndlovu — transition repro", period 2026-10-01 to 2026-10-28.
   - "Share on the client portal" and "Email the client now" were both unchecked (internal draft).
   - Clicked Preview & Save. The iframe HTML was saved, and the stored PDF was fetched via `GET /api/generated-documents/9cd499ff-…/download`.
7. **Banking cleared.** Firm › General › letterhead form: emptied the 5 banking fields and clicked Save letterhead (`08a`; GET → all 5 null, `08b`).
   - Regenerated INV-0001 (`08c`, `f3-…`).
   - Restored the exact original values and saved (`08d`). GET `08e` is **byte-identical to `00a`** (diffed).
8. **Signatory consumers.** `POST /api/templates/{id}/preview` with entity Khumalo for matter-closure-letter `30854651-…`, deed-of-transfer `c47c8399-…` and bond-cancellation-instruction `629a7ecc-…` (`09-*-preview-api.html`; the data-URI logo is truncated for size).
9. **Second restart** (`docker compose … restart backend`, 22:44:45 UTC) (`log-second-restart.txt`, `10-db-after-second-restart.txt`).

## Observations

| # | Check | Observed | Verdict |
|---|---|---|---|
| O1 | Image carries the v8 code | Covered in step 2. | PASS |
| O2 | Reconcile log on the existing tenant | `22:34:31.623 INFO TemplatePackInstaller Reconciled template pack legal-za to v8 for tenant tenant_7d218705360b (0 new templates, 8 refreshed, 0 re-pinned after the reconcile block)`, then `22:34:31.627 INFO PackInstallService Pack legal-za reconciled to v8 for tenant tenant_7d218705360b (internal path)`, then `Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed`. There were no WARN/ERROR from the pack code. **No `Reconcile skipped (edited)` line**: expected, because the PLATFORM litigation row was pristine (see step 1b). | PASS |
| O3 | pack_install → 8; the 8 PLATFORM bodies are the v8 bodies | Covered in steps 3 and 4. | PASS |
| O4 | R-8: the clone survives and is not resolved | The reconcile ran with a PLATFORM + ORG_CUSTOM pair sharing `(legal-za, engagement-letter-litigation)`. The v7 code would hit `IncorrectResultSizeDataAccessException` here (PR R-8). It did not crash. The clone is untouched: `updated_at` is 22:33:02, before the reconcile; it still contains "Firm edit 626D."; it has no `org.signatory`/`org.logoUrl`; it remains untagged (`source_pack_install_id` null, no hash). | PASS |
| O5 | General letter (`e2-*`) | 1 image XObject (120×120, the logo rendered from the `data:` URI). The page PNG shows the logo. The H1 and headings are `#1b3a4b` (content stream `#1b3a4b rg`; current brand from GET /api/settings is `#1b3a4b`). Address line `88 Fredman Drive, Illovo Point, 3rd Floor, Sandown 2196, South Africa`, plus Tel / Email / Web / `VAT No: 4123456789`. A navy horizontal rule under the letterhead. It ends "Yours faithfully, / **Sipho Ndlovu / Managing Partner** / E2E Test Organization". The backend log for the generation window has **no** ConnectException / `localhost:4567` (the before-state logo failure is gone). | PASS |
| O6 | Conveyancing letter (`e3-*`, Khumalo) | Same letterhead (1 image, brand headings, rule, VAT No) and the same signatory block. | PASS |
| O7 | Litigation PLATFORM letter (`e1-*`) | Same letterhead and signatory. | PASS |
| O8 | Firm-customised litigation letter (`e1c-*`, `04d`) | It shows "…returning a copy to our offices. **Firm edit 626D.**". It has **no** letterhead: 0 image XObjects, no address/Tel/VAT, no signatory. The v8 bodies did not overwrite the firm's template. Its variables render blank ("Dear ,", empty Client/Matter) and there is no clause step. That is the pre-existing save defect D-1 below, which happened at 22:33 on the v7 backend, not 626D. | PASS (626D) / see D-1 |
| O9 | Fee note letterhead and client block (`f1-*`) | Logo (1 image), H1 firm name, address, Tel, Email, Web, `VAT Registration No: 4123456789`, rule, H2 "FEE NOTE". `To: Apex Holdings mvr15e76ef9a` / `12 Rivonia Road` / `Sandton 2196` / `ZA` (the stored ISO code, per R-9). `Client VAT Number: 9012345678`. Matter line. `Due Date: 26 November 2026`. The "Matter Reference:" line is now **hidden**: it is conditional and the value is empty; before, it was printed empty. | PASS |
| O10 | Fee-note banking | "Banking details" (h3), then Bank: Standard Bank, Branch code: 051001, Account number: 0012345678, Account type: Cheque, Payment reference: INV-0001, then "Please use your fee note number as the payment reference.". The literal "available on request" and the "Payment Details" heading are **absent** (grep count 0). | PASS |
| O11 | G88-M8: money cells do not wrap | The text dump row is `… 1 R3 500,00 R525,00 R4 025,00`. The 200-dpi close-up `f1-…-p2-lines-closeup.png` shows `R525,00` on one line; before it was `R525,` / `00`. The SoA has `R150,00 R0,00` on one line (`s1-….txt` line 22; before, `R150,` / `00`). The preview HTML has `<td class="num">` on the Amount/VAT cells and a `td.num` CSS rule. | PASS |
| O12 | SoA Payment Instructions (`s1-*`, `07a`) | `<h3>Payment Instructions</h3><p><strong>Bank:</strong> Standard Bank</p>…Branch code 051001…Account number 0012345678…Account type Cheque…<p>Please use your fee note number as the payment reference.</p>`. The PDF text has the same leaves. There is no `{bankName=` text (`mapText:false`). The Category column is still raw **`SHERIFF_FEES`**: expected, it is 626E's (R-3). | PASS (category → 626E) |
| O13 | Clearing banking hides the section (`f3-*`) | After the clear, the PDF goes from "Total Due: R4 025,00" straight to the VAT-Act note and the footer. There is no "Banking details" heading and no Bank/Branch/Account/Payment reference line. The only "account" grep hit is the `accounts@` e-mail. Banking was then restored, and GET is identical to the pre-session state. | PASS |
| O14 | Closure letter, deed and bond sign with `org.signatory.*` | `matter-closure-letter`: "Yours faithfully, / Sipho Ndlovu / Managing Partner / E2E Test Organization". Deed of transfer and bond cancellation end the same way. `principal_attorney` count in the HTML is 0 for all three. | PASS |
| O15 | Second restart is a no-op | `22:45:02.493 INFO PackInstallService Pack legal-za already installed (internal path), skipping`, then `checked 1 tenants, 1 succeeded, 0 failed`. `pack_version` is still 8, and 0 legal-za templates were updated after 22:44:45. | PASS |

## Defects / notes
- **D-1 (HIGH, pre-existing, out of 626D scope): the UI template editor's Save strips every Tiptap node `attrs`.** Evidence is in `05-defect-template-save-strips-attrs.txt`.
  - **What is lost:** heading `level`, variable `key`, clauseBlock ids, conditionalBlock `fieldKey`/operator.
  - **Effect:** a firm-customised template renders blank variables, and its clause blocks and conditions vanish.
  - **Reproduced twice:**
    - litigation clone, on the v7 backend;
    - a throwaway "Notice of Motion" clone. That clone's DB row had its attrs straight after Clone & Customize. It lost them after one Save. It was then removed via the UI's "Reset to Default".
  - **Mechanism, observed:**
    - `editor.getJSON()` attrs are **null-prototype** objects (ProseMirror).
    - `TemplateEditorClient.handleSave` passes them straight to the `updateTemplateAction` server action.
    - The captured server-action request body carries `"attrs":"$T"` (React temporary references), so they never reach the backend `PUT /api/templates/{id}`.
  - **Scope:** none of the files involved are in PR #1688.
  - **Fix direction:** pass a plain JSON copy. This is a separate fix and needs its own spec.
- **N-1 (LOW, cosmetic):** the fee note's line-table header row now falls alone at the bottom of page 1 and its single row starts page 2 (`f1-…-p1.png`/`-p2.png`), because the letterhead makes page 1 longer. The text and amounts are intact.
- **N-2 (note):** `org.address` joins city and postal code with a space ("Sandown 2196", "Sandton 2196"), not a comma. This is 626B's formatting.
- **N-3 (note):** the SoA header shows the logo, firm name and VAT Reg but no address/contact block, and "File reference:" is still empty. 626D's SoA scope was Payment Instructions only (S3 is not claimed by the PR).

## State left changed on the stack
- **Tax Registration Number:** set to `4123456789` (was null).
- **Clone of the litigation letter:** ORG_CUSTOM `80791878-6e4d-47fc-acbc-9ec04a2a1c54` "Engagement Letter — Litigation (Custom)" exists. It contains "Firm edit 626D." and its attrs were stripped by D-1. Delete it via Reset to Default when no longer needed.
- **Pack:** legal-za is now **v8**, and its 8 PLATFORM templates carry the v8 bodies (by design).
- **Letterhead:** cleared and then restored, identical to the start of the session.
- **New `generated_documents` rows** (`11-…txt`):
  - `152742af` (general letter), `7e0b3178` (conveyancing letter), `ee2d40a9` (litigation Custom), `4d772f38` (litigation PLATFORM), `27db8507` and `1e218cfb` (fee notes). None of them has a `document_id`.
  - SoA internal draft `9cd499ff-b91e-42ba-a333-aec62049861a` (`document_id 141d2063-…`), saved to the Ndlovu matter's documents. It was not shared and not emailed: the newest Mailpit message is from 2026-10-24.
- **Throwaway clone:** "Notice of Motion (Custom)" `69808d72-…` was created and then deleted (Reset to Default; the row count is 0).

Presigned `X-Amz-Credential` / `X-Amz-Signature` values are redacted in `00b-…json`. No other saved file contains a presigned URL, because the logo is now a `data:` URI.
