# Phase 27 Ideation — Document Clauses
**Date**: 2026-02-26

## Lighthouse Domain
- Accounting/bookkeeping firms (unchanged from Phase 26)
- Document clauses are vertical-agnostic — engagement letters, proposals, service agreements all need reusable clause blocks
- Competitive references: Ignition (proposal clauses), PandaDoc (content blocks), Clio (document assembly)

## Decision Rationale
Founder chose to refine the Phase 27-31 roadmap (all had names but no requirements). Swapped E-Signing and Document Clauses order — clauses logically come before signing since you compose the document first, then send for signature.

Key design decisions:
1. **System packs + org clauses** — ship ~12 standard professional-services clauses, orgs can clone/edit and add their own
2. **Per-document clause picker** — at generation time, user sees template's suggested clauses, can toggle/reorder/browse full library. Richer model than template-embedded-only approach
3. **Full Thymeleaf variable support** — clauses render inside the template's context (customer name, project name, org branding, custom fields)
4. **Template suggestions + full library browse** — template suggests defaults, but user can add any clause from the org library at generation time
5. **~12 clauses in standard pack** — generic professional services + engagement letter specific (payment terms, confidentiality, limitation of liability, scope of work, termination, force majeure, dispute resolution, engagement acceptance, fee schedule, document retention, client responsibilities, professional indemnity)

## Key Design Preferences
1. Clause bodies are HTML/Thymeleaf (plain textarea editor, no WYSIWYG for v1)
2. System clauses are read-only — clone to customize (same as template packs)
3. Clauses are snapshotted at generation time (baked into rendered HTML/PDF)
4. Multi-step generation dialog: clause selection → preview → generate
5. No-clause templates skip the picker entirely (backward compatible)
6. TemplateClause join entity with required/optional flag and sort order

## Updated Phase Roadmap
- Phase 25: Online Payment Collection (in progress, 3 slices remaining)
- Phase 26: Invoice Tax Handling (requirements written)
- **Phase 27: Document Clauses** (requirements written)
- Phase 28: E-Signing
- Phase 29: Accounting Sync (Xero + Sage)
- Phase 30: Verification Port + Checklist Auto-Verify
- Phase 31: Platform Self-Billing

## Scope Assessment
Larger phase than recent ones — clause entity + service + pack seeder + template association + generation pipeline changes + 3 frontend surfaces (library management, template clause config, generation dialog picker). Estimate: 6-8 epics, ~15-20 slices.
