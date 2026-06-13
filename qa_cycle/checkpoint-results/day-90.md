# Day 90 — Final regression + exit sweep (LAST scenario day) `[PORTAL + FIRM]`

- **Cycle**: 33 (`bugfix_cycle_2026-06-13`)
- **Date**: 2026-06-13 SAST
- **Stack**: Keycloak dev stack — firm :3000, portal :3002, backend :8080, gateway :8443. All 4 services RUNNING + HEALTHY (svc.sh status verified).
- **Tooling**: **Playwright MCP exclusively.** Portal session (Sipho Dlamini) persisted live; firm session (Thandi Mathebula, Owner) persisted live — zero KC login, zero magic-link. SingletonLock did not recur.
- **Matter**: RAF-2026-001 `08ad56c4-ff5e-49c2-a034-cb5fa04b462c` (Dlamini v Road Accident Fund, CLOSED). Isolation target = Moroka Family Trust / EST-2026-002 (`dc10e9ac-…`).

## Result summary — ALL CHECKPOINTS PASS

| Checkpoint | Result |
|---|---|
| Portal routes render clean (0 JS errors, 0 500s) | **PASS** |
| Isolation re-probe — Moroka denied at URL + API | **PASS (404 across the board)** |
| Email isolation — zero Moroka refs in Sipho's emails | **PASS (0/27)** |
| Terminology consistency (Matters/Fee Notes/Engagement Letters/Trust/Requests) | **PASS** |
| Progressive disclosure / no cross-vertical leak (firm) | **PASS** |
| Mailpit final tally — zero bounced/failed | **PASS (33 sent, 0 failed)** |
| Firm-side final state sane | **PASS** |

**Blocked?** NO. Zero new defects. Carry-over exemptions noted (not re-filed): OBS-201/506, OBS-2101, OBS-6001 (WONT_FIX), OBS-6002 (OPEN-tooling), OBS-8801 (OPEN-MEDIUM).

---

## 1. Portal route sweep `[PORTAL]` — PASS

Walked every real portal route as Sipho (live session), captured per-navigation fresh console log (not session-accumulated). Canonical routes derived from the sidebar `<a href>` map (visible label → route slug):

| Sidebar label | Route | Console errors | Render |
|---|---|---|---|
| Home | `/home` | 0 | clean |
| Matters | `/projects` | 0 | clean (Your Matters: Dlamini v RAF + engagement letter only) |
| Trust | `/trust` → `/trust/08ad56c4-…` | 0 (1 benign warning*) | clean (balance R 0,00; 50k+20k deposits, 70k payout) |
| Deadlines | `/deadlines` | 0 | clean (no open deadlines — matter closed) |
| Fee Notes | `/invoices` | 0 | clean (INV-0001 PAID, INV-0002 SENT) |
| Engagement Letters | `/proposals` | 0 | clean (PROP-0001 ACCEPTED) |
| Requests | `/requests` | 0 | clean (REQ-0001/0002/0004 COMPLETED) |
| Activity | `/activity` | 0 | clean (Your actions + Firm actions tabs) |
| Profile | `/profile` | 0 | clean (Sipho Dlamini / sipho.portal@example.com) |

- **Zero JS errors, zero 500s** across all 9 routes.
- `*` The only WARNING (on `/trust`) is the benign Next.js dev advisory `Detected scroll-behavior: smooth on <html>` — cosmetic dev-only, not a JS error.
- The single ERROR observed during the sweep (`GET /matters 404`) was an **operator navigation mistake** (typed the label as a path; the canonical route is `/projects`). Not a product defect — the `/matters` slug is not a portal route.

## 2. Isolation re-probe (final) `[PORTAL]` — CLEAN

### URL layer (Sipho's live browser session)
- `/projects/dc10e9ac-…` (Moroka EST-2026-002) → renders **"The requested resource was not found"** (`Back to matters`). Zero Moroka/Peter/EST-2026 text on page. Backing API calls in console all **404** (`/portal/projects/dc10e9ac-…` + `/summary`, `/tasks`, `/documents`, `/comments`).

### API layer (Sipho's real `portal_jwt` Bearer token, replayed via `fetch` against :8080)
| Probe | Status | Body | Leak |
|---|---|---|---|
| `GET /portal/projects/dc10e9ac-…` | **404** | `"No project found with id dc10e9ac-…"` | none |
| `GET /portal/projects/dc10e9ac-…/documents` | **404** | `"No project found…"` | none |
| `GET /portal/info-requests/458c97b6-…` (REQ-0003) | **404** | not-found | none |
| `GET /portal/trust/transactions/23791476-…` (R25k DEP) | **404** | not-found | none |
| `GET /portal/documents/b72eaa77-…/presign-download` (death cert, INTERNAL) | **404** | `"No document found with id b72eaa77-…"` | none |
| **SANITY** `GET /portal/projects/08ad56c4-…` (Sipho's OWN matter) | **200** | `"Dlamini v Road Accident Fund"` | — |

The sanity 200 proves the token is valid and the five 404s are genuine tenant/ownership authz denials — not a broken session. Backend repository returns a clean "not found" that never reveals the entity exists. **Isolation: CLEAN at URL + API.**

### Email layer
- Deep-scanned **all 27** of Sipho's emails (full Subject + Text + HTML bodies, not snippets) for `moroka|peter|EST-2026|liquidation|distribution account|deceased|25 000|REQ-0003|001234`.
- **0 / 27 hits.** Sipho's stream is 100% RAF-2026-001/Dlamini: fee notes INV-0001/0002, requests REQ-0001/0002/0004 (no REQ-0003), proposal PROP-0001, 3× trust activity, SoA/closure-letter doc-ready, weekly digest, 6× portal access links.
- Moroka's 2 emails (REQ-0003, trust activity) go only to `moroka.portal@example.com` — fully separate recipient stream. No cross-contamination.

## 3. Terminology consistency `[PORTAL + FIRM]` — PASS

- **Portal**: sidebar = Home / **Matters** / **Trust** / Deadlines / **Fee Notes** / **Engagement Letters** / **Requests** / Activity / Profile. Page headings match: "Your Matters", "Fee Notes" (col "Fee Note #"), "Engagement Letters" (col "Engagement Letter #"), "Information requests", "Trust balance". No "Project/Invoice/Proposal" in visible copy (slugs differ but labels are legal-correct — established design).
- **Firm**: sidebar sections WORK / **MATTERS** (Matters, Recurring Schedules) / CLIENTS / FINANCE / TEAM / AI. Dashboard body scan: `Project` ✗, `Customer` (label) ✗, `Invoice` ✗ — zero leaks. Heading "Matters", "2 matters".

## 4. Progressive disclosure / cross-vertical leak `[FIRM]` — PASS

Firm sidebar shows legal modules (Matters, Court Calendar, Recurring Schedules) + standard WORK/CLIENTS/FINANCE/TEAM/AI sections. **Zero accounting or agency nav items** leaked in. Dashboard copy "Company overview and matter health".

## 5. Firm-side final state `[FIRM]` — SANE

- `/org/mathebula-partners/projects` (Matters): **2 matters** — "Estate Late Peter Moroka" (Moroka, correctly visible to Thandi the firm owner) + "Engagement Letter — Litigation (Dlamini v RAF)". Dual-visibility model confirmed: Moroka IS visible firm-side, INVISIBLE to Sipho on portal (§2).
- Dashboard: ACTIVE MATTERS 2, HOURS THIS MONTH 4.0h. Renders clean.
- Firm console: 1 ERROR = the dashboard sparkline SVG `<path> d` parse glitch (`L 2,20 L 2,20 Z` missing leading moveto) — cosmetic chart-render artifact, pre-existing, non-blocking. Plus the exempt OBS-201 `/api/assistant/invocations` 404 class (AI proxy unwired in KC mode, WONT_FIX-EXEMPT).

## 6. Mailpit final tally — PASS

- **33 total emails captured, 0 bounced / 0 failed.**
- Every message has a valid recipient (0 with no recipient). No `MailSendException` / SMTP error in backend log this session.
- Recipient breakdown: 27 → sipho.portal@example.com, 2 → moroka.portal@example.com, 2 → thandi@…, 1 → carol@…, 1 → bob@… (firm-internal). 9 unread (informational).

---

## Evidence

- `day-90-firm-matters-final.png` — firm Matters list (2 matters, Moroka visible firm-side).
- `day-90-portal-final-state.png` — portal Activity trail final state (Sipho).
- Console logs: `.playwright-mcp/console-2026-06-13T18-24-*.log` … `18-28-*.log` (per-route, fresh).
- API isolation probe results inlined in §2 (real `portal_jwt` Bearer replay).

## Exit determination

All Day 90 checkpoints PASS on a single clean read-only pass. No new gaps. **QA Position → ALL_DAYS_COMPLETE.** Carry-over exemptions remain open/exempt as documented (OBS-201/506, OBS-2101, OBS-6001 WONT_FIX, OBS-6002 OPEN-tooling, OBS-8801 OPEN-MEDIUM) — to be addressed at wrap-up, none blocking the demo-ready exit.
