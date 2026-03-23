# ADR-208: Pack Verification Approach — UI Assertions, Not API

**Status**: Proposed
**Date**: 2026-03-22
**Phase**: 54 (Keycloak Dev E2E Test Suite)

## Context

The accounting-ZA vertical profile seeds 8 pack types (60+ items total) during tenant provisioning: 3 field packs, 1 compliance pack, 1 template pack, 1 clause pack, 1 automation pack, and 1 request pack. After provisioning, the test suite must verify that all packs were correctly imported.

There are three approaches to verify pack correctness, each with different coverage and cost characteristics.

## Options Considered

### Option 1: Database Assertions (Direct SQL Queries)
- **Pros:** Fastest execution, most precise (exact row counts, field values), independent of UI, tests seeder + migration
- **Cons:** Doesn't validate API serialization, doesn't validate frontend rendering, requires DB connection from test code, bypasses permission checks, a pack item in the DB but invisible in the UI is still broken

### Option 2: API Assertions (REST Endpoint Calls)
- **Pros:** Faster than UI, validates API serialization, tests permission enforcement, programming-friendly assertions
- **Cons:** Doesn't validate frontend rendering, a pack item returned by the API but not shown in the UI is still broken, requires auth token management in test code

### Option 3: UI Assertions (Navigate Settings Pages, Assert Visible Content) (Selected)
- **Pros:** Tests what users actually see, validates full stack (seeder → DB → API → frontend), catches rendering bugs and permission issues, regression detection on frontend refactors
- **Cons:** Slowest execution, depends on page structure and selectors, debugging failures requires UI inspection, count assertions fragile if universal packs also shown

## Decision

**Option 3 — Verify pack seeding through UI assertions exclusively.**

## Rationale

1. **Tests what users see**: A pack item that exists in the database but doesn't render in the UI is broken from the user's perspective. Only UI assertions catch this class of bug.
2. **Full-stack coverage**: A single UI assertion implicitly validates: pack JSON file → seeder logic → database persistence → API serialization → frontend data fetching → component rendering. Each layer is tested without writing separate assertions for each.
3. **Regression detection**: If a future frontend refactor breaks the custom fields page or changes how templates are listed, pack verification tests catch it immediately — they're not silently green while the user experience is degraded.
4. **Alignment with ADR-207**: Since all test data is created through the UI (no DB seeding), verifying pack data through the UI maintains consistency — the entire test suite operates at the same abstraction level.

## Consequences

- **Positive:** Pack verification tests catch bugs at any layer of the stack
- **Positive:** Tests serve as living documentation of what the accounting-za profile surfaces in the UI
- **Positive:** Frontend rendering bugs and data mapping errors are detected
- **Negative:** UI assertions are slower than API or DB assertions
- **Negative:** Assertions depend on page structure — mitigated by `data-testid` attributes
- **Negative:** Debugging failures requires inspecting screenshots/traces, not just API responses
- **Mitigations:** Assert on specific item names (not just counts), use `data-testid` attributes on pack-related UI elements, screenshots on failure capture exact UI state
