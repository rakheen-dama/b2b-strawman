# Day 0 — Firm Setup (Legal Lifecycle QA)

**Date**: 2026-04-06
**Actor**: Alice (Owner)
**Cycle**: 1 (bugfix_cycle_2026-04-06)

## Pre-condition

E2E seed data had the **South African Accounting Firm** profile active, not the legal-za profile. The QA agent applied the **Legal (South Africa)** profile via Settings > General > Vertical Profile > Apply Profile. This is a seed issue — the test plan assumes legal-za is pre-applied.

## Checkpoint Results

| ID | Step | Result | Evidence / Notes |
|----|------|--------|-----------------|
| 0.1 | Login as Alice, lands on dashboard | PASS | URL: `/org/e2e-test-org/dashboard`, user: Alice Owner |
| 0.2 | Legal-za profile active — sidebar shows "Matters" not "Projects" | PASS (after fix) | Initially FAIL — seed had accounting profile. After applying Legal (South Africa), sidebar shows "Matters" under Projects section. Note: group header still says "Projects" not "Matters" (minor). |
| 0.3 | Sidebar includes Trust Accounting, Court Calendar, Conflict Check | PASS (after fix) | After legal profile applied: Court Calendar under Work, Conflict Check + Adverse Parties under Clients, Trust Accounting + Tariffs under Finance. |
| 0.4 | Navigate to Settings > General | PASS | URL: `/org/e2e-test-org/settings/general` |
| 0.5 | Default currency set to ZAR | PASS | Already set to "ZAR — South African Rand" in seed data. Persists on reload. |
| 0.6 | Brand colour set to #1B3A4B | PASS | Set via Brand Color input, saved, verified after page reload. Color chip shows #1b3a4b. |
| 0.7 | Navigate to Settings > Rates | PASS | URL: `/org/e2e-test-org/settings/rates` |
| 0.8 | Alice billing rate R2,500/hr | PASS | Created via Add Rate dialog. Shows "R 2 500,00" in ZAR. |
| 0.9 | Bob billing rate R1,200/hr | PASS | Shows "R 1 200,00" in ZAR. |
| 0.10 | Carol billing rate R550/hr | PASS | Shows "R 550,00" in ZAR. |
| 0.11 | Alice cost rate R1,000/hr | PASS | Cost Rates tab, shows "R 1 000,00". |
| 0.12 | Bob cost rate R500/hr | PASS | Shows "R 500,00". |
| 0.13 | Carol cost rate R200/hr | PASS | Shows "R 200,00". |
| 0.14 | Navigate to Settings > Tax | PASS | URL: `/org/e2e-test-org/settings/tax` |
| 0.15 | VAT 15% tax rate | PASS | Seed data has "Standard" at 15.00% (Default, Active). Also Zero-rated (0%) and Exempt (0%). Name is "Standard" not "VAT" but rate is correct. |
| 0.16 | Settings > Team — Alice, Bob, Carol listed | PASS | 3 members listed: Alice Owner, Bob Admin, Carol Member. Role column appears empty for all three (minor display gap). |
| 0.17 | Settings > Custom Fields — legal field packs | PASS | Customers tab shows legal-relevant fields: SA ID Number, Trust Registration Number, Entity Type (Legal), Trust Type, Trust Deed Date, Names of Trustees, Letters of Authority Date, FICA Verified, FICA Verification Date, Company Registration Number, Passport Number, Risk Rating, etc. Both accounting and legal packs loaded (additive). |
| 0.18 | Settings > Templates — 4 matter templates | **FAIL** | Settings > Templates shows document templates (engagement letters, invoices, compliance). Settings > Project Templates shows "No project templates yet." The 4 legal matter templates (Litigation, Deceased Estate Administration, Collections, Commercial) are NOT present. **GAP-D0-01** |
| 0.19 | Settings > Trust Accounts page | **FAIL** | Trust Accounting page (`/trust-accounting`) exists but shows "Coming Soon" stub. Cannot create a trust account. **GAP-D0-02** |
| 0.20 | Create trust account | **FAIL** | Blocked by 0.19 — no trust account creation UI. **GAP-D0-02** |
| 0.21 | Set LPFF rate 6.5% | **FAIL** | Blocked by 0.19 — no trust account config. **GAP-D0-02** |
| 0.22 | Settings > Modules — 4 legal modules enabled | **PARTIAL** | No explicit "Modules" settings page exists. However, the legal modules are implicitly enabled — sidebar shows Court Calendar, Conflict Check, Trust Accounting, and Tariffs links. **GAP-D0-03** |
| 0.23 | Screenshot: Dashboard with legal nav | SKIPPED | Deferred — not blocking. |

## Day 0 Checkpoint Summary

| Checkpoint | Result |
|-----------|--------|
| Currency shows ZAR | PASS |
| 3 billing + 3 cost rates visible | PASS |
| VAT 15% visible on tax page | PASS |
| Legal custom fields for CLIENT and MATTER | PASS (CLIENT fields present; MATTER fields on Projects tab are accounting-oriented, but legal Entity Type fields exist) |
| 4 matter templates present | **FAIL** — 0 matter templates seeded (GAP-D0-01) |
| Trust account created | **FAIL** — Coming Soon stub (GAP-D0-02) |
| All 4 legal modules enabled | PARTIAL — sidebar links present but no Modules settings page (GAP-D0-03) |
| Terminology: sidebar/headings/breadcrumbs use legal terms | PARTIAL — "Matters" in sidebar, "Court Calendar", "Engagement Letters", "Conflict Check" all correct. But group header still says "Projects" not "Matters", dashboard cards say "Active Projects" not "Active Matters", "Project Health" not "Matter Health". |

## Gaps Identified

| Gap ID | Summary | Severity | Blocker? |
|--------|---------|----------|----------|
| GAP-D0-01 | No legal matter templates (Litigation, Deceased Estate Admin, Collections, Commercial) seeded by legal-za profile | HIGH | YES — blocks Day 1 steps 1.16-1.20 (create matter from template) |
| GAP-D0-02 | Trust Accounting is "Coming Soon" stub — cannot create trust accounts, set LPFF rate | HIGH | YES — blocks Day 14, 30, 45, 60, 90 trust-related steps |
| GAP-D0-03 | No Settings > Modules page to verify/toggle legal modules | LOW | No — modules appear enabled via sidebar |
| GAP-D0-04 | "Projects" group header not renamed to "Matters" in sidebar | LOW | No — cosmetic |
| GAP-D0-05 | Dashboard cards say "Active Projects", "Project Health" instead of "Active Matters", "Matter Health" | LOW | No — cosmetic |
| GAP-D0-06 | Team page Role column empty for all members | LOW | No — cosmetic |
| GAP-D0-07 | E2E seed does not pre-apply legal-za profile — requires manual profile switch | MEDIUM | No — workaround applied during QA |

## Console Errors

None observed throughout Day 0.

## Decision

GAP-D0-01 (no matter templates) does NOT block Day 1 steps 1.1-1.15 (client creation and onboarding). Proceeding to Day 1 to test conflict check, client creation, and FICA flow. Will stop at step 1.16 if matter templates are still missing.
