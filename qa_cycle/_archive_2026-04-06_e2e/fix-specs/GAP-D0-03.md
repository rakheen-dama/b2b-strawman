# Fix Spec: GAP-D0-03 — No Settings > Modules page

## Problem
No Settings > Modules page exists to verify or toggle legal modules. Modules are implicitly enabled via sidebar links after applying the vertical profile.

## Root Cause (hypothesis)
The modules page was never built. The vertical module system (Phase 49) implemented backend module registry (`VerticalModuleRegistry`), module guard (`VerticalModuleGuard`), and profile-based module enablement. The frontend received sidebar filtering (`NavZone` — items with `requiredModule` are hidden when module is disabled) and `OrgProfileProvider` context. But no dedicated Settings page for viewing/managing modules was built.

## Fix
**WONT_FIX for this QA cycle.** Building a modules settings page requires:
- New frontend page at `frontend/app/(app)/org/[slug]/settings/modules/page.tsx`
- Settings nav entry in `frontend/components/settings/settings-nav-groups.ts`
- Module listing from `GET /api/verticals/modules` endpoint (already exists)
- Toggle UI (if module toggling is desired beyond profile-based)

This exceeds the 2-hour scope limit and is cosmetic — modules work correctly via the profile system.

## Scope
N/A (WONT_FIX)

## Estimated Effort
M-L (1-3 hr) — new page, settings nav entry, module listing UI
