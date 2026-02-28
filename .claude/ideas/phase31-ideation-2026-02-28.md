# Phase 31 Ideation — Document System Redesign
**Date**: 2026-02-28

## Trigger
Founder frustrated with document template + clause UX: "horrendous", "all a disaster on UI". Specific pain points:
1. Clauses and docs disconnected — separate pages, separate mental models
2. Seeded (system) clauses not viewable — must clone to read content
3. Thymeleaf HTML as editing surface — "not holding up", "serious limitations"
4. No WYSIWYG — raw HTML editing for professional documents

Also flagged: custom fields UX (Phase 23) saw no UX improvement. Parked for separate phase.

## Decision Rationale
Thymeleaf was the right call for Phase 12's scope (backend PDF generation). But the document system has since grown (clauses, acceptance, heading toward proposals) into a core product surface. Raw HTML editing is now a liability.

Founder explicitly stated: "Without this being really good I'm afraid a key part of the product is slop." Quality and premium feel are non-negotiable.

### Options Considered
1. **Markdown + Handlebars** — simpler, human-readable storage, but WYSIWYG↔MD round-trip loses edge cases
2. **Tiptap WYSIWYG with JSON storage** — full rich editor, lossless round-trip, custom nodes for variables/clauses/tables
3. **Keep HTML, just improve editor** — smallest change but doesn't fix the fundamental authoring problem

**Chose Tiptap JSON** because:
- Lossless round-trip (JSON is the editor's native format)
- Custom nodes perfectly model the domain (variable chips, clause blocks, loop tables)
- Client-side preview possible (no Thymeleaf = no backend dependency for preview)
- Eliminates SSTI attack surface entirely (no expression language)
- Markdown has table/formatting limitations that matter for professional documents

## Key Design Decisions
1. **Tiptap (ProseMirror)** as editor library — MIT, battle-tested, extensible
2. **Tiptap JSON** stored in DB (JSONB) — both templates and clauses
3. **Thymeleaf deleted** (for document rendering; email templates keep theirs)
4. **Clause Library stays** as separate page but with full content visibility everywhere
5. **Template editor unified** — no tabs, clauses inline as blocks
6. **Client-side preview** — instant feedback, backend only for PDF
7. **Clean migration** — platform packs hand-converted, org-custom best-effort + fallback

## Founder Preferences (This Session)
- "First impressions last" repeated — document UX is a revenue surface, must be premium
- Chose rich editor over markdown (wants non-technical users to edit templates)
- Chose unified format (same Tiptap JSON for templates AND clauses)
- Agreed to clean cut (no backward compatibility / dual rendering paths)
- Acknowledged custom fields UX is also broken but parked it — documents are the priority

## Phase Roadmap (Updated)
- Phase 29: Entity Lifecycle & Relationship Integrity (in progress)
- Phase 30: Expenses, Recurring Tasks & Daily Work (spec'd)
- **Phase 31: Document System Redesign** (design approved)
- Phase 32: Proposal → Engagement Pipeline (builds heavily on Phase 31)
- Phase 33: Accounting Sync (Xero + Sage)

## Scope
~7 epics, ~16-20 slices. Medium-large phase. Critical path: editor foundation (frontend) + renderer (backend) in parallel, then fan out to page rewrites.
