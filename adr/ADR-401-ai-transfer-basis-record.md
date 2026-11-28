# ADR-401: AI transfer basis record as the prerequisite for AI egress

**Status**: Accepted

*Amended 2026-11-28 (architecture review): field names aligned with the V38 DDL (`other_ground_text`, `dpa_attested_date`, `statement_version`); a missing basis BLOCKS user-invoked route A runs and makes system-invoked runs skip without a row (FD-11).*

**Context**:

Sending client PI to Anthropic PBC is a transfer to a third party in a foreign country (the United States), so POPIA s72 applies. Under BYOAK ([ADR-201](ADR-201-secret-store-reuse-for-ai-keys.md), [ADR-282](ADR-282-per-invocation-cost-metering-byoak.md)) the firm holds the Anthropic contract and is the responsible party; Kazi is its operator (s20/s21). The primary ground is s72(1)(a), a binding agreement (the firm's Anthropic Commercial Terms + DPA: no training, onward-transfer terms); the belt-and-braces ground is s72(1)(b), consent via the engagement letter (Legal basis Q2). On route B the firm's staff use their own Claude: Claude Free/Pro/Max have no DPA and may train on inputs, so **s72(1)(a) fails for consumer-plan MCP use**, and Kazi cannot verify the plan technically.

At HEAD nothing records any of this. `AiFirmProfile` (`integration/ai/profile/AiFirmProfile.java`) is a single mutable row of prompt configuration (practice areas, house style, preferred model, budget), gated by `AI_MANAGE` and cached for prompt assembly ([ADR-283](ADR-283-prompt-architecture-firm-profile-cache.md)). `McpEgressConsent` (`mcp/consent/McpEgressConsent.java`, table `mcp_egress_consents`) is an append-only GRANTED/REVOKED row with a `consentVersion` string — an owner acknowledgement for MCP only, not a legal basis and not data-subject consent ([ADR-305](ADR-305-mcp-popia-consent-audit.md)). The provider "Anthropic PBC, United States" appears nowhere as data (assumption A1 wants a later provider or region to be a data change).

**Options Considered**:

1. **Extend `AiFirmProfile` with the s72 fields.**
   - Pros: One AI settings row; existing form and endpoint.
   - Cons: Mutable with no history, so a ledger event cannot point at "the basis in force when this was sent"; gated by `AI_MANAGE` (admins) where the basis must be owner-only; mixes a legal record into a prompt-config row that is cached and read on every skill call; does not cover route B, which does not read the firm profile.
2. **Reuse and widen `McpEgressConsent`.**
   - Pros: Already append-only and versioned; already part of MCP enablement.
   - Cons: It is MCP-only and route A would have to depend on `mcp`; its semantics are "the owner acknowledged", which the wording rules are trying to separate from a legal basis; structured fields (grounds, attestations, provider, country) would overload a two-state table.
3. **Hard-code Anthropic/US and a single "AI terms accepted" checkbox in org settings.**
   - Pros: Minimal.
   - Cons: Violates A1; records neither the ground nor the attestations; no history; cannot feed the PAIA manual, the processing register or the DSAR answer.
4. **A new append-only `ai_transfer_bases` table in `egress/policy`, the latest version being current, required by both routes (chosen).**
   - Pros: Versioned, snapshot-able, owner-only, provider and country as data, one prerequisite shared by route A and route B.
   - Cons: A second record next to the MCP acknowledgement that owners must complete; every firm must complete it before its first AI use.

**Decision**: The firm's s72 basis is an append-only `ai_transfer_bases` record — provider legal entity and country, s72 grounds, commercial terms + DPA attestation, zero-data-retention status and the MCP commercial-plan attestation — recorded by an owner (`AI_EGRESS_GOVERN`) and required before any route A call and for MCP enablement.

**Rationale**:

*Fields (R5.2).* `provider_legal_entity` (default "Anthropic PBC"), `provider_country` (default "United States"), `s72_grounds` jsonb set of `BINDING_AGREEMENT` / `CONSENT` / `OTHER` + `other_ground_text`, `commercial_terms_dpa_attested` + `dpa_attested_date`, `zero_data_retention` YES / NO / UNKNOWN, `mcp_commercial_plan_attested`, `statement_version` (the version of the attestation copy shown, e.g. `ai-transfer-basis-v1`; RG-3 copy changes bump it), `recorded_by`, `recorded_at`, `version`. Write-time validation: at least one ground; `OTHER` needs text; `BINDING_AGREEMENT` needs the DPA attestation and its date. Every save is a new version and emits `egress.basis.recorded` (ids and enum values only).

*Prerequisite.* Route A, user-invoked runs: `EgressGuard.prepareSkill` returns `Blocked(TRANSFER_BASIS_MISSING)` when no version exists — no provider call, no cost, a BLOCKED execution and ledger row ([ADR-402](ADR-402-ai-egress-ledger.md)). Route A, system-invoked runs (collections scan, cash digest): their existing pre-flights check `basisRecorded()` and **skip** like an unconfigured provider, writing no BLOCKED row each night (FD-11). Route B: `McpEnablementService.effectiveState()` also requires a current basis with `mcp_commercial_plan_attested = true` ([ADR-399](ADR-399-mcp-dto-egress-classification-single-writer.md)). The attestation is Kazi recording the firm's statement that staff use Claude Team, Enterprise or the API; the UI says Kazi cannot verify it.

*Snapshots.* Each ledger event stores `basis_version` and `policy_version`, so a DSAR answer or a Regulator query can name the basis in force at the time. The sibling `ai_egress_policy_versions` table (identifiers ALLOW | MASK, default MASK, FD-8; route-A names ALLOW | TOKENISE, default TOKENISE; relaxing needs a reason; no row = code defaults reported as version 0) follows the same append-only pattern but is gated by `AI_MANAGE`.

*Why owner-only.* The basis is a legal statement by the responsible party; admins inherit everything except `Capability.OWNER_ONLY`, so the new capability is added there and to the frontend mirror together ([ADR-355](ADR-355-owner-only-capabilities-client-side.md)).

**Consequences**:

- Positive:
  - One record answers "on what basis does my client data go to the US?" for both routes, and feeds the processing register, the PAIA transborder part and the engagement-letter clause ([ADR-403](ADR-403-popia-document-integration-ai-egress.md)).
  - A different provider or region later is a new version with different values, not a code change (A1).
- Negative:
  - A firm without a recorded basis is refused AI on both routes; a setup prompt on settings and the MCP card explains this instead of a silent failure. There are no live tenants (FD-1, ruled 2026-11-28), so no migration handling for already-enabled firms is built.
  - Kazi records attestations it cannot check (DPA, zero data retention, commercial plan). Negotiating or verifying Anthropic's terms is out of scope; the attestation copy is RG-3.
  - There is no "withdraw basis" action; a firm stops egress by disabling AI or MCP. A later version with fewer grounds still counts as a basis.
  - The firm–Kazi operator terms covering AI egress (s20/s21, s22 breach notification) are a contract task, RG-7.
- Related: [ADR-397](ADR-397-one-egress-guard-two-route-adapters.md), [ADR-400](ADR-400-special-pi-children-biometric-blocking.md) (unblocks are a separate owner record), [ADR-305](ADR-305-mcp-popia-consent-audit.md), [ADR-090](ADR-090-secret-storage-strategy.md); [architecture §19](../architecture/phase94-ai-egress-guard.md); slice 94A (record + form), 94D (MCP enablement).
