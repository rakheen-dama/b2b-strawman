# Phase 81 Ideation — Inbound Correspondence & First Gated MCP Write-Back (Email → Kazi) — 2026-06-21

**Output:** `requirements/claude-code-prompt-phase81.md` (READY for `/architecture`).
**Type:** opens "Phase D — gated write-back over MCP" (reserved in [mcp-plugin-strategy-2026-06-14.md](mcp-plugin-strategy-2026-06-14.md)),
using email-filing as the lighthouse use case. Builds on Phase 78 (MCP read server) + the `kazi-legal-za` skillpack precedent.

## Lighthouse domain
SA small-to-medium law firms. The play: a lawyer's own Claude (Gmail connector + Kazi MCP) files an email into the
right matter without copy-paste — and does it safely.

## Decision path
Args: "claude-code email to kazi linking, extract." Founder corrected early: **MCP layer is not missing — built in Phase 78.**
Scout confirmed: MCP server = ~10–12 **read-only** tools + enablement/POPIA consent/OAuth. Genuinely **absent**: inbound
correspondence storage (only outbound notification email exists) and any **write** path over MCP. So this is the unbuilt
write-back chapter, not a new MCP server and not a Gmail integration.

## Key design preferences (locked, via 3 AskUserQuestion forks)
- **BYOC ingestion** — Kazi does NOT integrate Gmail. The firm's Claude reads the mailbox + extracts; Kazi only receives
  structured input over MCP write tools. **No Gmail OAuth/IMAP/webhook/poll, no LLM call in the backend.** Preserves
  "firm pays the tokens" + small surface. (Server-side Gmail+in-product extraction was rejected.)
- **Tiered write safety** — Tier-1 (direct, audited): `file_correspondence` + `attach_document` (recording, not acting).
  Tier-2 (gated via existing `AiExecutionGate`, approved in-Kazi): tasks/deadlines. Enforced **server-side** (no direct
  path exists for a Tier-2 action).
- **Wire the seam + 1 proof** — v1 builds Tier-1 capture AND exposes `AiExecutionGate` creation over MCP, validated by
  exactly **one** Tier-2 tool `propose_task` (correspondence → PENDING gate → attorney approves in Kazi → task created).
  Proves the full write-back loop now; bulk extraction is v2.
- **v1 extract scope** = correspondence record + attachments-as-documents (Tier-1) + the single `propose_task` proof.
  New contacts/parties, bulk task/deadline extraction, threading = **v2**.
- **Linking is Claude's job** — a `resolve_matter_by_email` read tool (reuses `CustomerRepository.findByEmail`) returns
  candidates; Claude disambiguates and passes an **explicit** matterId/customerId. Kazi never auto-files on a guess.

## Reuse vs net-new
Reuse: Phase 78 MCP pipeline (auth/enablement/consent/audit), `AiExecutionGate`+`GateActionExecutor`+approval UI,
`DocumentService`, `findByEmail`, `TaskService.createTask`, activity/audit registries. Net-new: `Correspondence` entity
(+ idempotency key = messageId), MCP **write** tool category + a new **write capability** + `mcp.write.*` audit family,
gate-creation-over-MCP, V{next} migration (latest V129 — don't hardcode V130).

## 6 epics (approx, for /breakdown)
1. `Correspondence` entity + migration + idempotency + Document↔correspondence link + activity/audit registration
2. `file_correspondence` MCP write tool (Tier-1) + new MCP write capability + `mcp.write.*` audit
3. `attach_document` MCP write tool (Tier-1) — presigned-URL via `DocumentService` (decide byte transfer in /architecture)
4. `resolve_matter_by_email` read tool (disambiguation helper)
5. Gate-over-MCP: `propose_task` (Tier-2) → `AiExecutionGate` PENDING; executor branch for "create task from correspondence"
6. Frontend (lean): correspondence list on matter detail + gate shows originating correspondence; QA capstone (observed)

## ADRs: 319 (correspondence domain) · 320 (BYOC ingestion boundary) · 321 (MCP write category + capability) ·
322 (tiered safety + gate-over-MCP) · 323 (email→matter linking)

## Domain notes
- **Repo split:** this spec = Kazi backend only. The **consumer skill** (Gmail-read → reason → call Kazi tools) ships in
  `../claude-for-legal-sa` (its plugin/marketplace flow, not `/architecture`) — like `kazi-legal-za`. Next step after build.
- Hard boundaries to verify at review: no Gmail/IMAP/webhook/poll, no backend LLM call, no Tier-2 bypass of the gate,
  read-only MCP user can't write, tenant-isolation test present.
- **Lesson reinforced (again):** scout before writing the spec — founder pre-empted a wrong assumption ("MCP missing").
  Phase 78's read server is the foundation this extends, not rebuilds.
- POPIA note: Phase 78 gates *reads* behind egress consent; writes move data *into* Kazi (lower-risk direction) but still
  enablement-gated + audited as writes — decide in /architecture whether writes need their own consent flag.
