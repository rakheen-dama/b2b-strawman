# Fix Spec: GAP-L-66 — Portal /login form omits orgId

## Problem

When a logged-out portal user follows a deep-link (e.g. fee-note email "View Fee Note" CTA at `:3002/invoices/[id]`) to the portal, the `(authenticated)/layout.tsx` redirects to `/login` (no query string carried). The login page reads `orgId` from `useSearchParams().get("orgId")` only — so when the redirect target lacks `?orgId=…`, the form POSTs `{ email, orgId: null }` to `/portal/auth/request-link`. Backend rejects with HTTP 400 ("orgId is required") and the UI shows "Something went wrong. Please try again." Workaround during cycle-34: paste the magic-link URL from email (which carries `?orgId=mathebula-partners`).

This was first observed informationally in Day 8 cycle-20 (`status.md` Log entry §c) and again in Day 11 cycle-26; cycle-34 (Day 30 replay) elevated it to a formally tracked gap because it broke a real Day 30 deep-link return flow.

## Root Cause (verified)

1. **Portal `(authenticated)/layout.tsx:73-77`** — when `!isAuthenticated`, calls `router.replace("/login")` with **no query** (drops both the original deep-link target and any orgId hint):
   ```tsx
   useEffect(() => {
     if (hasMounted && !isAuthenticated) {
       router.replace("/login");   // ← bare /login, no orgId, no redirectTo
     }
   }, [hasMounted, isAuthenticated, router]);
   ```

2. **Portal `app/login/page.tsx:19-20, 61-71`** — `orgId` is sourced **only** from URL search params; if absent, `null` is sent in the POST body:
   ```tsx
   const orgId = searchParams.get("orgId");   // null when /login has no query
   ...
   const response = await publicFetch("/portal/auth/request-link", {
     method: "POST",
     body: JSON.stringify({ email, orgId }),  // orgId=null → 400
   });
   ```

3. **Backend `PortalAuthController.MagicLinkRequest:47-50`** — DTO field is annotated `@NotBlank(message = "orgId is required") String orgId`, so a missing/null value triggers a 400 response.

4. **Email CTA URL (root trigger)** — `InvoiceEmailService.java:80` builds `portalUrl = portalBaseUrl + "/invoices/" + invoice.getId()` — no orgId carried (intentional; the invoice email is meant for an already-authenticated user). When the session has expired, the redirect chain `/invoices/[id]` → `/login` strips that already-deficient context.

5. **`portal/lib/auth.ts:48-98` (state lifecycle)** — `getAuth()` auto-clears expired JWT *and* the `portal_customer` JSON (which contains `orgId`) before the layout's auth-guard fires, so the orgId is not recoverable from localStorage when the redirect happens.

## Fix

The fix is **portal-frontend-only** in three small steps. No backend change. No email-template change.

### Step 1 — Persist last-known `orgId` separately (survives `clearAuth`)

**File**: `portal/lib/auth.ts`

Add a third localStorage key that holds *just* the orgId, written whenever auth is stored, and **not removed by `clearAuth()`**. This is purely a routing hint (not a security credential), so it's safe to keep across logout.

```ts
// New constant near JWT_KEY / CUSTOMER_KEY
const LAST_ORG_KEY = "portal_last_org_id";

// In storeAuth(): also persist the orgId hint
export function storeAuth(jwt: string, customer: CustomerInfo): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(JWT_KEY, jwt);
  localStorage.setItem(CUSTOMER_KEY, JSON.stringify(customer));
  localStorage.setItem(LAST_ORG_KEY, customer.orgId);   // NEW
  emitAuthChange();
}

// New getter (used by /login to prefill orgId when search-param is absent)
export function getLastOrgId(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(LAST_ORG_KEY);
}

// clearAuth() should NOT remove LAST_ORG_KEY — it's a routing hint, not a credential.
// (Keep current clearAuth body unchanged.)
```

### Step 2 — Capture deep-link target + orgId hint in the layout redirect

**File**: `portal/app/(authenticated)/layout.tsx`

Pass `redirectTo` (current pathname) and best-effort `orgId` (from last-known value) when bouncing to `/login`. This preserves the deep-link round-trip.

Replace lines 73-77:

```tsx
import { usePathname } from "next/navigation";
import { getLastOrgId } from "@/lib/auth";
...
const pathname = usePathname();

useEffect(() => {
  if (hasMounted && !isAuthenticated) {
    const params = new URLSearchParams();
    if (pathname && pathname !== "/login") params.set("redirectTo", pathname);
    const lastOrg = getLastOrgId();
    if (lastOrg) params.set("orgId", lastOrg);
    const qs = params.toString();
    router.replace(qs ? `/login?${qs}` : "/login");
  }
}, [hasMounted, isAuthenticated, router, pathname]);
```

### Step 3 — Login page: source orgId from query OR last-known; thread `redirectTo` through the magic-link

**File**: `portal/app/login/page.tsx`

3a. Replace the bare `searchParams.get("orgId")` (line 20) with a chain: query → last-known.

```tsx
import { getLastOrgId } from "@/lib/auth";
...
const queryOrgId = searchParams.get("orgId");
const orgId = queryOrgId ?? getLastOrgId();   // null only if first-ever visit AND no query
const redirectTo = searchParams.get("redirectTo");
```

3b. **Validation guard** — if `orgId` is still null after both sources are tried, show an inline error rather than POSTing a guaranteed-400 body. Keep copy generic to avoid leaking tenant existence. In `handleSubmit` (around line 61-95):

```tsx
if (!orgId) {
  setError(
    "We couldn't determine which organization to sign you into. " +
    "Please use the original link from your email."
  );
  setSubmitting(false);
  return;
}
```

3c. **Pass `redirectTo` into the magic-link** so post-exchange routing lands the user on the originally-requested page, not the default `/projects`. The simplest path: send `redirectTo` to the backend in the request-link body so it can be embedded into the magic-link URL. **However**, for this LOW-severity fix we keep scope frontend-only — instead we persist `redirectTo` to `sessionStorage` here and have `/auth/exchange` read it post-token-exchange. (Backend change deferred — not needed for the deep-link return; only loses the *originally-requested* invoice URL, which can be re-clicked from email.)

```tsx
useEffect(() => {
  if (redirectTo && typeof window !== "undefined") {
    sessionStorage.setItem("portal_post_login_redirect", redirectTo);
  }
}, [redirectTo]);
```

Then in `portal/app/auth/exchange/page.tsx`, after `storeAuth()` succeeds, read and consume the key:

```ts
const redirect = sessionStorage.getItem("portal_post_login_redirect");
sessionStorage.removeItem("portal_post_login_redirect");
router.replace(redirect && redirect.startsWith("/") ? redirect : "/projects");
```

(Validate that `redirect` starts with `/` to prevent open-redirect to external hosts.)

### Step 4 — (Optional polish, not required for SPEC_READY)

Update the magic-link email body to include a notice line: "If your session has expired, you may be asked to enter your email — please use the same address this email was sent to." This is documentation-only; the form already handles the expired-session retry once orgId resolution lands.

## Scope

- **Frontend only** (portal subdirectory).
- Files to modify:
  1. `portal/lib/auth.ts` — add `LAST_ORG_KEY`, `getLastOrgId()`, persist on `storeAuth()`.
  2. `portal/app/(authenticated)/layout.tsx` — thread `pathname` + last orgId into `/login` redirect.
  3. `portal/app/login/page.tsx` — chain query → last-known orgId; guard null with inline error; persist `redirectTo` to sessionStorage.
  4. `portal/app/auth/exchange/page.tsx` — consume sessionStorage `portal_post_login_redirect` (with `/`-prefix validation) on successful token exchange.
- Files to create: none.
- Migration needed: **no**.

## Verification

Reproduce the original failing flow:

1. Pre-state: Sipho is authenticated (`portal_jwt` + `portal_customer` in localStorage; `portal_last_org_id=mathebula-partners` after fix).
2. Wait for / force JWT expiry (or simulate by wiping `portal_jwt` only via DevTools, leaving `portal_last_org_id` intact).
3. From Mailpit, open the most recent fee-note email "Fee Note INV-NNNN from Mathebula & Partners" → click "View Fee Note" CTA → browser navigates to `:3002/invoices/[id]`.
4. Expected: portal redirects to `:3002/login?orgId=mathebula-partners&redirectTo=%2Finvoices%2F[id]`.
5. Branding card renders "Mathebula & Partners" (orgId resolved → branding fetch succeeds).
6. Enter `sipho.portal@example.com` → click "Send Magic Link". Expected: success card "Check your email for a login link." Backend log shows `request-link` POST returning 200 (not 400).
7. Open the new magic-link from Mailpit → exchange completes → land on `/invoices/[id]` (the originally-requested deep-link), NOT the default `/projects`.
8. Negative test: clear all localStorage (first-time-visitor simulation), navigate to bare `/login` → form shows the inline guard "We couldn't determine which organization to sign you into. Please use the original link from your email." rather than POSTing a 400.
9. Negative test: append `?redirectTo=https://evil.example.com` → `/auth/exchange` ignores the external URL and redirects to `/projects`.

## Estimated Effort

**S** (< 30 min). Three small files + one new constant + one new helper + one effect + one validation guard.

## Tests

- `portal/app/login/__tests__/page.test.tsx` — extend existing 3 cases:
  - Add: "uses last-known orgId from localStorage when query param missing"
  - Add: "renders inline guard when orgId is unresolvable"
  - Add: "persists redirectTo to sessionStorage when present in query"
- `portal/lib/__tests__/auth.test.ts` — add:
  - "storeAuth persists portal_last_org_id"
  - "clearAuth preserves portal_last_org_id"
  - "getLastOrgId returns persisted value"
- `portal/app/auth/exchange/__tests__/page.test.tsx` — extend (file exists per directory listing):
  - Add: "redirects to sessionStorage portal_post_login_redirect when set and starts with /"
  - Add: "ignores external redirect URLs (open-redirect guard)"
- No backend test changes (DTO contract unchanged).

## Regression risk

**Low.**

- `/portal/auth/request-link` callers in the codebase: `portal/app/login/page.tsx` (this fix), `portal/lib/__tests__/api-client.test.ts` (test only), `portal/e2e/helpers/auth.ts` (e2e helper passes orgId explicitly — unaffected). Backend contract unchanged.
- `clearAuth` callers: `portal/lib/api-client.ts:31` (401 handler), `portal/hooks/use-auth.ts:62` (logout). Both call sites still wipe JWT + customer; preserving `portal_last_org_id` is intentional and does not leak credentials (orgId is a public slug appearing in branding lookups already).
- `(authenticated)/layout.tsx` redirect target change: any existing tests/E2E that assert `router.replace("/login")` exactly will need to relax the assertion. Check `portal/e2e/` for hardcoded `/login` URL assertions before merging.
- Open-redirect attack surface: the `/`-prefix check in step 3c blocks external URLs; only same-origin paths are honoured.
- The `portal_last_org_id` localStorage key persists across logouts. This is the only behavioural surface change for already-authenticated returning users — it surfaces orgId branding on a "logged out and revisiting bare `/login`" flow, which is the desired behaviour (consistent with the magic-link UX).
