# ADR-303: MCP Authentication — OAuth 2.1 Resource Server Reusing Keycloak

**Status**: Accepted

**Context**:

An MCP client (Claude Desktop / Code / claude.ai) must authenticate to the Kazi MCP server as a specific firm user, so that every tool call executes with that user's tenant + RBAC + project access. Kazi already authenticates its web API with Keycloak OIDC: an inbound JWT flows through `BearerTokenAuthenticationFilter` + `ClerkJwtAuthenticationConverter` (identity), then `TenantFilter` (binds `RequestScopes.TENANT_ID`/`ORG_ID` from the JWT `o.id` org claim), then `MemberFilter` (resolves the JWT subject → `Member` → `OrgRole`, binding `MEMBER_ID`, `ORG_ROLE`, and `CAPABILITIES` at `MemberFilter.java:80`). All context is Java 25 `ScopedValue`.

The founder mandate is explicit: **reuse auth, do not fork it.** No parallel auth model, no API keys, no service-account-impersonates-user shortcut. A user must see via MCP exactly what their Kazi role + project access permit. The MCP spec models the server as an **OAuth 2.1 protected resource** that publishes protected-resource metadata so the client can discover the authorization server (Keycloak) and run the authorization-code flow itself.

**Options Considered**:

1. **OAuth 2.1 resource server reusing the existing Keycloak JWT pipeline (CHOSEN)** — The MCP server validates Keycloak access tokens exactly as the web API does and publishes `/.well-known/oauth-protected-resource` pointing at the Keycloak authorization server. The Claude client performs the authorization-code (or device) flow against Keycloak to obtain a token bound to the firm org + user. The inbound token rides the existing `BearerToken → TenantFilter → MemberFilter` chain, so `RequestScopes` is fully bound before any tool executes.
   - Pros: Honours the mandate exactly — one auth model, no impersonation. The token carries org + subject, so tenant + member + capabilities resolve through the unchanged filter chain. A user's MCP scope equals their web scope automatically. Audit attribution is the real member. Standard, spec-compliant MCP auth.
   - Cons: Requires a Keycloak OAuth client configured for the MCP/Claude redirect + device flow, and the protected-resource-metadata endpoint. The exact authorization-code-with-PKCE / dynamic-client-registration handshake the Claude client expects must be validated against the MCP spec and Keycloak's capabilities.

2. **Per-user API keys minted in Kazi** — Issue a long-lived API key per member; MCP authenticates with the key.
   - Pros: Simple for the client; no OAuth dance.
   - Cons: Forks the auth model the mandate forbids. API keys route through the `/internal/*` `ApiKeyAuthFilter` path, which `MemberFilter.shouldNotFilter` **excludes** — so `MEMBER_ID`/`ORG_ROLE`/`CAPABILITIES` would be unbound and every `@RequiresCapability` / `ActorContext.fromRequestScopes()` would fail unless we hand-bind capabilities, duplicating `MemberFilter`. Long-lived keys are a POPIA/credential-hygiene liability. Explicitly out of scope.

3. **Service account that impersonates the user** — A single MCP service principal authenticates, then asserts a user id per call.
   - Pros: One credential to manage; flexible.
   - Cons: The exact impersonation shortcut the mandate forbids. Decouples the audit trail from a real OAuth-authenticated identity, destroying the POPIA-defensible "which user pulled which data" property the strategy depends on. A bug in the asserted-id path is a cross-user data leak.

**Decision**: Option 1 — the MCP server is an OAuth 2.1 protected resource that validates Keycloak access tokens through the existing JWT → `TenantFilter` → `MemberFilter` pipeline and publishes protected-resource metadata for client-driven authorization-code/device flow against Keycloak. Tokens are scoped to org + user; no API keys, no impersonation.

**Rationale**:

1. **It is the only option that satisfies the mandate.** Reusing the JWT pipeline means there is literally one auth model; the MCP server adds a discovery endpoint, not a parallel system.
2. **The filter chain does the hard work for free.** Because `/mcp` is on the authenticated path (not excluded by `MemberFilter.shouldNotFilter`, unlike `/internal/*`), the same token that authorises the web app binds `TENANT_ID`, `MEMBER_ID`, `ORG_ROLE`, and `CAPABILITIES` before a tool runs — so scope equality with the web app is automatic, not re-implemented.
3. **It preserves the audit property the strategy is built on.** A real OAuth-authenticated member means `mcp.session.opened` / `mcp.tool.invoked` events attribute to an actual user, giving the POPIA-defensible record of who pulled which client data into AI.
4. **The alternatives reintroduce exactly the risks the founder ruled out** — unbound capabilities (API keys on `/internal`), or impersonation breaking attribution.

**Consequences**:
- Positive: One auth model; MCP scope = web scope by construction; audit attribution is the real member; spec-compliant.
- Positive: No new capability-binding code — the existing `MemberFilter` binds `CAPABILITIES` for the `/mcp` path automatically.
- Negative: Requires Keycloak configuration (an OAuth client for the Claude redirect/device flow) and a `/.well-known/oauth-protected-resource` metadata endpoint; the precise client handshake (PKCE, possible dynamic client registration, token audience) must be verified against the MCP spec + Keycloak during S1.
- Negative: Token lifetime/refresh UX for a long-lived desktop client connection must be handled per Keycloak's token policy.
- Neutral: A capability-binding helper would only be needed if MCP ever ran off the JWT path; it does not, so none is added.
- Related: [ADR-301](ADR-301-mcp-deployment-topology.md) (in-process placement that puts `/mcp` on the authenticated chain), [ADR-302](ADR-302-mcp-read-scope-model.md) (the member identity scopes firm-side reads), [ADR-304](ADR-304-mcp-tenant-isolation-capability-gating.md) (`MCP_ACCESS` front door layered on this auth), [ADR-T008](ADR-T008-tenant-scoped-runner.md) (the sanctioned `RequestScopes` scope-binding API).
