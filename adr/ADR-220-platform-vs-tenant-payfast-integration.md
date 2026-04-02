# ADR-220: Platform vs Tenant PayFast Integration

**Status**: Proposed  
**Date**: 2026-04-02  
**Phase**: 57

## Context

HeyKazi has two distinct payment domains:

1. **Tenant invoice payments** (Phase 25, ADR-098): Tenants collect payments from their customers via their own PayFast merchant accounts. Credentials are stored in the tenant-scoped `SecretStore` (via `IntegrationRegistry`), and the `PayFastPaymentGateway` adapter resolves credentials per-tenant using `@IntegrationAdapter(domain = PAYMENT, slug = "payfast")`.

2. **Platform subscription billing** (Phase 57): HeyKazi collects subscription payments from tenants via a platform-owned PayFast merchant account. Credentials come from environment variables or AWS Secrets Manager — not from the tenant-scoped `SecretStore`.

These two domains have different credential sources (tenant SecretStore vs platform config), different webhook endpoints (`/api/webhooks/payment/payfast` vs `/api/webhooks/subscription`), different payment types (one-time invoice payments vs recurring subscriptions), and different entities (`PaymentEvent` on tenant invoices vs `SubscriptionPayment` on global subscriptions).

The question is how to structure the platform billing code relative to the existing tenant payment integration.

## Options Considered

### Option 1: Reuse IntegrationRegistry with "Platform" Pseudo-Tenant

Register the platform PayFast account as an `OrgIntegration` with a special pseudo-tenant ID (e.g., `PLATFORM`). The existing `PayFastPaymentGateway` resolves credentials via `SecretStore` — store platform credentials there with a synthetic org scope.

- **Pros:** Reuses existing code paths. Single `PayFastPaymentGateway` class handles both domains. Credential management is unified.
- **Cons:** Violates the purpose of `IntegrationRegistry` — it was designed for tenant-owned BYOAK integrations, not platform billing. The pseudo-tenant is a lie: there is no real tenant schema for "PLATFORM," and the `SecretStore` encrypts per-org. Subscription billing requires subscription-specific fields (`subscription_type`, `recurring_amount`, `frequency`, `cycles`) that the existing `CheckoutRequest` does not support. The ITN handler for subscriptions has different semantics (token storage, recurring payment tracking) than invoice payments. Mixing both domains in one class violates SRP and makes testing harder.

### Option 2: Separate Platform Billing Service with Direct Config (Selected)

Create a new `PlatformPayFastService` in the `billing/payfast/` sub-package. It reads platform credentials from `@ConfigurationProperties` (application config), builds subscription-specific checkout forms, and handles subscription ITN webhooks. Completely independent of the tenant `PayFastPaymentGateway`.

- **Pros:** Clean separation of concerns — platform billing and tenant invoice payments are different domains with different credential sources, different webhook handling, and different entities. The `billing/payfast/` package owns its own configuration, form builder, and ITN handler. No risk of tenant payment code changes breaking platform billing or vice versa. Config via `@ConfigurationProperties` is the standard Spring Boot pattern for platform-level settings (vs per-tenant SecretStore lookups).
- **Cons:** Some code duplication — MD5 signature generation, IP validation, and form encoding are shared concerns. Two separate ITN endpoints to maintain.

### Option 3: Shared PayFast Library Extracted from Both

Extract common PayFast utilities (signature generation, IP validation, form encoding) into a `billing/payfast/core/` package. Both `PayFastPaymentGateway` (tenant) and `PlatformPayFastService` (platform) depend on the shared library.

- **Pros:** Eliminates duplication. Signature and IP validation logic maintained in one place. Both consumers get bug fixes automatically.
- **Cons:** Premature abstraction — the tenant `PayFastPaymentGateway` uses outbound signature generation (URL-encoded values) while the platform ITN handler uses inbound verification signature (raw decoded values). The "shared" surface is ~30 lines of utility code. Coupling the two domains through a shared library means changes to one can unexpectedly break the other. The existing `PayFastPaymentGateway` has its own IP validation and signature methods that work correctly — extracting them risks regressions.

## Decision

**Option 2 — Separate platform billing service with direct config.**

## Rationale

1. **Different domains, different code.** Platform subscription billing and tenant invoice payments share a PSP (PayFast) but nothing else. Different credential source (env vars vs SecretStore), different payment type (recurring vs one-time), different entities (`SubscriptionPayment` in `public` schema vs `PaymentEvent` in tenant schema), different webhook semantics (subscription token tracking vs invoice status updates). Forcing them into the same code path would require extensive branching that makes both harder to understand.

2. **BYOAK pattern integrity.** The `IntegrationRegistry` / `SecretStore` pattern (Phase 21, ADR-098) was designed for tenant-owned external accounts. Using it for platform billing would muddy the concept and require a fake "platform tenant" — a code smell that propagates confusion through every layer that touches integrations.

3. **Acceptable duplication.** The duplicated surface is small: MD5 signature generation (~15 lines), IP range validation (~10 lines), and URL-encoded form building (~10 lines). This is well below the threshold where extraction is justified (per the backend CLAUDE.md guidance: "avoid premature abstractions — do not create provider/adapter patterns until there are two concrete implementations"). Here there *are* two implementations, but they serve fundamentally different purposes and evolve independently.

4. **Independent evolution.** Platform billing will likely need subscription-specific features (trial management, grace period logic, resubscribe flows) that have no analog in tenant invoice payments. Keeping the code separate means these can be added without affecting the BYOAK path.

## Consequences

- **Positive:** Clean package boundary (`billing/payfast/` for platform, `integration/payment/` for tenant BYOAK). Each domain can evolve independently. No risk of one breaking the other. Platform credentials managed via standard `@ConfigurationProperties` — familiar Spring Boot pattern.
- **Negative:** ~35 lines of duplicated utility code (signature, IP check). Two PayFast ITN endpoints (different paths, different handlers). Developers must understand that PayFast appears in two packages for different reasons.
- **Mitigations:** Document the separation rationale in package-level Javadoc. If a third PayFast integration point appears in the future, revisit extraction at that time.
