# Fix Spec: GAP-PE-003 — Raw JSON error on 404 portal pages

## Problem
When a portal user navigates to a project they don't have access to (or doesn't exist), the page displays raw API error text: `API error: 404 {"detail":"No project found..."}`. This is a bad UX — should show a friendly "Project not found" page.

Evidence from QA Cycle 1 (T7.3): Direct URL access to another customer's resource shows raw JSON error instead of user-friendly message.

## Root Cause (hypothesis)
`portal/lib/api-client.ts` line 48-49:
```typescript
const body = await response.text();
throw new Error(`API error: ${response.status} ${body}`);
```
The `portalGet()` helper throws an `Error` with the raw response body concatenated. The project detail page (`portal/app/(authenticated)/projects/[id]/page.tsx` lines 76-80) catches this and displays it directly:
```typescript
setError(err instanceof Error ? err.message : "Failed to load project");
```
The error message `"API error: 404 {"detail":"No project found..."}"` is shown as-is in a red error banner.

## Fix

Two changes:

### 1. Improve `portalGet()` error messages (api-client.ts)
Parse the error body as JSON and extract a user-friendly message:
```typescript
export async function portalGet<T>(path: string): Promise<T> {
  const response = await portalFetch(path);
  if (!response.ok) {
    if (response.status === 404) {
      throw new Error("The requested resource was not found.");
    }
    if (response.status === 403) {
      throw new Error("You don't have permission to access this resource.");
    }
    throw new Error("Something went wrong. Please try again later.");
  }
  return response.json() as Promise<T>;
}
```

### 2. Improve project detail error display (projects/[id]/page.tsx)
Replace the generic error banner with a more helpful "not found" state:
```tsx
if (error) {
  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <p className="text-lg font-medium text-slate-600">Project not found</p>
      <p className="mt-1 text-sm text-slate-500">
        This project may have been removed or you may not have access.
      </p>
      <Link href="/projects" className="mt-4 text-sm text-teal-600 hover:underline">
        Back to projects
      </Link>
    </div>
  );
}
```

## Scope
Portal frontend only
Files to modify:
- `portal/lib/api-client.ts` — improve error messages for common HTTP status codes
- `portal/app/(authenticated)/projects/[id]/page.tsx` — friendlier error state with back link
Migration needed: no

## Verification
- Re-run T7.3.1: Direct URL access to unauthorized project shows friendly "not found" page
- Verify no raw JSON visible in any error state

## Estimated Effort
S (< 30 min)
