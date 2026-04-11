# Fix Spec: BUG-KC-001 — Settings page crashes on client-side sidebar navigation

## Problem
Clicking the "Settings" link in the sidebar crashes the app with a React error: "Rendered more hooks than during the previous render" at `updateWorkInProgressHook -> updateMemo -> useMemo -> Router`. The crash only occurs during client-side navigation (Link click); navigating directly via URL to `/org/{slug}/settings/general` works fine. This blocks natural access to all settings pages.

## Root Cause (hypothesis)
The sidebar Settings link points to `/org/{slug}/settings` (defined in `frontend/lib/nav-items.ts` line 219, UTILITY_ITEMS). The `settings/page.tsx` (`frontend/app/(app)/org/[slug]/settings/page.tsx`) is an async RSC that immediately calls `redirect()` to `/org/{slug}/settings/general`.

During client-side navigation, Next.js renders the settings page server-side and encounters the `redirect()`. This redirect mid-navigation causes the Next.js internal Router component to encounter a different hook count between renders, because the settings layout (`settings/layout.tsx`) mounts a new `SettingsSidebar` client component with hooks during what the Router expected to be a simple page swap. The rapid redirect (`/settings` -> `/settings/general`) during a single client-side navigation triggers a hooks ordering violation in the Router's useMemo.

The root cause is that the sidebar link targets a page that only exists to redirect, forcing a two-step server navigation during a single client-side route change.

## Fix
1. **Change the Settings sidebar link to point directly to the final destination.**
   - In `frontend/lib/nav-items.ts`, change the Settings item in `UTILITY_ITEMS` (line 219):
     - From: `href: (slug) => \`/org/${slug}/settings\``
     - To: `href: (slug) => \`/org/${slug}/settings/general\``

2. **Keep `settings/page.tsx` as-is** — it still serves as a fallback redirect for direct URL navigation to `/org/{slug}/settings` (bookmarks, external links), which works fine as a full page load.

## Scope
Frontend only.

Files to modify:
- `frontend/lib/nav-items.ts` (line 219: change Settings href)

Files to create: none
Migration needed: no

## Verification
- Re-run NAV-01.16: Click Settings in sidebar from any page. Should navigate to settings/general without crash.
- Verify other sidebar navigation items still work (NAV-01.1 through NAV-01.15).
- Verify direct URL to `/org/{slug}/settings` still redirects to `/settings/general`.
- Verify the mobile sidebar Settings link also works.

## Estimated Effort
S (< 30 min) — single line change in nav-items.ts
