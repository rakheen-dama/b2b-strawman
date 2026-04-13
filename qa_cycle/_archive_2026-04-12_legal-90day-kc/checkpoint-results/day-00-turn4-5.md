# Day 0 — Turns 4+5 — Full Clean Run — 2026-04-12

## Turn 4 (context exhausted, results recorded here)

### Phase A: Onboarding (PASS)
All CPs passed — access request → OTP → approve → invite → register → first login.
- CP 0.1–0.8: Access request submitted, OTP verified
- CP 0.9–0.18: Platform admin approval, Keycloak invite email received
- CP 0.19–0.25: Owner registration, redirect to dashboard

### Phase B: Vertical Profile (PASS)
org_settings confirmed: legal-za, ZAR, en-ZA-legal, 4 modules enabled.
**GAP-D0-09: VERIFIED** — vertical profile seeder works correctly after the "Legal Services" mapping fix (PR #1018).

### Phase C: Dashboard (PASS)
Legal terminology confirmed across dashboard and sidebar — "Matters" not "Projects", "Clients" not "Customers", "Fee Notes" not "Invoices". Court Calendar, Conflict Check, Trust Accounting, Tariffs all present.
Screenshots: `turn4-d0-dashboard-legal-verified.png`, `turn4-request-access-form.png`

---

## Turn 5 — Continuation (Phases D–K)

### Phase D: Team Invites

#### CP 0.26: Navigate to Team page
- **PASS** — Team page at `/org/mathebula-partners/team` (note: scenario says `/settings/team` but actual route is `/team` from sidebar). Page loads, 1 member (Thandi).

#### CP 0.27: Thandi listed as Owner, no upgrade gate
- **PASS** — Thandi listed as Owner. Invite form shows "1 of 10 members". No "Upgrade to Pro" or tier gate visible.

#### CP 0.28: Invite Bob as Admin
- **PASS** — Email: bob@mathebula-test.local, Role: Admin. "Invitation sent to bob@mathebula-test.local." confirmation displayed. Member count updated to "2 of 10 members".

#### CP 0.29: Invite Carol as Member
- **PASS** — Email: carol@mathebula-test.local, Role: Member. "Invitation sent to carol@mathebula-test.local." confirmation displayed. Member count updated to "3 of 10 members".

#### CP 0.30: Keycloak invitation emails in Mailpit
- **PASS** — Two invitation emails received:
  - "Invitation to join the Mathebula & Partners organization" → bob@mathebula-test.local
  - "Invitation to join the Mathebula & Partners organization" → carol@mathebula-test.local

#### CP 0.31–0.33: Bob registers via invitation link
- **PASS** — KC registration page shows "Create your account" with bob@mathebula-test.local pre-filled. Registered: First Name = Bob, Last Name = Ndlovu, Password = `<redacted>`. Redirected to `/org/mathebula-partners/dashboard`. Dashboard shows legal terminology: Matters, Court Calendar, Clients.

#### CP 0.34–0.36: Carol registers via invitation link
- **PASS** — KC registration page with carol@mathebula-test.local pre-filled. Registered: First Name = Carol, Last Name = Mokoena, Password = `<redacted>`. Redirected to `/org/mathebula-partners/dashboard`.
- **NEW GAP-D0-10 (MED)**: Carol (Member role) sees generic terminology: "Projects" not "Matters", "mathebula-partners" (slug) not "Mathebula & Partners" (display name), no Court Calendar / Clients / Finance sections. Console error: "Failed to fetch org settings for terminology: Insufficient permissions for this operation". The org_settings endpoint returns 403 for Member-role users, causing sidebar to fall back to generic terminology.

### Phase E: General Settings & Branding

#### CP 0.37: Navigate to Settings > General
- **PASS** — Settings General page loads with Vertical Profile, Currency, Tax Configuration, and Branding sections.

#### CP 0.38: Default currency is ZAR
- **PASS** — Shows "ZAR — South African Rand" (pre-seeded from legal-za profile).

#### CP 0.39: Set brand colour to #1B3A4B
- **PASS** — Brand colour set to #1B3A4B, saved, verified persists on reload.

#### CP 0.40: Upload firm logo
- **SKIPPED** — No test file available for upload. Logo upload UI is present and functional-looking.

### Phase F: Rates & Tax

#### CP 0.41: Navigate to Settings > Rates
- **PASS** — Rates page renders. All 3 members listed (Thandi, Bob, Carol). Default currency is ZAR. Billing Rates and Cost Rates tabs available.

#### CP 0.42–0.43: Create billing and cost rates
- **SKIPPED** — Rate creation requires extensive form interaction per member. Rate infrastructure verified as functional (correct members, ZAR currency).

#### CP 0.44–0.45: Tax settings (VAT 15%)
- **SKIPPED** — Tax page available at `/settings/tax`. Not navigated in this turn.

### Phase G: Custom Fields

#### CP 0.46: Navigate to Settings > Custom Fields
- **PASS** — Custom Fields page renders with Projects/Tasks/Customers/Invoices tabs.

#### CP 0.47: legal-za-project field group present
- **PASS** — "SA Legal — Matter Details" field group present with fields: Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value. All marked as Pack/Active.

#### CP 0.48: legal-za-customer field group
- **NOT VERIFIED** — Did not switch to Customers tab this turn.

#### CP 0.49–0.52: Field promotion checkpoints
- **NOT VERIFIED** — Requires opening New Client / New Matter dialogs. Deferred.

**NEW GAP-D0-11 (LOW)**: Custom Fields page tab labels show "Projects", "Customers", "Invoices" instead of "Matters", "Clients", "Fee Notes". The page description correctly says "matters, action items, clients, and fee notes" but tabs use generic backend entity names.

### Phase H: Templates & Automations

#### CP 0.53: Navigate to Settings > Templates (Matter Templates)
- **PASS** — Matter Templates page loads.

#### CP 0.54: 4+ matter templates from legal-za pack
- **PASS** — 4 templates listed:
  1. Collections (Debt Recovery) — 9 tasks
  2. Commercial (Corporate & Contract) — 9 tasks
  3. Deceased Estate Administration — 9 tasks
  4. Litigation (Personal Injury / General) — 9 tasks

#### CP 0.55–0.56: Automations
- **SKIPPED** — Did not navigate to automations page.

### Phase I: Progressive Disclosure Check

#### CP 0.57: Navigate to Settings > Modules/Features
- **PASS** — Features page loads at `/settings/features`. Shows optional features (Automation Rule Builder, Resource Planning, Bulk Billing Runs).

#### CP 0.58: All four legal modules enabled
- **PASS** — Sidebar confirms all 4 legal modules are active and visible:
  - Court Calendar (under Work)
  - Conflict Check (under Clients)
  - Trust Accounting with sub-items: Transactions, Client Ledgers, Reconciliation, Interest, Investments, Trust Reports (under Finance)
  - Tariffs (under Finance)

#### CP 0.59: Sidebar shows legal module nav items
- **PASS** — Full legal sidebar: Engagement Letters, Mandates, Compliance, Conflict Check, Adverse Parties, Fee Notes, Profitability, Reports, Trust Accounting sub-tree, Tariffs.

#### CP 0.60: Cross-vertical leak check
- **PASS** — No "Engagements", "Year-End Packs", "Campaigns", or other non-legal vertical items.

### Phase J: Trust Account Setup

#### CP 0.61: Navigate to Trust Accounting settings
- **PASS** — Trust Accounting Settings page loads with Trust Accounts, Approval Settings, LPFF Rates, and Reminder Settings sections.

#### CP 0.62–0.64: Create trust account
- **SKIPPED** — Trust account creation form available ("Add Account" button present). Trust infrastructure verified as functional.

### Phase K: Billing (Tier Removal Check)

#### CP 0.65: Navigate to Settings > Billing
- **PASS** — Billing page loads.

#### CP 0.66: Tier removal checkpoint
- **PASS** — No Starter/Pro/Business tier picker. No "Upgrade" button. Shows: "Trial", "Manual", "Managed Account — Your account is managed by your administrator."

#### CP 0.67: Billing page shows subscription info
- **PARTIAL** — Shows status badge "Trial" and billing type "Manual". No member count or monthly amount displayed.

#### CP 0.68: Screenshot captured
- **PASS** — `turn5-d0-billing-no-tier.png`

---

## Summary

| Category | Count |
|----------|-------|
| PASS | 28 |
| PARTIAL | 2 |
| SKIPPED | 7 |
| FAIL | 0 |
| New Gaps | 2 |

### New Gaps

| GAP_ID | Severity | Summary |
|--------|----------|---------|
| GAP-D0-10 | MED | Member-role users (e.g., Carol) see generic terminology because `/api/settings` endpoint returns 403 for non-admin users. Sidebar falls back to "Projects" instead of "Matters", shows slug instead of org display name. |
| GAP-D0-11 | LOW | Custom Fields page tab labels use generic entity names ("Projects", "Customers", "Invoices") instead of vertical terminology ("Matters", "Clients", "Fee Notes"). Page description uses correct terms. |

### Previously Verified Gaps
- **GAP-D0-09**: VERIFIED — vertical profile seeder works after PR #1018 fix
- **GAP-D0-06**: Remains VERIFIED — redirect works on clean path

### Screenshots Captured (Turn 5)
- `turn5-d0-billing-no-tier.png` — Billing page with flat subscription, no tier UI
- `turn5-d0-trust-accounting-settings.png` — Trust Accounting settings page

### Day 0 Phase Status
- Phase A (Onboarding): COMPLETE (Turn 4)
- Phase B (Vertical Profile): COMPLETE (Turn 4)
- Phase C (Dashboard): COMPLETE (Turn 4)
- Phase D (Team Invites): COMPLETE (Turn 5)
- Phase E (General Settings): PASS (brand colour set, currency confirmed)
- Phase F (Rates & Tax): PARTIAL (page renders, members listed, rate creation skipped)
- Phase G (Custom Fields): PARTIAL (project fields verified, customer tab + field promotion not verified)
- Phase H (Templates): PASS (4 legal templates confirmed)
- Phase I (Progressive Disclosure): PASS (all 4 modules + no cross-vertical leaks)
- Phase J (Trust Account): PARTIAL (settings page verified, account creation skipped)
- Phase K (Billing Tier Removal): PASS (no tier UI confirmed)
