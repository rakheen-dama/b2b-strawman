# Day 7 Checkpoint Results — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-15
**Branch**: `bugfix_cycle_2026-05-14`
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Agent**: QA Agent (Opus 4.6)
**Status**: **DAY 7 COMPLETE** -- 4 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED

## Summary

All Day 7 checkpoints passed. Two time-logging scenarios were executed:

1. **Carol logs 1.5 hours** on Sipho Dlamini's tax return engagement ("Drafted tax return in eFiling") against the "Prepare ITR12" task. Time entry recorded at Carol's billing rate of R 450,00/hr. Total: R 675,00. Sipho engagement now has 2.5h total (1.0h from Day 4 + 1.5h from Day 7).

2. **Thandi logs 2.0 hours** on Kgosi Holdings Year-End Pack engagement ("Initial planning meeting + scope confirmation") against the "Request & receive trial balance" task. Time entry recorded at Thandi's Owner billing rate of R 1 500,00/hr. Total: R 3 000,00. Year-End Pack engagement now has 2.0h total.

---

## Checkpoint Results

### 7.1 — Carol logs 1.5 hours on Sipho engagement

**Actor**: Carol Mokoena (Member, carol@thornton-test.local)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 7.1a | Login as Carol via Keycloak | **PASS** | Cleared browser cookies + KC session. KC login: carol@thornton-test.local / [REDACTED]. Redirected to /org/thornton-associates/dashboard. Sidebar confirms: "CM" initials, "Carol Mokoena", "carol@thornton-test.local". |
| 7.1b | Navigate to Sipho engagement, Tasks tab | **PASS** | URL: /org/thornton-associates/projects/583ee45e-40b5-4846-9082-92f69f0f5f17. Title: "Sipho Dlamini -- 2025/26 Tax Return", Status: Active, 7 tasks. Carol assigned to 4 tasks. Clicked "Log Time" on "Prepare ITR12" task (most relevant for eFiling work). |
| 7.1c | Log 1.5 hours with description "Drafted tax return in eFiling" | **PASS** | Log Time dialog: Duration=1h 30m, Date=2026-05-15, Description="Drafted tax return in eFiling", Billable=checked, Rate=R 450,00/hr. Total: 1h 30m x R 450,00 = R 675,00. Submitted successfully. Screenshot: `qa_cycle/evidence/day-07/carol-log-time-dialog-1h30m.png` |
| 7.1d | Verify time entry recorded on Time tab | **PASS** | Time tab confirms: Total=2h 30m, Billable=2h 30m, Entries=2, Contributors=1. By Task: Prepare ITR12 (1h 30m, 1 entry) + Collect IRP5/IT3(a) certificates (1h, 1 entry from Day 4). Screenshot: `qa_cycle/evidence/day-07/carol-time-tab-2h30m.png` |

### 7.2 — Thandi logs 2.0 hours on Kgosi Year-End Pack

**Actor**: Thandi Thornton (Owner, thandi@thornton-test.local)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 7.2a | Login as Thandi via Keycloak | **PASS** | Cleared browser cookies + KC session. KC login: thandi@thornton-test.local / [REDACTED]. Redirected to /org/thornton-associates/dashboard. Sidebar confirms: "TT" initials, "Thandi Thornton", "thandi@thornton-test.local". |
| 7.2b | Navigate to Kgosi Year-End Pack engagement, Tasks tab | **PASS** | URL: /org/thornton-associates/projects/388d5104-7789-4ad6-bb6c-6d045e9663f3. Title: "Kgosi Holdings -- FY2025/26 Year-End Pack", Status: Active, 7 tasks (all Open, Unassigned). Clicked "Log Time" on "Request & receive trial balance" task. |
| 7.2c | Log 2.0 hours with description "Initial planning meeting + scope confirmation" | **PASS** | Log Time dialog: Duration=2h 0m, Date=2026-05-15, Description="Initial planning meeting + scope confirmation", Billable=checked, Rate=R 1 500,00/hr. Total: 2h x R 1 500,00 = R 3 000,00. Submitted successfully. Screenshot: `qa_cycle/evidence/day-07/thandi-log-time-dialog-2h.png` |
| 7.2d | Verify time entry recorded on Time tab | **PASS** | Time tab confirms: Total=2h, Billable=2h, Non-billable=0m, Entries=1, Contributors=1. By Task: Request & receive trial balance (2h, 1 entry). By Member: Thandi Thornton (2h billable). Screenshot: `qa_cycle/evidence/day-07/thandi-time-tab-2h-yearend.png` |

---

## Time Entry Details

### Carol's Time Entry on Sipho Engagement

| Field | Value |
|-------|-------|
| Engagement | Sipho Dlamini -- 2025/26 Tax Return (583ee45e-40b5-4846-9082-92f69f0f5f17) |
| Task | Prepare ITR12 |
| Duration | 1.5 hours (1h 30m) |
| Description | Drafted tax return in eFiling |
| Billable | Yes |
| Rate | R 450,00/hr (Carol's member default) |
| Amount | R 675,00 |
| Date | 2026-05-15 |
| Logged by | Carol Mokoena |
| **Engagement total after Day 7** | **2.5h** (2 entries: 1.0h Day 4 + 1.5h Day 7) |

### Thandi's Time Entry on Kgosi Year-End Pack

| Field | Value |
|-------|-------|
| Engagement | Kgosi Holdings -- FY2025/26 Year-End Pack (388d5104-7789-4ad6-bb6c-6d045e9663f3) |
| Task | Request & receive trial balance |
| Duration | 2.0 hours (2h 0m) |
| Description | Initial planning meeting + scope confirmation |
| Billable | Yes |
| Rate | R 1 500,00/hr (Thandi's Owner default) |
| Amount | R 3 000,00 |
| Date | 2026-05-15 |
| Logged by | Thandi Thornton |
| **Engagement total after Day 7** | **2.0h** (1 entry) |

---

## Console Errors

| Category | Count | Severity | Details |
|----------|-------|----------|---------|
| 404 /api/assistant/invocations | ~3 | LOW | AI assistant API not implemented. Falls back gracefully. Pre-existing. |

**No new product-level console errors introduced by Day 7 operations.**

---

## Observations

1. **Time logging from task row**: The "Log Time" button is directly accessible from each task row in the Tasks tab, consistent with Day 4 behavior. The dialog pre-fills date, shows the member's billing rate, and calculates the total automatically.

2. **Rate differentiation by role**: Carol's Member rate (R 450,00/hr) and Thandi's Owner rate (R 1 500,00/hr) are correctly applied as pre-seeded from the accounting-za vertical profile rate cards. No manual rate override needed.

3. **Member role visibility**: Carol (Member role) has a reduced tab set on the engagement detail page (no Financials, Rates, or Audit tabs). Thandi (Owner role) sees the full tab set. Role-based UI scoping is working correctly.

4. **Keycloak session management**: Logging out required clearing browser cookies (via Playwright context.clearCookies()) and KC session logout. The gateway's CSRF-protected logout form was not directly submittable via JS evaluate; clearing cookies + KC logout was the reliable approach.

---

## Evidence Files

- `qa_cycle/evidence/day-07/carol-log-time-dialog-1h30m.png` -- Log Time dialog with 1h 30m, "Drafted tax return in eFiling", R 450,00/hr
- `qa_cycle/evidence/day-07/carol-time-tab-2h30m.png` -- Time tab showing 2.5h total (2 entries) on Sipho engagement
- `qa_cycle/evidence/day-07/thandi-log-time-dialog-2h.png` -- Log Time dialog with 2h 0m, "Initial planning meeting + scope confirmation", R 1 500,00/hr
- `qa_cycle/evidence/day-07/thandi-time-tab-2h-yearend.png` -- Time tab showing 2h total (1 entry) on Kgosi Year-End Pack

---

**Day 7 Result: 4 PASS / 0 FAIL / 0 PARTIAL / 0 DEFERRED**
**No new gaps filed.**
