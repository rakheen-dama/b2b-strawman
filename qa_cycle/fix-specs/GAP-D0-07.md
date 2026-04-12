# Fix Spec: GAP-D0-07 — Sidebar + breadcrumb show org slug instead of display name

## Problem
After login, the desktop sidebar displays the org **slug** `mathebula-partners` rendered in all-caps CSS (`uppercase`) as "MATHEBULA-PARTNERS". The breadcrumb at the top of the page also uses the slug. The firm's actual display name is "Mathebula & Partners" (stored in `public.organizations.name`). Using the slug breaks demo polish — a law firm does not refer to itself as "MATHEBULA-PARTNERS".

## Root Cause (confirmed)
File: `frontend/components/desktop-sidebar.tsx`, lines 32–36:

```tsx
<div className="flex items-center gap-2 px-4 py-3">
  <span className="truncate font-mono text-xs tracking-wider text-teal-500/80 uppercase">
    {slug}
  </span>
</div>
```

And in `frontend/app/(app)/org/[slug]/layout.tsx`, the DesktopSidebar is rendered with only the `slug` prop (line 131):
```tsx
<DesktopSidebar
  slug={slug}
  groups={groups}
  userName={userInfo.name}
  userEmail={userInfo.email}
/>
```

The breadcrumb (`frontend/components/breadcrumbs.tsx`) also takes only `slug` and displays it directly (confirmed by QA evidence in CP 0.24: "Breadcrumb: `mathebula-partners` → Dashboard").

The org display name is stored in `public.organizations.name` (see `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Organization.java` line 25–26). It is NOT currently exposed through `/bff/me` — the gateway's `BffUserInfoExtractor` pulls only alias from the KC `organization` OIDC claim. But the KC organization `name` attribute IS set correctly (QA verified: KC org has `name: "Mathebula & Partners"`, `alias: "mathebula-partners"`).

## Fix
Two tracks. Both are needed for a complete fix; implementer can ship them in one PR.

### Track 1 (backend + gateway) — Expose org display name to the frontend
1. **Gateway**: Extend `BffUserInfo` to include `orgName`. File: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`:
   - Line 33: add `String orgName` to the record.
   - Line 44 (unauthenticated factory): add `null` for `orgName`.
   - Line 82: populate `orgName`. Source: the simplest path is to let the backend resolve it via a lookup in `public.organizations` by alias/id, but since the gateway does not have that table, prefer to read it from the OIDC `organization` claim if KC includes `name` there, or add a gateway endpoint call to the backend `/internal/orgs/{alias}/info`.

2. **Check the KC OIDC claim**: if `user.getClaim("organization")` is the rich `Map<alias, {id, name, ...}>` format, the name is already available. Update `BffUserInfoExtractor.OrgInfo` to include `name` and have `extractOrgInfo` populate it. Currently lines 34–38 handle the `List<String>` shape (aliases only) and lines 41–49 handle the `Map` shape (id + roles). Add `name` extraction under the `Map` branch: `String name = (String) orgData.get("name");` — if null, fall back to `slug`.

3. **If KC does not include `name` in the claim** (likely, because KC's default `organization` scope exposes only alias/id/roles), add a lightweight backend internal endpoint:
   - `GET /internal/orgs/by-alias/{alias}` → returns `{name, alias, id}` from `public.organizations`. Protected by `ApiKeyAuthFilter` (already in place for `/internal/*`).
   - Gateway's `BffController.me()` calls this endpoint with its internal API key header and populates `orgName` in the response. Cache the result per session (store in `HttpSession` or derive on every `/bff/me` call — the endpoint is fast and `/bff/me` is also cached with React.cache on the frontend side).

### Track 2 (frontend) — Consume and render orgName
1. `frontend/lib/auth/types.ts`: add `orgName: string | null` to `AuthContext` (line 19–25 area).

2. `frontend/lib/auth/providers/keycloak-bff.ts`:
   - Line 11–20: add `orgName: string | null` to `BffUserInfo`.
   - Line 79–92 (`getAuthContext`): include `orgName: info.orgName ?? null` in the returned object.

3. `frontend/lib/auth/providers/mock/server.ts`: do the same for the mock provider. Derive from the existing mock user record (may need a seed data update; if so, include `orgName` as `"E2E Test Org"` or similar).

4. `frontend/app/(app)/org/[slug]/layout.tsx`:
   - Line 38: destructure `orgName` from `ctx`.
   - Line 130–135: pass `orgName={orgName}` to `DesktopSidebar` and `MobileSidebar`.
   - Line 144: pass `orgName={orgName}` to `Breadcrumbs`.

5. `frontend/components/desktop-sidebar.tsx`:
   - Line 14–19: add `orgName?: string | null` to `DesktopSidebarProps`.
   - Line 21: destructure `orgName`.
   - Lines 32–36: render `{orgName || slug}` instead of `{slug}`. Also remove the `uppercase` CSS class — display names should render in their natural case. Keep `tracking-wider` if it looks good; otherwise drop it for display-name context.

6. `frontend/components/mobile-sidebar.tsx`: same prop and same substitution.

7. `frontend/components/breadcrumbs.tsx`: accept `orgName` prop, substitute for the first breadcrumb segment when it matches the slug.

### Minimal path if Track 1 is too heavy for a 2-hour window
If the internal endpoint + gateway plumbing feels too scoped for this cycle, a simpler interim is to call the existing backend endpoint `GET /api/orgs/current` (if one exists) directly from the frontend layout via `lib/api.ts`. Grep the backend for an endpoint that returns current-org info by tenant. If none exists, prefer Track 1 over creating a new public API.

## Scope
- Gateway / Backend / Frontend
- Files to modify (Track 1):
  - `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`
  - `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffUserInfoExtractor.java`
  - (Maybe) `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/` — new `/internal/orgs/by-alias/{alias}` endpoint
- Files to modify (Track 2):
  - `frontend/lib/auth/types.ts`
  - `frontend/lib/auth/providers/keycloak-bff.ts`
  - `frontend/lib/auth/providers/mock/server.ts`
  - `frontend/app/(app)/org/[slug]/layout.tsx`
  - `frontend/components/desktop-sidebar.tsx`
  - `frontend/components/mobile-sidebar.tsx`
  - `frontend/components/breadcrumbs.tsx`
- Files to create: possibly one new internal controller file on the backend side (`OrgInfoInternalController.java`)
- Migration needed: no

## Verification
1. Re-run CP 0.23 end-to-end.
2. **Expected**: Sidebar shows "Mathebula & Partners" (natural case, NOT uppercase slug). Breadcrumb shows "Mathebula & Partners → Dashboard".
3. Log out, log back in as a different org, confirm the new org's name appears.
4. Regression: check org-switching (if any) still works; ensure the name updates on route change.
5. Screenshot: re-capture `day-0-cp-0-25-dashboard-wow.png` with the new name visible.

## Estimated Effort
M (30 min – 2 hr). The frontend prop-plumbing is quick; the backend path adds complexity because `/bff/me` currently has no dependency on the backend. If implementer chooses the "add name to OIDC claim" path via KC client scope mapper, effort drops to S.

## Priority Reason
MED severity, affects the single most prominent demo element: the org identity in the persistent sidebar + breadcrumbs. Visible on every screenshot. Worth fixing in Cycle 1 before customer-facing demo recording.
