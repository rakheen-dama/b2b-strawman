# Day 22 — Checkpoint Results (Accounting ZA)

**Date**: 2026-05-15
**Agent**: QA
**Branch**: main
**Scenario**: accounting-za-90day-keycloak-v2.md

## Day 22 Checkpoints

### 22.1 — Add 3 more time entries across bookkeeping and year-end engagements

- **Actor**: Bob (Admin)
- **Action**: Logged 3 new time entries across 2 engagements via task-level "Log Time" dialog

**Entry 1: Kgosi Bookkeeping — Creditors reconciliation**
- Task: Creditors reconciliation
- Duration: 1h 30m
- Description: "Creditors April reconciliation"
- Rate: R 850/hr (member default)
- Billable: Yes
- **Result**: Saved successfully, dialog closed

**Entry 2: Kgosi Bookkeeping — VAT calculation & reconciliation**
- Task: VAT calculation & reconciliation
- Duration: 1h 0m
- Description: "VAT reconciliation for March period"
- Rate: R 850/hr (member default)
- Billable: Yes
- **Result**: Saved successfully, dialog closed

**Entry 3: Kgosi Year-End Pack — Draft annual financial statements**
- Task: Draft annual financial statements
- Duration: 2h 0m
- Description: "AFS draft preparation -- note disclosure drafting"
- Rate: R 850/hr (member default)
- Billable: Yes
- **Result**: Saved successfully, dialog closed

**Dashboard Verification**:
- Hours This Month: **21.5h** (17.0h prior + 4.5h new = 1.5h + 1.0h + 2.0h)
- Billable: 21.5h, Non-billable: 0
- Bookkeeping: **7.5h** (5.0h prior + 1.5h creditors + 1.0h VAT)
- Year-End Pack: **5.0h** (3.0h prior + 2.0h AFS draft)
- Recent Activity: 3 "Bob Ndlovu created a time_entry" events visible
- All 4 engagements Healthy, 0 overdue tasks

- **Result**: **PASS**

---

## Summary

| Checkpoint | Result | Notes |
|-----------|--------|-------|
| 22.1 Add 3 time entries (bookkeeping + year-end) | **PASS** | 3 entries logged (1.5h + 1.0h + 2.0h = 4.5h). Dashboard: 21.5h monthly. |

**Day 22 Result**: 1 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED. No new gaps.
**Bookkeeping total hours**: 7.5h (Bob 4.5h + Carol 2.0h + Thandi 1.0h... correction: Bob 3.0h+1.5h+1.0h = 5.5h, Carol 2.0h = 7.5h total).
**Year-End Pack total hours**: 5.0h (Thandi 2.0h + Bob 1.0h + Bob 2.0h = 5.0h).
**Monthly total hours**: 21.5h.
