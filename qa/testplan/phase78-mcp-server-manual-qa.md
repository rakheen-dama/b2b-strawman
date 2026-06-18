# Phase 78 — Kazi MCP Server: Manual QA (pre-release)

Status: **manual gate only — NO live Claude in CI.**

The Kazi MCP server (Phase 78, epics 562–567) exposes the firm's read-model to a firm
staff member's own MCP client (Claude Desktop / Claude Code) over a read-only, consent-gated,
per-tenant connector. The automated suite (`backend/src/test/java/io/b2mash/b2b/b2bstrawman/mcp/*`)
covers the handshake, the full read catalogue, audit/metrics emission, cross-tenant isolation,
capability gating, the read-only registry guarantee, pagination caps, and module/enablement
refusals via the in-process `/mcp` JSON-RPC endpoint.

What the automated suite cannot prove is a **real third-party MCP client** negotiating and driving
the connector end-to-end. That is verified once, manually, against a dev tenant before each
release. This document is the script. **Do not wire a live Claude client into CI** (no API keys in
CI, non-deterministic model behaviour, egress).

## Prerequisites

- A dev tenant on the Keycloak stack with at least one matter, one client, and (for the trust
  checks) the `trust_accounting` module enabled.
- A second, non-legal dev tenant (trust module OFF) for the module-disabled check.
- A bearer token for a member of the dev tenant with `MCP_ACCESS` (plus `VIEW_TRUST`,
  `INVOICING`, `TEAM_OVERSIGHT`, `AI_MANAGE` as needed to exercise each regime).
- Claude Desktop (or Claude Code) configured with a custom MCP server.

## Steps

1. **Start the dev stack (Keycloak mode)** per the project quick-start:
   `bash compose/scripts/dev-up.sh`, then backend + gateway + frontend via `compose/scripts/svc.sh`.

2. **Enable the MCP connector for the dev tenant** via the settings UI (Epic 566 MCP Connector
   card → Enable) or the `enableMcpAction`. Confirm the POPIA data-egress consent is recorded
   (the consent dialog must be accepted; this writes the GRANTED consent row + enables the
   `MCP` integration). Note the connector server URL shown in the card.

3. **Configure the MCP client.** Point Claude Desktop / Claude Code at the dev tenant's `/mcp`
   URL (via the gateway) with the bearer token in the `Authorization: Bearer <token>` header.
   Confirm the client completes `initialize` and `notifications/initialized` (a session id is
   issued). Confirm the negotiated capabilities advertise `tools` and `resources` only — **no
   `prompts`, `sampling`, `completion`, or `elicitation`** — and the `instructions` mention the
   read-only nature.

4. **Exercise the surface** from the client:
   - `tools/list` → the read catalogue (`list_matters`, `get_matter`, `list_clients`,
     `get_client`, `get_trust_balance`, `list_trust_transactions`, `list_invoices`, `get_invoice`,
     `get_unbilled_time`, `list_compliance_gaps`, `search_documents`, `get_document_url`,
     `get_matter_activity`, `get_audit_events`, plus `kazi_ping`). No mutating tool appears.
   - A read tool — `list_matters` → returns the tenant's matters, page size capped at 50,
     `truncated`/`total` surfaced when there is more.
   - A **capability-denied** call — e.g. `get_trust_balance` with a token lacking `VIEW_TRUST` →
     `forbidden`, no data.
   - A **module-disabled** call — `get_trust_balance` on the **non-legal** tenant → clean
     `module_disabled` (no stack trace).
   - The **revoke → next-call-refused** flow — revoke the connector in the settings UI, then issue
     any tool call from the client → `not_enabled` on the very next request (state is not cached).
     Re-enable afterwards.

5. **Capture evidence:**
   - JSON-RPC transcripts (or screenshots) for the `initialize` handshake and each call above.
   - The audit rows written for the session: `mcp.session.opened` (on initialize),
     `mcp.tool.invoked` (per successful call, carrying sanitised tool/rowCount/entityRefs/params —
     **no client/member names or free text**), and `mcp.access.denied` (per refusal, carrying the
     `deniedGate`). Read them from the tenant's `audit_event` table or the audit UI.
   - The Micrometer metrics `kazi_mcp_tool_calls_total` / `kazi_mcp_tool_latency_seconds` — confirm
     labels are `tenant` / `tool` / `outcome` only (the tenant value is a `tenant_<hex>` schema
     hash, never a name/email).

## Sign-off

Record the date, the build/commit verified, the tester, and attach the evidence. A release is
gated on this manual run passing against a dev tenant. **CI never runs a live Claude client.**
