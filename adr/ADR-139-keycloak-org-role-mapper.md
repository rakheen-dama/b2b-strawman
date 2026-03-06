# ADR-139: Organization Role Mapper Strategy

**Status**: Proposed
**Date**: 2026-03-04
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

Keycloak's built-in `organization` token mapper includes organization name and optional ID in the JWT, but does **not** include the user's role within the organization. The backend requires the role to map to Spring Security authorities (`ROLE_ORG_OWNER`, `ROLE_ORG_ADMIN`, `ROLE_ORG_MEMBER`).

Two approaches exist to add roles to the JWT:
1. **Script Mapper** — JavaScript executed inside Keycloak at token issuance time. Quick to implement, no compilation needed, but Script Mappers have been disabled by default in some Keycloak versions (require `--features=scripts` flag).
2. **Custom Java Protocol Mapper SPI** — A compiled JAR deployed to Keycloak's `providers/` directory. More robust, survives Keycloak upgrades, but requires a build step.

## Decision

Start with **Script Mapper** for Phase A (proof-of-concept) with `--features=scripts` enabled in dev. Implement the **Java SPI mapper** before production deployment if Script Mappers prove unreliable or are deprecated in Keycloak 26.5+.

The mapper adds a `roles` array inside each organization entry in the `organization` claim.

## Consequences

- **Positive**: Script Mapper is zero-compilation, fast to iterate during proof-of-concept
- **Positive**: Java SPI fallback path is well-documented and widely used
- **Negative**: Must enable `--features=scripts` in Keycloak (disabled by default in production mode)
- **Negative**: If Script Mappers are removed in a future Keycloak version, must migrate to Java SPI
