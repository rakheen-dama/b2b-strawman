# ADR-013: Plan State Propagation

**Status**: Accepted

**Context**: The backend needs to know the current organization's plan tier (Starter vs Pro) on every request to select the correct tenancy model and enforce plan limits. The source of truth for subscription state is Clerk Billing. The question is how the backend accesses this information efficiently and reliably.

**Options Considered**:

1. **JWT claims only** — Read plan/feature information from the Clerk JWT token on each request.
   - Pros: Zero additional latency (data is in the token); no extra API calls or database queries.
   - Cons: Clerk JWT v2 does not automatically include plan/subscription claims in the `o` object — the frontend `has()` function works by checking against the Clerk session (client-side), not the JWT itself. Even if custom JWT templates are used, session tokens have a TTL (typically 1-5 minutes) and may not reflect a subscription change immediately. The backend cannot call `has()` — it only has the JWT. Session-tied claims (`pla`, `fea`) cannot be included in custom JWT templates.

2. **Clerk API call per request** — Call the Clerk Backend API to fetch the organization's subscription state on every incoming request.
   - Pros: Always returns the current subscription state (real-time consistency).
   - Cons: Adds 50-200ms latency to every request; creates a hard dependency on Clerk API availability (if Clerk is down, the application cannot serve requests); rate limiting concerns at scale.

3. **Local DB cache via webhooks** — Sync subscription state to the local database via Clerk Billing webhooks (`subscription.created`, `subscription.updated`). Read the cached tier from the `Organization` entity (already loaded by `TenantFilter`).
   - Pros: Zero additional latency (tier is part of the schema cache, already queried per request); no runtime dependency on Clerk API; resilient to Clerk outages; eventual consistency window is small (webhook delivery typically < 5 seconds).
   - Cons: Eventually consistent (not real-time); requires webhook handler for subscription events; brief window after subscription change where cached tier may be stale.

**Decision**: Local DB cache via webhooks (Option 3).

**Rationale**: The backend already caches the org-to-schema mapping in a Caffeine cache with a 1-hour TTL (`TenantFilter.schemaCache`). Adding the tier to this cached value adds zero incremental latency. The `Organization` entity gains `tier` and `planSlug` columns, and the cache value type changes from `String` (schema name) to `TenantInfo(String schemaName, Tier tier)`.

Webhook delivery latency (typically < 5 seconds) is acceptable for plan state propagation. A user who just subscribed to Pro will see the plan reflected in the frontend immediately (via Clerk's client-side `useSubscription()`) and in the backend within seconds (via webhook → DB → cache eviction). The critical path — provisioning a new dedicated schema on upgrade — is triggered by the webhook itself, so there is no consistency gap for the most important operation.

The JWT-only approach is not viable because Clerk Billing's plan/feature claims are session-bound and not available in custom JWT templates. The API-per-request approach adds unacceptable latency and fragility.

**Data Flow**:
```
Clerk Billing → subscription webhook → Next.js webhook handler
  → POST /internal/orgs/plan-sync → Spring Boot updates Organization.tier
  → TenantFilter cache eviction → next request picks up new tier
```

**Consequences**:
- `Organization` entity gains `tier` (enum: `STARTER`, `PRO`) and `planSlug` (Clerk plan slug) columns.
- New global migration `V4__add_org_tier.sql` adds these columns.
- New webhook handler for `subscription.created` and `subscription.updated` events.
- New internal endpoint `POST /internal/orgs/plan-sync` in Spring Boot.
- `TenantFilter` cache value type changes to `TenantInfo` record.
- Cache eviction on plan change (or rely on natural TTL expiry — 1 hour max staleness).
- Frontend reads plan state directly from Clerk session via `has()` / `useSubscription()` — no backend round-trip needed for UI gating.
- If Clerk webhook delivery fails, plan state remains stale until webhook is retried (Clerk/Svix retries automatically).
