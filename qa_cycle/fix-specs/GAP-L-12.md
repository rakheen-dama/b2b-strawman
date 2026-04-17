# Fix Spec: GAP-L-12 — Hydration mismatch on `/profitability` ZAR currency format

## Problem

From `status.md` Gap Tracker (Day 90 / 90.5, LOW):

> Hydration mismatch on `/profitability` (Matter Profitability table): server renders currency
> as "R 18,750.00" (US locale), client re-renders as "R 18 750,00" (en-ZA). React throws
> hydration warning, tree regenerated on client (degrades SSR perf). Fix: ensure SSR uses
> `Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' })` OR defer currency
> formatting to a Client Component (`'use client'` boundary).

User-impact: React hydration warning in console on every profitability page view; client
re-renders the server tree (SSR optimisation wasted). Cosmetic but every ZAR-aware page triggers
it — dashboard, reports, invoice previews are all candidates.

## Root Cause (validated, not hypothesis)

`formatCurrency()` in `frontend/lib/format.ts:63-72` calls `Intl.NumberFormat(locale, ...)`
with `locale = "en-ZA"` for ZAR (via the `currencyLocaleMap` on lines 53-58). This IS the right
API call — the problem is that **Node.js ships with small-icu by default, which silently
falls back to en-US formatting for any locale that isn't en-US** even though
`Intl.NumberFormat.supportedLocalesOf(['en-ZA'])` returns `['en-ZA']`.

Validated at the Node REPL on the local machine (Node v20.11.1, ICU 73.2, small-icu):

```
> new Intl.NumberFormat('en-ZA', {style:'currency', currency:'ZAR', minimumFractionDigits:2, maximumFractionDigits:2}).format(18750)
'R 18,750.00'                                 ← wrong (US-style thousand/decimal separators)
> Intl.NumberFormat.supportedLocalesOf(['en-ZA'])
[ 'en-ZA' ]                                   ← Node lies: says supported, still formats as US
```

Browser (full ICU) produces `"R 18 750,00"` (narrow no-break space thousands separator, comma
decimal) — hence the hydration mismatch.

Confirmed by reading:
- `frontend/lib/format.ts:63-72` — `formatCurrency`
- `frontend/lib/format.ts:53-58` — `currencyLocaleMap` (ZAR → en-ZA)
- `frontend/components/profitability/project-profitability-table.tsx:17,233,236,247` — table renders currency server-side via `formatCurrency`
- `frontend/components/profitability/*.tsx` — all use `formatCurrency` from `lib/format.ts`
- `frontend/package.json` dependencies — no `full-icu`, `@formatjs/intl-numberformat`, or
  equivalent polyfill installed

The **client** calls the identical function and the browser's ICU outputs correctly. The
**server** calls the same function in Node's small-icu and outputs wrongly. The call site is
identical; the diverging ICU data is the sole cause.

Other currencies dodge the bug by coincidence: Node's small-icu includes en-US (USD $), en-GB
(GBP £), and most Euro locales; ZAR is in the gap.

```
en-ZA ZAR "R 18,750.00"                       ← mismatches browser "R 18 750,00"
en-US USD "$18,750.00"                        ← matches browser
en-GB GBP "£18,750.00"                        ← matches browser
de-DE EUR "18.750,00 €"                       ← matches browser
```

## Fix

Two options; pick **Option A** (polyfill, minimal, future-proof). Option B (hand-rolled ZAR-only
formatter) is listed as a fallback if the team prefers zero new dependencies.

### Option A (recommended) — add `@formatjs/intl-numberformat` polyfill with en-ZA data

Polyfills `Intl.NumberFormat` with bundled locale data so Node produces the same output as the
browser for ZAR (and any other currency we add later).

1. `cd frontend && pnpm add @formatjs/intl-numberformat` (runtime dependency; ~30 KB gzipped
   including only the locales we import).

2. Create `frontend/lib/intl-polyfill.ts` (new file):

   ```typescript
   // Ensures Intl.NumberFormat has full ICU data for locales we render.
   // Node ships with small-icu by default: Intl.NumberFormat('en-ZA', ...) silently
   // formats as en-US in Node, causing SSR/CSR hydration mismatches for ZAR. See
   // qa_cycle/fix-specs/GAP-L-12.md.
   import "@formatjs/intl-numberformat/polyfill-force";
   import "@formatjs/intl-numberformat/locale-data/en";
   import "@formatjs/intl-numberformat/locale-data/en-ZA";
   import "@formatjs/intl-numberformat/locale-data/en-GB";
   import "@formatjs/intl-numberformat/locale-data/en-US";
   import "@formatjs/intl-numberformat/locale-data/de-DE";
   ```

   `polyfill-force` is the correct import for Next.js SSR because Node claims partial support
   via `supportedLocalesOf`; without `-force`, the polyfill defers to Node's (broken) implementation.

3. Import the polyfill at the top of `frontend/lib/format.ts` so it loads eagerly when any
   caller imports `formatCurrency`:

   ```typescript
   import "./intl-polyfill";
   ```

   Placing the import in `lib/format.ts` (rather than `app/layout.tsx`) ensures the polyfill
   loads both for server-rendered pages AND for any API route / server action that calls
   `formatCurrency`, without needing a separate polyfill bootstrap on the client (the browser's
   full ICU makes `polyfill-force` a no-op on the client path since browsers already format
   correctly).

### Option B (fallback) — hand-rolled ZAR formatter, no new dependency

If the team rejects the polyfill, replace `Intl.NumberFormat` for ZAR specifically:

```typescript
// In lib/format.ts — replace formatCurrency with:
const NBSP = "\u00A0"; // narrow no-break space expected by en-ZA

export function formatCurrency(amount: number, currency: string): string {
  const code = currency || "USD";
  const value = amount ?? 0;

  if (code === "ZAR") {
    // Deterministic en-ZA / ZAR formatting (matches browser Intl output).
    const abs = Math.abs(value).toFixed(2);
    const [whole, frac] = abs.split(".");
    const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, NBSP);
    const sign = value < 0 ? "-" : "";
    return `${sign}R${NBSP}${grouped},${frac}`;
  }

  const locale = currencyLocaleMap[code] ?? "en-US";
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency: code,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}
```

Trade-offs:
- **Pro**: zero new dependencies, trivial to review.
- **Con**: only fixes ZAR. If we ship another small-icu-missing locale (likely when new
  verticals arrive — en-AU, en-NZ, en-KE are the usual next suspects for professional-services
  SaaS), we re-hit the bug and have to grow this branching.

**Recommend Option A.** Polyfill is the idiomatic fix and matches how React/Next.js teams
typically handle SSR ICU issues.

### Verify output matches client after fix

Run at the node REPL:
```
> require('./lib/intl-polyfill')
> new Intl.NumberFormat('en-ZA', {style:'currency', currency:'ZAR', minimumFractionDigits:2, maximumFractionDigits:2}).format(18750)
'R 18 750,00'                                 ← expected after polyfill
```

## Scope

- **Frontend**: YES.
- **Backend / Gateway / Keycloak theme / Seed / Config / Migration**: NO.

Files to modify:
- `frontend/lib/format.ts` (add one import; otherwise unchanged with Option A)
- `frontend/package.json` + `pnpm-lock.yaml` (if Option A, adds `@formatjs/intl-numberformat`)

Files to create:
- `frontend/lib/intl-polyfill.ts` (Option A only)
- `frontend/lib/format.test.ts` — extend the existing test file with a `formatCurrency` block
  asserting `formatCurrency(18750, 'ZAR')` returns the exact browser-equivalent string (NBSP
  thousands, comma decimal).

Migration needed: NO.
KC restart required: NO.
Backend restart required: NO.
Frontend restart required: NO (HMR handles the change; Next.js watches `package.json` changes
and will re-init the server on first request). After `pnpm add`, the Next.js dev server picks
it up on next request.

## Verification

### Unit test (vitest)

Extend `frontend/lib/format.test.ts`:

```typescript
import { formatCurrency } from "./format";

describe("formatCurrency", () => {
  it("formats ZAR with narrow-no-break-space thousands and comma decimal (en-ZA)", () => {
    // Matches browser Intl.NumberFormat("en-ZA", {currency: "ZAR"}).format(18750)
    const out = formatCurrency(18750, "ZAR");
    expect(out).toMatch(/^R\u00A018\u00A0750,00$/);
  });

  it("formats USD with US-style separators", () => {
    const out = formatCurrency(18750, "USD");
    expect(out).toMatch(/^\$18,750\.00$/);
  });

  it("returns a safe fallback for null amount", () => {
    expect(formatCurrency(0, "ZAR")).toMatch(/^R\u00A00,00$/);
  });
});
```

Happy-dom (the current vitest env) uses the host Node's Intl, so without the polyfill this test
**fails** in CI the same way production does — which is what we want (it's a true regression
test). After applying Option A the test passes.

### Manual reproduction in <5 min

1. Pre-fix: on the running Keycloak stack, log in and navigate to `/org/<slug>/profitability`.
   Open browser DevTools Console. Expect a React hydration warning mentioning a mismatched
   text node for a ZAR amount (e.g., `"R 18,750.00"` vs `"R 18 750,00"`).
2. Apply fix (Option A): `pnpm add @formatjs/intl-numberformat`, add
   `lib/intl-polyfill.ts`, import in `lib/format.ts`. Frontend HMR picks it up.
3. Hard-refresh `/profitability`. Expect: no hydration warning in Console; currency values
   render consistently as `"R 18 750,00"` on both initial HTML and after hydration.

### Re-run QA checkpoint

Day 90 / 90.5 regression sweep: reload `/profitability`, open Console, confirm **no**
"Text content does not match server-rendered HTML" warning. Cross-check `/dashboard` and any
other page that renders ZAR (Reports, Invoice preview) to confirm the polyfill benefits them
too.

## Estimated Effort

**S (~25 min)** — one `pnpm add`, one new file (~8 lines), one import in `lib/format.ts`,
three-line vitest addition.

## Out of Scope / Follow-up

- **Adding more locales to the polyfill.** Import only what the app renders. If accounting-za
  or consulting-za or uk profiles need en-AU / en-GB-specific variants, extend the polyfill at
  that point.
- **Changing `formatCurrency` signature** (e.g., accepting an explicit locale override). Not
  needed now; the currency → locale map is sufficient.
- **Hardening other `toLocaleString` / `toLocaleDateString` calls elsewhere in the app.**
  `lib/format.ts` already uses en-ZA for `formatLocalDate` / `formatComplianceDate*` — these
  use `Date.prototype.toLocaleDateString` which also consults ICU data. Dates are less visible
  and the Node small-icu fallback happens to look close to en-ZA ("19 Feb 2026") because both
  use short month names in English — so no user-visible mismatch today. Still, adding
  `@formatjs/intl-datetimeformat` polyfill alongside would harden this. Treat as follow-up.
- **Fixing `formatDate` / `formatDateShort`** still using `en-US` (unrelated to GAP-L-12 but
  visible in code review). Those are intentional (date format for non-ZA audiences?) — leave
  untouched in this fix.

## Notes

- **Why not "defer to a Client Component" (the other suggestion in status.md)?** It would
  work (client-only currency formatting) but adds a `"use client"` boundary on every table
  that shows money, each of which blocks RSC streaming. The polyfill is one file and preserves
  RSC boundaries. Rejecting the `"use client"` option.
- **Why not `full-icu`?** Adds ~28 MB to the bundle; bloats the server image. Polyfill with
  explicit locale imports is ~30 KB.
- **Why not set `NODE_ICU_DATA` env var?** Requires provisioning ICU data at Dockerfile build
  time; cross-platform dev setup friction. Polyfill is portable.
- **Browser path is already correct.** `polyfill-force` is a no-op on browsers with full ICU
  (the package detects and skips). Zero perf cost client-side.
- **Testing gotcha**: happy-dom uses host Node's Intl for numeric formatting, so the vitest
  test case for ZAR correctly reproduces the SSR bug and verifies the polyfill. This matches
  `backend/CLAUDE.md`'s "evidence before assertions" stance: the test is load-bearing proof,
  not decoration.
