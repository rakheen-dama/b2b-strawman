# 647D observed gates — found items

PR #1797 @ 0915e8b0d. These are found items only. Nothing was fixed.

**One FAIL in OBS (h):** the Transactions table overflows its scroll wrapper at 1280 whenever 647D's resolution rows
are on the page (H-1). The rows 647D creates trigger it. The mechanism is the existing type badge, which does not wrap,
not 647D's new description line. Classification is the orchestrator's call.

Everything else in OBS a–g and i passed. Both legal suites matched their baselines.

## H-1 — Transactions page at 1280: the Actions column is pushed past the table's scroll wrapper (FAIL, triggered by 647D rows)

**Where:** `/trust-accounting/transactions` at a 1280 px viewport, on stack #1 after the OBS.

**Observed** (bounding rects; `obs/clip-b1-*.json`, `clip-g3-*.json`, `clip-z1-*.json`, `probe-clip.json`):

| State | Wrapper (clip) | Table | Columns |
|---|---|---|---|
| (b) one pending `UNIDENT_ALLOC_OUT` | 305–1231 (926) | 305–1275 (970) | Type 211, **Actions 1084–1275** |
| (g) + `UNIDENT_FUND_PAY` rows | 305–1231 (926) | 305–1316 (1011) | Type 253, **Actions 1125–1316** |
| final (`probe-clip.cjs`) | 305–1231 (926) | 305–1316 (1011) | same; 16 cells outside |
| 1440 and 1920, every state | — | table = wrapper | all inside |

At 1280 the Actions column ends 85 px past the wrapper's right edge. The wrapper is `overflow-x-auto`, so the column can
still be reached by scrolling sideways.
- On a pending row, **Approve** shows but **Reject** is cut off. See `obs/h-diag-transactions-1280-crop.png` and
  `obs/32-g3-bob-fund-pending-transactions-bob-1280.png`.
- This is the approver's view of the very rows 647D adds.

**Cause (`diag-tx-width.cjs`, read-only; it changed only the headless page's own DOM, `obs/diag-tx-width.json`):**

| Variant at 1280 | Table width |
|---|---|
| as rendered | **1011** |
| 647D description lines hidden | 1011 (no change) |
| the 4 resolution rows hidden (`Unidentified deposit allocated`, `… paid to the Fund` ×2, `Deposit identified and allocated`) | **926** (fits) |
| type badges allowed to wrap (`white-space: normal`) | **926** (fits) |

The type badge is `white-space: nowrap`. The registry labels of the 647D rows are the widest on the page:

| Label | Badge width |
|---|---|
| "Unidentified deposit paid to the Fund" | 237 px |
| "Deposit identified and allocated" | 205 px |
| "Unidentified deposit allocated" | 196 px |
| widest pre-647D label on this data ("Unidentified deposit") | 139 px |

Those badges widen the Type column (min-content) to 253 px, and the other columns cannot shrink further. The new
description line (`max-w-xs whitespace-normal`) wraps and adds no width.

**Scope:**
- The labels come from the 646E registry, and the badge markup is older still.
- 647D's resolution rows are the first rows a user can create through the UI whose label is wider than about 150 px.
  They are therefore the first rows to expose the problem. Other long registry labels, such as "Placed in section 86(4)
  investment", would do the same if their rows appeared.
- The 647C F-1 precedent treated a 1280 clip on a new trust table as a defect fixed in the next slice.

**Not fixed.**

**Related observation, H-1b (seen, not measured):** after Alice's refused self-approval, the error text sits inline after
Approve/Reject in the Actions cell:

> "The transaction recorder cannot be the sole approver. A different member with APPROVE_TRUST_PAYMENT capability must
> approve this transaction."

At **1440** it runs past the wrapper's right edge and is cut off ("The transaction rec… cannot be the sole …"). See
`obs/07-b2-alice-refused-row-crop.png` and `obs/07-b2-alice-approve-refused-1440.png`.
- The error span belongs to the existing `ApprovalBadge` (no width limit).
- I did not establish whether it clips the same way without 647D rows. The clip probe ran before the click, so this was
  captured by screenshot only.

**Not fixed.**

## O-1 — harness: obs.cjs died in step g on a transient empty `docker exec psql` read (not a product issue)

`obs.cjs` failed at `itemDb` with `TypeError: Cannot read properties of undefined (reading '0')`:
- This was right after item B's Fund-payment request had been submitted through the UI. The request row was saved,
  `AWAITING_APPROVAL`.
- The same query returned the row when re-run by hand a minute later.

The shell wrapper of that run was later reaped at the 10-minute tool ceiling. The run was slow because Playwright's
default 30 s wait was spent on optional per-row elements that were absent; this was not a hang.

`obs-g.cjs` continued from that exact state (B pending) with the same helpers, plus three changes:
- retry on an empty single-row read;
- count-guards instead of 30 s waits;
- SQL evidence appended, not overwritten.

It then did B approve, C request/reject and the final reads. No product state was lost or repeated. Before resuming, B's
request was read back from the DB and the UI.

## O-2 — dev-only: the portal `/auth/exchange` shows "Link expired or invalid" under `next dev` after a successful sign-in

**What happened:** both portal sign-ins on `next dev` (:3012) logged a 401 on `/portal/auth/exchange`.

**Why:** under React StrictMode the page's `useEffect` runs twice. `portal/app/auth/exchange/page.tsx` calls
`storeAuth(...)` before its `cancelled` check:
- the first exchange succeeds and stores `portal_jwt`;
- the second exchange (the token is already spent) gets 401 and shows the error.

`portal-obs.cjs` therefore waits for `portal_jwt` in localStorage and then opens the trust page, which renders
authenticated.

**Assessment:** production builds do not double-invoke effects, so this is a dev-server artefact. It is noted only
because it misleads anyone running the portal under `next dev`. Not 647D.

## N-1 — note: the firm's client-ledger page has no Description column

`/trust-accounting/client-ledgers/{client}` lists the `UNIDENT_ALLOC_IN` row as
"14 Nov 2026 · UID-DA88D7A5 · Deposit identified and allocated · R 4 321,09 · APPROVED · R 4 321,09"
(`obs/11-c4-moroka-client-ledger-*.png`).

The "Deposit received 10 November 2026 — DEPOSIT UNKNOWN REF A163PR" text (D12) is not shown there, because the table
has no description column. It is shown in:
- the API history (`api-c-moroka-history.json`);
- SQL;
- the client portal's trust statement (`obs/d1-portal-moroka-trust-*.png`).

This is the page's existing design, not a 647D change. It is recorded because the brief asked for "the client ledger has
the row … whose description carries the receipt date".
