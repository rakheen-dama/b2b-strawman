# Fix Spec: GAP-L-23 — `/settings/general` server-actions loader crashes with `ReferenceError: PortalDigestCadence is not defined`

## Problem

Every server-action POST to `/org/{slug}/settings/general` returns HTTP 500. Day 1 Checkpoint 1.1 captured 17 × POST → 500 in a single turn. Impacted actions: Save Settings (brand colour / logo / document footer / tax config), Upload Logo confirm, Vertical Profile fetch/apply, Portal Digest Cadence update, Portal Retainer Member Display update, Org Document upload initiate/confirm. DB check confirms nothing persists — `SELECT brand_color FROM tenant_5039f2d497cf.org_settings` returns empty after a Save Settings click. Frontend log (`.svc/logs/frontend.log`) prints `ReferenceError: PortalDigestCadence is not defined` at module evaluation of `.next-internal/server/app/(app)/org/[slug]/settings/general/page/actions.js` line 15 (`export {updatePortalDigestCadence} from 'ACTIONS_MODULE4'`).

## Root Cause (confirmed)

**File:** `frontend/app/(app)/org/[slug]/settings/general/portal-actions.ts` — line 10

```ts
"use server";

import { api, ApiError } from "@/lib/api";
import { revalidatePath } from "next/cache";
import type {
  PortalDigestCadence,
  PortalRetainerMemberDisplay,
} from "@/lib/types/settings";

export type { PortalDigestCadence, PortalRetainerMemberDisplay };   // ← BUG
```

The file carries a `"use server"` directive. Next.js 16's server-actions loader (turbopack) only permits exports whose identifiers exist as values in the bundled module. The `export type { ... }` re-export is compiled by turbopack into a plain `export { PortalDigestCadence, PortalRetainerMemberDisplay }` in the generated actions loader (`page/actions.js`) — but the originals were imported with `import type` and therefore stripped during compilation, leaving a dangling value reference. Module evaluation throws `ReferenceError: PortalDigestCadence is not defined` before any action body runs — which is why ALL actions on the page fail, not just the two portal ones.

**Server-actions rule:** every export in a `"use server"` module must be an async function. Type-only re-exports are disallowed.

**Downstream consumer:** `frontend/components/settings/portal-settings-section.tsx` lines 13–18 currently imports `type PortalDigestCadence` and `type PortalRetainerMemberDisplay` from `@/app/(app)/org/[slug]/settings/general/portal-actions`. Those types originate in `frontend/lib/types/settings.ts` lines 62–67 — the consumer can import them directly from the types file, bypassing the broken re-export entirely.

## Fix

**Step 1.** Edit `frontend/app/(app)/org/[slug]/settings/general/portal-actions.ts`:

- Delete line 10 (`export type { PortalDigestCadence, PortalRetainerMemberDisplay };`).
- Keep the `import type { ... }` on lines 5–8 — the types are still needed for the async-function parameter annotations on lines 22 and 50.

After the edit, every export in the file is an `async function` — matches the server-actions contract.

**Step 2.** Edit `frontend/components/settings/portal-settings-section.tsx` lines 13–18:

Change from:

```ts
import {
  updatePortalDigestCadence,
  updatePortalRetainerMemberDisplay,
  type PortalDigestCadence,
  type PortalRetainerMemberDisplay,
} from "@/app/(app)/org/[slug]/settings/general/portal-actions";
```

To:

```ts
import {
  updatePortalDigestCadence,
  updatePortalRetainerMemberDisplay,
} from "@/app/(app)/org/[slug]/settings/general/portal-actions";
import type {
  PortalDigestCadence,
  PortalRetainerMemberDisplay,
} from "@/lib/types/settings";
```

This is the canonical pattern — values come from the server-actions file, types come from `lib/types/settings.ts` where they are authoritatively defined.

**Step 3.** Verify no other file depends on the re-export from `portal-actions.ts`:

```
Grep for:
  from "@/app/(app)/org/[slug]/settings/general/portal-actions"
```

Expected results: only `portal-settings-section.tsx` and `__tests__/settings/portal-settings-cadence.test.tsx`. The test file (lines 13–15) mocks the module and does not depend on the re-exported types. No further edits needed.

## Scope

- Frontend only
- Files to modify:
  - `frontend/app/(app)/org/[slug]/settings/general/portal-actions.ts` (delete 1 line)
  - `frontend/components/settings/portal-settings-section.tsx` (change import lines 13–18)
- Files to create: none
- Migration needed: no

## Verification

1. Restart frontend dev server (the server-actions loader bundle needs to be regenerated).
2. Re-run Day 1 Checkpoint 1.1 via QA harness:
   - Sign in as Thandi → navigate to `/org/mathebula-partners/settings/general`.
   - Expect "Vertical Profile" card now loads with profile list (confirms GAP-L-24 also resolved — see note on L-24 in status.md).
   - Type `#1B3358` into Brand Color → click **Save Settings** → expect HTTP 200 (no 500s in frontend log, no `ReferenceError`).
   - Reload page → brand colour remains `#1B3358`.
   - DB read-only check: `SELECT brand_color FROM tenant_5039f2d497cf.org_settings` → returns `#1B3358`.
3. Re-run Day 1 Checkpoint 1.3 (Vertical Profile apply button — should now be enabled since profiles load).
4. Spot-check: click the Portal Digest Cadence dropdown, change from `WEEKLY` → `BIWEEKLY` → expect 200 and the value persists.
5. Existing unit test: `pnpm --filter frontend test portal-settings-cadence` should still pass (no contract change in the action signatures).
6. `pnpm --filter frontend run lint` — zero new warnings.
7. `pnpm --filter frontend run build` — verify production build succeeds (turbopack also runs the server-actions loader at build time).

## Estimated Effort

**S (< 30 min)** — two small edits + restart + QA verification. No tests need authoring; existing coverage is sufficient.
