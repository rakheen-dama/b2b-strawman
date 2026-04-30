# Fix Spec: BUG-CYCLE26-09 — Client-ledger detail breadcrumb shows raw slug + raw UUID

## Problem

Evidence: `qa_cycle/checkpoint-results/cycle21-day10-10.7-sipho-ledger-detail.yml:88-100`

On `/trust-accounting/client-ledgers/{customerId}` the breadcrumb renders:

```
Mathebula & Partners › Trust Accounting › client-ledgers › c4f70d86-c292-4d02-9f6f-2e900099ba57
```

Instead of:

```
Mathebula & Partners › Trust Accounting › Client Ledgers › Sipho Dlamini   (or "Client Ledger")
```

The third segment is the raw URL slug (`client-ledgers`, kebab-case, lowercased) and the fourth segment is the raw customer UUID. Affects every per-customer ledger detail page.

## Root Cause (verified)

`frontend/components/breadcrumbs.tsx` is a layout-level client component that builds breadcrumb labels purely from `usePathname()` segments via two static lookup maps:

- `SEGMENT_LABELS` (lines 8-49) — maps slug → human label (e.g. `"trust-accounting" → "Trust Accounting"`). **`client-ledgers` is missing.**
- `PARENT_SEGMENT_FALLBACKS` (lines 52-58) — when a segment is a UUID, the parent slug is looked up here to produce a singular noun (e.g. `"projects" → "Project"`, so `/projects/{uuid}` renders `"Project"`). **`client-ledgers` is missing here too.**

Resolution path at lines 90-101:
```ts
const rawLabel =
  SEGMENT_LABELS[segment] ??
  (isUuid(segment) && parentSegment
    ? (PARENT_SEGMENT_FALLBACKS[parentSegment] ?? segment)
    : segment);
```

When neither map has an entry, it falls through to `segment` itself — yielding the raw slug `"client-ledgers"` for the third segment and the raw UUID `c4f70d86-…` for the fourth.

The Breadcrumbs component is rendered in the org layout (`frontend/app/(app)/org/[slug]/layout.tsx:156`) and cannot fetch the customer name without an extra round-trip per page load. The pragmatic fix is to use a **generic singular-noun fallback** for the UUID segment (matching the existing `projects → Project`, `customers → Customer` pattern), not an async customer lookup. The customer's name is already shown in the page heading + the "Back to Client Ledgers" link, so the breadcrumb's job is just structural orientation.

## Fix

Two-line change to `frontend/components/breadcrumbs.tsx`:

### Step 1 — Add `client-ledgers` to `SEGMENT_LABELS`

After line 47 (`"court-calendar": "Court Calendar",`), in the `// Vertical module pages` block, add:

```ts
"client-ledgers": "Client Ledgers",
```

This handles the `/trust-accounting/client-ledgers` list page breadcrumb (third segment).

### Step 2 — Add `client-ledgers` to `PARENT_SEGMENT_FALLBACKS`

After line 56 (`customers: "Customer",`), add:

```ts
"client-ledgers": "Client Ledger",
```

This handles the `/trust-accounting/client-ledgers/{uuid}` detail page breadcrumb (fourth segment) — the UUID becomes "Client Ledger" instead of the raw UUID string.

### Result

Breadcrumb on the detail page will render:

```
Mathebula & Partners › Trust Accounting › Client Ledgers › Client Ledger
```

This matches the existing pattern used for `/projects/{uuid}` (renders "Project"), `/customers/{uuid}` (renders "Customer"), `/invoices/{uuid}` (renders "Invoice"), `/proposals/{uuid}` (renders "Proposal"). Consistent with the existing UX vocabulary and avoids any backend lookup.

### Why not "Sipho Dlamini" (the actual customer name)?

Showing the customer's name in the breadcrumb would require:
1. Fetching the customer by ID inside the layout (extra API call on every navigation), **or**
2. Threading a `customerName` prop from the page through the layout (Next.js layouts can't read from children — would need a context provider or parallel routes).

Both add structural complexity for a low-severity cosmetic fix. The page heading already shows "Sipho Dlamini's Trust Ledger" prominently, and the "Back to Client Ledgers" link clearly anchors the user. The "Client Ledger" generic fallback matches every other UUID-route in the app and is the consistent, surgical fix.

## Scope

**Frontend only.**

Files to modify:
- `frontend/components/breadcrumbs.tsx` — two new map entries

Files to create: **none**

Migration needed: **no**

## Verification

Re-capture `qa_cycle/checkpoint-results/cycle21-day10-10.7-sipho-ledger-detail.yml`. The breadcrumb section (currently lines 83-96) should now show:

```yaml
- navigation "Breadcrumb" [ref=...]:
  - link "Mathebula & Partners" [ref=...]
  - generic [ref=...]:
    - link "Trust Accounting" [ref=...]
  - generic [ref=...]:
    - link "Client Ledgers" [ref=...]    # was: "client-ledgers"
  - generic [ref=...]:
    - generic [ref=...]: Client Ledger    # was: "c4f70d86-c292-4d02-9f6f-2e900099ba57"
```

Also navigate to the `/trust-accounting/client-ledgers` list page (no UUID) and confirm its breadcrumb terminates at "Client Ledgers".

Browser verification: as Thandi (Owner) navigate to Trust Accounting → Client Ledgers → click any client row → inspect header breadcrumb.

## Estimated Effort

S (< 15 min)
