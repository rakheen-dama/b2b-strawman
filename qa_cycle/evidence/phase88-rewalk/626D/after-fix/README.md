# 626D: legal-za pack content — AFTER-FIX re-observation (fix cycle 1, PR #1688 head `2afd0e605`)

Captured 2026-10-27 between 23:09 and 23:23 UTC on the E2E mock-auth stack (:3001 / :8081), on the existing tenant `tenant_7d218705360b` (org `e2e-test-org`, logged in as Alice, owner). The first observation (head `837e2dcdd`) is in `../after/README.md`. This run reuses its matters, fee note INV-0001 and PDF inspection recipe (Ghostscript `txtwrite` + `png16m` + a page-1 pixel scan + a zlib content-stream decode for `rg` colours and `/Subtype /Image` counts).

- **Rebuild.** Only the backend was rebuilt, from the worktree at `2afd0e605` (`build backend`, then `up -d --no-deps backend`, at 23:10:21). The frontend container is unchanged (626C build). LocalStack and the logo bytes were kept.
- **No product code was changed.**
- **SQL.** All SQL was read-only SELECTs, except one documented observer-recipe write: the pack version rewind (625E/625F recipe) in step 2.
- **UI writes.** Every product write went through the UI (Playwright MCP).
- **Read-only API calls.** `GET /api/settings/letterhead`, `GET /api/templates/readiness`, `POST /api/templates/{id}/preview`, Mailpit `GET /api/v1/message/{id}[/part/2]`.
- **S3 download oracle (new in this run).** The LocalStack request log (`AWS s3.GetObject => 200`) shows when the logo bytes are downloaded. The browser's own presigned fetches were told apart through the Playwright network log.

## Steps
1. **Rebuild + image proof** (`02-image-check.txt`, `01-db-templates-before-rebuild.txt`). The Maven step in Docker was not cached (`RUN ./mvnw clean package` DONE 38.2s).
   - The jar contains `io/b2mash/b2b/b2bstrawman/template/InlineLogo.class`.
   - `template-packs/legal-za/pack.json` has `"version": 8`, and the fee-note-za `to` is `sha256:09168d1b13d1cc91d6fb98518ab9d1603affa99e11ec9b87597342d98c4fe8e9`.
   - The first boot on the new image logged `Pack legal-za already installed (internal path), skipping` (`log-first-boot-gate-closed.txt`). The gate was closed at v8, as expected.
2. **Re-open the version gate** (`03a-db-version-rewind.txt`). `UPDATE tenant_7d218705360b.pack_install SET pack_version = '7' WHERE pack_id = 'legal-za';` changed 8 → 7. Then `docker compose … restart backend` at 23:10:51 (`log-reconcile.txt`, `03-db-after-reconcile.txt`, `03b-db-body-hash-vs-pack-to.txt`, `03c-db-litigation-clone.txt`).
3. **General engagement letter** on "Ndlovu — transition repro" (UI › Generate Document › Engagement Letter — General › Next: Preview › Download PDF) (`04b`, `e2-*`).
4. **Readiness.**
   - Generate Document menus on the matter (`04a`) and on INV-0001 (`04c`).
   - A controlled direct-API window of 3 readiness calls (`05a`).
   - The client page, whose server component calls `/api/templates/readiness` (`05b`).
5. **Fee note INV-0001** (invoice page › Generate Document › Fee Note › Download PDF) (`06a`, `f1-*`), plus the preview HTML and the DB body structure (`06b`, `06c`).
6. **Emailed fee note with a clone present (F-4).**
   - Settings › Documents › Fee Note › Clone & Customize created ORG_CUSTOM `4ddb5375-a9c2-454f-b914-841c45975eca` "Fee Note (Custom)" (`07a`, `07b`). It was not edited or saved, which avoids D-1.
   - INV-0001 is already SENT and has no resend action. The send email fires only on the APPROVED → SENT transition (`InvoiceEmailEventListener.onInvoiceSent`), so a new fee note was created through the UI:
     - client Sipho Ndlovu › Fee notes › New fee note › Fetch Unbilled Time;
     - **"Deselect All" disbursements**, so only the one unbilled 2h time entry `71027972-…` on "Ndlovu v RAF — Depth II" was billed;
     - Validate & Create Draft › Create Draft, then Approve → **INV-0002**, then Send fee note (`07c`).
   - Mailpit and the attachment are in `m7-*`, the log in `07d`. The clone was then removed via **Reset to Default** (UI) (`07e`).
7. **Signatory gating (F-6).** Firm › General › letterhead: cleared only Signatory name and clicked Save letterhead (`08b-*`). I previewed the closure letter and the general letter through `POST /api/templates/{id}/preview` on Khumalo (`09-*`). I then typed "Sipho Ndlovu" back and saved (`08c-*`).
8. **Second restart** at 23:22:44 (`log-second-restart.txt`).

## Observations

| # | Check | Observed | Verdict |
|---|---|---|---|
| P1 | Image carries fix-cycle-1 code | Image `sha256:c1a51745…`, built 23:09:21 from `2afd0e605`. It has `InlineLogo.class`. pack.json is v8 with the 8 new `to` hashes, including fee-note-za `sha256:09168d1b…` (`02`). | PASS |
| P2 | Reconcile on the existing tenant (old v8 bodies → new v8 bodies) | `23:11:08.735 INFO TemplatePackInstaller Reconciled template pack legal-za to v8 for tenant tenant_7d218705360b (0 new templates, 7 refreshed, 0 re-pinned after the reconcile block)`, then `23:11:08.740 INFO PackInstallService Pack legal-za reconciled to v8 for tenant tenant_7d218705360b (internal path)`, then `23:11:08.913 INFO PackReconciliationRunner Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed`. **7, not 8, refreshed:** the fix cycle did not change `statement-of-account.json` (it is not in the diff), so its body was already current. There was no WARN/ERROR in the boot except the usual BeanPostProcessor and LibreOffice lines. There was no `ambiguous` / `pack-seeded rows` / `IncorrectResultSize` line, even though a PLATFORM + ORG_CUSTOM litigation pair is present (`log-reconcile.txt`). | PASS |
| P3 | pack_install back at 8; bodies = new `to` hashes | `legal-za | 8` (`03`). `document_templates.content_hash` is the install-time hash, which also covers CSS (`ContentHashUtil.hashOf` javadoc), so it is not the `to` value by design. I computed `sha256(canonical JSON of content)` for each DB PLATFORM row: **all 8 keys MATCH** their pack.json `to` (minus `sha256:`) and the classpath body in the running jar (`03b`). The 7 refreshed rows have `updated_at` 23:11:08 and a changed `content_hash` (`01` vs `03`). | PASS |
| P4 | Litigation clone untouched | `80791878-…` ORG_CUSTOM: `updated_at 22:33:02` (before both reconciles), `source_pack_install_id` null, `content_hash` null, contains "Firm edit 626D." = true (`03c`). | PASS |
| P5 | Logo via the lazy path, general letter (`e2-*`) | 1 image XObject (120×120); the page-1 PNG shows the logo. Headings `#1b3a4b rg` ×7. Firm block: address, Tel, Email, Web, `VAT No: 4123456789`. It ends "Yours faithfully, / Sipho Ndlovu / Managing Partner / E2E Test Organization". The LocalStack log shows `23:13:22 s3.GetObject` (preview) and `23:13:36.851 s3.GetObject` (PDF), then `23:13:37.646 s3.PutObject` (stored PDF): the backend downloaded the bytes at render time. There was 0 `ConnectException` / `localhost:4567` / `Failed to generate logo` / `Logo bytes` lines in the whole UI session (`log-ui-session-warn-error.txt`). | PASS |
| P6 | Readiness does not download the logo | **The brief's premise was corrected.** The Generate Document menu/dialog does **not** call the readiness endpoint. The only frontend caller is the client page (`clients/[id]/page.tsx:63` → `lib/api/setup-status.ts:16`). <br>• **UI:** the matter menu listed all 15 templates (`04a`) and the INV-0001 menu listed Invoice Cover Letter + Fee Note (`04c`). Both opened normally, with no errors. <br>• **Direct API:** `GET /api/templates/readiness` for PROJECT (15 templates), INVOICE (2) and CUSTOMER returned `ready:true`, with **0 GetObject** in that window and no WARN (`05a`). <br>• **Positive control:** one fee-note `POST …/preview` produced exactly **1 GetObject** (`06b` step). <br>• **Client page in the UI:** its readiness card rendered with 1 GetObject. That fetch was the **browser's** presigned `logo.png` request (Playwright network entry #36), so 0 came from the backend (`05b`). | PASS |
| P7 | Fee note INV-0001 (`f1-*`, `06b`, `06c`) | Logo, H1 firm name, address/Tel/Email/Web, `VAT Registration No: 4123456789`, rule, H2 "FEE NOTE". The client block reads `To: Apex Holdings mvr15e76ef9a` / `12 Rivonia Road` / `Sandton 2196` / `ZA`, as **separate `<p>`s** in the HTML. The DB body has sibling blocks: `addressLine1 isNotEmpty`, `addressLine2 isNotEmpty`, `city isNotEmpty` → "{{city}} {{postalCode}}", `city isEmpty` → (`postalCode isNotEmpty` → "{{postalCode}}"), `country isNotEmpty`. Then `Client VAT Number: 9012345678`, `Due Date: 26 November 2026`. Banking block: Standard Bank / 051001 / 0012345678 / Cheque / Payment reference: INV-0001 / note. G88-M8: the text row is `… 1 R3 500,00 R525,00 R4 025,00`, with `R525,00` unbroken. | PASS |
| P8 | Emailed fee note with a fee-note clone present (F-4) | **Mailpit (e2e, :8026):** message `bFVoYg4CmcDFgSEFV5quZr`, "Fee Note INV-0002 from E2E Test Organization", to `sipho.ndlovu.mvr15e76ef9a@example.com`, 1 attachment `INV-0002.pdf` (application/pdf, 4015 B). **The attachment** (`m7-*`) has the full letterhead (1 logo image, brand `#1b3a4b`, address/contacts/VAT Reg), `To: Sipho Ndlovu …` / `12 Rivonia Road` / `Sandton 2196` / `ZA`. The Client VAT line is hidden, correctly: this client has no tax number. Also: banking with `Payment reference: INV-0002`, and the row `2 R2 500,00 R750,00 R5 750,00`. **Log** (`07d`): `Marked invoice 7382c448-… as sent` → `Generated PDF: template=fee-note …, size=4015bytes` → `Created generated document: id=951a8914-…, template=c543e678-a7e0-4db8-963d-4b9c18d31e1f` (the **PLATFORM** row, not clone `4ddb5375`) → `Invoice email sent for invoice=7382c448-… to=sipho.ndlovu…`. `IncorrectResultSize` count: 0. **Clone removal:** it was removed via the UI's Reset to Default; afterwards only the PLATFORM Fee Note row remains (`07e`). | PASS |
| P9 | Signatory name cleared, designation kept (F-6) | GET after the save differs from `08a` only by `"signatoryName": null` (`08b-get…`). Closure letter preview: `Yours faithfully,</p><p>Managing Partner</p><p>E2E Test Organization</p>`, with **no empty name paragraph**. The general letter is the same. The one `<p></p>` in the closure HTML is the pre-existing "Reason for closure" body paragraph, byte-identical in `../after/09-matter-closure-letter-preview-api.html` (`09-*`). The DB bodies gate `org.signatory.name` and `org.signatory.designation` in separate `conditionalBlock`s (`09-signatory-block-structure.txt`). | PASS |
| P10 | Letterhead restored exactly | After typing "Sipho Ndlovu" and clicking Save letterhead, `08c-get-letterhead-restored.json` is **identical** to `08a` (diff empty). `08a` is itself identical to `../after/08e` (the state before this session). | PASS |
| P11 | Second restart is a no-op | `23:23:03.583 INFO PackInstallService Pack legal-za already installed (internal path), skipping`, then `23:23:03.744 INFO PackReconciliationRunner Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed`. `pack_version` is 8, and 0 legal-za templates were updated after 23:22:44. | PASS |

## Defects / notes
- No new defect in PR #1688 scope.
- **N-4 (LOW, pre-existing, out of scope): the invoice-creation WARN is stale.** `InvoiceCreationService` logged `created without a tax number — send will be blocked until the customer profile is updated (warning code: tax_number_missing)`. But the UI said "The fee note can be sent — it will be sent without a client VAT number", and the send succeeded. The log text contradicts the behaviour.
- D-1 (template editor Save strips node attrs) from `../after/` still applies. That is why the fee-note clone was not saved in this run.

## State left changed on the stack
- **legal-za pack:** the `pack_version` rewind 8 → 7 was reconciled back to **8**. The 7 PLATFORM bodies now carry the fix-cycle-1 v8 content (by design).
- **New fee note INV-0002** `7382c448-cff0-431b-8d11-5ee0825679ce` (client Sipho Ndlovu, matter "Ndlovu v RAF — Depth II"):
  - status **SENT**, total R5 750,00;
  - it bills time entry `71027972-…` (2h, R5 000 + VAT). Disbursements were not billed;
  - the fee-note email went to Mailpit `bFVoYg4CmcDFgSEFV5quZr`.
- **New `generated_documents` rows** (`11-…txt`): `9a5c9df0` (general letter), `b7d1ff3e` (INV-0001 fee note), `951a8914` (INV-0002 fee note, from the email path). None of them has a `document_id`.
- **Fee Note clone:** `4ddb5375` was created and then removed (Reset to Default); the row count is 0.
- **Letterhead:** the signatory name was cleared and then restored. It is identical to the start of the session.
- **Unchanged:** the litigation clone `80791878-…` and the tax registration number.

Presigned `X-Amz-Credential` / `X-Amz-Signature` values are redacted in `05b`. No other saved file contains a presigned URL: the preview HTML logos are `data:` URIs, truncated for size.
