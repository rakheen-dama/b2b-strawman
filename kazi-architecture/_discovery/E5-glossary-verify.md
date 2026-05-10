# E5 — Glossary Cross-Consistency Verification

**Date:** 2026-05-10
**File verified:** `glossary.md` (343 lines, 259 terms per header metadata)

## 1. Headline domain term coverage

**Sampled terms:** 61 (33 from the prompt's seed list + 28 additional terms drawn from `30-modules/*.md` and `20-cross-cutting/*.md`)

**Found in glossary:** 61
**Missing from glossary:** 0

The full prompt-supplied seed (Customer, Project, Matter, Invoice, Fee Note, TrustAccount, AcceptanceRequest, ProposalStatus, ChecklistInstance, RequestTemplate, Member, OrgRole, Capability, AutomationRule, DomainEvent, ScopedValue, Tenant, MagicLink, PortalContact, Section 86, LPFF, TariffSchedule, AdverseParty, ConflictCheck, CourtDate, Disbursement, Engagement Letter, Time Log, OrgSettings, FieldDefinition, FieldGroup, Tag, SavedView) all resolve. PascalCase forms (e.g. `AcceptanceRequest`) match the glossary's space-separated convention (`Acceptance Request`).

Additional terms cross-checked from module pages (all found):
- `Engagement Letter`, `Engagement`, `Action Item`, `Mandate`, `Fee Schedule`, `Audit Trail`, `Hour Bank`, `Trust Boundary Guard`, `Idempotent Push`, `Xero Adapter`, `Information Request`, `Activity Feed`, `Visibility`, `DSAR`, `FICA`, `PAIA`, `Portal Session Context`, `Module`, `Vertical Profile`, `Pack`, `Compliance Pack`, `Field Pack`, `Comment`, `Tax Rate`, `Recurrence Rule`, `Setup Status`, `Prerequisite`, `Trust Investment`, `Reconciliation`, `Bank Statement`, `Interest Run`, `Statement of Account`, `Closure Gate`, `Matter Closure`, `Prescription Tracker`, `Tariff Item`, `LSSA Tariff`, `AI Specialist`, `AI Queue`, `AI Invocation`, `Invocation Status`.

`Time Recording` is not a headline glossary entry but is correctly handled as a vertical UI label inside the `Time Entry` row's notes and in the Watch-words table — that is the canonical pattern, not a gap.

**Stubs added:** 0 (none required)

## 2. Code-anchor file existence

**Sample size:** 23 anchored paths (mix of backend Java entities, frontend TS files, ADRs, and gateway config)

**Resolved (file exists):** 23
**Stale (file missing):** 0

Sampled paths included:
- `backend/.../acceptance/AcceptanceRequest.java`
- `backend/.../acceptance/AcceptanceStatus.java`
- `backend/.../accessrequest/AccessRequest.java`
- `frontend/lib/terminology-map.ts`
- `backend/.../automation/ActionType.java`, `automation/AutomationRule.java`
- `backend/.../audit/AuditEvent.java`
- `backend/.../orgrole/Capability.java`, `orgrole/OrgRole.java`
- `backend/.../customer/Customer.java`
- `backend/.../event/DomainEvent.java`
- `backend/.../member/Member.java`
- `backend/.../portal/PortalContact.java`, `portal/MagicLinkToken.java`
- `backend/.../proposal/Proposal.java`
- `backend/.../multitenancy/RequestScopes.java`
- `backend/.../verticals/legal/trustaccounting/TrustAccountType.java`
- `backend/.../verticals/legal/courtcalendar/CourtDate.java`
- `backend/.../fielddefinition/FieldDefinition.java`
- `backend/.../view/SavedView.java`
- `backend/.../verticals/legal/conflictcheck/AdverseParty.java`, `conflictcheck/ConflictCheck.java`
- `adr/ADR-272-xero-only-accounting-adapter-v1.md`

All resolved cleanly. No `_anchor stale` markers needed.

## 3. Watch-words canonical-target gaps

**Watch-words rows:** 24
**Canonical terms checked:** 31 distinct (some rows have multiple canonical targets; e.g. "Calendar event → Court Date OR Deadline OR Schedule")

All 31 canonical terms (Customer, Audit Event, Activity Feed, AI Assistant, Court Date, Deadline, Schedule (Project), Portal, Organization, Project, Engagement, Proposal, Expense, Disbursement, Invoice, Lifecycle Status, Retainer, Capability, Org Role, Project Role, Portal Contact, Billing Rate, Access Request, Subscription, Tariff Schedule, Tenant, Tiptap, Time Entry, Member, Platform Admin, Automation Rule) have entries in the main glossary table.

**Stubs added for missing canonical targets:** 0

The "Plan / Tier / Pro / Starter → (none)" row is intentional (Kazi has no plan tiers) and does not point at a missing entry.

## 4. Known-divergences cross-references

**Divergences listed:** 8 (#1–#8)
**Inline cross-references in main table:** 6 (to #1, #3, #4, #5, #6, #7)

All 6 inline `divergence #N` references resolve to a numbered entry in the Known divergences section. #2 (three `BillingStatus` / `PaymentStatus` enums) and #8 (`Workflow` no anchor) stand alone without inline back-pointers — that is acceptable since #2 is implicitly referenced by every BillingStatus/PaymentStatus row's "watch" note and #8 is mirrored by the `Workflow` (gap) glossary row plus the `Workflow → Automation Rule` watch-word.

## 5. Net change to glossary.md

**Lines changed:** 0
**Stubs added:** 0
**Anchors marked stale:** 0
**Watch-word target stubs added:** 0

The glossary passes E5 verification cleanly. It is internally consistent: every sampled module-page term has a glossary entry, every sampled code anchor resolves, every watch-word canonical target exists, and every numbered divergence reference points at a real divergence.
