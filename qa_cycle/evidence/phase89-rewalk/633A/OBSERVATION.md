# 633A — portal task backfill, browser observation (G88-F19)

- **Observed:** 2026-11-05, 04:33–04:35 SAST (02:33–02:35Z), by the post-merge observer agent.
- **Stack:** Keycloak dev stack. Portal :3002 (`next dev`), backend :8080, gateway :8443, frontend :3000, Keycloak :8180, Mailpit :8025 (`b2b-mailpit`). Nothing was restarted or rebuilt by the observer.
- **Backend head:** `841695f40` on `main` (633A, PR #1730).
- **Boot line (backend restarted on the fix by the orchestrator):**
  `Portal task backfill: 2 tenants scanned, 0 skipped (already marked), 0 failed, 26 rows upserted`
- **Browser:** Playwright MCP (Chromium). Viewports 1440x900 and 1024x768.

## DB evidence (gathered by the orchestrator; read-only)

- Global flyway history: V3 "portal backfill markers", success = t.
- `portal.portal_backfill_markers`:
  - `portal_task_backfill` / `tenant_5039f2d497cf` / row_count 26
  - `portal_task_backfill` / `tenant_56399d06a773` / row_count 0
- Firm vs portal task counts for the 5 linked matters (org `mathebula-partners`, `tenant_5039f2d497cf`):

  | Matter | Firm | Portal |
  |---|---|---|
  | Estate Late Peter Moroka | 9 | 9 |
  | Khumalo Property Holdings — Erf 1234 Sandton Transfer | 12 | 12 |
  | Lerato Mthembu — RAF Claim | 9 | 9 |
  | Nomvula Khoza — Debt Recovery | 9 | 9 |
  | Sipho Dlamini v. Standard Bank (civil) | 9 | 9 |

- Note: the backfilled rows are at `sort_order` 0 by design (R7.2). Ordering among them is a recorded follow-up.
- Observer's own read-only SELECT on `portal.portal_tasks` for portal project `d42337ad-2c76-4a79-ac75-e3d9f4d36881` returned 9 rows. All 9 are `status = DONE` and `sort_order = 0`.

## Login

- Portal `/login`: entered `sipho.dlamini@email.co.za` and clicked "Send Magic Link". The response was "Check your email." with no 429.
- Mailpit message `QiMPXEGs7gLozEvcW3ERWt` (02:34:03Z, "Your portal access link from Mathebula & Partners") contained the link `http://localhost:3002/auth/exchange?token=Nxaqw0…&orgId=mathebula-partners`. I navigated to it and landed signed in as "Sipho Dlamini".
- Console: one `401` on `POST http://localhost:8080/portal/auth/exchange` during the exchange, but the session was established and every later page loaded authenticated. This is probably a second exchange call (dev-mode double invocation) against the already-consumed token. Not triaged. The only other console error was `favicon.ico` 404.

## Observation

- **URL:** `http://localhost:3002/matters/d42337ad-2c76-4a79-ac75-e3d9f4d36881`. I requested `/projects/d42337ad-…` and it redirected to `/matters/…`.
- **Heading:** "Sipho Dlamini v. Standard Bank (civil)", badge "Closed".
- **Tasks:** the "Tasks" section is a table (Task / Status / Assignee). It has **9 rows** at both viewports. At 1024 `main table tbody tr` count = 9. The page shows no "N of M" counter; the count comes from the rows.

| # | Task | Status | Assignee |
|---|---|---|---|
| 1 | Letter of demand | DONE | Carol Mokoena |
| 2 | Initial consultation & case assessment | DONE | Bob Ndlovu |
| 3 | Execution -- warrant / attachment | DONE | Unassigned |
| 4 | Post-judgment -- taxation of costs / appeal | DONE | Thandi Mathebula |
| 5 | Trial / hearing attendance | DONE | Thandi Mathebula |
| 6 | Pre-trial conference preparation | DONE | Thandi Mathebula |
| 7 | Discovery -- request & exchange documents | DONE | Unassigned |
| 8 | File plea / exception / counterclaim | DONE | Thandi Mathebula |
| 9 | Issue summons / combined summons | DONE | Thandi Mathebula |

- **1440x900:** 9 rows in the order above. Screenshot: `qa_cycle/evidence/phase89-rewalk/633A/portal-1440-sipho-matter-tasks.png` (full page).
- **1024x768:** the same 9 rows in the same order. No horizontal overflow (scrollWidth 1024 == clientWidth 1024). Screenshot: `qa_cycle/evidence/phase89-rewalk/633A/portal-1024-sipho-matter-tasks.png` (full page).
- The `/matters` list card for this matter shows "1 document · 6 days ago". It shows no task count.

## Notes (not fixed)

- **Ordering:** the rows are not in workflow order. "Letter of demand" comes first and "Issue summons" comes last, with "Execution" and "Post-judgment" before "Trial". This matches the known `sort_order = 0` follow-up (R7.2).
- **Status labels:** the status column shows the raw enum `DONE` in a green pill, not a humanised label like "Done".
- **Assignee column:** it is populated, including "Unassigned" for 2 tasks.

## Verdict

`G88-F19: PASS (observed)`. All 9 tasks for "Sipho Dlamini v. Standard Bank (civil)" are rendered in the client portal at both 1440x900 and 1024x768.

## Side effects

- Sent one portal magic-link email (Mailpit `QiMPXEGs7gLozEvcW3ERWt`) and consumed it.
- No DB writes, no restarts, no source edits, nothing committed. Browser closed after the walk.
