# Fix Spec: GAP-C-03 — Terminology overrides miss settings nav labels

## Problem

Day 0 checkpoints 0.16, 0.28, 0.56: scenario expects the consulting-za terminology profile to rename "Time Tracking" → "Time Logs" and "Rates & Currency" → "Billing Rates" in the settings sidebar. Only "Customer" → "Client" is actually applied. The two other overrides are silently ignored.

## Root Cause (confirmed via grep)

The terminology map HAS entries for "Time Entry" → "Time Log" and "Rate Card" → "Billing Rates", but the settings sidebar uses different hardcoded labels.

File: `frontend/components/settings/settings-nav-groups.ts` line 32: `{ label: "Time Tracking", href: "time-tracking" }` — the label is literally `"Time Tracking"`, not `"Time Entry"` or `"Time Entries"`.
Line 57: `{ label: "Rates & Currency", href: "rates" }` — label is `"Rates & Currency"`, not `"Rate Card"` or `"Rate Cards"`.

File: `frontend/lib/terminology-map.ts` lines 2–15 for `consulting-za`:
```ts
"Time Entry": "Time Log",
"Time Entries": "Time Logs",
"Rate Card": "Billing Rates",
"Rate Cards": "Billing Rates",
```

File: `frontend/components/settings/settings-sidebar.tsx` lines 15–27 (`translateNavLabel`) only checks prefix terms `["Project", "Customer", "Client", "Proposal", "Invoice"]` for multi-word labels and otherwise falls through to `t(label)` — which finds no match for `"Time Tracking"` or `"Rates & Currency"` in the map, so they render unchanged.

## Fix

Add the exact sidebar label strings as additional keys in the terminology map so they translate.

### Change

File: `frontend/lib/terminology-map.ts`

Inside the `"consulting-za"` block (lines 2–15), add these entries:

```ts
"Time Tracking": "Time Logs",
"Rates & Currency": "Billing Rates",
```

Full updated consulting-za block:

```ts
"consulting-za": {
  Customer: "Client",
  Customers: "Clients",
  customer: "client",
  customers: "clients",
  "Time Entry": "Time Log",
  "Time Entries": "Time Logs",
  "time entry": "time log",
  "time entries": "time logs",
  "Rate Card": "Billing Rates",
  "Rate Cards": "Billing Rates",
  "rate card": "billing rates",
  "rate cards": "billing rates",
  "Time Tracking": "Time Logs",
  "Rates & Currency": "Billing Rates",
},
```

### Alternative (not preferred)

Rename the labels in `settings-nav-groups.ts` to `"Time Entries"` and `"Rate Cards"` so the existing map keys match. Rejected because:
- Changes the default (English, no-profile) label users see — more invasive.
- Would ripple into tests that assert the current default labels.

Adding map keys is the lower-risk path.

## Scope

Frontend (map-only)
Files to modify: `frontend/lib/terminology-map.ts`
Files to create: none
Migration needed: no

## Verification

1. Frontend HMR picks it up.
2. As Zolani (consulting-za tenant), click Settings. Expect sidebar to show "Time Logs" (under Work group) and "Billing Rates" (under Finance group) instead of "Time Tracking" / "Rates & Currency".
3. Legal-za and accounting-za tenants should be unaffected (those maps don't include the new keys — they fall through to identity translation).
4. Re-run Day 0 checkpoint 0.16 + 0.28 + 0.56 and close GAP-C-03.

## Estimated Effort

S (< 10 min). 2-line addition to one file.
