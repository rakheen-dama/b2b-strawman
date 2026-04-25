# Day 46 — Sipho responds to second info request + trust re-check + isolation spot-check  `[PORTAL]`

## Day 46 Re-Verify — Cycle 1 — 2026-04-25 SAST

**Actor**: Sipho Dlamini (`sipho.portal@example.com`, customer `c3ad51f5-…`, RAF matter `e788a51b-…`)
**Tooling**: `mcp__playwright__*` browser-driven (plugin namespace was wedged this session — main namespace worked end-to-end). Read-only SQL `SELECT` for DB confirmation. Mailpit not consulted (REQ-0007 magic-link from Day 45 was unused; Sipho re-self-served via portal `/login` magic-link).
**Substitution note**: Day 45 GAP-L-67 forced REQ-0007 to use the FICA Onboarding pack template (3 items: ID copy / Proof of residence / Bank statement) instead of the scenario's medical-evidence template (discharge summary + orthopaedic report). Item count matches scenario expectation (3 items uploaded), but item names differ.

### Pre-state (read-only DB SELECT)
- `information_requests` REQ-0007 `454dea5d-…` status SENT, due 2026-05-02, 3 PENDING items
- `trust_transactions` 3 rows (Sipho 50K + 20K, Moroka 25K)
- `client_ledger_cards` Sipho R 70 000,00, Moroka R 25 000,00

### Checkpoint results

| # | Step | Result | Evidence |
|---|------|--------|----------|
| 46.1 | Sipho self-serve magic-link via portal `/login?orgId=mathebula-partners` → click dev-mode link → `/projects` | **PASS** | Token `d-1Edcm_PHwdklk9Lb3HKTJqvmUjHwEGCnDZawr6iew`, port 3002 ✓, orgId ✓. **L-42 magic-link self-service HOLDS.** |
| 46.2 | `/home` → "Pending info requests 4" tile + click `/requests` → REQ-0007 listed at top SENT 0/3 | **PASS** | `day-46-cycle1-portal-home-initial.yml`, `day-46-cycle1-portal-requests-list.yml`. REQ-0007 visible alongside REQ-0001/0002/0003/0004; only Sipho's RAF requests, no Moroka. (Note: home tile shows aggregate count not individual request names — scenario step 46.2 wording "Supporting medical evidence shows as pending → click into it" cannot be literally satisfied through this tile, but `/requests` list does identify REQ-0007 SENT.) |
| 46.3 | Click into REQ-0007 → upload 3 PDFs → all SUBMITTED → status flips DRAFT/SENT → IN_PROGRESS | **PASS** | `day-46-cycle1-req-0007-3of3-submitted.{yml,png}`. Per-item: ID copy SUBMITTED 16:46:51 UTC (doc `4d8e6125-…`), Proof of residence SUBMITTED 16:47:17 UTC (doc `68d9b68e-…`), Bank statement SUBMITTED 16:47:36 UTC (doc `8ca47203-…`). Parent request flipped SENT→IN_PROGRESS automatically (L-43 + L-47 listener fix HOLDS). |
| 46.4 | `/trust` → balance R 70 000,00 reflecting both deposits | **PASS** | `day-46-cycle1-portal-trust-two-deposits.png`. URL auto-redirected to `/trust/e788a51b-…` (single-matter shortcut). Trust balance card R 70 000,00 / "As of 25 Apr 2026". **L-52 portal trust-ledger sync VERIFIED for second deposit.** |
| 46.5 | Transactions list shows both deposits with running balance | **PASS** | Same screenshot. Top row "25 Apr 2026 / DEPOSIT / Top-up per engagement letter — RAF-2026-001 / R 20 000,00 / running R 70 000,00". Bottom row "25 Apr 2026 / DEPOSIT / Initial trust deposit — RAF-2026-001 / R 50 000,00 / running R 50 000,00". Order: descending by recorded_at (newest first). Running balance correct at every step. |
| 46.6 | Passive isolation — only Sipho's matter, no Moroka R 25 000 anywhere | **PASS** | (a) `/trust` redirected straight to RAF matter (no list view rendered for single-matter portal contact) — Moroka never enumerable. (b) **Active hard-negative** — direct URL `/trust/89201af5-…` (Moroka EST matter) renders "No trust balance is recorded for this matter" + 404s on `/portal/trust/matters/{moroka-id}/transactions` and `/statement-documents`. Backend correctly hard-rejects Sipho JWT against Moroka data. (c) `/projects`, `/requests`, `/home` snapshots searched — zero "moroka", "EST-2026", "deceased", "liquidation" tokens. **Isolation HOLDS at all 3 layers (UI render, direct-URL probe, API).** |
| 46.7 | `/home` → "Pending info requests" tile after 3 SUBMITTED → "no longer shows the medical evidence request" | **PARTIAL — see triage** | Home tile still shows count "4" (unchanged). Per data model, requests with all items SUBMITTED but not yet firm-ACCEPTED remain in IN_PROGRESS state and continue to count as "pending" from portal-contact perspective. This is consistent firm/portal semantics (firm has not yet reviewed/accepted), but scenario step 46.7 wording suggests the count should decrement when client finishes submitting. **Not a regression — design intent of "pending" appears to be "not yet COMPLETED/ACCEPTED", not "all items SUBMITTED".** Logged as observation OBS-Day46-PendingTileSemantics; recommend Product clarify expected behavior; not opening as gap. |
| 46.8 | Optional screenshot | **DONE** | `day-46-cycle1-portal-trust-two-deposits.png` |

### Day 46 checkpoints
- [x] Second info request lifecycle complete — 3 items SUBMITTED, parent request IN_PROGRESS
- [x] Trust balance update visible on portal — both deposits, correct totals + running balance, **L-52 VERIFIED for second deposit**
- [x] Isolation holds — no Moroka data leak 31 days after the explicit Day 15 check; verified via list-render, direct-URL probe, and API 404 boundary

### NEW gaps opened this turn

**GAP-L-68 LOW** — Portal Home "Last trust movement" tile is broken (404 on missing endpoint).
- **Symptom**: Portal `/home` "Last trust movement" card shows "No recent activity" even though Sipho has 2 RECORDED trust deposits. Browser console: `Failed to load resource: 404 @ http://localhost:8080/portal/trust/movements?limit=1` (twice, on two visits to `/home`).
- **Root cause** (grep'd):
  - Frontend caller: `portal/app/(authenticated)/home/page.tsx:239` — `portalGet<TrustMovement[]>("/portal/trust/movements?limit=1")`.
  - Backend: `PortalTrustController` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalTrustController.java`) maps `/portal/trust` and exposes only:
    - `GET /summary` (line 38)
    - `GET /matters/{matterId}/transactions` (line 45)
    - `GET /matters/{matterId}/statement-documents` (line 61)
  - **No `/movements` endpoint exists** — frontend `/home` fetcher is calling a route that was never built (or was renamed). NextJS dev-tool overlay in older sessions may have masked this.
- **Severity**: LOW — purely cosmetic Home tile; trust transactions display correctly on `/trust` (matter detail). Scenario doesn't gate on the Home tile content.
- **Suggested fix scope**: S (~1 hr). Options: (a) add `GET /portal/trust/movements?limit=N` returning latest N transactions across all of caller's matters (delegating to existing per-matter query joined across portal-contact's matters); or (b) change frontend to call `/portal/trust/summary` and read `lastTransactionDate`/`lastTransactionAmount` from each `PortalTrustMatterSummary` row, picking the max. Option (b) is smaller and reuses existing endpoint.
- **Evidence**: `day-46-cycle1-portal-home-initial.yml` line 91 ("No recent activity"), `day-46-cycle1-portal-home-final.png` (Last trust movement tile empty after 3 file uploads). Console errors: `.playwright-mcp/console-2026-04-25T16-45-39-702Z.log#L3-L4` and `…T16-49-02-272Z.log#L3-L4`.

**OBS-Day46-PendingTileSemantics** (not a gap, observation only).
- Home "Pending info requests" tile shows count 4 even after Sipho submits all 3 REQ-0007 items.
- Inferred semantics: counts requests in `SENT` or `IN_PROGRESS` (not `COMPLETED`). REQ-0007 went SENT→IN_PROGRESS on first submit (correct), and stays IN_PROGRESS until firm reviews (also arguably correct).
- Scenario 46.7 wording suggests user-facing expectation is "this request is no longer waiting on you" semantics. Worth a Product decision.
- Not opening as gap pending Product clarification.

### Verify-focus this turn
- **L-42 (magic-link)** — VERIFIED again on third self-service flow this cycle. Token / port 3002 / orgId / dev-mode link all correct. Holds.
- **L-43 (portal request-item submitted listener)** — VERIFIED. All 3 portal uploads produced SUBMITTED status on `request_items` rows with `submitted_at` populated and document_id linked.
- **L-47 (portal parent-request status sync)** — VERIFIED. REQ-0007 flipped SENT→IN_PROGRESS automatically after first portal submission.
- **L-52 (portal trust-ledger sync for RECORDED deposits)** — VERIFIED for **second deposit** (DEP-2026-002 R 20 000). Day 11 already verified for first deposit; this cycle confirms multi-deposit accumulation works correctly with running balance.
- **P-01, P-02, P-03** — VERIFIED transitively (portal nav working, requests UI route working, projects visible).
- **Tenant isolation (Day 15 follow-up at Day 46)** — HOLDS. Hard-negative direct-URL probe to Moroka matter trust returns 404 (backend rejects), UI renders "No trust balance recorded" with no Moroka data leak.

### Final DB state (read-only SELECT)
- `information_requests` REQ-0007 status `IN_PROGRESS` (was SENT)
- `request_items` for REQ-0007: 3 × `SUBMITTED` (was 3 × PENDING)
- `trust_transactions`: unchanged (3 rows; QA Day 46 is read-only on trust)
- `client_ledger_cards`: unchanged (Sipho R 70 000,00 / Moroka R 25 000,00)
- 3 portal-uploaded documents created in `documents` table (IDs `4d8e6125-…`, `68d9b68e-…`, `8ca47203-…`)

### Console errors observed
- `/home`: 2x 404 on `/portal/trust/movements?limit=1` → GAP-L-68
- `/trust/{moroka-id}` (direct hard-negative probe): 2x 404 on transactions/statements → expected, isolation working
- All other pages: 0 errors

### Tab/session state
- One main browser tab used throughout (no multi-tab needed since this is portal-only). No firm-side cross-checks performed (not in scope per dispatch).
- Sipho portal JWT issued at 16:45:36 UTC, valid through end of cycle.

### Summary
**5/5 substantive PASS + 1 PARTIAL (46.7 — semantic question, not regression) + 1 NEW LOW gap (GAP-L-68 home-tile endpoint mismatch). 0 BLOCKER.** Day 46 complete. Day 60 next per scenario.
