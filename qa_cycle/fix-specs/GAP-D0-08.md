# Fix Spec: GAP-D0-08 -- Team member names display as "Unknown" in mock-auth mode

## Problem

The Team page shows all members (Alice, Bob, Carol) with "Unknown" as their display name. Email addresses are correct. This affects the Team list, member mentions, and audit log actor names.

## Root Cause (confirmed)

The mock IDP's `/token` endpoint (`compose/mock-idp/src/index.ts` lines 46-55) does not include a `name` claim in the JWT payload. The payload includes `sub`, `email`, `organization`, `groups` but NOT `name`.

When the `MemberFilter.lazyCreateMember()` creates a new member (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` line 148):
```java
String jwtName = jwt.getClaimAsString("name");
```
This returns `null` because the mock IDP doesn't set it. The member is then created with `name = null` (line 188):
```java
var member = new Member(clerkUserId, email, name, null, orgRole);
```

The frontend renders null names as "Unknown".

## Fix

**Already included in the GAP-D1-02 fix spec.** The mock IDP token payload change adds a `name` claim:
```typescript
name: user ? `${user.firstName} ${user.lastName}` : null,
```

This will produce:
- Alice: `name: "Alice Owner"`
- Bob: `name: "Bob Admin"`
- Carol: `name: "Carol Member"`

The `MemberFilter.lazyCreateMember()` will pick this up on line 148 and store the name in the member record.

## Scope

- 1 file: `compose/mock-idp/src/index.ts` (already changed in GAP-D1-02)
- 1 line addition
- Requires mock-idp Docker image rebuild + fresh E2E data (full e2e-down/up)

## Verification

1. After GAP-D1-02 fix and full E2E rebuild
2. Login as Alice, navigate to Team page
3. Members should show: "Alice Owner", "Bob Admin", "Carol Member" (after each user has logged in at least once to trigger member sync)

## Estimated Effort

0 minutes (bundled with GAP-D1-02 fix)
