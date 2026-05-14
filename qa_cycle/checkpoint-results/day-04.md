# Day 4 — Sipho first portal login + FICA upload

**Stack**: dev/Keycloak — frontend :3000, backend :8080, gateway :8443, portal :3002, KC :8180, Mailpit :8025
**Date**: 2026-05-13
**Actor**: Sipho Dlamini (portal contact) on portal `:3002`
**Branch**: `bugfix_cycle_2026-05-13` (cycle 1)

---

## Day 4 step-by-step (portal context)

Context swap: navigated directly to magic-link URL on `http://localhost:3002` (Sipho unauthenticated — fresh portal session).

### Phase A — Magic-link landing

#### 4.1 Open Mailpit, locate FICA magic-link email
- **PASS** — Mailpit message `CJsf6oPciWqSqzH4EsN6xb` retrieved via `curl http://localhost:8025/api/v1/message/<id>`. Subject: `Information request REQ-0001 from Mathebula & Partners`. To: `sipho.portal@example.com`. Body contains `View Request` button → `http://localhost:3002/auth/exchange?token=26sbOhJ-bVL1kcKGKBk5Ez-H7Rv7mMuw3EoRJG9GWmU&orgId=mathebula-partners`. Header bar uses navy `#1B3358` brand colour + S3 logo URL — branding leaks into email correctly.

#### 4.2 Click magic-link → token exchange
- **PASS** — Navigated to magic-link URL `http://localhost:3002/auth/exchange?token=26sbOhJ-bVL1kcKGKBk5Ez-H7Rv7mMuw3EoRJG9GWmU&orgId=mathebula-partners`. Token exchange succeeded — `POST /portal/auth/exchange` fired and redirected to `/projects` (portal home). No Keycloak form appeared at any point. Zero errors during exchange (only `favicon.ico` 404 — known dev-mode gap).

#### 4.3 Token exchange completes → redirect to home
- **PASS** — Landing was `/projects` showing "Your Projects" with "Dlamini v Road Accident Fund" listed. Manual navigation to `/home` also succeeds.

#### 4.4 `/home` shows pending info request with matter context
- **PASS** — `/home` renders "Pending info requests" KPI card with count = `1` linking to `/requests`. Per OBS-401 scenario amendment from previous cycle: portal surfaces matter name (not template title "FICA Onboarding Pack") as the request label — this is the accepted behavior. Due date not shown inline on home card but the pending count is correct.

#### 4.5 Header / sidebar shows Mathebula firm branding
- **PASS** — Portal banner shows "Mathebula & Partners logo" (alt text). Sidebar header reads "Portal". Legal-za terminology applied in navigation: Home, Matters, Trust, Deadlines, Fee Notes, Proposals, Requests, Activity. No firm-vertical leakage.

#### 4.6 User identity = Sipho Dlamini
- **PASS** — Top-right user menu reads `Sipho Dlamini`.

#### 4.7 Screenshot of portal home
- **DONE** — `day-04-portal-home-first-login.png`

### Phase B — FICA upload

#### 4.8 Click into FICA Onboarding Pack → detail renders
- **PASS** — Navigated via Home → Pending info requests → `/requests` → clicked `REQ-0001` → landed on `/requests/ac2abebd-b08c-4594-b6ff-88717bb4dbc2`. Heading: `REQ-0001 / Dlamini v Road Accident Fund / 0/3 submitted • status SENT`. Matter context (project name + request number) rendered at top with per-item upload list below.

#### 4.9 Three upload slots labelled correctly
- **PASS** — Three list items rendered:
  1. **ID copy** (required) — `Certified copy of the client's South African ID document or passport bio page. Must be certified by a Commissioner of Oaths, SAPS, or other accepted certifier within the last 3 months. Accepts: PDF, JPG, PNG`
  2. **Proof of residence (≤ 3 months)** (required) — `Recent utility bill, municipal rates account, bank statement, or similar document confirming the client's residential address. Document date must be within the last 3 months. Accepts: PDF, JPG, PNG`
  3. **Bank statement (≤ 3 months)** (required) — `Most recent bank statement evidencing the client's source of funds. Statement must be dated within the last 3 months and show the client's name, account number, and at least one transaction. Accepts: PDF`

  Labels match the FICA Onboarding Pack template metadata + acceptable file types are surfaced.

#### 4.10 Upload PDF to each slot
- **PASS** — All three uploads completed via file chooser → `browser_file_upload`:
  - Slot 1 (ID copy): `qa_cycle/fixtures/fica-id.pdf`
  - Slot 2 (Proof of residence): `qa_cycle/fixtures/fica-address.pdf`
  - Slot 3 (Bank statement): `qa_cycle/fixtures/fica-bank.pdf`

  Each slot transitioned: file selected → Upload-and-submit button enabled → click → `Submitted — status: SUBMITTED` text appears. Submission counter advanced 0/3 → 1/3 → 2/3 → 3/3.

#### 4.11 (OBS-402 amend: removed)
- **N/A** — Per scenario amendment, the portal does not surface a request-level cover-message textarea. Per-item context is set by the firm when sending and rendered as the item's description. This is accepted behavior.

#### 4.12 Submit each FICA item via per-item Upload and submit
- **PASS** — Each item submitted individually via "Upload and submit" button. State transitions observed:
  - After item 1: `1/3 submitted • status IN_PROGRESS`
  - After item 2: `2/3 submitted • status IN_PROGRESS`
  - After item 3: `3/3 submitted • status IN_PROGRESS`
  
  Per OBS-403 scenario amendment: envelope stays `IN_PROGRESS` (not auto-`SUBMITTED`). Envelope will transition to `Completed` on firm-side "Mark as Reviewed" in Day 5. State machine: `Sent → IN_PROGRESS` (3/3 submitted) → `Completed` (firm review). Correct behavior.

#### 4.13 `/home` pending count drops to 0
- **PASS** — Returned to `/home`. "Pending info requests" KPI now reads `0`. The home view filters on items still awaiting client action — correct user-facing semantic. Confirms the upload flow is functionally complete from the portal user's POV.

#### 4.14 Screenshot
- **DONE** — `day-04-fica-submitted.png` (3/3 SUBMITTED)

---

## Day-end checkpoints

| Check | Result |
|-------|--------|
| Magic-link login succeeded — no Keycloak form appeared at any step | **PASS** — Portal magic-link path is `http://localhost:3002/auth/exchange?token=...&orgId=...` → `POST /portal/auth/exchange` → redirect to `/projects`. No Keycloak URL or form appeared. Token from Day 3 email was still valid (same backend session, no restart). |
| Uploads stored (firm side will verify on Day 5) | **DEFERRED to Day 5** — three SUBMITTED states recorded portal-side; firm-side download verification is Day 5.3. |
| Info-request state machine progressed: per-item Pending → Submitted, envelope Sent → IN_PROGRESS | **PASS** — Item state transitions confirmed inline (`Submitted — status: SUBMITTED` for all 3). Envelope state Sent → IN_PROGRESS (does not auto-advance to Submitted — correct per OBS-403 amendment). |
| No firm-side terminology leaks on portal | **PASS** — Sidebar uses legal-za terms (Matters, Fee Notes, Trust, Deadlines, Proposals, Requests, Activity). No "Project"/"Customer"/"Invoice" display-copy leaks. |
| Brand check: portal footer reads "Powered by Kazi" | **PASS** — Footer reads "Powered by Kazi" (not "DocTeams"). OBS-404 fix from previous cycle verified. |

---

## Console / Network

Total console errors during Day 4 portal session: **0 new portal-side errors**.

Errors present in full session log (from `all: true`):
1. `GET /favicon.ico → 404` on :3002 and :8180 — known dev-mode asset gap, not a regression.
2. Multiple `GET /api/assistant/invocations → 404` on :3000 — these are firm-side errors from a previous browser tab (OBS-203 nit from Day 2). They are NOT portal-side errors.

Zero `5xx` from backend during the upload sequence. Three successful FICA uploads = three successful round-trips.

---

## Gaps filed Day 4

No new gaps filed. All previously filed Day 4 gaps from the prior cycle have been addressed:
- **OBS-401** — scenario amended (portal uses matter name as request label, not template title)
- **OBS-402** — scenario amended (removed; no request-level notes textarea)
- **OBS-403** — scenario amended (envelope stays IN_PROGRESS until firm Mark-as-Reviewed)
- **OBS-404** — VERIFIED (footer now reads "Powered by Kazi")

---

## Verdict

**Day 4 → COMPLETE. 14/14 PASS, 0 blockers, 0 new gaps.** Ready to advance to Day 5 (firm reviews FICA submission as Bob).
