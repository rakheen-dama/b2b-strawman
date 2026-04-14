# Fix Spec: GAP-D0-01 — Access requests page tab switching broken

## Problem
On the platform admin access requests page, clicking "All", "Approved", or "Rejected" tabs does not change the selected state — "Pending" stays permanently highlighted. The filtering appears to work (data updates) but the visual indicator doesn't move. Reported at Day 0 checkpoint 0.11.

## Root Cause (hypothesis)
Reviewed `frontend/components/access-request/access-requests-table.tsx`. The implementation uses `TabsPrimitive.Root` with controlled `value={activeTab}` and `onValueChange={(v) => setActiveTab(v as TabId)}` (line 63). The tab triggers use `data-[state=active]` CSS for styling (line 73) and a `motion.span` with `layoutId="access-request-tab-indicator"` for the underline (line 79-84).

**Code looks correct** — the Radix Tabs should work. Possible causes:
1. **Hydration mismatch**: The QA report mentions "React hydration mismatch on `aria-controls` attribute" on most page navigations. This Radix UI SSR/CSR ID mismatch may be breaking the tab state management.
2. **Motion layoutId conflict**: If multiple `motion.span` elements with the same `layoutId` exist simultaneously (e.g., from a stale portal render), the animation may not update.
3. **Platform admin layout issue**: The platform admin page may have a different layout that interferes with Radix state.

## Fix
This is a cosmetic LOW priority issue. QA confirmed "Functional (approval works), cosmetic issue only."

1. **Quick fix**: Add `suppressHydrationWarning` to the TabsPrimitive.Root or wrap the component in a `useEffect` guard to ensure client-only rendering of the tabs.

2. **Better fix**: Replace the `motion.span` layoutId approach with a simpler CSS-only active indicator using `data-[state=active]` border-bottom styling, removing the motion dependency entirely. This eliminates the layoutId/hydration interaction.

3. **Investigate**: Check if the platform admin layout wraps the page in any context that might interfere with client-side state.

## Scope
Frontend
Files to modify:
- `frontend/components/access-request/access-requests-table.tsx`
Files to create: none
Migration needed: no

## Verification
Re-run Day 0 checkpoint 0.11: Navigate to platform admin access requests, click each tab (All, Pending, Approved, Rejected), verify the active indicator moves to the clicked tab.

## Estimated Effort
S (< 30 min)
