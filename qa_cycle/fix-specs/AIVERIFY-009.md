# AIVERIFY-009 — ExecutionGateCard hydration / locale mismatch

- **Severity**: Low
- **Disposition**: **SPEC_READY** — real hydration/locale bug; mechanism reproduced deterministically (see below). The original "observation" exists only in the resume checklist (no V8* checkpoint detail), so this triage re-derives it from code + a mechanical repro rather than from a prior screenshot.
- **Area**: Frontend (`components/ai/execution-gate-card.tsx`)
- **Effort**: S
- **Migration**: none

## Problem

`ExecutionGateCard` is a Client Component (`execution-gate-card.tsx:1` `"use client"`) that is **SSR'd**
on the initial request: the AI Reviews page is an `async` Server Component
(`app/(app)/org/[slug]/ai/reviews/page.tsx:6`) that fetches gates server-side and passes them to
`AiReviewsClient` (`reviews-client.tsx:1` `"use client"`), which maps each gate to an
`ExecutionGateCard` (`:41-44, :61-64`). So the card's JSX renders **once on the server** and then
**hydrates on the client** — and two pieces of that JSX are non-deterministic across server↔client:

### Defect 1 — timestamp uses unpinned `toLocaleString()` (locale + timezone non-determinism)

`execution-gate-card.tsx:160-162`:
```tsx
<p className="font-mono text-xs ...">
  {new Date(gate.createdAt).toLocaleString()}
</p>
```
`toLocaleString()` with **no locale and no `timeZone`** resolves against the runtime's defaults. The
Node SSR runtime and the user's browser differ on **both** axes, so the rendered string differs →
React logs a hydration mismatch ("Text content did not match…") and discards the server HTML for that
node.

### Defect 2 — `getTimeRemaining` reads wall-clock `new Date()` at render

`execution-gate-card.tsx:49-62, 71`:
```tsx
function getTimeRemaining(expiresAt: string): string | null {
  const now = new Date();            // server-render time ≠ client-hydrate time
  ...
  return `${hours}h ${minutes}m remaining`;
}
const timeRemaining = gate.status === "PENDING" ? getTimeRemaining(gate.expiresAt) : null;
```
The "Xh Ym remaining" string is computed from `Date.now()` at render. Server render time and client
hydrate time differ (network + hydration latency), so the minute value can differ → a second
hydration mismatch on the same card (for PENDING gates).

## Reproduction (deterministic mechanism repro)

The original visual observation isn't in any V8 checkpoint, so this triage proves the mechanism with a
Node repro of the exact expressions (server TZ/locale vs browser TZ/locale):

```
new Date("2026-06-15T13:29:00Z").toLocaleString(...)
  UTC                 => 6/15/2026, 1:29:00 PM
  Africa/Johannesburg => 6/15/2026, 3:29:00 PM     ← differs by timezone
  America/New_York    => 6/15/2026, 9:29:00 AM
  en-US               => 6/15/2026, 3:29:00 PM
  en-GB               => 15/06/2026, 15:29:00       ← differs by locale

getTimeRemaining (expiresAt = now+65m):
  server render (t0)      => 1h 4m remaining
  client hydrate (t0+90s) => 1h 3m remaining        ← differs by render time
```

Whenever the SSR runtime's TZ/locale ≠ the viewer's (the normal case), line 161 produces a guaranteed
text mismatch; the countdown adds a second one for PENDING gates. (A live browser-console capture was
not taken — the AI Reviews page sits behind Keycloak auth and the cycle's ENV-HEALTH guard discourages
extra live churn — but the SSR path is confirmed in code and the divergence is deterministic, which is
stronger than the prior bare hypothesis.)

## Fix

Render dates **deterministically** and the countdown **client-only**. `suppressHydrationWarning` is
**not** the fix (it hides the symptom; the text is still wrong on the server pass).

1. **Timestamp (line 161):** replace `new Date(gate.createdAt).toLocaleString()` with a pinned,
   shared formatter. The project already has deterministic, locale-pinned formatters in
   `@b2mash/shared` (`packages/shared/src/format.ts` — `formatComplianceDateWithTime` pins `en-ZA`,
   `formatDate` pins `en-GB`). For a timestamp-with-time, pin **both** locale **and** `timeZone`
   (e.g. add `timeZone: "Africa/Johannesburg"` — the product's ZA locale — to the `Intl` options) so
   server and client render identical text. Prefer extending/using a `@b2mash/shared` helper over an
   inline `Intl.DateTimeFormat`, per `frontend/CLAUDE.md` ("date/currency formatting come from
   `@b2mash/shared` — don't reimplement locally").
2. **Countdown (`getTimeRemaining`):** compute it **after mount** so it never participates in
   hydration — e.g. gate it behind a `useState`/`useEffect` "mounted" flag (render `null` on the
   server pass, fill in on the client), or move it to a small `"use client"` child that only renders
   the countdown post-mount. This also lets it tick (currently it's frozen at first render anyway).

## Scope

- One component (`execution-gate-card.tsx`). Optionally add/extend one `@b2mash/shared` formatter.
- **Blast-radius note (do NOT bundle — flag separately):** raw/locale-unpinned date rendering also
  appears in other AI client components (`compliance-audit-summary.tsx:60` uses
  `toLocaleDateString("en-ZA", …)` — locale-pinned, date-only, so lower TZ risk; plus
  `compliance-finding-detail.tsx`, `ai-cost-summary.tsx`, `compliance-audit-tab.tsx` contain
  `toLocaleString`/`new Date`). These are a related class but out of scope for this Low fix; per
  CLAUDE.md §7 keep this PR to `ExecutionGateCard` unless the orchestrator authorizes a same-class
  cluster.

## Verification

1. `pnpm lint && pnpm build && pnpm test` green. Add a unit test for `ExecutionGateCard` asserting the
   timestamp renders a fixed, deterministic string for a fixed `createdAt` regardless of
   `process.env.TZ` (set `TZ=UTC` then `TZ=America/New_York` and assert equal output), and that the
   countdown is absent on the server/first render.
2. Live (keycloak): open `/org/verifain-attorneys/ai/reviews` with at least one PENDING gate, open
   DevTools console → **no "hydration"/"did not match" warning** on the gate cards; the timestamp
   reads the same value the server sent; the countdown appears after mount.
