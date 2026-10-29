# Phase 88 — Vetted gap register (capstone follow-up, root-cause grouped)

**Source:** `tasks/phase87-capstone-findings.md` (59 gaps, 53 FAILs, 24 DEFERREDs) and `qa_cycle/evidence/phase87-capstone/gaps.md`.
**Vetting date:** 2026-10-22, code at main `50b88ed5c`.
**Method:** ten read-only research agents, one per suspected root-cause cluster, each reading the code the gap register pointed at and the code it did not. Every claim below is marked **CONFIRMED** (read in code, file:line in the dossier) or **HYPOTHESIS** (needs a reproduce step). The full dossiers live in `tasks/phase88-research/R1…R10` and are the authority for file:line detail; this file is the map.

**Why this file exists.** The capstone register is a walker's view: one row per observed symptom, with suspected owners. Fix agents need the opposite: one row per *root cause*, with the symptoms it explains, the exact code, the tests that pin current behaviour, and the product decision (if any) that gates the fix. Grouping by root cause turns 59 symptoms into **22 fix units**, five of which need a founder decision before an agent may start.

**Status vocabulary** (root `CLAUDE.md` §3 and §4): every fix PR is reproduce-first. The reproduction for UI gaps is the browser on the :3001 mock-auth stack, or the Keycloak stack for the gaps marked *KC-only*. `PASS` means observed.

---

## 1. Corrections and additions to the capstone findings

The research changed the picture in these places. Fix agents must work from this list, not from the "suspected owner" column of `gaps.md`.

| # | Gap(s) | Capstone said | Vetted finding | Dossier |
|---|---|---|---|---|
| 1 | G-D11 a, G-D13 | `lead` vs `LEAD` mismatch between template instantiation and three readers | CONFIRMED, and wider: 13 lowercase readers and **4 uppercase readers** (`NotificationService:359,512,615`, `AssignMemberActionExecutor:25,94`, `CreateTaskActionExecutor:105`). Lead notifications and the automation `PROJECT_OWNER` recipient silently never resolve for normally-created matters. `LegalDemoDataSeeder:151` writes a third value, `CONTRIBUTOR`. Column is `varchar(50)`, no CHECK. | R1 |
| 2 | G-B3 | Members panel unreachable since Epic 599 | CONFIRMED, plus the second half: template instantiation never auto-adds the creator (unlike `ProjectService.createProject`), so 0-member matters are the normal outcome, not an edge case. The panel, dialogs, API client and the `/api/matters/{id}/members` endpoints all still exist and work. Latent: `POST …/members` has no capability gate; `transferLead` can only be called by the current lead, so Owner/Admin cannot transfer. | R1 |
| 3 | G-D11 b, G-D21 Debtors | Debtor rule excludes SENT fee notes with no due date | CONFIRMED, but the root is upstream: **no code path computes `due_date`** outside batch billing (`Invoice.applyDefaults`, only caller `BillingRunGenerationService:327`), and **no firm payment-terms setting exists**. The Fee notes page "Total Outstanding" is a client-side sum over APPROVED+SENT with no due-date predicate, which is why the two surfaces disagree. The same rule drives `CashDigestService`, `InvoiceAgingReportQuery`, `CollectionsScanService`, and the PDF's blank "Due Date:" (G-C7 c). | R2 |
| 4 | G-D11 c, G-D21 FICA, G-B10 | FICA read from information requests, not checklists | CONFIRMED. Two parallel FICA mechanisms exist. The 90-day scenario and the lifecycle auto-activation (`ChecklistInstanceService.checkLifecycleAdvance:522-550`) both use **checklists**; `FicaStatusProjector:46-86` reads only `information_requests` on `fica-onboarding-pack` templates, which nothing in the walk creates. Every display (Home, Clients table, matter card, saved view, KPI) reads the projector. | R2 |
| 5 | G-B8, G-D9 | Feed formatter + entity scoping | CONFIRMED and sharpened: `AuditEventRepository.findByProjectId:56-77` matches **only** `details->>'project_id'`. `acceptance.*`, `trust_*.recorded/approved/rejected`, `invoice.created/approved` publishers never write that key. ~40 reachable event keys have no template (`ActivityMessageFormatter:153` raw fallback). Home uses a **second, cruder formatter** (`DashboardService.buildActivityDescription:920-938`), which is the G-D10 "performed invoice.sent" text. | R3 |
| 6 | G-B2 | Client audit tab filters entity_type=customer | CONFIRMED; checklist item events carry no client id at all (only the instance event carries `details.customerId`). `DatabaseAuditService.streamForCustomer:523-548` already has the child-join shape to reuse. | R3 |
| 7 | G-D6 | Audit filter needs raw keys | CONFIRMED; plus unused facet endpoints already exist (`AuditEventController:117-139`, FE clients in `lib/api/audit-events.ts:171-192`) and `GET /api/members`. 68 distinct entity literals enumerated with a legal-word map in R3 §4. ADR-342 explicitly allows UI-side words. | R3 |
| 8 | G-B13 | Automation rule trigger mapping | CONFIRMED by design: `TriggerTypeMapping:35-37` maps `DocumentUploadedEvent → DOCUMENT_ACCEPTED` ("upload is treated as acceptance"); rule `document-review-notification` seeds **enabled**. The double recipient is item 1 (role lookup `"lead"` misses `"LEAD"` → falls back to all admins/owners). | R3 |
| 9 | G-B5, G-C7 a–c | Template rendering context | CONFIRMED, and larger than a template edit: `OrgSettings` has **no firm address, phone, email, bank or signatory fields anywhere**; `org.bankingDetails` and `org.principal_attorney` are dangling template keys. Brand colour: `document-default.css:20` references `var(--brand-color)` that nothing defines and OpenHTMLToPDF has no CSS variables. Engagement-letter JSONs simply never reference `org.logoUrl`. Pack JSON edits reach **fresh tenants only** (`AbstractPackSeeder:143-153`). | R4 |
| 10 | G-C7 d/e | Draft preview raw numbers | CONFIRMED: the draft Preview is a **second renderer** (Thymeleaf `invoice-preview.html`), not the Tiptap pipeline. Canonical formatters exist (`VariableFormatter.formatCurrency` en-ZA; `packages/shared/src/format.ts`); tariff dialogs reimplement `R ${toFixed(2)}`; `TrustBalanceZeroGate:48` prints `"R%s"`. | R4 |
| 11 | G-C4 | SoA preview feedback loop | CONFIRMED: `a4-preview-wrapper.tsx:18-50` sets child width from its own padding-inclusive `clientWidth`. HYPOTHESIS for why only the SoA dialog loops: its grid item is a `<form>` (min-content grows) while the generate dialog's is an `overflow-y-auto` div. | R4 |
| 12 | G-B18 | OTP clause associations | CONFIRMED: `conveyancing-za-clauses/pack.json:288` `"templateAssociations": []`; the OTP template embeds 7 `clauseBlock`s that resolve to `<!-- clause not found -->`. Existing tenants need a reconcile. | R4 |
| 13 | G-D14 | Closure letter raw amounts | CONFIRMED and a real arithmetic bug: `MatterClosureContextBuilder:116` sums VAT-inclusive totals including disbursement lines, then `:124` adds disbursements again. | R4 |
| 14 | G-B7 | X-Frame-Options; "check same-origin prod first" | CONFIRMED a **production bug in both topologies**: VPS iframes `api-dev` from `portal-dev` (cross-origin), AWS staging is same-origin but Spring's default is `DENY`, which blocks same-origin too. The PDF endpoint sits on the **main @Order(2) chain**, not the portal chain. No `.headers(...)` config exists anywhere. | R5 |
| 15 | G-D1 | Portal login org resolution | CONFIRMED structural: `portal_contacts` is per-tenant with no global email index; the global `portal` schema holds only id-keyed projections. Only 2 of 11 email builders carry org context. One email can legitimately exist in several firms. | R5 |
| 16 | G-D19 | Portal card has no status | CONFIRMED frontend-only: `PortalMatterResponse` already carries `status` and the list page already filters on it. | R5 |
| 17 | G-B19 | Decline is a product decision | CONFIRMED a full feature: no DECLINED enum value, column, endpoint, audit event or read-model sync exists. | R5 |
| 18 | G-C5, G-D14 (emails) | Implicit client emails | CONFIRMED: `StatementService.generate` always saves the SoA as `Visibility.PORTAL`; the closure letter is born PORTAL; the handler's allowlist `portalNotificationDocTypes` defaults to exactly those two and has **no settings UI**. `PortalDocumentNotificationHandler:300` still builds `/projects/`. | R5 |
| 19 | G-C9 | No write-off UI | CONFIRMED UI-only: endpoint, DTO, capability `WRITE_OFF_DISBURSEMENTS`, audit event and tests all exist; `writeOffDisbursement` in `lib/api/legal-disbursements.ts:185` has zero callers. `restore()` (undo) has no endpoint. | R6 |
| 20 | G-B11 | Dialog filters DISBURSEMENT_PAYMENT, ledger writes PAYMENT | CONFIRMED, and `DISBURSEMENT_PAYMENT` is **not a legal value** of the ledger CHECK constraint; `DisbursementService.EXPECTED_TRUST_TRANSACTION_TYPE = "PAYMENT"`. The dialog is wrong, not the ledger. | R6 |
| 21 | G-B12 | No unbilled summary | CONFIRMED; `GET /api/legal/disbursements/unbilled?matterId=` already returns totals and has a frontend action used only by the invoice editor. | R6 |
| 22 | G-C10 | Line description drops the narrative | CONFIRMED; `time_entries.description` is never read; no test pins the current format. | R6 |
| 23 | G-C11 | No amount/method on Record Payment | CONFIRMED backend-side: request body is `paymentReference` only; amount is always `invoice.total`; no `amount_paid`/`balance_due`/PARTIALLY_PAID. Latent: trust fee-transfer, webhook and Xero paths all mark PAID regardless of amount. | R6 |
| 24 | G-C6 | Pre-check vs send | CONFIRMED: backend is already correct (INDIVIDUAL → WARNING, COMPANY/TRUST → block); the **message text** is severity-blind on both sides. Copy fix only. | R6 |
| 25 | G-B9 | Promoted fields not inline | Re-classified: "promotion" means structural columns (`matter_type → work_type`, already in the header). `case_number`/`court_name` are plain pack fields; **no pinned/header attribute exists**. This is a small feature (header-fields strip), and scenario step 5.2's word "promoted" is wrong. | R7 |
| 26 | G-B14 | URL tab wins over click | CONFIRMED; client tabs already do it right (`customer-grouped-tabs.tsx:60-61,135-142`). Port the pattern. | R7 |
| 27 | G-A6 | "Coming Soon" conflict card | CONFIRMED stale stub from Epic 371A (2026-03-19); the real module shipped two weeks later. The dialog has no opposing-party input, so inline it could only check the client. | R7 |
| 28 | G-D2 | Close not offered on COMPLETED | CONFIRMED one-line (`matter-header-card.tsx:88`). `sidebar-lifecycle-action` appears in no Playwright spec, only scenario prose. | R7 |
| 29 | G-D3 | Retention "isn't configured" | CONFIRMED design drift on four counts: the data-protection years column is **NULL** because the insert bypasses `DEFAULT 5`; `setLegalMatterRetentionYears` has zero callers and no settings UI; `retentionEndsAt` is computed into the closure response and never persisted; `complete()` starts the clock although ADR-249 says `close()` should. | R7 |
| 30 | G-D7 | Admin offered override | Re-rooted: `lib/capabilities.tsx:119-121` `hasCapability` short-circuits `isAdmin \|\| isOwner` and ignores `Capability.OWNER_ONLY`; the server action `matter-closure-actions.ts:51-53` also grants admins. The correct idiom exists for `FINANCIAL_RECONCILE` at `invoices/[id]/page.tsx:145`. This is a **capability-helper bug** that any owner-only capability can hit. | R7 |
| 31 | G-C2 | `.map` of undefined | CONFIRMED: `adverse-parties/actions.ts:136-142` returns `.content` of a flat list; `fetchCustomers()` already handles both shapes. One-line fix. | R9 |
| 32 | G-D4 | Export fails; BFF hypothesis | CONFIRMED: `getAuthToken()` throws unconditionally in BFF mode (`keycloak-bff.ts:121-125`); the `NEXT_REDIRECT` is swallowed. The same helper **also breaks the trust client-ledger statement PDF** (`client-ledgers/actions.ts:141-153`, unfiled). `exportAuditPdf` is the working template. KC-only reproduction. | R9 |
| 33 | G-D5 | My work misses court dates | CONFIRMED; `GET /api/diary` already aggregates them with a `feeEarnerId` filter. Court dates attribute to the **matter lead** only. | R9 |
| 34 | G-D12 | Invitation status not reconciled | Re-rooted: `listInvitationsBff()` calls `/api/invitations` **without `?status=PENDING`** (backend supports it), and the seat line adds all invitations to the member count. Stored status is most likely ACCEPTED already (HYPOTHESIS: verify in DB on repro). | R9 |
| 35 | G-A1 | Detail view missing | CONFIRMED nothing is hidden; only review metadata (OTP-verified, reviewed-by/at) is not on the row. Amend the step. | R9 |
| 36 | G-C1 | No Government type | CONFIRMED frontend-only: `party_type` is a free varchar with no enum or CHECK; four frontend lists. | R9 |
| 37 | G-D15 | Undetermined attribution | **Determined, pre-existing:** `org/[slug]/layout.tsx:131` and `<main>` (`:146`) lack `min-w-0`; 616A documented it and worked around with `@container`; 615C/615D added local `min-w-0`. One-line shell fix retires three workarounds. | R8 |
| 38 | G-A4/A5/B1/C3/D10 | "Sampled" copy clusters | Full census: **≈589 user-visible sites** (261 frontend/portal, 24 pack JSON + email HTML, 26 backend labels/registries, 278 backend exception/validation messages of 4 + 146 distinct strings). Pack and report-definition text is seeded once per tenant → needs a reconcile for existing tenants. Dead file `frontend/lib/messages/en-ZA-accounting/common.json`. | R8 |
| 39 | G-D16 | PRO badge | CONFIRMED and wider: `tier` prop, `isStarter`, "Upgrade to Pro" block and six `disabled={…isStarter}` guards in `IntegrationCard.tsx`; `tier` derived from `billing.status` in the Xero tab. | R8 |
| 40 | Carried six Playwright | "one PR each" | **None is a product regression.** Cross-cutting pattern: `getByText(/re/i).isVisible().catch(() => false)` is strict-mode and returns `false` on any 2+ match, defeating four of the six regardless of data. Verdicts: A1 locator/flow drift; A2/A3 flow drift (fee-note creation path changed); A4 flow drift (5-step wizard) + possible seed gap (no trust account); A5 seed gap (no invoices seeded); A6 strict-mode. Default-config: PACK-01, CUST-01 strict/exact; PROJ-02/03 grouped-tab locator; CUST-02 header smart action replaced the dropdown (not a product gap). | R10 |

**Newly surfaced latent defects** (not in the capstone; file as gaps in this phase):
- **N-1** Lead notifications and automation `PROJECT_OWNER` recipients never resolve for normally-created matters (item 1). Fixed by the casing unit.
- **N-2** Trust client-ledger statement PDF export fails in Keycloak mode (item 32). Fixed by the export-helper unit.
- **N-3** `transferLead` refuses Owner/Admin callers; `POST /members` has no gate (item 2). Fixed by the matter-team unit.
- **N-4** Closure letter double-counts disbursements in "Total fees billed" (item 13).
- **N-5** Trust fee-transfer / webhook / Xero mark a fee note PAID regardless of amount (item 23). Own it in the payments unit or document.
- **N-6** HYPOTHESIS: portal Firm-trail whitelist lists `trust_transaction.approved` but the backend emits `trust_<type>.approved`, so the portal may never show trust approvals (R3 aside). Reproduce before filing.
- **N-7** `VerticalModuleRegistry` still lists a dead "Court Calendar" module pointing at `/court-calendar` (R8 §1.3).
- **N-8** `restore()` (undo write-off) exists in the entity with no endpoint (R6).
- **N-9** (628I grep, outside the R8 census) The POPIA processing-register seed `datarequest/ProcessingActivityService.java:84,97` creates "Financial Records — Invoices, payment records…" and the category "Project Documentation". It renders on Settings › Firm › Data protection (`processing-register-table.tsx`). Rows are keyed by `category` (`existsByCategory`), so renaming needs a reconcile on existing tenants (ADR-356 shape). Evidence: `$EV/628I/grep.md`.
- **N-10** (628I grep) `integration/accounting/sync/TrustBoundaryGuard.java:61` "Invoice is flagged as trust-related…" reaches `markBlockedTrustBoundary` → `SyncLogTable.tsx:139-144` (Integrations sync log). R8 row 98 fixed `:125` in the same class.
- **N-11** (628I grep) `orElse("Unknown Project")` fallback names feed notification text at `member/ProjectMemberService.java:109` and `budget/BudgetCheckService.java:129`. They are effectively dead (the project row would have to vanish mid-transaction).
- **N-12** (628I grep) `automation/executor/UpdateStatusActionExecutor.java:72` ActionFailure "Customer status update not yet supported" (automation run log). It is reachable only for an UPDATE_STATUS action that targets CUSTOMER.

---

## 2. Fix units (the shape of the phase)

Each row is one fix unit: a reproduce-first PR, or a small same-class cluster where authorised (CLAUDE.md §7). "Gate" = founder decision required before an agent starts (§4). Severity is the highest of the gaps it closes.

| Unit | Closes | Sev | Root cause (one line) | Layer | Migration | Gate | Dossier |
|---|---|---|---|---|---|---|---|
| **U1 Project-role casing** | G-D11 a, G-D13, N-1, half of G-B13 | MED | `"LEAD"` written by template instantiation + demo seeder vs `Roles.PROJECT_LEAD="lead"` read by 13 sites and `"LEAD"` by 4 | BE | tenant V4: lower-case + `CONTRIBUTOR→member` + optional CHECK | — | R1 |
| **U2 Matter team** | G-B3, N-3, the 8 FAILs + 2 DEFERREDs behind "Carol can't" | HIGH | No membership path: creator not auto-added on instantiate; Team tab removed in Epic 599 with no replacement | BE+FE | no | D1 (team rules) | R1 |
| **U3 Fee-note due date** | G-D11 b, G-D21 Debtors, G-C7 c "Due Date:" | MED | `due_date` never computed; no payment-terms setting | BE(+FE) | tenant V4: `BillingSettings.defaultPaymentTermsDays` + backfill | D2 (terms) | R2 |
| **U4 FICA status source** | G-D11 c, G-D21 FICA, G-B10 | MED | Projector reads information requests; the product's onboarding path is checklists | BE+FE copy | no | — (rec. checklist-first) | R2 |
| **U5 Matter feed correlation + formatter** | G-B8, G-D9, G-D10 (feed text), G-D8 | MED | Publishers omit `project_id`; ~40 untemplated keys; Home's second formatter | BE | no | D3 (G-D8 copy vs amend) | R3 |
| **U6 Audit trail usability** | G-D6, G-B2, G-D10 (audit labels), E.9 | MED | Free-text filters; raw literals rendered; registry `titleCase` labels; client tab can't join checklist children | BE+FE | no | — | R3 |
| **U7 Automation "Document accepted"** | G-B13 | LOW | Upload mapped to DOCUMENT_ACCEPTED by design; rule seeds enabled | BE pack | pack reconcile | D4 (semantic) | R3 |
| **U8 Firm letterhead + branding in PDFs** | G-B5, G-C7 a–c, wow moment 6 | MED | No letterhead/bank fields exist; brand colour never injected; engagement letters omit logo | BE+FE+packs | tenant V4: `LetterheadSettings` embeddable; pack reconcile | D5 (fields in scope) | R4 |
| **U9 Money formatting + draft preview** | G-C7 d/e, G-D14 amounts, N-4, G-C12 (SoA key), `TrustBalanceZeroGate` | MED | Second Thymeleaf renderer; three ad-hoc formatters; raw enum serialisation; closure arithmetic | BE+FE | no | — | R4 |
| **U10 Document generation polish** | G-B6, G-B16, G-B18, G-C4 | MED | Row persisted on download; subject literal; empty `templateAssociations`; preview scaler width | BE+FE+pack | pack reconcile (OTP) | — | R4 |
| **U11 Portal firm resolution** | G-D1, the 68.x DEFERREDs | HIGH | No global email→org index; 9 of 11 email links carry no org | BE+portal | **global V2** directory table (pin `BaselineSchemaParityTest`) | D6 (model) | R5 |
| **U12 Acceptance PDF framing** | G-B7, wow moment 2 | HIGH | Spring default `X-Frame-Options: DENY` on the main chain; prod-affecting | BE | no | — | R5 |
| **U13 Client notification intent** | G-C5, G-D14 (emails), G-D19 | MED | SoA/closure letter born PORTAL + handler allowlist with no UI; `/projects/` link; card lacks status | BE+FE+portal | no | D7 (opt-in model) | R5 |
| **U14 Disbursement actions** | G-C9, G-B11, G-B12, the 45.x DEFERREDs, N-8 | HIGH | Write-off UI never built; dialog filters a non-existent type; summary endpoint unused | FE (+tiny BE for restore) | no | — | R6 |
| **U15 Fee-note lines, payments, pre-check** | G-C10, G-C11, G-C6, N-5 | HIGH | Line builder ignores narrative; payment DTO has no amount/method and no balance model; severity-blind copy | BE+FE | tenant V4: `payment_method`, `amount_paid`, `PARTIALLY_PAID` | D8 → **build part payments** (founder, 2026-10-22) | R6 |
| **U16 Matter detail** | G-B9, G-B14, G-A6, G-D2 | MED | No header-fields strip; URL-wins tab state; stale stub card; one-line status guard | FE | no | D9 (header fields; conflict card) | R7 |
| **U17 Retention model** | G-D3 | MED | NULL setting, no UI, end date never persisted, clock starts at complete | BE+FE | tenant V4 only if `retention_ends_on` is persisted | D10 (frozen vs computed) | R7 |
| **U18 Owner-only capabilities** | G-D7 | MED | `hasCapability` ignores `OWNER_ONLY`; server action mirrors it | FE | no | — | R7 |
| **U19 Export helper (BFF)** | G-D4, N-2 | MED | `getAuthToken()` throws in BFF mode; redirect swallowed | FE | no | — | R9 |
| **U20 Small behaviour fixes** | G-C2, G-D5, G-D12, G-C1 | MED | Flat-list `.content`; task-only deadlines; missing `status=PENDING`; four FE enum lists | FE | no | — | R9 |
| **U21 Terminology census + shell** | G-A2, A3, A4, A5, A7, B1, B4, C3, C12, D10, D15, D16, D17, D18, D20, E.8 | LOW | 589 literal sites; breadcrumb map; settings-nav labels; shell `min-w-0`; diary row layout; tier remnants | FE+BE+packs | pack/report reconcile for existing tenants | D11 (tab wording) | R8 |
| **U22 Playwright suites** | carried six, PACK-01, PROJ-02/03, CUST-01/02, portal seed ×3, P-1 | — | Strict-mode `isVisible` pattern; flow drift; seed gaps; path-regex filter | e2e | no | — | R10 |
| **U23 Decline on acceptance** (founder: build) | G-B19 | LOW→feature | No DECLINED state, column, endpoint, audit event, firm notification, read-model sync or UI | BE+portal+FE | tenant V4: `declined_at`, `decline_reason` on acceptance requests | — | R5 |
| **U24 Comment attachments** (founder: build) | G-B15 | LOW→feature | `comments` has body + visibility only; `documents` already carry `project_id`/`customer_id`/`scope`/`visibility`/`source` — a comment↔document link is missing | BE+FE | tenant V4: `comment_attachments(comment_id, document_id)` | — | — |

**Founder decision 2026-10-22:** G-B19, G-C11 (full part payments) and G-B15 are **built**, not amended (units U23, U15, U24). Remaining decision-only items: G-A1 (amend step), G-B17 progressive conveyancing fields (keep, amend step), G-C8 Approve/Send (wording), and the scenario wording list in §5.

---

## 3. Dependency and ordering

```
U1 casing ──► U2 matter team ──► (re-walk Days 1–7, 62.1)
U3 due date ─┐
U4 FICA ─────┴► re-shoot Home / Matters / Clients deck screenshots (C.10)
U8 letterhead ──► U9 formatting ──► U10 polish        (U8 adds the settings U9/U10 templates print)
U11 portal resolution ──► U13 notification intent      (U13's links need the org-aware builder)
U12 framing: independent, do first (prod-affecting, one config class)
U14 disbursements → U15 fee notes                       (write-off before the Day-75 closure re-walk)
U18 capabilities: independent, do early (security-adjacent)
U19 export, U20 small fixes, U16 matter detail: independent
U21 terminology: last among code units (touches ~130 files; rebases cheaply only when little else is open)
U22 Playwright: after U14/U15 (A2/A3/A5 need the fee-note path stable) and after U21 (labels)
```

Migration budget: **one tenant migration V4** carrying U1 (role normalisation), U3 (payment terms + backfill), U8 (letterhead columns), U15 (payment method) and U17 (if persisted) — written by the first unit to merge and amended by later units only if they land before V5 is needed; otherwise take the next free number (memory: `feedback_migration_numbers_next_free`). **One global migration V2** for U11 if the directory model is chosen; `BaselineSchemaParityTest` must be pinned to V1 first.

---

## 4. Decisions needed from the founder (gates)

Recommendations are mine; the dossiers give the trade-offs.

| ID | Unit | Question | Recommendation |
|---|---|---|---|
| D1 | U2 | Who manages a matter's team; is the creator auto-added; do Members see only their matters? | Owner/Admin or current lead may add/remove/transfer; creator auto-added as lead when no lead is chosen; Members keep membership-only visibility (ADR). Team as a sub-tab under Details; `?tab=members` aliased. CHECK constraint on `project_role`. |
| D2 | U3 | Payment terms: fixed 30 days or a firm setting; stamp at approve or send; backfill existing SENT rows? | Firm setting, default 30, stamped at **approve** (same moment as numbering). Backfill `issue_date + terms` for APPROVED/SENT rows with NULL. Fee notes "Total Outstanding" aligns to the debtor book (SENT only). |
| D3 | U5 | G-D8: copy the override justification into `matter_closure.closed` details, or amend step 85.17? | Copy it (one `Map` entry, firm-visible only). |
| D4 | U7 | Is "upload = accepted" the intended automation semantic? | No. Rename the rule and trigger label to "Document uploaded" and seed it **disabled**; a real acceptance trigger is out of scope. |
| D5 | U8 | Which letterhead fields; where in Settings? | Address (3 lines + city + postal + country), phone, email, website, VAT no. (exists), bank name / branch code / account no. / account type / reference note, signatory name + designation. Under **Settings › Firm › General** (one form, next to logo and brand colour). Brand colour applies to headings and rule lines. |
| D6 | U11 | Portal sign-in: global email→org directory, or org in every link only? | Both; directory first. One email in N firms → N branded links, generic response text. Keep `?orgId=` as a fast path. |
| D7 | U13 | Client notification on SoA save and matter closure: explicit opt-in, default off? Should SoA be portal-visible before the firm reviews it? | Opt-in checkbox on both dialogs, default off; SoA saved as INTERNAL until shared. Remove the `portalNotificationDocTypes` allowlist in favour of per-action intent. |
| D8 | U15 | Payments: method + explicit full amount now, part payments later? | **Founder chose full part payments now:** `payment_method`, per-payment `amount`, `amount_paid`/`balance_due` on the fee note, `PARTIALLY_PAID` status, debtor book and aging read the balance, trust fee-transfer / webhook / Xero paths respect the amount (closes N-5). |
| D9 | U16 | Header facts for a matter: `case_number` + `court_name` only, or also opposing party / date of instruction? Conflict card: link-out, inline client check, or remove? | Hard-coded legal-za list `case_number`, `court_name`, `opposing_party` (fast; pack flag later). Conflict card → "Run conflict check" link that opens `/conflict-check` prefilled with the client; no inline check. |
| D10 | U17 | Retention end: frozen at close (persist) or computed from the current policy? Does completion start the clock? | Persist `retention_ends_on` at close (ADR-249 literal); re-close resets it; completion no longer starts the clock; 5-year legal-za default, firm-editable under Firm › Compliance. |
| D11 | U21 | "Tariffs" settings tab shows rates; "Xero" tab shows all integrations; nav "Fee runs" vs h1 "Fee notes", "Debtors" vs h1 "Collections". | Rename tab → **Rates** (LSSA tariffs stay at Fees › Tariffs); tab → **Integrations** (keep `id: "xero"` for redirects); h1s follow the nav words (Fee runs, Debtors). |
| D12 | U21 | Audit labels: `"Invoice"` → `"Fee note"` and legal-case the synthesised labels (affects archived CSV/PDF exports)? | Yes; exports are regenerated on demand, not archived by the product. |
| D13 | U22 | Authorise Playwright locator/flow amendments in-slice (611B lesson B-7) and the seed additions (invoices, trust account, Day 3/60/85 portal rows)? | Yes. |

---

## 5. Scenario amendments requiring §6 authorisation

Citation convention: rows are cited as "§5 row 1–15" in table order (top to bottom); see §5a.

All in `qa/testplan/demos/legal-za-90day-keycloak.md` unless noted. Classified per the LZKC-029 style: **W** = wording (product unchanged), **O** = step order, **P** = product decision to keep behaviour.

| Step / line | Class | Change | Basis |
|---|---|---|---|
| 36.1, 45.1 "Billing tab" | W | → Fees group | no such tab |
| 75.1 → 75.2 order; `sidebar-lifecycle-action` | W | COMPLETED matters can be closed (U16 fixes) → drop the Reopen; locator → `close-matter-btn` / `header-lifecycle-actions`; also full-lifecycle `:812` | R7 |
| 0.17, 45.7, 85.9 REST-phrased | W | rephrase as browser-observable or mark read-only SELECT | QA rule |
| 5.2 "promoted fields" | W | → "header fields" (`case_number`, `court_name`, `opposing_party`) | R7 item 25 |
| 5.9 trust-linked disbursement | O | Sipho trust deposit + deeds-office PAYMENT + approval precede 5.9 | R6 |
| 81 access-request detail (G-A1) | W | verify inline row fields | R9 |
| 14.1 comment attachment (G-B15) | P | comment on the uploaded document | product decision |
| 14.4/14.13 conveyancing 10 fields (G-B17) | P | "8 visible; Registration Date and Deed Number appear once the earlier date is filled" | pack design |
| 14.12 / 346 Decline (G-B19) | P | drop Decline unless D-built | R5 |
| 380 Government party type (G-C1) | — | product fix in U20 | R9 |
| 436 Approve → SENT (G-C8) | W | "Approve, then Send fee note" | R6 |
| 85.17 justification (G-D8) | — | product fix per D3 | R3 |
| 480 portal `/home` | W | "/matters loads with Sipho's matters; /home shows fee notes and requests" | R5 Q2, 617B flag |
| Brief count 302 vs 307; E.3 path | W | fix numbers and path | capstone |
| Day 85 digest cadence (portal e2e title) | W | the portal page is read-only; the firm sets cadence | R10 |

### 5a. Proposed amendments awaiting founder authorisation (routed by 628H)

Not authorised. These scenario-versus-product differences surfaced during Phase 88 (walk hand-offs, slice follow-ups, the 628H scout). Under CLAUDE.md §6 none of them may change a scenario file until the founder authorises the row. 628H applied only the 15 rows of §5 above (cited in the scenario annotations as "register §5 row 1–15", numbered in table order) and task 628.18's 61.2 (ADR-350). §5 row 14 was applied for E.3 and the closing "If any checkpoint fails" paragraph only; the "302 vs 307" count appears in no scenario file (only in agent briefs), so there is nothing to change for the count (orchestrator ruling F-3). §5a row 3 (5.4 / wow moment 4 "promoted fields") should be authorised together with §5 row 4 so the file reads consistently. Step ids are primary; line numbers are `main` @ `ebbd97884`, before 628H (628H inserts steps 5.8a–5.8c in the 90-day file, so later 90-day lines move by +3).

| # | Step / line | Proposed change | Class | Evidence (file:line) | Originating slice |
|---|---|---|---|---|---|
| 1 | 90-day 0.A (`:47`); master `demo-readiness-keycloak-master.md` M.6 (`:133-139`) | 0.A: drop the org's `tenant_<12 hex>` schema (from `public.org_schema_mapping`), not `tenant_mathebula*`; M.6: `psql -d docteams`, not `-d app` | W | `SchemaNameGenerator.java:19` ("tenant_" + 12 hex); KC DB `docteams` (`qa_cycle/evidence/phase88-rewalk/mid/session-0.md`, `logs/session0-reset.txt`); master doc is outside 628.18's file list | 623A/623C (G88-M1) |
| 2 | 90-day 3.3 (`:247`) | enter `case_number` / `court_name` right after create on Details › Custom fields (the template dialog has no such inputs) | W | `NewFromTemplateDialog.tsx:305-429` (Matter name, Description, Client, Lead, Reference, Priority, Work type only) | 623A |
| 3 | 90-day wow moment 4 (`:36`) and 5.4 (`:273`) | wow 4: "Sipho's litigation matter with the header facts strip (case number, court)"; 5.4: "header fields" instead of "promoted fields" (wow 4 also wrongly says "RAF claim" for Day 5) | W | `header-field-slugs.ts:14-16`; `matter-header-card.tsx:76-80`; Day 5 = Sipho's matter (5.1) | 628H scout (same phrase as §5 row 4) |
| 4 | 90-day 6.2 (`:293`) | Bob comments on a task (or document) of the matter; there is no matter-level comment thread | W | `comment-section-client.tsx:14` (`TASK` or `DOCUMENT`); `task-detail-sheet.tsx:366`; `documents-panel.tsx:553` | 623A |
| 5 | 90-day 6.3 (`:294`) | "bell shows at least 1 unread" (the earlier member-added and task-assigned notifications also count) | W | walk: 3 unread (`qa_cycle/evidence/phase88-rewalk/mid/days-01-07.md:116`, `screens/6.3-carol-bell-open.png`) | 623A |
| 6 | 90-day 14.13 (`:348`) | allow two shots: Details › Custom fields, and Work › Generated documents (fields and documents sit on different tabs) | W | `matter-tab-groups.ts:24-46`; capstone `qa_cycle/evidence/phase87-capstone/days-08-21.md:37` | 628H scout (capstone 617C) |
| 7 | 90-day 45.8 (`:455`) | badge reads **Written off** (label), not `WRITTEN_OFF` (enum) | W | `disbursement-list-view.tsx:58-59`; `disbursements/[id]/detail-client.tsx:180,296` | 623B |
| 8 | 90-day 45.9 (`:456`), 85.17 filter half (`:544`) | filter Entity type **Disbursement** / **Matter** and Event type `disbursement.written_off` / `matter_closure.closed` (there is no "action" filter) | W | `audit-log-client.tsx:810,833` (Entity type select, Event type combobox); `entity-type-labels.ts:15,25`; `MatterClosureService.java:275-277` | 623B/623C (after 625D) |
| 9 | 90-day 45.11 (`:458`) | detail shows "Written off" + "Write-off reason"; the exact phrase appears only as the audit label | W | `detail-client.tsx:296`; `ActivityMessageFormatter.java:237` | 623B |
| 10 | 90-day 61.1 (`:477`), 62.1 (`:478`) | "Create another client" / "on the new matter": in a full walk Nomvula is the 5th client and 5th matter (Khumalo 14.2/14.3 and Lerato 22.x come first) | W | 90-day `:337-338`, `:373-375` | 623B |
| 11 | 90-day Day-75 pre-state (`:489`) | pick a pre-state reachable after §5 row 5 (Sipho keeps R 3 750 in trust): add a trust refund/fee-transfer step, or close a matter with no trust activity | W | walk: `qa_cycle/evidence/phase88-rewalk/mid/days-61-75.md:23,57` (`TRUST_BALANCE_ZERO` correctly fails) | 623B/623C (G88-M16) |
| 12 | 90-day 75.7 (`:498`), 85.15 (`:542`); full-lifecycle 60.21 (`:829`) | button label **Close matter**, not "Confirm Close" (test id unchanged) | W | `matter-closure-dialog.tsx:520-528` | 623B |
| 13 | 90-day 75.10 (`:501`); full-lifecycle 60.23 (`:831`), 85.3 (`:902`), Day-85 checkpoint (`:909`) | verify the matter's Retention card "Scheduled deletion: <close date + firm years>" (persisted `projects.retention_ends_on`); no `retention_policies` row is written per matter | W | `Project.java:318`; tenant `V8__phase88_retention_comment_attachments.sql:15`; `retention-card.tsx:181-193` | 627C |
| 14 | full-lifecycle 60.20 (`:828`), Day-60 checkpoint (`:837`) | at Step 2 tick **Email the client now** (shares + emails) so Day 61's email and 61.2 hold; the checkpoint email exists only on that intent. Recommended to authorise together with 61.2 — without it 61.1/61.2/`:837` fail | W | ADR-350; `matter-closure-dialog.tsx:117-118,385-427` (both default false) | 628H scout (ADR-350, pairs with 61.2) — **AUTHORISED by founder 2026-10-29**; applied to 60.20 and the Day-60 checkpoint by the orchestrator (629B restarted on the amended scenario) |
| 15 | full-lifecycle 5.5 (`:336`), Day-5 checkpoint (`:344`) | FICA card link reads **View checklist** (`?tab=onboarding`) when onboarding is checklist-driven; "View request" only for request-only FICA | W | `FicaStatusCard.tsx:90-107` | 620E |
| 16 | portal 7.1–7.3 (`:113-115`), Day-7 checkpoints (`:121-122`) | the digest CTA is **Open portal** → `/home`; reach Diary dates (`/deadlines`) from the nav; portal deadlines are empty until the deadline sync is built | W | `portal-weekly-digest.html:72-73`; `PortalDigestScheduler.java:305`; `DeadlinePortalSyncService.java:36-44` | 628G (1) |
| 17 | portal 45.4–45.8 (`:158-162`), E.9 (`:252`) | "mock payment gateway (local/keycloak profiles)" instead of "seeded sandbox PSP credentials"; on e2e (no PSP) mark N/A — Payments mandate exemption | W | `MockPaymentGateway.java:34` `@Profile({"local","dev","keycloak","test"})`; `MockPaymentController.java:40`; portal `invoices/[id]/page.tsx:90-92` | 628G (2) |
| 18 | portal 60.2 (`:177`), 60.6 (`:182`); full-lifecycle 61.2/61.5/61.8 (`:848,859,862`) | "Documents section of the matter page" instead of "Documents tab" | W | portal `matters/[id]/page.tsx:196-202` (`<h2>Documents</h2>`, no tabs) | 628G (3) |
| 19 | portal Day-75 heading (`:190`), 75.4–75.5 (`:197-198`), checkpoint (`:203`), 90.4 "Day 75 mark-read" (`:232`) | drop mark-read (no portal control); keep 75.x DEFERRED until portal deadlines exist (628G-BLOCKER) | P | `portal/e2e/tests/portal-client-90day/day-45-75.spec.ts:163-168`; `deadline-detail-panel.tsx:83-170` | 628G (4) |
| 20 | portal 85.1–85.2 (`:212-213`), checkpoint (`:220`), 90.4 "Day 85 profile update" (`:232`) | profile is read-only: verify Name / Email / Role / Client display; drop the edit + propagation | P | portal `profile/page.tsx:125-153` (dt/dd only); `PortalProfileController.java:16,26` (GET only) | 628G (5) |

---

## 6. Explicitly out of scope for this phase

- A "Government" party type beyond a frontend enum value; inline conflict check with an opposing-party input (D9).
- A pack-declared "header field" flag (D9 takes the hard-coded legal-za list).
- A settings UI for `portalNotificationDocTypes` (D7 removes the allowlist).
- A real "document acceptance" automation trigger (D4 renames and disables the upload rule).
- AI-core walk (needs a funded key).
- Phase 84 take-on; Phase 87 C.12 (c) next-phase items (`/api/calendar` deletion, profitability cost leg, redirect-table removal).
- Anything that re-proposes removed horizontal features (ADR-330).
