# Phase 13 Ideation — Customer Compliance & Lifecycle
**Date**: 2026-02-15

## Lighthouse Domain
- SA small-to-medium law firms (2-20 fee earners)
- FICA compliance is a hard legal obligation for law firms as "accountable institutions"
- POPIA obligations apply to DocTeams as an "operator" (data processor)

## Decision Rationale
User asked specifically about compliance. Two areas explored:
1. **Platform-level POPIA obligations** — mostly contractual, small feature scope (data export, deletion, breach notification)
2. **Customer onboarding compliance** — high-value for law firms (FICA KYC), portable across verticals

**Chosen**: Full "Customer Compliance & Lifecycle" phase combining both areas.

**Options considered for onboarding**:
- Option A: Checklist engine (configurable, builds on custom fields) — **chosen**
- Option B: Integration-first (BYOAK + KYC provider APIs) — deferred, needs BYOAK framework first
- Option C: Lightweight built-in verification (ID checksums, CIPC API) — too narrow/vertical

**Pairing decision**: Combined onboarding checklists + POPIA tooling + retention policies into one coherent "compliance" phase. Customer lifecycle state machine ties them together.

## Key Design Preferences (from founder)
1. **Portability is paramount** — engine must work for any jurisdiction without code changes. Content (checklists, retention periods) is seed data via compliance packs.
2. **First-class checklist entity** over custom-fields approach — compliance needs per-step audit trail (who verified what, when)
3. **No KYC API integration in v1** — manual verification workflow. KYC provider integration is future BYOAK phase.
4. **Full scope preferred** — lifecycle + checklists + POPIA tooling + retention policies. High-value phase.

## Phase Roadmap (updated)
- Phase 11: Tags, Custom Fields & Views (in progress)
- Phase 12: Document Templates & Generation (planned)
- Phase 13: Customer Compliance & Lifecycle (requirements written)
- Phase 14+: Candidates — Org Integrations (BYOAK), Customer Portal Frontend, Recurring Work/Retainers, Reporting & Export

## Vertical Fork Strategy (Key Conversation)
- **Legal is the endgame, but doesn't have to be first** — founder is open to sequencing strategically
- **Accounting firms = smallest gap** from generic platform (~2-3 phases after fork). Same FICA packs, projects=engagements, time/billing/profitability all match. Needs: recurring engagements, deadline calendar, Xero/QuickBooks sync.
- **Law firms = biggest gap** due to **trust accounting** (Legal Practice Act Section 86 — trust ledgers, three-way reconciliation, interest calculations, regulatory reporting). Estimated 3-5 phases of vertical-specific work just for trust accounting. Also needs: court calendar, matter type workflows, LSSA tariff rates, conflict-of-interest checking.
- **IT consultancies = small-medium gap**. Needs SLA tracking, recurring retainers with hour banks.
- **Agencies = medium gap**. Resource planning is the big missing piece.
- **Remaining generic phases before forking**: PSP/BYOAK integrations (Phase 14), Customer Portal Frontend (Phase 15), possibly Recurring Work (Phase 16). After that, diminishing returns on generic work.
- **Option C discussed**: Ship accounting fork while building law fork in parallel. Viable because accounting fork is small.

## Compliance Domain Notes
- **POPIA**: DocTeams = "operator", law firm = "responsible party". Operator needs written DPA, security safeguards, breach notification, facilitate data subject rights.
- **FICA**: Applies to law firms as "accountable institutions". CDD requirements: verify identity, address, beneficial ownership (companies), risk assessment, sanctions screening.
- **Retention**: FICA = 5 years after relationship ends. POPIA = "no longer than necessary." Both need configurable retention with admin review.
- **SA KYC market**: nCino/DocFox, LexisNexis KYC, eFICA, Sumsub — dedicated verification providers. Platform shouldn't compete with them; provide the workflow/checklist layer instead.
- **PCC 59** (Aug 2024): Beneficial ownership requirements for accountable institutions — relevant for company/trust onboarding checklists.
