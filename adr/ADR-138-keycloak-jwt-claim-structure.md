# ADR-138: Keycloak JWT Claim Structure

**Status**: Proposed
**Date**: 2026-03-04
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

Clerk v2 JWTs use a nested `"o"` object for organization claims: `{ "o": { "id": "org_xxx", "rol": "owner", "slg": "my-org" } }`. Keycloak 26.5 uses a different structure with the built-in `organization` client scope: `{ "organization": { "org-slug": { "id": "uuid" } } }`. The backend's `TenantFilter`, `MemberFilter`, and `JwtAuthenticationConverter` all depend on extracting org ID, slug, and role from the JWT.

We need a JWT claim structure for Keycloak that contains all the information the backend needs, and a backend strategy that supports both Clerk and Keycloak JWTs.

## Decision

Use Keycloak's built-in `organization` client scope with the organization ID toggle enabled, plus a custom Script Mapper to add organization roles. The JWT structure will be:

```json
{
  "organization": {
    "org-slug": {
      "id": "uuid",
      "roles": ["owner"]
    }
  }
}
```

Enforce single organization per user at the Keycloak level (organization membership policy). This means the `organization` claim always contains exactly one entry, making extraction deterministic.

## Consequences

- **Positive**: Standard Keycloak claim format — no custom claim restructuring to match Clerk's format
- **Positive**: Single org per user eliminates ambiguity in claim parsing
- **Positive**: Built-in mapper handles org name + ID; only roles need a custom mapper
- **Negative**: Different structure from Clerk requires a strategy pattern in the backend (see ADR-142)
- **Negative**: Custom Script Mapper for roles adds a moving part that may need adjustment across Keycloak versions
