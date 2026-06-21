# ADR-307: Session Lifetime Policy — Access/Idle/Max Values, Gateway Anchor & Override Story

**Status**: Accepted

**Context**:

Kazi's BFF model splits session state across two systems: the Spring Cloud Gateway holds the OAuth2 tokens server-side in Spring Session (`timeout: 8h`, `gateway/src/main/resources/application.yml:26`), and Keycloak owns the SSO session and token lifespans. The realm export sets **no** lifetime keys (`compose/keycloak/realm-export.json` — grep for `accessTokenLifespan`/`ssoSession*` returns 0), so Keycloak runs server defaults (~5m access, ~30m idle, ~10h max). Because nothing pins the two together, the gateway's 8h belief can diverge from Keycloak's actual lifetimes: after long inactivity Keycloak has ended the SSO session while the gateway still thinks its session is live, so the silent refresh via `OAuth2AuthorizedClientManager` fails — the documented stale-session bug ("clicking anything leads to errors").

This ADR fixes the values explicitly and decides which lifetime the gateway session should anchor to, the dev/prod override mechanism, and how `offline_access` interacts.

**Options Considered**:

1. **Explicit realm lifetimes (5m / 30m / 10h) with the gateway session anchored to SSO *max* (CHOSEN)** — Set `accessTokenLifespan=300`, `ssoSessionIdleTimeout=1800`, `ssoSessionMaxLifespan=36000` in `realm-export.json`; change the gateway `spring.session.timeout` from 8h to `${GATEWAY_SESSION_TIMEOUT:10h}` so it equals SSO max. Invariant: `access(5m) < idle(30m) < max(10h) == gateway`. No `offline_access` for the gateway-bff client, so SSO idle/max fully govern its refresh path.
   - Pros: The gateway can never outlive the IdP at the absolute ceiling — kills the divergence class at the 10h cap; values are explicit and committed (not implicit defaults that could shift on a KC upgrade); idle-driven expiry (30m) is now *handled gracefully* by the [ADR-308](ADR-308-expired-session-handling.md) funnel rather than leaking; one obvious anchor (max) is the easiest to reason about and test.
   - Cons: At the 10h max the gateway session and SSO end at the same instant — a request landing exactly on the boundary still needs the graceful funnel (so this option is only complete with ADR-308); does not by itself prevent the 30m-idle bounce (intended — that's UX, handled downstream).

2. **Leave lifetimes at KC defaults; only handle expiry gracefully** — Don't set realm keys; rely entirely on [ADR-308](ADR-308-expired-session-handling.md) to catch every expiry.
   - Pros: Smallest config change; defaults are reasonable.
   - Cons: Implicit defaults can change on a Keycloak version bump (silent behaviour drift); the 8h-vs-10h mismatch persists (gateway under-shoots max), so a session can appear dead to the gateway while KC still has 2h left — wasteful re-logins; nothing committed to source means no reproducible policy; fails the "lifetimes are config not accident" intent.

3. **Anchor the gateway session to SSO *idle* (30m) instead of max** — Set the gateway `timeout` to 30m to mirror idle.
   - Pros: Gateway and KC idle expire together; smallest live session footprint.
   - Cons: Spring Session idle and KC idle are *measured differently* (KC idle resets on token refresh/activity at the IdP; Spring Session idle resets on gateway request) — pinning them at the same value invites a new, subtler drift where one resets and the other doesn't; the active-user 10h ceiling would then be enforced only by KC while the gateway flaps at 30m; harder to reason about than a single hard cap.

**Decision**: Option 1 — explicit `5m / 30m / 10h` realm lifetimes with the gateway Spring session anchored to SSO **max** (10h), env-overridable, with no `offline_access` issued to the gateway-bff client.

**Rationale**:

1. **The bug is a divergence bug, so remove the divergence at the hard ceiling.** Equating the gateway session to SSO max means the two longest-lived anchors expire together; the gateway can never hold a "live" session after KC has hit its absolute cap. The remaining idle-expiry path is a *predictable* event, not a divergence, and is caught by [ADR-308](ADR-308-expired-session-handling.md).
2. **Explicit beats implicit for a multi-tenant security control.** Committing the values to `realm-export.json` makes session policy reviewable, testable (import assertion, §11.8), and immune to Keycloak default changes across the 26.x line.
3. **Env-overridable serves the fork/firm story.** Values flow through `realm-export.json` (committed default) with an optional env-parameterised realm `PUT` in `compose/scripts/keycloak-bootstrap.sh` (`KC_ACCESS_TOKEN_LIFESPAN`/`KC_SSO_IDLE_TIMEOUT`/`KC_SSO_MAX_LIFESPAN`) and `GATEWAY_SESSION_TIMEOUT` for the gateway — a security-conscious firm can shorten idle in prod with no code change, matching the existing env-override pattern.
4. **No offline token can outlive the SSO idle window.** The gateway-bff client scope is `openid,profile,email,organization` (no `offline_access`, `application.yml:31-40`), and offline lifetimes are governed by separate `offlineSession*` keys we deliberately do not set. So `ssoSessionIdleTimeout` genuinely bounds the gateway's refresh path — there is no silent long-lived refresh token behind it. Documented so a future `offline_access` request must set offline lifetimes too.
5. **30m idle / 10h max fits the domain.** A professional-services workday (legal/accounting) tolerates a 10h active cap; 30m idle bounds unattended-session risk on shared office machines (POPIA-relevant). The tradeoff is revisitable per-firm via the override.

**Consequences**:
- Positive: The stale-session divergence at the ceiling is structurally eliminated; session policy is explicit, committed, and testable.
- Positive: Per-firm/prod hardening is a config knob, not a code change.
- Negative: Complete only in combination with [ADR-308](ADR-308-expired-session-handling.md) — the 30m idle bounce still happens and *must* be handled gracefully, or this change alone just relocates the error.
- Negative: Two override layers (realm-export default + bootstrap PUT) to keep in sync; the import assertion must guard against silent drift.
- Neutral: Active users now hard-stop at 10h regardless of activity (intended security behaviour, previously implicit).
- Related: [ADR-308](ADR-308-expired-session-handling.md) (handles the now-predictable idle expiry), [ADR-309](ADR-309-return-to-redirect-safety.md) (return-to on the resulting re-login), GAP-L-22 (`GatewaySecurityConfig.java:116-152`, unaffected — it is a user-mismatch guard, not a lifetime guard).
