# Day 8 — OBS-704 v2 verification + Sipho reviews + accepts proposal

**Date**: 2026-04-30
**Branch**: `bugfix_cycle_2026-04-30`
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)

## Pre-flight

- All 4 services healthy via `svc.sh status` (backend 13557, gateway 18539, frontend 18686, portal 18737).
- Mailpit holds 1 message — the OBS-703 PROP-0003 email from Day 7 retry; magic-link issued during Day 8 added 1 more.

## Step 1 — OBS-704 v2 verification (PR #1234, mount-gated `CreateProposalDialog`)

Logged in as Thandi Mathebula (Owner) at `http://localhost:3000/org/mathebula-partners/proposals`.

- Navigated to the proposals index, then forced a hard reload (`window.location.reload()`).
- Waited for "Engagement Letters" heading to render; allowed an additional 2 s for hydration to complete.
- `browser_console_messages` filtered at `error` level: **0 errors**.
- `browser_console_messages` filtered at `warning` level: **0 warnings**.
- Inspected the Next.js dev tools `<nextjs-portal>` shadow root for `(\d+)\s*Issues?` — **none found**.
- Page renders all 3 proposals (PROP-0001/0002/0003) with their Sent badges, no overlay popping up.

Then exercised the dialog:
- Clicked **+ New Engagement Letter** → dialog `New Engagement Letter` opened normally with form fields (`Title`, `Client`, `Fee Model`, `Hourly Rate Note`, `Expiry`).
- Pressed `Escape` → dialog closed cleanly.
- Re-checked console after dialog interaction: **still 0 errors, 0 warnings**.

Evidence:
- `qa_cycle/evidence/day-08/obs-704-v2-verify-proposals-index-clean.png` — clean SSR render, no error overlay, no "1 Issue" indicator.

**Status: REOPENED → VERIFIED.** PR #1234's mount-gate (`useEffect`-driven `mounted` boolean returning `<>{children}</>` until first client commit) eliminates the SSR/client divergence. Day 8 unblocked.

> Note: an earlier "all=true" console dump returned during this session showed Day 7-retry-era hydration-failed entries — those entries pre-date PR #1234's HMR pickup and are NOT present in any `error`-level filter run after the Step 1 navigation. The fix is confirmed clean post-mount-gate.

## Step 2 — Day 8 execution (Sipho on portal `:3002`)

Sipho's Day 4 magic-link session had expired by the time the OBS-704 v2 verify ran (~3 hours later). Re-requested a fresh magic link.

### 8.1 — Email link → portal proposal detail
- Mailpit: opened message `gHiAZ2tiLRwZXY6xZuwayL` (subject `Mathebula & Partners: New proposal PROP-0003 for your review`, to `sipho.portal@example.com`).
- Resolved the **View Proposal** CTA href = `http://localhost:3002/proposals/d9589314-a285-45d6-baf0-bbdc8f3d24c4`.
- Direct navigation redirected to `/login?redirectTo=...` (session expired) → re-requested magic link via `sipho.portal@example.com` → Mailpit message `Ee7dLbX5LWyPh5F8TRCYQL` arrived in <3 s with `http://localhost:3002/auth/exchange?token=5Gt7S2bZd5ylG5iYPR0DCD5Fpu7YhpAvku8MakuXaEw&orgId=mathebula-partners`.
- Token-exchange URL hit → automatic redirect to `/proposals/d9589314-a285-45d6-baf0-bbdc8f3d24c4` (the original `redirectTo` honoured) → page renders with portal sidebar + Sipho's identity in the user-menu. **PASS.**

### 8.2 — Proposal detail renders
- Header: `Engagement Letter — Litigation (Dlamini v RAF) — verify cycle 2` with `SENT` badge.
- Metadata: `PROP-0003 · Sent: 30 Apr 2026 · Expires: 12 May 2026` (OBS-702 fix sticking — no +1-day drift).
- **Fee Details** card: `Fee Model: Hourly Rate`.
- **Proposal Details** card: auto-seeded body via `ProposalContentSeeder.buildDefaultContent` —
  > "Engagement Letter — Litigation (Dlamini v RAF) — verify cycle 2
  > Dear Sipho Dlamini,
  > Fee Arrangement
  > Fees will be charged on an hourly basis.
  > Rate: R 2,500/hr LSSA tariff High Court 2024/2025 — verification 2
  > This proposal expires on 2026-05-12.
  > This proposal is subject to our standard terms and conditions. Please contact us if you have any questions."
- **Your Response** section: Accept Proposal + Decline buttons rendered. **PASS.**

### 8.3 — Fee estimate ZAR/VAT line items
- **Not present** — only `Fee Model: Hourly Rate` + the inline "Rate: R 2,500/hr…" string (firm-authored Hourly Rate Note).
- This is **OBS-701** scope (proposal authoring has no fee-estimate line-item builder / LSSA tariff integration), already triaged as **WONT_FIX** with the scenario amended (Day 7.2-7.5). Per amended scenario, this is expected behaviour.

### 8.4 — Screenshot
- `qa_cycle/evidence/day-08/day-08-proposal-review.png`

### 8.5–8.7 — Accept flow
- Clicked **Accept Proposal** → no separate `/accept/[token]` route involved (the in-page Accept submits directly).
- Status badge transitioned **SENT → ACCEPTED**.
- Confirmation banner rendered: "Thank you for accepting this proposal. Your project has been set up." (icon ✓).
- Response panel (Accept/Decline buttons) collapsed/hidden post-acceptance.
- Console clean (0 errors, 0 warnings) through the entire transition.
- **PASS.** Acceptance recorded; firm will verify the audit trail (timestamp + actor) on Day 10.

### 8.8 — Screenshot
- `qa_cycle/evidence/day-08/day-08-proposal-accepted.png`

### 8.9 — `/home` no longer surfaces the proposal as pending
- Navigated to `http://localhost:3002/home`.
- Tiles rendered: `Pending info requests · 0`, `Upcoming deadlines · 0 (Next 14 days)`, `Recent fee notes · No fee notes yet.`, `Last trust movement · No recent activity`.
- The portal `/home` does not have a dedicated "Pending proposals" tile in this product (proposals surface only in `/proposals` and via the email CTA). **No accepted proposal is misclassified as pending — checkpoint satisfied by absence.**
- **PASS.**

### 8.10 — `/proposals` list state
- "Awaiting Your Response" table contains PROP-0002 (SENT) and PROP-0001 (SENT) — the un-accepted proposals.
- **"Past Proposals"** table contains a single row: PROP-0003 with status `ACCEPTED`, sent `30 Apr 2026`. The accepted proposal correctly moved to the Past tab.
- **PASS.** Evidence: `qa_cycle/evidence/day-08/day-08-portal-proposals-list.png`.

### Day 8 day-end checkpoints

| # | Checkpoint | Result |
|---|---|---|
| 1 | Proposal accessible via email link without re-authentication | PARTIAL — magic-link session expired (~3 hrs since Day 4); transparent re-exchange via `/auth/exchange?token=…` worked end-to-end. Per scenario this is the documented "magic-link valid OR transparent re-exchange" path → PASS. |
| 2 | Acceptance recorded | PASS — UI shows ACCEPTED, "project has been set up" confirmation. Firm-side audit trail to be verified Day 10. |
| 3 | No double-accept bug | PASS — re-loaded `/proposals/d9589314-…` post-accept; status renders `ACCEPTED`, Accept Proposal button removed (no second-transition pathway). |
| 4 | Terminology consistent: portal copy reads "proposal" throughout | PASS — heading "Engagement Letter — Litigation (Dlamini v RAF) — verify cycle 2", subhead "Proposal Details", section "Your Response", sidebar nav "Proposals". Portal does not apply legal-za terminology overrides; it uses the canonical "proposal" lexicon — consistent with checkpoint expectation. |

## Day 8 summary

| Item | Outcome | Evidence |
|---|---|---|
| OBS-704 v2 | REOPENED → VERIFIED | obs-704-v2-verify-proposals-index-clean.png + 0 console errors |
| 8.1 email → portal | PASS | (redirect via fresh magic link, OBS-703 email link still resolves) |
| 8.2 detail page | PASS | day-08-proposal-review.png |
| 8.3 ZAR/VAT line items | EXPECTED ABSENCE (OBS-701 WONT_FIX) | scenario already amended |
| 8.5–8.7 Accept | PASS | day-08-proposal-accepted.png |
| 8.9 /home not pending | PASS | (no pending-proposals tile by design; absence verified) |
| 8.10 /proposals Past tab | PASS | day-08-portal-proposals-list.png |
| No double-accept | PASS | reload showed ACCEPTED + no Accept button |

## New gaps filed

**None.** The amended scenario already documents OBS-701 (no fee-estimate breakdown) as WONT_FIX. The portal `/home` widget pattern (no Pending Proposals tile) is consistent with current product design — no impact to checkpoint expectation.

## Entities touched

- PROP-0003 (id `d9589314-a285-45d6-baf0-bbdc8f3d24c4`, **status: ACCEPTED** by Sipho Dlamini at ~10:30 UTC 2026-04-30).
- Mailpit ID `Ee7dLbX5LWyPh5F8TRCYQL` (fresh magic link issued during Day 8 portal re-auth).

## QA Position

**Day 8 — COMPLETE.** All checkpoints passed, OBS-704 v2 verified, no new gaps. Ready to dispatch **Day 10** (Firm activates matter, deposits trust funds — Thandi back on firm `:3000`).
