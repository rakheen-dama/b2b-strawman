# Day 61 — Third bookkeeping invoice (May cycle)

**Branch**: `main`
**Cycle**: Accounting ZA 90-Day Lifecycle (Keycloak)
**Actor**: Thandi Thornton (Owner, `thandi@thornton-test.local`)
**Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md` -- checkpoint 61.1
**Result**: **PASS**

---

## Context

Day 61 requires creating the third bookkeeping invoice (May cycle) for Kgosi Holdings Monthly Bookkeeping engagement. Prior invoicing state:

| Invoice | Client | Engagement | Status | Total |
|---------|--------|------------|--------|-------|
| INV-0001 | Kgosi Holdings | Monthly Bookkeeping | Paid | R 6,411.25 |
| INV-0002 | Sipho Dlamini | Tax Return | Paid | R 2,875.00 |
| INV-0003 | Kgosi Holdings | Year-End Pack | Sent | R 33,752.50 |
| INV-0004 | Kgosi Holdings | Monthly Bookkeeping | Sent | R 6,037.50 |

INV-0004 was created by a prior agent run (second bookkeeping invoice -- April cycle). All existing bookkeeping time entries (11.0h) were already invoiced across INV-0001 + INV-0004.

## Prerequisite: Log May bookkeeping time entries

To create a third bookkeeping invoice, new May time entries were needed on the Kgosi Monthly Bookkeeping engagement (ID: `a32c67d5-8e09-47b9-82ec-f0e82fa94ec4`).

### Time entry 1: Month-end close & review
- Task: Month-end close & review (Open)
- Duration: 2h 0m
- Date: 2026-05-15
- Description: "May month-end close & review -- bank recon verification, journal entries, management pack compilation"
- Billable: Yes
- Rate: R 1,500.00/hr (Thandi's rate)
- Amount: R 3,000.00
- **Logged successfully** -- dialog closed, entry confirmed on Time tab.

### Time entry 2: Debtors reconciliation
- Task: Debtors reconciliation (Open)
- Duration: 1h 30m
- Date: 2026-05-15
- Description: "May debtors reconciliation -- outstanding invoices follow-up and aging analysis"
- Billable: Yes
- Rate: R 1,500.00/hr (Thandi's rate)
- Amount: R 2,250.00
- **Logged successfully** -- dialog closed, entry confirmed on Time tab.

### Time tab verification after logging

| Metric | Value |
|--------|-------|
| Total Time | 14h 30m |
| Billable | 14h 30m |
| Non-billable | 0m |
| Contributors | 3 |
| Entries | 8 |

By Task breakdown:
- Creditors reconciliation: 3h 30m (2 entries)
- Debtors reconciliation: 3h 30m (2 entries) -- includes new May entry
- Bank reconciliation: 3h (1 entry)
- Month-end close & review: 2h (1 entry) -- new May entry
- Management accounts preparation: 1h 30m (1 entry)
- VAT calculation & reconciliation: 1h (1 entry)

By Member: Thandi 7h, Bob 5h 30m, Carol 2h.

---

## Checkpoint 61.1 — Create third bookkeeping invoice (May cycle)

### Step 1: Fetch unbilled time

Navigated to Kgosi Holdings client detail > Invoices tab. Unbilled Time widget confirmed: **R 5,250.00 across 3.5 hours**.

Clicked New Invoice > Fetch Unbilled Time. Dialog showed:

**Kgosi Holdings -- Monthly Bookkeeping (Mar 2026)**: 2 of 2 selectable

| Entry | Member | Date | Duration | Amount |
|-------|--------|------|----------|--------|
| Month-end close & review | Thandi Thornton | May 15, 2026 | 2h | R 3,000.00 |
| Debtors reconciliation | Thandi Thornton | May 15, 2026 | 1h 30m | R 2,250.00 |

Total (2 items): **R 5,250.00**

Both items selected. Pre-generation checks:
- Warning: 2 of 5 required fields filled (non-blocking)
- Pass: Organization name is set
- Pass: All time entries have billing rates
- Pass: Customer tax number is set

Clicked "Create Draft (1 issues)".

### Step 2: Verify draft

Draft invoice created. Navigated to invoice detail:

| Field | Value |
|-------|-------|
| Status | Draft |
| Client | Kgosi Holdings (Pty) Ltd |
| Currency | ZAR |
| Line Items | 2 |

Line items:
1. Month-end close & review -- 2026-05-15 -- Thandi Thornton | Kgosi Monthly Bookkeeping | Qty: 2 | R 1,500.00 | R 3,000.00 | VAT 15%: R 450.00
2. Debtors reconciliation -- 2026-05-15 -- Thandi Thornton | Kgosi Monthly Bookkeeping | Qty: 1.5 | R 1,500.00 | R 2,250.00 | VAT 15%: R 337.50

**Subtotal: R 5,250.00 | VAT: R 787.50 | Total: R 6,037.50**

VAT 15% calculation correct: R 5,250.00 x 0.15 = R 787.50.

### Step 3: Approve

Clicked "Approve". Invoice number assigned: **INV-0005**. Issue date: May 15, 2026. Status: Approved.

### Step 4: Send

Clicked "Send Invoice". Validation warning appeared (2/5 required fields). Used owner override "Send Anyway".

Status changed to: **Sent**.

Available actions: Preview, Record Payment, Void.

**PASS**.

---

## Final Invoice State

| Invoice | Client | Engagement | Status | Total |
|---------|--------|------------|--------|-------|
| INV-0001 | Kgosi Holdings | Monthly Bookkeeping | Paid | R 6,411.25 |
| INV-0002 | Sipho Dlamini | Tax Return | Paid | R 2,875.00 |
| INV-0003 | Kgosi Holdings | Year-End Pack | Sent | R 33,752.50 |
| INV-0004 | Kgosi Holdings | Monthly Bookkeeping | Sent | R 6,037.50 |
| **INV-0005** | **Kgosi Holdings** | **Monthly Bookkeeping** | **Sent** | **R 6,037.50** |

Total Outstanding: R 45,827.50 (INV-0003 + INV-0004 + INV-0005)
Total Paid: R 9,286.25 (INV-0001 + INV-0002)

---

## Day 61 Checkpoint Summary

| # | Checkpoint | Result | Notes |
|---|------------|--------|-------|
| 61.1 | Create third bookkeeping invoice (May cycle) | **PASS** | INV-0005 created from 3.5h unbilled May time (2h month-end close + 1.5h debtors recon), Subtotal R 5,250, VAT R 787.50, Total R 6,037.50. Approved + Sent. |

## Overall Day 61 Result: **PASS**

No new gaps filed.
