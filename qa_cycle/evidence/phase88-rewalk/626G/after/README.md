# Slice 626G — AFTER observation (PR #1691 head 330f385da), observer run 2026-10-28 02:46–02:53 UTC

## Delivery
- `docker compose -f compose/docker-compose.e2e.yml build backend` from `/Users/rakheendama/Projects/2026/worktree-epic-626G` (context `../backend` = worktree) → exit 0, `BUILD SUCCESS` (e2e-build.log). Build start 02:46:38Z.
- `up -d --no-deps backend` → container `e2e-backend` Created 2026-10-28T02:47:29.64Z, image sha256:b485a3d179bc…, compose working_dir `/Users/rakheendama/Projects/2026/worktree-epic-626G/compose`. Health UP.
- Reconcile ran at startup (reconcile-log.txt):
```
02:47:46.198 Running pack reconciliation for 1 tenants
02:47:46.367 Reconciling CLAUSE pack conveyancing-za-clauses v1 -> v2 for tenant tenant_7d218705360b
02:47:46.389 Reconcile applied: tenant=tenant_7d218705360b pack=conveyancing-za-clauses clause_template_association 'voetstoots' field association updated
02:47:46.395 … 'occupation-date' …
02:47:46.401 … 'suspensive-bond' …
02:47:46.406 … 'transfer-duty-liability' …
02:47:46.411 … 'fica-compliance' …
02:47:46.416 … 'rates-clearance' …
02:47:46.422 … 'jurisdiction-za' …
02:47:46.430 … 'transfer-duty-liability' …
02:47:46.436 … 'rates-clearance' …
02:47:46.444 … 'body-corporate-clearance' …
02:47:46.450 … 'fica-compliance' …
02:47:46.455 … 'cost-of-cancellation' …
02:47:46.623 Pack reconciliation: checked 1 tenants, 1 succeeded, 0 failed
```
  12 × "Reconcile applied … conveyancing-za-clauses"; 0 × "entry ignored"; no WARN/ERROR lines in the backend log.

## Verdicts
| Step | Verdict | Evidence |
|---|---|---|
| O1 DB | PASS | O1-db-after.txt — pack_install conveyancing-za-clauses = `2`; OTP 7 rows sort 0..6 voetstoots(t), occupation-date(f), suspensive-bond(t), transfer-duty-liability(t), fica-compliance(t), rates-clearance(f), jurisdiction-za(f); deed 3 (transfer-duty-liability 0, rates-clearance 1, body-corporate-clearance 2, all f); bond 1 (cost-of-cancellation 0 f); POA 1 (fica-compliance 0 f). EL counts unchanged 5/5/6/12; clone (Custom) 0. Total 28 → 40 (+12). |
| O2 OTP clause step | PASS | 01-otp-step1-select-clauses.png — heading "Generate: Offer to Purchase Step 1 of 2", "Select Clauses", 7 checked: Voetstoots (As Is) [Required, disabled], Occupation Date & Occupational Rent, Suspensive Condition -- Bond Approval [Required], Transfer Duty Liability [Required], FICA Compliance [Required], Municipal Rates Clearance, SA Law & Jurisdiction. |
| O3 OTP preview | PASS | otp-preview.html, 02-otp-preview-conditions-of-sale.png — `<h3>Conditions of Sale</h3><div class="clause-block" data-clause-slug="voetstoots"><p>The property is sold voetstoots, …`; data-clause-slug order voetstoots, occupation-date, suspensive-bond, transfer-duty-liability, fica-compliance, rates-clearance, jurisdiction-za; "suspensive condition", "Transfer Duty Act 40 of 1949" present; `clause not found` count 0. Saved: "Document saved successfully" (03-otp-saved.png); PDF 4.8 KB vs 2.1 KB before. |
| O4 deed/bond/POA | PASS | 04-*-step1.png + 05-*-preview.png + *-preview.html. Deed "Step 1 of 2": Transfer Duty Liability, Municipal Rates Clearance, Body Corporate Clearance; preview slugs in that order, 0 `clause not found`. Bond "Step 1 of 2": Breach & Cost of Cancellation; 0 not-found. POA "Step 1 of 2": FICA Compliance; 0 not-found. |
| O5 OTP subject | PASS | Mailpit `8RnzjAGTKFLuDJXMc7msHC`: `E2E Test Organization -- Please review offer to purchase for acceptance: offer-to-purchase-khumalo-property-holdings-erf-1234-sandton-transfer-mvr15e76ef9a-2026-10-28.pdf` |
| O6 EL control | PASS | Mailpit `GjS4ovNmUkeCAuyjUa4nN7`: `E2E Test Organization -- Please review engagement letter for acceptance: engagement-letter-conveyancing-khumalo-property-holdings-erf-1234-sandton-transfer-mvr15e76ef9a-2026-10-28.pdf` (byte-identical to before control mHxYYTdv6yHygbvBQrTUQh) |
| O7 reminder | PASS | UI Remind (expanded acceptance row). OTP: `ZqbTqwc4YpkAceb692okow` `Reminder: E2E Test Organization -- Please review offer to purchase for acceptance`. EL control: `joJo8MyFAzLgneS5fHV8xD` `Reminder: E2E Test Organization -- Please review engagement letter for acceptance`. |
| O8 idempotency | PASS | O8-restart.txt — restart 02:52:42Z, UP; `Running pack reconciliation for 1 tenants` / `checked 1 tenants, 1 succeeded, 0 failed`; 0 × `Reconciling CLAUSE pack conveyancing-za-clauses`; counts unchanged, total 40, max(created_at) still 02:47:46.455. |

## Data created
See created-ids.txt. OTP generated_document `0c70cab0-1872-45cc-8ebc-a2a0cb0b7432` (document `76a8c24c-c063-4057-b818-be6f83eb00b1`);
OTP acceptance_request `945c12a4-2b25-4004-a066-0508d9e93cfa` (SENT, reminder_count 1);
EL acceptance_request `4710c37b-8b75-4615-b63b-9a5d7feb6835` (SENT, reminder_count 1) on the existing EL doc `99eaa3f5-…` — re-sending REVOKED the earlier request `7944c9af-…` (existing behaviour).
Deed/bond/POA were previewed only (not saved).

## Notes (not defects of this slice)
- The "Reconcile applied" lines name only the clause slug, not the target template, so `transfer-duty-liability`, `rates-clearance` and `fica-compliance` each appear twice with no way to tell OTP from deed/POA in the log (observability nit).
- Clause text semantics (pre-existing pack content): the OTP voetstoots clause renders the client (purchaser) as the party accepting and "E2E Test Organization" (the firm) as the party not liable, i.e. the firm reads as the seller.
- Console: one "Blocked script execution in 'about:srcdoc' … sandboxed" error, caused by the observer's own probe of the sandboxed preview iframe, not the product.
