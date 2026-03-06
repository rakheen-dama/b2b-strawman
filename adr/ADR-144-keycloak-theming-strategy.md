# ADR-144: Keycloak Theming Strategy

**Status**: Proposed
**Date**: 2026-03-04
**Phase**: 36 (Keycloak + Gateway BFF Migration)

## Context

Keycloak provides hosted login, registration, and account management pages. These must visually match the DocTeams application. Keycloak supports theming via:

1. **Freemarker templates** — Traditional approach, modify `.ftl` files. Full control but verbose, no component model, hard to maintain.
2. **Keycloakify** — React-based theming tool. Write theme pages in React/TypeScript, compile to Keycloak theme JAR. Supports Tailwind CSS.
3. **Fully custom login pages** — Build login/registration in Next.js, use Keycloak only as an OIDC backend. Maximum control but requires implementing security-sensitive auth flows (PKCE, state management, CSRF).

## Decision

Use **Keycloakify** for login, registration, and account management pages. Use **Freemarker customization** for email templates.

### Rationale

- Keycloakify allows using React + Tailwind CSS — same technology as the main app
- Login pages are security-critical; delegating to Keycloak (even with custom theme) avoids implementing auth ceremony in the SPA
- Freemarker is simpler for email templates (HTML + variable substitution, no component model needed)
- Option 3 (fully custom) was rejected because it pushes security-sensitive logic into the SPA, defeating the purpose of the BFF pattern

### Theme Scope

| Page Type | Tool | Priority |
|---|---|---|
| Login (password + social) | Keycloakify | High — first user touchpoint |
| Registration | Keycloakify | High — onboarding experience |
| Password reset | Keycloakify | Medium |
| Invitation acceptance | Keycloakify | Medium |
| Account management | Keycloakify | Low — rarely accessed |
| Invitation email | Freemarker | High — drives adoption |
| Verification email | Freemarker | Medium |
| Password reset email | Freemarker | Medium |

## Consequences

- **Positive**: Consistent visual identity across app and auth pages
- **Positive**: React + Tailwind familiar to team — no new skills
- **Positive**: Security ceremony stays in Keycloak (PKCE, session, CSRF handled by Keycloak)
- **Negative**: Keycloakify adds a build step (React → Keycloak theme JAR)
- **Negative**: Theme must be updated when Keycloak upgrades change page structure
- **Negative**: Limited to Keycloak's page structure — can't add arbitrary UI elements to login flow
