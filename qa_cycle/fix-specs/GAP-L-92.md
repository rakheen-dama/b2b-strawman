# Fix Spec: GAP-L-92 — Portal home "Pending info requests" tile excludes IN_PROGRESS-fully-submitted

## Problem

Portal home `/home` "Pending info requests" tile over-counts: includes `IN_PROGRESS` requests where the portal contact has already submitted every item (now waiting on firm review). Day 46 cycle-42 evidence: after Sipho submits both items of `REQ-0005` (status `SENT → IN_PROGRESS`, `2/2 submitted`), the tile remained at **3** instead of dropping to **2**. Scenario §46.7 explicitly expects the count to drop when the portal user has finished their side. From Sipho's POV the request is "done on my side" but the headline number says he still has 3 things to do.

Evidence:
- `qa_cycle/checkpoint-results/cycle42-day46-10-portal-home-after.yml` line 60 — `link "Pending info requests 3"`
- `qa_cycle/checkpoint-results/cycle42-day46-11-portal-requests-after.yml` — list shows REQ-0005 `IN_PROGRESS 2/2 submitted`, REQ-0004 `SENT 0/3`, REQ-0002 `COMPLETED 0/3`, REQ-0001 `SENT 0/3`. Expected pending count after submit: **2** (REQ-0004 + REQ-0001 — the ones where Sipho still has items to upload). REQ-0005 should drop out (Sipho has nothing more to do); REQ-0002 already excluded (COMPLETED).

## Root Cause (verified)

File: `portal/app/(authenticated)/home/page.tsx`

Lines 60-77 (`InfoRequestsCard`):

```tsx
const [count, setCount] = useState<number | null>(null);
useEffect(() => {
  let cancelled = false;
  portalGet<InfoRequestSummary[]>("/portal/requests")
    .then((data) => {
      if (cancelled) return;
      const pending = Array.isArray(data)
        ? data.filter((r) => r.status !== "COMPLETED").length   // ← line 67
        : 0;
      setCount(pending);
    })
    .catch(() => {
      if (!cancelled) setCount(0);
    });
  return () => { cancelled = true; };
}, []);
```

Filter at line 67 only excludes `COMPLETED`. Any request in `SENT` or `IN_PROGRESS` counts as pending — including IN_PROGRESS requests where the portal user has already submitted every item.

The local `InfoRequestSummary` interface (lines 22-25) only declares `id` and `status`, but the backend `GET /portal/requests` endpoint already returns `totalItems` and `submittedItems` per item (verified in `backend/.../PortalInformationRequestController.java:140-151` `PortalRequestListResponse` record). **No backend change needed** — the data is already on the wire and is consumed elsewhere (e.g. `portal/app/(authenticated)/requests/page.tsx:9-21` reads `submittedItems` and `totalItems` correctly).

## Fix

**Definition of "pending for the portal user":** a request that has at least one item still requiring portal-user action — i.e. `submittedItems < totalItems`. This naturally excludes:
- `COMPLETED` requests (firm has closed; usually `submittedItems == totalItems` or items are accepted),
- `IN_PROGRESS` requests where every item is already SUBMITTED (waiting on firm review — nothing for portal user to do),
- And keeps `SENT` and partially-submitted `IN_PROGRESS` requests in the count.

For belt-and-braces against an edge case where a `COMPLETED` request might have `submittedItems < totalItems` (e.g. firm cancelled without responses), keep the COMPLETED exclusion explicit.

### Code change

File: `portal/app/(authenticated)/home/page.tsx`

**Step 1.** Extend the local `InfoRequestSummary` interface (lines 22-25) to include the item counts already returned by the backend:

```tsx
interface InfoRequestSummary {
  id: string;
  status: string;
  totalItems: number;
  submittedItems: number;
}
```

**Step 2.** Replace the filter on line 67:

```tsx
const pending = Array.isArray(data)
  ? data.filter(
      (r) => r.status !== "COMPLETED" && r.submittedItems < r.totalItems,
    ).length
  : 0;
```

That's the entire fix. No other callers of `InfoRequestSummary` exist (the type is locally declared in this file).

## Scope

**Frontend only.**

- Files to modify:
  - `portal/app/(authenticated)/home/page.tsx` (interface + filter, ~3 lines changed)
- Files to create:
  - `portal/app/(authenticated)/home/__tests__/page.test.tsx` (new — see Tests section)
- Migration needed: **no**
- Backend change: **no** (`/portal/requests` already returns `submittedItems`/`totalItems`)

## Verification

Manual / Playwright walk on `:3002` (portal):

1. Sign in as Sipho. Confirm `/home` "Pending info requests" tile = **3** (matches cycle-42 baseline: REQ-0005 IN_PROGRESS 1/2 + REQ-0004 SENT + REQ-0001 SENT).
2. As Sipho, open REQ-0005 detail and submit the remaining item (now 2/2 submitted, parent still IN_PROGRESS).
3. Navigate back to `/home`. **Expect tile to drop from 3 → 2.** (REQ-0005 now filtered out; REQ-0004 + REQ-0001 remain.)
4. As firm user, mark REQ-0005 COMPLETED. Reload portal `/home`. Tile stays at **2** (already excluded; firm action is a no-op for the count). REQ-0005 status field on `/requests` flips to COMPLETED.
5. As firm user, un-COMPLETE / re-open REQ-0005 (if surface exists, else simulate via DB or skip): tile should restore to **3** because REQ-0005 still has `submittedItems == totalItems` but has been re-opened — actually with this fix it will *stay at 2* since 2/2 == totalItems. **This is correct semantics**: re-opening alone doesn't add new work for the user; only adding new items to the request would. Document this decision in cycle log.

Edge cases:
- Request with `totalItems == 0` (degenerate) → `submittedItems < 0` is false → not counted. Acceptable.
- COMPLETED request with `submittedItems < totalItems` (firm closed without responses) → COMPLETED exclusion kicks in → not counted. Acceptable.

## Estimated Effort

**S — under 30 minutes.** Frontend-only, ~3 lines of business logic + a small Vitest unit test.

## Tests

### New unit test (Vitest + Testing Library + happy-dom)

Create `portal/app/(authenticated)/home/__tests__/page.test.tsx` covering `InfoRequestsCard` semantics. Mock `portalGet` from `@/lib/api-client` and `useModules` to return `["information_requests"]`.

Cases:
1. **Empty list** → tile renders `"0"`.
2. **All SENT, none submitted** → tile equals list length (e.g. 3 SENT/0,3 → "3").
3. **IN_PROGRESS partial (1/3) + SENT** → "2".
4. **IN_PROGRESS fully submitted (2/2) + SENT** → "1" (regression case for GAP-L-92).
5. **COMPLETED + IN_PROGRESS partial** → "1".
6. **IN_PROGRESS fully submitted + COMPLETED** → "0".
7. **CANCELLED 0/N** → "0" (terminal — firm withdrew).
8. **Network error** → tile renders `"0"` (existing catch behavior).

Test pattern mirrors `portal/app/(authenticated)/invoices/__tests__/page.test.tsx` (existing portal home-page-style test that mocks `portalGet`). Add `afterEach(() => cleanup())` per `frontend/CLAUDE.md` Radix-leak guidance.

### Existing tests
- No existing test file for `home/page.tsx` (verified via `find`); creating one is the test addition.
- E2E coverage: `portal/e2e/tests/portal-client-90day/day-45-75.spec.ts` does not currently assert on the home tile count after submit (verified — no "Pending info" string match). Optional: extend that spec with a count assertion after the Day 46 submit step to lock in the fix at E2E level. Not required for SPEC_READY.

### Backend tests
- No backend test changes required. `PortalInformationRequestControllerTest` (if exists) already covers the list response shape.

## Regression risk

**Very low.** Single component, single filter expression.

Surfaces using the same filter shape (audited):
- `portal/app/(authenticated)/home/page.tsx` `InfoRequestsCard` — **the fix target**.
- `portal/app/(authenticated)/requests/page.tsx` — renders the full list (no count filter; just maps items). **Untouched.**
- `portal/app/(authenticated)/home/page.tsx` `AcceptancesCard`, `DeadlinesCard`, `RecentInvoicesCard`, `TrustCard` — different endpoints (`/portal/acceptance-requests/pending`, `/portal/deadlines`, `/portal/invoices`, `/portal/trust/movements`); none filter by `status !== "COMPLETED"`. **No change needed.**

Side-effect concerns:
- The local `InfoRequestSummary` interface change is non-breaking — fields are added, not removed; no other file imports this type.
- The `/portal/requests` response already carries `submittedItems`/`totalItems` (backend `PortalRequestListResponse` record); no API contract drift.
- No tenant-isolation surface touched.
- No styling / a11y change.

Cycle-1 OBS-Day46-PendingTileSemantics is resolved by this fix; the "Open info requests" copy-rename alternative was considered and rejected as more invasive (changes user-facing wording, requires re-screen of all surfaces) and less precise (a SENT 0/3 is genuinely "open" from the user's perspective and should still be in the count — which the proposed filter handles correctly).
