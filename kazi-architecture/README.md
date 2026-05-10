# `kazi-architecture/` — Current-State Architecture & Ubiquitous Language

This folder is the **single source of truth** for what Kazi is and how it is built today. It is code-grounded: every architectural claim points at a file, line, identifier, or active ADR. If the doc disagrees with the code, the doc is wrong.

## Read in this order

1. **[`00-overview.md`](./00-overview.md)** — One-page orientation: what Kazi is, who uses it, the headline capabilities, the four deployable units.
2. **[`10-bounded-contexts.md`](./10-bounded-contexts.md)** — The module map. 22 bounded contexts grouped by layer (foundations / domain / money / operations / surfaces / mechanisms / vertical overlays). Read this to understand who owns what and who depends on whom.
3. **[`glossary.md`](./glossary.md)** — Ubiquitous language. ~260 terms, alphabetised, every term anchored to code. Includes a watch-words table for ambiguous product talk.
4. **[`90-adr-index.md`](./90-adr-index.md)** — ADR status overlay. Tells you which of the 278 ADRs are currently in force, which are superseded, and by what.

After those four, dive into:

- **[`20-cross-cutting/`](./20-cross-cutting/)** — How the load-bearing concerns thread through every module: multi-tenancy, auth + RBAC, audit, **multi-vertical** (the load-bearing concern), integration ports, observability, data protection.
- **[`30-modules/`](./30-modules/)** — One file per bounded context. Deep-dive on entities, REST surface, events, key flows, cross-cutting touch-points.
- **[`40-data-model.md`](./40-data-model.md)** — Top-level entity diagram and tenant boundaries. Pointers into module files for detail.
- **[`50-flows/`](./50-flows/)** — Headline user/system journeys (matter-to-cash, customer onboarding, automation trigger-to-action, etc.).
- **[`60-verticals/`](./60-verticals/)** — Per-vertical overlays: legal-za, accounting-za, consulting-za, base. What each layers onto core via packs, terminology, feature gates.
- **[`70-repos/`](./70-repos/)** — Per-repo deployable view (backend, frontend, gateway, portal).
- **[`80-tech-debt-and-gaps.md`](./80-tech-debt-and-gaps.md)** — Risk register: known fragility, drift between code and decisions, architectural inconsistencies, active defects, and recommended next ADRs.
- **[`99-conventions.md`](./99-conventions.md)** — How this folder is maintained: anchor format, section size budgets, update rules.

## Where the historical archive lives

- **`adr/`** (repo root) — All 278 ADRs, unmoved. Status overlay lives in `90-adr-index.md`, not in the ADR files themselves.
- **`architecture/phase*.md`** (repo root) — 67 phase architecture docs, treated as historical input. Module pages may cite a phase doc when it is the clearest record of a decision; otherwise phase docs are background.

## Maintenance principle

Update this folder *as code changes*, not in a quarterly catch-up. Every PR that adds/changes/retires an entity, a REST endpoint, a domain event, or a feature module should also update the relevant module page and glossary entry. The folder rots fast otherwise — that is exactly how `architecture/` got into the state that prompted this rewrite.

See `99-conventions.md` for the discipline that makes this not-rot: anchor format, size budgets, what stays out of module pages.
