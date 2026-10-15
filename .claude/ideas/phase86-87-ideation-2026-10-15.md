# Phase 86 + 87 Ideation — Subtraction & Legal-Native Shell (2026-10-15)

Builds on `gtm-design-partner-plan-2026-07-11.md` (run-as-SaaS, feature freeze after 83 except take-on) and
`phase84-ideation-2026-07-11.md` (take-on spec). Founder returned after ~3 months away and asked for an
honest "what would you remove" assessment.

## Lighthouse domain
Unchanged: SA small-to-medium law firms (legal-za). The horizontal multi-vertical platform story is **over**.

## Decision rationale
- Assessment: the legal vertical (~20k backend lines, trust ≈10k) is coherent; the cost sits in the
  horizontal suite built for agencies/accountants (capacity, retainers, schedules, proposals, CRM), a
  second AI surface (in-app chat + specialists, ≈7.6k lines, parallel to MCP), PayFast recurring tenant
  billing, two non-legal profiles, dead surfaces (in-app portal copy, Thymeleaf harness, docs site).
- Founder: "drop accounting and agency — push them out into a different fork so we don't lose it all;
  it's time to simplify; we can design a better UI without a generic structure that fits everything."
- **Archive, don't fork**: tag `horizontal-platform-final` + mirrored read-only repo. Maintained second
  codebase explicitly rejected.
- **Zero-tenant window**: deletions are real deletions and the 133 tenant + 25 global migrations get
  squashed to `V1` baselines **inside Phase 86** (founder chose not to split the squash out).
- **Dependency map corrected two early calls**: clauses STAY (document pipeline imports it 6×, legal-za
  ships a conveyancing clause pack); proposals need a replacement path (engagement letter = generated
  document + Phase 28 acceptance) made an acceptance test, not an assumption.
- **Borderline modules kept visible as legal features** (not hidden): Xero (firm's business books),
  pay-now on fee notes, acceptance (mandates). "Hidden" only earns its keep when visibility implies a
  support promise — e.g. automation builder, deal pipeline.
- **Rename depth**: "rename the API, keep the tables" — project→matter and customer→client on paths,
  DTOs, JSON, TS; `invoice` stays on the wire (VAT term), labels say Fee Note; JPA/tables unchanged.
  IntelliJ MCP `rename_refactoring` is available for Java symbols (one symbol per call, no move/extract).
- **Sequencing**: 86 subtract → 87 legal shell → 84 take-on. Pilots wait two phases; no pilot letter is
  signed yet so this is acceptable. Phase 85 (`/legal` page) can run any time.

## Key design preferences
- Terminology layer deleted outright in 87; Tasks stay "Tasks" (drop "Action Item").
- Nav IA target: Today / Matters / Clients / Fees / Trust / Firm; one Diary replaces three calendars;
  AI has no zone (lives where used). Settings: five families, ≤25 routes from ≈43.
- Re-shell, don't rewrite: reuse Phase 73/77 detail pages, trust pages, `@b2mash/ui`.
- MCP contract untouched (kazi-legal-za plugin). Audit entityType strings untouched.
- Gateway BFF and Phase 75 scalability: frozen, not removed (solo-operator continuity risk noted for
  the one-pager, not for code).

## Phase roadmap
86 (subtraction + squash) → 87 (legal-native shell + wire rename) → 84 (take-on, re-based onto 87 shell)
→ design-partner-driven fixes only. 85 opportunistic.

## Domain notes
- `Retainer→Mandate` and `Proposal→Engagement Letter` terminology existed; after 86 both words attach to
  the acceptance flow. Expenses vs legal disbursements are two tabs for one concept — 87 resolves.
- `/api/matters/{projectId}/closure` already exists: the wire vocabulary is mixed today, which is the
  symptom 87 fixes.
- Outputs: `requirements/claude-code-prompt-phase86.md`, `requirements/claude-code-prompt-phase87.md`.
