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

---

## Day 46 Re-walk — Cycle 42 — 2026-04-27 SAST

**Branch**: `bugfix_cycle_2026-04-26-day46` (cut from `main` `f227fd3e`)
**Backend rev / JVM**: main `f227fd3e` / backend PID 41372 (gateway PID 71426 ext, frontend 5771, portal 5677 — all healthy per `svc.sh status`)
**Stack**: Keycloak dev (3000/8080/8443/8180/3002)
**Method**: Browser-driven via Playwright MCP. Mailpit GET (legitimate). Read-only `psql` SELECT for evidence only. No SQL writes; no REST mutations.
**Actor**: Sipho Dlamini (portal contact — magic-link auth via REQ-0005 Mailpit email).

### Pre-state (read-only SELECT, tenant `tenant_5039f2d497cf`)

```text
information_requests (5 rows pre-walk):
  REQ-0001 a0306375-… SENT      (RAF/Sipho)        carry-forward Day 4
  REQ-0002 d8a58ade-… COMPLETED (RAF/Sipho)        carry-forward (3 items submitted historically)
  REQ-0003 de3cffc7-… SENT      (EST/Moroka)       carry-forward Day 14 — NOT visible to Sipho
  REQ-0004 d7dc4faf-… SENT      (RAF/Sipho) FICA   carry-forward Day 45 (3 items, 0 submitted)
  REQ-0005 73babd4e-… SENT      (RAF/Sipho) ad-hoc carry-forward cycle-41 retest (2 items, 0 submitted)

request_items for REQ-0005 pre-walk:
  9c416b24-… "Latest specialist medical reports" PENDING required
  cc6f025c-… "Independent expert assessment"     PENDING required

trust_transactions (4 rows, all carry-forward — no Day 46 mutations expected):
  Sipho: R 50 000 (Day 10) + R 100 (BUG-CYCLE26-11 retest) + R 20 000 (Day 45) = R 70 100
  Moroka: R 25 000 (Day 14, isolation reference)
```

> **Carry-forward note**: pre-state Sipho trust balance is **R 70 100,00** (not R 70 000,00) because of the BUG-CYCLE26-11 retest deposit (R 100). Therefore portal balance lands at R 70 100 — **expected carry-forward** documented in Day 45 cycle-38 results, not a new gap.
>
> **Cycle 1 GAP-L-68 status**: VERIFIED FIXED — portal `/home` "Last trust movement" tile now renders correctly ("R 20 000,00 / 27 Apr 2026") on `f227fd3e`; the missing `/portal/trust/movements?limit=1` endpoint that cycle-1 logged is no longer 404'ing. Cycle-1 GAP-L-68 closed by an intervening fix between cycle 1 and cycle 42 (presumably one of the L-44 / L-52 portal-trust-listing PRs).

### Summary

**5 PASS / 1 FAIL / 0 PARTIAL / 0 BLOCKED / 0 SKIPPED** (8 substantive checkpoints incl. console + isolation cross-check)

**Verdict**: Day 46 cycle-42 walk completes the request-response, trust re-check, and isolation assertions cleanly on `main f227fd3e`. The two portal file uploads (presigned-URL flow → S3 → confirm) succeeded end-to-end; both items are SUBMITTED in DB with linked `document_id`s. Trust balance reconciles correctly on the portal at R 70 100 (matches firm-side after Day 45 deposit). Cross-customer isolation HOLDS — zero Moroka leakage on `/projects`, `/trust`, or `/requests`. **One new gap surfaced (MEDIUM)**: portal home "Pending info requests" tile counts IN_PROGRESS-but-fully-submitted requests as pending; after submitting REQ-0005's 2/2 items the count remained 3 instead of dropping to 2. Scenario §46.7 explicitly expects the count to drop. **GAP-L-92** logged. (Cycle 1 had logged the same observation as `OBS-Day46-PendingTileSemantics` and deferred to Product — cycle 42 elevates it to a tracked MEDIUM gap because the scenario expectation has not been updated and a code-side fix is small.)

### Checkpoints

#### 46.1 — Login via magic-link for second info request
- Result: **PASS**
- Evidence: Mailpit message `QiFckxzGK9bLNEhPeEouZb` "Information request REQ-0005 from Mathebula & Partners" at 2026-04-27T15:21:17.298Z. HTML body href = `http://localhost:3002/auth/exchange?token=ufXdPduQrpso1TdXQeqPARYUBMIkV1Y5jaMiUWbKwDY&orgId=mathebula-partners`. Navigated → exchange completed → redirect to `/projects`. Header shows "Sipho Dlamini" — session established. Snapshot `cycle42-day46-1-portal-home.yml`.
- Notes: L-42 magic-link fix HOLDS for the cycle-41 ad-hoc REQ-0005 dispatch.

#### 46.2 — `/home` → "Supporting medical evidence" pending → click into it
- Result: **PASS** (with title-not-displayed observation)
- Evidence: `cycle42-day46-1-portal-home.yml` (Pending tile = 3); `cycle42-day46-2-portal-requests-list.yml` (REQ-0005 listed `SENT 0/2 submitted`); `cycle42-day46-3-req0005-detail.yml` (detail page shows two items "Latest specialist medical reports" + "Independent expert assessment", both required, with Upload buttons).
- Notes: Portal `/requests` rows show **REQ-number + matter name + status + counter** but NOT the request `name`. Detail-page heading is the matter name only. Same observation as cycle 1 — cosmetic UX not blocking. Logged as **OBS-cycle42-portal-request-title-missing**.

#### 46.3 — Upload 2 test PDFs → submit → state SUBMITTED
- Result: **PASS** (parent stays IN_PROGRESS — by design; see 46.7 gap)
- Evidence: `cycle42-day46-4-after-file-pick-1.yml`, `cycle42-day46-5-after-submit-1.yml` (`Submitted — status: SUBMITTED` + parent `1/2 submitted • status IN_PROGRESS`), `cycle42-day46-6-file-pick-2.yml`, `cycle42-day46-7-after-submit-2.yml` (`2/2 submitted • status IN_PROGRESS`).
- DB confirmation:
  ```text
  REQ-0005 (information_requests): status=IN_PROGRESS, items=2, submitted=2
  request_items:
    9c416b24-… "Latest specialist medical reports" SUBMITTED doc=2a3d25e1-3f64-4952-804f-b0062953a5e3 submitted_at=2026-04-27T15:29:31Z
    cc6f025c-… "Independent expert assessment"     SUBMITTED doc=0d6c8cf6-9723-48b1-8053-153f8f31bcac submitted_at=2026-04-27T15:30:09Z
  ```
- Notes: Presigned-URL upload flow (init → S3 PUT → confirm) worked end-to-end for both items; `document_id` populated. Parent transitions SENT → IN_PROGRESS at first submit (L-43 + L-47 holds). Stays IN_PROGRESS at 2/2 — by design, only firm-side acceptance flips parent to COMPLETED.

#### 46.4 — `/trust` → balance R 70 100 (R 70 000 expected + R 100 carry)
- Result: **PASS**
- Evidence: `cycle42-day46-8-portal-trust.yml`. Navigation to `/trust` correctly redirected to `/trust/cc390c4f-…` (RAF matter UUID — BUG-CYCLE26-11 fix carry-forward). Trust balance card "R 70 100,00 / As of 27 Apr 2026 / Matter cc390c4f".
- Notes: Reconciles to firm-side post-Day 45 client_ledger_card balance for Sipho.

#### 46.5 — Transaction list shows both deposits + carry-forward retest deposit
- Result: **PASS**
- Evidence: `cycle42-day46-8-portal-trust.yml` Transactions table (3 rows, descending by entry recency):
  - 27 Apr 2026 / DEPOSIT / "Top-up per engagement letter" / R 20 000,00 / running R 70 100,00 (Day 45 NEW)
  - 27 Apr 2026 / DEPOSIT / "Cycle 29 retest BUG-CYCLE26-11" / R 100,00 / running R 50 100,00 (carry)
  - 27 Apr 2026 / DEPOSIT / "Initial trust deposit — RAF-2026-001" / R 50 000,00 / running R 50 000,00 (Day 10)
- Notes: Running balance reads correctly bottom-up: 50 000 → 50 100 → 70 100. **Portal-side running-balance presentation is BETTER than firm-side** (which has the OBS-cycle1-running-balance-sort cosmetic gotcha). All amounts formatted `R N NNN,NN`. Scenario expected 2 deposits — the 3rd row is the documented BUG-CYCLE26-11 retest carry-forward.

#### 46.6 — Passive isolation spot-check
- Result: **PASS**
- Evidence: `cycle42-day46-8-portal-trust.yml` (transactions table — zero R 25 000 / Moroka entries); `cycle42-day46-9-portal-projects.yml` (only "Dlamini v Road Accident Fund" in matters list).
- Notes: `/trust` shows only RAF matter UUID with Sipho-owned transactions. `/projects` shows only RAF. `/requests` shows only Sipho's RAF requests (REQ-0001/0002/0004/0005 — no REQ-0003 EST/Moroka leak). Portal contact scoping holds 32 days into the lifecycle.

#### 46.7 — `/home` "Pending info requests" no longer shows medical evidence request
- Result: **FAIL — new gap GAP-L-92**
- Evidence: `cycle42-day46-10-portal-home-after.yml` (Pending tile still reads "3" post-submit; pre-walk also "3"); `cycle42-day46-11-portal-requests-after.yml` (REQ-0005 still listed as `IN_PROGRESS 2/2 submitted`).
- Notes: After 2/2 items submitted on REQ-0005, `/home` "Pending info requests" count remained at 3. Filter at `portal/app/(authenticated)/home/page.tsx:67` is `data.filter((r) => r.status !== "COMPLETED").length` — IN_PROGRESS counts as pending. From Sipho's POV the request is "done on my side" but the headline number says he still has 3 things to do. Cycle 1 logged this as a Product question (`OBS-Day46-PendingTileSemantics`); cycle 42 elevates to **GAP-L-92** because the scenario expectation has stood through 41 cycles and the fix is small.

#### Console errors
- Result: **PASS**
- Notes: 0 errors / 0 warnings across all Day 46 navigation (login → home → requests list → REQ-0005 detail → 2 uploads → /trust → /projects → /home). Cycle 1's two `/portal/trust/movements?limit=1` 404s are GONE — that endpoint resolves cleanly now.

#### Trust isolation cross-check
- Result: **PASS**
- Notes: Portal `/trust/cc390c4f-…` only shows Sipho's 3 RECORDED deposits totalling R 70 100. No Moroka R 25 000 line item. `/projects` only shows RAF. `/requests` only shows Sipho's 4 requests. Magic-link auth correctly scoped to portal_contact.organization=Sipho.

### Day 46 summary checks (per scenario)

- [x] Second info request lifecycle complete (items SUBMITTED end-to-end via presigned-URL flow; parent IN_PROGRESS by design)
- [x] Trust balance update visible on portal (R 70 100 matches firm-side; both deposits visible + carry-forward retest)
- [x] Isolation holds — no Moroka data leak 32 days after Day 14 onboarding
- [ ] Pending info requests tile drops after submission — **FAIL** (GAP-L-92)

### Gaps Found

#### NEW

- **GAP-L-92 — MEDIUM** — Portal home "Pending info requests" tile over-counts: includes IN_PROGRESS requests where ALL items are SUBMITTED. After Sipho uploaded REQ-0005's 2/2 items, the home tile remained at 3 instead of dropping to 2. Source: `portal/app/(authenticated)/home/page.tsx:67` filter `data.filter((r) => r.status !== "COMPLETED").length`. Suggested fix scope: S (~30 min) — change filter to also exclude IN_PROGRESS requests where all items are SUBMITTED, OR introduce an "AWAITING_REVIEW" pseudo-state semantically distinct from IN_PROGRESS, OR change the home tile copy to "Open info requests" and decrement on item-submission rather than parent-status. Owner: Product → Dev. Severity MEDIUM — directly contradicts scenario §46.7; portal contact gets a false sense of pending work. Evidence: `cycle42-day46-10-portal-home-after.yml`, `cycle42-day46-11-portal-requests-after.yml`. Backend support: `GET /portal/requests` already returns request status; would need to add per-request item counts (or compute client-side via separate fetch).

#### Re-observed (NOT re-logged per dispatch carry-forward list)

- **OBS-cycle1-running-balance-sort** (firm-side cosmetic) — not surfaced on portal-side this turn.
- **Trust-deposit nudge email body polish** — no new deposit fired; not re-observed.
- **Record Deposit raw UUIDs** — firm-side; not surfaced.
- **Portal Type column raw enum** — not seen on Day 46 navigation.
- **GAP-L-54 beneficial owners** — not exercised.

#### Cosmetic observations (not logged as gaps)

- **OBS-cycle42-portal-request-title-missing** — Portal `/requests` rows + REQ detail page never display the request's `name` ("Supporting medical evidence"); only matter name and REQ-number. Same as cycle 1.
- **OBS-cycle42-portal-status-IN_PROGRESS-misleading** — Tied to GAP-L-92. When 2/2 items SUBMITTED, parent stays IN_PROGRESS until firm acceptance — list/detail headline reads `IN_PROGRESS 2/2 submitted` (technically correct, semantically confusing).

### DB final state

```text
information_requests (5 rows, REQ-0005 transitioned):
  REQ-0001 a0306375-… SENT       carry-forward
  REQ-0002 d8a58ade-… COMPLETED  carry-forward
  REQ-0003 de3cffc7-… SENT       carry-forward (Moroka — not visible to Sipho)
  REQ-0004 d7dc4faf-… SENT       carry-forward Day 45 FICA
  REQ-0005 73babd4e-… IN_PROGRESS  [TRANSITIONED THIS DAY: SENT → IN_PROGRESS @ 15:29Z]

request_items for REQ-0005 (NEW THIS DAY):
  9c416b24-… "Latest specialist medical reports" SUBMITTED doc=2a3d25e1-… submitted_at=2026-04-27T15:29:31Z
  cc6f025c-… "Independent expert assessment"     SUBMITTED doc=0d6c8cf6-… submitted_at=2026-04-27T15:30:09Z

trust_transactions: unchanged (4 rows, no Day 46 mutations)
client_ledger_cards: unchanged (Sipho R 70 100, Moroka R 25 000)

documents added (in document_metadata + S3): 2 PDFs uploaded by Sipho (~580 bytes each)
```

### Verify-focus items observed

- **L-42 (magic-link to portal :3002 with orgId)**: VERIFIED — REQ-0005 magic-link exchange landed Sipho on portal with auth cookies set.
- **L-43 (portal request-item submitted listener)**: VERIFIED — both submitted items have `status=SUBMITTED`, `document_id` linked, `submitted_at` populated.
- **L-44 / portal modules sync**: VERIFIED — portal sidebar shows information_requests, trust_accounting, deadlines, document_acceptance, etc.
- **L-47 (portal parent-request status sync)**: VERIFIED — parent transitions SENT → IN_PROGRESS at first item submit; stays IN_PROGRESS at 2/2 (by design). However home-tile count semantics fall out of step (GAP-L-92).
- **L-52 (portal trust-ledger sync for RECORDED deposits)**: VERIFIED — Day 45 R 20 000 deposit + carry-forward R 100 + Day 10 R 50 000 all visible with correct amounts/descriptions/dates/running balances.
- **BUG-CYCLE26-11 (trust deeplink → matter UUID)**: VERIFIED-CARRY-FORWARD — `/trust` redirects to `/trust/cc390c4f-…` (matter UUID).
- **GAP-L-67 (ad-hoc info request items)**: VERIFIED-CARRY-FORWARD via cycle-41 retest — REQ-0005 was created with custom 2 items via the now-fixed dialog and successfully delivered to Sipho's portal, where he submitted both end-to-end. Full ad-hoc create→deliver→portal-respond loop closed.
- **Cycle-1 GAP-L-68 (portal home /portal/trust/movements?limit=1 404)**: VERIFIED-FIXED — endpoint resolves cleanly; "Last trust movement" tile renders R 20 000,00 / 27 Apr 2026.

### Stack at end-of-turn

- Backend PID 41372 (no restart this cycle)
- Gateway PID 71426 ext (unchanged)
- Frontend PID 5771 (unchanged)
- Portal PID 5677 (unchanged)
- Single Sipho portal tab on `/requests` after walk
- 2 PDF artefacts: `.playwright-mcp/day46/discharge-summary.pdf` (578 B), `.playwright-mcp/day46/orthopaedic-report.pdf` (581 B)

### Branch state

- No code changes this turn.
- New evidence files (under `qa_cycle/checkpoint-results/`):
  - `cycle42-day46-1-portal-home.yml`
  - `cycle42-day46-2-portal-requests-list.yml`
  - `cycle42-day46-3-req0005-detail.yml`
  - `cycle42-day46-4-after-file-pick-1.yml`
  - `cycle42-day46-5-after-submit-1.yml`
  - `cycle42-day46-6-file-pick-2.yml`
  - `cycle42-day46-7-after-submit-2.yml`
  - `cycle42-day46-8-portal-trust.yml`
  - `cycle42-day46-9-portal-projects.yml`
  - `cycle42-day46-10-portal-home-after.yml`
  - `cycle42-day46-11-portal-requests-after.yml`

### Next action

**Orchestrator**: Decide whether to spec/fix **GAP-L-92** (MEDIUM — portal home pending count over-states by including fully-submitted IN_PROGRESS requests) before advancing, or defer past Day 60. The bug is cosmetic-only (no data leak / no isolation break) but directly contradicts scenario §46.7 expectation. Suggested fix scope: S (~30 min). Day 47–59 in the scenario contain no checkpoints (Day 46 jumps straight to Day 60 firm closure). If GAP-L-92 is deferred, advance QA Position to **Day 60 — 60.1**. Branch `bugfix_cycle_2026-04-26-day46` ready for commit + push.

## Cycle 45 Retest — PR #1193 GAP-L-92 — 2026-04-27 SAST

**Scope**: Verify GAP-L-92 fix on `main` after PR #1193 (squash `6c30e247`) merged. New filter rule: `(r.status === "SENT" || r.status === "IN_PROGRESS") && r.submittedItems < r.totalItems` (positive allow-list of actionable statuses; CR follow-up `65e5fd76` tightened from the negated form). 8 unit-test cases (incl. CANCELLED regression guard); 185/185 portal tests pass.

**Setup**: `git log -1 --oneline main` → `6c30e247 fix(cycle-2026-04-26 Day 46): portal home pending-tile semantics + Day 46 walk results (#1193)`. `svc.sh status` → backend/gateway/frontend/portal all healthy. Frontend HMR — no restart needed.

**Authentication**: Fresh magic-link via `POST http://localhost:8080/portal/auth/request-link` (email `sipho.portal@example.com`, orgId `mathebula-partners`). Response token `JdFvUQqGML8ioCCu0X3RarxxGBiH6sHfrvFWw5qlG-g` (Mailpit message `88oo8hqK9MMRSxZh4StdNA` corroborates). Exchanged at `http://localhost:3002/auth/exchange?token=…&orgId=mathebula-partners` → header reads "Sipho Dlamini".

| Step | Result | Evidence |
|------|--------|----------|
| 1. Sipho auth via fresh magic-link | PASS | Mailpit `88oo8hqK9MMRSxZh4StdNA`; exchange to `:3002`; header "Sipho Dlamini" |
| 2. `/home` "Pending info requests" tile reads "2" | PASS | `cycle45-retest-PR1193-GAP-L-92-portal-home.yml` (line 88-95: `link "Pending info requests 2"` → `paragraph: "2"`); `cycle45-retest-PR1193-GAP-L-92-home-tile.png` |
| 3. `/requests` enumeration — REQ-0005 IN_PROGRESS 2/2, REQ-0004 SENT 0/3, REQ-0002 COMPLETED 0/3, REQ-0001 SENT 0/3 (4 rows visible to Sipho; REQ-0003 Moroka invisible per isolation) | PASS | `cycle45-retest-PR1193-GAP-L-92-portal-requests.yml` (lines 60-99); `cycle45-retest-PR1193-GAP-L-92-requests-list.png` |
| 4. Expected count = 2 (REQ-0001 SENT 0/3 + REQ-0004 SENT 0/3); REQ-0005 IN_PROGRESS 2/2 excluded (regression case); REQ-0002 COMPLETED excluded; matches tile "2" | PASS | enumeration cross-reference confirms `(SENT \|\| IN_PROGRESS) && submittedItems < totalItems` semantics |

**Outcome: VERIFIED.** Tile count "2" matches expected count 2. The CR-tightened filter correctly excludes the IN_PROGRESS-fully-submitted regression case (REQ-0005 2/2) and the COMPLETED case (REQ-0002), while keeping the two actionable SENT requests (REQ-0001, REQ-0004). 0 console errors. Day 60 walk should now proceed.

### Branch state

- No code changes this turn (read-only QA retest on `main`).
- New evidence files (under `qa_cycle/checkpoint-results/`):
  - `cycle45-retest-PR1193-GAP-L-92-portal-home.yml`
  - `cycle45-retest-PR1193-GAP-L-92-portal-requests.yml`
  - `cycle45-retest-PR1193-GAP-L-92-home-tile.png`
  - `cycle45-retest-PR1193-GAP-L-92-requests-list.png`

### Next action

GAP-L-92 → VERIFIED. Day 60 — 60.1 walk is unblocked. Cut fresh `bugfix_cycle_2026-04-26-day60` from `main` (HEAD `6c30e247`) for the next per-day cycle (firm matter closure + Statement of Account).
