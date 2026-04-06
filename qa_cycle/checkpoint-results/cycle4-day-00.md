# Cycle 4 — Day 0: Firm Setup

**Date**: 2026-04-06
**Actor**: Alice (Owner)
**Build**: Branch `bugfix_cycle_2026-04-06` (includes PRs #970-975)
**E2E Stack**: Rebuilt fresh with `VERTICAL_PROFILE=legal-za`

## Checkpoint Results

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 0.1 | Login as Alice, lands on dashboard | PASS | URL: `/org/e2e-test-org/dashboard`. User shows "Alice Owner". |
| 0.2 | Sidebar shows "Matters" not "Projects", "Clients" not "Customers" | PARTIAL | "Matters" link present, "Clients" section present, "Fee Notes" (not "Invoices"), "Engagement Letters" (not "Proposals"), "Mandates" (not "Retainers"). BUT group header still says "Projects" (known GAP-D0-04). Dashboard KPI cards say "Active Projects" / "Project Health" (known GAP-D0-05). |
| 0.3 | Sidebar includes Trust Accounting, Court Calendar, Conflict Check | PASS | Court Calendar under Work. Trust Accounting under Finance (with sub-items: Transactions, Client Ledgers, Reconciliation, Interest, Investments, Trust Reports). Conflict Check under Clients. Adverse Parties and Tariffs also present. |
| 0.4 | Navigate to Settings > General | PASS | URL: `/org/e2e-test-org/settings/general`. |
| 0.5 | Default currency ZAR, persists on reload | PASS | "ZAR -- South African Rand" pre-seeded. Persists after page reload. |
| 0.6 | Brand colour #1B3A4B, persists on reload | PASS | Set to #1B3A4B, saved. After reload: textbox shows "#1B3A4B", colour picker shows "#1b3a4b". |
| 0.7 | Navigate to Settings > Rates | PASS | URL: `/org/e2e-test-org/settings/rates`. Currency defaults to ZAR. |
| 0.8 | Create billing rate Alice R2,500/hr | PASS | "R 2 500,00 ZAR" shown. Effective from Apr 6, 2026. Ongoing. |
| 0.9 | Create billing rate Bob R1,200/hr | PASS | "R 1 200,00 ZAR" shown. |
| 0.10 | Create billing rate Carol R550/hr | PASS | "R 550,00 ZAR" shown. |
| 0.11 | Create cost rate Alice R1,000/hr | PASS | "R 1 000,00 ZAR" shown on Cost Rates tab. |
| 0.12 | Create cost rate Bob R500/hr | PASS | "R 500,00 ZAR" shown. |
| 0.13 | Create cost rate Carol R200/hr | PASS | "R 200,00 ZAR" shown. |
| 0.14 | Navigate to Settings > Tax | PASS | URL: `/org/e2e-test-org/settings/tax`. |
| 0.15 | VAT 15% pre-seeded | PASS | "Standard" rate at 15.00%, Default, Active. Also: Zero-rated (0%), Exempt (0%) both Active. |
| 0.16 | Team page: Alice (Owner), Bob (Admin), Carol (Member) | PASS | 3 members listed. Names display correctly: "Alice Owner", "Bob Admin", "Carol Member". GAP-D0-08 VERIFIED FIXED. Role column empty for all three (known GAP-D0-06 LOW). |
| 0.17 | Custom fields loaded for MATTER and CLIENT | PASS | Projects tab: 11 fields (Matter Type, Reference Number, Case Number, Priority, Category, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value). Field groups: "SA Legal -- Matter Details", "Project Info". Previous cycle confirmed 20 CLIENT fields. |
| 0.18 | 4 matter templates listed with 9 tasks each | PASS | Collections (Debt Recovery) 9 tasks, Commercial (Corporate & Contract) 9 tasks, Deceased Estate Administration 9 tasks, Litigation (Personal Injury / General) 9 tasks. All Active. |
| 0.19 | Trust account settings page | SKIP | WONT_FIX (GAP-D0-02). No "Create Trust Account" dialog. |
| 0.20 | Create trust account via UI | SKIP | WONT_FIX (GAP-D0-02). |
| 0.21 | Set LPFF rate 6.5% | SKIP | Dependent on trust account creation. |
| 0.22 | Settings > Modules verification | SKIP | WONT_FIX (GAP-D0-03). Modules work correctly via profile system. |
| 0.23 | Screenshot: Dashboard with legal nav | SKIP | Screenshots deferred to full pass. |

## Day 0 Checkpoint Summary

| Checkpoint | Result |
|-----------|--------|
| Currency shows ZAR on settings page | PASS |
| 3 billing rates + 3 cost rates visible | PASS |
| VAT 15% visible on tax page | PASS |
| Legal custom fields exist for CLIENT and MATTER | PASS |
| 4 matter templates present (9 tasks each) | PASS |
| Trust account created | SKIP (WONT_FIX) |
| All 4 legal modules enabled | PASS (via sidebar presence) |
| Terminology: sidebar/headings use legal terms | PARTIAL (GAP-D0-04 group header, GAP-D0-05 dashboard cards) |

## Previously Fixed Items Verified

| GAP ID | Summary | Status |
|--------|---------|--------|
| GAP-D0-01 | Matter templates not seeded | VERIFIED FIXED -- 4 templates present from initial provisioning |
| GAP-D0-07 | Seed does not pre-apply legal-za profile | VERIFIED FIXED -- legal-za active from start |
| GAP-D0-08 | Team member names display as "Unknown" | VERIFIED FIXED -- Names show correctly |
| GAP-D1-01 | Conflict Check page crashes | VERIFIED FIXED (tested as Alice on dashboard) |

## Console Errors

None observed during Day 0 execution.

## Known Persisting Issues (LOW)

- GAP-D0-04: "Projects" group header not renamed to "Matters"
- GAP-D0-05: Dashboard KPI cards use "Active Projects" / "Project Health"
- GAP-D0-06: Team page Role column empty for all members
