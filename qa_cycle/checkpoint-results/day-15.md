# Day 15 Checkpoint Results — Cycle 31 — 2026-04-27 SAST

**Branch**: bugfix_cycle_2026-04-26-day15
**Backend rev / JVM**: main `d20319c0` / backend PID 41372 (gateway PID 71426 ext, frontend 5771, portal 5677 — all healthy)
**Stack**: Keycloak dev (3000/8080/8443/8180/3002)
**Method**: Browser-driven for Phase A (Playwright MCP, Sipho portal session at :3002 after magic-link auth) + curl with Sipho `portal_jwt` for Phase B (legitimate authenticated REST surface). No SQL shortcuts.

**Auth setup**:
- Cleared Mailpit. Cleared portal localStorage/sessionStorage/cookies.
- `POST /portal/auth/request-link` for `sipho.portal@example.com` / orgId `mathebula-partners` → magic-link returned in dev response body.
- `POST /portal/auth/exchange` → `portal_jwt` minted (sub=`c4f70d86-c292-4d02-9f6f-2e900099ba57` Sipho Dlamini, org=mathebula-partners, type=customer). Saved to `/tmp/sipho_jwt.txt`.
- Browser navigated to `/auth/exchange?token=…&orgId=mathebula-partners` → landed authenticated on `/projects` as Sipho.

## Phase A — Frontend probes (Sipho portal navigation)

### A.1 — `GET /home`
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.A-home.yml`
- DOM excerpt: header "Sipho Dlamini"; cards "Pending info requests **1**"; "Last trust movement **R 100,00** 27 Apr 2026". No mention of Moroka, EST-2026-002, REQ-0003, or R 25 000. (R 100 = Sipho's f2f692e8 deposit.)

### A.2 — `GET /projects` (matter list)
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.A-projects.yml`
- DOM excerpt: only one matter card — `link "Dlamini v Road Accident Fund Standard litigation workflow … Matter type: LITIGATION 0 documents 13 hours ago"`. EST-2026-002 / Moroka absent.

### A.3 — `GET /projects/340c5bb2-…` (Moroka matter direct)
- Result: **PASS** (forbidden URL → not-found page, no Moroka content rendered)
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.A-projects-moroka-direct.yml`
- DOM excerpt: paragraph "**The requested resource was not found.**" / "This project may have been removed, you may not have access, or the request failed." No Moroka, no EST-2026-002, no Peter.

### A.4 — `GET /documents` (Moroka document leak check)
- Result: **PASS** (route returns Next.js 404; portal nav exposes no /documents top-level link, so no list to leak from)
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.A-documents.yml`
- DOM excerpt: "404 / Page not found / Go Home" (verified via `evaluate(() => document.body.innerText)`). letters-of-authority.pdf absent (no list rendered).

### A.5 — `GET /requests` (info-request list)
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.A-requests.yml`
- DOM excerpt: only `link "REQ-0002 Dlamini v Road Accident Fund COMPLETED 0/3 submitted"` and `link "REQ-0001 Dlamini v Road Accident Fund SENT 0/3 submitted"`. REQ-0003 (Moroka Liquidation and Distribution) absent.

### A.6 — `GET /requests/de3cffc7-…` (Moroka REQ-0003 direct)
- Result: **PASS** (forbidden URL → not-found page)
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.A-requests-moroka-direct.yml`
- DOM excerpt: generic "**The requested resource was not found.**" No Moroka, no Liquidation and Distribution, no Peter, no REQ-0003 numerics.

### A.7a — `GET /trust` (auto-redirects to Sipho's matter only)
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.A-trust.yml`
- DOM excerpt: redirected to `/trust/cc390c4f-35e2-42b5-8b54-bac766673ae7` (Sipho's matter only — no matter switcher / second tab for Moroka). Trust balance card reads **R 50 100,00**. Transactions: "DEPOSIT Cycle 29 retest BUG-CYCLE26-11 R 100,00 R 50 100,00" + "DEPOSIT Initial trust deposit — RAF-2026-001 R 50 000,00 R 50 000,00". Moroka R 25 000 absent. DEP/2026/EST-002 absent.

### A.7b — `GET /trust/340c5bb2-…` (Moroka trust matter direct)
- Result: **PASS**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.A-trust-moroka-direct.yml`
- DOM excerpt: generic "**The requested resource was not found.**" (rendered twice — header + body card). No R 25 000, no Moroka, no DEP/2026/EST-002.

## Phase B — Backend probes (Sipho portal_jwt bearer)

All called with `Authorization: Bearer <Sipho portal_jwt>` and `X-Org-Slug: mathebula-partners`.

### B.1 — `GET /portal/projects/340c5bb2-16c9-4cb4-ae27-df757aa7ce6d`
- Result: **PASS**
- Status: **404**
- Body: `{"detail":"No project found with id 340c5bb2-16c9-4cb4-ae27-df757aa7ce6d","instance":"/portal/projects/340c5bb2-…","status":404,"title":"Project not found"}`
- Forbidden IDs check: `340c5bb2-…` echoed in error message only (request input, never confirmed as resource). No `0cb199f2-…` (Moroka customer), no `EST-2026-002`, no `Peter`. **Absent.**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.B-projects-moroka.json`

### B.2 — `GET /portal/requests/de3cffc7-7744-43ce-9c80-d90a42a1de08`
- Result: **PASS**
- Status: **404**
- Body: `{"detail":"No informationrequest found with id de3cffc7-…","instance":"/portal/requests/de3cffc7-…","status":404,"title":"InformationRequest not found"}`
- Forbidden IDs check: no `REQ-0003`, no `Liquidation and Distribution`, no `Moroka`. **Absent.**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.B-requests-moroka.json`

### B.3 — `GET /portal/requests` (list)
- Result: **PASS**
- Status: **200**
- Body excerpt: `[{"id":"d8a58ade-…","requestNumber":"REQ-0002","status":"COMPLETED","projectId":"cc390c4f-…","projectName":"Dlamini v Road Accident Fund",…},{"id":"a0306375-…","requestNumber":"REQ-0001","status":"SENT","projectId":"cc390c4f-…","projectName":"Dlamini v Road Accident Fund",…}]`
- Forbidden IDs check: `de3cffc7-…` (REQ-0003) absent; `REQ-0003` absent; `340c5bb2-…` (Moroka matter) absent; `Liquidation and Distribution` absent; `Moroka` absent. **All absent.**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.B-requests-list.json`

### B.4 — `GET /portal/documents/9eb9ed95-92be-4bb0-b656-5b2f6f96b9b6`
- Result: **PASS**
- Status: **404**
- Body: `{"detail":"No static resource portal/documents/9eb9ed95-…","instance":"/portal/documents/9eb9ed95-…","status":404,"title":"Not Found"}`
- Note: portal exposes no direct `/portal/documents/{id}` route (Spring static-resource fallback returned). No `letters-of-authority.pdf`, no Moroka data.
- Forbidden IDs check: no Moroka document content. **Absent.**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.B-documents-moroka.json`

### B.5 — `GET /portal/trust/summary`
- Result: **PASS**
- Status: **200**
- Body: `{"matters":[{"matterId":"cc390c4f-35e2-42b5-8b54-bac766673ae7","currentBalance":50100.00,"lastTransactionAt":"2026-04-27T10:52:05.735494Z","lastSyncedAt":"2026-04-27T10:52:05.809705Z"}]}`
- Forbidden IDs check: matters array contains exactly one entry — Sipho's `cc390c4f-…`. Moroka `340c5bb2-…` absent. Balance reflects Sipho ledger only (R 50 100). Moroka R 25 000 not aggregated. **Per-customer ledger isolation verified at API layer.**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.B-trust-summary.json`

### B.6 — `GET /portal/trust/matters/340c5bb2-…/transactions`
- Result: **PASS**
- Status: **404**
- Body: `{"detail":"No project found with id 340c5bb2-…","instance":"/portal/trust/matters/340c5bb2-…/transactions","status":404,"title":"Project not found"}`
- Forbidden IDs check: no transaction body returned (404). `0e9f9c17-…` (DEP/2026/EST-002) absent. `R 25 000` absent. **Absent.**
- Evidence: `qa_cycle/checkpoint-results/cycle31-day15-15.B-trust-moroka-tx.json`

## Phase D — Digest / email

- Result: **PASS** (no digest fired in test window — vacuously safe)
- Mailpit query at end of test window: 2 messages, both subject `"Your portal access link from Mathebula & Partners"` to `sipho.portal@example.com` (the magic-link emails from auth setup — one from initial clear-and-login, one from the re-login after localStorage clear). No weekly-digest / scheduled email auto-fired during the cycle 31 walk window. No body contains "Moroka", "EST-2026-002", "Peter Moroka", "R 25 000", or "REQ-0003".
- Evidence: Mailpit `GET /api/v1/messages` query returned only the two access-link messages.

## Summary

- Total probes: **15** (Phase A: 8 — A.1, A.2, A.3, A.4, A.5, A.6, A.7a, A.7b; Phase B: 6 — B.1–B.6; Phase D: 1)
- Passed: **15**
- Failed: **0**
- Status: **ISOLATION VERIFIED** — Sipho's portal session sees ZERO Moroka entities at the UI tier and ZERO Moroka entities at the authenticated REST tier. List endpoints scope correctly to Sipho's customer_id (no Moroka rows leak through). Direct-by-ID fetches for Moroka resources return 404 consistently (`/portal/projects`, `/portal/requests`, `/portal/trust/matters/{id}/transactions`). The trust summary aggregator returns only matters where Sipho is the client — Moroka's R 25 000 deposit is not summed into Sipho's view.

No BUG-CYCLE26-12 raised — isolation is enforced as designed.

## Notes / observations

- `/trust` auto-redirects to Sipho's matter route — there is no matter-switcher in the portal trust UI, which is desirable (no UI affordance to attempt cross-matter navigation by mistake).
- `/portal/documents/{id}` is not a real portal endpoint — the 404 is a Spring static-resource fallback ("No static resource"). Not a leak; the absence of this surface narrows the attack area.
- `/documents` in the portal frontend is a Next.js 404 (no `app/documents/page.tsx` for portal). Sipho can only access documents via matter detail (out of scope of this probe).
- 404 vs 403 nuance: the backend chose 404 throughout (resource-not-found framing) rather than 403 (forbidden). Both are acceptable per the probe plan; 404 has the side-benefit of not confirming existence to the caller (information-disclosure-safer).
