# The Gap Report: How I Measured Vertical Readiness

*Part 4 of "From Generic to Vertical" — a series about building one codebase that serves multiple industries.*

---

"Is the accounting vertical ready?" is a binary question with a non-binary answer. Ready for a demo? Probably. Ready for a 3-person firm's daily practice? About 75%. Ready for a 20-person firm with complex compliance requirements? Not yet.

After building the accounting-za packs, I needed a systematic way to measure readiness. Not a feeling — a structured report with categorized gaps, severity ratings, and prioritization. Here's the methodology I used, and what it found.

## The Gap Report Methodology

I ran a QA cycle against the accounting vertical — automated Playwright tests walking through the complete lifecycle of an accounting firm's typical month:

**Day 0-30: Client onboarding**
- Create a new client (Pty Ltd)
- Fill in FICA custom fields (registration number, VAT, SARS reference, entity type)
- Start onboarding → verify FICA checklist appears with correct items
- Complete checklist items → verify auto-transition to ACTIVE
- Create a project (monthly bookkeeping engagement)

**Day 30-60: Active work**
- Log time entries against the project
- Create tasks, assign team members
- Upload documents
- Generate an engagement letter from template
- Send information request to client (year-end docs)

**Day 60-90: Billing cycle**
- Review unbilled time
- Generate invoice from time entries
- Preview invoice (verify VAT, ZAR formatting)
- Review profitability report

Each step was tested both through the UI (Playwright) and through the API (direct endpoint testing). The QA agents recorded what worked, what failed, and what was missing.

## What the Report Found

27 gaps total: 1 blocker, 12 major, 12 minor, 2 cosmetic.

### Blocker (1)

**GAP-001: No proposal/engagement letter workflow.** The firm can generate an engagement letter from a template, but there's no *workflow* for sending it to the client, tracking whether they've accepted it, and linking acceptance to the engagement. This is the core document in accounting firm onboarding — without it, the firm can't formalize the client relationship.

Impact: Can't complete client onboarding. No engagement letter = no formal agreement = no billing.

### Major Gaps (selected examples)

**GAP-008B: FICA field groups not auto-attached during customer creation.** The accounting-za custom fields (Company Registration Number, VAT Number, SARS Tax Reference, Entity Type) exist in the system, but aren't automatically shown on the customer creation form. The user has to manually navigate to custom fields and attach the group. For a firm creating 10+ clients during setup, this is painful friction.

Fix: The `autoApply` flag exists on the field group definition, but the frontend wasn't reading it during customer creation. A 3-line fix.

**GAP-P47-002: 7 accounting-specific templates not seeded.** The template pack JSON existed for engagement letters, SA tax invoices, and FICA confirmation letters, but the content files (the actual template HTML) were missing for several templates. The seeder ran, found the content file references, and silently skipped them.

Fix: Create the 7 missing content JSON files with SA-specific formatting.

**Currency defaults to USD, not ZAR.** The vertical profile declares `"currency": "ZAR"`, but the rate card defaults and invoice displays still showed `$` signs and USD formatting. The profile currency was being stored correctly in OrgSettings but not propagated to the rate card creation flow.

Fix: Thread `orgSettings.currency` through to the rate card defaults and invoice rendering.

**No `FIELD_DATE_APPROACHING` automation trigger.** The SARS deadline reminder automation was seeded, but the trigger type `FIELD_DATE_APPROACHING` wasn't implemented in the automation engine. The automation rule existed in the database but never fired.

Fix: Implement the trigger type — scan custom fields of type DATE, compare to current date, fire when within threshold.

**No Aged Debtors report.** Accounting firms track unpaid invoices by age: current, 30 days, 60 days, 90+ days. The profitability reports exist but don't segment by age.

Fix: Add an aged debtors view to the invoicing section — a SQL query grouping by `due_date - current_date` intervals.

### Minor Gaps (selected examples)

**Terminology overrides not applied everywhere.** The `t()` function translates "Projects" to "Engagements" for accounting firms, but some page titles and breadcrumbs still show "Projects." Inconsistent terminology confuses users who expect accounting-specific language.

**Onboarding progress tracker missing engagement letter status.** The customer onboarding view shows FICA checklist progress and document uploads, but doesn't show whether the engagement letter has been sent/accepted. Without the proposal workflow (GAP-001), this is moot — but once GAP-001 is fixed, the tracker needs this field.

**No default tax rate for ZAR invoices.** SA invoices should default to 15% VAT. The system doesn't pre-fill tax rates based on currency or vertical profile.

### Cosmetic (2)

**Organization settings page shows "Coming soon."** The settings page where firms configure their name, logo, and brand color exists for some fields but has placeholder sections for currency and timezone.

**Sidebar "Projects" icon doesn't update for "Engagements" terminology.** Minor — the terminology system handles labels but not icon tooltips.

## The Assessment

> "A 3-person accounting firm could run ~75% of daily practice on this platform today."

Specifically:
- **Fully working**: Client creation, FICA compliance checklists, time tracking, project management, document storage, invoicing, profitability reporting, notifications, audit trail
- **Partially working**: Template generation (generic templates work; accounting-specific templates need content files), automation rules (triggers partially implemented)
- **Missing**: Proposal/engagement letter workflow (blocker), aged debtors reporting, SARS deadline automation

The 75% assessment means: the firm could manage clients, track time, generate invoices, and monitor profitability. They'd need to handle engagement letters manually (email + manual tracking) and do aged debtors analysis in a spreadsheet. Not ideal, but workable.

## Prioritization

With 27 gaps, you can't fix everything at once. Here's the prioritization framework:

**Priority 1 — Blockers**: Fix first. GAP-001 (proposal workflow) blocks the complete onboarding flow. Without it, the product is a demo, not a tool.

**Priority 2 — Major gaps that affect daily use**: Fix before first customer. Currency display, field auto-attachment, missing templates. These create friction that makes the firm question whether the tool understands their practice.

**Priority 3 — Major gaps that affect monthly cycles**: Fix before first billing cycle. Aged debtors, SARS automation triggers. The firm can survive a month without these, but they'll notice by month two.

**Priority 4 — Minor and cosmetic**: Fix over time. Terminology inconsistencies, tooltip text, settings page polish. Important for professionalism but not for functionality.

Total estimated effort: Priority 1-2 = ~2 weeks. Priority 3 = ~1 week. Priority 4 = ongoing polish.

## Running Your Own Gap Report

The methodology is generalizable to any vertical:

1. **Define a lifecycle script**: The typical month/quarter of your target user. What do they do, in what order?

2. **Walk through it systematically**: UI test every step. API test every endpoint involved. Document what works, what fails, what's missing.

3. **Categorize by severity**: Blocker (can't complete core workflow), Major (daily friction), Minor (inconsistency), Cosmetic (polish).

4. **Assess the percentage**: What fraction of the lifecycle script succeeds? 75% is "demo-ready with manual workarounds." 90% is "usable with known limitations." 95%+ is "production-ready."

5. **Prioritize by workflow order**: Fix gaps that block earlier lifecycle stages first. A firm that can't onboard clients will never reach the invoicing stage.

The gap report isn't a one-time exercise. I ran four QA cycles for the accounting vertical, with 8 PRs fixing issues between cycles. Each cycle found fewer gaps and the percentage improved. The discipline is: measure, fix, re-measure.

---

*Next in this series: [Terminology Overrides: Projects → Engagements Without Forking the Frontend](05-terminology-overrides.md)*

*Previous: [Document Templates Per Vertical](03-document-templates-per-vertical.md)*
