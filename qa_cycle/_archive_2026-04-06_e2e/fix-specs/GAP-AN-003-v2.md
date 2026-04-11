# Fix Spec: GAP-AN-003 (v2) — Toggle switch blocked by gateway BFF 302

## Problem

The automation rule toggle switch does not change the backend state. The frontend fix from Cycle 1 (optimistic UI, error logging in commit 3f605219) is correct — the toggle handler, server action, and error handling all work properly. The issue is that the underlying API call through the gateway BFF returns HTTP 302 instead of proxying to the backend.

This was verified in QA Cycle 2: the backend API `POST /api/automation-rules/{id}/toggle` works when called directly on port 8080, but fails when routed through the gateway on port 8443.

## Root Cause

**This item is blocked by OBS-AN-006** (gateway BFF returns 302 for all server action mutations).

The frontend code in `rule-list.tsx` and `actions.ts` is correct:
- `rule-list.tsx` (lines 62-86): Optimistic toggle with revert on error, toast notifications
- `actions.ts` (lines 27-49): Server action with error logging, calls `toggleRule(ruleId)`
- `toggleRule` in `lib/api/automations.ts` (line 216-222): `api.post("/api/automation-rules/{id}/toggle")`
- `api.post` routes through `apiRequest` in `lib/api/client.ts`, which forwards the SESSION cookie to the gateway

The gateway at port 8443 returns 302 for the POST request. See OBS-AN-006 fix spec for full root cause analysis.

## Fix

**No additional frontend changes needed.** The fix for OBS-AN-006 (gateway security config + frontend redirect handling) will unblock this item.

Once OBS-AN-006 is fixed:
1. The gateway will return 401 (not 302) for expired sessions, which the frontend handles via redirect to sign-in
2. Valid sessions will proxy POST requests correctly to the backend
3. The existing optimistic UI and error handling in `rule-list.tsx` will work as designed

## Scope

No changes — depends entirely on OBS-AN-006 fix.

## Verification

After OBS-AN-006 is fixed:
1. Navigate to Settings > Automations
2. Toggle the "FICA Reminder (7 days)" rule from disabled to enabled
3. Verify: Switch flips immediately (optimistic UI)
4. Verify: Backend state changes (call `GET /api/automation-rules` and check `enabled` field)
5. Verify: Success toast appears ("Rule toggled successfully")
6. Toggle back to disabled, verify same behavior
7. Disconnect network/invalidate session, toggle a rule — verify error toast and switch revert

## Estimated Effort

None (blocked by OBS-AN-006). After OBS-AN-006 is fixed, this should work without any additional changes.
