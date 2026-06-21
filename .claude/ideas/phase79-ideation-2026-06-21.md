# Phase 79 Ideation — Session Lifecycle & Auth Experience Hardening — 2026-06-21

**Output:** `requirements/claude-code-prompt-phase79.md` (READY for `/architecture`).
**Type:** platform hardening, not a domain phase. Foundation-quality, inherited by every fork.

## Trigger
Founder raised 4 auth complaints: session expiry/management, logout shows "whitelabel",
change passwords, consistent themes. Plus a concrete bug: **inactive session → clicking
anything leads to errors.**

## Key diagnosis (the value-add)
The 4 complaints are symptoms of one architectural seam + unfinished branding. Auth is a
**BFF pattern** (gateway holds tokens server-side, browser has opaque SESSION cookie, refresh
auto via Spring `OAuth2AuthorizedClientManager`).
- **Bug root cause:** lifetime mismatch — gateway session 8h, but Keycloak token/SSO lifetimes
  left at *defaults* (never set in `realm-export.json`). Keycloak kills the session; gateway
  thinks it's alive; silent refresh fails. Graceful 401→re-login only exists for *server-action*
  401s (`client.ts:164`); client fetches / polling / `/bff/me` leak raw errors.
- **Whitelabel:** `post.logout.redirect.uris` lands at root, unbranded; error/info Keycloak pages
  unthemed.
- **Change password:** doesn't exist in-app at all.
- **Theme:** custom Keycloakify theme EXISTS but still named `docteams` (dead brand → Kazi).

## Decisions (all lean / recommended path)
- Scope = **auth/session UX only** (no app-wide theme audit).
- Expiry UX = **silent graceful re-login** + validated return-to (no idle modal, no keep-alive).
- Change password = **deep-link to Keycloak account console** (no native form).
- Theme = **rebrand docteams→kazi + brand error/info/logout pages**.
- Lifetimes LOCKED: access **5m**, SSO idle **30m**, SSO max **10h**; gateway session aligned to 10h.

## 5 epics
1. Explicit Keycloak lifetimes + gateway alignment (root cause)
2. Single expired-session funnel + return-to allowlist
3. Branded `/signed-out` + post-logout redirect fix
4. Change-password deep-link ("Account & Security" menu item)
5. Theme rebrand + full page coverage (error/info/logout)

## Out of scope (future)
Idle-warning modal, keep-alive refresh, native password form, MFA/passkeys, app-wide dark-mode
/design-token audit, signup/registration changes.

## Notes
- Naming: Phase 78 doc called "Phase 79" the skill pack — that shipped in the `../claude-for-legal-sa`
  fork instead, NOT as an in-repo phase. This Phase 79 is unrelated platform work; number is free.
- Reproduce-before-fix gate applies to the stale-session bug (Keycloak dev stack).
