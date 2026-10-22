# Phase 88 — Demo-Ready: Root-Cause Closure of the Phase 87 Capstone Gaps

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-10-22). Runs after Phase 87 (all epics Done, capstone **not demo-ready**) and before Phase 84 (take-on). This phase exists because the Phase 87 capstone walked the legal-za 90-day story end to end on the real Keycloak stack and found **59 gaps (5 HIGH), 53 failed steps and 5 of 11 exit checkpoints failing**, with 52 of the 59 gaps pre-dating Phase 87. Kazi cannot be shown to a design-partner firm with a matter that no second attorney can be added to, a client who accepts an engagement letter they cannot see, a fee note that bills the task name instead of the attorney's narrative, and a portal a client cannot sign in to unaided.
>
> **Founder decisions (2026-10-22):** (1) **Fix at the root, not the surface.** Ten read-only research agents traced the 59 symptoms to **24 fix units** (`tasks/phase88-gap-register.md` §2, dossiers in `tasks/phase88-research/`); each unit closes every symptom that shares its cause. (2) **The three feature-grade units are in:** firm letterhead and banking settings, a portal email→firm directory, and payment method on Record Payment. (3) **Build, don't amend,** the three product-decision gaps: Decline on the acceptance page, full part payments, comment attachments. (4) **Decisions D1–D13 in the register are authorised as recommended**, and the §5 scenario amendments are authorised (CLAUDE.md §6). (5) **One phase, ordered**, with a mid-phase re-walk checkpoint.
>
> **Scouting note (verified against current code 2026-10-22, main `50b88ed5c` — do NOT re-derive; read the dossier for file:line):**
> - **Project-role casing is a data defect with 17 readers.** `ProjectTemplateService.java:569,739` writes `"LEAD"`; `Roles.PROJECT_LEAD = "lead"`; `LegalDemoDataSeeder:151` writes `"CONTRIBUTOR"`; column `varchar(50)`, no CHECK. 13 readers compare lowercase (`ProjectAccessService:47`, `MatterListRowRepository:46`, `ProjectMemberRepository:40`, `TimeEntryService:424-431`, the partial unique index), 4 compare uppercase (`NotificationService:359,512,615`, `AssignMemberActionExecutor:25,94`, `CreateTaskActionExecutor:105`). Lead notifications and automation `PROJECT_OWNER` recipients therefore never resolve for normally-created matters. `InstantiateTemplateIntegrationTest:277` pins `"LEAD"`. (R1)
> - **Matter team UI exists and is mounted nowhere.** `project-members-panel.tsx`, `add-member-dialog.tsx`, `transfer-lead-dialog.tsx`, `member-actions.ts`, `lib/api/matters.ts:312-327` all wire to live `/api/matters/{id}/members` endpoints (`ProjectMemberController:21-68`). Template instantiation adds nobody unless a lead is chosen. `POST …/members` has no capability gate; `transferLead` requires the caller to be the lead. `matter-tab-groups.ts` 7 groups / 21 tabs; two tests pin "21". (R1)
> - **`due_date` is never computed** outside batch billing (`Invoice.applyDefaults`, sole caller `BillingRunGenerationService:327`). No payment-terms setting exists (`BillingSettings` has three unrelated fields). The debtor rule `status='SENT' AND due_date IS NOT NULL` is at `CollectionsReadService:81-82,103-104,120-121`, `ClientListRowRepository:54-55`, `CashDigestService:104`, `InvoiceAgingReportQuery:76`, `CollectionsScanService:59`. Fee notes page "Total Outstanding" is a client-side sum in `invoices/page.tsx:32-39`. (R2)
> - **Two FICA mechanisms.** `FicaStatusProjector:46-86` reads `information_requests` on `fica-onboarding-pack`; the scenario and `ChecklistInstanceService.checkLifecycleAdvance:522-550` use checklist instances (`legal-za-individual-onboarding` 9/8, `legal-za-trust-onboarding` 12/11). Consumers: Home widget (`DashboardService.getFicaOutstanding:417-455`), Clients table + `ficaStatus` filter + system view (`CustomerService:308-362`), `FicaStatusCard.tsx:84`, KPI dashboard. `FicaStatusProjectorTest` (17 cases). (R2)
> - **Matter feed correlation.** `AuditEventRepository.findByProjectId:56-77` matches only `details->>'project_id'`; `acceptance.*` (`AcceptanceService:213-246,377-389`), `trust_*.recorded/approved/rejected` (`TrustTransactionService:258-268,502-512,896-905`), `invoice.created/approved` (`InvoiceCreationService:222-236`, `InvoiceTransitionService:165-180`) never write it. `ActivityMessageFormatter:153` raw fallback for ~40 reachable keys (list in R3 §2); Home has a second formatter `DashboardService.buildActivityDescription:920-938`. Audit filter is free text (`audit-log-client.tsx:103-108,480,496-502`); facet endpoints exist unused (`AuditEventController:117-139`); 68 entity literals and the legal-word map in R3 §4. ADR-342 keeps stored literals and allows UI words. (R3)
> - **Rendering.** `TemplateContextHelper.buildOrgContext:82-125` exposes `org.name/defaultCurrency/brandColor/documentFooterText/taxRegistrationNumber/logoUrl` only; no address/phone/email/bank/signatory field exists on `OrgSettings` or `Organization`. `document-default.css:20` uses an undefined `var(--brand-color)`; OpenHTMLToPDF has no CSS variables. Engagement-letter pack JSON never references `org.logoUrl`. Draft preview is a separate Thymeleaf renderer (`InvoiceRenderingService.renderPreview:66`, `invoice-preview.html:281-324`). Canonical formatters: `VariableFormatter.formatCurrency` (en-ZA), `packages/shared/src/format.ts:35`. Packs seed once per tenant (`AbstractPackSeeder:143-153`). `conveyancing-za-clauses/pack.json:288` has empty `templateAssociations`. `a4-preview-wrapper.tsx:18-50` uses padding-inclusive `clientWidth`. `MatterClosureContextBuilder:116,124` double-counts disbursements. (R4)
> - **Portal.** `portal_contacts` is per-tenant (`UNIQUE(email, customer_id)`), no global email index; `POST /portal/auth/request-link` requires `orgId` (`PortalAuthController:47-50`); `login/page.tsx:99-106` short-circuits. 2 of 11 email link builders carry org context (`PortalEmailService:98`, `InformationRequestEmailService:63`); the other nine (`InvoiceEmailService:80,145`, `PortalDocumentNotificationHandler:300` still `/projects/`, digest, trust, deadlines, payment returns) do not. No `.headers(...)` in security config; `/api/portal/acceptance/{token}/pdf` is on the main `@Order(2)` chain (`SecurityConfig:144-145`); VPS is cross-origin (`Caddyfile:17-23`), AWS same-origin — `DENY` blocks both. `PortalMatterResponse` already carries `status`. `AcceptanceStatus` = PENDING/SENT/VIEWED/ACCEPTED/EXPIRED/REVOKED. `StatementService.generate:159-166` saves SoA as `Visibility.PORTAL`; closure letter born PORTAL (`MatterClosureService:391-401`); handler allowlist `OrgSettings.portalNotificationDocTypes` has no UI. (R5)
> - **Disbursements and fee notes.** Write-off backend complete: `POST /api/legal/disbursements/{id}/write-off` (`DisbursementController:104`), `WriteOffRequest(reason)`, `@RequiresCapability("WRITE_OFF_DISBURSEMENTS")`, guard `billingStatus == UNBILLED`, audit `disbursement.written_off`; `writeOffDisbursement` (`lib/api/legal-disbursements.ts:185`) has zero callers; `restore()` has no endpoint. Trust-link dialog filters `DISBURSEMENT_PAYMENT` (`disbursements/actions.ts:285-289`) which is not in the ledger CHECK (`V1:1821`); `DisbursementService.EXPECTED_TRUST_TRANSACTION_TYPE = "PAYMENT"`. `GET /api/legal/disbursements/unbilled?matterId=` returns totals. `InvoiceCreationService:1180` `taskTitle -- date -- member`; `time_entries.description` never read. `RecordPaymentRequest(paymentReference)`; amount always `invoice.getTotal()` (`InvoiceTransitionService:330-333`); `InvoiceStatus` DRAFT/APPROVED/SENT/PAID/VOID; `payment_events` stores per-event amounts, nothing aggregates. `InvoiceValidationService:125-134` already warns (not blocks) INDIVIDUAL without tax number; copy is severity-blind on both sides. (R6)
> - **Matter detail and closure.** Promotion = structural columns only (`promoted-field-slugs.ts:45-50`); `case_number`/`court_name` are plain pack fields; header card props carry no custom fields but `page.tsx:326,476` already has `project.customFields` + defs. `matter-tabs.tsx:117-121` `urlTab ?? userTab`; client tabs use URL-as-source (`customer-grouped-tabs.tsx:60-61,135-142`). "Coming Soon" card `create-project-dialog.tsx:278-294` (Epic 371A stub); live API `POST /api/conflict-checks` (`ConflictCheckService:64-75`). `matter-header-card.tsx:88` `=== "ACTIVE"` vs `Project.closeMatter` "ACTIVE or COMPLETED". Retention: `legal_matter_retention_years` NULL (insert bypasses `DEFAULT 5`, `OrgSettingsService:120-141`), no setter caller, no UI, `retentionEndsAt` computed only into the response (`MatterClosureService:319-326`), `complete()` starts the clock (`Project.java:253-265`, pinned by `RetentionClockIntegrationTest:67`). `lib/capabilities.tsx:119-121` `hasCapability` short-circuits admin/owner ignoring `Capability.OWNER_ONLY` (`Capability.java:67-71`); `matter-closure-actions.ts:51-53` same; correct idiom at `invoices/[id]/page.tsx:145`. (R7)
> - **Terminology census.** ≈589 user-visible legacy sites: 261 frontend/portal (129 files), 24 pack JSON/email HTML, 26 backend labels/registries, 278 backend exception/validation messages (4 `ResourceNotFoundException` strings ×132 sites + 146 others). Full tables in R8 §1 (`table-frontend`, `table-backend`). Breadcrumb map and all `org/[slug]/**` segments in R8 §3; `RecordRecentItem`/`RecentItemsProvider` already publish entity names. Settings-nav 31 tabs mapped in R8 §4. Shell: `org/[slug]/layout.tsx:131` and `<main>` `:146` lack `min-w-0` (616A/615C/615D workarounds cite it). `diary-event-row.tsx:101-113` single-line truncate in ~150 px columns. `desktop-sidebar.tsx:72-77` brand colour as text. `IntegrationCard.tsx` tier remnants (`:62,72,84,182,195-205`). Dead `frontend/lib/messages/en-ZA-accounting/common.json`. 259 test lines in ~70 files pin these strings (R8 §8). (R8)
> - **Misc.** `adverse-parties/actions.ts:136-142` `.content` of a flat list. `lib/api/reports.ts:117-122,146-151` `getAuthToken()` throws in BFF (`keycloak-bff.ts:121-125`); `exportAuditPdf` (`audit-events.ts:241-270`) is the working template; `client-ledgers/actions.ts:141-153` has the same defect. `GET /api/diary` (`DiaryController:30-45`) aggregates TASK_DUE/MATTER_DUE/COURT_DATE/PRESCRIPTION with `feeEarnerId`. `team/invitation-actions.ts:113` omits `status=PENDING` (supported at `InvitationController:39-44`); seat line `invite-member-form.tsx:166`. `party_type varchar(20)` free string; four FE lists. (R9)
> - **Playwright.** Carried-six verdicts and the strict-mode `isVisible().catch(() => false)` pattern in R10 §A; default-config locator fixes §B; portal seed rows §C (Day 3 information request via `POST /api/information-requests` + `/send`; Day 60 `POST /api/matters/{id}/statements`; Day 85 `PATCH /api/settings/portal-digest-cadence`); P-1 path-anchored filters §D. (R10)
> - **Migrations:** next free tenant **V4**, global **V2** (`backend/db/BASELINE.md`); `BaselineSchemaParityTest` is pinned to V1 and must be re-pinned before a global V2. Memory rule: take the next free number at build time, never a planned one.

## System Context

Kazi after Phase 87 is a legal-only product with a legal-native shell: matters, clients, time, fee notes, disbursements, trust accounting, one diary, conflict checks, FICA/POPIA compliance, document templates with acceptance, a client portal, Xero, collections, an AI skills layer and an MCP server. The wire vocabulary is `matter`/`client`; JPA entities and tables remain `project`/`customer`; audit `entity_type` literals are pinned (ADR-342). The capstone proved the shell holds and the data model is sound, and that the product has a layer of defects that no unit test caught because they only appear with data created through the real UI path: role strings written in two casings, projections reading a store the UI never writes, publishers omitting the correlation key the feed joins on, templates referencing context the firm cannot enter, and a portal that cannot find the firm.

## Objective

Make the legal-za 90-day story walk **PASS on every step that is not a documented product decision**, on the real Keycloak stack, with the seven wow moments observed. Concretely: (1) every root cause in `tasks/phase88-gap-register.md` §2 is fixed at its source so that all symptoms sharing it close together; (2) a matter can have a team, a client can see what they accept, a fee note prints the attorney's narrative on the firm's letterhead with banking details, a client can sign in to the portal from any email; (3) Home, the Matters and Clients lists and the matter feed reflect real data; (4) the firm app says Matter / Client / Fee note everywhere a user can read, including breadcrumbs, settings tabs, audit labels, pack text and backend messages; (5) the legal Playwright suites and the default-config suites are green, with the seed data the suites need; (6) a re-walk of the 90-day scenario at the end of the phase produces a gap register with **zero HIGH and zero FAIL** outside the authorised scenario amendments.

## Constraints & Assumptions

- **Root-cause units, not symptom PRs.** The unit table in the register (U1–U24) is the work breakdown input. `/breakdown` maps units to slices; a slice closes a whole unit or an authorised same-class part of one (CLAUDE.md §7). A fix that addresses one symptom of a multi-symptom unit without touching the cause is rejected at review.
- **Reproduce-first, observed PASS.** Every unit starts with a reproduction: the browser on the :3001 mock-auth stack, or the Keycloak stack for *KC-only* units (U19 export, U11 portal sign-in in prod topology). Hypotheses marked HYPOTHESIS in the register (G-C4 grid-item cause, G-D12 stored status, N-6 portal whitelist) are confirmed or refuted in the reproduction step before the fix is designed.
- **Real-data tests.** Every unit that fixes a projection or a join (U1, U3, U4, U5, U6) adds an integration test that creates its rows **through the real service path** the UI uses (template instantiation, checklist completion, approve→send) — the capstone lesson that `MatterListRowIntegrationTest` was green because it never instantiated a template.
- **Migrations:** tenant V4 is shared by the units that need columns (U1 role normalisation + CHECK, U3 payment terms + backfill, U8 letterhead, U15 payments, U17 retention end, U23 decline columns, U24 comment attachments). The first unit to merge creates it; later units append until V4 has shipped to a non-dev tenant, then take V5. Global V2 for U11's directory; re-pin `BaselineSchemaParityTest` first. No migration touches pinned entity-type literals.
- **Pack content changes must reach existing tenants.** Pack JSON (field groups, templates, clauses, automations, report definitions) seeds once per tenant. Any unit editing pack text ships a reconcile (pack version bump + idempotent re-apply of names/descriptions/associations) so the walked tenant and demo tenants change too; "fresh tenants only" is not acceptable for demo surfaces.
- **ADR-342 stands.** Audit `entity_type` and event-key literals do not change. Legal words are applied in the UI label layer and the registry's display labels only. `EntityTypeLiteralStabilityTest` stays green; new `"project"` publish sites carry the ADR-342 comment.
- **Owner-only capabilities are honoured in the frontend.** `OWNER_ONLY` is mirrored in `lib/capabilities.tsx`; no server action grants an owner-only capability to admins.
- **Formatting has one source.** Money renders through `VariableFormatter.formatCurrency` (backend) and `@b2mash/shared` `formatCurrency` (frontend). No new `toFixed(2)` currency strings; the three existing ad-hoc sites are retired.
- **Terminology is a census, not a sample.** U21 works from R8's tables and must end with a repo-wide grep showing zero user-visible `Project`/`Customer`/`Invoice` strings outside the allow-list (`/api/invoices` paths, `INV-` numbering, statutory "Tax Invoice" wording, pinned literals, archived code). The remnant-scan guard test from Epic 611 is extended to cover JSX literals, pack JSON and the backend message strings.
- **Playwright amendments are authorised in-slice** (Epic 611B lesson B-7): locator and flow drift is fixed in the spec; the strict-mode `isVisible().catch(() => false)` pattern is replaced suite-wide by `expect(locator.first()).toBeVisible()` or role-based locators. Seed additions (invoices, trust account, the three portal rows) go into the e2e seed, not into specs.
- **Scenario amendments authorised** (register §5): wording, order and product-decision hunks are applied in one docs PR in the LZKC-029 annotation style, before the end-of-phase re-walk.
- **Quality gates unchanged:** full `./mvnw verify` for any backend change (use `backend/scripts/verify.sh`), `pnpm lint && pnpm build && pnpm test` (+ `format:check`) for frontend and portal, two reviewer passes, CodeRabbit where it runs, merge-gate hook, verify markers. Backend restarts after merges follow the `clean compile` rule.
- **No re-proposal of removed features** (ADR-330). No tier/plan UI (memory: no plan subscriptions).

## Unit specifications

Each unit lists: closes · root cause · the change · acceptance (observed) · tests. File:line detail is in the named dossier; do not duplicate it here.

### U1 — Project-role casing (R1)

**Closes:** G-D11 a (Matters "Fee earner —"), G-D13 (lead 403), N-1 (lead notifications / automation recipients), the double-recipient half of G-B13.
**Change:** every writer uses `Roles.PROJECT_LEAD` / `Roles.PROJECT_MEMBER` (template instantiation both paths, demo seeder `CONTRIBUTOR → member`); the four uppercase readers use the constants; `ProjectMember` lower-cases on persist/update as a guard; tenant V4 normalises existing rows (`lower()`, `CONTRIBUTOR → member`, demote duplicate leads to member keeping the earliest) and adds `CHECK (project_role IN ('lead','member'))`. Flip `InstantiateTemplateIntegrationTest:277`.
**Acceptance:** a matter created from a template with a chosen lead shows that lead in the Matters list "Fee earner" column; the lead opens the per-member time breakdown; a lead receives the lead notification for a task assignment on their matter.
**Tests:** new integration test instantiating a template and reading `MatterListRow`, `ProjectAccessService.canEdit`, notification recipients and `AssignMemberActionExecutor` against the real row; V4 migration test on a tenant with mixed-case rows.

### U2 — Matter team (R1)

**Closes:** G-B3 and the eight FAILs + two DEFERREDs behind "Carol can't"; N-3.
**Change (D1):** template instantiation auto-adds the creator (lead when no lead is chosen, member otherwise), mirroring `ProjectService.createProject`. Restore a **Team** sub-tab under the Details group of the matter page mounting the existing members panel, add-member and transfer-lead dialogs; bump the two "21" tab pins to 22; alias `members → team` in `legacy-tab-ids.ts`; the Overview setup step links to it. Backend: `POST/DELETE …/members` and `transferLead` gated to Owner, Admin or current lead (`canManage`); `transferLead` accepts an Owner/Admin caller. Members keep membership-only visibility (record as an ADR). The task assignee picker continues to read the members list.
**Acceptance:** Thandi opens a template-created matter, adds Bob and Carol from the Team tab, assigns a task to each; Carol (Member) sees the matter in her list, opens it, logs time and replies to a mention; Carol's notification link lands on the matter, not an error page. Reproduce the "Something went wrong" (not 404) first and record which boundary rendered it.
**Tests:** `ProjectMemberIntegrationTest` gate cases (member cannot add; admin can transfer); matter-tabs test for the Team tab; Playwright `day-01-07` Carol steps un-skipped.

### U3 — Fee-note due date and payment terms (R2)

**Closes:** G-D11 b, G-D21 Debtors, G-C7 c ("Due Date:" blank), the Fee notes vs Home disagreement.
**Change (D2):** `BillingSettings.defaultPaymentTermsDays` (V4, default 30, exposed on Settings › Fees › Billing); `approve` stamps `dueDate = issueDate + terms` when the draft left it null; V4 backfills APPROVED/SENT rows with NULL `due_date` as `issue_date + 30`. Fee notes page "Total Outstanding" moves to a backend summary endpoint using the debtor book's definition (SENT, unpaid balance). `ClientListRowIntegrationTest` javadoc updated; `CollectionsReadApiTest` non-null pin stays.
**Acceptance:** after approve → send with no due date entered, Home Debtors lists the client with the amount, the Clients table "Outstanding" shows it, the fee-note PDF prints the due date, and the Fee notes tile equals Home.
**Tests:** approve-path integration test asserting `due_date`; debtor-book test creating the fee note through `InvoiceCreationService` + transitions, not entity `markSent`.

### U4 — FICA status source (R2)

**Closes:** G-D11 c, G-D21 FICA, G-B10.
**Change:** `FicaStatusProjector` reads the client's onboarding checklist instances first (templates with pack ids `legal-za-individual-onboarding` / `legal-za-trust-onboarding`, or any `auto_instantiate` onboarding template — architecture decides the selector): COMPLETED → DONE with `completed_at`, else IN_PROGRESS; falls back to the information-request rule; response carries `checklistInstanceId`. Batched via `findByCustomerIdIn`; statement count re-verified against the 10-statement cap on `ClientListRow`. `FicaStatusCard` copy no longer says "No FICA onboarding request yet" when a checklist exists.
**Acceptance:** after Day 2's checklist completes, the matter FICA card, the Clients table, the "FICA outstanding" view and Home all read complete/0 outstanding.
**Tests:** extend `FicaStatusProjectorTest` and `FicaOutstandingIntegrationTest` with checklist-created clients.

### U5 — Matter feed correlation and formatter (R3)

**Closes:** G-B8, G-D9, G-D10 (Home feed text), G-D8 (D3).
**Change:** acceptance, trust transaction and invoice created/approved publishers write `project_id` into details (and `customer_id` where known); `findByProjectId` additionally ORs `entity_type='project' AND entity_id` to backfill history (D-recommended; architecture confirms row-count impact). `ActivityMessageFormatter` gains templates for every reachable key in R3 §2's table with legal wording ("sent fee note INV-0003", "approved disbursement", "created matter from template", trust deposit/payment amounts); the generic default becomes a legal verb/noun sentence; `DashboardService` delegates to the formatter. `matter_closure.closed` details gain `justification`.
**Acceptance:** the estate matter's Activity feed shows the R 50 000 deposit, the R 12 000 payment, engagement-letter sent/viewed/accepted with actor Sipho (PORTAL), fee note created/approved/sent, with no "performed x on y" text; Home Recent Activity reads the same way.
**Tests:** `ActivityMessageFormatterTest` fallback case updated; feed integration test through `AcceptanceService` and `TrustTransactionService`.

### U6 — Audit trail usability (R3)

**Closes:** G-D6, G-B2, G-D10 (audit labels), E.9.
**Change:** actor filter becomes a Select fed by the audit actor facets (Portal Contact and System included); entity-type filter is a Select of legal words mapped to stored literals (R3 §4 map, 68 literals); event-type filter uses the facet list; inputs commit on Enter; `EntityCell` renders the legal label. Registry display label `"Invoice"` → `"Fee note"` and `titleCase` legal-cases `project`/`customer` (D12). Client Activity tab joins checklist instance/item events for the client (reuse the `streamForCustomer` three-branch shape).
**Acceptance:** Firm › Audit trail filtered by actor "Thandi Mathebula" and entity "Matter" lists the matter events; the client's Activity tab shows the nine FICA item completions.
**Tests:** `audit-trail.test.tsx` pins updated; backend filter test for the child join.

### U7 — Automation "Document accepted" (R3)

**Closes:** G-B13 (D4).
**Change:** trigger and rule copy renamed "Document uploaded"; `document-review-notification` seeds disabled; reconcile existing tenants' rule name and enabled flag.
**Tests:** `AutomationEventListenerTest` updated.

### U8 — Firm letterhead and brand in PDFs (R4)

**Closes:** G-B5, G-C7 a–c, wow moment 6.
**Change (D5):** new `LetterheadSettings` embeddable on `OrgSettings` (V4): address lines 1–3, city, postal code, country, phone, email, website, bank name, branch code, account number, account type, payment reference note, signatory name, signatory designation. Form under Settings › Firm › General beside logo and brand colour (`OrgSettingsSchemaSnapshotTest` re-pinned). `buildOrgContext` exposes `org.address`, `org.phone`, `org.email`, `org.website`, `org.bankingDetails`, `org.signatory`. `TiptapRenderer.render` injects the brand colour as concrete CSS rules (headings, rule lines, letterhead border) — not a CSS variable. Template packs: engagement letters gain `{{org.logoUrl}}` + letterhead block; fee note gains client address block and a real banking block replacing "available on request"; optional labels wrapped in `conditionalBlock`. Pack reconcile for existing tenants.
**Acceptance:** the engagement letter and fee-note PDFs show logo, brand-coloured headings, firm address block, client address, VAT number, due date and banking details for the walked tenant.
**Tests:** `PdfRenderingServiceTest` / `VisualRegressionTest` golden updates; settings form vitest.

### U9 — Money formatting and the draft preview (R4)

**Closes:** G-C7 d/e, G-D14 amounts, N-4, G-C12 (SoA category key), `TrustBalanceZeroGate` raw "R%s".
**Change:** retire the Thymeleaf draft preview in favour of the Tiptap fee-note preview (or reformat it through `VariableFormatter` — architecture picks; retirement preferred); tariff dialogs use `@b2mash/shared` `formatCurrency` (move the "R 850.00" pin); `DisbursementCategory` gains a label used by the SoA builder; closure letter builder formats BigDecimals, fees excl. VAT with a separate VAT line and disbursements counted once; `TrustBalanceZeroGate` uses the formatter.
**Acceptance:** the draft Preview tab shows "R 1 200,00" with the logo; the SoA prints "Sheriff fees"; the closure letter prints formatted totals that add up.

### U10 — Document generation polish (R4)

**Closes:** G-B6, G-B16, G-B18, G-C4.
**Change:** generated-document rows with `documentId == null` are badged "Not saved" and hidden from the matter's Generated documents list by default; acceptance email subject uses the template category/name ("Please review Offer to Purchase for acceptance"; engagement letters unchanged, GAP-L-51 test extended); `conveyancing-za-clauses` pack declares the OTP `templateAssociations` for the seven embedded clauses + reconcile; `a4-preview-wrapper` uses `contentRect.width` and `min-w-0 overflow-hidden` (reproduce the SoA-only loop first and record the cause).
**Acceptance:** OTP preview shows Conditions of Sale clauses; SoA dialog preview stays within the dialog and Download PDF is clickable.

### U11 — Portal firm resolution (R5)

**Closes:** G-D1 and the 68.x DEFERREDs.
**Change (D6):** global `portal_contact_directory` (global V2; re-pin `BaselineSchemaParityTest`) maintained by the portal-contact lifecycle (create/update/delete/email change) with `(email_lower, tenant_schema, org_id, org_name)`; `POST /portal/auth/request-link` accepts email alone, resolves one or many orgs, sends one branded link per org, returns a generic response; bare `/login` works; `?orgId=` stays as a fast path. Every client email link builder carries org context (`InvoiceEmailService`, collections, payment returns, `PortalDocumentNotificationHandler` (now `/matters/`), digest, trust, deadlines) and `(authenticated)/layout.tsx` forwards it. Backfill the directory from each tenant's `portal_contacts` on migration.
**Acceptance:** fresh browser, `:3002/login`, Sipho's email, magic link from Mailpit, `/matters` loads with his matters; the fee-note email link lands on the fee note after exchange.
**Tests:** portal login vitest pin at `page.test.tsx:179` replaced; a bare-`/login` Playwright spec in the portal suite; directory sync integration tests; isolation test that a contact in tenant A cannot resolve tenant B.

### U12 — Acceptance PDF framing (R5)

**Closes:** G-B7, wow moment 2.
**Change:** request-matcher-scoped headers on `/api/portal/acceptance/*/pdf` only: `Content-Security-Policy: frame-ancestors <PORTAL_BASE_URL>` and `X-Frame-Options` disabled for that path; everything else keeps `DENY`. Add "Open in new tab" on the acceptance page. Header test on both chains.
**Acceptance:** `/accept/<token>` on :3002 renders the PDF inline; an unrelated API response still carries `X-Frame-Options: DENY`.

### U13 — Client notification intent (R5)

**Closes:** G-C5, G-D14 (emails), G-D19.
**Change (D7):** SoA generate and matter closure requests gain `notifyClient` (default false) and `shareWithClient`; SoA is saved INTERNAL until shared; the "Document ready" handler fires only on explicit intent; `portalNotificationDocTypes` allowlist removed; dialog copy states exactly what will be sent. Portal matter card shows a status badge (`PortalMatter.status` required).
**Acceptance:** "Preview & Save" sends no email; ticking "Email the client" sends one with a `/matters/` link; a Closed matter reads "Closed" on the portal list.

### U14 — Disbursement actions (R6)

**Closes:** G-C9, G-B11, G-B12, the 45.x DEFERREDs, N-8.
**Change:** Write Off action + reason dialog on the disbursement detail, firm list and matter tab for UNBILLED rows (clone the approval panel/action pattern); "Undo write-off" endpoint + action exposing `restore()`; trust-link dialog filters approved trust **PAYMENT** transactions on the matter (hide already-linked ones); matter Fees › Disbursements tab gets an unbilled summary strip (excl. VAT, VAT shown separately) from the existing `/unbilled` endpoint; firm list gets the same strip.
**Acceptance:** Day 45 write-off sub-flow passes end to end with badge, audit event and exclusion from the fee note; Day 5 trust-linked deeds-office disbursement creates and approves after the authorised step reorder; the tab shows "Unbilled disbursements R 2 000,00".
**Tests:** un-fixme the three depth-ii specs; write-off dialog vitest; restore endpoint test.

### U15 — Fee-note lines, payments, pre-check (R6)

**Closes:** G-C10, G-C11 (full part payments, D8), G-C6, N-5.
**Change:** line description from unbilled time becomes `date · fee earner · narrative` with the task title as fallback when the narrative is empty (carried into preview, PDF and email); **payments:** `payment_method` enum (EFT, CARD, CASH, TRUST_TRANSFER, XERO, OTHER), per-payment `amount`, `invoices.amount_paid` and derived `balance_due`, `InvoiceStatus.PARTIALLY_PAID`, Record Payment form with amount (defaulting to balance) and method; debtor book, aging, cash digest, Collections, Home Debtors and the Fee notes summary read the balance; trust fee-transfer, pay-now webhook and Xero sync post their actual amount and transition to PARTIALLY_PAID or PAID accordingly; portal fee-note view shows balance. Pre-check copy severity-aware on both sides (warning for individuals, block for company/trust).
**Acceptance:** Bob's "Drafting particulars of claim" appears verbatim on INV-0001; a R 5 000 EFT part payment on a R 42 182 fee note leaves R 37 182 outstanding on Home, the client row and the aging report; a full payment marks PAID.
**Tests:** `InvoiceLifecycleIntegrationTest` part-payment cases; Collections read tests on balance; Xero/trust/webhook amount tests.

### U16 — Matter detail (R7)

**Closes:** G-B9, G-B14, G-A6, G-D2.
**Change (D9):** `HEADER_FIELD_SLUGS` for legal-za (`case_number`, `court_name`, `opposing_party`) rendered as a facts strip on the matter header card from the custom-field values the page already loads; client header shows `id_passport_number` the same way. Matter tabs use the URL as the single source (`router.replace("?tab=")` on click, as client tabs do). "Coming Soon" card replaced by a "Run conflict check" link to `/conflict-check` prefilled with the client. Close Matter offered on COMPLETED.
**Acceptance:** wow moment 4 (case number and court inline); clicking Fees › Time recording on a matter opened with `?tab=tasks` switches the panel; a COMPLETED matter closes without Reopen.

### U17 — Retention model (R7)

**Closes:** G-D3.
**Change (D10):** `legal_matter_retention_years` exposed on Firm › Compliance (seeded 5, insert no longer bypasses the default); closure persists `projects.retention_ends_on = closed_at + years` (V4) and re-close resets it; `complete()` no longer starts the clock; the Overview card reads the persisted date with the firm default as fallback. ADR records the departure from the firm-wide `retention_policies` row (or retires it).
**Acceptance:** closing on Day 75 shows "Scheduled deletion 2031-10-22" on the matter.

### U18 — Owner-only capabilities (R7)

**Closes:** G-D7.
**Change:** `OWNER_ONLY` mirrored in `lib/capabilities.tsx` so `hasCapability` denies admins those three; `matter-closure-actions.ts` fixed; closure dialog shows Step 3 "Cannot close" for admins; `capabilities.test.tsx:92` and the dialog test gain the admin case; un-fixme `day-75-85-closure.spec.ts:319`.

### U19 — Export helper in BFF mode (R9) — *KC-only*

**Closes:** G-D4, N-2.
**Change:** `exportReportCsv/Pdf` and the trust client-ledger statement download use `getAuthFetchOptions()` + `API_BASE` like `exportAuditPdf`; the generic catch no longer swallows `NEXT_REDIRECT`. The bespoke Profitability page gets CSV export via a new backend endpoint (or links to the `project-profitability` report definition — architecture decides).
**Acceptance:** Timesheet report Export CSV/PDF downloads on the Keycloak stack; client-ledger statement PDF downloads.

### U20 — Small behaviour fixes (R9)

**Closes:** G-C2, G-D5, G-D12, G-C1.
**Change:** `fetchProjects` returns the flat list (as `conflict-check/actions.ts` does); My work "Upcoming" reads `GET /api/diary` for today+1…+14 with `feeEarnerId=me` and renders `DiaryEventRow` (next item replaces the task-only card); Team invitations list requests `status=PENDING` and the seat line reads "3 members · 0 pending" (add a backend reconcile by email only if the reproduction shows ACCEPTED is not stored); `GOVERNMENT` added to the four frontend party-type lists.
**Acceptance:** registry Link to Matter works; Bob's My work shows the 06 Dec CMC (he is the matter lead); Team shows no phantom pending invites; RAF registers as Government.

### U21 — Terminology census and shell layout (R8)

**Closes:** G-A2, A3, A4, A5, A7, B1, B4, C3, C12, D10, D15, D16, D17, D18, D20, E.8.
**Change (D11, D12):** all 589 census sites rewritten per R8's proposed-string tables (frontend/portal literals and label maps; pack JSON + reconcile; backend labels, registries, report definitions + reconcile; the four `ResourceNotFoundException` strings and the 146 validation messages); redundant record-status badge on the client header rendered only when not ACTIVE; breadcrumb label map completed for every `org/[slug]/**` segment and entity pages use `RecentItemsProvider` names for the last crumb; settings tab "Tariffs" → "Rates", "Xero" → "Integrations" (ids kept), h1s follow nav words; `IntegrationCard` tier prop, PRO badge, "Upgrade to Pro" block and `isStarter` guards removed; `TemplateList` maps SEEDER → "Pack"; shell `min-w-0` on the layout column and `<main>` (retire the 616A `@container` and 615C/615D local workarounds); Diary week row stacks time and title with `line-clamp-2`; sidebar org name renders white with the brand colour as an accent bar; dead `en-ZA-accounting` messages file and the `VerticalModuleRegistry` "Court Calendar" entry deleted. The Epic 611 remnant guard is extended to the census sources so the sweep cannot regress.
**Acceptance:** E.8 PASS: the Day 90 sweep finds zero Project/Customer/Invoice in sidebar, breadcrumbs, settings, dialogs, audit labels, reports, fee-note pages and pack text; Settings › Documents fits 1440 px; Diary week shows full titles.
**Tests:** the ~70 vitest/Playwright files pinning old strings updated in the same PRs; new guard test.

### U22 — Playwright suites and seed (R10)

**Closes:** the carried six, PACK-01, PROJ-02/03, CUST-01/02, portal 90-day seed ×3, P-1.
**Change:** suite-wide replacement of `isVisible().catch(() => false)` with strict assertions; A1 targets the Interest page LPFF dialog; A2/A3 follow the real fee-note creation path (client Fee notes tab → Generate → Fetch unbilled → Create draft → detail Add line / Add tariff items); A4 drives the five-step reconciliation wizard; A6 uses `columnheader` locators; PACK-01/CUST-01 use `exact`/role locators; PROJ-02/03 use `openGroupedTab`; CUST-02 uses the header smart action; e2e seed adds invoices, a trust account and the three portal rows (information request sent, SoA statement, digest cadence); `package.json` gains per-suite scripts with path-anchored filters and the default config `testIgnore`s the legal dirs.
**Acceptance:** legal-lifecycle and legal-depth-ii 0 failed on a fresh `e2e-down && e2e-up` stack; default-config 0 failed; portal 90-day 0 failed and 0 soft-skipped.

### U23 — Decline on acceptance (R5; founder: build)

**Closes:** G-B19.
**Change:** `AcceptanceStatus.DECLINED`, `declined_at` + `decline_reason` (V4), `POST /api/portal/acceptance/{token}/decline` with an optional reason, audit event `acceptance.declined` (with `project_id`, U5), firm notification to the sender, read-model sync, a Decline button + reason dialog on `/accept/[token]`, "Declined" badge on the generated-document row with the reason, and the matter closure/engagement gates treat DECLINED like REVOKED (architecture confirms the gate semantics).
**Acceptance:** Sipho declines the OTP with a reason; Thandi gets the notification; the document row reads Declined.

### U24 — Comment attachments (founder: build)

**Closes:** G-B15.
**Change:** `comment_attachments(comment_id, document_id)` (V4); the comment form accepts files, uploading them through the existing presigned document flow with the comment's matter/client scope and the comment's visibility; attachments render as document chips on the comment; portal client comments show attachments the client may see; deleting a comment detaches, not deletes, the document.
**Acceptance:** Thandi comments "Master's appointment received" with the PDF attached; the document appears in the matter's Documents list and on the comment.

## Mid-phase checkpoint and end-of-phase re-walk

- After U15 merges: re-walk Days 1–7, 36–47 and 61–75 of the 90-day scenario on the Keycloak stack (the G-B3 / G-C9 / G-C10 chains) and re-shoot the deck screenshots that showed "No client owes fees." / "Fee earner —".
- After U24 merges: full 90-day re-walk (same rules as 617C: real Keycloak, browser only, no substitutions, evidence per step) producing `qa_cycle/evidence/phase88-rewalk/` and a gap register. Exit: 0 HIGH, 0 FAIL outside authorised amendments, E.1/E.2/E.3/E.8/E.9 PASS, legal + default + portal Playwright green on a fresh stack, verify markers for every PR.

## Out of Scope

- Phase 84 take-on; the Phase 87 C.12 (c) next-phase items (`/api/calendar` deletion, profitability cost leg, redirect-table removal, `/legal/*` prefix).
- A pack-declared "header field" flag; an inline conflict check with an opposing-party input; a "Government" type beyond a frontend enum value.
- A settings UI for the removed `portalNotificationDocTypes` allowlist; a real document-acceptance automation trigger.
- The AI-core walk (needs a funded key); any horizontal feature (ADR-330); any tier/plan UI.

## ADR topics for `/architecture`

- ADR: canonical project-role values and the write-side guard (why normalise + CHECK rather than case-insensitive compare).
- ADR: matter visibility for org Members (membership-only) and who manages a matter's team.
- ADR: fee-note due date is stamped at approve from firm payment terms; balance model and `PARTIALLY_PAID` lifecycle; how trust/webhook/Xero post partial amounts.
- ADR: FICA status source of truth (checklist first, information request fallback).
- ADR: activity-feed correlation key (`project_id` in details) as a publisher obligation, and the entity-branch backfill.
- ADR: client-facing emails are sent only on explicit intent; generated documents are INTERNAL until shared.
- ADR: portal contact directory (global schema) and multi-firm magic-link resolution; tenant isolation guarantees.
- ADR: frame-ancestors on the acceptance PDF only; everything else stays `DENY`.
- ADR: firm letterhead settings and brand-colour injection in PDFs (no CSS variables in OpenHTMLToPDF).
- ADR: per-matter persisted retention end date (supersedes the firm-wide `retention_policies` row from closure; realigns with ADR-249).
- ADR: owner-only capabilities mirrored client-side.
- ADR: pack content reconciliation for existing tenants (version bump + idempotent re-apply).
- ADR: terminology guard extended to literals, pack JSON and backend messages; the allow-list.

## Style and boundaries

- One unit per epic; slices within a unit follow the register's dependency order (§3). HIGH and Phase-87-introduced units first: U12, U18, U1, U2, U3, U4, U14, U15, U11, then U5–U10, U13, U16, U17, U19, U20, U23, U24, then U21, U22, then the re-walk.
- Every slice's PR body cites the register unit, the dossier section it worked from, the reproduction evidence (screenshot or log path), and the observed PASS after the fix. "Code looks right" is not PASS.
- Agents may not edit `tasks/phase88-gap-register.md` §4 decisions or `qa/testplan/demos/*.md` beyond the §5 authorised list; new scenario mismatches are filed as gaps.
- Backend verify through `backend/scripts/verify.sh`; before a Keycloak re-walk `./mvnw -q clean compile -DskipTests`, `docker builder prune -f` before `e2e-up.sh`, gate-grade E2E is `e2e-down && e2e-up`.
- Follow `backend/CLAUDE.md` and `frontend/CLAUDE.md`; UI questions go to the `vercel:nextjs` / `vercel:shadcn` / `frontend-design` skills; browser-check every UI slice at 1024 and 1440 px.
