# Phase 64 — Legal Vertical QA: Terminology, Matter Templates & 90-Day Lifecycle

## System Context

The legal vertical for SA law firms is feature-complete across three phases:

- **Phase 55** (Legal Foundations): Court calendar, conflict check (pg_trgm fuzzy search), adverse party registry, LSSA tariff schedules, prescription tracking — all with frontend pages. Module-gated behind `legal-za` profile.
- **Phase 60** (Trust Accounting): Double-entry ledger with 8 transaction types, 5 bank CSV parsers, 3-way reconciliation, daily-balance interest calculation with LPFF split, trust investments, 7 report types including Section 35 data pack. Full frontend: dashboard, transactions, client ledgers, reconciliation, interest wizard, investments, reports.
- **Phase 61** (Legal Compliance): §86(3) firm-discretion vs §86(4) client-instructed investment distinction, KYC verification integration via BYOAK adapters (VerifyNow, Check ID SA).

**Existing infrastructure relevant to this phase:**

- **Terminology system** (ADR-185): Static `TERMINOLOGY` map in `frontend/lib/terminology-map.ts` with `t(term)` function via `TerminologyProvider` React context. A `legal-za` entry already exists but has incorrect/incomplete mappings.
- **Project templates**: Template pack system exists (Phase 12), seeded per vertical profile via `FieldPackSeeder`. Accounting vertical has template packs; legal-za profile references template packs but may not have project workflow templates.
- **Vertical profiles**: `backend/src/main/resources/vertical-profiles/legal-za.json` defines enabled modules, field packs, compliance packs, terminology reference.
- **E2E infrastructure**: Mock-auth stack (port 3001/8081/8090), Playwright with auth fixtures, `loginAs()` helper, Mailpit for email verification.
- **Screenshot support**: Playwright configured with `screenshot: 'only-on-failure'`. No `toHaveScreenshot()` visual regression baselines exist.

## Objective

1. **Fix and extend** the legal terminology map so the UI feels purpose-built for law firms.
2. **Create 4 matter-type project templates** (Litigation, Estates, Collections, Commercial) with pre-populated task lists.
3. **Write and execute a 90-day lifecycle QA test plan** for a fictional SA general-practice law firm, validating every legal module end-to-end.
4. **Capture both regression baselines and curated screenshots** for visual validation and content reuse.

## Constraints & Assumptions

- **Phase 61 must be complete** before QA execution (§86(3)/(4) distinction is tested on Day 60).
- **No conveyancing template** — too many conditional paths (bond types, sectional title, etc.). Deferred to vertical fork.
- **E2E stack must support legal-za profile** — seed data must include legal-za profile activation or the QA plan's Day 0 must configure it.
- **Screenshots are additive** — no changes to existing Playwright test suite, only new test files for the lifecycle plan.
- **Terminology fix is content-only** — no changes to the `TerminologyProvider` infrastructure (ADR-185), just the map entries in `terminology-map.ts`.

---

## Section 1 — Terminology Pack Fix & Extension

### 1.1 Current State (Needs Correction)

The `legal-za` entry in `frontend/lib/terminology-map.ts` currently has:

| Current Mapping | Issue |
|---|---|
| `Time Entry → Fee Note` | **Wrong.** Fee Note = Invoice in SA legal practice. Time Entry should stay as "Time Entry" or become "Time Recording". |
| `Document → Pleading` | **Too narrow.** "Pleading" applies to litigation only, not estates, commercial, or collections. Should be "Document" (keep as-is) or remove the override entirely. |
| `Task → Work Item` | **Acceptable but non-standard.** SA law firms typically say "Action Item" or just "Task". Either is fine. |

### 1.2 Corrected Terminology Map

| Generic Term | Legal Term (en-ZA) | Rationale |
|---|---|---|
| Project / project | Matter / matter | Standard SA legal terminology |
| Projects / projects | Matters / matters | — |
| Customer / customer | Client / client | Universal legal usage |
| Customers / customers | Clients / clients | — |
| Proposal / proposal | Engagement Letter / engagement letter | Standard SA legal engagement instrument |
| Proposals / proposals | Engagement Letters / engagement letters | — |
| Invoice | Fee Note | "Fee Note" or "Statement of Account" — Fee Note is more common in SA practice |
| Invoices | Fee Notes | — |
| invoice | fee note | — |
| invoices | fee notes | — |
| Expense | Disbursement | Standard legal billing term for costs advanced on client's behalf |
| Expenses | Disbursements | — |
| expense | disbursement | — |
| expenses | disbursements | — |
| Budget | Fee Estimate | Law firms estimate fees, not "budget" work |
| Task / task | Action Item / action item | Common in SA legal practice management |
| Tasks / tasks | Action Items / action items | — |
| Rate Card | Tariff Schedule | Correct — LSSA uses "tariff" terminology |
| Rate Cards | Tariff Schedules | — |
| Retainer | Mandate | SA legal term for ongoing client engagement |
| Retainers | Mandates | — |
| retainer | mandate | — |
| retainers | mandates | — |
| Time Entry | Time Recording | Neutral term; "time entry" is acceptable too but "recording" is slightly more formal |
| Time Entries | Time Recordings | — |

**Remove:** `Document → Pleading` mapping (too narrow for a general practice).

### 1.3 Implementation

- **File**: `frontend/lib/terminology-map.ts` — update the `legal-za` object
- **Test**: Update `frontend/__tests__/terminology.test.ts` and `frontend/__tests__/terminology-integration.test.ts`
- **Verify**: Navigate all major pages with legal-za profile active; confirm no "Project", "Customer", "Invoice", "Expense", "Budget" visible in nav, headings, breadcrumbs, buttons, empty states

---

## Section 2 — Matter-Type Workflow Templates

### 2.1 Template Infrastructure

Check how the existing accounting template pack seeds project templates. The legal-za profile should seed 4 project templates that appear in the "New Matter" creation flow. Each template pre-populates tasks (action items) with default priorities and suggested role assignments.

Templates should be linked to the `matter_type` custom field value so the system can auto-suggest the right template when a matter type is selected.

### 2.2 Template Definitions

#### Litigation (Personal Injury / General)

**matter_type**: `LITIGATION`

| # | Action Item | Priority | Suggested Role | Notes |
|---|---|---|---|---|
| 1 | Initial consultation & case assessment | HIGH | Attorney | Taking instructions, evaluating merits |
| 2 | Letter of demand | HIGH | Attorney | Pre-litigation demand per Prescription Act |
| 3 | Issue summons / combined summons | HIGH | Attorney | Court filing |
| 4 | File plea / exception / counterclaim | HIGH | Attorney | Defensive pleading |
| 5 | Discovery — request & exchange documents | MEDIUM | Candidate Attorney | Rule 35 discovery |
| 6 | Pre-trial conference preparation | MEDIUM | Attorney | Rule 37 conference |
| 7 | Trial / hearing attendance | URGENT | Attorney | Court appearance |
| 8 | Post-judgment — taxation of costs / appeal | MEDIUM | Attorney | If applicable |
| 9 | Execution — warrant / attachment | LOW | Candidate Attorney | If judgment in favour |

#### Estates (Deceased Estate Administration)

**matter_type**: `ESTATES`

| # | Action Item | Priority | Suggested Role | Notes |
|---|---|---|---|---|
| 1 | Obtain death certificate & will | HIGH | Attorney | First step, required for reporting |
| 2 | Report estate to Master of High Court | HIGH | Attorney | J294 form within 14 days |
| 3 | Obtain Letters of Executorship | HIGH | Attorney | Master issues after acceptance |
| 4 | Advertise for creditors (Gazette + local newspaper) | MEDIUM | Candidate Attorney | 30-day notice period |
| 5 | Inventory of assets & liabilities | HIGH | Attorney | Full estate inventory |
| 6 | Open estate bank account | MEDIUM | Candidate Attorney | Trust/estate banking |
| 7 | Prepare Liquidation & Distribution account | HIGH | Attorney | Core deliverable |
| 8 | Lodge L&D account with Master | HIGH | Attorney | Filing + inspection period |
| 9 | Distribute estate & file final account | MEDIUM | Attorney | Close out matter |

#### Collections (Debt Recovery)

**matter_type**: `COLLECTIONS`

| # | Action Item | Priority | Suggested Role | Notes |
|---|---|---|---|---|
| 1 | Client intake & verify claim documentation | HIGH | Candidate Attorney | Confirm amount, basis, supporting docs |
| 2 | Skip tracing / debtor address verification | MEDIUM | Candidate Attorney | TPN, credit bureau, CIPC searches |
| 3 | Letter of demand — Section 129 notice | HIGH | Attorney | NCA-compliant 20 business day notice |
| 4 | Issue summons | HIGH | Attorney | If demand unanswered |
| 5 | Apply for default judgment | MEDIUM | Attorney | If no appearance to defend |
| 6 | Issue warrant of execution — movables | MEDIUM | Candidate Attorney | Sheriff instruction |
| 7 | Issue warrant of execution — immovables (if applicable) | LOW | Attorney | Property attachment (rare for small claims) |
| 8 | Sale in execution / asset attachment | LOW | Attorney | Auction process |
| 9 | Close matter & report to client | LOW | Candidate Attorney | Final account, archive |

#### Commercial (Corporate & Contract)

**matter_type**: `COMMERCIAL`

| # | Action Item | Priority | Suggested Role | Notes |
|---|---|---|---|---|
| 1 | Client intake & scope of work | HIGH | Attorney | Define engagement scope |
| 2 | Due diligence review | HIGH | Attorney | Legal/commercial review |
| 3 | First draft — agreement / memorandum | HIGH | Attorney | Primary drafting |
| 4 | Internal review & quality check | MEDIUM | Senior Attorney | Peer review |
| 5 | Client review & feedback incorporation | MEDIUM | Attorney | Client comments, redlines |
| 6 | Counterparty negotiation | MEDIUM | Attorney | Multi-round negotiation |
| 7 | Final draft & execution copies | HIGH | Candidate Attorney | Clean copies for signing |
| 8 | Signing & witness coordination | MEDIUM | Candidate Attorney | Logistics, witnessing |
| 9 | Post-execution — filing & compliance | LOW | Candidate Attorney | CIPC filings, register updates |

### 2.3 Implementation

- **Backend**: Seed templates via the existing template pack system. Check `src/main/resources/` for template pack JSON files and `FieldPackSeeder` or equivalent template seeder.
- **Frontend**: Verify templates appear in "New Matter" creation flow when legal-za profile is active.
- **Linking**: If the template system supports auto-suggestion based on custom field values, link each template to its `matter_type` value.

---

## Section 3 — Screenshot Infrastructure

### 3.1 Regression Baselines

Add Playwright `toHaveScreenshot()` support for legal vertical pages.

**Configuration** (in `playwright.config.ts` or a legal-lifecycle-specific config):
```typescript
expect: {
  toHaveScreenshot: {
    maxDiffPixelRatio: 0.01,
    animations: 'disabled',
  },
}
```

**Baseline storage**: `e2e/screenshots/legal-lifecycle/` (committed to git as the "approved" visual state).

**Naming convention**: `day-{DD}-{feature}-{state}.png`
- Example: `day-00-dashboard-legal-nav.png`, `day-30-fee-note-tariff-lines.png`

### 3.2 Curated Walkthrough Shots

Higher-resolution captures for documentation, blog, and investor deck.

**Storage**: `documentation/screenshots/legal-vertical/`

**Naming**: Descriptive for content reuse:
- `trust-dashboard-overview.png`
- `conflict-check-clear-result.png`
- `conflict-check-adverse-match.png`
- `fee-note-tariff-disbursement.png`
- `bank-reconciliation-matched.png`
- `interest-run-lpff-split.png`
- `section-35-report.png`

**Capture method**:
- `page.screenshot({ fullPage: true })` for full-page captures
- `locator.screenshot()` for component-level hero shots
- 2x device scale factor for retina-quality images

### 3.3 Implementation

- **New Playwright test file**: `e2e/tests/legal-lifecycle.spec.ts` (or split per day segment)
- **Helper**: Screenshot capture utility that saves to both regression and curated directories when flagged
- **No changes to existing tests** — purely additive

---

## Section 4 — 90-Day QA Lifecycle Test Plan

### 4.1 Test Firm Profile

**Firm**: Mathebula & Partners
**Type**: General/mixed practice, 4 attorneys, Johannesburg
**Profile**: `legal-za`
**Currency**: ZAR
**Stack**: E2E mock-auth (port 3001 / backend 8081 / Mailpit 8026)

**Team**:
| User | Role | Billing Rate | Cost Rate | Persona |
|---|---|---|---|---|
| Alice | Owner | R2,500/hr | R1,000/hr | Senior Partner, 20yr experience |
| Bob | Admin | R1,200/hr | R500/hr | Associate, 8yr experience |
| Carol | Member | R550/hr | R200/hr | Candidate Attorney, 1yr articles |

### 4.2 Client Archetypes

| # | Client | Entity Type | Matter Type | Primary Legal Module Exercised |
|---|---|---|---|---|
| 1 | Sipho Ndlovu | Individual | Litigation (personal injury) | Court calendar, prescription tracking, hourly + tariff billing |
| 2 | Apex Holdings (Pty) Ltd | Company | Commercial (shareholder agreement) | Fixed-fee billing, disbursements, fee estimate tracking |
| 3 | Moroka Family Trust | Trust | Estates (deceased estate) | Trust deposits, client ledger, interest, investments, Section 35 |
| 4 | QuickCollect Services | Company | Collections (debt recovery) | Bulk matters, letter of demand workflow, default judgment |

### 4.3 Day-by-Day Outline

| Day | Theme | Key Verifications |
|---|---|---|
| 0 | Firm setup | Legal-za profile active, terminology correct, trust account created, rates set, modules enabled |
| 1-3 | Client onboarding (4 clients) | Conflict checks (all clear), FICA/KYC, matter creation from templates, custom fields |
| 7 | First week of work | Time logging by 3 users, rate snapshots, court date creation, comments, My Work |
| 14 | Trust deposits & conflict detection | 2 trust deposits approved, client ledger balances, conflict check with adverse party match |
| 30 | First billing cycle | 4 fee notes (hourly+tariff, fixed, trust fee transfer, collections), LSSA tariff lines, disbursements, VAT |
| 45 | Reconciliation & prescription | Bank CSV upload, 3-way reconciliation, prescription tracking, court date lifecycle, payment recording |
| 60 | Interest run & second billing | Interest calculation with LPFF split, §86(3)/(4) investment placement, second billing cycle, reports |
| 75 | Complex engagement & adverse parties | Multi-matter per client, adverse party registry, conflict stress test, estate progression |
| 90 | Quarter review & Section 35 | Portfolio review, Section 35 report, trust reports, profitability, dashboard, role-based access, final verdict |

**Full step-by-step plan**: Written as `tasks/qa-legal-lifecycle-test-plan.md` with checkable items following the exact format of the existing accounting plan (`tasks/qa-lifecycle-test-plan.md`).

---

## Out of Scope

- **Conveyancing template** — too many conditional paths for a general template. Fork-day feature.
- **Matter closure workflow** — no formal archive/close process exists in the platform.
- **Dedicated disbursements module** — expenses feature covers this; no separate legal costs tracking.
- **Smart deadline-to-calendar scheduling** — prescription dates calculated but not auto-scheduled.
- **Terminology for deeply nested component text** — per ADR-185, only ~30-40 high-visibility locations are overridden (nav, headings, breadcrumbs, buttons, empty states).
- **Multi-language i18n** — English-only; terminology switching is English-to-English, not translation.

## ADR Topics

- No new ADRs needed. This phase extends existing infrastructure (ADR-185 terminology, existing template system, existing Playwright setup).

## Style & Boundaries

- Follow all conventions in `frontend/CLAUDE.md` and `backend/CLAUDE.md`.
- Terminology changes are content-only — update the static map, not the infrastructure.
- Templates follow existing seed patterns — no new entity types.
- QA test plan follows the exact format of `tasks/qa-lifecycle-test-plan.md`.
- Screenshots are additive Playwright tests — no modifications to existing test suite.
