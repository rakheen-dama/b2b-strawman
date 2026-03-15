# Phase 47 — Vertical QA: Small SA Accounting Firm (Accelerated-Clock Shakeout)

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 46 phases of functionality: projects, customers, tasks, time tracking, invoicing (with tax, payments, bulk billing), rate cards, budgets, profitability dashboards, document templates (Tiptap + Word), proposals, document acceptance, retainer agreements, recurring tasks/schedules, expenses, custom fields, tags, saved views, workflow automations, resource planning, comments, notifications, activity feeds, audit trails, client information requests, customer portal, reporting/export, email delivery, command palette, RBAC with custom roles, Keycloak auth with Gateway BFF, and admin-approved org provisioning.

**The problem**: Every feature has been tested in isolation — unit tests, integration tests, slice-by-slice verification — but nobody has walked through a complete real-world workflow end to end. There is no confidence that the platform supports an actual firm's daily operations: onboarding a client, doing a month of work, billing them, collecting payment, reviewing profitability. Fields may be wrong for the target vertical. Document templates may produce unusable output. Automation triggers may not match real workflow patterns. Information gates may block at the wrong moments. The system *works* but has never been *used*.

**The fix**: A structured QA phase that picks a concrete vertical — a small South African accounting firm — and simulates their first 90 days on the platform. An accelerated-clock walkthrough exercises every major workflow. Two passes: an agent-driven first pass produces a raw gap report, then the founder does a guided second pass for UX and workflow judgement. The output is a categorised gap analysis and a fix backlog, plus a reusable accounting vertical profile.

## Objective

1. **Create an accounting firm vertical profile** — a complete configuration (terminology, field packs, template packs, compliance packs, clause packs, automation rule templates, rate card defaults) that makes the platform feel purpose-built for a small SA accounting practice.
2. **Script a 90-day accelerated lifecycle** covering the full rhythm of a 3-person accounting firm: prospect intake, FICA onboarding, engagement letter, monthly bookkeeping cycle, time tracking, billing, payment, ad-hoc advisory, quarterly profitability review, year-end tax return.
3. **Execute the script (agent pass)** against the E2E mock-auth stack, logging every friction point, missing field, broken gate, wrong template variable, confusing flow, or dead end.
4. **Execute the script (founder pass)** with a structured walkthrough guide, capturing UX judgement, workflow friction, and "this works but feels wrong" moments.
5. **Produce a consolidated gap analysis** — every finding categorised, prioritised, and ready to feed into fix phases.

## Constraints & Assumptions

- **The vertical is SA small accounting firm.** 3 people: owner (accountant), admin/bookkeeper, junior. Client base of ~20-40 SMEs. Revenue from monthly retainer bookkeeping + hourly advisory.
- **No new platform features.** This phase configures existing functionality, tests it, and documents gaps. If a gap requires a new feature, it's logged in the gap report for a future phase — not built here.
- **The vertical profile is real and reusable.** It should be production-quality: if an accounting firm signs up tomorrow and this profile is applied, it should feel right. Not test data — real field names, real template content, real compliance checklists.
- **E2E stack is the test environment.** All agent-driven testing runs against `compose/docker-compose.e2e.yml` with mock auth. The stack must be running before script execution.
- **Accelerated clock is scripted, not time-manipulated.** We don't change system clocks. The script simulates "day 30" by creating the appropriate state (e.g., 30 days of time entries, a retainer period ready to close). Each "day" is a section of the script.
- **The gap report is the primary deliverable.** Code changes in this phase are limited to: the vertical profile data (field packs, templates, etc.), the 90-day script, and the gap report. No production code fixes.
- **SA-specific context matters.** Fields should use South African terminology: company registration number (not EIN), VAT number (not GST), SARS tax reference, FICA documentation requirements, CIPC references. Document templates should reference SA legislation where relevant.

---

## Section 1 — Accounting Firm Vertical Profile

### 1.1 Target Persona

**Firm**: "Thornton & Associates" — a 3-person accounting practice in Johannesburg.

| Role | Name | Responsibilities | OrgRole |
|------|------|-----------------|---------|
| Owner | Alice (existing E2E user) | Client advisory, tax, firm management, profitability review | Owner |
| Admin/Bookkeeper | Bob (existing E2E user) | Client comms, invoicing, document prep, bookkeeping | Admin |
| Junior | Carol (existing E2E user) | Data capture, basic bookkeeping, time logging | Member |

**Clients** (to be created during walkthrough):

| Client | Type | Service | Billing Model |
|--------|------|---------|---------------|
| Kgosi Construction (Pty) Ltd | SME, construction | Monthly bookkeeping + annual tax | Retainer (R5,500/month) |
| Naledi Hair Studio | Sole proprietor, beauty | Annual tax return only | Hourly (R850/hr) |
| Vukani Tech Solutions (Pty) Ltd | SME, tech startup | Monthly bookkeeping + advisory | Retainer (R8,000/month) + hourly advisory overflow |
| Moroka Family Trust | Trust, property | Annual compliance + trust returns | Fixed fee per engagement |

### 1.2 Terminology Mapping

The i18n message catalog (Phase 43) supports overrides. The accounting vertical profile sets:

| Platform Term | Accounting Term | Notes |
|---------------|----------------|-------|
| Projects | Engagements | Standard accounting terminology |
| Tasks | Work Items | Less generic than "tasks" for professional services |
| Customers | Clients | Universal in accounting |
| Documents | Documents | No change needed |
| Proposals | Engagement Letters | SA accounting standard terminology |
| Time Entries | Time Records | Minor preference |
| Rate Cards | Fee Schedule | Standard accounting terminology |

**Implementation**: Create `frontend/src/messages/en-ZA-accounting/` with override files. The vertical profile specifies which message namespace to load. If the i18n system doesn't support vertical overrides yet, document this as a gap (it likely doesn't — Phase 43 built the catalog but not vertical switching).

### 1.3 Field Packs

Custom field definitions for accounting-specific data. These extend the existing field pack seeder (Phase 11, Epic 90).

**Client (Customer) Fields:**

| Field Name | Field Type | Required | Pack | Notes |
|------------|-----------|----------|------|-------|
| Company Registration Number | text | Yes (for Pty Ltd) | accounting-za | CIPC registration (e.g., 2024/123456/07) |
| Trading As | text | No | accounting-za | Many SA SMEs trade under a different name |
| VAT Number | text | No | accounting-za | Only if VAT-registered (threshold R1M turnover) |
| SARS Tax Reference | text | Yes | accounting-za | Income tax reference number |
| SARS eFiling Profile Number | text | No | accounting-za | For electronic filing |
| Financial Year-End | date | Yes | accounting-za | Usually Feb or Jun for SA companies |
| Entity Type | select | Yes | accounting-za | Options: Pty Ltd, Sole Proprietor, CC, Trust, Partnership, NPC |
| Industry (SIC Code) | text | No | accounting-za | SARS SIC code for tax returns |
| Registered Address | text | Yes | accounting-za | Physical address (CIPC requirement) |
| Postal Address | text | No | accounting-za | If different from registered |
| Primary Contact Name | text | Yes | accounting-za | Person at the client company |
| Primary Contact Email | text | Yes | accounting-za | For correspondence |
| Primary Contact Phone | text | No | accounting-za | |
| FICA Verified | select | Yes | accounting-za | Options: Not Started, In Progress, Verified |
| FICA Verification Date | date | No | accounting-za | Date FICA was completed |
| Referred By | text | No | accounting-za | Tracking referral source |

**Engagement (Project) Fields:**

| Field Name | Field Type | Required | Pack | Notes |
|------------|-----------|----------|------|-------|
| Engagement Type | select | Yes | accounting-za | Options: Monthly Bookkeeping, Annual Tax Return, Annual Financial Statements, Advisory, Trust Administration, Company Secretarial, VAT Returns, Payroll, Other |
| Tax Year | text | No | accounting-za | e.g., "2026" — relevant for tax return engagements |
| SARS Submission Deadline | date | No | accounting-za | Auto-populated based on engagement type + financial year-end |
| Assigned Reviewer | text | No | accounting-za | Senior person who reviews before client delivery |
| Complexity | select | No | accounting-za | Options: Simple, Moderate, Complex |

### 1.4 Compliance Pack (FICA/KYC)

Extends the existing compliance checklist system (Phase 14). For SA accounting firms, FICA (Financial Intelligence Centre Act) requires:

**Checklist: "FICA KYC — SA Accounting"**

| Item | Description | Required |
|------|------------|----------|
| Certified ID Copy | Certified copy of director/member ID document | Yes |
| Proof of Residence | Utility bill or bank statement (< 3 months old) | Yes |
| Company Registration (CM29/CoR14.3) | CIPC company registration certificate | Yes (companies only) |
| Tax Clearance Certificate | SARS tax clearance or compliance status pin | Yes |
| Bank Confirmation Letter | Confirmation of banking details from the bank | Yes |
| Proof of Business Address | Lease agreement or utility bill for business premises | No |
| Resolution / Mandate | Board resolution authorising engagement | No (recommended for Pty Ltd) |
| Beneficial Ownership Declaration | Declaration of ultimate beneficial owners (>25%) | Yes (FICA Amendment Act) |
| Source of Funds Declaration | Client declaration on source of funds | Risk-based |

### 1.5 Document Template Pack

Templates for the accounting vertical. Uses existing Tiptap template system (Phase 31) with `{{variable}}` syntax.

**Templates to Create:**

| Template | Type | Key Variables | Notes |
|----------|------|--------------|-------|
| Engagement Letter — Monthly Bookkeeping | Tiptap | client.name, client.registrationNumber, engagement.monthlyFee, org.name, org.brandColor | Standard SAICA format |
| Engagement Letter — Annual Tax Return | Tiptap | client.name, client.taxReference, engagement.fee, engagement.taxYear | |
| Engagement Letter — Advisory | Tiptap | client.name, engagement.hourlyRate, engagement.estimatedHours | |
| Monthly Report Cover | Tiptap | client.name, engagement.month, org.name | Cover page for monthly deliverables |
| Invoice | Tiptap | Standard invoice variables (already exist) | Verify SA-specific: VAT number, bank details |
| Statement of Account | Tiptap | client.name, outstanding amounts | If supported — may be a gap |
| FICA Confirmation Letter | Tiptap | client.name, verification date | Confirms FICA compliance to client |

**Clause Pack:**

| Clause | Usage | Notes |
|--------|-------|-------|
| Limitation of Liability | Engagement letters | Standard SAICA recommended clause |
| Fee Escalation | Engagement letters | Annual fee increase clause (CPI-linked) |
| Termination | Engagement letters | 30-day written notice standard |
| Confidentiality | Engagement letters | Client data protection commitment |
| Document Retention | Engagement letters | SARS requires 5-year retention |
| Third-Party Reliance | Engagement letters | Limits who can rely on the work product |
| Electronic Communication Consent | Engagement letters | Consent to email/portal communication |

### 1.6 Automation Rule Templates

Pre-configured workflow automation rules (Phase 37) for accounting firm workflows:

| Rule | Trigger | Condition | Action |
|------|---------|-----------|--------|
| FICA Reminder | Client created | FICA Verified = "Not Started" after 7 days | Notify assigned member |
| Engagement Letter Follow-up | Proposal sent | Not accepted after 5 days | Notify owner |
| Monthly Bookkeeping Task Creation | Schedule fires (1st of month) | -- | Already handled by recurring schedules |
| Tax Deadline Approaching | -- | SARS Submission Deadline within 14 days | Notify assigned member + owner |
| Budget Alert — Engagement | Time logged | Budget consumed > 80% | Notify owner |
| Invoice Overdue | Invoice sent | Not paid after 30 days | Notify admin/bookkeeper |

**Note**: Some of these may not be achievable with the current automation trigger/condition model (Phase 37). Document any gaps — e.g., "date-field-based triggers" may not exist.

### 1.7 Rate Card Defaults

| Rate Type | Role | Hourly Rate (ZAR) | Notes |
|-----------|------|-------------------|-------|
| Standard Billing Rate | Owner | R1,500/hr | Advisory, complex tax |
| Standard Billing Rate | Admin/Bookkeeper | R850/hr | General bookkeeping |
| Standard Billing Rate | Junior | R450/hr | Data capture |
| Cost Rate | Owner | R650/hr | For profitability calc |
| Cost Rate | Admin/Bookkeeper | R350/hr | |
| Cost Rate | Junior | R180/hr | |

Default currency: ZAR (South African Rand).

---

## Section 2 — 90-Day Accelerated Lifecycle Script

The script is divided into "days" that represent key moments in the firm's lifecycle. Each day has a set of actions, expected outcomes, and checkpoints.

### Day 0 — Firm Setup

**Actor**: Alice (Owner)

1. **Getting Started checklist** — verify it appears on dashboard
2. **Organisation settings**:
   - Set org name: "Thornton & Associates"
   - Upload logo (test with a placeholder image)
   - Set default currency to ZAR
   - Set branding colour
3. **Rate cards** — configure billing rates and cost rates per team member (Section 1.7)
4. **Tax rates** — configure SA VAT (15%) as a tax rate
5. **Team** — verify Alice, Bob, Carol exist with correct roles (Owner, Admin, Member)
6. **Custom fields** — verify accounting field pack is applied (or apply it manually)
7. **Document templates** — verify accounting template pack is available
8. **Compliance checklist** — verify FICA/KYC checklist template exists
9. **Automation rules** — apply accounting automation rule templates

**Checkpoints**:
- [ ] Dashboard shows getting started checklist
- [ ] Rate cards show correct ZAR rates for all 3 members
- [ ] VAT rate of 15% is configured
- [ ] Custom fields appear on customer/project forms
- [ ] Template pack has accounting-specific templates
- [ ] FICA checklist template exists
- [ ] Automation rules are active

### Day 1 — First Client Onboarding (Kgosi Construction)

**Actor**: Bob (Admin/Bookkeeper)

1. **Create customer**: Kgosi Construction (Pty) Ltd
   - Fill all accounting custom fields (registration number, VAT number, SARS ref, etc.)
   - Entity Type: Pty Ltd
   - Financial Year-End: 28 February
2. **FICA/KYC onboarding** — verify checklist is instantiated
   - Mark "Certified ID Copy" as complete
   - Mark "Company Registration" as complete
   - Verify progress tracking works
3. **Customer lifecycle** — verify customer status (should be PROSPECT → ONBOARDING)
4. **Information request** — send FICA document request to client via portal
   - Verify portal link works
   - Verify email notification fires (check Mailpit)

**Actor**: Alice (Owner)

5. **Create proposal/engagement letter** — Monthly Bookkeeping engagement
   - Use accounting engagement letter template
   - Set fee: R5,500/month
   - Include clauses: limitation of liability, fee escalation, termination, confidentiality
   - Send for acceptance
6. **Verify portal** — check proposal appears in customer portal
7. **Accept proposal** (simulate client acceptance via portal)
8. **Create engagement (project)** from accepted proposal
   - Fill engagement custom fields (Engagement Type: Monthly Bookkeeping)
   - Set up retainer: R5,500/month

**Checkpoints**:
- [ ] Customer created with all custom fields populated and visible
- [ ] FICA checklist instantiated with correct items
- [ ] Information request sent and visible in portal
- [ ] Engagement letter generated with correct template variables
- [ ] Clauses rendered correctly in the document
- [ ] Proposal visible in portal, acceptance flow works
- [ ] Project created from proposal with correct linking
- [ ] Retainer configured with monthly amount

### Day 2-3 — Additional Client Onboarding

Repeat Day 1 pattern for:
- **Naledi Hair Studio** (sole proprietor, hourly billing, simpler FICA)
- **Vukani Tech Solutions** (Pty Ltd, retainer + hourly overflow)
- **Moroka Family Trust** (trust, fixed fee)

**Focus areas**:
- Different entity types trigger correct custom field requirements
- Sole proprietor vs. Pty Ltd vs. Trust differences in FICA checklist
- Hourly vs. retainer vs. fixed fee billing model setup
- Engagement letter template adapts to different engagement types

**Checkpoints**:
- [ ] All 4 clients onboarded with correct configurations
- [ ] Different billing models correctly set up
- [ ] Field validation catches missing required fields
- [ ] Entity type affects the appropriate forms/checklists

### Day 7 — First Week of Work

**Actor**: Carol (Junior) — daily bookkeeping work

1. **Navigate to assigned tasks** (My Work page)
2. **Log time** on Kgosi Construction — Monthly Bookkeeping
   - 3 hours, "Captured bank statements and receipts for January"
   - Verify time entry has correct rate snapshot (R450/hr)
3. **Log time** on Vukani Tech — Monthly Bookkeeping
   - 2 hours, "Reconciled Sage accounts"
4. **Mark tasks as in progress** — verify task lifecycle transitions

**Actor**: Bob (Admin/Bookkeeper) — client communication

5. **Add comment** on Kgosi Construction engagement
   - "Missing February bank statements — sent follow-up email to Thabo"
6. **Log time** on Kgosi Construction
   - 1 hour, "Client liaison — outstanding documentation follow-up"
   - Verify rate snapshot (R850/hr)
7. **Upload document** to Kgosi Construction engagement
   - Test document upload flow

**Actor**: Alice (Owner) — advisory work

8. **Log time** on Naledi Hair Studio — ad-hoc advisory call
   - 0.5 hours, "Tax planning discussion — provisional tax implications"
   - Verify rate snapshot (R1,500/hr)

**Checkpoints**:
- [ ] My Work page shows correct tasks per user
- [ ] Time entries created with correct rate snapshots
- [ ] Task lifecycle transitions work (not started → in progress)
- [ ] Comments appear on engagement detail and activity feed
- [ ] Document upload works
- [ ] Activity feed shows all actions in chronological order

### Day 14 — Two Weeks In

**Actor**: Carol (Junior)

1. **Continue logging time** — another ~15 hours across clients
2. **Complete FICA items** for Kgosi Construction (mark remaining items done)
3. **Verify** customer transitions to appropriate lifecycle state after FICA completion

**Actor**: Bob (Admin/Bookkeeper)

4. **Check notifications** — verify automation rules have fired
   - FICA reminder for incomplete clients
   - Any other configured automations
5. **Review time entries** across clients (use reporting/time views)

**Checkpoints**:
- [ ] FICA completion triggers correct customer lifecycle transition
- [ ] Automation notifications fired correctly
- [ ] Time reporting shows accurate hours per client/member
- [ ] Budget tracking updates (if budgets are set)

### Day 30 — First Month-End Billing

**Actor**: Bob (Admin/Bookkeeper)

1. **Retainer period close** — close January period for Kgosi Construction retainer
   - Verify consumption summary (hours used vs. included)
   - Verify retainer invoice is generated
2. **Retainer period close** — close January for Vukani Tech retainer
3. **Hourly billing** — create invoice for Naledi Hair Studio
   - Select unbilled time entries
   - Verify rate calculation (0.5hr × R1,500 = R750)
   - Add VAT (15%)
   - Verify invoice total (R750 + R112.50 = R862.50)
4. **Invoice review** — verify all invoices have correct:
   - Client details (name, VAT number, registration number)
   - Line items with descriptions from time entries
   - Tax calculation
   - Bank details (from org settings)
   - Invoice numbering sequence
5. **Send invoices** — send via email
   - Verify email content (check Mailpit)
   - Verify portal shows invoices
   - Verify payment links (if Stripe/PayFast configured)

**Actor**: Alice (Owner)

6. **Review profitability** — check project profitability dashboard
   - Kgosi Construction: revenue (R5,500 retainer) vs. cost (hours × cost rates)
   - Naledi Hair Studio: revenue (R750) vs. cost
   - Verify utilisation rates per team member
7. **Budget check** — if engagement budgets are set, verify tracking

**Checkpoints**:
- [ ] Retainer period close generates correct invoice
- [ ] Hourly invoice calculates correctly with correct rates
- [ ] VAT (15%) applied correctly on all invoices
- [ ] Invoice template renders with all SA-specific fields
- [ ] Email delivery works (Mailpit receives)
- [ ] Portal shows invoices with payment option
- [ ] Profitability dashboard shows meaningful data
- [ ] Utilisation rates calculate correctly

### Day 45 — Mid-Quarter Operations

**Actor**: Bob (Admin/Bookkeeper)

1. **Bulk billing run** — process all retainer clients for February
   - Verify batch creation flow
   - Verify all retainer invoices generated correctly
   - Approve and send batch
2. **Chase overdue invoices** — check if January invoices are marked as paid
   - Record payment for Kgosi Construction (simulate payment received)
   - Verify invoice status transition (sent → paid)
3. **Expense logging** — log disbursements
   - CIPC annual return filing fee (R150) for Kgosi Construction
   - Verify expense appears in unbilled summary

**Actor**: Alice (Owner)

4. **Create ad-hoc engagement** — Vukani Tech needs a BEE certificate review
   - Create from project template (if one exists for advisory)
   - Estimate 5 hours at R1,500/hr
   - Set budget: 5 hours / R7,500
5. **Resource planning** — check team capacity for the month
   - Verify allocation grid shows current workload
   - Identify if Carol is over-allocated

**Checkpoints**:
- [ ] Bulk billing run processes all retainer clients correctly
- [ ] Payment recording works, invoice status updates
- [ ] Expenses tracked and included in unbilled summary
- [ ] Ad-hoc engagement created from template
- [ ] Budget set and tracking from first time entry
- [ ] Resource planning shows meaningful capacity data

### Day 60 — Second Billing Cycle + Quarterly Review

**Actor**: Bob (Admin/Bookkeeper)

1. **Monthly billing cycle** — repeat Day 30 pattern for March
   - Retainer closes, hourly invoices generated
   - Include expenses in invoices (Kgosi CIPC fee)
2. **Invoice with expenses** — verify expense line items appear correctly on invoice
3. **Ad-hoc invoice** — bill Vukani Tech for BEE advisory work
   - Verify budget vs. actual (5hr budget, actual hours logged)

**Actor**: Alice (Owner)

4. **Quarterly profitability review**:
   - Organisation profitability report
   - Per-client profitability breakdown
   - Team utilisation over 60 days
   - Compare retainer clients vs. hourly clients
5. **Review rate cards** — check if rates need adjustment based on profitability
6. **Reports & export** — generate timesheet report for Q1
   - Export to CSV
   - Verify data accuracy

**Checkpoints**:
- [ ] Second billing cycle runs smoothly
- [ ] Expenses included in invoice correctly
- [ ] Budget tracking accurate for ad-hoc engagement
- [ ] Profitability reports show 60 days of meaningful data
- [ ] Utilisation rates over time are trackable
- [ ] Report export produces accurate, usable data

### Day 75 — Year-End Engagement (Tax Season Prep)

**Actor**: Alice (Owner)

1. **Create year-end engagement** for Kgosi Construction
   - Use "Annual Tax Return" project template
   - Set engagement type: Annual Tax Return
   - Set tax year: 2026
   - Set SARS submission deadline (based on financial year-end)
2. **Send information request** to client
   - Request: trial balance, bank statements, loan agreements, fixed asset register
   - Verify portal information request flow
   - Verify client can upload documents via portal
3. **Generate engagement letter** for year-end work
   - Use annual tax return template
   - Include relevant clauses
   - Send for acceptance

**Actor**: Carol (Junior)

4. **Begin tax return preparation** — log time on year-end engagement
   - Review client-uploaded documents
   - Begin data capture

**Checkpoints**:
- [ ] Year-end engagement created from template with correct fields
- [ ] Information request sent via portal
- [ ] Client can upload documents through portal
- [ ] Engagement letter generated with tax-year-specific content
- [ ] Time logging works on year-end engagement
- [ ] Multiple concurrent engagements per client don't conflict

### Day 90 — Quarter Review + Year-End Wrap

**Actor**: Alice (Owner)

1. **Full profitability review**:
   - 90-day overview: total revenue, total cost, margin
   - Per-client breakdown: most/least profitable
   - Per-engagement-type breakdown: bookkeeping vs. tax vs. advisory
   - Team utilisation: who's billing the most?
2. **Client portfolio review**:
   - All clients listed with status, outstanding amounts, engagement status
   - Any clients with incomplete FICA?
   - Any overdue invoices?
3. **Dashboard review** — does the personal + company dashboard tell a useful story?
4. **Getting Started checklist** — should be fully completed and dismissed by now

**Actor**: Bob (Admin/Bookkeeper)

5. **Document generation** — generate a year-end report document for Kgosi Construction
   - Use monthly report cover template
   - Verify document variables resolve correctly
6. **Saved views** — create saved views for:
   - "My overdue invoices" (filtered invoice list)
   - "Active retainer clients" (filtered client list)
   - Verify views persist and load correctly

**Checkpoints**:
- [ ] 90-day profitability data is meaningful and accurate
- [ ] Client portfolio view gives a complete picture
- [ ] Dashboard tells a useful operational story
- [ ] Getting Started checklist completed and dismissed
- [ ] Document generation works with 90 days of real data
- [ ] Saved views persist and filter correctly
- [ ] Overall: Could this firm actually run on this platform?

---

## Section 3 — Agent-Driven First Pass

### Execution Approach

The agent executes the Day 0–90 script against the E2E mock-auth stack (`compose/docker-compose.e2e.yml`). The agent uses Playwright MCP to navigate the frontend at `http://localhost:3001` and authenticates via mock-login.

**Agent instructions**:
- Follow the script sequentially, day by day
- At each step, attempt the action and record the result
- If an action fails or is impossible, log it as a gap and continue to the next step
- At each checkpoint, verify every item and record pass/fail with evidence (screenshot or API response)
- For every gap found, categorise it immediately (see Section 5)

**Authentication mapping**:
- Alice (Owner) → mock-login as Alice
- Bob (Admin) → mock-login as Bob
- Carol (Member) → mock-login as Carol

**State simulation for accelerated clock**:
- The agent cannot fast-forward time, so "Day 30" actions that depend on 30 days of data require the agent to first create the prerequisite data (e.g., bulk-create time entries for the preceding weeks via API calls or UI)
- When creating prerequisite data, use realistic values (not test garbage) — real descriptions, reasonable hours, correct rates

### Gap Logging Format

For each gap found, the agent records:

```markdown
### GAP-{NNN}: {Short description}

**Day**: {day number}
**Step**: {step description}
**Category**: {content | bug | missing-feature | ux | vertical-specific}
**Severity**: {blocker | major | minor | cosmetic}
**Description**: {What happened vs. what was expected}
**Evidence**: {Screenshot path or API response}
**Suggested fix**: {Brief suggestion if obvious}
```

### Output

The agent produces a single file: `tasks/phase47-gap-report-agent.md` containing:
1. Summary statistics (total gaps by category and severity)
2. All gaps in chronological order (by day)
3. A "critical path blockers" section highlighting anything that prevented the workflow from continuing

---

## Section 4 — Founder-Guided Second Pass

### Walkthrough Guide

After the agent pass produces the gap report, a structured walkthrough guide is generated for the founder. This is NOT a repeat of the agent script — it's a curated path that focuses on:

1. **First impressions** — sign in, see the dashboard, navigate the sidebar. Does it feel like an accounting product?
2. **Client onboarding flow** — onboard one client end to end. Is the information architecture right? Do fields make sense? Is anything confusing?
3. **Daily work rhythm** — log time, check My Work, update tasks. Does the daily loop feel efficient?
4. **Billing cycle** — generate an invoice, review it, send it. Does the invoice look professional? Would a client accept this?
5. **Profitability check** — review the profitability dashboard. Does it tell a useful story? Can you spot which clients are profitable?
6. **Portal check** — view the portal as a client. Does it inspire confidence?
7. **Gap review** — walk through the agent's gap report. Agree/disagree/reprioritise.

### Founder Gap Logging

The founder's findings are appended to: `tasks/phase47-gap-report-founder.md`

Same format as agent gaps but with an additional field:

```markdown
**UX Judgement**: {What feels wrong and why, even if technically functional}
```

---

## Section 5 — Gap Analysis & Fix Prioritisation

### Gap Categories

| Category | Definition | Example |
|----------|-----------|---------|
| **content** | Wrong/missing pack data (fields, templates, clauses, rules) | Missing "Trading As" field on customer form |
| **bug** | Something doesn't work as designed | Invoice VAT calculation rounds incorrectly |
| **missing-feature** | Workflow requires something that doesn't exist | Can't set different tax rates per line item |
| **ux** | Works but confusing, slow, or frustrating | Have to click 6 times to log time on a task |
| **vertical-specific** | Requires accounting-specific logic, not just configuration | Trust accounting rules for Moroka Family Trust |

### Severity Levels

| Severity | Definition | Fix Priority |
|----------|-----------|-------------|
| **blocker** | Cannot continue the workflow at all | Must fix before launch |
| **major** | Workaround exists but workflow is degraded | Fix in first patch phase |
| **minor** | Inconvenient but doesn't block work | Fix when convenient |
| **cosmetic** | Visual or copy issue, no functional impact | Nice to have |

### Consolidation Output

After both passes, produce: `tasks/phase47-gap-analysis-consolidated.md`

Contents:
1. **Executive summary** — how many gaps, by category and severity, overall confidence assessment
2. **Blocker gaps** — must fix before an accounting firm could use this
3. **Major gaps** — grouped by category, with fix effort estimates (S/M/L)
4. **Minor + cosmetic gaps** — listed but not individually estimated
5. **Vertical profile quality assessment** — is the profile good enough to ship?
6. **Recommended fix phases** — suggested grouping of fixes into 1-3 follow-up phases
7. **Fork readiness assessment** — based on the gaps found, is the platform ready for a vertical fork, or does the foundation need more work first?

---

## Out of Scope

- **Fixing gaps found during this phase.** The output is the gap report. Fixes are a separate phase.
- **Automated Playwright test suite.** The agent walkthrough is a one-time structured exploration, not a repeatable test suite. A proper E2E test suite may be recommended in the gap analysis.
- **Multiple verticals.** This phase tests one vertical only (SA accounting). Law, consulting, and other verticals are tested in future QA phases if this one succeeds.
- **Skin/terminology switching infrastructure.** If the platform can't swap terminology per vertical, that's logged as a gap. Building the switching mechanism is a separate phase.
- **Production deployment.** This is local E2E testing only.
- **Trust accounting.** Even though Moroka Family Trust is a test client, trust accounting rules are out of scope. Standard project/invoicing flows are used. Any trust-specific gap is logged as "vertical-specific."
- **Real email/payment integration.** Email is tested via Mailpit. Payments are tested via NoOp adapter or mock. No real Stripe/PayFast transactions.

## ADR Topics

- **ADR: Vertical profile structure** — How should vertical profiles be defined and applied? JSON config vs. database seeder vs. feature flag bundle. Consider: how does a new tenant get the accounting profile applied at provisioning time?
- **ADR: Terminology override mechanism** — How to layer vertical-specific terminology on top of the i18n catalog. Options: per-vertical message files, runtime term mapping, build-time vertical bundles.
- **ADR: QA methodology for vertical readiness** — Formalise the two-pass (agent + founder) approach as a repeatable process for future verticals. What worked, what didn't, what to change.

## Style & Boundaries

- The vertical profile content must be production-quality. Real SA terminology, real FICA requirements, real clause language. Not placeholder text.
- The 90-day script should read like a narrative, not a test script. Each "day" tells a story of what the firm is doing and why.
- Gap reports should be brutally honest. If the platform can't do something, say so clearly. Don't hedge.
- The founder walkthrough guide should be conversational, not a checklist. Guide the founder's attention to the things that matter most.
- Severity ratings should be from the perspective of "could a real accounting firm use this?" — not from a developer's perspective.
- The consolidated gap analysis should end with a clear recommendation: "ship it", "fix N blockers first", or "needs another phase of foundation work."
