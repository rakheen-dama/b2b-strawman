# Phase 94 Ideation — AI Egress Guard / PII protection (2026-11-28)

Builds on `mcp-plugin-strategy-2026-06-14.md` (route B) and the Phase 72 AI foundation (route A). Output: `requirements/claude-code-prompt-phase94.md`.

## Lighthouse / market
- SA small/medium law firms. Drivers (founder): real risk reduction, the design-partner objection "does my client data go to Anthropic / the US?", and responsible client data handling as a product value.
- Competitive angle: AJS has no AI product. Kazi can be the system that sends less, and proves what it sent.

## Decision rationale
- **Gap found:** no minimisation on either route. Drafting sends ID, tax, registration and address; intake sends 500 other matters with their clients; MCP returns correspondence bodies verbatim. No ledger, and the POPIA surfaces ignore AI.
- **Chosen: one guard, two adapters, one ledger.** Rejected: ML/NER (cost, opacity), keyword scanning (noise), route-only scopes.
- **Key insight:** Kazi already knows every name in the tenant, so dictionary tokenisation needs no NER. Route A tokenises reversibly; route B can't (the lawyer needs names), so names pass through there and are logged.
- **FICA correction found by the scout:** no skill sends images today (`requiresVision()` false everywhere). FICA sends extracted ID-document text, so the fix is identifier tokenisation plus a locally computed ID-match fact. The images-as-biometric default-block rule governs any future vision use.

## Founder choices
- Identifiers MASK by default (both routes). Names tokenised on route A. Special/child PI BLOCK by default, unblock by the owner on a structured legal basis.
- Detection: matter sensitivity flags (template-prefilled) + child derived from the SA ID DOB.
- Images = biometric, block by default.
- All of the advisor's additions are in: s72 transfer-basis record, "no AI" client/matter flag, MCP commercial-plan attestation, processing register + PAIA, engagement-letter AI clause.

## Legal notes (Fable advisor + Perplexity, 2026-11-28)
- s57(1)(d): prior authorisation is needed for special/child PI to a country without s72 adequacy. The Regulator has issued no adequacy determinations, so unblocking needs "prior authorisation obtained" or an attested binding-agreement basis (RG-1).
- s72: the primary ground is (1)(a), the firm's Anthropic commercial terms + DPA, with (1)(b) consent via the engagement letter as a second ground. Under BYOAK the firm holds the basis. **Consumer Claude plans fail s72(1)(a)**, hence the MCP Team/Enterprise/API attestation.
- Tokenised data is still PI (s6), so never say "anonymised". The MCP "consent" is the owner's acknowledgement, not data-subject consent, so rename it.
- LSSA revised AI guidelines (De Rebus, July 2026): no consumer tools for confidential information; honour a client mandate excluding AI; no general proactive-disclosure duty.
- Release gates RG-1..RG-9 need a practising attorney. The build is not blocked.

## Future-phase candidates surfaced
1. SA-region or self-hosted model option (A1 keeps the provider as data).
2. NER for names outside the dictionary (witnesses, people in documents), if residual risk proves material.
3. A client-facing portal "AI disclosures" view; per-matter identifier policy overrides.
4. POPIA s22 breach register (carried from the Phase 93 ideation).
