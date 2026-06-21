# ADR-311: Change-Password Approach — kc_action=UPDATE_PASSWORD vs Account-Console Deep-Link vs Native Form

**Status**: Accepted

**Context**:

Kazi has **no** in-app way to change a password. The user menu (`frontend/components/auth/user-menu-bff.tsx:104-161`) offers only "Sign out"; the portal profile (`portal/app/(authenticated)/profile/page.tsx`) is read-only. A user who wants to change their password today must use Keycloak's forgot-password email flow — clumsy for a deliberate, authenticated change. Keycloak owns credentials and password policy in the BFF model; the frontend never talks to Keycloak directly (only via the gateway's `/oauth2/*`, `/logout`, `/bff/*`), and password policy lives at the IdP. Phase 79 must add a self-service entry point without forking the auth model or duplicating password policy in the app.

This ADR decides how the change-password experience is delivered.

**Options Considered**:

1. **`kc_action=UPDATE_PASSWORD` initiated required-action (CHOSEN)** — Add an "Account & Security" item to the user menu that initiates Keycloak's `UPDATE_PASSWORD` action via the authorization endpoint (`${KEYCLOAK_BASE}/realms/{realm}/protocol/openid-connect/auth?...&kc_action=UPDATE_PASSWORD`). This renders Keycloak's `login-update-password` page **under the login theme — which is already Kazi-branded** (the Keycloakify JAR) — then returns the user to the app. The app initiates it through the gateway's OAuth client (the gateway adds `kc_action` to the authorization request).
   - Pros: Renders under the **already-branded login theme**, so the user stays on a Kazi surface end-to-end; zero password-policy duplication (Keycloak owns strength, history, breach checks); reuses the user's existing KC SSO session (no re-auth in the happy path); no new credential-handling code in Kazi (keeps the BFF "no client-side token/credential handling" invariant); rides the existing gateway OAuth client rather than a separate account-console URL surface.
   - Cons: `kc_action` is an on-demand trigger of the required-action page (not the standing account-console UX, so sessions/MFA aren't surfaced alongside as the console would); the exact gateway/authorization-endpoint wiring (gateway-added param vs a direct authorization-endpoint link) needs to be settled at `/breakdown`.

2. **Account-console deep-link** — Add an "Account & Security" item to the user menu that opens the Keycloak account console security page (`${KEYCLOAK_BASE}/realms/{realm}/account/#/security/signing-in`), with realm + KC base resolved from existing config (a `NEXT_PUBLIC_ACCOUNT_CONSOLE_URL` env var or a `/bff/account-url` gateway helper that already knows `KEYCLOAK_ISSUER`). (Not chosen.)
   - Pros: Zero password-policy duplication (Keycloak owns strength, history, breach checks); reuses the user's existing KC SSO session (no re-auth in the happy path); no new credential-handling code in Kazi; the console also surfaces sessions/MFA for free if later enabled.
   - Cons: The account console runs `accountThemeImplementation: none` (`vite.config.ts:12`) — it is a separate, **unthemed** Keycloak SPA, so it renders **stock/unbranded**, contradicting the phase's anti-whitelabel goal; a context switch to the account console (a different page than the app shell); requires resolving the account-console URL without hardcoding a host; the console UX is Keycloak's, not Kazi's. As this is now only the fallback, note the exact route fragment (`signing-in` vs `signingin`) must be verified against the running Keycloak 26.5.0 account console — the requirements file used `signingin` (no dash).

3. **Native in-app password form** — Build a Kazi form that collects old/new password and calls Keycloak's admin/account API via the gateway.
   - Pros: Stays entirely within the app shell (no context switch); full design control.
   - Cons: Reintroduces credential handling into the app (against the BFF invariant — the browser/app should never touch credentials beyond the KC login page); duplicates/echoes Keycloak password policy (strength rules, history) which then drifts from the IdP's actual policy; more attack surface and more to test; explicitly out of scope per the requirements ("No native in-app password form in v1").

**Decision**: Option 1 — an "Account & Security" menu item that initiates Keycloak's `UPDATE_PASSWORD` action via `kc_action=UPDATE_PASSWORD` on the authorization endpoint, rendered by the **already-branded login theme**, returning the user to the app afterward.

**Rationale**:

1. **The login theme is branded; the account theme is not — this is the deciding factor.** The account console runs `accountThemeImplementation: none` (`vite.config.ts:12`), so deep-linking it would land the user on a **stock, unbranded** Keycloak SPA — exactly the whitelabel leak this phase exists to kill. The `login-update-password` page, by contrast, lives in the Keycloakify JAR (the login theme), which is already Kazi-branded. Triggering `kc_action=UPDATE_PASSWORD` renders the change-password screen under that branded login theme, keeping the user on a Kazi surface end-to-end.
2. **Keycloak owns credentials; the app should not.** The flow keeps password policy, strength, and history at the IdP — no duplication, no drift, and no credential handling reintroduced into Kazi, preserving the BFF invariant that the app never touches credentials outside the KC login screen.
3. **It reuses the existing SSO session.** Because the user is already authenticated at Keycloak, the `kc_action` flow runs without re-auth in the happy path; if the KC SSO session has expired, it re-auths via the same branded login — graceful either way.
4. **It rides the existing gateway OAuth client.** The app initiates the action through the gateway's OAuth client (the gateway adds `kc_action` to the authorization request), so there is no new account-console URL surface to resolve and no hardcoded host — honouring the multi-realm/multi-env and "no hardcoded host" constraints.
5. **No native form, no account-theme build.** This avoids both the credential-handling cost of a native form and the cost (and scope) of theming the account console (`accountThemeImplementation` stays `none`, [ADR-312](ADR-312-visible-brand-rebrand-scope.md)).

**Consequences**:
- Positive: Self-service password change rendered on the **already-branded login theme** — no unbranded context switch, no account-theme build needed; zero policy duplication and no new credential handling.
- Positive: Honours the BFF model and the explicit "no native form in v1" scope; rides the existing gateway OAuth client.
- Negative: The exact wiring of the `kc_action` flow — whether the gateway adds the `kc_action` param to its authorization request or the app builds a direct authorization-endpoint link — is an **implementation detail for `/breakdown`** to settle. Either way the round-trip goes through the gateway's OAuth client.
- Neutral: The account console (stock, `accountThemeImplementation: none`) remains intentionally **unused** for this flow; sessions/MFA surfaced by the console are out of scope here.
- Related: [ADR-312](ADR-312-visible-brand-rebrand-scope.md) (why the account console is *not* themed, which drove this choice), [ADR-310](ADR-310-branded-auth-landing-strategy.md) (consistency across KC-rendered surfaces), ADR-T005 (portal magic-link auth — portal gets this entry only where applicable to its model).
