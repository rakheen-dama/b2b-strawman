# 633C — G88-F20 / F29 / F30 / F31 + §5b row 19 (UI) reproduction at HEAD (builder, 2026-11-05)

Worktree `/Users/rakheendama/Projects/2026/worktree-epic-633C`, branch
`phase89/04C-portal-deadlines-copy-guard` @ `85bb3867b3461a5023dcebcf9139d32bfa4f0420` (identical to
`main`). The new / changed vitest cases and the new guard were written first and run **before any
`portal/{app,components,lib}` change** (only `__tests__/` files were touched — `git status` at the
time of the run listed only the three modified test files and the three new test / allow-list files).

## 1. Vitest red (633.10)

```
$ cd portal && NODE_OPTIONS="" pnpm exec vitest run components/__tests__/status-badge.test.tsx \
    "app/(authenticated)/requests/__tests__/request-copy.test.tsx" \
    components/trust/__tests__/balance-card.test.tsx \
    components/trust/__tests__/transaction-list.test.tsx > /tmp/red-633C.log 2>&1     # exit=1

     × renders balance in ZAR, as-of date, and the matter name
     × falls back to 'Matter' when the summary has no matter name
     × renders task status in sentence case with the colour classes
     × renders OPEN and DONE task statuses in sentence case (G88-F20)
     × detail h1 falls back to the request number for a client-level request (G88-F30)
     × request list shows the status label, not the enum (G88-F29)
     × labels the transaction type in sentence case, never the enum (G88-F31)
     × synthesises a fallback description when the backend sends an empty one
 Test Files  4 failed (4)
      Tests  8 failed | 6 passed (14)
```

Failure messages and what HEAD actually rendered (from the testing-library DOM dumps in the log):

| Gap | Test | Failure | HEAD renders |
|---|---|---|---|
| G88-F20 | status-badge: task `IN_PROGRESS` | `Unable to find an element with the text: In progress` | `IN PROGRESS` (`status-badge.tsx:47-49` `formatStatus` = `replace(/_/g, " ")`) |
| G88-F20 | status-badge: task `OPEN` / `DONE` | `Unable to find an element with the text: Open` | `OPEN` |
| G88-F30 | request-copy: detail h1, `matterName: null` | `Unable to find role="heading" and name "Request REQ-0007"` | `<h1 class="font-display mt-1 …" />` — **empty** (`requests/[id]/page.tsx:92-94` renders `detail.matterName`) |
| G88-F29 | request-copy: detail status + item | (same test) | `1/1 submitted • status IN_PROGRESS`; item `Submitted — status: SUBMITTED` (`[id]/page.tsx:99,190`) |
| G88-F29 | request-copy: list `SENT` | `Unable to find an element with the text: Requested` | list row `<p>SENT</p>` (`requests/page.tsx:93`) and an empty title `<p />` (`:89`, D3) |
| G88-F31 | balance-card: matter name | `Unable to find an element with the text: Estate Late J Mokoena` | `Matter ab12cd34` (`trust.ts:107-110` `formatMatterLabel`, `balance-card.tsx:30`) |
| G88-F31 | balance-card: `matterName: null` | `Unable to find an element with the text: Matter` | `Matter ab12cd34` |
| G88-F31 | transaction-list: `FEE_TRANSFER` / `REFUND` | `Unable to find an element with the text: Fee transfer` | Type cell + mobile row `FEE_TRANSFER` (`transaction-list.tsx:133,181`) |
| G88-F31 | transaction-list: empty-description fallback | `Unable to find … /Interest credited — INT-001/` | `Interest Credit — INT-001` (title-cased enum, `:30-34`) — not the §14.3.15 label |

(The balance-card red run passes the HEAD prop `matterId` alongside the new `matterName`, so HEAD
renders instead of crashing; `matterId` is dropped from the test once the prop is replaced.)

## 2. Guard red at HEAD (633.13)

```
$ cd portal && NODE_OPTIONS="" pnpm exec vitest run __tests__/no-raw-enum-copy.test.ts     # exit=1

     × zero raw enum renders outside the allow-list
 Test Files  1 failed (1)
      Tests  1 failed | 3 passed (4)

AssertionError: expected [ …(5) ] to deeply equal []
+ [
+   "app/(authenticated)/requests/[id]/page.tsx:99 [jsx-child]: {detail.status}",
+   "app/(authenticated)/requests/[id]/page.tsx:190 [jsx-child]: {item.status}",
+   "app/(authenticated)/requests/page.tsx:93 [jsx-child]: {r.status}",
+   "components/trust/transaction-list.tsx:133 [jsx-child]: {txn.transactionType}",
+   "components/trust/transaction-list.tsx:181 [jsx-child]: {txn.transactionType}",
+ ]
```

Exactly the five JSX-child hits the scout predicted; the detector self-test, the "scans the real
tree" test and the allow-list liveness test (StatusBadge / InvoiceStatusBadge prop hits) pass.

Planted proof at HEAD (scratch `components/__raw-enum-guard-proof.tsx` = `<p>{item.status}</p>`):

```
GUARD_HEAD_PLANTED=1
+   "components/__raw-enum-guard-proof.tsx:2 [jsx-child]: {item.status}",     (6th line, alongside the 5 above)
$ rm components/__raw-enum-guard-proof.tsx && git status --short components/   # proof file gone
```

The post-fix planted proof (guard green → planted red → removed → green) is in `guard-red.md`.

## 3. E2E red (§5b row 19 — 633.11)

`portal/e2e/tests/portal-client-90day/day-45-75.spec.ts:166-169` at HEAD:

```ts
    test.fixme(
      true,
      "628G-BLOCKER: no portal deadline can be produced on demand — a firm court date never reaches the portal (COURT_DATE/PRESCRIPTION/FILING portal sync dormant — customerbackend/service/DeadlinePortalSyncService.java:36-44,123-130) and the portal has no mark-read control or deadline documents — components/deadlines/deadline-detail-panel.tsx:83-170",
    );
```

This is the suite face of §5b row 19; 632C's fresh-stack E2E-PORTAL run recorded
`portal-client-90day` **12 passed / 1 skipped** (this fixme). 633B built the backend sync
(API-level PASS, `$EV/633B/OBSERVATION.md`); this slice removes the fixme and the mark-read steps
(decision 7).

## 4. grep facts

```
$ grep -rn formatMatterLabel portal --include='*.ts' --include='*.tsx' | grep -v node_modules
portal/components/trust/balance-card.tsx:7:import { formatMatterLabel } from "@/lib/api/trust";
portal/components/trust/balance-card.tsx:30:  const label = formatMatterLabel(matterId);
portal/components/trust/matter-selector.tsx:8:  formatMatterLabel,
portal/components/trust/matter-selector.tsx:57:                {formatMatterLabel(matter.matterId)}
portal/lib/api/trust.ts:107:export function formatMatterLabel(matterId: string): string {
```

5 lines, **two** callers (balance-card, matter-selector). After the fix → 0 (see PR body).
