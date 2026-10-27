# Slice 625D — AFTER FIX CYCLE 1 (re-observed on PR #1682 head 146728b9e)

Stack: e2e mock-auth. `e2e-frontend` rebuilt from the worktree at 146728b9e (container healthy before the run; :3001 → 200).
Backend :8081 unchanged. Tenant `tenant_7d218705360b`. Observer = Alice (owner); only logged in, navigated, used
filters/tabs/expanders and typed into filter inputs. No record writes, no SQL writes, no restarts. Screenshots taken by
Playwright MCP into `b2b-strawman/.playwright-mcp/625D-after-fix/` and copied here. Navigation was traced by wrapping
`history.pushState/replaceState` in the page (`window.__nav`).

| # | Check | Verdict | Evidence |
|---|---|---|---|
| 1 | F-1 "Checklist" / "Checklist item" labels, no product "FICA" label | **PASS** | F01, F12 |
| 2 | F-5 Event-type combobox ordering + Enter commits | **PASS** (see observation O-1) | F03, F04, F05, F06 |
| 3 | F-6 search input accessible name | **PASS** | snapshot / evaluate |
| 4 | F-3 Entity ID validation | **PASS** | F07, F08, F09 |
| 5 | F-4 garbage `from`/`to` | **PASS** | F10 |
| 6 | F-7 counts right-aligned; F-10 portal contact label | **PASS** | F01, F02 |
| 7 | Regression re-check | **PASS** | F11, F12, F13, F14, `api-clients-activity-acme.json`, `sql-readonly-crosschecks.txt` |
| 8 | Console | PASS (pre-existing only) | §8 |

## 1. F-1 labels
- Entity type Select options (22): All entity types, Adverse party 4, Billing rate 2, **Checklist 5**, **Checklist item 10**, Client 17,
  Disbursement 14, Document 3, Document for acceptance 16, Fee note 11, Firm settings 2, Generated document 19, Matter 19,
  Matter closure 3, Matter team member 6, Member 3, Pack 5, Security 4, Tax rate 1, Time recording 3, Trust account 1,
  Trust transaction 12. → F01
- Paged all 4 audit-trail pages (160 rows): Entity column tallies Checklist 5 / Checklist item 10 (e.g. "Checklist e5278c50",
  "Checklist item 5831f9d6"); `\bFICA\b` in the tab panel = 0 on every page (a naive /FICA/i scan only hits "certiFICAte").
  Event-type combobox options: 0 containing "FICA".
- Acme Activity tab: the only "FICA" text is the checklist **item's own name** "Checklist item skipped — FICA Risk Assessment"
  (data, from `details.itemName`) and the "FICA & compliance" client tab / sidebar nav (out of scope). Expanded
  "Checklist instance completed" details: no "FICA checklist" string. → F12

## 2. F-5 Event type combobox
- Type `matter`: options in order — **Matter created** (highlighted), Matter created from template, Matter closure closed,
  Matter Closure Override Used, Matter team member added / role changed / removed, Matter updated, and **last**
  `Event key starts with “matter”`. No URL change while typing (`__nav` empty). → F03
- Enter → exactly one pushState `…&eventType=project.created`. → F04
- Type `matter_closure.closed` → single option "Matter closure closed" (prefix item not shown when an exact option matches);
  Enter → one pushState `…&eventType=matter_closure.closed` → **4 total**, all `matter_closure.closed`. → F05
- Type `disbursement.written_off` → Enter → one pushState `…&eventType=disbursement.written_off` → **1 total**. → F06
- Extra: `invoice.voided` (no facet, no rows) → only `Event key starts with “invoice.voided”` offered; Enter commits
  `eventType=invoice.voided` → 0 total, empty-state copy "No audit events in this range…".
- **O-1 (observation, pre-existing backend semantics, not a fix-cycle regression):** `eventType=project.created` returns
  **14 rows** (8 `project.created` + 6 `project.created_from_template`) although the option shows count 8. The backend
  treats `eventType` as a prefix (`AuditEventRepository.java:38/409/431` `e.event_type LIKE CONCAT(:eventTypePrefix,'%')`).
  Any facet key that is a prefix of another key over-matches when picked as an exact option. SQL agrees (8 + 6).

## 3. F-6
`document.activeElement` on opening = `INPUT role=combobox`, `aria-labelledby` → "Search event types", `labels[0]` =
"Search event types"; snapshot renders `combobox "Search event types"` inside the dialog with `listbox "Suggestions"`.

## 4. F-3 Entity ID
- `abc` + Enter → URL unchanged (`__nav` empty), inline error **"Enter a full ID"** (role=alert), input `aria-invalid="true"`,
  `aria-describedby="audit-filter-entity-id-error"`. → F07
- Then WARNING toggle on → `?…&severities=WARNING`; toggle off → `?tab=audit-trail`; Next → `?…&page=1` (Page 2 of 4).
  None of the three navigations carried `entityId=abc`; error + aria-invalid persisted. → F08
- Select-all + Backspace → value "", error gone, `aria-invalid`/`aria-describedby` removed.
- `7b7e7dde-15db-4105-a38c-9da37c3189e5` + Enter → exactly one pushState `…&entityId=7b7e7dde-…` (page param dropped);
  a following Tab added no navigation → **4 total** (INV-0003 partially_paid/sent/approved + created "Fee note 7b7e7dde"). → F09

## 5. F-4
`?tab=audit-trail&from=garbage&to=2026-02-30T00:00:00Z` → renders normally, title "Firm — Audit trail", no
"Unable to load settings"/"Something went wrong", date inputs blank, **160 total** (invalid dates dropped). → F10

## 6. F-7 / F-10
- Entity type Select: every count's right edge at x=790 within options whose right edge is 822 (same column for all rows). → F01
- Actor Select: groups Members (Alice Owner 116, Bob Admin 21, Carol Member 6) and Portal contacts (**"Portal contact (65f788e3)" 4**);
  counts right edge x=1338 for all rows. → F02
- Note: the table's Actor *cell* for portal rows still reads "Portal Contact" (row actor name) — F-10 scoped to the options.

## 7. Regression
- Alice + Matter (`actorId=2e38845d-…&entityType=project`) → **19 total**: project.created 8, project.created_from_template 6,
  matter_closure.closed 4, project.updated 1; triggers show "Alice Owner" / "Matter". Read-only SQL identical. → F11
- Entity column: "Fee note INV-0003" (partially_paid/sent/approved) and "Fee note 7b7e7dde" (invoice.created) on page 1.
- Acme Activity tab: **15 rows**, same order/content as `GET /api/clients/b6dc030f-…/activity` (totalElements 15,
  `api-clients-activity-acme.json`), incl. "Client lifecycle transitioned (ONBOARDING → ACTIVE)" and item-name suffixes
  ("Checklist item completed — Conflict Check Performed" etc.). Loader-ref change: activity server-action fetch count stayed
  at 2 over a 5 s idle window (no refetch loop). → F12
- Overflow: 1280 → `documentElement.scrollWidth` 1280 / innerWidth 1280 (table wrapper 798/646 scrolls inside its card);
  1440 → 1440 / 1440 (wrapper 806/806). → F13, F14

## 8. Console
- Audit trail: one React #418 per full load (pre-existing). RSC-prefetch 404s from Entity-column links:
  `/trust-accounting/transactions/{id}`, `/matters/{closureId}/closure`, and also **`/documents/{id}`** (Document rows on page 2;
  `frontend/app/(app)/org/[slug]/documents/` has no page/[id]). Same `ENTITY_ROUTES` on main b56371a2c → pre-existing, not
  introduced by 625D (extends prior defect #1 with the `document` route).
- Acme Activity tab: 0 console messages.

## New defects
None introduced by the fix cycle. Carried/observations: O-1 prefix over-match on exact option pick (backend semantics);
dead Entity-column routes now known to include `document` (pre-existing).
