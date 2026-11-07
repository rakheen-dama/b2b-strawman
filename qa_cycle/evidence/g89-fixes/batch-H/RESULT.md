# G89 Batch H: browser verification (PR #1750, main 4f3323525)

- **Stack:** Keycloak dev stack. Firm app http://localhost:3000, gateway http://localhost:8443, org `mathebula-partners`, tenant schema `tenant_5039f2d497cf`. Backend, gateway, frontend and portal were all healthy (`svc.sh status`).
- **Date:** 2026-11-07 (SAST), walk run 22:41–22:56.
- **Tools:** Playwright MCP with a 1440×900 viewport. Thandi used the default context. Carol used an isolated context named `carol`.
- **Changes:** none to code. No SQL writes. SQL was read-only.

## Result

| Gap | Status | Observation | Evidence |
|---|---|---|---|
| **F52-list** (matter Generated documents) | PASS | Carol had no VIEW_TRUST. On Lerato RAF › Work › Generated documents, the "Statement of Account" row (Thandi, 7 Nov 2026, 5.7 KB) showed `Download PDF` **disabled**, wrapped in `data-testid=trust-locked-download`. Hover tooltip: **"Trust details are not available"**. The non-trust "Matter Summary Report" row kept Download enabled, and clicking it downloaded `matter-summary-report-lerato-mthembu-raf-claim-mva-2025-11-03-2026-11-07.pdf` (valid `%PDF-1.6`, 1388 B). | 02, 06 |
| F52-list (matter Documents panel) | PASS | Work › Documents: the linked SoA file `statement-of-account-lerato-…-2026-11-07.pdf` showed `Download` **[disabled]** and trust-locked. The matter-summary-report row was enabled. | 06 |
| F52-list (client page, Client Trust Statement) | PASS | Lerato client › Matters › Generated documents. The page shows "No saved documents — 1 unsaved hidden". After clicking **Show unsaved (1)**, the "Client Trust Statement · Not saved · Thandi Mathebula · 7 Nov 2026 · 4.6 KB" row showed `Download PDF` **[disabled]**, tooltip **"Trust details are not available"**. | 07 |
| F52-list (closed matter, Sipho) | PASS | All 4 SoA rows (Thandi ×3, Bob ×1) were locked. The Matter Closure Letter and Engagement Letter rows downloaded as normal. | 16 |
| F52-list, Power of Attorney | DEFERRED (not needed) | No PoA exists. Carol cannot generate one: the only generate control she sees is "Generate Statement of Account". The non-trust controls above (Matter Summary Report, Closure Letter, Engagement Letter) cover "non-trust still downloads". The PoA over-flag rule is not exercised in the browser. | — |
| **F52-direct** (Carol) | PASS | Browser navigation to `http://localhost:8443/api/generated-documents/ec0783b9-…/download` (Lerato SoA) returned **403** `{"title":"Trust details are not available","detail":"This document contains trust account details, which your role cannot view."}`. The same URL with `b0e34495-…` (Client Trust Statement) returned **403** with the same body. `/api/documents/a0e3432f-…/presign-download` (the SoA's linked document) returned **403** with the same body. Ordering check: `/api/generated-documents/de372cea-…/download` (Khumalo Offer to Purchase, Carol not on the matter) returned **404** "Matter not found". Control: `5439af3b-…` (Matter Summary Report) returned **302** to LocalStack. | 03, 04, 05 |
| F52-direct (Thandi, control) | PASS | Navigating to the SoA URL `ec0783b9-…/download` returned **302** to `http://localhost:4566/docteams-dev/org/tenant_5039f2d497cf/generated/737912d3-…` and then **200** (the PDF viewer opened). The CTS `b0e34495-…/download` returned **302** to LocalStack. | 21 |
| **C2-Withheld** | PASS | Configuration B (see setup). Carol's Lerato RAF › Statements of account showed the columns GENERATED · CLOSING BALANCE OWING · TRUST BALANCE HELD · ACTIONS, and the row `7 Nov 2026 · R 0,00 · **Withheld** · Download`. Download was **disabled**, with the tooltip "Trust details are not available". The list response (`GET /api/matters/e0f25d3f-…/statements`, 200) carried `"trustBalanceHeld":null,"trustWithheld":true,"containsTrustData":true,"pdfUrl":"/api/generated-documents/ec0783b9-…/download"`, so the F55 pdfUrl is fixed. Thandi sees the same row as `7 Nov 2026 · R 0,00 · **R 7 000,00** · Download`. Download is enabled and downloaded `statement-ec0783b9-….pdf` (valid PDF, 5816 B). | 14, 17 |
| **F53/F54** (matter access) | PASS | Carol is not on the Khumalo matter `a9f6a004-…`, whose team is Thandi only. Opening it in the UI at `/matters/a9f6a004-…`, with `?tab=statements`, `?tab=generated` or `?tab=documents`, rendered only **"This matter isn't on your list. It may not exist, or you haven't been added to its team. Ask the matter lead or a firm admin to add you. Back to Matters"**. No tab content and no generate controls rendered. Direct GETs via the gateway: `/api/templates/readiness?entityType=PROJECT&entityId=a9f6…` → **404** "Matter not found". `/api/generated-documents?entityType=PROJECT&entityId=a9f6…` → **404**. `/api/matters/a9f6…/statements` → **404**, and `/statements/{id}` → **404** (both in config B, with VIEW_LEGAL). Control: readiness for Lerato (Carol is on it) → **200**. Generate, preview, generate-docx, statement-generate and generated-document DELETE are POST/DELETE calls. They cannot be reached by URL, and the UI renders no control for them on a matter Carol is not on. They are not observed in the browser (backend ITs only). | 09, 10, 11, 15 |
| **F56/F58-FICA** | DEFERRED | Settings › Connections shows **"AI Assistant: Not Configured"**. `org_integrations` has no AI row, and `ai_executions` has 0 rows ever. Lerato's "Verify with AI" button is disabled, with the tooltip **"No pending checklist items to verify."** because Lerato's FICA is complete. No AI run is possible, so I could not observe whether the Client Trust Statement is excluded from the prompt. Note: the Client Trust Statement `b0e34495` has `document_id = NULL` (not saved), so it is not an uploaded client document. A FICA run would need a *saved* Client Trust Statement to exercise the exclusion. | 12, 23 |
| **F56-contract** (contract review, matter not on) | DEFERRED | AI is not configured (as above). The "Review" control exists on matter Documents rows (seen on Lerato, row 06), but Carol cannot reach any document of a matter she is not on through the UI: the matter page is blocked (404 page above). The download and listing of such documents return 404 (see F52-direct and F53). | 06, 23 |
| **Control** (Thandi) | PASS | Generated-documents SoA: no lock, and the download completed (`statement-of-account-lerato-….pdf`, 5816 B). Client CTS: no lock, and the download completed (`client-trust-statement-lerato-mthembu-2026-11-07.pdf`, 4694 B, `%PDF-1.6`). Statements figure: R 7 000,00. Direct URLs: 302. The Khumalo matter opens with Generate Document and Generate Statement of Account controls. | 17, 19, 20, 21, 22 |
| **Console** | NOTE | Thandi: only a Keycloak `favicon.ico` 404. Carol: the expected 403/404 resource errors for the direct URLs, plus **2× 500** on `POST /org/mathebula-partners/clients/ec988df3-…?tab=trust` (see finding N2). | — |

## Setup and teardown

**Carol's role before:** Member (`835bacba-f240-4997-b524-22b68ff6b174`), with no capability overrides.

**Member capabilities**, from read-only SQL and the Roles page: `AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, MANAGE_DISBURSEMENTS, VIEW_LEGAL, VIEW_TRUST`.

**Constraint found (N1).** The Create Role dialog (Settings › Firm › Roles › New Role) offers only 10 capabilities: Financial Visibility, Billing, Matter Management, Team Oversight, Client Management, Automations, View Trust, AI Management, AI Execute, AI Review. These come from `frontend/lib/capabilities.tsx` `CAPABILITY_META`. **VIEW_LEGAL, GENERATE_STATEMENT_OF_ACCOUNT and MANAGE_DISBURSEMENTS cannot be selected.** "Member minus View Trust" therefore cannot be built as a custom role through the UI. I ran two configurations, both set through the UI:

- **Configuration A (custom role):**
  - **Role:** "Associate (no trust)", id **`1f0364c5-5b24-4a41-903f-f812604c963d`**, capabilities `AI_EXECUTE` (the only Member capability the dialog offers apart from View Trust). Assigned to Carol via Settings › Team › Carol › Role.
  - **Used for:** F52-list, F52-direct, the F53 UI 404 page, readiness/list 404s, and FICA.
  - **Effect:** in this configuration the Statements tab showed "Failed to load statements. Insufficient permissions for this operation", and `/api/matters/{khumalo}/statements` returned **403** (the capability gate fires before the matter check), because VIEW_LEGAL was missing. Screenshot 08.
- **Configuration B (Member + override):**
  - **Setup:** Carol back on Member, with View Trust unticked in the member panel, which saves the override `-VIEW_TRUST`. Effective capabilities are exactly Member minus View Trust.
  - **Used for:** C2-Withheld, the F54 statements 404s and a re-check of the SoA 403.
  - **Evidence:** screenshot 13. Read-only SQL showed `member_capability_overrides = (3498b6c3-…, -VIEW_TRUST)`.

**Teardown (UI):**
- In Settings › Team › Carol, I ticked View Trust back on and saved. Carol is on Member with no override (screenshot 24).
- In Settings › Firm › Roles, I deleted "Associate (no trust)" and confirmed "Are you sure you want to delete the role "Associate (no trust)"? This action cannot be undone." (screenshot 25).

**Carol's role after:** Member (`835bacba-…`), no overrides.

Proof (read-only SQL, after teardown):

```
select m.email, m.org_role_id, r.name from tenant_5039f2d497cf.members m join tenant_5039f2d497cf.org_roles r on r.id=m.org_role_id where m.email like 'carol%';
 carol@mathebula-test.local | 835bacba-f240-4997-b524-22b68ff6b174 | Member

select count(*) from tenant_5039f2d497cf.member_capability_overrides;            -> 0
select id,name,is_system from tenant_5039f2d497cf.org_roles;                     -> Owner, Admin, Member (3 rows, all system)
select count(*) from tenant_5039f2d497cf.org_role_capabilities
  where org_role_id='1f0364c5-5b24-4a41-903f-f812604c963d';                      -> 0
Member caps: AI_EXECUTE, GENERATE_STATEMENT_OF_ACCOUNT, MANAGE_DISBURSEMENTS, VIEW_LEGAL, VIEW_TRUST (unchanged)
```

**Matter teams** (from the UI Team tab and read-only SQL):

| Matter | Team |
|---|---|
| Lerato RAF | Bob (Lead), Carol (Member) |
| Sipho (CLOSED) | Bob (lead), Carol (member) |
| Khumalo | Thandi (lead) only |
| Moroka (CLOSED) | Thandi, Bob, Carol |
| Nomvula (CLOSED) | Thandi, Bob, Carol |

## Data created

- The custom role "Associate (no trust)" (`1f0364c5-…`) was created and then **deleted**.
- The override `-VIEW_TRUST` on Carol was created and then **removed**.
- No documents were generated or deleted. `generated_documents` had 18 rows before and after. `ai_executions` had 0 rows before and after.
- I downloaded 4 PDFs locally into `.playwright-mcp/` to prove the downloads, then deleted them. The temporary screenshot folder `.playwright-mcp/g89h/` was deleted after its contents were copied here.

## Side findings (not Batch H regressions; for triage)

- **N1 (LOW/MEDIUM, product):** the custom-role and member-override UIs (`CAPABILITY_META`, 10 entries) cannot grant VIEW_LEGAL, GENERATE_STATEMENT_OF_ACCOUNT, MANAGE_DISBURSEMENTS, or the other legal and trust capabilities (MANAGE_TRUST, APPROVE_TRUST_PAYMENT, CLOSE_MATTER and so on). A firm cannot build a "Member without trust" associate role. It can only use the `-VIEW_TRUST` override on Member.
- **N2 (LOW):** for a member without VIEW_TRUST, the client page's Fees tab (`?tab=trust`) runs a server action that calls `GET /api/trust-accounts`. That call returns 403, the error is not handled and surfaces as **HTTP 500** (frontend log: `Error [ApiError]: Insufficient permissions … instance: '/api/trust-accounts'`). The panel then reads **"Trust Balance — No trust account configured. Set up a trust account in settings to track client funds."** That message is misleading: a trust account exists, and the reader simply lacks VIEW_TRUST.
- **N3 (LOW, cosmetic):** after a role change in the Team member panel, the members table still showed "Member" until reload. After reload, the Role cell showed the raw slug **`org:associate-no-trust`** instead of "Associate (no trust)".

## Screenshots

00a roles before (Member caps) · 00b create-role dialog · 00c custom role created · 00d assign Carol · 01 Lerato team · 02 SoA locked + tooltip · 03 direct SoA 403 · 04 direct CTS 403 · 05 Khumalo download 404 · 06 matter Documents locked · 07 client CTS locked + tooltip · 08 config A Statements (403 without VIEW_LEGAL) · 09 Khumalo matter 404 page · 10 Khumalo `?tab=statements` 404 page · 11 readiness 404 · 12 FICA Verify disabled · 13 Member −View Trust override · 14 Carol Statements "Withheld" · 15 Khumalo statements 404 (config B) · 16 Sipho locked SoAs · 17 Thandi Statements R 7 000,00 · 19 Thandi SoA enabled · 20 Thandi CTS enabled · 21 Thandi direct 302→PDF · 22 Thandi Khumalo · 23 Connections "AI Assistant Not Configured" · 24 teardown Carol Member · 25 teardown role deleted
