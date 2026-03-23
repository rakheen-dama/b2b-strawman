# ADR-207: E2E Test Data Strategy — UI-Driven, No DB Seeding

**Status**: Proposed
**Date**: 2026-03-22
**Phase**: 54 (Keycloak Dev E2E Test Suite)

## Context

The mock IDP E2E stack uses a boot-seed container that calls internal API endpoints to create an org, provision a schema, and sync members. This bypasses the product's user-facing flows — the access request form, OTP verification, platform admin approval, Keycloak org creation, and invitation email flow are never exercised.

The Phase 54 test suite needs a data strategy: how are orgs, users, and test data created before assertions run?

## Options Considered

### Option 1: API/DB Seeding (Same as Mock IDP Stack)
- **Pros:** Fast setup, deterministic state, isolated from UI changes, reusable seed scripts
- **Cons:** Bypasses provisioning pipeline, doesn't validate pack seeding, doesn't test onboarding flow, seeder divergence from product behavior

### Option 2: UI-Driven Data Creation (Selected)
- **Pros:** Full-stack validation (form → server action → API → DB → UI), tests the provisioning pipeline, catches pack seeding bugs, exercises email flows
- **Cons:** Slower test setup, fragile (depends on UI selectors), serial execution required, clean state needs full volume wipe

### Option 3: Hybrid — API Seed for Baseline, UI for Test-Specific Data
- **Pros:** Faster baseline setup, UI tests focus on specific flows, compromise between speed and coverage
- **Cons:** Two data creation paths, risk of divergence, unclear boundary for what's seeded vs. UI-created, doesn't validate provisioning pipeline

## Decision

**Option 2 — Tests create ALL data through the product UI.**

## Rationale

1. **Full-stack validation**: A test that creates a customer through the UI validates the form, the server action, the API call, the backend service, the entity persistence, and the response rendering. A test that inserts directly into the database validates none of these layers.
2. **Provisioning pipeline coverage**: The onboarding flow exercises the complete pipeline — access request, OTP, approval, Keycloak org creation, schema provisioning, Flyway migrations, pack seeding, JIT sync. This pipeline has many moving parts that can only be validated by driving it end-to-end.
3. **Pack seeding accuracy**: If a pack file has a malformed field or a seeder has a bug, only a test that goes through the seeder will catch it. Direct DB assertions prove the schema is correct but not that the seeder works.
4. **Single source of truth**: When tests create data through the UI, they document the product's actual behavior. If the UI changes, the tests break — which is the correct signal.

## Consequences

- **Positive:** Tests validate the entire provisioning pipeline from public form to dashboard
- **Positive:** Pack seeding bugs are caught (malformed JSON, missing seeder logic, broken frontend rendering)
- **Positive:** Tests serve as living documentation of the onboarding flow
- **Negative:** Tests are slower (full UI flow vs. API call for setup)
- **Negative:** Tests are more fragile (depend on UI selectors, form validation, Keycloak page stability)
- **Negative:** Tests cannot run in parallel (shared Keycloak state, serial org creation)
- **Negative:** Clean state requires full volume wipe + rebuild (no incremental reset)
- **Mitigations:** Serial execution (`workers: 1`), `data-testid` attributes for selector stability, `--clean` flag on startup script
