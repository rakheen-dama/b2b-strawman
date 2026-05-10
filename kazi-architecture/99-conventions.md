# 99 — Conventions for Maintaining `kazi-architecture/`

This folder rots if it is treated as a one-shot document drop. These conventions exist so it can stay current with the same effort the codebase already spends on `CLAUDE.md` files.

## Anchor format

Every architectural claim has a code anchor. Anchors look like:

- `→ backend/.../customer/Customer.java:34` — entity anchor (preferred when an entity exists)
- `→ frontend/lib/types/invoice.ts:40` — frontend type anchor (when no backend entity)
- `→ ADR-064` — decision anchor (when the claim is a decision, not a structural fact)
- `→ phase49-vertical-architecture` — historical anchor (when the cleanest record is a phase doc; sparingly)

Rules:
1. Backend entity anchor wins over frontend type anchor when both exist.
2. Path is repo-relative (no leading `/Users/...`).
3. Line numbers may drift — that is acceptable. The next reader greps if the line is wrong; we don't add tooling to re-anchor automatically.
4. Prose-only claims are forbidden in module pages and cross-cutting pages. The `00-overview.md` and `10-bounded-contexts.md` may have one or two general-orientation sentences without anchors, but only at the top.

## Section size budgets (hard limits)

| Section type | Budget | Reason |
|---|---|---|
| Module page (`30-modules/<slug>.md`) | ≤ 600 lines | If a module page exceeds 600 lines, the bounded context is doing too much — split it. |
| Cross-cutting page (`20-cross-cutting/*.md`) | ≤ 400 lines | These are syntheses; over-budget means too many concerns are being conflated. |
| Flow page (`50-flows/*.md`) | ≤ 200 lines | Flows are narrative + sequence; over-budget means it is a *use case study*, not a flow. |
| Vertical overlay (`60-verticals/*.md`) | ≤ 500 lines | Each vertical's specifics — not its full feature set. |
| Repo page (`70-repos/*.md`) | ≤ 300 lines | Repo pages are entrypoints, not duplicates of module pages. |
| Glossary | n/a | Single file, ~260+ rows. Length is fine; it's a lookup. |
| Bounded-contexts (`10-bounded-contexts.md`) | ≤ 1500 lines | The map, not the territory. |

When a budget is hit, refactor first; don't bend the rule.

## What goes in a module page

Required sections, in order:

1. **Purpose** — one paragraph.
2. **Entities owned** — bullet list with anchors.
3. **REST surface** — endpoint paths grouped by controller, anchored.
4. **Frontend pages / components** — pages and key components, anchored.
5. **Domain events** — emitted and consumed, with cross-links to publishers/consumers.
6. **Cross-cutting touchpoints** — multi-tenancy, auth, audit, feature gating, vertical relevance.
7. **Vertical specifics** — terminology overrides, packs that apply, module-gate slug.
8. **Active ADRs** — list with one-line context each.
9. **Key flows** — pointers into `50-flows/` (or short inline mini-flows ≤ 30 lines).
10. **Open questions / known fragility** — short, one-bullet-per-issue.

Sections may be empty (e.g. a module with no scheduled jobs), in which case write `_None._` — explicit absence is signal.

## What stays OUT of module pages

- Vertical-specific *content*. "Legal kyc form fields are X, Y, Z" goes in `60-verticals/legal-za.md`, not in the customer-lifecycle module page. The *mechanism* (custom-field packs scoped per vertical) goes in the module page; the *list* does not.
- Future-state proposals. This folder captures *current* state. Future architecture goes in a new ADR + a phase doc.
- Per-component frontend documentation. The module page lists pages and key components; component-level detail belongs in code (JSDoc, in-file comments).
- Tutorial-style explanations of "how to use feature X". That is product documentation, not architecture.
- Long historical narratives. The point of `90-adr-index.md` and the phase docs is that history lives elsewhere.

## ADR handling

- All 278 ADRs in `adr/` are **unmoved**. Status lives only in `90-adr-index.md`.
- New ADRs continue to land in `adr/ADR-XXX-<slug>.md`. When merged, update `90-adr-index.md` to add the new ADR to its topic cluster (Active) and mark any ADRs it supersedes as Superseded with a `Superseded by ADR-XXX` reference.
- Module pages link only to **Active** ADRs. If a module page references an ADR that has since been superseded, remove the reference and replace with the supersedor.

## Phase-doc handling

- Phase docs (`architecture/phase*.md`) are historical input. Module pages cite a phase doc only when it is the clearest record of a decision and no Active ADR captures it.
- The folder `architecture/` is **not modified** by this work. It stays as-is.

## Glossary discipline

- Every term in the glossary has a code anchor or is explicitly marked `(no code anchor — gap)`.
- New terms enter the glossary only when they enter the code. No invented vocabulary.
- Vertical terminology overrides are surfaced in the Notes column on the canonical term, not as separate entries (e.g. "Project" is the canonical term; legal-vertical-UI rename to "Matter" is a Note).
- Watch-words (ambiguous product talk → canonical term) live at the bottom of `glossary.md`.

## Update cadence

Update this folder **in the same PR that changes the code** for non-trivial structural changes:

- New entity / REST endpoint / domain event / feature module / vertical pack → update the relevant module page + glossary in the same PR.
- New ADR → update `90-adr-index.md` in the same PR as the ADR commit.
- Renaming an entity or breaking a REST contract → update the glossary and the module page; add a watch-word for the retired name if it had any product circulation.

Trivial changes (typo, JSDoc tweak, internal refactor that does not change the seams) do **not** require an architecture update.

## Self-review on every PR that touches `kazi-architecture/`

Before merging, scan the changed file(s) for:

1. Anchors point to real lines (or close enough).
2. Section under budget.
3. No future-state language ("we should...", "later we will...") — those go in ADRs/phase docs, not here.
4. No vertical-specific content in core pages, no core mechanism explanations buried in vertical pages.
5. Glossary cross-checks: any new term in the page is in the glossary; any term used differs from glossary canonical wording is fixed (or the glossary is updated, with anchor).

## Where this discipline is enforced

It isn't, automatically. There is no CI check on these files. The discipline is human + agent + reviewer. The convention exists so reviewers know what to push back on, and so agents have a written rule to follow.

If/when this folder rots faster than expected, two interventions are available: (a) a CI check that diffs `kazi-architecture/` against the changed code paths and warns on PRs that change one without the other; (b) a slash-command (`/sync-architecture`) that re-runs the discovery agents on a sub-tree and surfaces drift. Neither is built today.
