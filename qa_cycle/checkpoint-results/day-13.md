# Day 13 — Accounting ZA 90-Day Lifecycle (Keycloak)

**Date**: 2026-05-15
**Branch**: `bugfix_cycle_2026-05-14`
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Agent**: QA Agent (Opus 4.6)
**Actor**: Bob Ndlovu (bob@thornton-test.local)

---

## OBS-4005 Verification

| Test | Expected | Observed | Result |
|------|----------|----------|--------|
| Log in as Bob, navigate to Kgosi Year-End Pack engagement | Dashboard loads as Bob (BN initials, bob@thornton-test.local in sidebar) | Logged in via KC at :8180, redirected to dashboard as Bob Ndlovu | **PASS** |
| Post comment on Year-End Pack via Client Comments tab | Comment submitted by Bob with correct author/timestamp | "Acknowledged @Thandi -- will have FS structure draft ready. Starting review today." posted, visible as "Bob Ndlovu now" | **PASS** |
| Check Activity tab for new comment event | Activity event shows actual engagement name, not literal "project" | `Bob Ndlovu commented on project "Kgosi Holdings — FY2025/26 Year-End Pack"` -- shows ACTUAL engagement name | **PASS** |
| Compare with old (pre-fix) comment event | Old event still shows "project" (expected -- data not retroactively updated) | `Thandi Thornton commented on project "project"` -- old data, expected | **PASS** |

**OBS-4005 Verdict: VERIFIED**

The fix correctly resolves the project name in newly created activity events. Bob's new comment shows "Kgosi Holdings -- FY2025/26 Year-End Pack" instead of literal "project". The old activity event from Thandi's Day 12 comment still shows "project" because the audit data was written before the fix -- this is expected and not a regression.

**Evidence**: `qa_cycle/evidence/day-13/obs-4005-verified-activity-tab.png`, `qa_cycle/evidence/day-13/obs-4005-activity-events-scrolled.png`

---

## Day 13 Checkpoint: 13.1 — Bob acknowledges with comment + 1.0h log: "FS structure review"

### Part A: Bob posts acknowledgment comment

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 13.1a | Log in as Bob (Admin) | **PASS** | KC login: bob@thornton-test.local / SecureP@ss2. Sidebar confirms BN / Bob Ndlovu. |
| 13.1b | Navigate to Kgosi Year-End Pack engagement | **PASS** | URL: /org/thornton-associates/projects/388d5104-7789-4ad6-bb6c-6d045e9663f3. Heading: "Kgosi Holdings -- FY2025/26 Year-End Pack". Status: Active. |
| 13.1c | Verify Thandi's Day 12 comment visible | **PASS** | Client Comments tab shows: Thandi Thornton (1 hour ago) -- "@Bob Need FS draft by day 30" |
| 13.1d | Post acknowledgment reply | **PASS** | Typed "Acknowledged @Thandi -- will have FS structure draft ready. Starting review today." in reply box. Clicked Post Reply. Comment appears as "Bob Ndlovu now". |
| 13.1e | Thread shows both comments in order | **PASS** | Client Comments tab: (1) Thandi "@Bob Need FS draft by day 30" (2) Bob "Acknowledged @Thandi -- will have FS structure draft ready. Starting review today." |

### Part B: Bob logs 1.0h on Year-End Pack: "FS structure review"

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 13.1f | Navigate to Tasks tab on Year-End Pack | **PASS** | 7 tasks visible (all Open, Unassigned). Each has Log Time + Claim buttons. |
| 13.1g | Click "Log Time" on "Draft annual financial statements" task | **PASS** | Log Time dialog appeared. Duration: 0h 0m, Date: 2026/05/15, Billable checked, Rate: R 850,00/hr. |
| 13.1h | Fill: 1h, description "FS structure review", submit | **PASS** | Set hours=1, description="FS structure review". Calculation: 1h x R 850,00 = R 850,00. Clicked Log Time. Dialog closed. |
| 13.1i | Verify Time tab: 3h total, 2 contributors, 2 entries | **PASS** | Time tab: Total=3h, Billable=3h, Contributors=2, Entries=2. By Task: Request & receive TB (2h/1 entry), Draft AFS (1h/1 entry). By Member: Thandi (2h), Bob (1h). |
| 13.1j | Verify Dashboard: monthly hours updated | **PASS** | Dashboard "Hours This Month" = 10.5h (was 9.5h). Year-End Pack row: 3.0h (was 2.0h). Team Time donut: 11h total. |

**Evidence**: `qa_cycle/evidence/day-13/time-tab-after-bob-1h.png`, `qa_cycle/evidence/day-13/dashboard-hours-after-bob-1h.png`

---

## Summary

| Metric | Value |
|--------|-------|
| Checkpoints | 10 |
| PASS | 10 |
| FAIL | 0 |
| PARTIAL | 0 |
| DEFERRED | 0 |
| New gaps | 0 |
| OBS-4005 | **VERIFIED** |

### Updated Running Totals

- **Year-End Pack total hours**: 3.0h (Thandi 2.0h + Bob 1.0h)
- **Monthly hours**: 10.5h (Sipho 2.5h + Bookkeeping 5.0h + Year-End Pack 3.0h)
- **Year-End Pack unbilled**: R 3,850.00 (Thandi 2h x R 1,500 = R 3,000 + Bob 1h x R 850 = R 850)
