# Day 15 — **Isolation check** — Sipho cannot see Moroka's data  `[PORTAL]`

Cycle: 1 | Date: 2026-04-22 04:20 SAST | Auth: Portal JWT (magic-link) | Portal: :3002 | Backend: :8080 | Actor: Sipho Dlamini

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 15 (checkpoints 15.1–15.20) — Phases A (list leak), B (direct-URL), C (frontend nav), D (digest/email scan).

## Verdict

**ISOLATION: PASSED** — 8 Phase A API probes (all 404), 7 Phase B list probes (zero Moroka tokens), 8 Phase C frontend URL probes (all denied/clean), 13 Phase D emails strictly addressed To:Sipho scanned (zero Moroka references). **Total: 36 probes, 36 PASS, 0 FAIL, 0 BLOCKER.** Zero cross-tenant leaks observed at backend, list, frontend, or email layer.

## Pre-flight

- Services verified UP: backend `{"status":"UP"}`, portal 307, frontend 200, gateway UP, Mailpit 200.
- Sipho session: **Fresh magic-link minted via `POST /portal/auth/request-link`** (email `sipho.portal@example.com`, orgId `mathebula-partners`) → token `<redacted-token>` → exchanged via `POST /portal/auth/exchange` → portal JWT issued. Decoded claims: `sub=8fe5eea2-75fc-4df2-b4d0-267486df68bd` (Sipho's customerId) / `type=customer` / `org_id=mathebula-partners` / exp 1h. `/portal/me` positive-control returns Sipho's portal contact `d9ecf332-e9cc-4296-9652-d29171a4adb6`. No stale-session workaround needed.

## Phase A — Direct API probes on Moroka entities (Sipho JWT targeting Moroka IDs)

| # | Probe | Expected | Observed | Verdict |
|---|---|---|---|---|
| A1 | `GET /portal/projects/4e87b24f-cf40-4b5b-9d1e-59a63fdda55a` | 403/404 | **404** `{"title":"Project not found"}` | PASS |
| A2 | `GET /portal/projects/4e87b24f-…/tasks` | 403/404 | **404** `{"title":"Project not found"}` | PASS |
| A3 | `GET /portal/projects/4e87b24f-…/summary` | 403/404 | **404** `{"title":"Project not found"}` | PASS |
| A4 | `GET /portal/requests/4a698dc9-b24a-45b4-a44b-bf7c0d6aceac` | 403/404 | **404** `{"title":"InformationRequest not found"}` | PASS |
| A5 | `GET /portal/trust/matters/4e87b24f-…/transactions` | 403/404 | **404** `{"title":"Matter not found"}` | PASS |
| A6 | `GET /portal/trust/matters/4e87b24f-…/statement-documents` | 403/404 | **404** `{"title":"Matter not found"}` | PASS |
| A7 | `GET /portal/deadlines/project/4e87b24f-…` | 403/404 | **404** `{"title":"Deadline not found"}` | PASS |
| A8 | `GET /portal/projects/4e87b24f-…/comments` | 403/404 | **404** `{"title":"Project not found"}` | PASS |

**Phase A: 8/8 PASS.** Backend correctly refuses every Moroka-targeted lookup with Sipho's JWT.

_Note on 404 vs 403:_ The scenario accepts either 404 or 403. Backend uses "404 for not-found-or-access-denied" as the security-by-obscurity pattern documented in `backend/CLAUDE.md`. This is canonical and intentional. No isolation leak (200 with Moroka data was never observed on any probe).

_Note on A5/A6 response title "Matter not found":_ Slight title inconsistency (prior probes say "Project not found"; trust endpoints say "Matter not found") but response payload contains only the error text and no Moroka data. Not a new gap — existing terminology drift.

## Phase B — List/index endpoint scan for cross-tenant bleed

Leak-token scanlist: `Moroka`, `Peter Moroka`, `EST-2026-002`, `REQ-0004`, `DEP-2026-002`, `25 000`, `25000`, `75 000`, `75000`, `4e87b24f-…`, `29ef543a-…`, `0f980d16-…`, `4a698dc9-…`, `1e96f979-…`, `f2cea65b-…`, `Liquidation`, `Distribution`.

| # | Probe | Response | Leak tokens found | Verdict |
|---|---|---|---|---|
| B1 | `GET /portal/projects` | 200 `[]` | NONE | PASS |
| B2 | `GET /portal/requests` | 200 — 3 items (REQ-0001, REQ-0002, REQ-0003 — all `projectId=40881f2f-…` "Dlamini v Road Accident Fund") | NONE (no REQ-0004, no `4a698dc9`, no `Moroka`) | PASS |
| B3 | `GET /portal/trust/summary` | 200 `{"matters":[]}` | NONE | PASS |
| B4 | `GET /portal/invoices` | 200 `[]` | NONE | PASS |
| B5 | `GET /portal/deadlines` | 200 `[]` | NONE | PASS |
| B6 | `GET /portal/retainers` | 404 `"Retainers not available"` | NONE | PASS (feature-off, not a leak) |
| B7 | `GET /portal/acceptance-requests/pending` | 200 `[]` | NONE | PASS |

**Phase B: 7/7 PASS.** Zero Moroka IDs, Moroka client names, Moroka matter refs, or R 25 000 / aggregated R 75 000 appear in any list response.

## Phase C — Frontend URL + navigation probes (browser with Sipho's portal_jwt in localStorage)

| # | URL | Expected | Observed | Leak tokens in `document.body.innerText` | Verdict |
|---|---|---|---|---|---|
| C1 | `GET http://localhost:3002/home` | Sipho's home cards only | Header "Sipho Dlamini" + 3 empty cards (0 deadlines / No invoices / No trust activity) | NONE | PASS |
| C2 | `GET /projects/4e87b24f-…` (Moroka matter) | 403/404/redirect | **"The requested resource was not found."** error card (portal chrome retained) | NONE | PASS |
| C3 | `GET /requests/4a698dc9-…` (Moroka request) | 403/404 | **"The requested resource was not found"** | NONE | PASS |
| C4 | `GET /projects` | only Sipho matters | "No projects yet" empty state (GAP-P-03 re-observation — Sipho's own matter also empty, but no Moroka leak either) | NONE | PASS |
| C5 | `GET /trust` | only Sipho balance | "No trust activity on your matters" (GAP-L-52 mask — but no R 25 000 / R 75 000 leak) | NONE | PASS |
| C6 | `GET /requests` | only Sipho requests | 3 cards: REQ-0003 SENT / REQ-0002 COMPLETED / REQ-0001 SENT — all "Dlamini v Road Accident Fund" | NONE (no REQ-0004, no Moroka, no Liquidation) | PASS |
| C7 | `GET /invoices`, `/proposals`, `/deadlines`, `/profile` | Sipho-only | All empty states or Sipho's own profile | NONE | PASS (4 pages combined) |
| C8 | `GET /documents` | Sipho-only | 404 Page not found (route missing — separate gap, but no leak) | NONE | PASS |

**Phase C: 8/8 PASS.** No Moroka data renders anywhere in Sipho's portal UI.

Screenshots:
- `day-15-portal-home-isolated.png` — Sipho's `/home` clean, "Sipho Dlamini" chrome, 3 empty cards.
- `day-15-portal-moroka-matter-denied.png` — direct-URL `/projects/4e87b24f-…` renders "The requested resource was not found" + "Try again / Back to projects".
- `day-15-portal-requests-isolated.png` — `/requests` list showing only Sipho's 3 REQ cards (all Dlamini v RAF).

## Phase D — Digest / notification / activity-feed scan for cross-entity references

Method: fetch all Mailpit messages, strictly filter by envelope `To` address matching `sipho.portal@example.com` (not a loose body-text search), then scan HTML + text for Moroka-leak tokens.

| Metric | Value |
|---|---|
| Loose `to:"sipho.portal@example.com"` query | 15 messages |
| **Strict-match** (To header contains sipho.portal@example.com) | **13 messages** |
| Messages matching the loose query but addressed To Moroka (false-positive candidates) | 2 (REQ-0004 dispatch + access-link — both explicitly `To: moroka.portal@example.com`) |
| **Leaks in emails strictly addressed To Sipho** | **0** |

Email inventory (To: Sipho, chronological, all CLEAN):

1. `WhoCHWwXSJCF8PmyY4wHNN` 2026-04-22T04:15 — "Your portal access link" (from this Day 15 re-auth)
2. `HvtnPLLGYp5EMtEKS6aUeP` 2026-04-22T00:52 — "Your portal access link"
3. `TAsQcNY52ibhohG3Y6EPgw` 2026-04-21T23:18 — "Confirmed: You have accepted engagement-letter-…"
4. `kmxzpCRtd42hvkvQG8ycqB` 2026-04-21T23:17 — "Document for your acceptance: engagement-letter-…"
5. `jtrVksK2HnwPVj9KCDDuP6` 2026-04-21T22:44 — "Document for your acceptance: engagement-letter-…"
6. `dCFdahuNmaYUU5zsoPDq8x` 2026-04-21T22:35 — "Information request REQ-0003"
7. `9jK9caSfickhxvr82PrzBL` 2026-04-21T22:35 — "Your portal access link"
8. `7meN9Wha9u7o8kbg6DYZBE` 2026-04-21T22:09 — "Request REQ-0002 completed"
9. `A5p9gaTZF7rwekv3SjaDyV` 2026-04-21T22:09 — "Item accepted — Bank statement"
10. `fdrDQgrUMwWCZv25Xzz5T8` 2026-04-21T22:09 — "Item accepted — Proof of residence"
11. `T4bBQgCoCGoZYYWAZsDZFQ` 2026-04-21T22:09 — "Item accepted — ID copy"
12. `JX8VFPvXNpoeNbfhRbxGAz` 2026-04-21T21:59 — "Information request REQ-0002"
13. `iM2SFNnkDe382dMk7sCXRQ` 2026-04-21T21:59 — "Your portal access link"

**Phase D: 13/13 PASS.** Every email content references only Sipho and his RAF matter; no cross-tenant bleed to Sipho's inbox. No weekly digest has fired yet (expected — script sets digests at Day 14 cadence).

Scenario 15.19 (`/profile` activity view): `/profile` renders only Sipho's contact card (Name/Email/Role/Customer), no activity feed exposed in portal — consistent with scenario's "if portal exposes" hedge. No Moroka leak.
Scenario 15.20 (digest emails): none delivered yet — `Weekly` cadence default, first digest would land after Day 14 in scenario time; nothing to inspect. No leak to check against.

## Day 15 rollup

- [x] List views on /home, /projects, /trust, /invoices, /deadlines, /proposals show ONLY Sipho's data — **PASS**
- [x] Direct-URL probes to 4+ Moroka entity IDs denied at the frontend — **PASS** (2 URL-level probes + 8 API-level probes — 10 total)
- [x] API-level probes to 4+ Moroka endpoints denied at backend (403/404, never 200) — **PASS** (8/8 — all 404)
- [x] Trust balance card shows only Sipho's — **PASS** (shows empty due to GAP-L-52, but never Moroka's R 25 000 and never aggregated R 75 000)
- [x] Activity trail / digest have zero Moroka references — **PASS** (13 To:Sipho emails scanned, 0 leaks)

## Re-observed / carry-forward

| Existing item | Re-observed? | Note |
|---|---|---|
| GAP-L-52 (portal trust empty for Sipho deposits) | YES | `/trust` shows empty state even with Sipho's own R 50 000 recorded. Did NOT invalidate isolation probe — the R 25 000 Moroka deposit also does not appear, and the backend `/portal/trust/summary` returns `{"matters":[]}` for Sipho. Since L-52 is already an OPEN BLOCKER, no new gap. |
| GAP-P-03 (portal /projects shows "No projects yet" for Sipho) | YES | `/projects` and `GET /portal/projects` both empty for Sipho. Existing MED gap. Did not invalidate isolation probe — Moroka matter also absent. |
| GAP-L-43 (request-item submission projection) | N/A | Not exercised this turn. |
| `/documents` route missing on portal | YES | 404 Page not found for `/documents`. Minor — portal sidebar links to `/documents` but no route. New LOW gap **GAP-P-07** (see below). |
| 404 vs 403 consistency on Moroka probes | MINOR | A5/A6 return `title:"Matter not found"` while A1/A2/A3/A8 return `title:"Project not found"`. Minor terminology drift; not a leak; tracked as **GAP-L-55** (LOW, cosmetic). |

## New gaps (this turn)

| GAP_ID | Severity | Summary |
|--------|----------|---------|
| **GAP-P-07** | LOW | Portal sidebar has "Documents" nav link pointing to `/documents`, but the route returns a Next.js 404 "Page not found" page. Not a security/isolation issue — just a broken nav link. Scenario 15.11 expects `/documents/{id}` to be denied, which it effectively is (404 via missing route); isolation is still satisfied. Owner: portal-fe — either ship `/documents` list page OR remove the sidebar link until it's built. Off critical path. |
| **GAP-L-55** | LOW | Portal backend error title inconsistency on trust endpoints: `GET /portal/trust/matters/{id}/…` returns `"title":"Matter not found"` while other `/portal/projects/{id}` endpoints return `"title":"Project not found"`. Same controller family, different error-message string. Cosmetic. Not a leak. Owner: backend — align the `ResourceNotFoundException` message used in `PortalTrustController` with the shared "Project" terminology used elsewhere in portal controllers. |

## Halt reason

Day 15 (all Phases A–D) complete with zero isolation leaks. Per rule: if no BLOCKER found, QA proceeds. Orchestrator decides next dispatch (per task brief: "Do NOT skip ahead to Day 21+ (orchestrator's call)").

## QA Position on exit

`Day 21 — 21.1` (orchestrator's next dispatch per task brief — Day 15 PASSED, unblocks Day 21 firm-side time logging).

## Next-turn recommendation

Orchestrator can dispatch **Day 21** (firm-side time entry + disbursements + court date on RAF-2026-001). No isolation-related blocker. GAP-L-52 and GAP-P-03 remain OPEN but neither blocks Day 21 (firm-side flow). Suggest deferring L-52/P-03 fixes until portal-side Days (30/46/61/75) reopen — or batch all portal read-model gaps together after Day 85. GAP-L-55 + GAP-P-07 are LOW cosmetic; batch later.

## Evidence summary

- Sipho JWT (short-lived, decoded for evidence): `sub=8fe5eea2-75fc-4df2-b4d0-267486df68bd` (customer), `org_id=mathebula-partners`, `type=customer`, exp `1776834960` (1h from 04:16 SAST).
- Backend 4xx on every Moroka-id probe (8 paths × Sipho JWT): all **404**, zero 200-with-Moroka-data.
- List endpoints (`/portal/requests`, `/portal/trust/summary`, etc.): zero Moroka IDs or strings in response bodies.
- Frontend direct-URL probes on 2 Moroka URLs: error card shown, zero Moroka data rendered.
- Mailpit scan: 13 emails strictly To Sipho, zero Moroka tokens in HTML or Text body.

## Screenshots

- `day-15-portal-home-isolated.png` — Sipho's clean home.
- `day-15-portal-moroka-matter-denied.png` — "Resource not found" on `/projects/[moroka-matter-id]`.
- `day-15-portal-requests-isolated.png` — `/requests` list showing only Sipho's 3 REQs.

---

## Day 15 Re-Verify — Cycle 1 — 2026-04-25 SAST

Cycle: 1 (verify) | Date: 2026-04-25 SAST | Auth: Sipho's portal JWT (already-live in Tab 1 localStorage) | Portal: :3002 | Backend: :8080 | Actor: Sipho Dlamini

**Method**: Playwright MCP browser-driven for Phase 15.1 + 15.2 (list-view leak probes + direct-URL hard-negative probes); REST `curl` for Phase 15.3 (API hard-negative — API is the SUT per dispatch); read-only SQL SELECT for Phase 15.4. Tab 1 Sipho portal session preserved untouched on entry; JWT extracted from `localStorage.portal_jwt` (decoded: `sub=c3ad51f5-… (Sipho's customerId), type=customer, org_id=mathebula-partners, exp 1777118542 = 2026-04-25 12:02:22 UTC ≈ 50 min remaining`). Tab 0 Bob firm session left intact (untouched).

### Probe IDs (cycle-1, on `tenant_5039f2d497cf`)

```
Moroka customer_id          : 2b454c42-ac4e-4e96-af64-4f3d2a409d45
Moroka matter_id (project)  : 89201af5-f6e0-4d9a-952e-a2af6e5b70ee  (EST-2026-002)
Moroka info-request_id      : 83428106-0e6e-4550-acc7-bdd184fd727f  (REQ-0005 Liquidation pack)
Moroka document_id          : 8d92037c-b1de-4b3d-9016-034d27cd032b  (death-certificate-moroka.pdf)
Moroka trust_tx_id          : 446fa97c-8d8d-43f2-b4be-0e7c0ef8af95  (R 25 000,00 DEPOSIT)
Moroka client_ledger_card   : 182621ca-57d2-423b-a998-434b518b4db6
```

### Phase 15.1 — List-view leak probe (browser-driven)

| # | URL | Expected | Observed | Leak tokens | Verdict |
|---|---|---|---|---|---|
| 15.1a | `/home` | Sipho-only | Empty cards (deadlines/invoices/trust nudges); chrome shows "Sipho Dlamini" | NONE | PASS |
| 15.1b | `/projects` | Only Sipho's matters | 3 matters: `Dlamini v Road Accident Fund` (`e788a51b-…`), `L-37 Regression Probe` (`af9b14b2-…`), `L-37 Conveyancing Probe` (`db5ff54a-…`). DB confirms ALL 3 bound to Sipho's `customer_id=c3ad51f5-…`. Moroka matter `89201af5-…` ABSENT. | NONE | PASS |
| 15.1c | `/requests` | Only Sipho's REQs | REQ-0001/REQ-0002/REQ-0003/REQ-0004 — all "Dlamini v Road Accident Fund" projectName. REQ-0005 (Moroka Liquidation pack) ABSENT. | NONE | PASS |
| 15.1d | `/trust` | Only Sipho's matter | Auto-redirect to `/trust/e788a51b-…` (Sipho's RAF matter). Trust balance card R 50 000,00; transactions table 1 row "25 Apr 2026 / DEPOSIT / Initial trust deposit — RAF-2026-001 / R 50 000,00". Moroka R 25 000,00 ABSENT; aggregate R 75 000,00 not shown. | NONE | PASS |

Snapshots: `day-15-cycle1-list-home.yml`, `day-15-cycle1-list-projects.yml`, `day-15-cycle1-list-requests.yml`, `day-15-cycle1-list-trust.yml`. Token-grep across all 4 yml for `Moroka|REQ-0005|Liquidation|Estate Late|2b454c42|89201af5|83428106|8d92037c|446fa97c|182621ca|25 000|75 000|death-cert` returned 0 hits per file.

**Phase 15.1 result: 4/4 PASS — zero Moroka tokens visible to Sipho on any list view.**

### Phase 15.2 — Direct-URL hard negative (browser-driven, Moroka IDs)

| # | URL | Expected | Observed | Leak tokens | Verdict |
|---|---|---|---|---|---|
| 15.2a | `/projects/89201af5-…` (Moroka matter) | 404/403/denial | Renders portal chrome with denial card: **"The requested resource was not found."** + "This project may have been removed, you may not have access, or the request failed." Buttons: "Try again" / "Back to projects" | NONE | PASS |
| 15.2b | `/requests/83428106-…` (Moroka REQ-0005) | 404/403/denial | Renders portal chrome + **"The requested resource was not found."** | NONE | PASS |
| 15.2c | `/trust/89201af5-…` (Moroka matter trust) | 404/403/denial | Renders portal chrome + "Back to trust" link + **"No trust balance is recorded for this matter."** + Transactions: "The requested resource was not found." + Statements: "The requested resource was not found." NO R 25 000,00 anywhere. | NONE | PASS |
| 15.2d | `/documents/8d92037c-…` (Moroka document) | 404/403/denial | Next.js **"404 Page not found"** (route doesn't exist on portal — same outcome as cycle-0 GAP-P-07; functionally a denial) | NONE | PASS |

Snapshots: `day-15-cycle1-direct-project.yml`, `day-15-cycle1-direct-request.yml`, `day-15-cycle1-direct-trust.yml`, `day-15-cycle1-direct-document.yml`. Screenshot evidence: `day-15-cycle1-direct-project-denial.png` (denial card on Moroka matter URL).

**Phase 15.2 result: 4/4 PASS — every direct-URL probe denied; zero Moroka data rendered.**

### Phase 15.3 — API hard negative (REST allowed — API is the SUT)

JWT extracted from Sipho's portal session via `browser_evaluate(() => localStorage.portal_jwt)`. Curl probes against `localhost:8080/portal/...` and `localhost:8080/api/...`:

| # | Endpoint | Expected | HTTP | Body (first 200 chars) | Leak | Verdict |
|---|---|---|---|---|---|---|
| Pos-control | `GET /portal/projects/e788a51b-…` (Sipho's own RAF) | 200 | **200** | `{"id":"e788a51b-…","name":"Dlamini v Road Accident Fund","status":"ACTIVE",…}` | N/A | PASS (control proves JWT works) |
| 15.3a | `GET /portal/projects/89201af5-…` (Moroka matter) | 403/404 | **404** | `{"detail":"No project found with id 89201af5-…","title":"Project not found"}` | NONE | PASS |
| 15.3b | `GET /portal/requests/83428106-…` (Moroka REQ-0005) | 403/404 | **404** | `{"detail":"No informationrequest found with id 83428106-…","title":"InformationRequest not found"}` | NONE | PASS |
| 15.3c | `GET /portal/trust/matters/89201af5-…/transactions` | 403/404 | **404** | `{"detail":"No project found with id 89201af5-…","title":"Project not found"}` | NONE | PASS |
| 15.3d | `GET /portal/documents/8d92037c-…` (Moroka doc) | 403/404 | **404** | `{"detail":"No static resource portal/documents/8d92037c-…","title":"Not Found"}` | NONE | PASS |
| 15.3e | `GET /portal/projects/89201af5-…/tasks` | 403/404 | **404** | `{"detail":"No project found with id 89201af5-…","title":"Project not found"}` | NONE | PASS |
| 15.3f | `GET /api/projects/89201af5-…` (firm endpoint w/ portal JWT) | 401/403 | **401** | (empty — backend correctly rejects portal JWT against firm scope) | NONE | PASS |
| 15.3g | `GET /portal/requests` (Sipho's own list) | 200, no Moroka REQ-0005 | **200** | 4 items: REQ-0004/REQ-0003/REQ-0002/REQ-0001 — ALL `projectName: "Dlamini v Road Accident Fund"` and `projectId: e788a51b-…`. NO REQ-0005, NO `83428106`, NO `Moroka`, NO `Liquidation`. | NONE | PASS |
| 15.3h | `GET /portal/projects` (Sipho's own list) | 200, no Moroka matter | **200** | 3 items: `db5ff54a-…/L-37 Conveyancing Probe`, `af9b14b2-…/L-37 Regression Probe`, `e788a51b-…/Dlamini v Road Accident Fund`. NO `89201af5`, NO `Moroka`, NO `Estate Late`. (DB confirms all 3 ids belong to `customer_id=c3ad51f5-…` Sipho.) | NONE | PASS |
| 15.3i | `GET /portal/trust/summary` (Sipho's own) | 200, no Moroka matter | **200** | `{"matters":[{"matterId":"e788a51b-…","currentBalance":50000.00,…}]}` — exactly 1 matter, balance R 50 000,00. NO `89201af5`, NO `25000`, NO `446fa97c`. | NONE | PASS |

**Phase 15.3 result: 9/9 PASS (1 positive-control + 8 hard-negatives). Backend correctly enforces tenant + customer-scope authorization.**

### Phase 15.4 — Activity trail (read-only SELECT)

```sql
SELECT event_type, entity_type, entity_id, actor_type, source, occurred_at
FROM tenant_5039f2d497cf.audit_events
WHERE actor_id = 'c3ad51f5-…' /* Sipho */
   OR entity_id IN ('89201af5-…','83428106-…','8d92037c-…','446fa97c-…') /* Moroka */
ORDER BY occurred_at DESC LIMIT 20;
```

6 rows returned — ALL `actor_type=USER, source=API` (i.e., Bob the firm admin creating Moroka entities on Day 14). Zero rows with Sipho as actor against Moroka entities. Audit layer does not currently log denied/failed access attempts (existing pattern, not a new gap), but importantly there is no record of Sipho ever successfully retrieving Moroka data.

**Phase 15.4 result: PASS — zero Sipho-actor entries against Moroka entities.**

### Day 15 cycle-1 rollup

| Checkpoint | Result |
|---|---|
| 15.1 List-view leak (4 list pages, browser) | **4/4 PASS** |
| 15.2 Direct-URL hard negative (4 URLs, browser) | **4/4 PASS** |
| 15.3 API hard negative (8 probes + 1 control, curl) | **9/9 PASS** |
| 15.4 Activity trail (read-only SELECT) | **PASS** |
| **Total cycle-1** | **18 probes — 18 PASS — 0 FAIL — 0 BLOCKER** |

**ISOLATION HOLDS at all four layers (UI list, UI direct-URL, API, audit). L-52 portal trust ledger does NOT leak Moroka's R 25 000,00 deposit into Sipho's view (Sipho sees only his own R 50 000,00). No new gaps. Cycle-1 verify CLEAN.**

### Tab state on exit

- Tab 0 — Bob firm session at `localhost:3000/org/mathebula-partners/projects/89201af5-…` — **untouched**.
- Tab 1 — Sipho portal session at `localhost:3002/projects/89201af5-…` (last-visited Moroka URL, denial card rendered) — JWT still live (~50 min remaining); can be reused or logged out at orchestrator's discretion.

### Evidence files

- `day-15-cycle1-list-home.yml`, `day-15-cycle1-list-projects.yml`, `day-15-cycle1-list-requests.yml`, `day-15-cycle1-list-trust.yml`
- `day-15-cycle1-direct-project.yml`, `day-15-cycle1-direct-request.yml`, `day-15-cycle1-direct-trust.yml`, `day-15-cycle1-direct-document.yml`
- `day-15-cycle1-direct-project-denial.png`
