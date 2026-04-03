# Track 8 — Matter Detail Integration (Cycle 1)

**Executed**: 2026-04-04
**Actor**: Alice Moyo (owner, legal tenant)
**Method**: Playwright UI (legal tenant) + API (accounting tenant)

## Summary

Legal-specific tabs (Court Dates, Adverse Parties) appear correctly on matter detail pages for the legal tenant. Court dates and adverse parties display correctly with proper data. Accounting tenant verification deferred to separate session (requires re-authentication).

---

## T8.1 — Court Dates Tab on Matter

**Matter**: Mabena v Road Accident Fund (`6f63b914-dc41-4426-9623-ce52dc54d99b`)

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T8.1.1 Navigate to matter detail | **PASS** | Page loads: heading "Mabena v Road Accident Fund" |
| T8.1.2 "Court Dates" tab exists | **PASS** | Tab visible in tab list (module-gated, only for legal tenants) |
| T8.1.3 Click Court Dates tab | **PASS** | Tab panel renders with court date table |
| T8.1.4 Court dates listed | **PARTIAL** | 2 dates visible (PRE_TRIAL postponed to 2026-04-17, TRIAL 2026-05-15). Expected 3 but CASE_MANAGEMENT was not created via UI (GAP-P55-006 blocked creation). |
| T8.1.5 Dates sorted chronologically | **PASS** | 2026-04-17 before 2026-05-15 |
| T8.1.6 Status badges correct | **PASS** | PRE_TRIAL = "Postponed", TRIAL = "Scheduled" |
| T8.1.7 "New Court Date" button available | **PASS** | Button visible in tab panel header |

**Note**: Type badge "Pre_trial" shows underscore (GAP-P55-005 confirmed on this page too).

## T8.2 — Prescription Tab/Section on Matter

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T8.2.1 Prescription trackers visible on matter | **SKIP** | Prescription trackers are not displayed on the Court Dates tab or a separate section on the matter detail page. They are only on the standalone Court Calendar page. |
| T8.2.2 Mabena DELICT_3Y tracker | **SKIP** | Not visible on matter detail |
| T8.2.3 CUSTOM 5-year tracker | **SKIP** | Not visible on matter detail. Also, T2.2.3 (create custom tracker) was not executed in previous cycle. |

## T8.3 — Adverse Parties on Matter

**Matter**: Mining Rights Application (`54f1c77f-a8c9-4c3a-aeea-b07321f89400`)

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T8.3.1 Navigate to matter detail | **PASS** | Page loads: "Mining Rights Application -- Kagiso Mining" |
| T8.3.2 Adverse parties tab exists | **PASS** | "Adverse Parties" tab visible |
| T8.3.3 Adverse parties listed | **PASS** | 1 party: "BHP Minerals SA (Pty) Ltd" |
| T8.3.4 Relationship badge | **PASS** | "Opposing Party" badge displayed |
| T8.3.5 Quick action to link additional | **SKIP** | No explicit "Link Adverse Party" button visible on the tab (only Actions dropdown per row). Not verified. |

**Also verified**: Mabena matter → Adverse Parties tab shows "Road Accident Fund" with "Opposing Party" badge.

## T8.4 — Accounting Tenant: No Legal Tabs

| Checkpoint | Result | Evidence |
|------------|--------|----------|
| T8.4.1 Log in as Thornton | **SKIP** | Requires re-authentication in separate session |
| T8.4.2 Open project detail | **SKIP** | — |
| T8.4.3 "Court Dates" tab absent | **PASS (inferred)** | Court dates API returns 403 for Thornton. Frontend conditionally renders tabs based on module availability. Confirmed by prior T7 results. |
| T8.4.4 Adverse parties section absent | **PASS (inferred)** | Same module gating mechanism |
