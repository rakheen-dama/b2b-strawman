# Day 15 — Isolation check — Sipho cannot see Moroka's data `[PORTAL]`

**Cycle**: 21 (bugfix_cycle_2026-06-13)
**Date executed**: 2026-06-13 SAST
**Actor**: Sipho Dlamini (portal :3002)
**Tooling**: Playwright MCP exclusively (`mcp__playwright__browser_*`) — clean Chromium, no claude-in-chrome (Day 14 block did NOT recur). Phase C API probes via `curl` with Sipho's portal JWT (the only legitimate non-browser surface — backend authz hard-negative).
**Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` Day 15 (lines 519–575)

## Verdict

**ALL CHECKPOINTS PASS — ZERO ISOLATION LEAK.** Tenant/contact isolation holds at every layer
(list views, direct URL, backend API, activity, email). No Moroka data surfaced anywhere on
Sipho's portal session. Not blocked.

## Session setup

- Fresh Sipho magic-link minted via `POST /portal/dev/generate-link` (email `sipho.portal@example.com`,
  org `mathebula-partners`). Exchanged at `:3002/auth/exchange` → portal session for **Sipho Dlamini**
  (customerId `2211a80a-5523-4a6d-8f96-0d638dff88f6`).
- Separate JWT minted for Phase C curl probes (same identity; backend `/portal/auth/exchange` returned
  `customerId 2211a80a-…`, `customerName "Sipho Dlamini"`, `org_id mathebula-partners`).

## Phase A — List-view leak probe (browser)

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 15.1 | Login as Sipho | PASS | User menu = "Sipho Dlamini" |
| 15.2 | `/home` — no Moroka in any list/card | PASS | Pending info requests **0**; Upcoming deadlines **0**; Recent fee notes "No fee notes yet"; Last trust movement **R 50 000,00** (13 Jun 2026 — Sipho's only). No death certificate, no Liquidation request. `day-15-portal-home-isolated.png` |
| 15.3 | `/projects` — only Sipho's matters | PASS | Only **Dlamini v Road Accident Fund** (`08ad56c4-…`) + acceptance-auto matter **Engagement Letter — Litigation (Dlamini v RAF)** (`15a25aa5-…`). NO EST-2026-002 / Estate Late Peter Moroka |
| 15.4 | `/trust` — Sipho R50k only, NOT R75k aggregate | **PASS (critical)** | Auto-redirect to `/trust/08ad56c4-…`. Balance **R 50 000,00**. One tx row: 13 Jun 2026 / DEPOSIT / "Initial trust deposit — RAF-2026-001" / R 50 000,00. NO Moroka, NO EST-2026-002, NO R 25 000. `day-15-portal-trust-isolated.png` |
| 15.5 | `/invoices` (Fee Notes) — empty/Sipho only | PASS | "No fee notes yet." |
| 15.6 | `/deadlines` — only Sipho's | PASS | "No deadlines in this view." No Moroka Master's Office filings |
| 15.7 | `/proposals` — only Sipho's accepted | PASS | Only **PROP-0001** ACCEPTED 13 Jun 2026. No Moroka |
| 15.8 | Screenshot `/home` isolated | PASS | `day-15-portal-home-isolated.png` |
| (extra) | `/requests` list | PASS | Only REQ-0001 + REQ-0002 (both RAF). No REQ-0003 / Liquidation pack |

## Phase B — Direct-URL probe (hard negative, Moroka IDs, browser)

| # | URL | Result | Rendered |
|---|---|---|---|
| 15.9 | `/projects/dc10e9ac-…` (Moroka matter EST-2026-002) | PASS | "The requested resource was not found… you may not have access." Backend returned **404** on detail/comments/tasks/summary/documents (console confirms). No matter data |
| 15.10 | `/requests/458c97b6-…` (Moroka REQ-0003) | PASS | "The requested resource was not found." No Liquidation/Distribution content |
| 15.11 | `/documents/b72eaa77-…` (Moroka death cert) | PASS | **404 Page not found** (no portal route; backend presign-download also 404 — see Phase C). PDF never reachable |
| 15.12 | `/trust/transactions/23791476-…` (Moroka tx) | PASS | **404 Page not found** (no per-tx URL shape) |
| (extra) | `/trust/dc10e9ac-…` (Moroka matter ledger) | PASS | "No trust balance is recorded for this matter" + "The requested resource was not found" (txns + statements). R 25 000 not rendered |
| 15.13 | Screenshot denial | PASS | `day-15-portal-denial.png` |

## Phase C — API-level probe (backend authz, Sipho JWT)

| # | Endpoint | Expected | Got | Result |
|---|---|---|---|---|
| 15.14 | `GET /portal/projects/dc10e9ac-…` | 403/404 | **404** "No project found" | PASS |
| 15.15 | `GET /portal/requests/458c97b6-…` | 403/404 | **404** "No informationrequest found" | PASS |
| 15.16 | `GET /portal/trust/matters/dc10e9ac-…/transactions` | 403/404 | **404** "No project found" | PASS |
| 15.17 | `GET /portal/documents/b72eaa77-…/presign-download` | 403/404 | **404** "No document found" — NO bytes | PASS |
| 15.18 | `GET /portal/trust/summary` | Sipho only | **200**: single matter `08ad56c4-…` balance **50000.00**. No R25k/R75k | PASS |
| (extra) | `GET /portal/trust/movements` | Sipho only | **200**: one DEPOSIT 50000.00 "Initial trust deposit — RAF-2026-001". No Moroka | PASS |
| (extra) | `GET /portal/projects` (list) | Sipho only | 2 matters (RAF + engagement). No EST-2026-002 | PASS |
| (extra) | `GET /portal/requests` (list) | Sipho only | REQ-0001 + REQ-0002 only | PASS |
| (extra) | `GET /portal/api/proposals` (list) | Sipho only | PROP-0001 only | PASS |
| **CONTROL** | `GET /portal/projects/08ad56c4-…` (Sipho's OWN matter) | 200 | **200** "Dlamini v Road Accident Fund" | PASS — proves the 404s are per-contact scoping, NOT a blanket failure |

**Identifier-leak scan**: every portal endpoint response (`/projects`, `/requests`, `/api/proposals`,
`/activity`, `/trust/summary`, `/trust/movements`, `/documents`) grepped for `moroka | EST-2026-002 |
9894de9b | dc10e9ac | 458c97b6 | b72eaa77 | 23791476 | 25000 | liquidation | death-certificate |
651e35a8` → **ALL CLEAN, zero matches.**

## Phase D — Activity trail + digest leak probe

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 15.19 | `/activity` (both tabs) references only Sipho's matters | PASS | "Your actions" = Sipho's own uploads/submits. "Firm actions" = Bob Ndlovu's REQ-0001/REQ-0002 events. No Moroka REQ-0003, no Estate, no death-cert events |
| 15.20 | Most-recent digest email — Moroka-free | N/A (PASS by absence) | No digest delivered to Sipho by Day 15 (conditional checkpoint). All 12 Sipho emails (magic-links, trust-activity, PROP-0001, REQ-0001/0002 item-accepted/completed) body-scanned → **zero Moroka identifiers**. Moroka's 2 emails (REQ-0003, trust-activity) went to `moroka.portal@example.com` ONLY — never misrouted to Sipho |

## Day 15 summary checkpoints (BLOCKER severity)

- [x] List views (`/home`, `/projects`, `/trust`, `/invoices`, `/deadlines`, `/proposals`, `/requests`) show ONLY Sipho's data
- [x] Direct-URL probes to 4+ Moroka entity IDs denied at frontend (+1 extra trust-ledger probe) — no matter data renders
- [x] API-level probes to 4+ Moroka endpoints denied at backend (404, never 200)
- [x] Trust balance card R 50 000,00 (Sipho's only) — NOT R 75 000 aggregate
- [x] Activity trail + email have zero Moroka references

## Console / errors

- Trust ledger page: 0 errors, 1 benign warning (Next.js `scroll-behavior: smooth` dev warning — carry-over, non-defect).
- Direct-URL Moroka probes: the only console errors are the **expected 404s** from the backend authz layer
  (`/portal/projects/dc10e9ac-…` + sub-resources). These are the desired isolation behaviour, not defects.

## Gaps

**Zero new gaps.** No isolation leak — the most security-sensitive day passed clean at all four layers.

Carry-over exemptions (noted, not re-filed): OBS-701 (no structured fee/VAT line on portal proposal),
OBS-201/OBS-506 (firm-side AI-proxy 404s, not portal-origin). None observed on portal surfaces this day.

## Next

Day 21 — Firm logs time, adds disbursement, creates court date `[FIRM]` (context swap portal → firm :3000, actor Bob).
Day 90 (E.10) will re-run Phase B + Phase C probes against the same Moroka IDs to confirm zero isolation drift.
