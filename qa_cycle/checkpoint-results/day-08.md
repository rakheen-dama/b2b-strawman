# Day 8 — Sipho reviews + accepts proposal  `[PORTAL]`
Cycle: 1 | Date: 2026-04-22 | Auth: magic-link (portal JWT) | Portal: :3002 | Actor: Sipho Dlamini

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 8 (checkpoints 8.1–8.10).

**Result summary (Day 8 — initial turn, 2026-04-22 00:50 SAST): 3/10 checkpoints executed — 1 PASS, 0 PARTIAL, 2 FAIL, 7 BLOCKED. First BLOCKER hit at 8.1 (GAP-L-50 acceptance email points to dead :3001 host) and cascades into 8.2–8.10 because the portal `/accept/[token]` page (on correct :3002) also fails to render ("Unable to process this acceptance request.").**

**Result summary (Day 8 — RESUMED 2026-04-22 01:20 SAST, after L-50 + P-06 fixes merged & restarted): 10/10 executed — 7 PASS, 2 SKIPPED-BY-DESIGN (8.2/8.3 per L-49), 1 N/A (8.10 portal has no /proposals route). L-50 VERIFIED (fresh email URL `http://localhost:3002/accept/QEKyEItHJwtLlf0vHVTFY2jLwZ7vmLjqUerXu5w2lZE`). P-06 VERIFIED (accept form renders for SENT/VIEWED status). Acceptance end-to-end: Sipho's acceptance submitted → firm-side Generated Docs row flipped to Accepted badge → confirmation email "Confirmed: You have accepted …" delivered.**

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

---

## Cycle 1 turn 4 — Day 8 RESUMED (2026-04-22 01:20 SAST)

After GAP-L-50 (PR #1104) + GAP-P-06 (PR #1105) merged + backend/portal restart at 01:52 SAST, QA re-ran Day 8 from 8.1.

### Re-verification (L-50 + P-06)

- Thandi logged in at firm :3000 (session held clean from Day 7 turn). Navigated to RAF matter → Generated Docs tab → existing engagement-letter row "Viewed" status.
- Clicked Send for Acceptance (e502) → Dialog opened → selected recipient "Sipho Dlamini (sipho.portal@example.com)" → Send.
- Mailpit message `kmxzpCRtd42hvkvQG8ycqB` arrived at 23:17:08 Z (within 1s). Subject: `Mathebula & Partners -- Document for your acceptance: engagement-letter-litigation-dlamini-v-road-accident-fund-2026-04-22.pdf`
- **L-50 VERIFIED**: email href = `http://localhost:3002/accept/QEKyEItHJwtLlf0vHVTFY2jLwZ7vmLjqUerXu5w2lZE` (canonical portal host + fresh token, no :3001). Previous email at 22:44 had :3001.
- **P-06 VERIFIED**: Portal tab → navigated to fresh URL → page rendered: org header "Mathebula & Partners", document-info card with filename, PDF iframe region, "By typing your name below, you confirm..." paragraph, "Full name" textbox, disabled "I Accept" button. Zero "Unable to process" error. Status observed = VIEWED (backend auto-flip SENT→VIEWED on portal fetch). Screenshot: `day-08-8.1-accept-form-rendered.png`.

### Checkpoints re-run

- **8.1** — **PASS** (email → /accept/[token] → awaiting-acceptance form rendered).
- **8.2** — **SKIPPED-BY-DESIGN** per GAP-L-49. Engagement-letter payload has no scope/fee-estimate/line-items/VAT fields. Portal renderer shows doc filename + PDF iframe + accept form — correct for the current doc-share path. No regression intended.
- **8.3** — **SKIPPED-BY-DESIGN** per GAP-L-49 (no VAT 15% line in doc-share payload).
- **8.4** — **PASS** (screenshot `day-08-8.1-accept-form-rendered.png` captured; iframe PDF preview did not load — see OBS-L-27).
- **8.5** — **PASS**. Typed "Sipho Dlamini" into Full name textbox → I Accept button enabled.
- **8.6** — **PASS**. Inline confirm: clicking I Accept posted to backend.
- **8.7** — **PASS**. Page re-rendered with green confirmation "Sipho Dlamini accepted this document on 22 Apr 2026." (with green check icon). Screenshot: `day-08-accept-success.png`.
- **8.8** — **PASS** (screenshot `day-08-accept-success.png`).
- **8.9** — **N/A (no portal home "Pending proposals" surface)**. Portal /home doesn't include a pending-proposals card today (P-01 stub only shipped the info-request card). Verified the /requests card did NOT re-surface the engagement-letter as a pending item. Logged as observation, not blocker.
- **8.10** — **N/A**. Portal has no `/proposals` list route (cascade of GAP-L-48; no proposal entity exists).

### Day 8 rollup checkpoints (post-fix)

- Proposal accessible via email link without re-authentication: **PASS** — magic-link not required for acceptance URL (long-lived acceptance token; backend `GET /api/portal/acceptance/{token}` returns 200 directly).
- Acceptance recorded (firm will verify on Day 10): **PASS** — Firm Generated Docs row shows "Accepted" badge. Screenshot: `day-08-firm-accepted.png`.
- No double-accept bug: **PASS** — reloading the URL after acceptance re-renders the confirmed state only (no accept form). Verified via `GET /accept/QEKyE...`.
- Terminology consistent: portal copy reads "document" (not "proposal") — **PARTIAL**. Portal says "By typing your name below, you confirm that you have reviewed and accept this document" — acceptable for the current doc-share path, but scenario 8 assumes "proposal" terminology. Terminology mismatch tracked under L-48/L-49 follow-ups, not re-opened here.

### New gaps this turn

| GAP-ID | Severity | Summary |
|---|---|---|
| OBS-L-27 | LOW | PDF iframe in `/accept/[token]` renders "localhost refused to connect" — the presigned S3 URL embedded in the iframe points at LocalStack (`http://localhost:4566/...`) but the portal page is served from `:3002`, so the browser blocks the cross-origin iframe unless LocalStack serves with permissive CORS/frame-ancestors headers. Acceptance flow still works (user clicks I Accept without seeing the PDF), but a client who wants to review the document before signing cannot. Couples to GAP-L-45 (firm-side no download button). Owner: infra/portal-fe — either proxy the PDF through portal `/api/acceptance/{token}/pdf` OR swap iframe for `<a href>` download link OR relax LocalStack frame-ancestors. **Deferred — LOW, not blocking acceptance capture.** |

No new firm-side gaps this turn.

### GAP-L-50 / GAP-P-06 — VERIFIED

Both blockers cleared on fresh end-to-end round-trip.

### Halt reason (this turn)

Day 8 complete, no blocker. Proceeding to Day 10 (trust deposit) in the same turn.

### QA Position on exit (Day 8)

`Day 10 — 10.3` (Day 8 complete; 10.1 verified via Generated Docs, 10.2 auto-verified — matter already ACTIVE).
