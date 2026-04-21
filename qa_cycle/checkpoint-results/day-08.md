# Day 8 — Sipho reviews + accepts proposal  `[PORTAL]`
Cycle: 1 | Date: 2026-04-22 | Auth: magic-link (portal JWT) | Portal: :3002 | Actor: Sipho Dlamini

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 8 (checkpoints 8.1–8.10).

**Result summary (Day 8): 3/10 checkpoints executed — 1 PASS, 0 PARTIAL, 2 FAIL, 7 BLOCKED. First BLOCKER hit at 8.1 (GAP-L-50 acceptance email points to dead :3001 host) and cascades into 8.2–8.10 because the portal `/accept/[token]` page (on correct :3002) also fails to render ("Unable to process this acceptance request.").**

New gaps: **GAP-P-06** (HIGH/BLOCKER, portal-fe — `/accept/[token]` page renders error screen even when backend returns valid 200 acceptance payload; UI renderer rejects the minimal doc-share payload shape; couples to GAP-L-49 + GAP-L-50).

## Session prep

Portal tab already authenticated from Day 4 resume (Sipho's portal_jwt + portal_customer still valid in localStorage). No fresh magic-link needed.

## Phase A: Open proposal from email

### Checkpoint 8.1 — Mailpit → open the proposal email → click the proposal link → lands on `/proposals/[id]`
- Result: **FAIL — BLOCKER (GAP-L-50)**
- Evidence:
  - Mailpit `jtrVksK2HnwPVj9KCDDuP6` email has href = `http://localhost:3001/accept/08OMMtVvodcXZEQ3oBeB5HSl144JCXnNomFR_HZbsng`.
  - Direct-nav to this URL is **blocked by Playwright hook** (port 3001 not in allowed-list). `curl http://localhost:3001/` returns exit 7 (connection refused). No service bound on 3001 in current stack.
  - Attempted fallback on correct portal port: `http://localhost:3002/accept/08OMMtVvodcXZEQ3oBeB5HSl144JCXnNomFR_HZbsng` — portal route resolves (200 OK at HTTP level) but page renders `"Unable to process this acceptance request. Please contact the sender."` with a red warning triangle. No proposal content visible. Screenshot: `day-08-accept-failure.png`.
  - Backend API probe on same token: `GET /api/portal/acceptance/08OMMtVvodcXZEQ3oBeB5HSl144JCXnNomFR_HZbsng` → **200** with valid payload `{requestId, status:"VIEWED", documentTitle, documentFileName, expiresAt:"2026-04-28T22:44:58Z", orgName:"Mathebula & Partners", orgLogo, brandColor:"#1B3358", acceptedAt:null, acceptorName:null}`. Backend works; portal UI page logic is rejecting this shape.
  - Console shows 2 infos only — no error trace captured during SSR/CSR render. No 4xx/5xx in network waterfall (the backend 200 is the only acceptance-related call).
  - Logged **GAP-P-06** (HIGH/BLOCKER, portal-fe — /accept/[token] render failure).

### Checkpoint 8.2 — Verify proposal detail page renders: scope, fee estimate breakdown (tariff lines + totals in ZAR incl. VAT), effective date, expiry, Accept / Decline buttons
- Result: **BLOCKED (cascade of 8.1)**
- Evidence: No detail page reached.

### Checkpoint 8.3 — Fee estimate renders with ZAR currency symbol + VAT 15% line
- Result: **BLOCKED (cascade of 8.1; also underlying GAP-L-49 — backend payload has no fee/VAT structure)**

### Checkpoint 8.4 — Screenshot proposal review
- Result: **PASS (captured — but evidence of failure state)**
- Evidence: `day-08-accept-failure.png` — error card centred on blank portal shell.

### Checkpoint 8.5–8.10 — Click Accept, confirm, status transitions, home update, /proposals list
- Result: **BLOCKED (cascade of 8.1)**

## Day 8 checkpoints (final rollup)

- Proposal accessible via email link without re-authentication: **FAIL** — email link host wrong + portal render broken.
- Acceptance recorded (firm will verify on Day 10): **BLOCKED** — cannot accept.
- No double-accept bug: **N/A** — never reached Accept.
- Terminology consistent: portal copy reads "proposal" throughout: **N/A** — no proposal content rendered.

## New gaps

| GAP-ID | Severity | Summary |
|---|---|---|
| GAP-P-06 | **HIGH / BLOCKER** | Portal `/accept/[token]` page renders `"Unable to process this acceptance request. Please contact the sender."` even when backend `GET /api/portal/acceptance/{token}` returns **200** with valid payload. Likely causes: (a) portal UI renderer validates payload shape against a richer proposal schema (expects `lineItems`, `totalZar`, `vatAmount`, `scope`, `effectiveDate`) that backend doesn't populate for doc-share acceptances; (b) portal expects acceptance `status == "SENT"` but backend already flipped to `"VIEWED"` on the fetch (confirmed — response `status:"VIEWED"`); (c) missing `acceptorName` triggers error path. Needs inspection of `portal/app/(public)/accept/[token]/page.tsx` render logic. Owner: portal-fe (first), backend second (may need proposal payload extension if (a)). Blocks Day 8 + every downstream portal-proposal-acceptance moment in the lifecycle (days 10 Phase A proposal-accepted-verify + any future proposals). |

## Carry-forward observations
- GAP-L-50 (email host wrong) is the upstream root cause for most real-client failures; GAP-P-06 surfaces only when the URL is hand-repaired to :3002. Both need to land for portal acceptance to work end-to-end.
- GAP-L-49 (no line-item / VAT structure in proposal) is the third piece: even if portal `/accept` renders, the client has no way to verify fee estimate / VAT breakdown (scenario 8.2 / 8.3 hard-require this).

## Halt reason
First BLOCKER (GAP-P-06) hit at 8.1, with upstream blocker GAP-L-50 also present. Per execution rules, halted. Day 8 is compound-blocked: need L-50 (email host) + P-06 (portal render) + L-49 (payload schema) fixed before end-to-end proposal acceptance will work.

## QA Position on exit
`Day 8 — 8.1 (blocked pending GAP-L-50 + GAP-P-06 + GAP-L-49)`. Day 10 (firm-side proposal verify + trust deposit) is partly blocked: Phase A (verify proposal acceptance flowed through) cannot be asserted until Day 8 unblocks, but Phase B (trust deposit) might be executable in isolation.

## Screenshots
- `day-08-accept-failure.png` — portal `/accept/[token]` error card.
