# ADR-206: Test Stack Unification — Keycloak Replaces Mock IDP

**Status**: Proposed
**Date**: 2026-03-22
**Phase**: 54 (Keycloak Dev E2E Test Suite)

## Context

Phase 20 introduced a mock IDP E2E stack (`docker-compose.e2e.yml`) to bypass Clerk authentication for Playwright tests. With Keycloak now the production auth provider, the mock IDP is a second auth implementation that tests a path no production user follows. The mock IDP validates mock tokens, uses cookie-based auth, and skips OAuth2 PKCE, Gateway BFF sessions, and email-based flows. Bugs in the real auth path are invisible to mock IDP tests.

The team now has full control over the IDP (Keycloak) and can create users, orgs, and configure auth flows programmatically — the original limitation that justified the mock IDP no longer exists.

## Options Considered

### Option 1: Keep Mock IDP as Primary, Add Keycloak Tests Separately
- **Pros:** No migration effort, existing tests remain stable, two independent test suites
- **Cons:** Double maintenance, mock IDP tests don't validate real auth, divergent test infrastructure, team confusion about which stack to target

### Option 2: Keycloak Dev Stack as Primary, Deprecate Mock IDP (Selected)
- **Pros:** Tests exercise real auth path, single authoritative test stack, validates email flows (OTP, invites), tests what production runs
- **Cons:** Tests are slower (~2-3x per login), Keycloak page selectors may be fragile, existing tests need migration

### Option 3: Delete Mock IDP Entirely, Migrate All Tests Immediately
- **Pros:** Clean break, no ambiguity about which stack to use
- **Cons:** High migration risk, all 50+ test files need immediate changes, no fallback if Keycloak stack breaks

## Decision

**Option 2 — Keycloak dev stack as primary, deprecate mock IDP.**

## Rationale

1. The mock IDP was a tactical workaround for Clerk's uncontrollable CAPTCHA. With Keycloak, the reason for the mock IDP no longer exists.
2. Tests that exercise the real auth path (OAuth2 PKCE → Gateway BFF session → JWT) catch bugs that mock auth tests cannot — token format differences, protocol mapper issues, session expiry, CORS configuration.
3. Email-based flows (OTP verification, Keycloak invitation emails) are core to the product and cannot be tested without a real IDP + Mailpit.
4. The mock IDP stack is retained (not deleted) as a lightweight fallback for developers who want fast smoke tests without starting the full Keycloak stack.

## Consequences

- **Positive:** Tests validate the actual production auth pipeline end-to-end
- **Positive:** Email-based flows (OTP, invitations, registration) become testable for the first time
- **Positive:** Single authoritative test stack reduces maintenance and confusion
- **Negative:** Test execution is slower due to Keycloak page interactions (~2-3x per login vs. programmatic token injection)
- **Negative:** Two test stacks coexist during migration period — CLAUDE.md and README must clarify which is primary
- **Neutral:** Existing mock IDP tests continue to work but are not the target for new test development
