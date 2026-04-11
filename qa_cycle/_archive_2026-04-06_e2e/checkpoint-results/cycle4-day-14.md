# Cycle 4 -- Day 14: Trust Deposits & Conflict Detection

**Date**: 2026-04-06
**Actors**: Alice (Owner), Bob (Admin), Carol (Member)
**Build**: Branch `bugfix_cycle_2026-04-06` (includes PRs #970-975)
**Method**: UI (Playwright MCP) + DB for trust/adverse party operations (known gaps)

## Checkpoint Results

### Trust Accounting -- Actor: Alice

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 14.1 | Navigate to Trust Accounting > Transactions | BLOCKED | Trust Accounting page loads but shows "No Trust Accounts" with no create button. GAP-D0-02 (WONT_FIX) confirmed -- CreateTrustAccountDialog component missing. |
| 14.2 | Click New Transaction > Type = DEPOSIT | BLOCKED | No trust accounts exist, cannot create transactions. |
| 14.3 | Fill: Moroka Family Trust, R250,000, reference | BLOCKED | Cascading from 14.1. |
| 14.4 | Save, verify PENDING APPROVAL | BLOCKED | Cascading from 14.1. |
| 14.5 | Approve the transaction | BLOCKED | Cascading from 14.1. |
| 14.6 | Navigate to Client Ledgers > Moroka | BLOCKED | Cascading from 14.1. |
| 14.7 | Verify R250,000 balance | BLOCKED | Cascading from 14.1. |
| 14.8 | Screenshot: Trust deposit + ledger | BLOCKED | Cascading from 14.1. |

### Second Trust Deposit

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 14.9 | Create deposit: QuickCollect, R45,000 | BLOCKED | Cascading from 14.1. |
| 14.10 | Approve transaction | BLOCKED | Cascading from 14.1. |
| 14.11 | Verify QuickCollect ledger R45,000 | BLOCKED | Cascading from 14.1. |

### Conflict Detection -- Actor: Bob

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 14.12 | Navigate to Conflict Check | PASS | Page loads with Run Check and History (4 previous checks) tabs. Form fields: Name, ID Number, Registration Number, Check Type, Customer, Matter. |
| 14.13 | Search "Mokoena" -> expect AMBER/RED match | PARTIAL | Search returned **"No Conflict"**. Mokoena is a debtor name in matter title, not a registered client or adverse party. Conflict check only matches against client names. No adverse parties had been registered at this point. Expected result per test plan was AMBER/RED but system design doesn't cross-reference matter names/descriptions. |
| 14.14 | Verify match details show linked matter | FAIL | No match found -- "No Conflict" result. |
| 14.15 | Screenshot: Conflict check with match | SKIP | No match to screenshot. |

### Adverse Party Registry

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 14.16 | Sipho matter > Adverse Parties tab | PASS | Tab loads showing "No adverse parties linked to this project." No "Add" button visible (GAP-D7-05). |
| 14.17 | Add adverse party: "Road Accident Fund" | PARTIAL | Created via DB (adverse_parties + adverse_party_links tables). No UI create functionality available. Party type: ORGANIZATION, relationship: DEFENDANT. |
| 14.18 | Verify adverse party linked to matter | PASS | After DB insert, Adverse Parties tab shows table: Party Name "Road Accident Fund", Relationship "DEFENDANT", Description with full text, Linked date 06/04/2026, Actions button. |

### More Time Logging

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 14.19 | Carol: 120 min on Sipho "Discovery" | PASS | Log Time via UI. Duration 2h. Description: "Collating medical records and police report for discovery bundle". Rate R550/hr confirmed. |
| 14.20 | Carol: 60 min on QuickCollect vs Mokoena "Issue summons" | PASS | Log Time via UI. Duration 1h. Description: "Preparing summons for service". Rate R550/hr confirmed. |
| 14.21 | Bob: 90 min on Moroka "Inventory of assets & liabilities" | PASS | Log Time via UI. Duration 1h 30m. Description: "Compiling estate asset register from bank statements and property valuations". Rate R1200/hr confirmed. |

### Trust Dashboard Check

| Step | Description | Result | Evidence / Notes |
|------|-------------|--------|------------------|
| 14.22 | Navigate to Trust Accounting dashboard | BLOCKED | No trust accounts exist. Dashboard shows "No Trust Accounts" empty state. |
| 14.23 | Verify summary: R295,000 total, 2 clients | BLOCKED | Cascading from 14.1. |
| 14.24 | Screenshot: Trust dashboard | BLOCKED | Cascading from 14.1. |

## Day 14 Checkpoint Summary

| Checkpoint | Result |
|-----------|--------|
| 2 trust deposits approved and posted | BLOCKED (GAP-D0-02: no create trust account dialog) |
| Client ledger balances correct | BLOCKED (no trust accounts) |
| Conflict check found Mokoena as adverse party | FAIL (returned NO_CONFLICT -- conflict check doesn't search matter names/debtors) |
| Adverse party "Road Accident Fund" linked to Sipho | PASS (created via DB, displays correctly in UI) |
| Trust dashboard shows accurate summary | BLOCKED (no trust accounts) |
| Terminology: Trust pages use "Client" not "Customer" | N/A (trust pages not functional) |

## New Gaps Found

| ID | Summary | Severity | Status |
|----|---------|----------|--------|
| GAP-D14-01 | Conflict check does not match against adverse party names or matter name/description substrings. Searching "Mokoena" returns NO_CONFLICT despite "vs Mokoena" appearing in matter name. Only matches exact client names. | MEDIUM | OPEN |

## Existing Gaps Confirmed

| ID | Status | Notes |
|----|--------|-------|
| GAP-D0-02 | WONT_FIX confirmed | Trust Accounting page loads, has full dashboard layout, but no "Create Trust Account" button. All trust operations blocked. |
| GAP-D7-05 | OPEN confirmed | Adverse Parties tab is read-only. No "Add Adverse Party" UI control. |

## Data State After Day 14

- **Time entries**: 9 total (Day 7: 6 + Day 14: 3 new)
  - Carol: 90+60+45+120+60 = 375 min (6h 15m)
  - Bob: 120+180+90 = 390 min (6h 30m)
  - Alice: 60 min (1h)
  - **Total**: 825 min = 13h 45m, all billable
- **Adverse parties**: 1 (Road Accident Fund, DEFENDANT, linked to Sipho matter)
- **Trust accounts**: 0 (blocked by GAP-D0-02)
- **Trust transactions**: 0
