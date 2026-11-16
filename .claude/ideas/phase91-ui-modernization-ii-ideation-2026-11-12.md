# Phase 91 Ideation — UI Modernization II (2026-11-12)

Output: `requirements/claude-code-prompt-phase91.md`. Builds on `phase90-ui-modernization-ideation-2026-11-11.md`.

## Context
- Phase 90 had shipped 637–641 (tokens, shell, primitives, dashboard, matters); 642 (clients), 643 (trust/profitability) and 644 (exit gate) were still open.
- Founder pain points: settings and the rest of the app never got the shadcn treatment. Kinks: the "No tasks assigned" empty state and "set up custom fields" take too much space. Black rail + white canvas in light mode is not liked.

## Decision rationale
- **Phase 91, sequenced after 644 closes.** 644's dark-mode debt list and adoption backlog are inputs; starting mid-90 blurs the exit metrics and collides with 642/643 files.
- **Scope = daily-use + settings, light pass on the rest.** Alternatives were a whole-app sweep (12+ epics, collision-prone) and settings + kinks only (too small). Fallback: if the breakdown exceeds 20 slices, split Sections 8–9 into Phase 92.
- **Settings = settings shell + section cards**, not a restyle in place. Keep Phase 87's 5 families / 31 `?tab=` tabs; add a mobile settings nav (the sidebar is `hidden` below md).
- **Empty custom fields = compact one-liner for everyone.** Rejected: hiding it for members, which would be a visibility/product change.
- **Observe-first kinks register**, approved by the founder before surface slices start.
- **Light-mode colour scheme gets revisited.** This reverses the Phase 90 arch ruling (§15 line 234) that kept the slate-950 light rail as the identity mark. It is design-first: 3 candidates prototyped on 4 real pages (A light rail + inset canvas, recommended; B tinted rail; C deep-teal rail), the founder picks, and a new ADR supersedes the ruling. Dark mode keeps its dark rail.

## Key facts (save re-scouting)
- EmptyState has default/compact/inline variants, but only 8 of 63 call sites pass `variant`. That is the root of the "too much space" class. `CustomFieldSection`'s no-fields state renders in fee-note detail, the client and matter Fields tabs and the **task detail sheet**.
- `PageHeader` is used by 3 of 57 org pages. Direct table files: settings 11, trust 7, invoices 4.
- Light tokens: `--sidebar` slate-950 (`frontend/app/globals.css:29`) and `--primary` slate-950 (black buttons). **The portal imports the shared globals.css**, so the scheme change must be scoped deliberately.
- White on teal-600 is about 3.7:1 and fails AA; teal-700 is needed for primary buttons if they go teal.

## Design preferences (carry forward)
- Calmer light mode over a high-contrast black slab; the founder is open to brand-forward teal.
- Still: copy, labels, routes and IA frozen; behaviour parity; browser OBS at 1024/1440 × light/dark is the merge gate.
- Small sections must not render page-sized empty states.

## Parked
- Full redesign of the light-pass pages (compliance, conflict check, legal registers, team, AI, platform-admin).
- Portal restyle (unless chosen in the scheme ADR), theme customizer, component-folder rename.
