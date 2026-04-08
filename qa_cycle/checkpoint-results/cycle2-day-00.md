# Cycle 2 — Day 0 Checkpoint Results

**Date**: 2026-04-06
**Actor**: Alice (Owner)
**Stack**: E2E mock-auth (localhost:3001 / localhost:8081)
**Profile**: legal-za (pre-provisioned)

## Results

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 0.1 | Login as Alice | PASS | Landed on `/org/e2e-test-org/dashboard` |
| 0.2 | Verify legal-za profile active | PARTIAL | Sidebar links show "Matters", "Clients", "Fee Notes", "Engagement Letters", "Mandates". BUT group header still says "Projects" (GAP-D0-04). Dashboard KPI cards say "Active Projects" / "Project Health" (GAP-D0-05). |
| 0.3 | Sidebar includes Trust Accounting, Court Calendar, Conflict Check | PASS | Court Calendar under Work. Trust Accounting + sub-items (Transactions, Client Ledgers, Reconciliation, Interest, Investments, Trust Reports) under Finance. Conflict Check + Adverse Parties under Clients. |
| 0.4 | Navigate to Settings > General | PASS | `/org/e2e-test-org/settings/general` loaded. Vertical Profile shows "Legal (South Africa)" (Apply Profile disabled). |
| 0.5 | Verify ZAR currency | PASS | "ZAR -- South African Rand" pre-set by legal-za profile. Persists on reload. |
| 0.6 | Set brand colour #1B3A4B | PASS | Changed from #000000 to #1B3A4B, saved, reloaded -- persists. |
| 0.7 | Navigate to Settings > Rates | PASS | `/org/e2e-test-org/settings/rates` loaded. |
| 0.8 | Create Alice billing rate R2,500/hr | PASS | R 2,500.00 ZAR, effective Apr 6, 2026, Ongoing. |
| 0.9 | Create Bob billing rate R1,200/hr | PASS | R 1,200.00 ZAR, effective Apr 6, 2026, Ongoing. Note: Bob had to login first to trigger member sync. |
| 0.10 | Create Carol billing rate R550/hr | PASS | R 550.00 ZAR, effective Apr 6, 2026, Ongoing. Carol logged in first for member sync. |
| 0.11 | Create Alice cost rate R1,000/hr | PASS | R 1,000.00 ZAR on Cost Rates tab. |
| 0.12 | Create Bob cost rate R500/hr | PASS | R 500.00 ZAR on Cost Rates tab. |
| 0.13 | Create Carol cost rate R200/hr | PASS | R 200.00 ZAR on Cost Rates tab. |
| 0.14 | Navigate to Settings > Tax | PASS | Tax settings page loaded with pre-configured rates. |
| 0.15 | Verify VAT 15% rate | PASS | "Standard" rate at 15.00% exists (Default, Active). Also "Zero-rated" (0%) and "Exempt" (0%) present. Pre-seeded by legal-za profile. |
| 0.16 | Navigate to Settings > Team | PARTIAL | 3 members listed (alice, bob, carol). BUT: names show as "Unknown" (not Alice Owner, etc.), Role column is empty for all (GAP-D0-06), Joined shows "--". |
| 0.17 | Verify legal custom fields | PASS | Projects tab: 11 legal fields (Matter Type, Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value, etc.). Field groups: "SA Legal -- Matter Details", "Project Info". Customers tab: 20+ fields including Client Type (Required), ID/Passport Number, Registration Number, Physical Address, Risk Rating, etc. Field groups: "SA Legal -- Client Details", "FICA Compliance", "Company FICA Details", "Contact & Address". |
| 0.18 | Verify 4 matter templates | PASS | All 4 present on Project Templates page: (1) Collections (Debt Recovery) - 9 tasks, (2) Commercial (Corporate & Contract) - 9 tasks, (3) Deceased Estate Administration - 9 tasks, (4) Litigation (Personal Injury / General) - 9 tasks. **GAP-D0-01 VERIFIED FIXED.** |
| 0.19 | Navigate to Trust Accounts | FAIL | Trust Accounting page loads showing "No Trust Accounts" but NO "Create Trust Account" button exists. Known GAP-D0-02 (WONT_FIX). |
| 0.20 | Create trust account | FAIL | Cannot create via UI (GAP-D0-02). API attempt returns 403 Insufficient permissions. Workaround blocked. |
| 0.21 | Set LPFF rate 6.5% | FAIL | Depends on 0.20. |
| 0.22 | Verify legal modules enabled | PARTIAL | No Settings > Modules page exists (GAP-D0-03 WONT_FIX). However, all 4 modules confirmed working via sidebar presence: Court Calendar, Conflict Check, Trust Accounting, Tariffs. |
| 0.23 | Screenshot | SKIP | Non-blocking, screenshot convention step. |

## Day 0 Checkpoint Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| Currency shows ZAR on settings page | PASS | Pre-set by legal-za profile |
| 3 billing rates + 3 cost rates visible | PASS | All 6 rates created successfully |
| VAT 15% visible on tax page | PASS | "Standard" 15% (pre-seeded) |
| Legal custom fields exist for CLIENT and MATTER | PASS | Extensive legal field packs loaded |
| 4 matter templates present | PASS | All 4 with 9 tasks each. GAP-D0-01 VERIFIED FIXED. |
| Trust account created | FAIL | GAP-D0-02 (WONT_FIX) -- no create UI, API workaround fails with 403 |
| All 4 legal modules enabled | PARTIAL | Confirmed via sidebar presence; no modules settings page (GAP-D0-03) |
| Terminology uses legal terms | PARTIAL | Links correct (Matters, Clients, Fee Notes). Group headers wrong (GAP-D0-04). Dashboard KPIs wrong (GAP-D0-05). |

## Gap Verification

| ID | Cycle 1 Status | Cycle 2 Result | Notes |
|----|---------------|----------------|-------|
| GAP-D0-01 | FIXED (PR #971) | **VERIFIED** | 4 legal matter templates present from initial provisioning |
| GAP-D0-02 | WONT_FIX | **CONFIRMED** | Still no Create Trust Account dialog. API workaround fails with 403. |
| GAP-D0-03 | WONT_FIX | **CONFIRMED** | No Settings > Modules page |
| GAP-D0-04 | SPEC_READY | **CONFIRMED** | "Projects" group header still not renamed |
| GAP-D0-05 | SPEC_READY | **CONFIRMED** | Dashboard KPI cards still say "Active Projects" / "Project Health" |
| GAP-D0-06 | SPEC_READY | **CONFIRMED** | Team Role column empty, member names show "Unknown" |
| GAP-D0-07 | FIXED (e7a13e67) | **VERIFIED** | legal-za profile active from start, no manual switch needed |

## New Issues

| ID | Summary | Severity | Notes |
|----|---------|----------|-------|
| GAP-D0-08 | Team member names display as "Unknown" instead of real names (Alice Owner, etc.) | LOW | Mock-auth member sync does not populate display names. Email addresses are correct. |
| GAP-D0-09 | Trust account API returns 403 for Owner role -- cannot create via API workaround | MEDIUM | Blocks trust account creation entirely in E2E stack. May be role mapping issue in mock-auth. |

## Conclusion

Day 0 is **COMPLETE with known gaps**. The trust account creation failure (GAP-D0-02 + GAP-D0-09) is non-cascading for Day 1 (client onboarding, conflict check, matter creation do not depend on trust accounts). Proceeding to Day 1.
