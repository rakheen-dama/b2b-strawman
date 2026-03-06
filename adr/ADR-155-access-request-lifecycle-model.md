# ADR-155: Access Request Lifecycle Model

**Status**: Proposed
**Date**: 2026-03-06
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

Admin-approved provisioning ([ADR-154](ADR-154-admin-approved-provisioning-flow.md)) introduces a new entity: the access request. This entity must track the full lifecycle from submission through provisioning to completion, including failure states and retry capability.

Key design questions:
1. Where does the entity live (public schema vs. tenant schema)?
2. What states does it go through?
3. How does it handle concurrent approval attempts and mid-provisioning failures?
4. How does it link to the Keycloak organization and tenant schema created during approval?

## Options Considered

1. **Stateless model** — Store only PENDING/APPROVED/REJECTED, treat provisioning as atomic
   - Pros: Simple, fewer states
   - Cons: No retry capability if provisioning fails mid-way, no visibility into which step failed, double-click risk

2. **State machine with provisioning tracking (chosen)** — PENDING → PROVISIONING → APPROVED/FAILED/REJECTED, with columns tracking which provisioning steps completed
   - Pros: Admin can see exactly where provisioning failed, retry resumes from last successful step, PROVISIONING state prevents concurrent attempts
   - Cons: More complex entity, more states to manage

3. **Separate provisioning log table** — Access request has simple states, a separate `provisioning_log` tracks step-by-step progress
   - Pros: Clean separation of concerns
   - Cons: Over-engineered for the expected volume (tens of requests per week), joins required for admin dashboard

## Decision

Use a **state machine with provisioning tracking** directly on the `AccessRequest` entity. The `PROVISIONING` state acts as a lock against concurrent approval attempts. The `keycloak_org_id` and `tenant_schema` columns track which steps completed, enabling the retry endpoint to resume from the last successful step.

## Rationale

The entity lives in the **public schema** because it exists before any tenant does — it represents a request to create a tenant. This is consistent with other cross-tenant entities (`organizations`, `org_schema_mapping`, `subscriptions`).

The five-state model (PENDING, PROVISIONING, APPROVED, REJECTED, FAILED) balances simplicity with operational needs:
- `PROVISIONING` prevents the double-click problem (admin clicks approve twice → second attempt sees non-PENDING state and rejects)
- `FAILED` with retained `keycloak_org_id`/`tenant_schema` enables intelligent retry (skip already-completed steps)
- `REJECTED` with optional `rejection_reason` provides audit trail

A separate provisioning log table was rejected as over-engineering. At B2B scale (low volume), the admin dashboard query is simple: `SELECT * FROM access_requests WHERE status = 'PENDING' ORDER BY created_at`.

## Consequences

- **Positive**: Single table captures the full lifecycle — no joins for the admin dashboard
- **Positive**: Retry is intelligent (resumes from failed step, not from scratch)
- **Positive**: PROVISIONING state prevents concurrent approval race conditions
- **Positive**: Public schema placement is consistent with existing cross-tenant entities
- **Negative**: Entity has more columns than a minimal design (provisioning tracking fields are null until approval)
- **Neutral**: Migration is a single table in the global migration sequence (V55)
