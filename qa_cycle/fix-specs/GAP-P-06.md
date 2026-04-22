# Fix Spec: GAP-P-06 — Portal `/accept/[token]` rejects valid `SENT` / `VIEWED` payloads

## Problem

Day 8 Checkpoint 8.1 blocked: the portal `/accept/[token]` page renders
`"Unable to process this acceptance request. Please contact the sender."`
under a red warning triangle, even though the backend
`GET /api/portal/acceptance/{token}` returns 200 with a valid
`AcceptancePageData` payload:

```json
{
  "requestId": "a23d81a3-…",
  "status": "VIEWED",
  "documentTitle": "engagement-letter-…pdf",
  "documentFileName": "engagement-letter-…pdf",
  "expiresAt": "2026-04-28T22:44:58Z",
  "orgName": "Mathebula & Partners",
  "orgLogo": "…",
  "brandColor": "#1B3358",
  "acceptedAt": null,
  "acceptorName": null
}
```

(Evidence: `qa_cycle/checkpoint-results/day-08.md` line 22; screenshot
`day-08-accept-failure.png`.) No 4xx/5xx in the network waterfall, no console
errors. The page renderer is the sole reason for the error screen.

## Root Cause (confirmed via code read)

**File:** `portal/app/accept/[token]/acceptance-page.tsx` lines 71–89

```tsx
setPageData(data);

if (data.status === "ACCEPTED") {
  …
  setPageState("ACCEPTED");
} else if (data.status === "EXPIRED") {
  setPageState("EXPIRED");
} else if (data.status === "REVOKED") {
  setPageState("REVOKED");
} else if (data.status === "PENDING") {
  setPageState("PENDING");
} else {
  // Unknown status from backend — treat as error rather than showing the form
  setError("Unable to process this acceptance request. Please contact the sender.");
  setPageState("ERROR");
}
```

The portal handles `ACCEPTED / EXPIRED / REVOKED / PENDING` and blows everything
else up as an error.

The backend's `AcceptanceStatus` enum
(`backend/.../acceptance/AcceptanceStatus.java`) actually has six values:
`PENDING, SENT, VIEWED, ACCEPTED, EXPIRED, REVOKED`. Per
`AcceptanceService.createAndSend` the request is saved as `PENDING`, immediately
transitioned to `SENT` after the email dispatch (line 231), and then — the moment
the customer clicks the link and the portal page calls
`getAcceptancePageData()` — auto-flipped to `VIEWED` as a side effect of
`getPageData` (line 767–777, calls `markViewed` inside the scoped transaction).
So the **first** real client render is always against `status: "VIEWED"`;
subsequent reloads keep `status: "VIEWED"` until accept/expire/revoke.

**The portal also contains two related, smaller bugs that will bite the next
render:**

1. **`AcceptanceStatus` TypeScript union omits `SENT` and `VIEWED`**
   (`portal/lib/types.ts` line 166 — `"PENDING" | "ACCEPTED" | "EXPIRED" | "REVOKED"`).
   This is the reason the code had to fall into an `else` branch at all: the
   exhaustive-check shape pushed the author to guard on the four documented
   values only.
2. **`PENDING` is treated as "show Accept form"** but in the backend `PENDING`
   means *not yet dispatched* — the actual "show Accept form" states are `SENT`
   and `VIEWED`. The current code therefore shows the form for a status the
   client can never legitimately observe (the token isn't even known to the
   portal until `SENT`) and refuses to show it for the two states the client
   *always* observes.

The existing component test suite
(`portal/app/accept/[token]/__tests__/page.test.tsx`) only exercises the four
canonical statuses — `SENT` and `VIEWED` are untested, which is why this
regressed unnoticed.

Minimal payload concern from the day-08 gap description (missing `lineItems`,
`totalZar`, `vatAmount`, `scope`, `effectiveDate`) turned out to be a red
herring — the renderer never touches those fields; the entire failure is the
status-switch falling through. That coupling to GAP-L-49 can be dropped.

## Fix

Treat `SENT` and `VIEWED` as actionable states (show the accept form), drop
`PENDING` from the user-facing states (backend never exposes `PENDING` to
portal callers — `resolveByToken` returns the request as-is, but `PENDING` only
exists for the millisecond between `save` and `markSent` in the same
`@Transactional` method, so the portal will never see it), and align the
TypeScript union with the backend enum.

### Step 1 — widen the status union

**File:** `portal/lib/types.ts` line 166

```diff
-export type AcceptanceStatus = "PENDING" | "ACCEPTED" | "EXPIRED" | "REVOKED";
+export type AcceptanceStatus =
+  | "PENDING"
+  | "SENT"
+  | "VIEWED"
+  | "ACCEPTED"
+  | "EXPIRED"
+  | "REVOKED";
```

### Step 2 — treat SENT + VIEWED as the actionable state

**File:** `portal/app/accept/[token]/acceptance-page.tsx` lines 73–89

```diff
 if (data.status === "ACCEPTED") {
   setAcceptedAt(data.acceptedAt);
   setAcceptorName(data.acceptorName);
   setPageState("ACCEPTED");
 } else if (data.status === "EXPIRED") {
   setPageState("EXPIRED");
 } else if (data.status === "REVOKED") {
   setPageState("REVOKED");
-} else if (data.status === "PENDING") {
+} else if (data.status === "SENT" || data.status === "VIEWED") {
   setPageState("PENDING"); // "PENDING" here is the UI state = "awaiting user action"
 } else {
   // Unknown status from backend — treat as error rather than showing the form
   setError(
     "Unable to process this acceptance request. Please contact the sender.",
   );
   setPageState("ERROR");
 }
```

The internal `PageState` type already uses `"PENDING"` to mean "show the accept
form". Leaving that name alone avoids a rename churn, and keeps the change
surgical; a follow-up pass could rename the internal state to `"ACTIONABLE"` for
clarity, but that's polish.

### Step 3 — extend the component tests

**File:** `portal/app/accept/[token]/__tests__/page.test.tsx`

Add two cases mirroring the existing PENDING test:

- `status: "SENT"` → form renders, "I Accept" button present.
- `status: "VIEWED"` → form renders (same — VIEWED is "seen, still actionable").

These are ~15 lines each; the existing PENDING test is the template.

## Alternative considered (rejected)

"Widen the error gate — just don't throw on unknown statuses, assume it's
actionable." Rejected because silently swallowing a genuinely unknown value
(e.g., a future `DECLINED` state) would hide real defects. Explicit handling of
the full backend enum is the correct answer and costs the same LOC.

## Scope

- Files to modify:
  - `portal/lib/types.ts` (line 166 — 1-line union widen)
  - `portal/app/accept/[token]/acceptance-page.tsx` (lines 81–82 — 1 branch edit)
  - `portal/app/accept/[token]/__tests__/page.test.tsx` (+2 test cases)
- Files to create: none
- Backend changes: **none**. Backend payload is already correct; no need to
  extend `AcceptancePageData` with proposal / VAT / line-item structure for this
  fix. (That work remains part of deferred GAP-L-49 — and is explicitly out of
  scope here.)
- Migration needed: no
- Env / config: no

## Verification

1. Portal HMR picks up TSX edits automatically (no restart).
2. Re-run Day 8 Checkpoint 8.1 with GAP-L-50 already fixed:
   - Open the Mailpit email (now `http://localhost:3002/accept/<token>`).
   - Click the link. Page renders: org-branded header (Mathebula & Partners logo
     + brand color), document-info card with filename, PDF iframe, Accept form
     with name input and "I Accept" button.
3. Reload the page once. Status in the payload is now `"VIEWED"`. Same form
   still renders (no "Unable to process").
4. Type a name, submit. `POST /api/portal/acceptance/<token>/accept` → 200.
   Page transitions to the green "accepted on …" confirmation card.
5. Reload after accept. Payload status is now `"ACCEPTED"` — existing code path
   renders the accepted confirmation correctly (unchanged behaviour).
6. Run the component test suite: `pnpm --filter portal test accept` → all six
   status cases pass (old four + new SENT/VIEWED).

## Estimated Effort

**S (< 30 min)** — three TSX edits totaling ~10 LOC of production code + ~30 LOC
of tests. Zero backend work. Zero migration. Risk bounded to the one render
branch.

## Status Triage

**SPEC_READY.** Smallest unblock that matches QA's suggestion ("loosen portal
`/accept/[token]` renderer to tolerate minimal doc-share payload") while staying
explicit about which statuses are legitimate. Ship alongside GAP-L-50 to fully
unblock Day 8.

## Coupling note

- Does **not** depend on GAP-L-49 (proposal structure / VAT / line items) — the
  renderer never reads those fields on the `AcceptancePageData` shape, so this
  spec stays independent and ships in parallel with GAP-L-50.
- Pairs cleanly with GAP-L-50: once the email points at `:3002` *and* the portal
  tolerates `SENT`/`VIEWED`, Day 8 ends-to-end unblocks.
