# Phase 82 Ideation — Correspondence Read-Back + Consumer Skills (after Phase 81) — 2026-06-27

**Args:** "expand claude-for-legal-sa skills for kazi integration after phase 81."
**Two outputs (two repos):**
- `requirements/claude-code-prompt-phase82.md` (Kazi backend — READY for `/architecture`)
- `../claude-for-legal-sa/project/prd-correspondence-skills.md` (consumer skills — plugin flow)

## Lighthouse domain
SA small-to-medium law firms. Phase 81 shipped the *server* write-back (file_correspondence,
attach_document, propose_task, resolve_matter_by_email) but the *consumer* `kazi-legal-za` plugin still
has zero skills that use it. This expansion builds the consumer side + the read-back the server lacks.

## Decision path (forks resolved)
1. **Scope** — founder chose "Correspondence suite": `file-email` + `correspondence-digest` (over
   lighthouse-only or weaving propose_task into existing skills). The suite deliberately takes on a
   backend dependency.
2. **Read depth / Gmail-lock challenge** — founder pushed back: "if it only works with Gmail it may not
   be worth doing." Reframed: the lighthouse is **NOT Gmail-locked** — Kazi write tools take structured
   input; mail source = connector (bulk) OR paste/forward (any provider). The Gmail-lock only appeared
   in my Option B (re-hydrate body from Gmail). **Killed Option B.** Body read-back instead comes from
   Kazi (`get_correspondence`) — provider-neutral, and it's the *same* read-egress consent posture as
   `get_matter`/`get_client`, NOT a new POPIA decision (I'd over-weighted it). Founder: "file-email + digest."
3. **propose_task reach** — tight: only the 2 new skills use it; existing 5 stay read-only/draft-only.

## What got specced
- **Backend Phase 82 (small, ~1 epic/2 slices):** `list_correspondence` (metadata, reuses
  `CorrespondenceService.listByProject/listByCustomer` — already exist) + `get_correspondence` (body;
  one new service method + detail DTO). No new domain/table/migration/write. ADR-324.
- **Consumer (kazi-legal-za):** `file-email` (Phase 81 tools, ships first, zero dep, closes the live-OAuth
  GA gate) + `correspondence-digest` (Phase 82 tools, blocked until they ship).

## Key design preferences (locked)
- Provider-neutral framing mandatory — skill copy must NOT assume Gmail; paste/forward is the floor.
- Gate UX honesty: `file_correspondence`/`attach_document` = "filed"; `propose_task` = "proposed, approve
  in Kazi" — never "created." Idempotent re-file = success ("already filed"), not error.
- Trust stays out of every write path (unchanged hard line).
- The old `v2-write-back-contract.md` is **partly superseded** — Phase 81 built `propose_task`, NOT the
  four `propose_fee_note`/`propose_kyc_request`/`propose_matter_update`/`propose_intake_decision` it
  sketched. Those tools don't exist; no skill may call them.

## Grounded facts (verified 2026-06-27, don't re-derive)
- `Correspondence` persists `bodyText` AND `bodyHtml`, `messageId`, `threadKey`, to/cc, sentAt/receivedAt.
- `CorrespondenceListResponse` = (id, subject, fromAddress, receivedAt, attachmentCount, direction) — no body/messageId.
- `listByProject`/`listByCustomer` exist (raw); `listForProject`/`listForCustomer` add access-check + clamp.
  No single-with-body read method exists yet (the one net-new backend method).
- `McpReadOnlyRegistryTest`: `list_`/`get_` names match the read-verb pattern → NO `READ_NAME_EXEMPTIONS`
  edit needed (unlike `resolve_matter_by_email`), but expect catalogue-count assertions to bump.

## Phase roadmap (emerging)
- Phase 82 (this) → read-back tools. Then build consumer skills in the fork.
- v2 (future Kazi phase, separate ideate): the other `propose_*` tools (fee-note/kyc/etc.) if demand.

## Next steps
1. `/architecture requirements/claude-code-prompt-phase82.md` → ADR-324, then `/breakdown 82`.
2. Build `file-email` in `../claude-for-legal-sa` (no dep). `correspondence-digest` after Phase 82 ships.
Builds on [phase81-ideation-2026-06-21.md], [mcp-plugin-strategy-2026-06-14.md], [kazi-grounded-skillpack-ideation-2026-06-20.md].
