# 627F — observed after-evidence (627.14), 2026-10-28

Code under test: `$WT` (`phase88/10F-small-fixes`, PR #1698, ca82fc0cc).
- e2e :3001: the frontend was rebuilt from `$WT` with `docker compose -f compose/docker-compose.e2e.yml up -d --build --no-deps frontend`. The DB was kept and the backend untouched (tenant `tenant_7d218705360b`).
- Keycloak :3000: the slice ran as `pnpm dev` (keycloak mode) from `$WT/frontend`, then the frontend was restored to main via `svc.sh`.

## O1 G-C2 — PASS
As Alice: Adverse parties → Road Accident Fund → Actions → Link to Matter. The dialog lists 10 matters. No error boundary, and the console is clean (`o1-link-to-matter-dialog-lists-matters.png`).
Linked to "Ndlovu v RAF fabk" / Sipho Ndlovu fabk as Opposing Party. The registry's Links count went from 0 to 1 (`o1-registry-raf-links-1.png`). Link id `df94ec27-4b93-4134-9125-bcd03a3723ca` (read-only SELECT).

## O2 G-D5 — PASS
As Bob, on matter 5b7fcf0c → Tasks → New Task, "627F O2 - file heads of argument", due 2026-11-05, assignee Bob. This created task `cbd9376e-d69e-44f2-bc20-5d74647bf6e3` (`o2-db-task.txt`).
My work, "Upcoming · next 14 days":
1. Court date 09:30, Gauteng Division, Johannesburg · Thu 5 Nov · Case management
2. Task due "627F O2 …" · Thu 5 Nov

Next deadline: "Court date · Thu 5 Nov / Gauteng Division, Johannesburg / 627F repro - Bob lead court date / 8d remaining". Today's diary still reads "Nothing in the diary today" (`o2-bob-my-work-court-date-only.png`, `o2-bob-my-work-court-date-then-task-1280.png`).
The task-row wording "06 Dec CMC" refers to the Phase 87 capstone tenant, which no longer exists. The court date at today+8 on a Bob-led matter is the equivalent check under the 14-day window that architecture §13.3.20 specifies.

## O3 G-D5 scoping — PASS
Carol and Alice both see Upcoming "Nothing in the diary for the next 14 days" and Next deadline "No upcoming deadlines" (`o3-carol-…`, `o3-alice-…`). The tenant's only court date is on Bob's matter (`o3-db-court-dates.txt`). There is no firm-wide fallback.

## O4 G-D12 mock — PASS
Alice's Team page reads "3 members · 0 pending invitations" (`o4-team-seat-line-e2e.png`).

## O5 G-C1 — PASS (acceptance), edit path not observable
- The Add Party type select offers "Government / organ of state" (`o5-add-party-government-selected.png`).
- "Road Accident Fund 627F" saved with a "Government" badge (`o5-registry-government-badge.png`).
- The Government type filter shows "1 party" (`o5-filter-government-1-party.png`).
- SELECT: `Road Accident Fund 627F | GOVERNMENT | b582646a-cc8e-4653-b077-069d8969213e` (`o5-db-select.txt`).

The edit dialog has no UI entry point. `AdversePartyDialog` is only mounted without a `party` prop, the row menu offers Link to Matter / Delete, and clicking a row opens nothing. This is pre-existing. The edit mode uses the same select component as create, and unit tests cover it.

## O6 layout — PASS
My work at 1280px: scrollWidth 1280 = innerWidth. At 1024px: 1024 = 1024. Upcoming rows stay within the card, are 50px tall, and are readable (`o6-bob-my-work-1024.png`).

## O7 G-D12 Keycloak — PASS
As Thandi (mathebula-partners), with the slice frontend on :3000:
- The seat line reads "3 members · 0 pending invitations"; before it was "5 members (2 pending)" (`o7-kc-team-seat-line-3-members-0-pending.png`).
- The Pending tab shows "No pending invitations / Invited members will appear here", with 0 Revoke buttons (`o7-kc-team-pending-tab-empty.png`).
- Members lists Thandi, Bob and Carol (`o7-kc-team-members-tab-3.png`).
- Backend log: 11:11 `GET "/api/invitations"` (main) vs 11:38 `GET "/api/invitations?status=PENDING"` (slice) (`o7-kc-backend-invitations-requests.txt`).

## Console
e2e: 0 errors on the new build. KC: only HMR websocket refusals while the frontend was being swapped.

## Out-of-scope observations (follow-ups, not in this diff)
- The My work "My Tasks" table clips its Status column at 1280px.
- "See all upcoming" / "+n more" open the Diary List view. That view's window is the calendar month (`diaryWindow`), not the 14-day window, so near a month end some upcoming rows are not behind the link.

## State left
- e2e frontend: the ca82fc0cc build (healthy). Data now includes task cbd9376e, adverse party b582646a and link df94ec27.
- KC frontend: restored to main.
