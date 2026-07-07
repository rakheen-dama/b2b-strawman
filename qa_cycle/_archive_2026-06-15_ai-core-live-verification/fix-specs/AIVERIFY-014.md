# AIVERIFY-014 — CORS double-Origin on /ai/reviews approve

- **Severity**: Low (as filed)
- **Disposition**: **NOT-A-DEFECT** (for the product path) — could not reproduce a double `Access-Control-Allow-Origin`; the gateway returns exactly one ACAO on the AI approve route, and the real product flow has no CORS at all. New tracker id assigned (the V11 note had none).
- **Area**: Gateway CORS / Next.js server actions
- **Effort**: n/a (no fix)

## What was claimed

V11 note (`qa_cycle/checkpoint-results/V11-final.md:63`): a direct cross-origin REST probe from the
:3000 page to the gateway :8443 was "CORS-blocked — the known double-Origin/CORS deferred item." The
gap to investigate: does the gateway/backend emit a duplicated `Access-Control-Allow-Origin` header
that breaks the browser preflight for `/ai/reviews` approve?

## Findings (grounded + reproduced)

### 1. The product approve path is a server action — no browser CORS exists

`/ai/reviews` Approve/Reject is a **Next.js Server Action**, server-to-server:
- `app/(app)/org/[slug]/ai/reviews/actions.ts:1` `"use server"` → `approveGateAction` (`:18`) calls
  `approveAiGate` from `lib/api/ai.ts`.
- `lib/api/ai.ts:1` `import "server-only"`; `:153-158` `approveAiGate`/`rejectAiGate` →
  `api.post("/api/ai/gates/{id}/approve|reject", …)`, which (in keycloak mode) runs on the Next.js
  server and targets `GATEWAY_URL` server-side, forwarding the SESSION cookie + CSRF
  (`lib/api/client.ts:8-10,23-32`).

A server-to-server call carries **no `Origin` header and triggers no preflight** — CORS is a
browser-only mechanism. So the product approve flow is **not subject to CORS** at all.

### 2. The gateway already de-duplicates ACAO on `/api/**` — single header, verified live

Both the gateway and the backend define a CORS source for `/**`:
- Gateway: `GatewaySecurityConfig.java:91-103` `setAllowedOrigins(List.of(frontendUrl))`
  (`frontend-url` default `http://localhost:3000`).
- Backend: `SecurityConfig.java:167-183` binds `cors.allowed-origins`
  (`application-local.yml:97-101` = `http://localhost:3000/3001/3002`).

A browser request that traverses browser → gateway → backend would get ACAO appended twice — which is
exactly why the gateway route carries a **dedupe filter**:
`application.yml:48-53`
```yaml
- id: backend-api
  predicates: [ Path=/api/** ]
  filters:
    - TokenRelay=
    - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_FIRST
```
This is the **only** proxied route, and it covers the AI approve path (`/api/ai/gates/...` matches
`/api/**`). Live reproduction against the running gateway (HTTP :8443):

```
# OPTIONS preflight, AI approve route
curl -i -X OPTIONS http://localhost:8443/api/ai/gates/<id>/approve \
  -H "Origin: http://localhost:3000" -H "Access-Control-Request-Method: POST" ...
  → HTTP 200, Access-Control-Allow-Origin: http://localhost:3000   (count of ACAO headers = 1)

# actual cross-origin POST, AI approve route
curl -i -X POST http://localhost:8443/api/ai/gates/<id>/approve \
  -H "Origin: http://localhost:3000" -H "Content-Type: application/json" -d '{}'
  → HTTP 401, Access-Control-Allow-Origin: http://localhost:3000   (count of ACAO headers = 1)
```

`grep -c access-control-allow-origin` returned **1** in both cases — **no double-Origin**. The
non-proxied gateway endpoints (`/bff/me`, `/oauth2/authorization/keycloak`) are served by the gateway's
own filter chain (not forwarded to the backend), so only the gateway emits ACAO there → also a single
header (verified: count = 1). The dedupe filter has been present since the keycloak-mode work
(predates this cycle), so it was already in effect at V11.

### 3. What the V11 probe most likely was

The V11 "CORS-blocked" line describes a **hand-issued, non-product** direct browser fetch from the
:3000 page to :8443 (used to evidence a 400, not a real user action). A browser fetch from the page's
JS to a cross-origin gateway will preflight; if that probe omitted credentials, sent a header the CORS
config doesn't allow (`allowedHeaders` is an explicit list — `GatewaySecurityConfig.java:95`:
`Content-Type, Authorization, X-XSRF-TOKEN, Accept`), or was issued by tooling that injected its own
`Origin`, the browser would block it. That is the probe being blocked **correctly**, not a duplicated
ACAO header. It does not reflect a real user flow (which uses the server action).

## Disposition

**NOT-A-DEFECT** for the product. Concretely:
- No double `Access-Control-Allow-Origin` is reproducible on the AI approve route (or any gateway
  route) — the `DedupeResponseHeader … RETAIN_FIRST` filter already collapses it to one.
- The product `/ai/reviews` approve path is a server action with no CORS surface at all.
- The V11 observation was a non-product, hand-issued direct browser probe; its block is expected CORS
  behaviour, not a gateway misconfiguration.

No code change recommended. If a future requirement adds a **genuine browser-origin** call to the
gateway for AI actions (it currently has none), revisit the `allowedHeaders` allow-list at
`GatewaySecurityConfig.java:95` — but that is a feature need, not a fix for this finding.

## Verification of this disposition

Re-runnable: with the stack up, `curl -i -X OPTIONS http://localhost:8443/api/ai/gates/x/approve -H
"Origin: http://localhost:3000" -H "Access-Control-Request-Method: POST"` →
`grep -c access-control-allow-origin` == **1**. (Confirmed during triage 2026-06-15.)
