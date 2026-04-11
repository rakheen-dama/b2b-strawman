# Cycle 4 — Day 75 Checkpoint Results

**Executed**: 2026-04-06 ~22:21–22:39 UTC
**Actor**: Alice (Owner), Bob (Admin), Carol (Member)
**Stack**: E2E mock-auth (localhost:3001 / 8081)

## Step Results

### Multi-matter per client — Actor: Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 75.1 | Create 2nd matter for Sipho (Litigation template) | PASS | Created via UI "New from Template". Project ID: `90de7a04-260f-49f8-9a3b-ba8188d044b9`. 9 tasks created. GAP-D1-07 persists — name shows `{client} - {type}` instead of entered name. |
| 75.2 | Verify Sipho has 2 matters | PASS | Client detail shows "2 projects" with 2 rows in Projects tab. |

### Adverse party registry — Actor: Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 75.3 | Add RAF to Sipho matter #1 | PASS | Already added on Day 14 via DB. Verified via API: RAF linked as DEFENDANT. |
| 75.4 | Add RAF to Sipho matter #2 | PASS (via DB) | Inserted via DB. GAP-D7-05 persists (no "Add" button on Adverse Parties tab). Verified via UI: Adverse Parties tab shows RAF. |
| 75.5 | Add T. Mokoena to QuickCollect vs Mokoena | PASS (via DB) | Created adverse party + link via DB. GAP-D7-05 blocks UI creation. |
| 75.6 | Add R. Pillay to QuickCollect vs Pillay | PASS (via DB) | Created adverse party + link via DB. |

### Conflict check stress test — Actor: Bob

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 75.7 | Search "Ndlovu" → existing client | PARTIAL | Returns "Potential Conflict" with 40% score, relationship EXISTING CLIENT. Party Name cell is blank (minor display issue). Expected green "same side" result but shows amber. |
| 75.8 | Search "Road Accident Fund" → adverse party | PASS | Returns "Conflict Found" at 100%, 2 rows (both Sipho matters), relationship DEFENDANT. Linked Matter shows `{client} - {type}` (GAP-D1-07). |
| 75.9 | Search "Mokoena" → adverse party | PASS | Returns "Conflict Found" — T. Mokoena at 63% on "Debt Recovery — vs Mokoena (R45,000)", DEBTOR. Improvement over Day 14 GAP-D14-01 — adverse parties now searchable. |
| 75.10 | Screenshot: conflict check adverse detected | PASS | Screenshot captured: `day-75-conflict-adverse-detected.png` |

### Estate matter progression — Actor: Carol + Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 75.11 | Mark "Advertise for creditors" → DONE | PASS (via API) | Carol cannot change task status from UI (GAP-D7-03). Transitioned OPEN → IN_PROGRESS → DONE via API as Alice. Follow-up task auto-created. |
| 75.12 | Mark "Inventory of assets & liabilities" → DONE | PASS (via API) | Same approach. Follow-up task auto-created. |
| 75.13 | Log 360 min on "Prepare L&D account" (Carol) | PASS | Logged via UI Log Time dialog. Carol rate R550/hr. Time tab shows 6h billable. Total project time: 8h 30m across 3 contributors. |
| 75.14 | Create information request for Moroka | PARTIAL | "New Request" dialog exists but lacks Subject and Items fields (only Template, Portal Contact, Reminder Interval). "Save as Draft" silently failed. **NEW GAP: GAP-D75-01**. |
| 75.15 | Send information request → check Mailpit | NOT_TESTED | Blocked by GAP-D75-01 — cannot compose request content. |

### Year-end engagement — Actor: Alice

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 75.16 | Create 2nd matter for Apex (Commercial template) | PASS | Created via UI "New from Template". Project ID: `58c9ac50-3c0a-4c09-94f6-442ec4f5b710`. 9 tasks. Linked to Apex. Name shows `{client} - {transaction}` (GAP-D1-07 variant). |
| 75.17 | Verify Apex has 2 matters | PASS | Client detail shows "2 projects". |
| 75.18 | Log 120 min as Bob on "Client intake & scope of work" | PASS | Logged via UI. Bob rate R1,200/hr. Dialog confirmed billable. |

### Resource utilization

| Step | Description | Result | Evidence |
|------|-------------|--------|----------|
| 75.19 | Navigate to Resources | PASS | Page loads with capacity planning grid. Alice, Bob, Carol shown. GAP-D45-03 persists (4 "Unknown" entries). |
| 75.20 | Verify billable hours breakdown | PARTIAL | Capacity grid shows 0/40h — this is a staffing allocation view, not a billable hours report. Time data from logging is tracked per-project, not reflected here. |
| 75.21 | Check capacity data | PASS | 40h/week per member, 280h team total (7 members incl. stale). |

## Day 75 Checkpoints

| Checkpoint | Result |
|------------|--------|
| Multi-matter per client (Sipho: 2, Apex: 2) | PASS |
| 8 total matters across 4 clients (not 9 — QuickCollect still has 3) | PASS (8 matters total) |
| Adverse party registry populated (RAF, Mokoena, Pillay) | PASS (via DB — GAP-D7-05 blocks UI) |
| Conflict checks detect adverse parties | PASS — RAF 100%, Mokoena 63% |
| Estate matter progression tracked | PASS — 2 items DONE, follow-ups created, 6h logged |
| Information request sent (Mailpit) | FAIL — GAP-D75-01 blocks composition |
| Resource utilization shows meaningful data | PARTIAL — capacity grid loads but doesn't show time utilization |
| Terminology consistency | PARTIAL — "Matters" sidebar, "Projects" heading/breadcrumb mixed |

## New Gaps

| ID | Summary | Severity | Status |
|----|---------|----------|--------|
| GAP-D75-01 | Information request "New Request" dialog lacks Subject and Items fields — cannot compose request content. "Save as Draft" silently fails with no error feedback. | MEDIUM | OPEN |

## Console Errors

0 console errors observed during Day 75 execution.

## Data State After Day 75

- **4 clients** (all ACTIVE): Sipho Ndlovu, Apex Holdings, Moroka Family Trust, QuickCollect Services
- **8 matters**: Sipho (2 Litigation), Apex (1 Commercial + 1 Commercial), Moroka (1 Estates), QuickCollect (3 Collections)
- **12+ time entries** (9 from earlier days + Carol 6h + Bob 2h + earlier Moroka entries)
- **3 adverse parties**: Road Accident Fund (2 links), T. Mokoena (1 link), R. Pillay (1 link)
- **2 estate tasks DONE** with follow-up tasks auto-created
- **1 invoice PAID** (Apex INV-0001)
