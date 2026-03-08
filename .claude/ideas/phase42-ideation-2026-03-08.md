# Phase 42 Ideation — Word Template Pipeline
**Date**: 2026-03-08

## Lighthouse Domain
Universal across all verticals. Every professional services firm has existing Word templates (engagement letters, contracts, compliance forms, court documents) that they've refined over years. Particularly critical for law firms (court-standard formats) and accounting firms (audit report templates with firm branding). The Tiptap editor is good for creating new structured documents but doesn't serve firms that want to bring their existing templates.

## Decision Rationale
Conversation started as a competitive analysis against AJS (SA legal practice management). AJS has MS Office integration — this surfaced the gap. Founder identified that the current Tiptap + custom fields document UX is "awkward and gap-ridden." Rather than only investing in fixing the WYSIWYG editor, the hybrid approach (Option D) was chosen:

1. **Keep Tiptap** for simple/structured documents where the variable system works well
2. **Add Word upload pipeline** for firms with existing templates — upload `.docx`, auto-discover merge fields, generate filled documents

This is strategically sound because it splits the document problem into two clean concerns and takes pressure off needing to anticipate every template need in the WYSIWYG editor.

### Key Design Choices
1. **Same variable syntax** — `{{entity.field}}` in Word templates, resolved by the same `TemplateContextBuilder` pipeline
2. **Apache POI (XWPF)** for `.docx` processing — Java-native, mature, handles the critical split-run problem
3. **Format discriminator on DocumentTemplate** — single entity with `format` field (TIPTAP/DOCX), not separate entities
4. **PDF conversion optional** — LibreOffice headless preferred, docx4j fallback, graceful skip if unavailable
5. **No in-app editing** — the platform is a merge engine for Word templates, not an editor replacement
6. **Field discovery on upload** — parse `.docx` XML immediately, validate against VariableMetadataRegistry, show results

## Founder Preferences (Confirmed)
- Hybrid approach (Tiptap + Word pipeline) over replacing one with the other
- "Scalable and takes pressure off" — key motivator
- Not considering AJS as a partner or acquisition target — pure competitive displacement strategy

## Phase Roadmap (Updated)
- Phase 40: Bulk Billing & Batch Operations (planned)
- Phase 41: Organisation Roles & Capability-Based Permissions (spec written)
- **Phase 42: Word Template Pipeline** (spec written)
- Phase 43: Reporting & Data Export (candidate)

## Competitive Context
- AJS comparison revealed DocTeams is on par or ahead in most areas (projects, time, billing, profitability, audit, notifications)
- AJS's unique advantages: trust accounting (legal-only, massive gap), business accounting (vertical-specific, integrate don't build), bank feeds (integration work), mobile app
- Trust accounting deliberately not pursued — 3-5 phases of legal-only work
- Accounting sync (Xero/Sage) pushed to late roadmap — integration, not capability

## Estimated Scope
~4-5 epics, ~10 slices. New service: `DocxMergeService`. Extends `DocumentTemplate` entity with format discriminator. Reuses all existing context builders and S3 infrastructure. The hardest part is split-run handling in Apache POI.
