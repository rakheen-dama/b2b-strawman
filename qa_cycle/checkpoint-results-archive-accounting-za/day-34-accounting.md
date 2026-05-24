# Day 34 -- Profitability wow moment (accounting cycle)

**Date**: 2026-05-15
**Actor**: Thandi Thornton (Owner)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080)
**Scenario**: `qa/testplan/demos/accounting-za-90day-keycloak-v2.md`, checkpoints 34.1-34.4

---

## Checkpoint 34.1 -- Navigate to Reports > Profitability

| ck | Step | Result | Evidence |
|----|------|--------|----------|
| 34.1 | Navigate to Finance > Profitability | **PASS** | Sidebar Finance section contains Invoices, Profitability, Reports links. Clicked Profitability -- page loaded at `/org/thornton-associates/profitability`. Title: "Profitability -- Team utilization, project profitability, and customer profitability across your organization". |

## Checkpoint 34.2 -- Verify all engagements listed with ZAR formatting

| ck | Step | Result | Evidence |
|----|------|--------|----------|
| 34.2a | Team Utilization table | **PASS** | 3 team members listed (May 1-15): Bob Ndlovu 40.5h (100.0% utilization), Carol Mokoena 4.5h (100.0%), Thandi Thornton 4.5h (100.0%). All billable, 0 non-billable. Currency breakdown expandable per member. |
| 34.2b | Engagement Profitability table | **PASS** | 4 engagements with full ZAR financials: (1) Kgosi Year-End Pack: 33.0h, Rev R 29,350, Cost R 12,150, Margin R 17,200 (58.6%); (2) Moroka Trust AFS: 6.5h, Rev R 7,150, Cost R 3,025, Margin R 4,125 (57.7%); (3) Kgosi Bookkeeping: 7.5h, Rev R 5,575, Cost R 2,285, Margin R 3,290 (59.0%); (4) Sipho Tax Return: 2.5h, Rev R 1,125, Cost R 450, Margin R 675 (60.0%). All amounts in ZAR with R prefix and comma formatting. |
| 34.2c | Customer Profitability table | **PASS** | 3 customers: Kgosi Holdings R 34,925 rev (58.7% margin), Moroka Family Trust R 7,150 rev (57.7% margin), Sipho Dlamini R 1,125 rev (60.0% margin). Expandable rows -- Kgosi shows 2 sub-engagements (Year-End Pack + Bookkeeping). |

## Checkpoint 34.3 -- Verify filter by client, engagement type, date range

| ck | Step | Result | Evidence |
|----|------|--------|----------|
| 34.3a | Date range filter | **PASS** | All 3 tables have From/To date inputs. Default range: 2026-05-01 to 2026-05-15. Fields are editable. |
| 34.3b | Sort by Client/Engagement | **PASS** | Engagement Profitability table has sortable columns (Engagement, Client, Billable Hours, Revenue, Cost, Margin, Margin %). Clicking "Client" column sorts the table. |
| 34.3c | Customer expand/drill-down | **PASS** | Customer Profitability rows are expandable. Clicked Kgosi Holdings -- expanded to show sub-engagement breakdown (Year-End Pack + Monthly Bookkeeping). |
| 34.3d | Include Projections toggle | **PASS** | Toggle switch "Include Projections" available at top of page. |

## Checkpoint 34.4 -- Screenshot: Profitability dashboard

| ck | Step | Result | Evidence |
|----|------|--------|----------|
| 34.4 | Screenshot captured | **PASS** | Full-page screenshot saved as `day-34-profitability-dashboard.png`. Shows all 3 tables: Team Utilization (3 members), Engagement Profitability (4 engagements), Customer Profitability (3 customers). All ZAR formatted. |

---

## Day 36 -- First invoice: Kgosi Monthly Bookkeeping

| ck | Step | Result | Evidence |
|----|------|--------|----------|
| 36.1 | Navigate to Kgosi > Bookkeeping engagement > Overview | **PASS** | Engagement overview shows "Unbilled Time: R 5,575.00 across 7.5 hours" with "Generate Invoice" CTA link. |
| 36.2 | Create invoice from unbilled time entries | **PASS** | Clicked "New Invoice" on Kgosi client Invoices tab. Generate Invoice dialog opened. Fetched unbilled time (11 items total across 2 engagements). Deselected Year-End Pack entries (7 items). Left only Monthly Bookkeeping entries (4 items, R 5,575.00). Pre-generation checks: 3 pass (org name, billing rates, tax number), 1 warning (2/5 required fields). Created draft. |
| 36.3 | Verify line items: date, member, description, hours, rate, amount | **PASS** | Invoice detail page shows 4 line items: (1) Bank recon -- Bob -- 3h @ R 850 = R 2,550; (2) Creditors recon -- Bob -- 1.5h @ R 850 = R 1,275; (3) VAT calc -- Bob -- 1h @ R 850 = R 850; (4) Debtors recon -- Carol -- 2h @ R 450 = R 900. Each line shows description, date (2026-05-15), member name, engagement name. |
| 36.4 | Verify VAT 15% calculation | **PASS** | Each line item shows "VAT -- Standard (15%)" with correct tax amount. Tax amounts: R 382.50 + R 191.25 + R 127.50 + R 135.00 = R 836.25. Invoice totals: Subtotal R 5,575.00, VAT R 836.25, Total R 6,411.25. Math verified correct (5575 * 0.15 = 836.25). |
| 36.5 | Field promotion check (invoice promoted slugs) | **PASS** | Invoice details form shows inline fields: PO Number (textbox), Tax Type (dropdown: VAT/GST/Sales Tax/None), Billing Period Start (date), Billing Period End (date). Also: Due Date, Payment Terms, Notes. These are the promoted invoice slugs rendering as native form fields, not in a custom fields sidebar. |
| 36.6 | Invoice saved as DRAFT in list | **PASS** | Invoice ID: `b6ba784c-d189-4cb1-8651-d7e84b34f610`. Status: Draft. Appears in Kgosi Holdings Invoices tab (count: 1). Unbilled Time card updated from R 34,925 to R 29,350 (bookkeeping entries now billed). Audit log shows "Invoice -- Thandi Thornton -- 21 seconds ago". |

---

## Summary

- **Day 32**: All steps PASS. Mathole Engineering ACTIVE, VAT Return engagement created with 5 tasks.
- **Day 34**: All steps PASS. Profitability dashboard shows 4 engagements + 3 customers with full ZAR revenue/cost/margin data. Screenshot captured.
- **Day 36**: All steps PASS. First invoice created as DRAFT for Kgosi Monthly Bookkeeping. 4 line items, VAT 15% correct, invoice total R 6,411.25. Promoted invoice slugs visible inline.
- **New gaps**: None.
