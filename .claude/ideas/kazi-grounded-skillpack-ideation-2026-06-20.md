# Kazi-Grounded Skill Pack Ideation — 2026-06-20

**Builds on:** [mcp-plugin-strategy-2026-06-14.md](mcp-plugin-strategy-2026-06-14.md) (Phase C),
[claude-for-legal-sa-integration-2026-05-23.md](claude-for-legal-sa-integration-2026-05-23.md).
**Output:** `../claude-for-legal-sa/project/prd-kazi-grounded-skillpack.md` (spec lives in the legal-sa repo).

## Lighthouse domain
SA small-to-medium law firms. The play: a firm's own Claude grounded in their live Kazi data.

## Decision
"Enhance integration / turn into plugin repo / add skills + tools" resolves to: **build one new
first-party plugin, `kazi-legal-za`, in the existing `claude-for-legal-sa` marketplace** — the
*consumer* of the Phase 78 Kazi MCP server. The server (16 read tools, OAuth ADR-303 auth, POPIA
egress consent, audit) is **shipped but has zero consumers**; the two repos are severed (every
plugin's `.mcp.json` points at Slack/Drive, none at Kazi). This is the unbuilt "Phase C — skill pack
+ bridge."

Options weighed: (a) Kazi-grounded skill pack ✅, (b) deepen the SA overlay only, (c) both. Founder
chose (a) — pure consumer, no Kazi backend changes.

## Scope (founder-selected, all-in)
- **v1 skills (read-only):** fee-note-run, trust-reconciliation (§86), fica-gap-review, matter-brief,
  intake-triage, + connect-kazi onboarding.
- **Extras (all selected):** Claude-for-Legal bridge (make 85 upstream skills Kazi-aware),
  tools/scripts (`kazi-doctor`, grounding linter, statute-freshness, install helper), SA statute
  knowledge bundling per skill, v2 write-back contract **spec-only**.

## Key design preferences
- **Read-only v1.** Claude drafts; human commits back in Kazi by hand. v2 write-back = `propose_*`
  tools that create `AiExecutionGate` (PENDING); approval stays in-product. Don't reinvent.
- **Reuse, don't fork.** Auth = existing OAuth resource flow (ADR-303); never a parallel token model.
- **SA-grounding mandatory.** Every drafting skill cites `jurisdictions/za` (LSSA tariff, LPA §86,
  FICA YAML). Prefer *referencing* the path over copying; symlink+sync fallback if packaging can't
  resolve cross-plugin paths.
- **"PASS means observed"** carried over: skills verified Claude→MCP→audit-log→draft end-to-end.

## Out of scope
Kazi backend changes; any write tool; new research connectors (SAFLII/CCMA — deferred in
`mcp-requirements-za.md`); editing the 13 upstream plugins.

## Build sequence
7 epics: E1 skeleton+connection (plugin.json/.mcp.json/kazi-doctor/connect-kazi/marketplace), E2
knowledge bundling+grounding linter, E3 fee-note, E4 trust-recon, E5 fica-gap, E6 matter-brief+intake,
E7 bridge+README+v2 contract doc.

## Next step
Build happens in `../claude-for-legal-sa` (it's already a plugin marketplace — no `/architecture`,
which is Kazi-entity/ADR-specific). Use that repo's `jurisdiction-expansion` + writing-plans flow, or
hand the PRD to a build agent there.
