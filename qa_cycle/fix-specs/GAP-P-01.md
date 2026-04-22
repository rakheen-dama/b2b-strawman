# Fix Spec: GAP-P-01 — Portal home info-requests card queries wrong backend path + module gate

## Problem

Day 4 Checkpoint 4.4 failed: portal `/home` renders three cards (Upcoming deadlines, Recent invoices, Last trust movement) but **no "Pending info requests" card**, even though REQ-0001 was dispatched to Sipho minutes earlier and is visible at `GET /portal/requests` → 200.

Browser fetch probes from the QA turn:

- `fetch('http://localhost:8080/portal/information-requests?status=PENDING')` → **404** `"No static resource portal/information-requests"`
- `fetch('http://localhost:8080/portal/requests')` → **200** returning REQ-0001

The card swallows the 404 in its `.catch(() => setCount(0))` branch and renders nothing, giving the client no discovery affordance for the pending FICA request.

## Root Cause (confirmed via code read)

**Two independent issues that each suppress the card — both need fixing.**

### Issue 1: wrong path

**File:** `portal/app/(authenticated)/home/page.tsx` line 62

```tsx
portalGet<InfoRequestSummary[]>("/portal/information-requests?status=PENDING")
```

Backend controller is `PortalInformationRequestController` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/customerbackend/controller/PortalInformationRequestController.java` line 28):

```java
@RequestMapping("/portal/requests")
```

List endpoint is `GET /portal/requests` (line 39, no query-param filter). Response DTO has field `status` per row; frontend can filter client-side if it only wants pending items, but the straightforward fix is to count all rows whose `status != "COMPLETED"` (the backend's terminal state), or simply count all rows as the "pending count" since completed requests are usually stored elsewhere in the UX. The scenario semantics are "anything the client hasn't finished yet" — for a legal-za dispatch the status values are `SENT` (dispatched, awaiting client), `IN_PROGRESS` (partial submissions), and `COMPLETED` (all items accepted).

### Issue 2: module gate excludes the card entirely

**File:** `portal/app/(authenticated)/home/page.tsx` line 48

```tsx
{modules.includes("information_requests") && <InfoRequestsCard />}
```

**File:** `backend/src/main/resources/vertical-profiles/legal-za.json` line 5

```json
"enabledModules": ["court_calendar", "conflict_check", "lssa_tariff", "trust_accounting", "disbursements", "matter_closure", "deadlines"]
```

`information_requests` is **not** in the list for legal-za (nor accounting-za nor consulting-za). Since `usePortalContext()` reads `enabledModules` from `/portal/session/context` which ultimately serialises from `org_settings.enabled_modules` (seeded from the profile JSON at tenant-provision time), the gate always evaluates false on a legal-za tenant.

**Bonus consequence:** the same gate on the sidebar nav item (`portal/lib/nav-items.ts` line 77–82 — the `requests` item declares `modules: ["information_requests"]`) means there is also **no sidebar link** to the Requests page. This is part of the cascade into GAP-P-02 (even once the page exists, users need a way to reach it).

## Fix

Two small edits. Both are required — fixing only one leaves the card hidden.

**Step 1.** `portal/app/(authenticated)/home/page.tsx`

Change line 62 from `"/portal/information-requests?status=PENDING"` to `"/portal/requests"`, and update the shape filter so the card counts only non-completed requests:

```tsx
interface InfoRequestSummary {
  id: string;
  status: string;
}

// ...
portalGet<InfoRequestSummary[]>("/portal/requests")
  .then((data) => {
    if (cancelled) return;
    const pending = Array.isArray(data)
      ? data.filter((r) => r.status !== "COMPLETED").length
      : 0;
    setCount(pending);
  })
```

**Step 2.** `backend/src/main/resources/vertical-profiles/legal-za.json`

Append `"information_requests"` to `enabledModules`:

```json
"enabledModules": ["court_calendar", "conflict_check", "lssa_tariff", "trust_accounting", "disbursements", "matter_closure", "deadlines", "information_requests"]
```

Do the same for `consulting-za.json` and `accounting-za.json` — information requests are a universal feature (every vertical that has clients can send document-collection requests), so every profile should enable it. For `consulting-generic.json` (empty modules array), leave as-is; that profile is a minimal stub and the owning team should decide.

After the JSON change, `PackReconciliationRunner` will update `org_settings.enabled_modules` on backend startup for every existing tenant using that profile.

## Scope

- Files to modify:
  - `portal/app/(authenticated)/home/page.tsx` (line 21–28 interface + line 60–68 fetch callback)
  - `backend/src/main/resources/vertical-profiles/legal-za.json` (line 5 array)
  - `backend/src/main/resources/vertical-profiles/accounting-za.json` (line 3 array)
  - `backend/src/main/resources/vertical-profiles/consulting-za.json` (line 7 array)
- Files to create: none
- Migration needed: no (Flyway `V75__add_vertical_modules.sql` already established the `enabled_modules` column; the profile JSON is the source-of-truth and `PackReconciliationRunner` re-syncs every tenant on startup)
- Env / config: no

## Verification

1. Portal hot-reloads the home page (Next.js HMR picks up the TSX edit without a restart).
2. Backend restart after the JSON edits so `PackReconciliationRunner` updates `tenant_5039f2d497cf.org_settings.enabled_modules`. Verify:
   ```
   docker exec b2b-postgres psql -U postgres -d docteams -c \
     "SELECT enabled_modules FROM tenant_5039f2d497cf.org_settings;"
   ```
   → JSON array includes `"information_requests"`.
3. Re-run Day 4 Checkpoint 4.4 as Sipho (magic-link authenticated). Expected:
   - `/home` renders a fourth card "Pending info requests" with count **1** (REQ-0001 is SENT, not COMPLETED).
   - Sidebar now shows a "Requests" item between Proposals and Documents.
   - Clicking either the card or the sidebar item navigates to `/requests` — this will still 404 until GAP-P-02 lands (expected).
4. Control probe — create a second info-request for a different customer; Sipho's card count must not change. Cross-tenant isolation preserved.

## Estimated Effort

**S (< 30 min)** — 1 TSX edit + 3 JSON edits + backend restart. No new code, no new tests, no migration.

## Status Triage

**SPEC_READY.** Trivially small, high confidence in root cause, zero ripple risk.
