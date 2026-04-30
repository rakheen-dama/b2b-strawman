# Day 7 retry — OBS-702 / OBS-703 / OBS-704 verification

**Date**: 2026-04-30
**Branch**: `bugfix_cycle_2026-04-30`
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)
**Actor**: Thandi Mathebula (Owner) on firm `:3000`

## Pre-flight

- Mailpit cleared (`DELETE /api/v1/messages` → 200, total = 0).
- Stack health: backend 11473 → restarted to 13725 (12:09:32) — see "Stale backend" finding below.
- Browser console clean before navigation.

## Stale backend before retry — discovered & remediated

When polling Mailpit after the first send (PROP-0002, see below), no email arrived even though
backend logs showed `Sent proposal …` + `Portal sync completed`. Root cause:

```
src ProposalSentEmailHandler.java       mtime: Apr 30 12:03:44
target/.../proposal/ProposalService.class mtime: Apr 30 12:03:03
backend JVM started (PID 11473)         lstart: Apr 30 12:03:10
```

The Java source file was on disk at 12:03:44 but the JVM had already finished loading classes at
12:03:10 — the `ProposalSentEmailHandler` was on disk but never compiled into `target/classes`,
so its `@Component` was never registered. The PID handed to me by the orchestrator (11325) was
stale; the running PID was 11473 with no email handler bean.

**Fix**: `bash compose/scripts/svc.sh restart backend` → new PID 13725 (clean compile,
`ProposalSentEmailHandler.class` now present). After restart, sending PROP-0003 produced the
expected portal email within ~5 s.

## Step 1 — OBS-702: proposal expiry timezone drift

- Created PROP-0002 with title "Engagement Letter — Litigation (Dlamini v RAF) — verify cycle",
  Fee Model = Hourly, Hourly Rate Note = "R 2,500/hr LSSA tariff High Court 2024/2025 — verification",
  **Expiry = 2026-05-12**.
- Detail page renders **Expires: May 12, 2026** (NOT May 13).
- Email body (PROP-0003 retest) renders "This proposal will expire on **12 May 2026**" — same day.
- Evidence: `qa_cycle/evidence/day-07-retry/obs-702-verify-expiry.png`,
  `obs-703-verify-mailpit-message.png`.

**Status: VERIFIED**.

## Step 2 — OBS-703: portal email on proposal send

First attempt (PROP-0002, against stale JVM) — no email. After backend restart, second attempt
(PROP-0003) succeeded:

- Sent PROP-0003 (id `d9589314-a285-45d6-baf0-bbdc8f3d24c4`) to Sipho Dlamini at 12:10:36.
- Mailpit polled at +5 s — total = 1 message.
- Subject: `Mathebula & Partners: New proposal PROP-0003 for your review`
- From: `noreply@docteams.app` → To: `sipho.portal@example.com`
- HTML body contains link `http://localhost:3002/proposals/d9589314-a285-45d6-baf0-bbdc8f3d24c4`
  (the `View Proposal` CTA button).
- Backend log confirms the new handler ran:
  `Portal notification sent template=portal-new-proposal contact=c99db0e9-6745-465e-a542-3c842e829758 to=sipho.portal@example.com`
- Evidence:
  - `qa_cycle/evidence/day-07-retry/obs-703-verify-mailpit-message.png` (Mailpit web view)
  - `qa_cycle/evidence/day-07-retry/obs-703-verify-mailpit-message.json` (raw API payload)
  - `qa_cycle/evidence/day-07-retry/obs-703-verify-mailpit-body.html` (rendered HTML body)

**Status: VERIFIED**.

## Step 3 — OBS-704: proposals index hydration mismatch — **FAIL**

Navigated to `/org/mathebula-partners/proposals` (twice — first via direct URL, then dashboard →
proposals to rule out caching). On both loads, console fires:

```
Error: Hydration failed because the server rendered HTML didn't match the client.
…
<DialogTrigger asChild={true}>
  <DialogTrigger data-slot="dialog-tri..." asChild={true}>
    <Primitive.button type="button" aria-haspopup="dialog" aria-expanded={false} ...>
      <Primitive.button.Slot type="button" aria-haspopup="dialog" aria-expanded={false} ...>
        <Primitive.button.SlotClone type="button" aria-haspopup="dialog" aria-expanded={false} ...>
+         <button data-slot="button" data-variant="default" data-size="default"
+           className={"inline-flex items-center justify-center gap-2 …"}
+           aria-controls="radix-_R_4clritrqiqbn5rknelb_"
+           data-state="closed" onClick={function handleEvent} ref={function}>
```

Same surface as the original report — the `New Engagement Letter` `<DialogTrigger asChild>`
button renders divergent HTML between SSR and client (`aria-controls="radix-_R_…"` is
generated client-side and is not present on the server-rendered button). Next.js dev tools
overlay shows "1 Issue" on the page.

**The PR #1231 fix targeted `<ProposalTable now={...}>` (timestamp prop) and removed the
`now` prop in favour of a client-only `useNowMs()` hook**, but the actual hydration mismatch
is anchored on the `<CreateProposalDialog>` button trigger — outside the `<ProposalTable>`
subtree. The page renders correctly, but the error fires per page load and violates the
"frontend must run clean" mandate.

- Evidence:
  - `qa_cycle/evidence/day-07-retry/obs-704-verify-proposals-index-still-broken.png`
  - `qa_cycle/evidence/day-07-retry/obs-704-console-errors.txt`

**Status: REOPENED**. Per brief — "If any FAIL → mark REOPENED, stop, return." — Day 8 not
attempted in this cycle. Need a follow-up fix that addresses the `DialogTrigger asChild`
button, not the table timestamp prop. Likely fixes: (a) gate `CreateProposalDialog` behind a
`useEffect`-mounted flag so the trigger only renders client-side, OR (b) move the
`CreateProposalDialog` opening logic into a route-level state pattern that doesn't introduce
SSR/client divergence. Suspended Radix `aria-controls` ID generation needs investigation —
this is a known Radix pattern and the typical fix is `<Suspense>` wrapping or
`use client` + `useId()`-stable boundary.

## Day 7 verification summary

| Gap     | Outcome   | Evidence                                                                    |
|---------|-----------|-----------------------------------------------------------------------------|
| OBS-702 | VERIFIED  | obs-702-verify-expiry.png + email body "12 May 2026"                        |
| OBS-703 | VERIFIED  | obs-703-verify-mailpit-message.png + .json + .html + backend log line       |
| OBS-704 | REOPENED  | obs-704-verify-proposals-index-still-broken.png + obs-704-console-errors.txt|

## Entities created during retry

- PROP-0002 (id `a4ad3e2e-c2af-4227-a30f-80196b044b6a`, Sent — first attempt against stale JVM, no email)
- PROP-0003 (id `d9589314-a285-45d6-baf0-bbdc8f3d24c4`, Sent — second attempt after backend restart, email delivered)
