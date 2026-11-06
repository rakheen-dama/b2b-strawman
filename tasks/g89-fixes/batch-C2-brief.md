# Batch C2 brief: template packs and document generation (G89-F6, G89-F26, 632A(a)(b)(c), 632C)

**Branch:** `fix/g89-c2-templates-generation`, cut from fresh `origin/main` after C1 merges.
**PR title:** `fix(documents): legal-za pack v13 (LPA s86 wording, trust statement data), hide dedicated-flow templates from generic menus, matter-type suffix cleanup, receipt filenames (G89-F6/F26, 632A, 632C)`
**Authorisation:** founder D1/D2/D10/D11/D12 (2026-11-06). F6 wording needs **founder sign-off** before merge (D12); put the exact before/after strings in the PR body under "Wording for sign-off".
**Gates:**
- backend full verify (orchestrator)
- frontend gate
- portal gate (portal re-sync of descriptions)
- two bound reviews and CodeRabbit
- browser checks on the Keycloak stack:
  - S1-67: Trust Receipt hidden; Client Trust Statement shows a real balance and lines, with no "[… to be inserted]"
  - S2-50: the portal matter page has no "Matter type: LITIGATION"
  - matter / fee-note Generate menus: no SoA, closure letter, payment receipt or common-pack PROJECT templates
  - the receipt PDF file name carries R1

`BE` = `backend/src/main/java/io/b2mash/b2b/b2bstrawman`, `RES` = `backend/src/main/resources`. Lines verified at `30317a851`; re-check them after earlier batches.

## One legal-za pack bump: v12 → v13 (ADR-356)

**Pack decision from C1:**
- If C1 put the "Unbilled WIP" context key in, add its SoA template summary row here as well.
- If C1 already bumped to v13, this batch is v14.

Follow precedent `e099f966b` (632A v10→v11):
1. Bump `"version"`.
2. Re-point older `to` hashes for edited templates (cumulative chain).
3. Add a `from` (previous head) → `to` (new head) entry per edited template.
4. Snapshot the old bodies under `src/test/resources/fixtures/template-packs/legal-za-v12/`.
5. Extend `LegalZaTemplateReconcileIntegrationTest` and `TemplatePackSeederReconcileTest`.

## G89-F6 + D12: every Attorneys Act / s78 reference becomes the Legal Practice Act

**Regulatory documents (pack v13):**
- `RES/template-packs/legal-za/trust-receipt.json:122` " — Section 78(1) Trust Account" → " — Section 86(2) trust account"
- `RES/template-packs/legal-za/trust-receipt.json:168` "Section 78(1) of the Legal Practice Act 28 of 2014" → "section 86 of the Legal Practice Act 28 of 2014". Founder to confirm the subsection.
- `RES/template-packs/legal-za/client-trust-statement.json:122` → same wording as `trust-receipt.json:122`

**Other places:**
- `RES/clause-packs/legal-za-clauses/pack.json:124` "per Section 86 of the Attorneys Act" → "Legal Practice Act 28 of 2014".
  - The clause pack is v2. Its reconcile supports only `body`.
  - If this string is a `description`, either add `description` resolver support (small; mirror the body resolver) or limit the change to fresh tenants. Record the choice as a judgment call.
- `RES/ai/skills/compliance-audit/system.txt:26-27` and `RES/ai/skills/matter-intake/system.txt:55`.
  - Replace "Attorneys Act s78 / Rule 54" with "Legal Practice Act s86 and the LPC Rules (rule 54)".
  - `ComplianceAuditSkillTest:87` asserts `containsIgnoringCase("Attorneys Act")`. Update it to assert the new wording.
- `BE/verticals/legal/trustaccounting/report/Section35DataPackQuery.java:17-18`: javadoc only.
  - The LPA's audit/report provision is the "Section 35" of the old Act. Founder to confirm the right LPA reference.
  - Fix the comment only; the report name stays.
- `frontend/components/marketing/built-for-africa.tsx:79`: "Attorneys Act Section 78" → "Legal Practice Act Section 86" (line 58 already says s86).
- `qa/testplan/demos/ai-core-verification-keycloak.md:98`: update the expectation text if it quotes the old wording.

**Leave unchanged:** the historical fixtures under `src/test/resources/fixtures/**` (they are `from` states).

**Test:** a new `LegalZaRegulatoryWordingTest` that greps the shipped resources (template packs, clause packs, AI skills) for `78(1)` and `Attorneys Act`, and expects 0 hits. It must fail on main.

## D11, 632A(a) and (c): generic menus and dedicated-flow templates

**Cause:**
- The generic path `DocumentTemplateController:172-184` → `GeneratedDocumentService.generateDocument` (6-arg overload, `:130`) → `PdfRenderingService.generatePdf:121-152` → `findBuilder(primaryEntityType)` only supplies project, customer or invoice context.
- So SoA (`statement.*`, `fees.*`, `summary.*`), the closure letter (`closure.*`) and the payment receipt (`payment.*`) render hollow there.
- Menus: matter `matters/[id]/page.tsx:420-423` and fee note `invoices/[id]/page.tsx:59` → `GenerateDocumentDropdown.tsx:94` list every active template for the entity type. That includes:
  - the universal `common` pack PROJECT templates "Standard Engagement Letter" and "Matter Summary Report" (`RES/template-packs/common/pack.json`)
  - `payment-receipt-za` (INVOICE)

**Fix:**
1. **Mark dedicated-flow templates.** Expose a `dedicatedFlow` (boolean) on `TemplateListResponse` (`DocumentTemplateController:246-261`), derived from the pack template key or slug. It is true for:
   - `statement-of-account` and `statement-of-account-*`
   - `matter-closure-letter`
   - `payment-receipt-za`
   - `trust-receipt` (hidden until a deposit-bound flow exists, D11)

   The generic menus filter these out. Settings › Documents still lists them, with a "Generated from its own flow" hint.
2. **Refuse on the generic endpoint.** The 6-arg controller path rejects dedicated-flow templates with a 400 `InvalidStateException` that names the right flow. Internal callers (closure, statements, receipts) use other overloads, so they are unaffected. Grep to confirm.
3. **Common-pack PROJECT templates for legal-za.** Hide "Standard Engagement Letter" and "Matter Summary Report" from the generic menus on legal-za tenants.
   - Prefer a declarative mechanism: e.g. the common pack's PROJECT templates carry `excludedProfiles: ["legal-za"]`, or the legal-za pack lists templates it supersedes. Do not use a slug blacklist in the frontend.
   - If installation is already profile-filtered, use that instead.
   - **Do not delete tenant data.**
   - Record the mechanism as a judgment call.
4. **Client Trust Statement gets real data (D11).**
   - Add a `trust` block to `CustomerContextBuilder` (`:68-161`), guarded by `isModuleEnabled("trust_accounting")`. Contents: account name, balance, and the recent ledger lines from `ClientLedgerService.getClientLedgerStatement:210` (opening, lines, closing).
   - Mirror the account selection and module guard in `StatementOfAccountContextBuilder.buildTrustBlock:492-570`.
   - In `client-trust-statement.json`, replace `:144` "[… to be inserted]" with a loop table over `trust.lines`, plus balance fields. This is part of the v13 bump.
5. **Trust Receipt.** Hidden through `dedicatedFlow` (step 1). Leave its body's placeholders at `:147,154,161` alone, apart from the F6 wording.

**Tests:**
- `GenerateDocumentDedicatedFlowIntegrationTest`:
  - `genericGenerate_statementOfAccount_rejected400`
  - `listTemplates_marksDedicatedFlow`
  - `legalZaTenant_commonPackProjectTemplates_notInGenericList`
- `ClientTrustStatementContextIntegrationTest`:
  - `rendersBalanceAndLines`
  - `trustModuleDisabled_noTrustBlock`
- vitest for `GenerateDocumentDropdown` filtering.

## 632A(b): required-field warnings show raw paths

**Cause:** `GenerateDocumentDialog.tsx:~308` renders `{f.entity}.{f.field}`, e.g. "project.customFields.seller_name". The reason strings come from `TemplateValidationService.java:57`.

**Fix:** resolve a label on the backend. In `TemplateValidationService`, map custom-field paths to the field definition's display name, and core fields to a small label map, e.g. "Matter › Seller name". Return it as `label` next to the existing fields; the frontend shows `label ?? path`. No pack change is needed.

**Tests:** a unit test on the label resolution, and a vitest.

## 632C: identical receipt file names (numbering unchanged, D10)

**Cause:** `PdfRenderingService.generateFilename:333-339` / `extractEntityName` INVOICE branch `:351-356` → `slug-<invoiceNumber>-<date>.pdf`.

**Fix:** when the context has `payment.number`, include it, e.g. `payment-receipt-za-inv-0002-r1-2026-11-06.pdf`. **Keep R1→R2 renumber-after-delete as is** (D10). `PaymentReceiptIntegrationTest:283-310` stays green.

**Test:** `twoReceiptsSameNoteSameDay_distinctFilenames`.

## G89-F26 + D12: the "Matter type: <ENUM>" suffix

**Fix:**
- Remove the suffix from the six description strings in `RES/project-template-packs/legal-za.json` (`:9,90,171,252,333,414`), and bump the pack version to 4.
- Add a tenant migration at the next free version after A (check with `ls ... | sort -V | tail -1`; expect V13). It strips `\s*Matter type: [A-Z_]+$` from:
  - `project_templates` where `source = 'SEEDER'`
  - `projects` where the description ends with that suffix

  Precedent for the form: `V2__add_project_template_work_type.sql`.
- Portal: `portal.portal_projects.description` is a copy. Trigger the existing re-sync path for projects, or add a global V4 that applies the same `regexp_replace`.
  - Prefer the re-sync if there is a startup or one-shot hook (cf. the 633B `portal_backfill_markers` pattern).
  - Otherwise use global V4.
  - Record which, and why.

**Tests:**
- A migration test that seeds a description with the suffix, migrates, and expects it stripped. Follow the repo's precedent for migration tests; if there is none, use an integration test that runs the SQL through the repo.
- Assert that the pack JSON contains no "Matter type:".

## Process

As in `batch-B-brief.md` "Process":
- `pwd` check
- red tests committed before fixes
- targeted tests: `template/**`, `document/**`, `seeder/**`, `packs/**`, `customer/**`, `invoice/**`, `verticals/legal/**`, `integration/ai/**`, `customerbackend/**`, plus importers
- frontend and portal gates
- open the PR with the "Wording for sign-off" section
- no full verify and no merge
- report back
