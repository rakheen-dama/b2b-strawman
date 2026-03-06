# ADR-154: Admin-Approved Tenant Provisioning Flow

**Status**: Proposed
**Date**: 2026-03-06
**Supersedes**: [ADR-143](ADR-143-tenant-provisioning-strategy.md) (JIT provisioning becomes safety net, not primary path)
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

The current tenant provisioning flow is reactive and fully automated: a user signs up via Clerk, creates an organization, and Clerk fires a webhook that triggers schema creation. This "self-registration" model has two concerns for B2B SaaS:

1. **No gatekeeping** — anyone with an email address can create an org and consume platform resources (schema, storage, seeded data packs). In a B2B context where tenants represent paying companies, uncontrolled tenant creation is undesirable.
2. **Clerk dependency** — the webhook-driven flow is tightly coupled to Clerk's event system. With the migration to Keycloak (Phase 36), the provisioning trigger needs to change regardless.

ADR-143 proposed JIT provisioning (provision on first authenticated request) as the Keycloak replacement. While functional, JIT has drawbacks: 2-5 second latency on first request, provisioning triggered by any authenticated user (no approval gate), and no visibility into pending/failed provisioning.

The product now needs an explicit admin-approval step before tenant creation.

## Options Considered

1. **JIT provisioning (ADR-143)** — Provision on first authenticated request when no schema mapping exists
   - Pros: Zero Keycloak customization, no admin bottleneck
   - Cons: No approval gate, 2-5s first-request latency, any user can trigger provisioning, no visibility into pending requests

2. **Keycloak Event Listener SPI** — Custom JAR inside Keycloak that fires on org creation, calling backend `/internal/orgs/provision`
   - Pros: Push-based (mirrors Clerk webhooks), instant provisioning
   - Cons: Requires maintaining a Keycloak SPI JAR, still no approval gate (unless org creation itself is gated in Keycloak), adds operational complexity

3. **Admin-approved provisioning (chosen)** — Public access request form → admin approval → synchronous orchestration (KC org + schema + invite)
   - Pros: Explicit approval gate, full visibility into request lifecycle, synchronous orchestration eliminates race conditions, admin can retry failures
   - Cons: Manual bottleneck (every tenant needs admin approval), additional UI to build (request form + admin dashboard)

## Decision

Use **admin-approved provisioning** as the primary tenant creation path. JIT provisioning (ADR-143) is retained as a safety net in `TenantFilter` but should not be the normal trigger.

The flow:
1. Visitor submits access request via public form (no auth required)
2. Product admin reviews pending requests in an admin dashboard
3. On approval, the backend synchronously orchestrates: Keycloak org creation → tenant schema provisioning → owner invitation
4. Invitee accepts, logs in, member is JIT-synced with Owner role

## Rationale

Admin-approved provisioning aligns with B2B SaaS norms where tenant onboarding is a controlled process. The synchronous orchestration (all steps in one service call) is simpler to reason about than async webhook choreography and eliminates the "user arrives before schema exists" race condition.

The manual approval bottleneck is acceptable because:
- B2B SaaS typically has low tenant creation volume (tens per week, not thousands)
- Each tenant represents a business relationship, not a casual sign-up
- Auto-approval rules (e.g., email domain allowlists) can be added later without architectural changes

Retaining JIT provisioning as a fallback ensures resilience: if an admin provisions a Keycloak org manually (outside the approval flow), the system still works.

## Consequences

- **Positive**: Every tenant is explicitly approved — no runaway resource consumption
- **Positive**: Admin has full visibility into the provisioning pipeline (PENDING → PROVISIONING → APPROVED/FAILED)
- **Positive**: Synchronous orchestration eliminates webhook timing issues and provides immediate failure feedback
- **Positive**: Retry mechanism for failed provisioning (admin clicks retry, system resumes from last successful step)
- **Negative**: Manual bottleneck — admin must approve each request. Mitigated by future auto-approval rules
- **Negative**: Additional UI to build (public form + admin dashboard). Estimated at 2 epics
- **Neutral**: JIT provisioning remains in `TenantFilter` as safety net — no code removed, just deprioritized
