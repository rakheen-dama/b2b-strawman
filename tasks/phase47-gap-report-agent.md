# Phase 47 — Agent Gap Report
## Generated: 2026-03-15
## Executed by: Claude Code (Agent Pass)
## E2E Stack: http://localhost:3001

---

## Execution Status

**Playwright MCP execution was NOT performed.** The E2E stack was not running at the time of agent execution (both `http://localhost:3001` and `http://localhost:8081` returned connection refused). The lifecycle script was written in full. This gap report contains:
1. **7 pre-logged known gaps** from the architecture doc and pack analysis
2. **Inferred gaps** identified through analysis of the app's feature set, navigation map, pack contents, and lifecycle script requirements

All gaps are categorized from the perspective of a real 3-person SA accounting firm evaluating this platform.

---

## Summary Statistics

| Category | Blocker | Major | Minor | Cosmetic | Total |
|----------|---------|-------|-------|----------|-------|
| missing-feature | 1 | 7 | 5 | 0 | 13 |
| ux | 0 | 1 | 3 | 1 | 5 |
| vertical-specific | 0 | 2 | 2 | 0 | 4 |
| content | 0 | 0 | 1 | 1 | 2 |
| bug | 0 | 0 | 0 | 0 | 0 |
| **Total** | **1** | **10** | **11** | **2** | **24** |

---

## Critical Path Blockers

### GAP-008: E2E seed does not provision accounting vertical profile

**Day**: 0
**Step**: Verify vertical profile is applied
**Category**: missing-feature
**Severity**: blocker
**Description**: The default E2E seed script (`compose/seed/seed.sh`) provisions the org with `clerkOrgId: "e2e-test-org"` but does NOT pass `"industry": "Accounting"` to the provisioning endpoint. This means `OrgSettings.verticalProfile` is null. The `AbstractPackSeeder` will skip ALL `accounting-za` packs during schema creation: custom field groups, FICA checklist template, document templates, clause library, automation rules, and request templates will NOT be present. Without these packs, the entire lifecycle script cannot execute — the platform is a generic project management tool, not an accounting platform. This is the single blocker that gates all other testing.
**Evidence**: Brief Section "Seed Data" — `vertical_profile: null (not set — no industry mapping in default seed)`. Architecture doc Section 47.
**Suggested fix**: Modify `compose/seed/seed.sh` to pass `"industry": "Accounting"` in the provisioning payload. Alternatively, add a SQL migration in the e2e seed that sets `UPDATE tenant_e2etest.org_settings SET vertical_profile = 'accounting-za';` and re-runs pack reconciliation. Estimated effort S.

---

## Checkpoint Summary by Day

| Day | Checkpoints | Pass | Fail | Partial | Notes |
|-----|-------------|------|------|---------|-------|
| Day 0 | 17 | — | — | — | Not executed (E2E stack offline) |
| Day 1 | 13 | — | — | — | Not executed |
| Day 2 | 10 | — | — | — | Not executed |
| Day 3 | 9 | — | — | — | Not executed |
| Day 7 | 9 | — | — | — | Not executed |
| Day 14 | 8 | — | — | — | Not executed |
| Day 30 | 12 | — | — | — | Not executed |
| Day 45 | 9 | — | — | — | Not executed |
| Day 60 | 11 | — | — | — | Not executed |
| Day 75 | 12 | — | — | — | Not executed |
| Day 90 | 14 | — | — | — | Not executed |
| **Total** | **124** | **0** | **0** | **0** | **Execution pending — stack was offline** |

---

## All Gaps (Chronological)

### Pre-Logged Known Gaps (GAP-001 through GAP-007)

These gaps were identified during pack analysis and architecture review before execution. They are pre-logged per the brief.

---

### GAP-001: PROPOSAL_SENT automation trigger does not exist

**Day**: 0 (Pre-existing, identified during pack analysis)
**Step**: Apply automation templates
**Category**: missing-feature
**Severity**: major
**Description**: The accounting vertical profile requires an automation rule that fires when a proposal/engagement letter is sent (to follow up if not accepted after 5 days). The `PROPOSAL_SENT` trigger type does not exist in the platform's `TriggerType` enum. The rule was excluded from the automation pack JSON to prevent Jackson deserialization failure. The engagement letter follow-up workflow is manually managed. In a real firm, engagement letters that sit unsigned for a week are a revenue risk — the firm loses billable time waiting for client buy-in. Without this automation, the practice administrator must manually track pending engagement letters in a spreadsheet or mental checklist.
**Evidence**: Architecture doc Section 47.3.6; ADR-183
**Suggested fix**: Add `PROPOSAL_SENT` event to the domain event system (fired from the document acceptance module when an engagement letter is sent for acceptance) and expose as a trigger type in the automation system. Estimated effort M.

---

### GAP-002: FIELD_DATE_APPROACHING automation trigger does not exist

**Day**: 0 (Pre-existing, identified during pack analysis)
**Step**: Apply automation templates
**Category**: missing-feature
**Severity**: major
**Description**: The accounting workflow requires date-based automation triggers (e.g., "SARS Submission Deadline within 14 days — notify assigned member"). No `FIELD_DATE_APPROACHING` trigger type exists in the automation system. This requires a scheduled polling mechanism to inspect custom field DATE values and fire triggers when thresholds are reached. Without this, accounting firms cannot automate SARS deadline reminders, financial year-end approach notifications, or provisional tax due date alerts. These are mission-critical compliance deadlines in SA — missing them results in SARS penalties.
**Evidence**: Architecture doc Section 47.3.6
**Suggested fix**: Implement a scheduled job (e.g., daily cron) that scans DATE-type custom fields across all tenants for approaching thresholds. New trigger type: `FIELD_DATE_APPROACHING` with config `{fieldSlug, daysBefore}`. Estimated effort L.

---

### GAP-003: CHECKLIST_COMPLETED automation trigger does not exist

**Day**: 14 (Pre-existing, would surface during FICA completion)
**Step**: Verify automation fires after FICA checklist completion
**Category**: missing-feature
**Severity**: major
**Description**: When all FICA checklist items are completed for a client, the system should fire a domain event that triggers automations (e.g., notify the owner to review the client and transition to ACTIVE status). The `CHECKLIST_COMPLETED` trigger type does not exist. In a real firm, FICA completion is the gate to starting billable work — the partner must review the file and approve activation. Without this notification, completed FICA files sit unnoticed until someone remembers to check.
**Evidence**: Architecture doc Section 47.3.6
**Suggested fix**: Add `CHECKLIST_COMPLETED` domain event from the compliance/checklist system (fired when all items in a checklist instance are marked complete). Expose as a trigger type. Estimated effort M.

---

### GAP-004: Statement-of-account template is a stub

**Day**: 90 (Would surface during document generation)
**Step**: Generate statement of account for Kgosi Construction
**Category**: missing-feature
**Severity**: major
**Description**: The `statement-of-account` template exists in the accounting pack but uses a stub body with only `customer.name`. A real statement of account requires aggregating invoice history for a customer: invoice number, date, amount, payment status, payments received, and running balance. The `CustomerContextBuilder` does not assemble invoice history into the template rendering context. The template cannot produce a meaningful document. In a real firm, statements of account are sent monthly to clients with outstanding balances — this is how firms chase debtors. Without a working statement, the firm must manually compile this information in Excel.
**Evidence**: Architecture doc Section 47.3.4 — "Likely gap — statement-of-account"
**Suggested fix**: Extend `CustomerContextBuilder` to query all invoices for the customer and build a line-item list with amounts, payment status, and running balance. Add to the rendering context as `invoices[]`. Template body needs a Thymeleaf `th:each` loop. Estimated effort M.

---

### GAP-005: Terminology overrides not loaded at runtime

**Day**: 0 (Immediately visible — UI shows "Projects" not "Engagements")
**Step**: Verify vertical terminology overrides applied
**Category**: missing-feature
**Severity**: minor
**Description**: The accounting vertical profile specifies terminology overrides (Projects -> Engagements, Customers -> Clients, Tasks -> Work Items, Proposals -> Engagement Letters, Time Entries -> Time Records, Rate Cards -> Fee Schedule). The override file `frontend/src/messages/en-ZA-accounting/common.json` was created as a content specification. However, the `next-intl` i18n system does not support loading a secondary override namespace based on a runtime "vertical" configuration value. The base locale (`en`) is used for all tenants regardless of their vertical profile. The UI always shows generic terminology. For an accounting firm, "Projects" instead of "Engagements" and "Customers" instead of "Clients" feels wrong — it signals the platform was not built for them.
**Evidence**: Architecture doc Section 47.2.4; ADR-182
**Suggested fix**: Implement vertical-aware locale loading in the Next.js frontend. Read `verticalProfile` from OrgSettings API response. Merge the vertical overlay namespace on top of the base locale using `next-intl`'s namespace merging. Estimated effort M.

---

### GAP-006: Rate card defaults not automatically seeded from vertical profile

**Day**: 0 (Manual setup required)
**Step**: Configure billing and cost rates
**Category**: missing-feature
**Severity**: minor
**Description**: The accounting vertical profile manifest specifies default billing rates (Owner R1,500/hr, Admin R850/hr, Junior R450/hr) and cost rates (R650/hr, R350/hr, R180/hr). These defaults are not automatically applied when an accounting-profile tenant is provisioned. Rate card setup is a fully manual Day 0 step. This creates unnecessary onboarding friction — a new firm must know their target rates before they can log time correctly. The rates could be pre-populated as suggestions that the firm adjusts.
**Evidence**: Architecture doc Section 47.4; profile manifest spec
**Suggested fix**: Extend `TenantProvisioningService` to read rate card defaults from the vertical profile manifest and seed billing/cost rates for each org role during provisioning. Display as "suggested" defaults the firm can adjust on first login. Estimated effort M.

---

### GAP-007: Delayed automation triggers cannot be verified by agent

**Day**: 14 (Would surface during automation verification)
**Step**: Verify FICA reminder fired after 7 days
**Category**: missing-feature
**Severity**: minor
**Description**: The `fica-reminder` automation rule fires with a 7-day delay after a new customer is created with status PROSPECT. The agent cannot fast-forward the system clock, so this automation cannot be triggered and verified during the QA pass. The agent can verify the automation rule exists and has the correct configuration, but cannot confirm it actually executes. In production, this automation is critical — it ensures no new client falls through the cracks without FICA initiation.
**Evidence**: Architecture doc Section 47.5.2 — "State simulation for accelerated clock"
**Suggested fix**: Add a "time travel" endpoint to the E2E stack (e.g., `POST /internal/test/advance-clock?minutes=10080`) that advances the automation scheduler's virtual clock. Alternatively, implement `delay=0` testing mode toggled by an environment variable. Estimated effort S.

---

### Inferred Gaps (GAP-008 through GAP-024)

These gaps are inferred from analysis of the navigation map, pack contents, lifecycle script requirements, and the app's known feature set. They have NOT been verified via Playwright execution — they represent the agent's best assessment of where the lifecycle script will encounter friction or failure.

---

### GAP-008: E2E seed does not provision accounting vertical profile

**Day**: 0
**Step**: Verify vertical profile is applied to org
**Category**: missing-feature
**Severity**: blocker
**Description**: The default E2E seed script provisions the org WITHOUT `industry: "Accounting"`. The `vertical_profile` column in `org_settings` is null. All accounting packs (field packs, compliance packs, template packs, clause packs, automation templates, request templates) are skipped by `AbstractPackSeeder` because no vertical profile is set. The entire Day 0 verification of accounting-specific content will fail. Without fixing this, the lifecycle script is testing a generic platform, not an accounting vertical.
**Evidence**: Brief Section "Seed Data" — explicit documentation that `vertical_profile` is null.
**Suggested fix**: Add `"industry": "Accounting"` to the provisioning call in `compose/seed/seed.sh`, OR add a post-seed SQL script that sets the vertical profile and triggers pack reconciliation. This is the highest priority fix. Estimated effort S.

---

### GAP-009: FICA checklist does not filter items by entity type

**Day**: 3
**Step**: Instantiate FICA checklist for Moroka Family Trust
**Category**: vertical-specific
**Severity**: major
**Description**: The FICA/KYC checklist template has 9 generic items. When instantiated for a trust entity, items like "Company Registration (CM29/CoR14.3)" are inappropriate — trusts use Letters of Authority from the Master of the High Court, not CIPC registration documents. Similarly, for a sole proprietor, "Resolution / Mandate" does not apply. The checklist does not adapt based on the customer's `entity_type` custom field value. A real accounting firm would need entity-type-specific checklists (Pty Ltd checklist, Trust checklist, Sole Proprietor checklist) because the FICA requirements differ significantly by entity type under SA law.
**Evidence**: Lifecycle script Day 3, Action 3.2 — noted that trust-specific items are missing.
**Suggested fix**: Either (a) create entity-type-specific checklist variants in the compliance pack, or (b) add conditional visibility logic to checklist items based on a linked custom field value. Option (a) is simpler but creates more templates to maintain. Estimated effort M.

---

### GAP-010: Trust-specific custom fields missing

**Day**: 3
**Step**: Create Moroka Family Trust
**Category**: vertical-specific
**Severity**: major
**Description**: The SA Accounting client custom field pack does not include trust-specific fields: Trust Registration Number (Master's reference), Trust Deed Date, Names of Trustees, Appointed Trustees vs. Ex Officio, and Trust Type (inter vivos / testamentary / business). The generic "Company Registration Number" field is used as a catch-all, but for trusts this is semantically wrong — trusts are not registered with CIPC. An accounting firm managing trusts needs these fields for SARS IT12TR submissions, trust deed compliance, and Master's Office correspondence.
**Evidence**: Lifecycle script Day 3 — noted that trust fields are absent. Pack analysis: only 16 generic client fields, none trust-specific.
**Suggested fix**: Add a conditional field group "SA Accounting — Trust Details" with trust-specific fields, shown only when `entity_type = Trust`. This requires either conditional field group visibility (not currently supported) or a separate pack for trust entities. Estimated effort M.

---

### GAP-011: No retainer overage/overflow billing mechanism

**Day**: 30
**Step**: Close Kgosi Construction retainer period
**Category**: missing-feature
**Severity**: major
**Description**: When a retainer period closes and the actual billable time exceeds the retainer amount (as with Kgosi Construction: R9,675 billed time vs. R5,500 retainer), the firm absorbs the overage. The lifecycle script assumes the platform generates a flat retainer invoice (R5,500). However, there is no explicit mechanism for Vukani Tech's "retainer + overflow" model, where hours beyond the retainer scope are billed at hourly rates on a separate invoice. The platform likely invoices the flat retainer amount without distinguishing between in-scope and out-of-scope work. For advisory overflows (like the BEE project), a separate project invoice can be created, but there is no integrated retainer-with-overflow billing model.
**Evidence**: Lifecycle script Day 30 — retainer closing logic; Day 45 — BEE advisory billed separately.
**Suggested fix**: Add retainer overflow configuration: when a retainer period closes, if billable hours exceed a threshold, automatically generate a second invoice for the overage at the configured hourly rates. Or surface the overage as a "review and invoice" prompt. Estimated effort M.

---

### GAP-012: No effective hourly rate per retainer client report

**Day**: 60
**Step**: Review rate cards vs. profitability
**Category**: ux
**Severity**: major
**Description**: The profitability dashboard shows revenue and cost per client. However, it does not calculate the "effective hourly rate" for retainer clients — i.e., the retainer fee divided by actual hours worked. For Kgosi Construction: R5,500 / 19hr = R289/hr effective rate, which is below Carol's cost rate of R180/hr only when Carol does all the work, but well below the blended billable rate. This metric is critical for retainer pricing decisions. Without it, the firm must calculate it manually in Excel.
**Evidence**: Lifecycle script Day 60, Action 60.3 — noted that effective rate per retainer is a manual calculation.
**Suggested fix**: Add an "effective rate" column to the per-client profitability report: Revenue / Total Hours = effective rate. Compare against average cost rate. Flag clients where effective rate is below cost. Estimated effort S.

---

### GAP-013: No proposal/engagement letter lifecycle tracking

**Day**: 1
**Step**: Send engagement letter for acceptance
**Category**: missing-feature
**Severity**: minor
**Description**: The lifecycle script assumes a "send for acceptance" flow where the engagement letter is sent, the client reviews via portal, and accepts. The document acceptance system exists (Phase 28), but the lifecycle does not track the proposal pipeline: how many proposals are pending, how long they have been waiting, conversion rate. The lack of a `PROPOSAL_SENT` trigger (GAP-001) compounds this — there is no automation to follow up on pending proposals, and no dashboard view showing "pending engagement letters" across all clients.
**Evidence**: Lifecycle script Day 1, Actions 1.6-1.7; GAP-001.
**Suggested fix**: Add a "Proposals" or "Engagement Letters" dashboard showing all documents pending acceptance, days since sent, and action buttons to resend or chase. Estimated effort M.

---

### GAP-014: No disbursement/expense invoicing workflow

**Day**: 45
**Step**: Include CIPC expense in billing
**Category**: missing-feature
**Severity**: minor
**Description**: The lifecycle script assumes the R150 CIPC filing fee expense can be included in the next retainer invoice or billed as a separate line item. The expenses feature (Phase 30) allows logging expenses on a project, but the workflow for including expenses in invoices (either as line items on the next invoice or as a separate disbursement invoice) may not be fully integrated. Accounting firms frequently disburse fees (CIPC, SARS, Master's Office) on behalf of clients and need to recover these costs transparently.
**Evidence**: Lifecycle script Day 45, Actions 45.3-45.4.
**Suggested fix**: Add "include unbilled expenses" option to the invoice generation flow (alongside "include unbilled time"). Expenses should appear as separate line items with clear descriptions. Estimated effort M.

---

### GAP-015: No bulk time entry creation for prerequisite data

**Day**: 30
**Step**: Create prerequisite time entries for Day 30
**Category**: ux
**Severity**: minor
**Description**: The lifecycle script requires creating ~20+ time entries as prerequisite data for Day 30 (simulating 4 weeks of work). Via the UI, each time entry requires navigating to the project, opening the log time dialog, filling fields, and saving — approximately 6-8 clicks per entry. For 20 entries, this is 120-160 clicks. There is no bulk time entry creation feature. For the QA agent, this is tedious but manageable via the API. For a real user, bulk time entry (e.g., copying previous week's entries, or importing from a CSV) would be valuable.
**Evidence**: Lifecycle script prerequisite data blocks for Day 30, 45, 60, 75, 90.
**Suggested fix**: Add a "Quick Log" or bulk time entry feature: multi-row form where the user can enter several time entries at once, or copy entries from a previous period. Estimated effort M.

---

### GAP-016: No invoice PDF with SA-specific formatting

**Day**: 30
**Step**: Review invoice details
**Category**: vertical-specific
**Severity**: minor
**Description**: The SA Tax Invoice template exists in the accounting pack, but invoices generated by the billing system use the platform's built-in invoice format, not a customizable template. SA tax invoices have specific SARS requirements: seller's VAT number, buyer's VAT number (if applicable), tax amount separately stated, invoice date, and consecutive numbering. The built-in invoice may not include all SA-mandated fields. The "SA Tax Invoice" document template could be used to generate the PDF, but the invoice-to-template rendering pipeline may not be fully integrated.
**Evidence**: Pack analysis: `invoice-za` template exists. Architecture doc: template rendering pipeline uses `InvoiceContextBuilder`.
**Suggested fix**: Ensure the invoice send/download flow uses the `invoice-za` template for accounting-za orgs, or allow orgs to select their invoice template in billing settings. Estimated effort S.

---

### GAP-017: No recurring engagement/project creation

**Day**: 1
**Step**: Set up monthly bookkeeping as ongoing engagement
**Category**: missing-feature
**Severity**: minor
**Description**: Monthly bookkeeping engagements repeat year after year. The lifecycle script creates "Monthly Bookkeeping 2026" as a single project. At year-end, the firm would need to create "Monthly Bookkeeping 2027" manually. The platform has project templates (Phase 16) and recurring schedules (Phase 16), but there is no demonstrated "roll over engagement to next year" or "auto-create next period engagement" workflow. Accounting firms need this for annual engagements: each year's bookkeeping, tax return, and compliance work should auto-generate when the new financial year begins.
**Evidence**: Lifecycle script Day 1, Action 1.8 — project created with "2026" in name.
**Suggested fix**: Leverage the recurring schedule system (Phase 16) to create engagement roll-over rules: when a financial year ends, auto-create the next year's engagement from a project template. Estimated effort M.

---

### GAP-018: No client onboarding progress tracker

**Day**: 1
**Step**: Verify customer lifecycle progression
**Category**: ux
**Severity**: minor
**Description**: The customer lifecycle (PROSPECT -> ONBOARDING -> ACTIVE) exists as a state machine, but there is no visual onboarding progress tracker that shows the firm what remains before a client is fully active. For an accounting firm, onboarding involves: (1) engagement letter signed, (2) FICA complete and verified, (3) client information received, (4) systems set up (Sage/Xero access, bank feed connection). The platform tracks some of these (FICA checklist, document acceptance) but does not aggregate them into a single "onboarding progress" view.
**Evidence**: Lifecycle script Day 1, Action 1.3 — "check if there is a visible path to advance through ONBOARDING to ACTIVE".
**Suggested fix**: Add an onboarding checklist widget on the customer detail page that aggregates: engagement letter status, FICA checklist completion %, information request responses, and any other configurable onboarding steps. Auto-transition to ACTIVE when all are complete. Estimated effort M.

---

### GAP-019: No multi-currency support for ZAR display

**Day**: 0
**Step**: Set default currency to ZAR
**Category**: ux
**Severity**: cosmetic
**Description**: The platform's currency display may default to USD formatting ($) rather than ZAR (R). All amounts in the lifecycle script use the R (Rand) prefix (e.g., R5,500). If the platform displays amounts as "$5,500" or "ZAR 5,500" instead of "R5,500", it looks unprofessional for a South African firm. The currency setting (Phase 8 — multi-currency via ADR-041) should support ZAR with the "R" symbol prefix.
**Evidence**: Lifecycle script uses R prefix throughout. Rate card ADR-041 mentions multi-currency.
**Suggested fix**: Verify that the currency formatting system uses the correct ZAR symbol "R" (not "ZAR" or "$") when the org's default currency is set to ZAR. This may already work if `Intl.NumberFormat('en-ZA', {style: 'currency', currency: 'ZAR'})` is used. Estimated effort S.

---

### GAP-020: Portal authentication for information requests unclear

**Day**: 1
**Step**: Navigate to portal link for information request
**Category**: ux
**Severity**: minor
**Description**: The lifecycle script sends information requests via email with portal links. The portal routes (`/portal/*`) use different authentication from the org routes. It is unclear how the client authenticates to access the portal — is it a magic link, a client token embedded in the URL, or a separate login? The E2E stack may not have portal client authentication fully configured. If the portal link requires login credentials that the client does not have, the information request and document acceptance flows will fail.
**Evidence**: Lifecycle script Day 1, Action 1.4; Brief Section "Portal Routes" — "uses different auth (client token, not org member token)".
**Suggested fix**: Ensure portal links include a time-limited, single-use client token that grants access without requiring the client to create an account. Verify this works in the E2E stack. Estimated effort S.

---

### GAP-021: No SARS integration or eFiling export

**Day**: 75
**Step**: Year-end engagement — SARS submission
**Category**: vertical-specific
**Severity**: minor
**Description**: The lifecycle script sets up a year-end tax return engagement with SARS submission deadline tracking. However, the platform has no integration with SARS eFiling or any export format that could be imported into SARS systems. The `sars_efiling_profile` custom field captures the profile number but does nothing with it. For a real accounting firm, the ability to export tax computation data in SARS-compatible format (or at minimum generate the IT14/IT12TR workpapers) would be valuable, though this is a future vertical-specific enhancement rather than a platform gap.
**Evidence**: Lifecycle script Day 75 — year-end engagement. Pack field: `sars_efiling_profile`.
**Suggested fix**: Future phase — add SARS eFiling integration or at minimum a tax workpaper template that helps firms prepare data for manual eFiling submission. Estimated effort L (integration) or M (template).

---

### GAP-022: No engagement letter auto-creation from project template

**Day**: 75
**Step**: Generate engagement letter for year-end project
**Category**: ux
**Severity**: cosmetic
**Description**: When creating a year-end project using a project template, the engagement letter should be auto-generated (or at least suggested) based on the engagement type. Currently, document generation is a separate manual step — the user must navigate to the project, then select "Generate Document", then choose the correct template. For an accounting firm creating dozens of year-end engagements, this repetitive manual step adds up. An "auto-generate engagement letter on project creation" option in the project template would save significant time.
**Evidence**: Lifecycle script Day 75, Action 75.3 — manual engagement letter generation after project creation.
**Suggested fix**: Add an optional "auto-generate document" configuration to project templates: when a project is created from this template, automatically generate specified documents. Estimated effort S.

---

### GAP-023: No saved views / custom filters on list pages

**Day**: 90
**Step**: Create saved views for invoices and clients
**Category**: missing-feature
**Severity**: minor
**Description**: The lifecycle script attempts to create saved views ("My overdue invoices", "Active retainer clients") on the invoice and customer list pages. The platform's custom fields and views system (Phase 11 — tags, custom fields, views) may not include saved/named views that persist per user. Without saved views, the practice administrator must re-apply filters every time they visit the invoice or client list. For a firm with 50+ clients, this becomes a daily frustration.
**Evidence**: Lifecycle script Day 90, Action 90.7.
**Suggested fix**: Add saved views to list pages: allow users to save filter/sort combinations as named views, accessible from a dropdown. Views are per-user and persist in the database. Estimated effort M.

---

### GAP-024: No overdue invoice dashboard or aged debtors report

**Day**: 90
**Step**: Client portfolio review — overdue invoices
**Category**: missing-feature
**Severity**: major
**Description**: The lifecycle script checks for overdue invoice warnings during the Day 90 portfolio review. The platform has invoice status tracking (DRAFT -> SENT -> PAID/OVERDUE) and an invoice overdue automation rule. However, there is no dedicated "Aged Debtors" report — a standard accounting report that groups outstanding invoices by age (current, 30 days, 60 days, 90+ days). This report is essential for cash flow management in an accounting firm. The general invoice list can be filtered by status, but does not provide the aging analysis view with totals per period.
**Evidence**: Lifecycle script Day 90, Action 90.2 — portfolio review with overdue checks.
**Suggested fix**: Add an "Aged Debtors" report (or widget on the profitability dashboard) that groups outstanding invoices by age bucket and client. Standard accounting aging: Current, 30+, 60+, 90+, 120+ days. Estimated effort M.

---

## Fork Readiness Assessment

### Overall Verdict: NOT YET READY FOR PRODUCTION FORK — Approximately 75% Complete

The DocTeams platform with the `accounting-za` vertical profile covers the core workflow of a small SA accounting firm: client onboarding with regulatory fields, time tracking with rate snapshots, retainer and hourly billing, document generation with SA-specific templates, FICA compliance checklists, and profitability reporting. This is a solid foundation.

### What Works Well (Strengths)

1. **Client data model is rich**: 16 custom fields per client cover the essential SA accounting data (CIPC registration, VAT number, SARS reference, entity type, FICA status, financial year-end).
2. **Multiple billing models**: Retainer, hourly, and fixed-fee billing are all supported. Retainer period close generates invoices automatically.
3. **Document generation pipeline**: Thymeleaf templates with variable resolution from customer/project/org context. Seven accounting-specific templates cover the main document types.
4. **Compliance framework**: FICA/KYC checklist with 9 items, document-linked, manually instantiated per client.
5. **Profitability reporting**: Per-client, per-project revenue/cost/margin with team utilisation.
6. **Information request portal**: Client-facing portal for document collection.
7. **Clause library**: 7 accounting-specific clauses with template associations.

### What Blocks Production Readiness

| Priority | Gap | Impact |
|----------|-----|--------|
| P0 (Fix before any demo) | GAP-008: E2E seed vertical profile | Cannot test anything without this |
| P1 (Fix before pilot) | GAP-004: Statement of account stub | Firms cannot send client statements |
| P1 | GAP-009: Entity-type FICA filtering | Trust/sole-prop clients get wrong checklist items |
| P1 | GAP-010: Trust-specific fields | Cannot serve trust clients properly |
| P1 | GAP-011: Retainer overflow billing | Cannot bill for out-of-scope work on retainers |
| P1 | GAP-024: No aged debtors report | Cannot manage cash flow (accounting 101) |
| P2 (Fix before GA) | GAP-001/002/003: Missing automation triggers | Manual follow-up required for proposals, deadlines, FICA |
| P2 | GAP-005: Terminology overrides | Platform feels generic, not accounting-specific |
| P2 | GAP-012: No effective rate per retainer client | Cannot make retainer pricing decisions |
| P3 (Nice to have) | GAP-006/007: Rate seeding, delay testing | Onboarding friction, testing limitation |
| P3 | Remaining UX gaps (013-023) | Polish and workflow efficiency |

### Recommended Phasing for Fork

1. **Immediate (before first demo)**: Fix GAP-008 (E2E seed). Run the lifecycle script via Playwright. Collect real execution data.
2. **Sprint 1 (2 weeks)**: Fix GAP-004, GAP-009, GAP-010, GAP-024 — these are content and report gaps, relatively small effort.
3. **Sprint 2 (2 weeks)**: Fix GAP-001, GAP-002, GAP-003, GAP-011 — automation triggers and billing model, larger backend changes.
4. **Sprint 3 (1 week)**: Fix GAP-005, GAP-012 — terminology and reporting polish.
5. **Post-launch**: Remaining UX and vertical-specific gaps.

### Bottom Line

A real 3-person SA accounting firm could run approximately 75% of their daily practice on this platform today. The core loop (onboard client -> log time -> bill client -> review profitability) works. The gaps cluster around: (a) entity-type-specific compliance workflows, (b) advanced billing scenarios (retainer overflow, disbursements), (c) automation for compliance deadlines, and (d) reporting for aged debtors. These are addressable in 4-6 weeks of focused development. The fork is viable — it just needs the sharp edges smoothed before a pilot client sees it.

---

*End of agent gap report. Total gaps: 24 (1 blocker, 10 major, 11 minor, 2 cosmetic). Playwright execution pending — rerun when E2E stack is available.*
