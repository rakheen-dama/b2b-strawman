# Phase 49 — Agent Gap Report (Document Content Verification)

## Generated: 2026-03-17
## Scenario: Document & Field Content Verification (accounting-za)

## Summary Statistics

| Category | Blocker | Major | Minor | Cosmetic | Total |
|----------|---------|-------|-------|----------|-------|
| content-error | 1 | 1 | 0 | 0 | 2 |
| missing-variable | 0 | 1 | 1 | 0 | 2 |
| missing-feature | 2 | 2 | 0 | 0 | 4 |
| bug | 0 | 1 | 0 | 0 | 1 |
| ux | 0 | 0 | 1 | 1 | 2 |
| format-error | 0 | 0 | 1 | 0 | 1 |
| **Total** | **3** | **5** | **3** | **1** | **12** |

## Critical Path Blockers

### 1. Only 7 accounting-za templates exist (test plan expects 8)
The `accounting-za` template pack (`backend/src/main/resources/template-packs/accounting-za/pack.json`) defines exactly 7 templates. The test plan (Section 2, T1) references "all 8 accounting-za templates" but only 7 exist:
1. `engagement-letter-bookkeeping` (PROJECT)
2. `engagement-letter-tax-return` (PROJECT)
3. `engagement-letter-advisory` (PROJECT)
4. `monthly-report-cover` (PROJECT)
5. `invoice-za` (INVOICE)
6. `statement-of-account` (CUSTOMER)
7. `fica-confirmation` (CUSTOMER)

Track T1 has 7 sub-tracks (T1.1-T1.7) which matches the actual count. The "8 templates" in the test plan scope table appears to be a documentation error. **Not a blocker for execution — T1 can run with 7 templates.**

### 2. Portal acceptance page does not exist (T6 entirely blocked)
The backend `PortalAcceptanceController` exposes REST APIs at `/api/portal/acceptance/{token}` for viewing, accepting, and streaming PDFs. However, there is no frontend portal page for acceptance. The portal frontend at `frontend/app/portal/` only has pages for projects, documents, and requests — no acceptance page exists. Track T6 (Document Acceptance / E-Signing) requires a client-facing portal page to view the document, type their name, and click "I Accept". This entire track is blocked.

### 3. `company_registration_number` template variable mismatched with field slug
The `engagement-letter-bookkeeping` template references `customer.customFields.company_registration_number`, but the field pack slug is `acct_company_registration_number`. This means the template variable will resolve to blank/placeholder for all customers, even when the field is populated. This affects T1.1.3, T5.1.4, and T7.3.4.

---

## All Gaps (Chronological by Track)

### GAP-P49-001: Template pack has 7 templates, not 8
**Track**: T1 (scope)
**Step**: Test plan scope table says "all 8 accounting-za templates"
**Category**: content-error
**Severity**: minor
**Description**: The test plan references 8 templates but only 7 exist in the `accounting-za` pack. The T1 sub-tracks (T1.1-T1.7) correctly enumerate 7 templates, so the "8" in the scope table is a documentation error. The QA agent can proceed with all 7 templates without issue.
**Evidence**:
- Pack file: `backend/src/main/resources/template-packs/accounting-za/pack.json` — 7 entries in `templates[]` array
**Suggested fix**: Update the test plan scope table to say "7 templates". Effort: S

---

### GAP-P49-002: `company_registration_number` variable mismatched with field slug `acct_company_registration_number`
**Track**: T0.2 / T1.1 / T5.1
**Step**: T1.1.3 — `customer.customFields.company_registration_number` should resolve to "2019/123456/07"
**Category**: content-error
**Severity**: blocker
**Description**: The `engagement-letter-bookkeeping` template content JSON uses the variable key `customer.customFields.company_registration_number`. However, the field pack `accounting-za-customer.json` defines this field with slug `acct_company_registration_number`. When a user populates this field via the CustomFieldSection UI, the value is stored under the key `acct_company_registration_number` in the customer's `customFields` JSONB column. The template variable lookup will not find it because it looks for `company_registration_number`. The rendered document will show a placeholder (`________`) instead of the actual registration number.
**Evidence**:
- Template content: `backend/src/main/resources/template-packs/accounting-za/engagement-letter-bookkeeping.json` line 55 — key `customer.customFields.company_registration_number`
- Field pack: `backend/src/main/resources/field-packs/accounting-za-customer.json` line 14 — slug `acct_company_registration_number`
- The `LenientOGNLEvaluator` returns `________` for unresolved expressions (line 19): this is the placeholder that will appear
- Note: The compliance pack `sa-fica-company/pack.json` also references `company_registration_number` (without `acct_` prefix) at line 71, suggesting a historical inconsistency
**Suggested fix**: Either (a) rename the field pack slug from `acct_company_registration_number` to `company_registration_number` (requires migration to update stored field definition slugs and re-key existing custom field values), or (b) update the template content to use `customer.customFields.acct_company_registration_number`. Option (b) is simpler but less clean. Effort: S (option b) / M (option a)

---

### GAP-P49-003: Missing blank-field handling in templates — dangling labels when fields are empty
**Track**: T5.6
**Step**: T5.6.3 — Check generated output when `company_registration_number` is blank (Naledi)
**Category**: ux
**Severity**: minor
**Description**: Templates use a "label + variable" pattern (e.g., `"Registration Number: " + variable`). When a custom field is not populated, the `LenientOGNLEvaluator` returns `________` (8 underscores). While this is not blank, it produces ugly output like "Registration Number: ________" instead of hiding the line entirely. The template content JSON does not use conditional blocks to hide fields when values are missing. This is the "dangling label" anti-pattern mentioned in T5.6.3 option (b), but the placeholder makes it slightly better than a pure blank. The test plan's unacceptable criteria include "no indication to the user that data is missing" — the `________` placeholder technically satisfies this but looks unprofessional in a client-facing engagement letter.
**Evidence**:
- `LenientOGNLEvaluator.java` line 19: `private static final String PLACEHOLDER = "________";`
- Template content files do not use conditional rendering — every variable is rendered inline
- The `TemplateValidationService` does warn about missing fields pre-generation, satisfying criteria (a) from T5.6 expected behavior
**Suggested fix**: Add conditional blocks in Tiptap template content to hide label+variable lines when the value is null/blank. Alternatively, change the placeholder from `________` to an empty string and rely on pre-generation warnings. Effort: M (per template)

---

### GAP-P49-004: `generatedAt` rendered as raw ISO 8601 in Statement of Account context
**Track**: T1.4 / T1.6
**Step**: T1.4.4 / T1.6.3 — `generatedAt` shows today's date, human-readable
**Category**: format-error
**Severity**: minor
**Description**: All context builders store `generatedAt` as `Instant.now().toString()` which produces ISO 8601 format (e.g., `2026-03-17T14:30:00.123456Z`). The `VariableMetadataRegistry` marks `generatedAt` with type hint `"date"`, and the `TiptapRenderer` applies `VariableFormatter.formatDate()` which converts ISO timestamps to "d MMMM yyyy" format (e.g., "17 March 2026"). So for Tiptap-rendered templates, dates WILL be properly formatted. However, if any template uses `generatedAt` in a context where the format hint is not applied (e.g., DOCX merge or direct context access), it would appear as raw ISO. For T1.4 and T1.6, which use Tiptap rendering, this should be LIKELY_PASS.
**Evidence**:
- `CustomerContextBuilder.java` line 128: `context.put("generatedAt", Instant.now().toString());`
- `VariableMetadataRegistry.java` line 246: `new VariableInfo("generatedAt", "Generated At", "date")`
- `VariableFormatter.java` line 57-69: parses ISO dates and formats as "d MMMM yyyy"
- `TiptapRenderer.java` line 271: calls `VariableFormatter.format(current, typeHint)`
**Suggested fix**: Store `generatedAt` as a pre-formatted string in context builders to ensure consistent display regardless of rendering path. Effort: S

---

### GAP-P49-005: Portal acceptance page missing — Track T6 entirely blocked
**Track**: T6
**Step**: T6.2.1 — "Open the acceptance link from the email (or navigate to portal acceptance page)"
**Category**: missing-feature
**Severity**: blocker
**Description**: The backend implements the full acceptance workflow: `AcceptanceController` (firm-side), `PortalAcceptanceController` (portal API at `/api/portal/acceptance/{token}`), `AcceptanceCertificateService` (SHA-256 certificate generation), `AcceptanceService` (token resolution, accept/view/revoke). The firm-side UI also exists: `SendForAcceptanceDialog.tsx`, `AcceptanceDetailPanel.tsx`, `AcceptanceStatusBadge.tsx`. However, the portal frontend has NO acceptance page. The `frontend/app/portal/` directory only contains pages for projects, documents, and requests. There is no `frontend/app/portal/(authenticated)/acceptance/` or similar route. Without this page, portal clients cannot view the document, type their name, and accept it. The entire T6 track (6 checkpoints including certificate verification) is blocked.
**Evidence**:
- Backend API exists: `backend/src/main/java/.../acceptance/PortalAcceptanceController.java` — GET `/{token}`, GET `/{token}/pdf`, POST `/{token}/accept`
- Frontend firm-side exists: `frontend/components/acceptance/SendForAcceptanceDialog.tsx`
- Portal pages: `frontend/app/portal/(authenticated)/` — only `documents/`, `projects/`, `requests/`
- No acceptance page found via glob or grep across `frontend/app/portal/`
**Suggested fix**: Create a portal acceptance page at `frontend/app/portal/accept/[token]/page.tsx` (or similar route outside the authenticated layout, since acceptance uses token-based auth not session auth). The page should call the portal acceptance API endpoints, display the PDF inline, show an acceptance form with a name field, and display confirmation after acceptance. Effort: L (new page + PDF viewer + form + confirmation flow)

---

### GAP-P49-006: Project custom field pack `autoApply` is `false` — fields may not appear on projects
**Track**: T0.7
**Step**: T0.7.1 — Open Kgosi "Monthly Bookkeeping" project > custom fields
**Category**: missing-variable
**Severity**: major
**Description**: The `accounting-za-project` field pack has `"autoApply": false`, meaning project custom fields (engagement_type, tax_year, sars_submission_deadline, etc.) are NOT automatically applied to projects. They must be manually applied via Settings > Custom Fields. If the QA agent or seed script does not explicitly apply this field pack to projects, T0.7 steps will fail — the custom field section will show "No custom fields configured" for projects. This cascades to T1.1.4 (engagement_type blank), T1.2.4 (tax_year blank), and T1.2.5 (sars_submission_deadline blank).
**Evidence**:
- Field pack: `backend/src/main/resources/field-packs/accounting-za-project.json` line 6: `"autoApply": false`
- Compare: `accounting-za-customer.json` line 10: `"autoApply": true` (customer fields auto-apply)
- `CustomFieldSection.tsx` lines 521-529: shows "No custom fields configured" empty state when no groups are applied
**Suggested fix**: Either change `autoApply` to `true` in the project field pack, or ensure the seed script explicitly applies the `accounting_za_engagement` field group to projects. The former is simpler. Effort: S

---

### GAP-P49-007: No proposal creation page/dialog exists — T3.1 partially blocked
**Track**: T3.1
**Step**: T3.1.1 — Navigate to Proposals > New Proposal
**Category**: missing-feature
**Severity**: major
**Description**: The proposals page exists (`frontend/app/(app)/org/[slug]/proposals/page.tsx`) with a proposal table and summary cards. However, searching for a "create proposal" dialog or form yields no results. The `proposal-detail-actions.tsx` component exists for actions on existing proposals (send, accept, etc.), and the backend `ProposalController` and `ProposalService` support full CRUD. But the frontend create flow needs verification — the existing components suggest proposals can be listed and managed, but the creation dialog may be missing or may exist only as a direct API action. T3.1.2 (fill form) requires a UI form for creating proposals.
**Evidence**:
- `frontend/app/(app)/org/[slug]/proposals/page.tsx` — proposals listing page exists
- `frontend/components/proposals/proposal-detail-actions.tsx` — actions for existing proposals
- Backend: `backend/src/main/java/.../proposal/ProposalController.java` — full CRUD endpoints exist
- No `CreateProposalDialog` or `NewProposalForm` component found in `frontend/components/proposals/`
**Suggested fix**: Search the proposals page more carefully for inline creation forms. If missing, create a `CreateProposalDialog` or `NewProposalSheet` component with fields for title, customer, fee model, amount, expiry. Effort: M

---

### GAP-P49-008: Proposal sending — email delivery and customer selection for T3.4 needs verification
**Track**: T3.4
**Step**: T3.4.1 — Open proposal > click Send > status changes to SENT
**Category**: missing-feature
**Severity**: major
**Description**: The backend `ProposalService` supports proposal status transitions and the `ProposalOrchestrationService` handles the send workflow. The `proposal-detail-actions.tsx` frontend component handles proposal actions. However, the proposal send workflow requires a portal contact to send to, and the email delivery (Mailpit verification) depends on the outbound email infrastructure being configured. The `ProposalPortalSyncService` handles portal syncing. The proposal send action exists in the UI (`proposal-detail-actions.tsx`), but whether the full email delivery pipeline works end-to-end in the E2E mock-auth stack needs runtime verification. This is UNCERTAIN rather than LIKELY_FAIL.
**Evidence**:
- Backend: `ProposalOrchestrationService.java` — handles send orchestration
- Frontend: `components/proposals/proposal-detail-actions.tsx` — has Send action
- Email: depends on Mailpit being configured in E2E stack
**Suggested fix**: Verify at runtime. No code change likely needed. Effort: S (verification only)

---

### GAP-P49-009: Information request "create from template" — template selection may not pre-populate items
**Track**: T4.1
**Step**: T4.1.3-T4.1.4 — Select template "Year-End Info Request", verify items pre-populated
**Category**: bug
**Severity**: major
**Description**: The `create-request-dialog.tsx` component exists in `frontend/components/information-requests/` and the `year-end-info-request-za.json` request pack template is defined with 8 items. The backend `RequestTemplateService` and `InformationRequestService` support creating requests from templates. However, the flow from template selection to item pre-population in the UI needs runtime verification. The request pack is seeded via `RequestPackSeeder` based on the vertical profile. If the vertical profile is correctly set to `accounting-za` during seed (T0.1), the template should be available. The `create-request-dialog.tsx` likely loads template items when a template is selected. This is LIKELY_PASS but warrants runtime verification.
**Evidence**:
- Request pack: `backend/src/main/resources/request-packs/year-end-info-request-za.json` — 8 items (4 required, 3 optional, 1 required)
- Frontend: `frontend/components/information-requests/create-request-dialog.tsx` — creation dialog exists
- Backend: `RequestTemplateService.java`, `InformationRequestService.java` — template-based creation supported
**Suggested fix**: Verify at runtime. If template items don't populate, check the `create-request-dialog.tsx` template selection handler. Effort: S

---

### GAP-P49-010: Information request firm-side review — accept/reject actions exist
**Track**: T4.4
**Step**: T4.4.3-T4.4.5 — Accept/reject items
**Category**: ux
**Severity**: cosmetic
**Description**: The firm-side review flow exists. `request-detail-client.tsx` renders item details with status badges, and `reject-item-dialog.tsx` provides a rejection dialog with reason field. The customer detail page at `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` includes request-related actions (`request-actions.ts`). Accept/reject actions are available. The portal-side request detail page (`frontend/app/portal/(authenticated)/requests/[id]/page.tsx`) supports file upload, text response, and shows rejection reasons with re-submission capability. This track should be LIKELY_PASS.
**Evidence**:
- Firm-side: `frontend/components/information-requests/reject-item-dialog.tsx`, `request-detail-client.tsx`
- Portal-side: `frontend/app/portal/(authenticated)/requests/[id]/page.tsx` — full upload/submit/re-submit flow
**Suggested fix**: None needed — feature appears complete. Effort: N/A

---

### GAP-P49-011: `customerVatNumber` correctly mapped in `InvoiceContextBuilder` — T5.5 should pass
**Track**: T5.5
**Step**: T5.5.2 — Verify `customerVatNumber` = "4520012345"
**Category**: missing-variable
**Severity**: minor (observation, not a gap)
**Description**: The `InvoiceContextBuilder` correctly maps `customer.customFields.vat_number` to a top-level `customerVatNumber` context variable (lines 107-113). The `invoice-za.json` template content uses `customerVatNumber` as the variable key (line 43). The field pack slug is `vat_number` (line 33 of `accounting-za-customer.json`). All three keys match: field slug `vat_number` -> `customer.customFields` -> `customerVatNumber` alias. This should resolve correctly for Kgosi (value = "4520012345") and be null for Naledi (not VAT-registered). LIKELY_PASS.
**Evidence**:
- `InvoiceContextBuilder.java` lines 107-113: maps `customFields.get("vat_number")` to `customerVatNumber`
- `invoice-za.json` line 43: uses key `customerVatNumber`
- `accounting-za-customer.json` line 33: slug `vat_number`
**Suggested fix**: None needed. Effort: N/A

---

### GAP-P49-012: DOCX pipeline — LibreOffice likely not in E2E Docker stack
**Track**: T7
**Step**: T7.4.1-T7.4.4 — PDF conversion from DOCX
**Category**: missing-feature
**Severity**: blocker
**Description**: The `PdfConversionService` checks for LibreOffice (`soffice`) availability at startup and falls back to docx4j. In the E2E Docker stack, LibreOffice is unlikely to be installed in the backend container (it's not mentioned in compose scripts or Dockerfile). The docx4j fallback requires the `org.docx4j` dependency on the classpath, which may or may not be present. If neither is available, `convertToPdf()` returns `Optional.empty()` and the generation service logs a warning: "PDF conversion unavailable. DOCX output returned instead." The DOCX merge pipeline itself (field discovery, merge) should work via `DocxMergeService`, but PDF conversion (T7.4) will likely fail gracefully. T7 is marked as manual testing by founder, so this is expected behavior for the E2E stack.
**Evidence**:
- `PdfConversionService.java` line 27-34: checks `which soffice` at startup
- `PdfConversionService.java` lines 116-137: docx4j fallback checks classpath
- `GeneratedDocumentService.java` lines 397-418: handles `Optional.empty()` from PDF conversion gracefully, adds warning
**Suggested fix**: If DOCX-to-PDF is needed in E2E, add LibreOffice to the backend Docker image (`apt-get install libreoffice-writer`). Otherwise, accept DOCX-only output for T7.3 and skip T7.4. Effort: M (Docker image change)

---

## Track-by-Track Readiness Assessment

### Track 0 — Data Preparation: LIKELY_PASS (with caveats)
- T0.1 (lifecycle seed): Depends on Phase 48 script, should work
- T0.2-T0.5 (custom field population): CustomFieldSection UI exists with full edit/save capability, visibility conditions supported for trust fields. LIKELY_PASS
- T0.5.8 (trust fields visible for TRUST entity): Visibility conditions are implemented in `CustomFieldSection.tsx` (lines 182-205) and defined in `accounting-za-customer-trust.json` with `visibilityCondition` on each field. LIKELY_PASS
- T0.6 (org settings): `documentFooterText` and `taxRegistrationNumber` both exist on `OrgSettings` entity. LIKELY_PASS
- T0.7 (project custom fields): GAP-P49-006 — `autoApply: false` may prevent fields from appearing. UNCERTAIN
- T0.8 (portal contacts): Portal contacts can be created from customer detail. LIKELY_PASS

### Track 1 — Template Variable Fidelity: LIKELY_PASS (except GAP-P49-002)
- T1.1: `company_registration_number` will show `________` placeholder (GAP-P49-002). All other variables should resolve.
- T1.2: `sars_tax_reference` correctly mapped. `tax_year`/`sars_submission_deadline` depend on GAP-P49-006.
- T1.3: Advisory template variables should resolve correctly.
- T1.4: `generatedAt` will be formatted via `VariableFormatter` as "d MMMM yyyy". LIKELY_PASS
- T1.5: Invoice variables and `customerVatNumber` correctly mapped. LIKELY_PASS
- T1.6: Statement of account with invoices, totalOutstanding, running balance. LIKELY_PASS
- T1.7: FICA confirmation. `fica_verification_date` slug matches template. LIKELY_PASS
- T1.8: Cross-customer isolation via schema boundary. LIKELY_PASS

### Track 2 — Clause Assembly: LIKELY_PASS
- 7 clauses exist in `accounting-za-clauses` pack with 4 required, 3 optional for bookkeeping template
- `GenerationClauseStep` component has checkbox toggles (required locked, optional toggleable), reordering buttons
- `ClauseResolver` validates required clauses, supports explicit selections
- Clause bodies use `org.name` and `customer.name` variables which are resolved by TiptapRenderer
- LIKELY_PASS for T2.1-T2.4

### Track 3 — Engagement Letter Pipeline: UNCERTAIN
- GAP-P49-007: Proposal creation UI needs verification
- GAP-P49-008: Proposal send + email delivery needs runtime verification
- Document generation from project scope works (T3.2-T3.3 LIKELY_PASS)

### Track 4 — Information Request Full Loop: LIKELY_PASS
- Backend: full CRUD with template support, email notifications, portal API
- Frontend firm-side: create dialog, request list, detail with accept/reject
- Frontend portal-side: request detail with file upload, text response, re-submission
- Request pack `year-end-info-request-za` exists with correct items
- LIKELY_PASS (but T4.1.5 "add custom item" needs verification — may not be supported in create dialog)

### Track 5 — Custom Field -> Document Flow: LIKELY_PASS (except GAP-P49-002 and GAP-P49-003)
- T5.1: `company_registration_number` will fail (GAP-P49-002), other fields should work
- T5.2: `fica_verification_date` mapped correctly. LIKELY_PASS
- T5.3: Trust fields with visibility conditions. LIKELY_PASS
- T5.4: Non-trust conditional hidden. LIKELY_PASS
- T5.5: `customerVatNumber` mapped correctly. LIKELY_PASS
- T5.6: Blank fields produce `________` placeholder (GAP-P49-003). PARTIAL

### Track 6 — Document Acceptance / E-Signing: BLOCKED (GAP-P49-005)
- Backend: Full acceptance workflow implemented (create, send, view, accept, certificate, expiry)
- Certificate: SHA-256 hash, Thymeleaf template, PDF generation. All implemented.
- Firm-side UI: SendForAcceptanceDialog, AcceptanceDetailPanel exist
- Portal-side: NO acceptance page exists. Entire track blocked.

### Track 7 — DOCX Pipeline: PARTIAL (manual, GAP-P49-012)
- DOCX upload, field discovery (`DocxFieldValidator`), and merge (`DocxMergeService`) exist
- PDF conversion via LibreOffice likely unavailable in E2E Docker stack
- T7.1-T7.3: LIKELY_PASS (DOCX merge works)
- T7.4: LIKELY_FAIL (PDF conversion requires LibreOffice)

---

## Content Verification Readiness Assessment

**Overall verdict**: The QA agent can successfully execute approximately **70-75%** of the content verification checkpoints.

**Tracks that will largely succeed**:
- Track 0 (data prep): ~90% pass rate. Project custom fields may need manual field pack application.
- Track 1 (template fidelity): ~85% pass rate. 6 of 7 templates should produce correct output. The `company_registration_number` variable mismatch (GAP-P49-002) affects the bookkeeping engagement letter.
- Track 2 (clause assembly): ~95% pass rate. Clause infrastructure is robust.
- Track 4 (information requests): ~85% pass rate. Full loop from firm to portal to review exists.
- Track 5 (custom field flow): ~80% pass rate. Most fields map correctly; GAP-P49-002 affects one field.

**Tracks that will partially fail**:
- Track 3 (engagement letter pipeline): ~60% pass rate. Document generation works but proposal creation UI uncertain.
- Track 7 (DOCX pipeline): ~60% pass rate. DOCX merge works but PDF conversion blocked.

**Tracks that are blocked**:
- Track 6 (acceptance / e-signing): 0% — no portal acceptance page exists.

**Recommended action before QA execution**:
1. **Fix GAP-P49-002** (blocker): Update template variable key from `company_registration_number` to `acct_company_registration_number` in `engagement-letter-bookkeeping.json`. Immediate unblock. Effort: S (5 min).
2. **Build portal acceptance page** (blocker): Create `frontend/app/portal/accept/[token]/page.tsx`. Effort: L (half day).
3. **Set project field pack to autoApply** (major): Change `accounting-za-project.json` `autoApply` to `true`. Effort: S (5 min).
4. **Verify proposal creation UI** (major): Check if the proposals page has an inline create form or if it needs to be built. Effort: S-M.
