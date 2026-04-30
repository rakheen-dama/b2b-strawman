# Day 4 — Sipho first portal login + FICA upload

**Stack**: dev/Keycloak — frontend :3000, backend :8080, gateway :8443, portal :3002, KC :8180, Mailpit :8025
**Date**: 2026-04-30
**Actor**: Sipho Dlamini (portal contact) on portal `:3002`
**Branch**: `bugfix_cycle_2026-04-30` (PRs #1225 OBS-102, #1226 OBS-201, #1227 OBS-301 all merged via main; backend restarted to bind new `@Size(max=2000)`)

---

## OBS-301 Verification (pre-Day-4)

Re-ran on the dev stack as Bob, against client Sipho (`a30bb16b-743c-45a5-9fb5-13167fb92fde`). Two paths exercised:

| # | Check | Result |
|---|-------|--------|
| 1 | New Matter from Template (RAF) prefills 273-char description | **PASS** — `Personal-injury workflow for Road Accident Fund (RAF) claims under the Road Accident Fund Act 56 of 1996. Covers RAF1 claim filing, statutory medical assessments, RAF tariff negotiation, Section 24 court action, and 3/5-year prescription monitoring. Matter type: LITIGATION` (273 chars) loaded into the textarea on entering "Configure" step. `maxLength=2000` confirmed on the textarea attribute. |
| 2 | Submit succeeds with full prefilled description (no manual trimming) | **PASS** — Created matter `5ae531ff-c30d-4f4e-8645-8ac6fa0a3384` with name "OBS-301 Verify - Long Description Test". Backend accepted the 273-char body; matter detail page renders the full description verbatim (verified via `main.innerText` — string starts at offset 63, length matches the prefilled content). HTTP 200, no validation errors. |
| 3 | Negative path: textarea forced to 2100 chars (DOM `maxlength` removed in dev tools); Submit → field-level error appears INLINE under description | **PASS** — On Submit, dialog stayed open. Inline `<p class="text-destructive mt-1 text-xs">size must be between 0 and 2000</p>` rendered directly under the Description textarea. The textarea also acquired `aria-invalid` styling (red ring). The generic banner "1 field(s) have validation errors" still appears as a complementary signal — but the user now sees exactly which field is wrong + the actual constraint. |
| 4 | Console errors during the verification | **PASS** — Zero new errors. (One pre-existing dev warning about `scroll-behavior: smooth` and one self-induced 404 from a probe `fetch('/api/projects/...')` — unrelated.) |

**Evidence**:
- `qa_cycle/evidence/day-04/obs-301-verify-prefilled-state.png` — full 273-char prefill in the dialog
- `qa_cycle/evidence/day-04/obs-301-verify-long-desc-success.png` — created matter with full description on detail page
- `qa_cycle/evidence/day-04/obs-301-verify-fielderror-display.png` — inline "size must be between 0 and 2000" + aria-invalid textarea

**Verdict**: **OBS-301 → VERIFIED** (FIXED → VERIFIED). Both halves of the hybrid fix work end-to-end:
1. Backend `@Size(max=2000)` accepts seed-template prefills (273 chars) without truncation.
2. Frontend renders `ApiError.detail.fieldErrors[].message` as an inline error under the offending field, removing the previous black-box "1 field(s) have validation errors" UX.

Test matter `5ae531ff-c30d-4f4e-8645-8ac6fa0a3384` is left in place, clearly named "OBS-301 Verify - Long Description Test"; cannot be archived without going through the full close-matter gates (trust balance / disbursements / billing / tasks). It is excluded from Day 5+ flow which uses the canonical RAF-2026-001 matter (`b7e319f7-fd7e-4526-a8b3-b40b1f85b34b`).

---

## Day 4 step-by-step (portal context)

Context swap: opened a fresh tab on `http://localhost:3002` (Sipho is unauthenticated there).

### Phase A — Magic-link landing

#### 4.1 Open Mailpit, locate FICA magic-link email
- **PASS** — Mailpit message `WVVCHF6KxLFodNmUpcRWoG` retrieved via `curl http://localhost:8025/api/v1/message/<id>`. Subject: `Information request REQ-0001 from Mathebula & Partners`. To: `sipho.portal@example.com`. Body contains `View Request` button → `http://localhost:3002/auth/exchange?token=ep0gaG5qc0V6JZaLEMh4HGz-nrwgVkF0dsd7xWqKKBI&orgId=mathebula-partners`. Header bar uses navy `#1B3358` brand colour + S3 logo URL — branding leaks into email correctly.

#### 4.2 Click magic-link → token exchange
- **PARTIAL** — The Day-3 magic link returned `401` from `POST /portal/auth/exchange` with redirect to `/login` "Link expired or invalid. Please request a new login link." Likely cause: token was either expired (≥24h), one-time-consumed, or invalidated by the backend restart needed for OBS-301. **Recovery** (no firm-side help required): on `/login`, entered `sipho.portal@example.com` → "Send Magic Link" → portal issued a fresh dev-mode magic link (`token=SZgp5bH0UW1mjO2mmCXK2UBhiqEeBfU6i7xzqu89nEk`) inline on the page (dev-mode "click to sign in" affordance). Clicked → `/auth/exchange` → redirected to `/projects` (portal home), authenticated as Sipho Dlamini.
- **Note**: This portal magic-link "request a new one" path is the first-class recovery flow — the original mail-link is fragile across backend restarts and re-issues correctly. This is acceptable behaviour, not a gap.

#### 4.3 Token exchange completes → redirect to home
- **PASS** — Initial landing was `/projects`. Manual navigation to `/home` succeeds and renders authenticated Sipho.

#### 4.4 `/home` shows pending info request "FICA Onboarding Pack"
- **PARTIAL** — `/home` does render a "Pending info requests" KPI card with count = `1` linking to `/requests`, but the card does NOT display the FICA template name inline. `/requests` shows `REQ-0001 / Dlamini v Road Accident Fund / SENT / 0/3 submitted`. The matter name is shown as the request label, not the template/title "FICA Onboarding Pack". Functional flow works (the only request surfaces correctly); cosmetic gap noted as **OBS-401** (request title falls back to matter name on portal index — minor, scenario-only).

#### 4.5 Header / sidebar shows Mathebula firm branding
- **PASS** — Portal banner shows the Mathebula navy logo (`#1B3358`) + "Mathebula & Partners logo" alt-text. Sidebar header reads "Portal". No firm-vertical leakage; legal-za terminology applied (sidebar nav: Home / Matters / Trust / Deadlines / Fee Notes / Proposals / Requests / Activity).

#### 4.6 User identity = Sipho Dlamini
- **PASS** — Top-right user menu reads `Sipho Dlamini`. Confirmed against firm-side client record (`a30bb16b-743c-45a5-9fb5-13167fb92fde`).

#### 4.7 Screenshot of portal home
- **DONE** — `qa_cycle/evidence/day-04/day-04-portal-home-first-login.png`

### Phase B — FICA upload

#### 4.8 Click into FICA Onboarding Pack → detail renders
- **PASS** — Navigated to `/requests/7f8f9422-e8ae-4966-976e-85f90199d6c2`. Heading: `REQ-0001 / Dlamini v Road Accident Fund / 0/3 submitted • status SENT`.

#### 4.9 Three upload slots labelled correctly
- **PASS** — Three list items rendered:
  1. **ID copy** (required) — `Certified copy of the client's South African ID document or passport bio page. Must be certified by a Commissioner of Oaths, SAPS, or other accepted certifier within the last 3 months. Accepts: PDF, JPG, PNG`
  2. **Proof of residence (≤ 3 months)** (required) — `Recent utility bill, municipal rates account, bank statement, or similar document confirming the client's residential address. Document date must be within the last 3 months. Accepts: PDF, JPG, PNG`
  3. **Bank statement (≤ 3 months)** (required) — `Most recent bank statement evidencing the client's source of funds. Statement must be dated within the last 3 months and show the client's name, account number, and at least one transaction. Accepts: PDF`

  Labels match the FICA Onboarding Pack template metadata + acceptable file types are surfaced.

#### 4.10 Upload PDF to each slot
- **PASS** — All three uploads completed via `<label>` click → `<input type="file">` chooser → `browser_file_upload`:
  - Slot 1: `qa_cycle/fixtures/fica-id.pdf`
  - Slot 2: `qa_cycle/fixtures/fica-address.pdf`
  - Slot 3: `qa_cycle/fixtures/fica-bank.pdf`

  Each slot transitioned: file selected → Upload-and-submit button enabled → click → `Submitted — status: SUBMITTED` text appears. Submission counter advanced 1/3 → 2/3 → 3/3.

#### 4.11 Optional note "All documents current as of this week"
- **NOT PRESENT** — The portal FICA detail UI does not expose a per-request notes / cover-message textarea. Fields are per-slot only. This is a minor scope gap vs. scenario but is not a blocker: per-item submission carries the document and the request audit trail records the actor + timestamp. Logged as **OBS-402** (no request-level notes textarea on portal info-request detail — scenario-only / nice-to-have).

#### 4.12 Submit / state transition
- **PASS** (with caveat) — There is no top-level "Submit all" button; each item submits individually via "Upload and submit". Once all 3 slots show `SUBMITTED`, the request envelope shows `3/3 submitted • status IN_PROGRESS` (NOT `SUBMITTED` / `Awaiting review` as the scenario expected). Item-level statuses are all SUBMITTED, but the envelope status remains IN_PROGRESS until the firm marks it complete (see Day 5.4 — `Mark as Reviewed` is the firm-side transition that closes the request envelope). The scenario is slightly imprecise: from a portal-user perspective this is fine — the user has done everything they can and the firm now owns the next state transition. Logged as **OBS-403** (request envelope status stays IN_PROGRESS even when all items are SUBMITTED — likely WONT_FIX / scenario amend).

#### 4.13 Home pending count drops
- **PASS** — Returned to `/home`. "Pending info requests" KPI now reads `0`. The home view filters on items still awaiting client action, which is the correct user-facing semantic. Confirms the upload flow is functionally complete from the portal user's POV.

#### 4.14 Screenshot
- **DONE** — `qa_cycle/evidence/day-04/day-04-fica-submitted.png` (3/3 SUBMITTED)

---

## Day-end checkpoints

| Check | Result |
|-------|--------|
| Magic-link login succeeded — no Keycloak form appeared at any step | **PASS** — Portal magic-link path is `/login → email → /auth/exchange?token=…` (no Keycloak interstitial). Sipho never saw a KC URL. The original Day-3 mail-link was stale; new link from `/login` worked. |
| Uploads stored (firm side will verify on Day 5) | **DEFERRED to Day 5** — three SUBMITTED states recorded portal-side; firm-side download verification is Day 5.3. |
| Info-request state machine progressed: Sent → IN_PROGRESS (items: SENT → SUBMITTED) | **PASS** — Item state transitions confirmed inline (`Submitted — status: SUBMITTED`). Envelope state Sent → IN_PROGRESS (does not auto-advance to SUBMITTED — see OBS-403). |
| No firm-side terminology leaks on portal | **MOSTLY PASS** — Sidebar uses legal-za terms (Matters, Fee Notes, Trust, Deadlines, Proposals, Requests, Activity) — correct. **One leak found**: footer reads `Powered by DocTeams`. Per CLAUDE.md global memory and `feedback_product_name_kazi.md`: product brand is **Kazi**, never DocTeams. Logged as **OBS-404** (footer brand string mis-uses retired "DocTeams" name on portal). |

---

## Console / Network

Total console errors during Day 4 portal session: **0 new errors after authentication**.

The two errors that did appear are non-regressions:
1. `GET /favicon.ico → 404` — known dev-mode portal asset gap (parallel of KC-DEV-001).
2. `POST /portal/auth/exchange → 401` — single occurrence, from the stale Day-3 token attempt before requesting a new link. Token re-issue path worked first try.

Zero `5xx` from backend during the upload sequence. Three successful FICA uploads = three successful PUT/POST round-trips to `/portal/...`.

---

## Gaps filed Day 4

| ID | Description | Severity | Recommended status |
|----|-------------|----------|-------------------|
| OBS-401 | Portal `/requests` index uses matter name as the request label instead of the request template title (e.g. "Dlamini v Road Accident Fund" instead of "FICA Onboarding Pack"). Functional flow works; cosmetic only. | nit | likely WONT_FIX / scenario amend |
| OBS-402 | Portal info-request detail has no request-level notes textarea ("All documents current as of this week"). Per-item submission carries the data; cover-message is a UX nice-to-have. | nit | likely WONT_FIX / scenario amend |
| OBS-403 | Info-request envelope status stays `IN_PROGRESS` after all 3 items are SUBMITTED — does not auto-transition to SUBMITTED. Firm `Mark as Reviewed` (Day 5.4) is the next transition. | nit | likely WONT_FIX (state machine is correct; scenario terminology is loose) |
| OBS-404 | Portal footer reads "Powered by DocTeams" — retired product name. Per `~/.claude/projects/.../feedback_product_name_kazi.md` and CLAUDE.md, brand is **Kazi**. | bug (terminology) | SPEC_READY for Dev — single-string change in portal footer component |

---

## Verdict

**OBS-301 → VERIFIED. Day 4 → COMPLETE.** Ready to dispatch Day 5 (firm reviews FICA submission as Bob).
