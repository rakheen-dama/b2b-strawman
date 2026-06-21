# ADR-312: Visible-Brand Rebrand Scope — Render-Only, Realm/Theme Identifiers Unchanged

**Status**: Accepted

**Context**:

The product is **Kazi** (company **b2mash**), but the Keycloak realm, theme, and OAuth identifiers all carry the dead brand **`docteams`**: the realm id/realm name are `docteams` (`realm-export.json:2-3`), the OIDC **issuer URI is `…/realms/docteams`** (baked into the `iss` claim of every token, and into gateway/backend `application*.yml`, the bootstrap script `REALM="docteams"`, mock-idp, and compose), the theme is `docteams` (`vite.config.ts:11`, `package.json:2`, `themes/docteams/`), and the OAuth secret default is `docteams-web-secret`. Meanwhile the *user-facing* Keycloak surfaces (login pages, email templates) still render "DocTeams" copy/branding, and the error/info/logout pages are stock — the whitelabel leak. A repo-wide grep finds ~655 `docteams` matches, but the vast majority are QA/insight artefacts, not live identifiers; the live identifiers are concentrated in ~30 infra files across 4 services.

This ADR decides the *scope* of the rebrand: what changes to read "Kazi," and what deliberately stays `docteams`.

**Options Considered**:

1. **Visible-brand only — rebrand rendered output, leave realm id + theme directory identifier as `docteams` (CHOSEN)** — Change only what the user *sees*: the rendered copy, logo, titles, and palette in the Keycloakify login pages (`compose/keycloak/theme/src/login/pages/*.tsx`) and email templates (`themes/docteams/email/html/*.ftl`), plus bringing `Error.tsx`/`Info.tsx`/logout under the Kazi theme. The realm id/name (`docteams`), the issuer URI `…/realms/docteams`, the `loginTheme`/`emailTheme` identifier values, the theme directory name, and the OAuth client/secret identifiers are **left unchanged**.
   - Pros: Contained to theme assets + page coverage; touches no OAuth config, no token issuance, no redirect URIs, no e2e selectors; zero token invalidation; zero risk to the multi-tenant OIDC flow; achieves the entire user-visible goal (every auth surface reads Kazi).
   - Cons: Internal identifiers stay `docteams` — a cosmetic mismatch visible only to engineers reading config (acceptable; invisible to users).

2. **Theme-directory rename** — Rename the theme directory + `vite.config` `themeName` + `package.json` name + realm `loginTheme`/`emailTheme` from `docteams` to `kazi`, but keep the realm id.
   - Pros: Internal theme naming matches the brand.
   - Cons: Forces coordinated changes across `realm-export.json` (`loginTheme`/`emailTheme`), `vite.config.ts`, `package.json`, the `themes/docteams/` dir, the built JAR paths, and compose mounts; risks a theme-not-found at import if any reference is missed; pure churn for no user-visible benefit (users never see the theme *identifier*).

3. **Full realm rename** — Rename the realm id `docteams` → `kazi` everywhere.
   - Pros: Fully consistent internal naming.
   - Cons: The realm id is **load-bearing in the OIDC issuer URI** (`…/realms/docteams`) — it appears in the `iss` claim of every issued token and in every redirect/issuer config across gateway, backend, mock-idp, compose, and the bootstrap script (~30 files, 4 services); changing it **invalidates every existing token/session**, requires a coordinated multi-service config migration, and breaks e2e selectors/fixtures (`padmin@docteams.local`, `keycloak-auth.ts` `/\/realms\//`) — all for **zero user-visible benefit**. This is a migration project, not an auth-hardening task.

**Decision**: Option 1 — visible-brand only: rebrand rendered theme/email output to Kazi and cover error/info/logout pages; leave the realm id, issuer URI, theme directory, and OAuth identifiers as `docteams`.

**Rationale**:

1. **The realm id is infrastructure, not branding.** `docteams` is embedded in the OIDC issuer URI and therefore in the `iss` claim of every token and in issuer/redirect config across 4 services. Renaming it invalidates tokens and forces a ~30-file coordinated migration — a token-invalidating change with **no** user-visible payoff, which is exactly what an auth-*hardening* phase must avoid.
2. **Users see rendered output, not identifiers.** The whitelabel leak is "DocTeams" *copy/logo* and stock error pages — all of which option 1 fixes. Users never observe `loginTheme: "docteams"` or the issuer path, so renaming those is churn.
3. **Containment is a feature.** Restricting the change to theme assets + page coverage keeps it off the OAuth/token/redirect/e2e-selector surface, so it cannot regress the multi-tenant auth flow — aligning with the phase's "don't fork the auth model" constraint.
4. **Consistency is achieved where it matters — via the login theme, not the account console.** The account console is **not** themed: it runs `accountThemeImplementation: none` (`vite.config.ts:12`) and renders as a stock Keycloak SPA. That is precisely why [ADR-311](ADR-311-change-password-approach.md) routes change-password through `kc_action=UPDATE_PASSWORD` (rendered by the already-branded **login** theme) rather than deep-linking the unbranded console. Branding all login-theme-rendered surfaces (login, email, error, info, logout, and the `UPDATE_PASSWORD` page) Kazi, alongside the first-party `/sign-in`/`/signed-out` routes ([ADR-310](ADR-310-branded-auth-landing-strategy.md)), makes every auth surface read as one product — the actual goal.
5. **POPIA/trust posture is served by appearance, not identifier.** A consistently-branded auth surface builds user trust; the internal identifier is irrelevant to that.

**Consequences**:
- Positive: Every user-facing auth surface reads Kazi; no token invalidation; no OAuth/redirect/issuer/e2e changes; change is contained to theme assets.
- Positive: All login-theme-rendered KC pages render branded — including the `UPDATE_PASSWORD` change-password page ([ADR-311](ADR-311-change-password-approach.md)). The account console stays `accountThemeImplementation: none` (stock) and is intentionally not used for change-password.
- Negative: Internal identifiers (`docteams` realm/theme/secret) stay inconsistent with the brand — an engineering-only cosmetic mismatch, documented as deliberate.
- Negative: A future genuine realm rename (if ever justified) remains an outstanding, separate migration — explicitly deferred.
- Neutral: The theme JAR must be rebuilt and redeployed (`build-keycloak-theme` → `compose/keycloak/providers/keycloak-theme.jar`) for rendered changes to take effect; verify in a real browser, not from source.
- Related: [ADR-310](ADR-310-branded-auth-landing-strategy.md) (which surfaces are KC-themed vs first-party), [ADR-311](ADR-311-change-password-approach.md) (why the account console is *not* themed → `kc_action=UPDATE_PASSWORD` on the branded login theme instead), Phase 79 §11.4.6 / §11.7 (the page-coverage audit).
