# Day 14 Checkpoint Results — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-15
**Branch**: `bugfix_cycle_2026-05-14`
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, KC :8180)
**Agent**: QA Agent (Opus 4.6)
**Actor**: Bob Ndlovu (Admin)
**Engagement**: Kgosi Holdings — FY2025/26 Year-End Pack (ID: 388d5104-7789-4ad6-bb6c-6d045e9663f3)

---

## Scenario

> **14.1** Budget tab on year-end pack: set budget to 40 hours, R60,000 → verify burn tracking

## Pre-conditions

- Logged in as Bob Ndlovu (bob@thornton-test.local, Admin)
- Year-End Pack engagement exists with 3.0h logged (2.0h Thandi + 1.0h Bob) and R3,850 unbilled
- No budget previously configured (Overview showed "Budget: --", Budget tab showed "No budget configured")

---

## Checkpoint Results

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 14.1a | Navigate to Year-End Pack engagement | **PASS** | Clicked engagement from Dashboard Engagement Health table. URL: `/org/thornton-associates/projects/388d5104-7789-4ad6-bb6c-6d045e9663f3`. Title: "Kgosi Holdings — FY2025/26 Year-End Pack", Status: Active. |
| 14.1b | Budget tab exists and loads | **PASS** | Budget tab visible in tablist. Clicked it. Shows "No budget configured" with "Configure budget" button and description: "Set a budget to track spending against your project plan. Choose between fixed-price or time-and-materials." |
| 14.1c | Configure budget dialog opens | **PASS** | Clicked "Configure budget". Dialog: "Set Budget -- Configure budget limits for this project. Set hours, amount, or both." Fields: Budget Hours (spinbutton), Budget Amount (spinbutton), Currency (combobox, pre-set to "ZAR -- South African Rand"), Alert Threshold (spinbutton, default 80%), Notes (textbox). |
| 14.1d | Set budget to 40 hours | **PASS** | Filled Budget Hours = 40. Value confirmed in dialog snapshot. |
| 14.1e | Set budget to R60,000 | **PASS** | Filled Budget Amount = 60000. Value confirmed in dialog snapshot. Currency: ZAR. |
| 14.1f | Alert threshold defaults to 80% | **PASS** | Pre-populated at 80%. Help text: "You will be alerted when consumption reaches this percentage (50-100%)." |
| 14.1g | Save budget | **PASS** | Clicked "Save Budget". Dialog closed. Budget tab re-rendered with burn tracking view (no errors, no spinner stuck). |
| 14.1h | Verify hours burn tracking | **PASS** | Hours section: Budget=40h, Consumed=3h, Remaining=37h, Used=8%. Status badge: "On Track". Progress bar rendered. |
| 14.1i | Verify amount burn tracking (ZAR) | **PASS** | Amount section: Budget=R 60 000,00, Consumed=R 3 850,00, Remaining=R 56 150,00, Used=6%. Status badge: "On Track". Progress bar rendered. ZAR formatting correct (space-separated thousands, comma decimal). |
| 14.1j | Budget status overall | **PASS** | Top-level status: "On Track". Edit and Delete buttons available. |
| 14.1k | Notes persisted | **PASS** | Displays: "Notes: Year-end pack budget: 40 hours at blended rate for AFS preparation and CIPC filing". Alert threshold: 80%. |
| 14.1l | Overview tab reflects budget | **PASS** | Switched to Overview tab. Budget summary card now shows "8% used" (was "--"). Setup steps advanced from 3/5 (60%) to 4/5 (80%) -- budget configuration counted as a setup step. Budget widget in sidebar: "8% used", "3.0h consumed", "37.0h remaining". |
| 14.1m | Activity event recorded | **PASS** | Activity tab shows: "Bob Ndlovu performed budget.created on project_budget" as most recent event. Timestamp correct. |
| 14.1n | Dashboard budget health | **PASS** | Dashboard Budget Health card: 3 on track (green), 0 at risk, 0 over budget. Year-End Pack row: "Healthy", 3.0h, 0/7 tasks. |

---

## Summary

| Metric | Count |
|--------|-------|
| PASS | 14 |
| FAIL | 0 |
| PARTIAL | 0 |
| DEFERRED | 0 |
| New gaps | 0 |

## Evidence Files

- `qa_cycle/evidence/day-14/budget-configure-dialog.png` -- Set Budget dialog with 40h / R60,000 / ZAR / 80% threshold filled
- `qa_cycle/evidence/day-14/budget-tracking-configured.png` -- Budget tab showing burn tracking (hours + amount progress bars, On Track status)

## Notes

- Budget configuration is straightforward -- single dialog with hours, amount, currency, alert threshold, and notes.
- Burn tracking correctly picks up the 3.0h and R3,850 already logged against this engagement.
- Hours used (8%) and amount used (6%) differ because the blended hourly rate of logged time (~R1,283/hr weighted average of Thandi R1,500/hr and Bob R850/hr) is lower than the implied budget rate (R60,000 / 40h = R1,500/hr).
- ZAR formatting throughout is correct (R symbol, space-separated thousands, comma for decimals).
- The alert threshold (80%) will be relevant for Day 30 automation trigger check in the scenario.
- One pre-existing cosmetic note: Thandi's Day 12 comment activity still shows literal "project" text -- this is the audit event persisted before OBS-4005 was fixed. Not a new issue; the fix only affects events created after PR #1306.

**Day 14: ALL PASS (14/14)**
