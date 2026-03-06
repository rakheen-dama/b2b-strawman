# ADR-143: Tenant Provisioning Without Clerk Webhooks

**Status**: Proposed
**Date**: 2026-03-04
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

Currently, Clerk fires webhooks (`organization.created`, `organizationMembership.created`) which trigger tenant schema provisioning and member sync via `/internal/orgs/provision` and `/internal/members/sync`. Keycloak does not have built-in outgoing webhooks but offers:

1. **Event Listener SPI** — Custom Java code that runs inside the Keycloak JVM on lifecycle events
2. **Admin Events API** — Polling-based event consumption
3. **Just-in-Time (JIT) provisioning** — Provision on first authenticated request

## Decision

Use **JIT provisioning** for the proof-of-concept (Phase A), then implement the **Event Listener SPI** for production readiness (Phase E).

### JIT Provisioning (Phase A-D)

The `TenantFilter` is modified: when it receives a JWT with an org ID that has no `org_schema_mapping` entry, it synchronously provisions the tenant schema before continuing the request.

- **Pro**: Zero Keycloak customization needed
- **Con**: 2-5 second latency on first request for a new org
- **Con**: Member sync must also happen JIT (first request from each member)

### Event Listener SPI (Phase E)

A custom Keycloak Event Listener JAR that listens for organization and membership Admin Events and calls the backend's `/internal/*` endpoints. This provides the same push-based provisioning as Clerk webhooks.

- **Pro**: Instant provisioning, no first-request latency
- **Pro**: Mirrors the existing webhook-driven architecture
- **Con**: Requires compiling and deploying a JAR into Keycloak

## Consequences

- **Positive**: JIT approach gets the system working with zero Keycloak SPI development
- **Positive**: Event Listener SPI reuses existing `/internal/*` endpoints unchanged
- **Positive**: Both approaches are compatible — JIT serves as a fallback if Event Listener fails
- **Negative**: JIT adds latency to first request per org (acceptable for B2B)
- **Negative**: Event Listener SPI is a separate build artifact to maintain
