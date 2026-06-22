# ADR-320: BYOC Ingestion Boundary — Kazi Does Not Integrate Gmail or Run Extraction

**Status**: Accepted

**Context**:

The Phase 81 use case is "a firm's lawyer, sitting in Claude with a Gmail connector, files an email into the right Kazi matter without copy-paste." The obvious naive design is for Kazi to integrate Gmail directly (OAuth to Google, poll/IMAP/webhook the mailbox, run an LLM to extract subject/body/attachments/deadlines, and write the result). Kazi already shipped a read-only MCP server (Phase 78, ADR-303) on the "bring your own Claude; Kazi provides grounded context" model — the firm's Claude reads Kazi over MCP, and the firm pays the LLM tokens. The strategy note reserved a gated write-back path. The constraints in play: Kazi is multi-tenant schema-per-tenant; the firm operates under POPIA (data-protection); the founder mandate is a small Kazi surface and the "firm pays the tokens" cost model; and the firm's Claude *already* holds the Gmail connector.

**Options Considered**:

1. **BYOC receive-only — Kazi exposes MCP write tools; the firm's Claude reads the mailbox, extracts, and calls the tools with structured input (CHOSEN)** — no Gmail/Google dependency, no IMAP/POP, no inbound webhook, no mail poll, no Anthropic/LLM call in the Kazi backend. Kazi receives already-structured input and validates (tenant, capability, linkage, idempotency).
   - Pros: tiny Kazi surface (no mailbox plumbing, no provider keys, no extraction model to host/version/cost); the firm pays the tokens (cost model preserved); reuses the existing MCP auth/enablement/consent/audit pipeline verbatim; clean POPIA posture (Kazi never touches the mailbox; data flows *into* Kazi, the lower-risk direction, already human-supervised); no new Google/Anthropic vendor relationship per tenant.
   - Cons: depends on the firm running a Claude client (no server-side automation/cron filing); extraction quality is outside Kazi's control; a separate consumer skill must be built (in `../claude-for-legal-sa`, not this repo).
2. **Server-side Gmail integration with in-product extraction** — Kazi OAuths to Google per tenant, polls/webhooks mailboxes, runs its own LLM to extract, and files automatically.
   - Pros: works without the firm running Claude; fully automatable; Kazi controls extraction quality.
   - Cons: large surface (Gmail OAuth, token refresh, webhook receiver, poll scheduler, mailbox sync state per tenant); Kazi now pays the LLM tokens (breaks the cost model); Kazi ingests entire mailboxes (a much larger POPIA egress/processing footprint, full DPIA territory); a new per-tenant Google vendor relationship; duplicates what the firm's Claude already does. Directly contradicts the founder mandate.
3. **Hybrid — Kazi runs Gmail ingestion but defers extraction to the firm's Claude (or vice-versa)** — split the mailbox plumbing from the reasoning.
   - Pros: some automation without owning extraction.
   - Cons: worst-of-both — still carries the Gmail OAuth/webhook/sync surface and the POPIA mailbox footprint, while adding an awkward Kazi↔Claude handoff for half-processed data; more moving parts than either pure option.

**Decision**: BYOC receive-only. Kazi adds **no** Gmail/Google/IMAP/POP dependency, **no** inbound webhook or mail poll, and **no** Anthropic/LLM/extraction call to the backend. The firm's own Claude holds the Gmail connector and the Kazi MCP server, reads the mailbox, performs extraction under human supervision, and calls the Kazi write tools with structured input. Kazi receives and validates; it never reads the mailbox and never reasons.

**Rationale**:

1. **Cost model preserved.** "The firm pays the tokens" is the whole economic premise of the MCP-plugin play (ADR-303). A server-side extraction LLM would put the token bill on Kazi per tenant — economically backwards.
2. **POPIA posture stays clean.** A server-side mailbox integration would have Kazi ingesting and processing entire client mailboxes — a large new processing/egress footprint requiring its own DPIA. BYOC keeps Kazi out of the mailbox entirely; data arriving over the write tools flows *into* Kazi (lower-risk than the Phase 78 egress) and is already filtered by a human-supervised Claude.
3. **Small surface, less to secure.** No Google OAuth, no token refresh, no webhook receiver, no poll scheduler, no per-tenant mailbox sync state — none of it exists to break or be attacked. This honours the founder's small-surface mandate and the reuse-the-Phase-78-pipeline mandate.
4. **The firm already has the connector.** Lawyers already reach for Claude with Gmail attached. BYOC meets them where they are instead of duplicating a capability they have.
5. **Clear trust boundary.** Kazi trusts Claude to have read the right mailbox and chosen the right matter; Kazi validates every authorization, tenant, linkage, and idempotency invariant before persisting (see the trust-vs-validate table in the phase doc). Trust is bounded and explicit, not blind.

**Consequences**:
- Positive: no Gmail/Google/IMAP dependency, no LLM call, no mailbox sync — verifiable at review by dependency/grep checks (a hard boundary the phase mandates); cost model and POPIA posture intact; the Phase 78 pipeline is extended, not forked.
- Positive: the boundary is testable as an explicit non-goal (review-time assertion that no such dependency was added).
- Negative: no automation without a running Claude client (no server-side cron filing) — acceptable; the human-in-the-loop is a feature for legal work, not a bug.
- Negative: extraction quality is outside Kazi's control; a separate consumer skill is required (`../claude-for-legal-sa`) and is out of scope here.
- Related: [ADR-303](ADR-303-mcp-authentication.md) (MCP auth + "reuse, don't fork" + the firm-pays-tokens model this extends), [ADR-321](ADR-321-mcp-write-tool-category.md) (the write tools that receive the structured input), [ADR-319](ADR-319-inbound-correspondence-domain.md) (what Kazi persists from the received input).
