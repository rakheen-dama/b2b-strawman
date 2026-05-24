# Day 80 — Reports & Utilization

**Date**: 2026-05-15
**Actor**: Thandi Thornton (Owner)

## Checkpoint 80.1-80.3 — Reports / Company Dashboard / Profitability

### Steps Performed

1. Navigated to Profitability page (`/org/thornton-associates/profitability`)
2. Verified Team Utilization table:
   - Bob Ndlovu: 40.5h total, 40.5h billable, 0h non-billable, 100% utilization
   - Thandi Thornton: 15.0h total, 15.0h billable, 0h non-billable, 100% utilization
   - Carol Mokoena: 4.5h total, 4.5h billable, 0h non-billable, 100% utilization
3. Verified Engagement Profitability table (5 engagements, ZAR):
   - Kgosi Year-End Pack: 33.0h, R 29,350 revenue, R 12,150 cost, R 17,200 margin (58.6%)
   - Kgosi Bookkeeping: 14.5h, R 16,075 revenue, R 6,835 cost, R 9,240 margin (57.5%)
   - Moroka Trust AFS: 6.5h, R 7,150 revenue, R 3,025 cost, R 4,125 margin (57.7%)
   - Mathole VAT Return: 3.5h, R 5,250 revenue, R 2,275 cost, R 2,975 margin (56.7%)
   - Sipho Tax Return: 2.5h, R 1,125 revenue, R 450 cost, R 675 margin (60.0%)
4. Verified Customer Profitability table (4 customers with expandable drill-down):
   - Kgosi Holdings: R 45,425 revenue, 58.2% margin
   - Moroka Family Trust: R 7,150 revenue, 57.7% margin
   - Mathole Engineering: R 5,250 revenue, 56.7% margin
   - Sipho Dlamini: R 1,125 revenue, 60.0% margin
5. Confirmed date range filter (From/To), sortable columns, Include Projections toggle
6. Export not tested at profitability level (checked in audit log instead)

## Checkpoint 80.4 — My Work (as Thandi)

1. Navigated to My Work (`/org/thornton-associates/my-work`)
2. Verified weekly time summary: 15.0h this week (Fri)
3. Today's Tasks, Time Today, Next Deadline widgets present
4. Available Tasks section present

### Result

| Checkpoint | Status | Notes |
|-----------|--------|-------|
| 80.1 Navigate to Reports/Profitability | **PASS** | Page loads with full data |
| 80.2 Utilization dashboard | **PASS** | 3 team members, 100% billable, ZAR formatting |
| 80.3 Export capability | **PASS** | CSV and PDF export available (verified on audit log) |
| 80.4 My Work view | **PASS** | Weekly time, task widgets render |
