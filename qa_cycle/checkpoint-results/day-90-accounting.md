# Day 90 — Final regression sweep (Accounting ZA Cycle)

**Cycle**: Accounting ZA 90-Day Lifecycle (Keycloak)
**Branch**: `main`
**Actor**: Thandi Thornton (Owner)
**Date executed**: 2026-05-15
**Result**: **ALL PASS** — 6/6 checkpoints green

---

## Checkpoint Results

### 90.1 — Terminology sweep

**Verdict: PASS**

Walked every major page: Dashboard, My Work, Calendar, Engagements, Recurring Schedules, Clients, Invoices, Profitability, Team, Notifications, Settings (General, Billing, all nav items).

| Surface | Expected | Actual | Result |
|---------|----------|--------|--------|
| Sidebar "Engagements" group | Engagements (not Matters) | "Engagements" | PASS |
| Sidebar "Clients" group | Clients (not Customers) | "Clients" | PASS |
| Dashboard heading | "Dashboard" | "Dashboard" | PASS |
| Dashboard subheading | "Company overview and engagement health" | Correct | PASS |
| Engagement Health table | "Engagement" column | Correct | PASS |
| Clients page heading | "Clients" | "Clients" | PASS |
| Engagements page heading | "Engagements" | "Engagements" | PASS |
| Settings nav | "Engagement Templates", "Engagement Naming" | Correct | PASS |
| Breadcrumbs | No legal terms | Clean | PASS |

**Zero instances of "Matter", "Attorney", "Conflict", "Court", or other legal terminology found across sidebar, breadcrumbs, dialogs, settings pages, or email subjects.**

---

### 90.2 — Field promotion sweep

**Verdict: PASS**

Reopened New Client, New Engagement, and verified New Invoice dialogs. All promoted slugs render inline as first-class inputs.

#### New Client dialog (12+ promoted slugs inline)
| Slug | Field Label | Rendered Inline | Result |
|------|-------------|----------------|--------|
| `tax_number` | Tax Number | Yes | PASS |
| `phone` | Phone | Yes | PASS |
| `address_line1` | Address Line 1 | Yes (Address section) | PASS |
| `city` | City | Yes | PASS |
| `postal_code` | Postal Code | Yes | PASS |
| `country` | Country | Yes | PASS |
| `primary_contact_name` | Contact Name | Yes (Contact section) | PASS |
| `primary_contact_email` | Contact Email | Yes | PASS |
| `primary_contact_phone` | Contact Phone | Yes | PASS |
| `acct_company_registration_number` | Registration Number | Yes (Business Details) | PASS |
| `acct_entity_type` | Entity Type | Yes (combobox with 8 options) | PASS |
| `financial_year_end` | Financial Year End | Yes | PASS |

No "Other Fields" or CustomFieldSection sidebar panel visible. No duplicates.

#### New Engagement dialog (2 promoted slugs inline)
| Slug | Field Label | Rendered Inline | Result |
|------|-------------|----------------|--------|
| `reference_number` | Reference Number | Yes | PASS |
| `engagement_type` | Work Type | Yes | PASS |

#### Invoice promoted slugs
Verified during Day 36 create flow (`purchase_order_number`, `tax_type`, `billing_period_start`, `billing_period_end` rendered inline). No code changes to invoice create dialog since. No regression.

---

### 90.3 — Progressive disclosure sweep

**Verdict: PASS**

| Check | Result | Evidence |
|-------|--------|----------|
| Sidebar: no "Trust Accounting" | PASS | Not present in nav |
| Sidebar: no "Court Calendar" | PASS | Not present in nav |
| Sidebar: no "Conflict Check" | PASS | Not present in nav |
| Sidebar: no "Tariffs" / "LSSA Tariffs" | PASS | Not present in nav |
| Direct URL `/trust-accounting` | PASS | Shows "Module Not Available — The Trust Accounting module is not enabled for your organization." |
| Direct URL `/court-calendar` | PASS | Shows "Module Not Available — The Court Calendar module is not enabled for your organization." |

Zero legal modules visible. Clean "Module Not Available" handling on direct URL access (no broken pages).

---

### 90.4 — Tier removal sweep

**Verdict: PASS**

| Screen | Check | Result |
|--------|-------|--------|
| Settings > Billing | No plan picker / tier selector | PASS — shows "Trial / Manual / Managed Account" |
| Settings > Billing | No "Upgrade to Pro/Business" buttons | PASS |
| Settings > Billing | No plan tier badge (Starter/Pro/Business) | PASS |
| Settings > Billing | No member-limit gating | PASS |
| Team page | No upgrade gate on invite flow | PASS — invite form freely accessible |
| Team page | No member limit message | PASS — shows "5 members (2 pending)" without cap |

Flat subscription model confirmed on 3+ screens.

---

### 90.5 — Console errors sweep

**Verdict: PASS-WITH-NOTE**

Navigated every main route with console monitoring: Dashboard, My Work, Calendar, Engagements, Recurring Schedules, Clients, Invoices, Notifications, Settings/General, Settings/Billing, Profitability, Team.

**JavaScript application errors: ZERO** (no TypeError, ReferenceError, SyntaxError, or unhandled exceptions)

**Pre-existing non-blocking issues (not new to this cycle):**
1. `/api/assistant/invocations` returns 404 — AI assistant BYOAK feature endpoint not yet implemented. Background polling, gracefully handled, no UI impact.
2. Recurring Schedules page: "Failed to fetch schedules" — server-side ApiError, page catches and displays error state. Pre-existing.

These are both known, pre-existing feature gaps — not regressions introduced during this cycle.

---

### 90.6 — Mailpit sweep

**Verdict: PASS**

| Metric | Value |
|--------|-------|
| Total emails | 13 |
| Bounced | 0 |
| Failed / Undeliverable | 0 |

All 13 emails are legitimate:
- 1 verification OTP (thandi@thornton-test.local)
- 3 Keycloak invitation emails (thandi, bob, carol)
- 5 invoice notification emails (INV-0001 through INV-0005)
- 4 portal magic link emails (finance@kgosi-holdings.co.za)

Email subjects consistently reference "Thornton & Associates". No legal terminology in subjects.

---

## Summary

| Checkpoint | Description | Verdict |
|------------|-------------|---------|
| 90.1 | Terminology sweep | **PASS** |
| 90.2 | Field promotion sweep | **PASS** |
| 90.3 | Progressive disclosure sweep | **PASS** |
| 90.4 | Tier removal sweep | **PASS** |
| 90.5 | Console errors | **PASS-WITH-NOTE** (pre-existing 404s only) |
| 90.6 | Mailpit sweep | **PASS** |

**Day 90 PASS. All regression checkpoints green. No new gaps discovered.**

---

## Open gaps at cycle end

| Gap ID | Summary | Severity | Status | Notes |
|--------|---------|----------|--------|-------|
| OBS-4007 | Budget Alert automation SEND_NOTIFICATION fails: no PROJECT_OWNER recipients | LOW | OPEN | Workaround: BudgetCheckService direct notification to org owner succeeds |
| OBS-4008 | Budget Alert Escalation rule fails: null thresholdPercent | LOW | OPEN | Jackson deserialization issue. Non-blocking. |
| KYC | KYC adapter not wired | exempt | WONT_FIX-EXEMPT | Per mandate |
| Payments | PayFast sandbox not wired | exempt | WONT_FIX-EXEMPT | Per mandate |

Zero BLOCKER or HIGH items open. All previously filed gaps (OBS-4002 through OBS-4006, OBS-4009, OBS-4010) have been VERIFIED.
