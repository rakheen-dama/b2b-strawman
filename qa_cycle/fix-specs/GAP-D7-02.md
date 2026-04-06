# Fix Spec: GAP-D7-02 — Add Member dialog crashes (null name charAt)

## Problem
The "Add Member" dialog crashes with `TypeError: Cannot read properties of null (reading 'charAt')` when rendering member avatars.

## Root Cause (confirmed)
The `MemberAvatar` component in `frontend/components/projects/add-member-dialog.tsx` (line 27-34) calls `name.charAt(0)` without null-checking:

```typescript
function MemberAvatar({ name }: { name: string }) {
  const initial = name.charAt(0).toUpperCase();
  // ...
}
```

When `member.name` is null (from stale API-created members or members synced before the name-claim fix in PR #972), the component crashes.

The `CommandItem` passes `member.name` directly:
```tsx
<MemberAvatar name={member.name} />
```

## Fix

**`frontend/components/projects/add-member-dialog.tsx`:**

1. Add null guard to `MemberAvatar`:
```typescript
function MemberAvatar({ name }: { name: string | null }) {
  const initial = (name ?? "?").charAt(0).toUpperCase();
  // ...
}
```

2. Also guard the `CommandItem` value and display:
```tsx
<CommandItem
  key={member.id}
  value={`${member.name ?? ""} ${member.email}`}
  // ...
>
  <MemberAvatar name={member.name} />
  <div className="min-w-0 flex-1">
    <p className="truncate text-sm font-semibold">{member.name ?? "Unknown"}</p>
    // ...
```

## Scope
- 1 frontend file: `frontend/components/projects/add-member-dialog.tsx`
- 3 lines changed

## Verification
1. Navigate to any matter > Members tab
2. Click "Add Member"
3. Verify dialog opens without crash
4. Verify member list displays (with fallback "?" avatar for null-name members)
5. Run `pnpm test` — add-member-dialog tests pass

## Estimated Effort
15 minutes
