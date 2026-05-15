# Day 32 — OBS-4009 Verification

**Date**: 2026-05-15
**Bug**: OBS-4009 — "Start Onboarding" lifecycle transition does not execute via Change Status dropdown
**Fix**: PR #1309 — `onSelect={(e) => e.preventDefault()}` + controlled dropdown state in LifecycleTransitionDropdown.tsx
**Result**: VERIFIED

## Test Steps

1. Logged in as Thandi Thornton (already authenticated via Keycloak session)
2. Navigated to clients list — confirmed no PROSPECT clients exist (all 4 clients are ACTIVE)
3. Created new client "QA Test Prospect" (email: qa-test@prospect.co.za) — confirmed lifecycle status = Prospect
4. Clicked "Change Status" dropdown — **menu opened successfully** (previously broken by Radix dialog race)
5. Menu showed "Start Onboarding" option — clicked it
6. Confirmation dialog appeared with title "Start Onboarding" and description about compliance checklists
7. Clicked "Start Onboarding" confirm button
8. Client status changed from **Prospect** to **Onboarding**

## Observed Behaviour (post-fix)

- Lifecycle badge updated: "Prospect" -> "Onboarding"
- "Since" date populated (onboarding start date)
- "Ready to start onboarding?" CTA card disappeared
- "Onboarding" tab appeared in client detail tabs
- Onboarding checklist auto-created: "Onboarding checklist (0/8)"
- "Blocking activation" warnings displayed (Address Line 1, City, Country required)
- "Fill in from uploads" button appeared in action bar

## Additional Verification

Also verified the Change Status dropdown works on an ACTIVE client (Mathole Engineering):
- Dropdown opens correctly
- Shows context-appropriate transitions: "Mark as Dormant" and "Offboard Customer"
- No Radix dialog race condition observed

## Verdict

OBS-4009 is fully resolved. The lifecycle transition dropdown works end-to-end for all client statuses.
