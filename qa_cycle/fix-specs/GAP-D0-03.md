# Fix Spec: GAP-D0-03 — Keycloak login theme still branded "DocTeams"

## Problem
Keycloak's login pages still render "DocTeams" in the page title, the "Sign in to DocTeams" heading, and the footer "© 2026 DocTeams". When a user clicks the Keycloak invitation link or is redirected to the OIDC login endpoint, the first screen they see is a DocTeams-branded form.

## Root Cause (confirmed)
The Keycloak custom theme source lives in `compose/keycloak/theme/src/login/pages/`. Three files contain the hardcoded strings:

1. `compose/keycloak/theme/src/login/pages/shared/Layout.tsx`
   - Line 13: `DocTeams` (header heading)
   - Line 27: `&copy; {new Date().getFullYear()} DocTeams` (footer)

2. `compose/keycloak/theme/src/login/pages/Login.tsx`
   - Line 15: `<Layout title="Sign in to DocTeams">`

3. `compose/keycloak/theme/src/login/pages/LoginUsername.tsx`
   - Line 15: `<Layout title="Sign in to DocTeams">`

4. `compose/keycloak/theme/index.html`
   - Line 6: `<title>DocTeams</title>`

5. `compose/keycloak/realm-export.json`
   - Line 4: `"displayName": "DocTeams"` (shown in some default KC screens that aren't overridden by the custom theme)

## Fix
1. `compose/keycloak/theme/src/login/pages/shared/Layout.tsx`:
   - Line 13: replace `DocTeams` with `Kazi`.
   - Line 27: replace `DocTeams` with `Kazi`.

2. `compose/keycloak/theme/src/login/pages/Login.tsx`, line 15:
   - Change `title="Sign in to DocTeams"` → `title="Sign in to Kazi"`.

3. `compose/keycloak/theme/src/login/pages/LoginUsername.tsx`, line 15:
   - Change `title="Sign in to DocTeams"` → `title="Sign in to Kazi"`.

4. `compose/keycloak/theme/index.html`, line 6:
   - Change `<title>DocTeams</title>` → `<title>Kazi</title>`.

5. `compose/keycloak/realm-export.json`, line 4:
   - Change `"displayName": "DocTeams"` → `"displayName": "Kazi"`.

6. **Build the theme** — the theme is a Vite/React project (`compose/keycloak/theme/`). After editing TSX files, the theme must be rebuilt and the built artifact copied to the KC themes directory used by the container. The dev stack likely bakes the built theme into the KC image at startup. The fix spec should:
   - Run whatever build command the theme package defines (likely `pnpm run build` from `compose/keycloak/theme/`).
   - Restart the Keycloak container so the new theme is served.
   - If `compose/scripts/dev-up.sh` handles the theme build automatically, a full `dev-down.sh && dev-up.sh` cycle should suffice. Check `package.json` scripts before committing.

## Scope
- Keycloak theme (frontend build) + realm export config
- Files to modify:
  - `compose/keycloak/theme/src/login/pages/shared/Layout.tsx`
  - `compose/keycloak/theme/src/login/pages/Login.tsx`
  - `compose/keycloak/theme/src/login/pages/LoginUsername.tsx`
  - `compose/keycloak/theme/index.html`
  - `compose/keycloak/realm-export.json`
- Files to create: none
- Migration needed: no
- Restart required: keycloak container (+ theme rebuild)

## Verification
1. Rebuild theme, restart Keycloak.
2. Clear browser session, navigate to `http://localhost:3000/dashboard`.
3. Expected: redirect to KC login page shows "Sign in to Kazi" heading, page title "Kazi", footer "© 2026 Kazi". No occurrences of "DocTeams" in the rendered HTML.
4. Re-run CP 0.10.

## Estimated Effort
S (< 30 min) — assuming theme build is a one-command pnpm script. Bump to M if the build system is stale or produces unexpected output.

## Priority Reason
LOW severity but customer-demo-visible on the very first screen after clicking the invite link. Strings are trivial; the only risk is the theme build plumbing.
