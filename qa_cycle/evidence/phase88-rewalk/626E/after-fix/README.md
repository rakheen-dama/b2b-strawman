# 626E re-observation after fix commit 1070711c7 (PR #1689)

**When:** 2026-10-28, 00:47–00:52 UTC.
**Where:** E2E mock-auth stack (:3001 / :8081), tenant `tenant_7d218705360b`, signed in as Alice (owner).

**What changed since c46eb4203:** in `fee-note-za.json`, the `org.bankingDetails.referenceNote` conditional now sits inside the `invoice.invoiceNumber` isNotEmpty conditional. The pack.json fee-note v9 `to` hashes were re-pinned to match.

## Stack swap
1. Worktree `/Users/rakheendama/Projects/2026/worktree-epic-626E` was at HEAD `1070711c7`.
2. I re-opened the reconcile gate (see "The one write" below).
3. I ran `docker compose -f compose/docker-compose.e2e.yml build backend`. It exited 0; the log is `/tmp/626E/reobs-build.log`.
4. I ran `up -d --no-deps backend`.
   - New image: `sha256:7b9ea7c535f5…`, built 00:48:53Z.
   - Container started 00:48:57Z and was healthy within about 20 s.
   - Postgres, frontend, LocalStack and mock-idp were not touched.

**Provenance** (`05-image-provenance.txt`):
- The jar's `template-packs/legal-za/fee-note-za.json` has sha256 `45079e89…`. This equals `git show 1070711c7:…`. It does not equal c46eb4203 (`7e647e80…`).
- The jar's pack.json is at version 9 and contains the new `to` hash `841ecc2205b2…` twice.

## The one write (test infrastructure, disposable e2e stack)
```sql
UPDATE tenant_7d218705360b.pack_install SET pack_version = '8' WHERE pack_id = 'legal-za';  -- UPDATE 1, at 00:48:08Z
```
- **Why it was needed:** the tenant was already at v9 with the c46eb4203 body, so a restart alone would not reconcile.
- **Why it works:** `TemplatePackInstaller.reconcile` → `refreshIfUnmodified` refreshes any pristine row (stored content_hash equals current content). It does not depend on the reconcile-block `from` hashes, so the row is refreshed whatever the old v9 body was.
- **Result:** the version was restored to 9 by the reconcile itself. Every other query was a read-only SELECT. Output is in `01-gate-reopen-update.txt`.

## Results

| # | Item | Status | Observed |
|---|---|---|---|
| R1 | Reconcile delivers the new body | PASS | Log at 00:49:13.17Z: `Reconciled template pack legal-za to v9 for tenant tenant_7d218705360b (0 new templates, 1 refreshed, 0 re-pinned after the reconcile block)`, followed by `Pack legal-za reconciled to v9 … (internal path)` (`02-…`). Details below the table. |
| R2 | DRAFT preview (API) | PASS | `GET /api/invoices/852969e7-…/preview` returned 200 `text/html` (`10-…`). Counts and banking block below the table. |
| R3 | SENT INV-0004 preview + PDF | PASS | Preview returned 200 `text/html` (`11-…`); PDF generated through the API (`20-…`). Details below the table. |
| R4 | Browser: DRAFT fee note › Preview | PASS | Fee note page `/org/e2e-test-org/invoices/852969e7-…` (`40-…png`) › **Preview** opened a new tab at `:3001/api/invoices/852969e7-…/preview`. Details below the table. |
| R5 | Closure-gate regression (M1) | PASS | `GET /api/matters/b9174723-…/closure/evaluate` gate 1 TRUST_BALANCE_ZERO: `"Matter trust balance is R38 000,00. Transfer to client or office before closure."`, `detail.balance: 38000.00` (`30-…`). |

### R1 details
- **pack_version:** went from 8 (after the gate re-open) back to **9**.
- **fee-note-za row `c543e678-…`:**

  | | md5 | content_hash | updated_at |
  |---|---|---|---|
  | Before | `f58d7d92…` | `08f9cb2d…` | 00:27:48 |
  | After | `c5bf6a39…` | `b4482971…` | **00:49:13** |

- **Nesting check** (`jsonb_path_query`, `03-…`):
  - The `invoice.invoiceNumber` conditional's children are now `[paragraph, conditionalBlock(org.bankingDetails.referenceNote)]`.
  - All 2 occurrences of `org.bankingDetails.referenceNote` in the content are inside that conditional.
- **Firm clone `80791878-…`:** unchanged. md5 is `87c8f26b…` and updated_at is 2026-10-27 22:33:02, the same before and after and again at the end of the run.
- **Other legal-za rows:** all have the same md5 and updated_at as before (`00-…` compared with `04-…`).

### R2 details: DRAFT preview over the API
| String | Count |
|---|---|
| `DRAFT &mdash; not yet issued` | 1 |
| Reference-note sentence `Please use your fee note number as the payment reference.` | 0 |
| `Payment reference:` | 0 |
| `Fee Note Number:` | 0 |

- The banking block is present: Banking details, Bank: Standard Bank, Branch code 051001, Account number 0012345678, Account type Cheque, then the VAT tax-invoice line.
- The tenant's `org_settings.letterhead_payment_reference_note` is that same sentence, so it is configured and correctly hidden.

### R3 details: SENT INV-0004
- **Preview:** contains `Fee Note Number:` ×1 and `INV-0004` ×2. The banking block ends with `<p><strong>Payment reference:</strong> INV-0004</p><p>Please use your fee note number as the payment reference.</p>`. DRAFT appears 0 times.
- **PDF:**
  - Request: `POST /api/templates/c543e678-…/generate` with `saveToDocuments:false`. This is the Download-PDF path and persists nothing.
  - Response: 200 `application/pdf`, `fee-note-inv-0004-2026-10-28.pdf`.
  - Ghostscript txtwrite text includes:
    - `Fee Note Number: INV-0004`
    - `Payment reference: INV-0004`
    - `Please use your fee note number as the payment reference.`
  - DRAFT appears 0 times in the PDF text (`20-…txt`).

### R4 details: browser preview tab
- `innerText` checks (`41-…json`):
  - hasDraftBanner = true
  - hasBankingHeading = true
  - hasBank = true
  - hasRefNote = **false**
  - hasPaymentRef = false
  - hasFeeNoteNumber = false
- Screenshot `42-…png` (full page): the banking block ends at "Account type: Cheque", then the VAT line. The reference-note sentence does not appear.

## SQL run
- Read-only:
  - `\d pack_install`
  - `select … from pack_install`
  - `select id,slug,source,pack_template_key,length(content::text),md5(content::text),content_hash,updated_at from document_templates where pack_id='legal-za'` (before and after)
  - `position('referenceNote' …)`
  - `jsonb_path_query(content,'strict $.**?(@.type=="conditionalBlock" && @.attrs.fieldKey=="invoice.invoiceNumber")')`
  - `select letterhead_payment_reference_note from org_settings`
  - the clone md5 re-check.
- Write: only the single UPDATE above.

## Data created or changed
- `pack_install.legal-za` was set 9 → 8 (by me), then 8 → 9 (by the reconcile).
- fee-note-za was refreshed by the product reconcile.
- No invoices, documents or matters were created or changed. The PDF was generated with saveToDocuments=false, and the closure evaluate is read-only.

## Notes
- Backend log: 0 ERROR lines since the 00:48:57Z restart.
- Bearer tokens are not stored here; a grep for `eyJ` and `Bearer ` finds nothing.
- Playwright: I used the existing shared browser and closed only the preview tab I opened. My three output files were moved here from the repo root, so none are left in the checkout.
- Stack left running: e2e-backend on image `7b9ea7c535f5…` built from `1070711c7`. Frontend and other services are unchanged.
