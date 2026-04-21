# Day 4 — Sipho first portal login, upload FICA documents  `[PORTAL]`
Cycle: 1 | Date: 2026-04-21 | Auth: Magic-link (portal JWT) | Portal: :3002 | Actor: Sipho Dlamini

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 4 (checkpoints 4.1–4.14).

**Result summary (Day 4): 8/14 checkpoints executed — 2 PASS (4.3/4.6 via workaround), 3 FAIL (4.2/4.4/4.5 link-target + pending-card missing), 1 BLOCKER at 4.8 (GAP-P-02 — portal has no UI route to view/respond to information requests), 6 BLOCKED (4.8–4.14).** First BLOCKER reached at **4.2** (email link does not point to portal); forced workaround via DevPortalController token → `/auth/exchange` succeeded, but portal has **no UI at all** for viewing / uploading info-request items, blocking 4.8 onwards.

New gaps: **GAP-L-42** (HIGH/BLOCKER, backend — info-request email link target, reported in Day 3 resume), **GAP-P-01** (HIGH, frontend — portal home `InfoRequestsCard` calls `/portal/information-requests` (404) instead of backend's actual `/portal/requests` (200); dead UI), **GAP-P-02** (HIGH/BLOCKER, portal — no UI route for information-request pickup / upload; `/requests` → 404, project detail page shows "The requested resource was not found"), **GAP-P-03** (MED, portal — Matters list shows "No projects yet" even though the Sipho-linked matter `40881f2f-…` exists firm-side in ACTIVE status; portal project scope / filter issue).

## Session prep

Fresh browser context. No firm login carryover. Portal health pre-check: `curl http://localhost:3002/ → 307` (redirects to /home then /login when unauthenticated — expected). Started from Mailpit inbox at `http://localhost:8025`.

Carry-forward watch list items checked this turn:
- **PORTAL_ORG_SLUG override**: portal uses `mathebula-partners` via `orgId` query param on `/auth/exchange` — no env override needed (confirmed by successful token exchange), unlike the prior `e2e-test-org` default. **Not a gap.**
- **Portal magic-link email subject stability**: subject = `"Information request REQ-0001 from Mathebula & Partners"` — word "request" present but NONE of scenario's asserted keywords ("sign in", "action required", "your portal"). Copy drift — low severity, logged as GAP-L-42 sub-note.
- **Portal currency rendering**: not exercised this turn (no invoice / trust pages reached before BLOCKER).

## Phase A: Magic-link landing

### Checkpoint 4.1 — Locate FICA info-request email in Mailpit
- Result: **PASS**
- Evidence:
  - Mailpit API: `GET /api/v1/messages?limit=20` → 7 messages total. Newest matching message id `kunWvRjbgFwpWHzveAQqrA`, Created `2026-04-21T20:15:43`, From `noreply@docteams.app`, To `sipho.portal@example.com`, Subject `"Information request REQ-0001 from Mathebula & Partners"`.
  - Subject references REQ-0001 (scenario-addressable) but does not match the scenario's asserted keyword set ("sign in" / "action required" / "your portal"). Noted under GAP-L-42.

### Checkpoint 4.2 — Click the magic-link → browser navigates to portal `/accept/[token]`
- Result: **FAIL (BLOCKER-class per GAP-L-42, carry-through workaround applied)**
- Evidence:
  - Email HTML body contains exactly ONE href: `http://localhost:3000/portal` (firm port 3000, no token, literal path fragment). Plain-text body shows `View Request (http://localhost:3000/portal)`. No token, no portal host.
  - DB probe: `SELECT id, portal_contact_id, created_at FROM tenant_5039f2d497cf.magic_link_tokens ORDER BY created_at DESC;` → 2 rows, both created by DevPortalController at 20:12 during portal-contact backfill; zero tokens created by the 20:15 info-request send. Confirms the send path does not mint a magic-link token.
  - Clicking the email's `http://localhost:3000/portal` link lands on the **firm** frontend (not the portal on :3002), which is a KC-protected route → redirects to login — not a useful destination for a client.
  - **Workaround applied for QA continuity**: used the token minted by DevPortalController (`fEmBJ0Kwy8Qy_sFd6-z_eaWptbdFHsKME3brpcc-oAs`, expires 20:27 UTC) → navigated to `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners`.
  - Logged **GAP-L-42** (HIGH/BLOCKER) in Day 3 resume block.

### Checkpoint 4.3 — Portal exchanges token → redirects to `/home`
- Result: **PASS (via workaround)**
- Evidence:
  - `GET /auth/exchange?token=…&orgId=mathebula-partners` → client-side POST `/portal/auth/exchange` → portal stored `portal_jwt` + `portal_customer` in `localStorage` → redirected to `/projects` (not `/home` — minor scenario drift, but both are valid authenticated routes).
  - `localStorage.getItem('portal_jwt')` returns a JWT (confirmed via `page.evaluate`). `localStorage.getItem('portal_customer')` returns `{id,name,email,orgId}`.
  - `GET http://localhost:8080/portal/requests` with `Authorization: Bearer {portal_jwt}` → **200** returning the FICA REQ-0001 payload. Backend auth OK.

### Checkpoint 4.4 — `/home` renders pending info request section with "FICA Onboarding Pack"
- Result: **FAIL (GAP-P-01)**
- Evidence:
  - Navigated to `/home` → sidebar renders (Home/Matters/Trust/Deadlines/Invoices/Proposals/Documents), main area shows three cards: "Upcoming deadlines 0 Next 14 days", "Recent invoices (No invoices yet)", "Last trust movement (No recent activity)". **No "Pending info requests" card** — even though REQ-0001 was dispatched minutes earlier.
  - Browser console fetch probe: `fetch('http://localhost:8080/portal/information-requests?status=PENDING', ...)` → **404** `"No static resource portal/information-requests"`. Backend endpoint does not exist under that path.
  - Control probe: `fetch('http://localhost:8080/portal/requests', ...)` → **200** returning REQ-0001.
  - **Root cause**: `portal/app/(authenticated)/home/page.tsx` line 62 calls `portalGet<InfoRequestSummary[]>("/portal/information-requests?status=PENDING")` but backend exposes the feature under `/portal/requests`. Path mismatch — frontend card queries a dead endpoint, silently catches the 404, and renders nothing. The module-gate `modules.includes("information_requests")` also likely evaluates false because `/api/portal-context` returns 404 in dev (no portal_context backend resource registered).
  - Logged **GAP-P-01** (HIGH, portal frontend — `InfoRequestsCard` queries wrong path; add to the request list once path is fixed).

### Checkpoint 4.5 — Header / sidebar shows Mathebula firm branding
- Result: **PARTIAL**
- Evidence:
  - Portal header renders `<img alt="Mathebula & Partners logo">` (src is S3-presigned URL). Branding logo **present** — consumed correctly on portal (firm-side GAP-L-26 still open for the firm UI but portal does).
  - Navy brand-color accent not clearly visible in sidebar / nav — sidebar is the default slate-50 / dark text scheme. Partial consumption of `brand_color`.
  - User chip top-right: "Sipho Dlamini" (correct).
  - Not re-logged as a new gap — folds into watch-list observation that brand-color consumption is incomplete across UI chrome.

### Checkpoint 4.6 — User identity displayed as "Sipho Dlamini"
- Result: **PASS**
- Evidence: Portal header button "User menu" → "Sipho Dlamini", matches `customers.name` on firm-side.

## Phase B: Upload FICA documents

### Checkpoint 4.8 — Click into "FICA Onboarding Pack" → info-request detail renders
- Result: **BLOCKED (GAP-P-02)**
- Evidence:
  - No visible entry point on portal `/home` or portal sidebar for information requests. Sidebar items: Home, Matters, Trust, Deadlines, Invoices, Proposals, Documents — **no Requests / Information Requests**.
  - Direct URL probes:
    - `http://localhost:3002/requests` → **404** "Page not found"
    - `http://localhost:3002/information-requests` → **404**
    - `http://localhost:3002/projects/40881f2f-7cfc-45d9-8619-de18fd2d75bb` → renders "The requested resource was not found. This project may have been removed, you may not have access, or the request failed. Please try again." (despite REQ-0001 being active on that project and the matter being ACTIVE firm-side). Screenshot: `qa_cycle/checkpoint-results/day-04-4.4-portal-no-requests-ui.png`.
    - `http://localhost:3002/projects` → renders "Your Projects / No projects yet / Your Mathebula & Partners team will share projects with you here."
  - Backend has the feature: `GET /portal/requests` → 200 with REQ-0001; `GET /portal/requests/06dc1a7e-…` → 200 with full 3-item FICA payload including `name`, `description`, `responseType=FILE_UPLOAD`, `fileTypeHints=PDF, JPG, PNG`, `status=PENDING`. **The backend is ready; the portal UI simply doesn't have the pages.**
  - Logged **GAP-P-02** (HIGH/BLOCKER, portal — no UI route for info-request pickup / upload) + **GAP-P-03** (MED, portal — Matters scope filter doesn't surface a matter that has an active request addressed to Sipho).
  - **Halting Day 4 per BLOCKER rule.** Checkpoints 4.8–4.14 cannot proceed without the portal UI.

### Checkpoints 4.8–4.14 — BLOCKED
- 4.8 Click into FICA pack → detail renders — **BLOCKED** (GAP-P-02: no portal UI)
- 4.9 Verify three upload slots — **BLOCKED** (cascade of 4.8)
- 4.10 Upload three test PDFs — **BLOCKED** (cascade)
- 4.11 Optional note — **BLOCKED** (cascade)
- 4.12 Submit → state Submitted — **BLOCKED** (cascade)
- 4.13 `/home` pending card updates — **BLOCKED** (GAP-P-01: dead home card + GAP-P-02 cascade)
- 4.14 Optional screenshot — **BLOCKED** (cascade)

## Day 4 checkpoints (final rollup)

- Magic-link login succeeded — no Keycloak form appeared at any step: **PASS with workaround** (via DevPortal token, not via the actual info-request email; GAP-L-42 blocks the intended path).
- Uploads stored (firm side will verify on Day 5): **FAIL** — cannot upload (GAP-P-02).
- Info-request state machine progressed: Sent → Submitted: **FAIL** — no state change possible (GAP-P-02).
- No firm-side terminology leaks on portal: **N/A** — didn't reach the detail screens.

## New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-L-42 | **HIGH / BLOCKER** | (also logged in Day 3 resume) Information-request email "View Request" link points to `http://localhost:3000/portal` (firm host, no token, literal path) — no `magic_link_tokens` row minted by the send path. Owner: backend. |
| GAP-P-01 | HIGH | Portal home `InfoRequestsCard` queries `/portal/information-requests?status=PENDING` which returns 404 — backend exposes the feature under `/portal/requests` (returns 200). Frontend/backend path mismatch. Fix: update `portal/app/(authenticated)/home/page.tsx` line 62 to use the correct path, and register `/api/portal-context` for module-gating to work. Effect: home card silently renders nothing, client has no affordance to discover the FICA request. Owner: portal (frontend). |
| GAP-P-02 | **HIGH / BLOCKER** | Portal has **no UI route** for information-request pickup / upload. `/requests`, `/information-requests` both 404. Backend `/portal/requests/{id}` returns full FICA payload (3 items, FILE_UPLOAD type, PDF/JPG/PNG hints, all PENDING) but there is no client-facing page to render it, let alone trigger uploads. Day 4 Phase B is fundamentally un-executable. Scenario Days 4/8/11/30/46/61/75 all implicitly assume this UI exists. Owner: portal — needs `/requests` list page + `/requests/[id]` detail page with per-item presigned-upload flow (`POST /portal/requests/{id}/items/{itemId}/upload-init` or similar) calling backend's existing upload endpoints. Blocks entire portal POV across the 90-day script. |
| GAP-P-03 | MED | Portal Matters tab renders "No projects yet" and direct-URL access to `/projects/40881f2f-…` says "The requested resource was not found", despite the customer being linked to that matter firm-side (customer.id on project row, matter ACTIVE, member count 1). Either the portal project scope filter is over-restrictive (e.g., only ACTIVE customer lifecycle) or an authorization check is rejecting the portal contact as a non-member. Owner: backend portal service + portal frontend fallback copy. Severity MED — a visible matter list would be nice but isn't strictly required to respond to the FICA request (if the portal had a Requests UI, it could link direct). |

## Halt reason

Day 4 halted at **Checkpoint 4.8** on BLOCKER rule — **GAP-P-02** (no portal UI for information requests). GAP-L-42 would normally halt us at 4.2 (email link wrong), but a workaround was available via DevPortalController; no equivalent workaround exists for the missing portal UI. Day 5 depends on Day 4 Phase B uploads landing; cannot start Day 5 without a portal-side submission path.

## QA Position on exit

`Day 4 — 4.8 (blocked pending GAP-P-02, cascades from GAP-P-01 + GAP-L-42)`.

Next-turn recommendation: **Dev fix** the portal UI gap. Minimum viable fix for the 90-day lifecycle to proceed:
1. **GAP-P-02** (portal UI) — author `portal/app/(authenticated)/requests/page.tsx` listing pending requests from `GET /portal/requests` + `portal/app/(authenticated)/requests/[id]/page.tsx` showing request items with per-item FILE_UPLOAD slots calling the existing `upload-init` → S3 PUT → `confirm` pattern. Add `Requests` entry to `portal/lib/nav-items.ts`.
2. **GAP-P-01** (home card) — fix the path in `portal/app/(authenticated)/home/page.tsx` line 62 from `/portal/information-requests?status=PENDING` to `/portal/requests?status=SENT` (backend's actual path + terminology). Also register or stub `/api/portal-context` so the `modules.includes("information_requests")` gate evaluates correctly (or remove the gate if modules are a future concept).
3. **GAP-L-42** (email link) — update info-request send path to `MagicLinkService.generateToken(portalContactId, ...)` and render link as `{portal.base-url}/auth/exchange?token={raw}&orgId={externalOrgId}`.

Lowest priority: GAP-P-03 (Matters list empty) is an ancillary fix — not on the Day 4–5 critical path once GAP-P-02 lands.

Deferred: all Day 3 deferred gaps (L-35/L-36/L-37/L-38/L-39/L-41) remain deferred.
