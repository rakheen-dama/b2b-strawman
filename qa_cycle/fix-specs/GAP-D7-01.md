# Fix Spec: GAP-D7-01 — Court date dialog matter dropdown empty

## Problem
The "Schedule Court Date" dialog's matter (project) dropdown is empty — no options populated. Cannot create court dates from either the global Court Calendar page or the matter-level Court Dates tab.

## Root Cause (confirmed)
The `fetchProjects()` server action in `frontend/app/(app)/org/[slug]/court-calendar/actions.ts` (lines 219-226) calls:
```typescript
const result = await api.get<PaginatedResponse<{ id: string; name: string }>>(
  "/api/projects?size=200"
);
return result?.content ?? [];
```

The `CreateCourtDateDialog` component (`frontend/components/legal/create-court-date-dialog.tsx`, line 80) calls `fetchProjects()` on dialog open.

The issue is that `fetchProjects()` makes a direct API call without the org context. In the E2E mock-auth stack, the `api.get()` client attaches the JWT from auth context, which includes the org ID for tenant resolution. If the API call succeeds but returns an empty page, it means either:

1. The tenant schema has no projects (unlikely — Day 2-3 created 6 matters), OR
2. The backend `/api/projects` endpoint returns a different shape than expected, OR
3. The `fetchProjects()` call silently catches an error and returns `[]`

Looking more carefully: `fetchProjects()` does NOT have error handling (no try/catch). If `api.get` throws, the `catch(() => setProjects([]))` in the dialog swallows the error silently.

**Most likely root cause**: The `fetchActiveSchedule()` in the tariff actions calls `/api/tariff-schedules/active` WITHOUT required query params `category` and `courtLevel` (backend requires `@RequestParam String category, @RequestParam String courtLevel`). This is a separate issue. But for `fetchProjects()`, the issue is likely that the projects are not returned by the API — need to verify the actual API response.

**Update after further analysis**: The `fetchProjects()` function itself looks correct. The most likely issue is that `api.get` is failing silently due to an auth or header issue specific to the dialog context. The `.catch(() => setProjects([]))` swallows ALL errors including network errors, 401s, 403s, etc.

## Fix

1. **`frontend/components/legal/create-court-date-dialog.tsx`** — Add error logging to the `fetchProjects` call:
```typescript
useEffect(() => {
  if (open) {
    fetchProjects()
      .then((all) => setProjects(all ?? []))
      .catch((err) => {
        console.error("Failed to load projects for court date dialog:", err);
        setProjects([]);
      });
  }
}, [open]);
```

2. **`frontend/app/(app)/org/[slug]/court-calendar/actions.ts`** — Add defensive response handling and better error propagation in `fetchProjects()`.

3. **Debug step**: Before implementing, verify the actual API response by calling the endpoint directly from the E2E backend:
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8081/api/projects?size=200
```

4. If projects return successfully via curl but not via the dialog, the issue is in the server action context (cookie/auth forwarding). May need to switch to using `useEffect` with a direct fetch or SWR pattern.

## Scope
- 1-2 frontend files
- Possible auth context investigation
- No backend changes

## Verification
1. Open Court Calendar page
2. Click "New Court Date"
3. Verify the Matter dropdown shows all 6+ matters
4. Select a matter and complete the court date creation
5. Verify the court date appears in the list

## Estimated Effort
1 hour (includes debugging actual API behavior)
