# Fix Spec: GAP-AN-003 — UI toggle switch doesn't change backend state

## Problem
Clicking the toggle switch on an automation rule in the list fires what appears to be a Server Action POST (observed in network tab), but the backend rule state does not change. The API endpoint `POST /api/automation-rules/{id}/toggle` works correctly when called directly. The QA agent tested toggling "FICA Reminder (7 days)" from disabled to enabled -- the switch visual did not change and the backend confirmed `enabled=false` after the UI interaction. No error toast was observed.

## Root Cause
The toggle handler in `rule-list.tsx` (line 62-71) calls the `toggleRuleAction` server action inside `startTransition`. The server action at `actions.ts` (line 27-48) calls `toggleRule(ruleId)` which POSTs to the gateway at `/api/automation-rules/{id}/toggle`.

In Keycloak mode, the API client (`lib/api/client.ts` line 21-37) forwards the `SESSION` cookie and includes the `XSRF-TOKEN` as the `X-XSRF-TOKEN` header for mutation requests. The Spring Cloud Gateway at port 8443 validates CSRF tokens on POST/PUT/DELETE requests.

**Two potential failure modes:**

1. **CSRF token missing or stale**: If the `XSRF-TOKEN` cookie hasn't been set by the gateway (e.g., the initial page load was server-rendered and no prior client-side mutation established the cookie), the `X-XSRF-TOKEN` header will be empty. The gateway returns 403, which `toggleRuleAction` catches and returns `{success: false, error: "You do not have permission..."}`. The handler calls `toast.error(result.error)` -- but if the toast library isn't rendering or the transition swallows the result, the user sees nothing.

2. **`startTransition` swallowing errors**: React 19's `startTransition` with async functions can silently swallow errors in some edge cases. If the server action throws (as opposed to returning an error object), the error would be lost inside the transition.

3. **`revalidatePath` not triggering re-render**: Even if the toggle succeeds, the page must re-render to show the updated state. `revalidatePath` in `actions.ts` line 46 should trigger a server re-render, but the `RuleList` component receives `rules` as props from the server component. If the page doesn't re-render, the old `rules` data (with the old `enabled` value) is displayed.

**Most likely cause**: The CSRF flow issue -- since ALL mutations on the automations page fail (toggle, create via button, edit via row click -- though those last two are navigation issues, the toggle is the only actual API call that fires), this points to either a session/CSRF issue or a hydration issue preventing the transition callback from executing properly.

## Fix
Add resilience to the toggle flow with proper error visibility, and ensure the Switch reflects optimistic state so users see immediate feedback.

### Step 1: Add error logging in `toggleRuleAction`

In `frontend/app/(app)/org/[slug]/settings/automations/actions.ts`, add console.error logging before returning errors so failures are visible in the server logs:

Change the catch block (lines 33-44) to:
```typescript
  } catch (error) {
    console.error("[toggleRuleAction] Failed to toggle rule:", ruleId, error);
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to toggle automation rules.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
```

### Step 2: Add optimistic UI update to the toggle handler

In `frontend/components/automations/rule-list.tsx`, add local optimistic state for toggle so the user sees immediate feedback and error recovery:

Add state tracking:
```typescript
const [optimisticToggles, setOptimisticToggles] = useState<Record<string, boolean>>({});
```

Modify the `handleToggle` function (lines 62-71) to update optimistic state:
```typescript
function handleToggle(ruleId: string, currentEnabled: boolean) {
  // Optimistic update
  setOptimisticToggles(prev => ({ ...prev, [ruleId]: !currentEnabled }));

  startTransition(async () => {
    const result = await toggleRuleAction(slug, ruleId);
    if (result.success) {
      toast.success("Rule toggled successfully");
      // Clear optimistic state — server re-render will provide fresh data
      setOptimisticToggles(prev => {
        const next = { ...prev };
        delete next[ruleId];
        return next;
      });
    } else {
      // Revert optimistic state
      setOptimisticToggles(prev => {
        const next = { ...prev };
        delete next[ruleId];
        return next;
      });
      toast.error(result.error ?? "Failed to toggle rule");
    }
  });
}
```

Update the Switch to use optimistic state:
```tsx
<Switch
  checked={optimisticToggles[rule.id] ?? rule.enabled}
  size="sm"
  onCheckedChange={() => handleToggle(rule.id, optimisticToggles[rule.id] ?? rule.enabled)}
  onClick={(e) => e.stopPropagation()}
  disabled={!canManage || isPending}
  aria-label={`Toggle ${rule.name}`}
/>
```

### Step 3: Verify CSRF cookie is set

In the Keycloak dev stack, verify the gateway sets the `XSRF-TOKEN` cookie. The gateway's CSRF configuration should set the cookie on the first response. If the cookie is missing, the gateway's `SecurityConfig` needs `CookieCsrfTokenRepository.withHttpOnlyFalse()` (so the cookie is readable by JavaScript in the Next.js server action runtime).

**Diagnostic step for dev agent**: Run `curl -v http://localhost:8443/api/automation-rules -H "Cookie: SESSION=..." 2>&1 | grep -i 'set-cookie.*XSRF'` to verify the CSRF cookie is returned.

## Scope
Frontend only (unless CSRF cookie is not being set by gateway -- then gateway SecurityConfig needs a one-line fix)
Files to modify:
- `frontend/components/automations/rule-list.tsx` (optimistic UI)
- `frontend/app/(app)/org/[slug]/settings/automations/actions.ts` (error logging)

## Verification
Re-run QA checkpoint T1.4 — toggle a rule and verify:
1. Switch visual changes immediately (optimistic)
2. Backend state is updated (check via API)
3. On error, switch reverts and toast shows error message

## Estimated Effort
M (30 min - 2 hr) — includes CSRF investigation and possible gateway config fix.
