# ADR-010: Billing Integration Approach

**Status**: Accepted

**Context**: Phase 2 introduces subscription-based billing to support tiered pricing (Starter and Pro plans). The application needs a billing system that manages plan definitions, subscription lifecycle (trial, active, canceled), payment processing, and feature entitlements. The existing stack already uses Clerk for authentication and organization management, and Clerk offers a managed billing product (Clerk Billing) built on Stripe.

**Options Considered**:

1. **Direct Stripe integration** — Use Stripe SDK directly for plans, checkout, webhooks, and subscription management.
   - Pros: Full control over billing UX and logic; mature ecosystem; no intermediary markup.
   - Cons: Requires building custom checkout flows, subscription management UI, webhook handlers for payment events, and plan-entitlement mapping. Significant frontend and backend work. Must maintain Stripe-to-Clerk user/org mapping manually.

2. **Clerk Billing (managed Stripe)** — Use Clerk's billing layer which wraps Stripe and integrates with Clerk's auth/org infrastructure.
   - Pros: Pre-built React components (`<PricingTable>`, checkout flows); plan/feature definitions in Clerk Dashboard; session-aware billing (no user/org mapping needed); `has({ plan: 'pro' })` for frontend gating; subscription webhooks integrated with existing Clerk webhook infrastructure; member limit enforcement built-in.
   - Cons: Beta status (experimental APIs may change); 0.7% per-transaction markup on top of Stripe fees; less control over billing UX customization; dependency on Clerk's billing roadmap.

3. **Third-party billing platform (Paddle, Lemon Squeezy)** — Use an alternative to Stripe.
   - Pros: Merchant of Record model simplifies tax compliance; potentially simpler integration.
   - Cons: No native integration with Clerk; requires custom user/org mapping; smaller ecosystem; less B2B-oriented.

**Decision**: Use Clerk Billing as the managed billing platform.

**Rationale**: Clerk Billing eliminates the need for custom billing UI, Stripe webhook handling, and subscription state management by providing these as pre-built, session-aware components. Since the application already depends on Clerk for auth and organization management, the billing integration is additive — it reuses the same session/JWT infrastructure, organization model, and webhook delivery mechanism. The 0.7% markup is acceptable given the engineering time saved (estimated 2-3 weeks of custom Stripe integration). The beta status is mitigated by:
- The underlying Stripe infrastructure being production-grade.
- Pinning SDK versions to avoid unexpected breaking changes.
- Using webhooks to sync plan state to the local DB (reducing runtime dependency on Clerk's billing API).

Plans and features are defined in the Clerk Dashboard, not in application code. This means plan changes (pricing, feature inclusion) can be made without code deployments.

**Consequences**:
- Plans (`starter`, `pro`) and features (`dedicated_schema`, `max_members_10`) defined in Clerk Dashboard.
- Subscription state synced to local DB via webhooks for backend access (see ADR-013).
- Frontend uses `@clerk/nextjs` billing components (currently under `experimental` imports — will stabilize).
- No direct Stripe SDK dependency in the application.
- Member limits configured per-plan in Clerk Dashboard and enforced by Clerk (backed by server-side validation).
- New environment variable: none required (Clerk Billing uses existing `CLERK_SECRET_KEY`).
- Future: if Clerk Billing remains in beta beyond 12 months or introduces unacceptable breaking changes, the fallback path is direct Stripe integration using the local DB's plan state as the migration bridge.
