# ADR-323: Email→Matter Linking — `resolve_matter_by_email`, Claude-Disambiguates-with-Explicit-Target

**Status**: Accepted

**Context**:

Before the firm's Claude can file an email into Kazi (ADR-319), it must decide *which* matter and/or client the email belongs to. The sender's address is the strongest signal, and Kazi already has `CustomerRepository.findByEmail(email) → Optional<Customer>`. The question is **who decides the link, and how Kazi exposes the signal**. Options range from Kazi auto-filing on a best guess to Kazi providing candidates and letting the human-supervised Claude choose. The constraints: a wrong filing in a legal context is a real problem (privileged material in the wrong matter, a deadline on the wrong client); Kazi must not auto-file on a guess (founder decision); the BYOC model (ADR-320) puts the reasoning in the firm's Claude, with a human in the loop; and one email address can map to zero customers, one customer with one matter, or one customer with many matters.

**Options Considered**:

1. **`resolve_matter_by_email` read tool returns candidates; Claude disambiguates and passes an explicit `matterId`/`customerId` to the write tools (CHOSEN)** — the tool reuses `findByEmail` then lists that customer's matters and returns `{customer, matters[]}`; the write tools require an explicit target.
   - Pros: Kazi never guesses — the human-supervised Claude makes the call and the write tools record an *explicit*, auditable target; reuses `findByEmail` (zero new matching logic); handles zero/one/many uniformly (return what matched, let Claude choose); keeps Kazi's surface small and its reasoning-free posture (ADR-320) intact; the explicit target is what gets audited (defensible trail of *which* matter, chosen deliberately).
   - Cons: requires a round-trip (Claude calls resolve, then file) — acceptable, and Claude can cache; relies on Claude to disambiguate well (but a human supervises).
2. **Server-side auto-file — Kazi picks the matter from the email and files automatically** — `findByEmail`, then if exactly one matter, file it.
   - Pros: one call; no disambiguation step.
   - Cons: Kazi guesses — exactly what the founder forbade; the "one matter" heuristic is wrong for multi-matter clients and for emails that belong to a *different* matter than the obvious one; puts reasoning into the Kazi backend (violates ADR-320's reasoning-free boundary); a wrong auto-file in a legal context is a serious error with no human checkpoint.
3. **Server-side fuzzy match (name/subject/reference similarity ranking)** — Kazi ranks candidates by fuzzy signals and returns a best guess or auto-files the top hit.
   - Pros: catches cases where the sender address isn't a known customer.
   - Cons: fuzzy matching *is* reasoning — it belongs in Claude, not Kazi (ADR-320); ranking heuristics are exactly the kind of model logic the BYOC design keeps out of the backend; `findBySimilarName` exists but using it for auto-linking re-introduces the guessing the founder rejected; false positives file privileged email into the wrong matter.

**Decision**: Add a `resolve_matter_by_email` **read tool** that reuses `CustomerRepository.findByEmail` → the matched customer's matters and returns all candidates `{customer, matters[]}`. The firm's Claude disambiguates (optionally helped by `subjectHint`/`reference` pass-through params it ranks itself) and passes an **explicit** `matterId`/`customerId` to `file_correspondence`/`attach_document`/`propose_task`. Kazi performs **no** server-side auto-filing and **no** fuzzy matching. Zero-match returns `{customer: null, matters: []}`; multi-match returns all matters.

**Rationale**:

1. **Kazi must not guess.** The founder decision and the legal-risk profile both demand a human-in-the-loop link. Returning candidates and recording an explicit target means every filing has a deliberately-chosen, auditable matter — no silent heuristic.
2. **Reasoning stays in Claude (ADR-320).** Fuzzy/ranked matching is model reasoning; putting it in the Kazi backend would violate the BYOC boundary and re-introduce an extraction-shaped responsibility Kazi explicitly declined. `subjectHint`/`reference` are *passed through* for Claude to rank, not matched server-side.
3. **Reuse, no new matching logic.** `findByEmail` already exists; the tool is a thin read over it plus the customer's matter list — zero new search code, consistent with the reuse mandate.
4. **Uniform zero/one/many.** Returning exactly what matched (including empty) is the honest contract: Claude is told when there is no match (and must not file blindly) and sees all matters when there are several. The lowercase/trim normalisation on the lookup matches existing email handling.
5. **Read capability, read audit.** `resolve_matter_by_email` is read-only (`MCP_ACCESS`, `mcp.tool.invoked`), so a read-only MCP user can resolve but still cannot write (ADR-321) — resolution is safe to expose broadly.

**Consequences**:
- Positive: no wrong auto-filing; every link is explicit and audited; zero new matching logic; the reasoning-free Kazi boundary holds; a read-only user can resolve without being able to write.
- Positive: handles multi-matter clients and unknown senders gracefully (Claude decides, or asks the lawyer).
- Negative: a two-step interaction (resolve then file) — minor; Claude can cache the result within a session.
- Negative: link quality depends on Claude's disambiguation (a human supervises, but a careless approval could still file to the wrong matter — mitigated by the explicit-target audit trail and, for Tier-2, the in-Kazi gate review which shows the originating correspondence).
- Related: [ADR-319](ADR-319-inbound-correspondence-domain.md) (the ≥1-non-null linkage the explicit target satisfies), [ADR-320](ADR-320-byoc-ingestion-boundary.md) (reasoning stays in Claude — no server-side matching), [ADR-321](ADR-321-mcp-write-tool-category.md) (resolve is read-capability; writes need `MCP_WRITE`), [ADR-304](ADR-304-mcp-tenant-isolation-capability-gating.md) (capability-gating model reused).
