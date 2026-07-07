# AIVERIFY-003 — 404 on customer-page assistant/pending-suggestions widget

- **Severity**: Low
- **Disposition**: **SPEC_READY** (real FE bug confirmed + reproduced; root cause is a same-origin client fetch with no proxy, NOT a capability denial and NOT a backend path bug)
- **Area**: Frontend (Next.js keycloak BFF routing)
- **Effort**: S
- **Migration**: none

## Problem

On the customer-detail page (`/org/{slug}/customers/{id}`) the `PendingSuggestionsWidget`
issues:

```
GET /api/assistant/invocations?contextEntityType=customer&contextEntityId=<uuid>&status=PENDING_APPROVAL&size=10  → 404
```

QA observed this 404 at V3 (`qa_cycle/checkpoint-results/V3.md:66`).

## Root cause (grounded, reproduced)

The prior hypothesis (404-as-403 capability obscurity, or backend path mismatch) is **WRONG**.
The capability check passes and the backend route exists. The 404 comes from **Next.js itself**,
because the widget makes a **same-origin browser fetch that nothing proxies to the gateway**.

Evidence chain (files read):

1. **The widget is a Client Component** doing a browser `fetch` via SWR:
   `frontend/components/assistant/queue/pending-suggestions-widget.tsx:1` (`"use client"`),
   `:30-40` calls `listInvocationsClient(...)`.

2. **The client fetch uses `API_BASE = ""` in keycloak mode** — i.e. it targets the **same origin
   (`http://localhost:3000`)**, not the gateway:
   `frontend/lib/api/assistant-specialists.ts:58-60`
   ```ts
   const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
   const API_BASE = AUTH_MODE === "keycloak" ? "" : process.env.NEXT_PUBLIC_BACKEND_URL || "http://localhost:8080";
   ```
   `:262-272` `listInvocationsClient` then does `fetch(\`${API_BASE}/api/assistant/invocations...\`)`
   → resolves to `http://localhost:3000/api/assistant/invocations...`.
   The inline comment ("relative URL — SESSION cookie forwarded automatically") is **misleading**:
   a relative URL points back at the Next.js origin (:3000), not at the gateway (:8443).

3. **Next.js has no handler or rewrite for `/api/assistant/invocations`:**
   - `frontend/next.config.ts` has **no `rewrites()`** at all (only one unrelated `redirects()` for
     `/org/:slug/settings/team`). There is **no `/api/** → gateway` proxy anywhere in the frontend**
     (grep across the repo found none).
   - `frontend/app/api/` contains only `customers/route.ts` and `invoices/[id]/preview/route.ts` —
     **no `assistant/invocations` route handler**.
   - The keycloak auth middleware (`frontend/lib/auth/middleware.ts:59-100`) only gates auth and does
     `NextResponse.next()` for authenticated `/api/**` requests — it **does not rewrite/proxy** them to
     the gateway.

   So an authenticated browser request to `:3000/api/assistant/invocations` passes the middleware,
   reaches the Next.js router, matches nothing → **404**.

4. **The backend route exists and is reachable** (rules out a backend path/capability cause):
   - `AiSpecialistInvocationController.java:26` `@RequestMapping("/api/assistant/invocations")`,
     `:37-55` `@GetMapping` accepts `contextEntityType` (String) + `contextEntityId` (UUID) +
     `status` (`InvocationStatus`, `PENDING_APPROVAL` is a valid enum value) + `size`.
   - Capability: `@RequiresCapability("AI_ASSISTANT_USE")` (`:38`). A denial would be **403**, not 404
     (`CapabilityAuthorizationManager.java:40` → `AuthorizationDecision(false)` → Spring Security 403).
   - The test actor is Nomsa (**owner**); `OrgRoleService.resolveCapabilities():68-69` grants owners
     `Capability.ALL_NAMES`, which **includes `AI_ASSISTANT_USE`** — so even though no role is
     DB-seeded with `AI_ASSISTANT_USE` (verified: `tenant_c6107524c9b4.org_role_capabilities` has only
     `AI_EXECUTE/AI_MANAGE/AI_REVIEW`), the owner still passes the check at runtime. The 404 is
     therefore not a capability issue.

### Live reproduction

```
curl :8080/api/assistant/invocations?...   → HTTP 401   (route exists at backend; auth required)
curl :3000/api/assistant/invocations?...   → HTTP 307   (unauth → middleware login redirect)
```
Authenticated, the :3000 request passes the middleware and then 404s at the Next.js router (no
handler / no proxy) — exactly the QA observation.

> Note: this is a **whole-module** defect, not just the list call. `approveInvocation`,
> `rejectInvocation`, `retryInvocation`, `bulkApproveInvocations`, `listSpecialists` in the same file
> (`assistant-specialists.ts:177-272`) all use the same `API_BASE=""` keycloak path and would 404 the
> same way if invoked from the browser in keycloak mode.

## Fix

Route the widget's reads/writes through a **server action** that uses the already-correct
`server-only` client (`lib/api/client.ts`, which targets `GATEWAY_URL` and forwards the SESSION
cookie + CSRF in keycloak mode — `client.ts:8-10,23-32`). A `server-only` data layer already exists:
`frontend/lib/api/ai-invocations.ts` (`listInvocations`, `getInvocation`). Mirror the working
`ai/reviews/actions.ts` server-action pattern (`approveGateAction`/`rejectGateAction`).

Concretely:
1. Add a server actions file (e.g. `app/(app)/org/[slug]/customers/[id]/pending-suggestions-actions.ts`
   or a shared `lib/actions/ai-invocations.ts`) exposing:
   - `listPendingInvocationsAction({ contextEntityType, contextEntityId })` → calls
     `listInvocations({ contextEntityType, contextEntityId, status: "PENDING_APPROVAL", size: 10 })`
   - `approveInvocationAction(id)` / `rejectInvocationAction(id, reason)` → call the corresponding
     `ai-invocations.ts` mutators (add `approveInvocation`/`rejectInvocation` there if absent; they
     hit `/api/assistant/invocations/{id}/approve|reject` via `api.post`).
2. Point the widget's `useSWR` fetcher at the new server action instead of `listInvocationsClient`,
   and the approve/reject handlers at the new actions (same SWR-wrapping-server-action pattern the
   frontend already prescribes — `frontend/CLAUDE.md` "wrapping server actions with SWR").
3. Leave `assistant-specialists.ts` `API_BASE=""` clients as-is unless other callers are also browser
   calls in keycloak (out of scope here; flag separately if found).

This is the minimal correct fix: an **authorized-but-routed-wrong read** becomes a real 200 (empty
page when there are no pending invocations — and the widget already renders nothing on empty, so the
customer page simply stops 404-ing).

## Scope

- Frontend only. One widget + one small server-actions module + possibly two helper functions added
  to `ai-invocations.ts`. No backend change, no migration, no capability change.
- Do NOT "fix" by seeding `AI_ASSISTANT_USE` — owners already have it; that is not the cause.

## Verification

1. `pnpm lint && pnpm build && pnpm test` green (add/adjust the widget test
   `pending-suggestions-widget.test.tsx` to mock the new server action).
2. Live (keycloak, as Nomsa): open `/org/verifain-attorneys/customers/809563f8-1feb-4043-bbd7-c9aeaf356900`.
   Network tab: the pending-suggestions request now returns **200** (a server-action POST to the Next
   origin that internally proxies to the gateway), **no 404** in console.
3. With at least one `PENDING_APPROVAL` `ai_specialist_invocation` for that customer, the widget
   renders the row and Approve/Reject work (status flips in `ai_specialist_invocations`). With none,
   the widget renders nothing and there is no 404 — the original symptom is gone.
