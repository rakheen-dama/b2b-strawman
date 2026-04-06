# Fix Spec: GAP-D0-06 — Team page Role column empty for all members

## Problem
The Team page shows all 3 members (Alice, Bob, Carol) but the Role column is empty for all of them. No "Owner", "Admin", or "Member" badges appear.

## Root Cause (confirmed)
In mock auth mode, the `MockMemberList` component (`frontend/components/team/member-list.tsx` line 46-91) uses `useOrgMembers()` from `frontend/lib/auth/client/hooks.ts`. This hook fetches members from `GET /api/members` (the backend endpoint).

The backend endpoint (`OrgMemberController.java` line 48-49) returns `OrgMemberResponse` with field name `orgRole` (a Java record field).

The frontend type `OrgMemberInfo` (`frontend/lib/auth/types.ts` line 36-41) has field name `role` (not `orgRole`).

When the JSON response `{ ..., "orgRole": "owner" }` is cast as `OrgMemberInfo`, the `role` field is `undefined` because there's no JSON field named `role` — only `orgRole`.

The `MemberRow` component then looks up `ROLE_BADGES[member.role]` (line 188) which is `ROLE_BADGES[undefined]` = `undefined`, so `isSystemRole` is false. The fallback at line 222 shows `member.orgRoleName ?? member.role` — both are undefined, resulting in an empty display.

Additionally, even if the field name matched, the `ROLE_BADGES` lookup expects `"org:owner"` format (line 20-22), but the backend returns `"owner"` without the `org:` prefix. The `normalizeRole()` function in `member-actions.ts` (line 28-33) handles this for the BFF path but is NOT used in the mock path.

## Fix
In the `useOrgMembers()` hook, map the backend response to match the expected `OrgMemberInfo` type.

### In `frontend/lib/auth/client/hooks.ts`, change lines 48-51:
**Before:**
```tsx
.then((res) => (res.ok ? res.json() : []))
.then((data: OrgMemberInfo[]) => {
  if (!cancelled) {
    setMembers(Array.isArray(data) ? data : []);
  }
})
```

**After:**
```tsx
.then((res) => (res.ok ? res.json() : []))
.then((data: Array<Record<string, unknown>>) => {
  if (!cancelled) {
    const mapped: OrgMemberInfo[] = (Array.isArray(data) ? data : []).map((m) => ({
      id: String(m.id ?? ""),
      email: String(m.email ?? ""),
      name: m.name ? String(m.name) : null,
      role: normalizeRole(String(m.orgRole ?? m.role ?? "member")),
    }));
    setMembers(mapped);
  }
})
```

Also add a local `normalizeRole` function (or import it) at the top of the file:
```tsx
function normalizeRole(role: string): string {
  const lower = role.toLowerCase();
  if (lower.startsWith("org:")) return lower;
  return `org:${lower}`;
}
```

## Scope
Frontend
Files to modify:
- `frontend/lib/auth/client/hooks.ts` (lines 48-51, add normalizeRole helper)
Files to create: none
Migration needed: no

## Verification
1. Navigate to `/org/e2e-test-org/team`
2. Alice should show "Owner" badge
3. Bob should show "Admin" badge
4. Carol should show "Member" badge

## Estimated Effort
S (< 30 min)
