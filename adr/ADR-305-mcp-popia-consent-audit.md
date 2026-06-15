# ADR-305: POPIA Data-Egress Consent, Per-Tenant Enablement & MCP Audit

**Status**: Accepted

**Context**:

When a firm connects its Claude client to Kazi via MCP, client PII flows out of Kazi into the firm's external AI context. Under POPIA the firm is the responsible party for that egress, so it must be an explicit, recorded firm decision — not a default. The requirements demand: MCP is **disabled by default**; an owner/admin must enable it and acknowledge a data-egress consent before any tool returns data; the firm gets a POPIA-defensible audit record of which user pulled which client data into AI, and when.

Two modelling questions: (1) where does the per-tenant **enablement flag + connection config** live, and (2) how is the **consent** itself recorded. A hard project constraint pushes toward minimal-to-zero new tables. The existing `OrgIntegration` entity (`domain`, `provider_slug`, `enabled`, `config_json`) is the established per-tenant integration-config pattern, and the `IntegrationDomain` enum is how a new integration domain is introduced; the `IntegrationAdapter`/`IntegrationRegistry` machinery, however, is built for **outbound** ports (Xero, SMTP, PSP). The Phase 6 append-only `AuditEvent` is the established audit substrate.

**Options Considered**:

1. **`IntegrationDomain.MCP` + `OrgIntegration` row for enablement; dedicated append-only `mcp_egress_consents` table for consent; `mcp.*` events on `AuditEvent` (CHOSEN)** — Add an `MCP` constant to `IntegrationDomain`; persist a per-tenant `OrgIntegration` row (domain=MCP, enabled flag, connection config in `config_json`) — but implement **no** `IntegrationAdapter` bean (MCP is inbound read-exposure, nothing to resolve outbound). Record each consent action in a dedicated tenant-scoped `mcp_egress_consents` table (id, consented_by, consented_at, consent_version, action GRANTED/REVOKED, created_at) via one Flyway tenant migration (V100). Emit `mcp.session.opened`, `mcp.tool.invoked`, `mcp.access.denied` on the existing `AuditEvent`.
   - Pros: Enablement reuses the established integration-config pattern and surfaces naturally in the existing Integrations settings UI. Consent gets a proper append-only history (grant → revoke → re-grant, with versioned consent text) — the strong POPIA evidence trail. Audit reuses Phase 6 infra (no new audit machinery). No adapter bean avoids forcing the outbound pattern onto an inbound surface.
   - Cons: Adds one tenant table (V100), slightly against the zero-new-tables preference. `IntegrationDomain.MCP` exists without an adapter, a minor inconsistency with the other domains.

2. **Everything in `OrgIntegration.config_json` (consent fact inline), zero new tables** — Store `consentedBy`/`consentedAt`/`consentVersion` as fields in the MCP `OrgIntegration` row's `config_json`.
   - Pros: Zero new tables; fully honours the minimal-tables constraint; simplest.
   - Cons: `config_json` holds only the *current* consent fact — no history. A grant/revoke/re-grant cycle, or a consent-text version change, overwrites the prior record. For a POPIA responsible-party position, an overwritable single fact is a weak evidence trail; reconstructing "who consented to which version, when, and when it was revoked" becomes impossible. The product decision (D2) explicitly chose history over minimalism here.

3. **No per-tenant gate; consent acknowledged once at the org level out-of-band** — Enable MCP globally with a one-time click-through.
   - Pros: Least machinery.
   - Cons: Fails the "disabled by default, per-tenant opt-in" requirement; no durable, queryable consent record; no revoke control; not POPIA-defensible. Non-starter.

**Decision**: Option 1 — `IntegrationDomain.MCP` + an `OrgIntegration` row (no adapter bean) for enablement/config; a dedicated append-only `mcp_egress_consents` tenant table (Flyway tenant V100) for versioned consent history; `mcp.*` event types on the existing `AuditEvent`. When the MCP integration is disabled (or consent absent/revoked), the server refuses tool calls for that tenant with a clear, non-leaking error.

**Rationale**:

1. **Consent is evidence, and evidence needs history.** The POPIA responsible-party position depends on being able to show *who* consented to *which version* of the egress notice, *when*, and whether/when it was revoked. A dedicated append-only table captures that; an overwritable `config_json` field does not. The one-table cost is justified by the legal property it buys (the locked decision D2).
2. **Enablement fits the existing integration pattern; the adapter does not.** Reusing `OrgIntegration` + the Integrations settings UI is the path of least surprise for a per-tenant on/off + config. But MCP is inbound read-exposure with no outbound port to resolve, so adding an `IntegrationAdapter` bean would be ceremony with nothing behind it — hence enum + row, no adapter (D4).
3. **Audit reuses Phase 6 entirely.** `mcp.session.opened` / `mcp.tool.invoked` (tool name, params summary, result row count, actor, entity refs) / `mcp.access.denied` are just new event types on the append-only `AuditEvent` — no new audit infrastructure, and they deliver the "which user pulled which client data into AI" record the whole strategy leans on.
4. **Disabled-by-default with a non-leaking refusal** is the safe posture: a tenant that hasn't opted in returns a clear "MCP not enabled" error that does not reveal whether data exists.

**Consequences**:
- Positive: Strong, append-only, versioned consent trail; per-tenant enablement via the familiar Integrations UI; audit on proven Phase 6 infra; inbound nature respected (no vacuous adapter).
- Negative: One new tenant table (V100) and an `IntegrationDomain.MCP` constant without an adapter (documented inconsistency).
- Negative: The settings UI must implement enable + consent-acknowledgement + revoke, and the server must check both enablement and current consent state on every tool call (cheap, cacheable per tenant).
- Neutral: `consent_version` lets the firm re-prompt for consent if the egress notice text changes; the audit `params summary` must summarise, never inline, PII, and metrics labels carry no PII.
- Related: [ADR-304](ADR-304-mcp-tenant-isolation-capability-gating.md) (`MCP_ACCESS` is the per-member front door; this ADR is the per-tenant front door — both must pass), [ADR-301](ADR-301-mcp-deployment-topology.md) (the audit/POPIA vantage point the hosted topology provides), Phase 6 audit foundations, Phase 21 `OrgIntegration`/Integrations settings.
