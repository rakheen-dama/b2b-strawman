# Day 4 — Sipho first portal login, upload FICA documents — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`)
**Actor**: Sipho Dlamini (unauthenticated portal contact, arriving via email magic-link on portal :3002)
**Driver**: QA agent via Playwright MCP — fresh browser session (firm context from Days 2–3 closed; portal auth is an independent JWT session, no Keycloak cookies involved)
**Pre-checks**: `curl http://localhost:3002/` → `307` (redirect to login — healthy); svc.sh status all 4 services RUNNING+HEALTHY; Mailpit :8025 → 200
**Result**: 14/14 checkpoints PASS + 5/5 day-summary checkpoints PASS. **Zero new gaps filed.**

## Phase A: Magic-link landing

| Checkpoint | Result | Evidence | Gap |
|---|---|---|---|
| 4.1 Mailpit → locate FICA magic-link email | PASS | Mailpit UI (browser) inbox row: From noreply@kazi.app → sipho.portal@example.com, Subject "Information request REQ-0001 from Mathebula & Partners", msg ID `hhoVkD8UxgQaLsn2dG2oNu` (same email captured Day 3 — still valid, no re-request needed). | — |
| 4.2 Click magic-link in email body | PASS | Email body's single CTA link "View Request" → `http://localhost:3002/auth/exchange?token=nsJKu6Q0-…&orgId=mathebula-partners`. Navigated to link target. (Scenario's literal `/accept/[token]` route does not exist — actual route is `/auth/exchange?token=…`; identical to prior cycle's accepted PASS.) | — |
| 4.3 Token exchange → redirect | PASS | Exchange succeeded; browser landed authenticated on `/projects` (portal's default post-auth landing, same as prior cycle). **No Keycloak form at any step** — pure magic-link JWT auth. Landing page already showed Sipho's matter "Dlamini v Road Accident Fund". | — |
| 4.4 `/home` renders pending info request with matter context | PASS | `/home`: "Pending info requests **1**" card → links `/requests`; `/requests` row indexed by matter name "Dlamini v Road Accident Fund" (REQ-0001), per OBS-401 amendment (portal indexes by matter, not template title). Other cards: Upcoming deadlines 0 (next 14 days), Recent fee notes "No fee notes yet", Last trust movement "No recent activity". | — |
| 4.5 Header/sidebar shows Mathebula firm branding | PASS | Header `img "Mathebula & Partners logo"` served from tenant S3 path (`localhost:4566/docteams-dev/org/tenant_5039f2d497cf/branding/logo.png`, presigned). Brand navy verified computed: element with `backgroundColor: rgb(27, 51, 88)` = **#1B3358** (Day 1 brand colour) + inline `27, 51, 88` rgb in DOM. | — |
| 4.6 User identity "Sipho Dlamini" | PASS | Header user-menu button renders "Sipho Dlamini" (from firm-side client record) — consistent on /projects, /home, /requests, /requests/{id}. | — |
| 4.7 📸 Screenshot | PASS | `qa_cycle/checkpoint-results/day-04-portal-home-first-login.png` | — |

## Phase B: Upload FICA documents

| Checkpoint | Result | Evidence | Gap |
|---|---|---|---|
| 4.8 Click request row → detail renders matter context + per-item list | PASS | `/requests` row REQ-0001 / "Dlamini v Road Accident Fund" / SENT / 0/3 submitted → `/requests/de3d6962-6018-43bf-852d-d366d1a4d626`. Detail header: "REQ-0001 / Dlamini v Road Accident Fund / 0/3 submitted • status SENT"; per-item upload list below. | — |
| 4.9 Three upload slots labelled | PASS | (1) **ID copy** (required, Accepts PDF/JPG/PNG, certified-copy description), (2) **Proof of residence (≤ 3 months)** (required, PDF/JPG/PNG), (3) **Bank statement (≤ 3 months)** (required, PDF only). Each slot: "Upload file" button + disabled "Upload and submit". | — |
| 4.10 Upload test PDF to each slot | PASS | Via portal file-chooser (no API/SQL): `fica-id.pdf` (626 B) → ID copy, `fica-address.pdf` (627 B) → Proof of residence, `fica-bank.pdf` (615 B) → Bank statement (all from `qa_cycle/test-files/`, all ≤ 2 MB). Each selection enabled that item's "Upload and submit" button; no validation errors. | — |
| 4.11 (OBS-402 amend: removed) No cover-message textarea | PASS | Confirmed: no request-level cover-message/notes input anywhere on the detail page — per-item firm-set `description` only. Matches OBS-402 amendment. | — |
| 4.12 Per-item "Upload and submit" → each item Submitted | PASS | Sequential submits: ID copy → "Submitted — status: SUBMITTED", header "**1/3 submitted • status IN_PROGRESS**" (envelope SENT → IN_PROGRESS on first submit); Proof of residence → "2/3 submitted • status IN_PROGRESS"; Bank statement → "**3/3 submitted • status IN_PROGRESS**". No envelope-level Submit button exists; envelope stays IN_PROGRESS pending firm review (Day 5) — exactly the OBS-403 state machine. | — |
| 4.13 `/home` pending count drops to 0 | PASS | `/home` after third submit: "Pending info requests **0**" (was 1). Envelope still IN_PROGRESS but no longer pending from portal contact's perspective. | — |
| 4.14 📸 Screenshot | PASS | `qa_cycle/checkpoint-results/day-04-fica-submitted.png` (3/3 submitted • IN_PROGRESS, all items SUBMITTED). | — |

## Day 4 summary checkpoints

| Checkpoint | Result | Evidence |
|---|---|---|
| Magic-link login succeeded — no Keycloak form at any step | PASS | `/auth/exchange?token=…&orgId=mathebula-partners` → authenticated portal session directly; zero Keycloak interaction observed across the whole walk. |
| Uploads stored (firm side verifies Day 5) | PASS | All 3 accepted + SUBMITTED portal-side; **storage proven**: LocalStack S3 `docteams-dev` bucket now lists 3 new objects under `org/mathebula-partners/project/08ad56c4-ff5e-49c2-a034-cb5fa04b462c/` (`07d78e24-…`, `60639e26-…`, `c13bb325-…`) alongside the Day 1 branding logo. Firm-side retrieval check deferred to Day 5 per scenario. |
| State machine: per-item Pending → Submitted ×3; envelope Sent → IN_PROGRESS | PASS | Observed SENT (0/3) → IN_PROGRESS (1/3) → (2/3) → (3/3, still IN_PROGRESS). Closes to COMPLETED on firm accepts Day 5 (OBS-403). |
| No firm-side terminology leaks on portal | PASS | Sidebar: Matters / Trust / Deadlines / **Fee Notes** (not Invoices) / **Engagement Letters** (not Proposals) / Requests / Activity. Headings: "Your Matters", "Information requests". No "task"/"ticket"/"project" leak in visible copy (route slug `/projects` is URL-only, accepted prior cycle). |
| Brand check: footer "Powered by Kazi", never "DocTeams" | PASS | `contentinfo` → "Powered by Kazi" on every page visited (/projects, /home, /requests, /requests/{id}). Zero "DocTeams" strings anywhere (OBS-404 fix holds). |

## Console / log health

- Portal Day 4 session console: **only** `favicon.ico` 404 on :3002 — cosmetic, same as prior cycle (not an application error, not filed). Zero JS/hydration/render errors during the entire portal walk.
- The session-wide error buffer also contains `:3000/api/assistant/invocations` 404s (OBS-201 WONT_FIX-EXEMPT) and 2 API-probe 404s — all from the firm-side Days 2–3 browser sessions, timestamped before Day 4 start; none from the portal session.
- `.svc/logs/backend.log`: **0 ERROR lines**. `.svc/logs/portal.log`: no errors/exceptions.

## Gaps filed

None.

## Screenshots

- `qa_cycle/checkpoint-results/day-04-portal-home-first-login.png` — /home first login: pending request 1, firm logo, Sipho identity
- `qa_cycle/checkpoint-results/day-04-fica-submitted.png` — REQ-0001 detail: 3/3 submitted • IN_PROGRESS, all items SUBMITTED

## Entity IDs (for downstream days)

- **Info request REQ-0001**: `de3d6962-6018-43bf-852d-d366d1a4d626` — 3/3 submitted, envelope **IN_PROGRESS** (awaits firm per-item Accepts Day 5); portal route `/requests/de3d6962-6018-43bf-852d-d366d1a4d626`
- **Uploaded docs (S3 keys)**: `org/mathebula-partners/project/08ad56c4-ff5e-49c2-a034-cb5fa04b462c/{07d78e24-0b50-4853-b490-ec7be8ec5be8, 60639e26-7557-4044-853e-608bc0bc355a, c13bb325-7e7b-4319-8977-ce8f676ad69b}` — fica-id.pdf / fica-address.pdf / fica-bank.pdf
- **Sipho portal session**: live on :3002 (magic-link token `nsJKu6Q0-…` consumed/exchanged this walk); Day 5 portal spot-check may need re-auth if session expires
- **Matter / client IDs**: unchanged from Day 3 (`08ad56c4-ff5e-49c2-a034-cb5fa04b462c` / `2211a80a-5523-4a6d-8f96-0d638dff88f6`)
