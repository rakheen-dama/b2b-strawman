# Phase 12 Ideation — Document Templates & Generation
**Date**: 2026-02-15

## Lighthouse Domain
- Same as Phase 11: SA small-to-medium law firms (2-20 fee earners)
- Document generation is high-value for legal: engagement letters, powers of attorney, court filing cover sheets
- Pattern universal across services verticals (SOWs for agencies, engagement letters for accountants)

## Decision Rationale
- Phase 12 was already decided in the Phase 11 ideation session (2026-02-14)
- This session refined the design details, not the sequencing

**Key design decisions made:**
1. **Thymeleaf templates** — reuse the existing engine from invoice HTML preview (Phase 10, 85B)
2. **PDF required from day one** — OpenHTMLToPDF (pure Java, no headless Chrome)
3. **Rich rendering context** — templates reference the full entity graph (project + customer + org + custom fields + members), not limited to a single entity type. Primary entity type determines where the "Generate" button lives.
4. **Two-layer template model** — platform-shipped (template packs, same pattern as field packs) + org-customizable (clone and edit)
5. **E-signing parked** — future phase, likely part of an "Org Integrations" layer (bring-your-own API key for DocuSign, Payfast, Anthropic, etc.)

## Key Design Preferences (from founder)
1. **Non-technical users** — org admins will need to edit templates but they're not developers. v1 uses HTML editor (acceptable for admin), future WYSIWYG editor for broader adoption.
2. **AI template creation parked** — upload a Word doc, AI converts to template. Comes after the base template system exists.
3. **Org branding matters** — logo upload, brand color, footer text on OrgSettings. Documents should look professional with minimal effort.
4. **Tier gating is a config concern, not architecture** — template customization can be gated to Premier tier later, but the architecture doesn't change. Don't over-engineer for tiers.
5. **"Bring your own API key" integrations framework** — a horizontal concern for a future phase. Covers DocuSign (e-signing), Payfast (payments), Anthropic (AI features), Xero/QuickBooks (accounting). Needs secure per-tenant credential storage.

## Phase Roadmap (updated)
- Phase 10: Invoicing (nearly complete — 2 slices remaining)
- Phase 11: Tags, Custom Fields & Views (planned, not started)
- Phase 12: Document Templates & Generation (requirements written)
- Phase 13+: Candidates — Org Integrations (BYOAK), Customer Portal Frontend, AI Invoice Features, Recurring Work/Retainers, Reporting & Export

## Architecture Notes
- OpenHTMLToPDF supports CSS 2.1 + some CSS3 — no flexbox/grid in PDFs. Templates must use tables for layout.
- Template content stored in DB (not S3) — tenant-scoped, transactional consistency, containerization-friendly.
- Logo stored in S3, resolved to pre-signed URL at render time.
- GeneratedDocument entity tracks every generation with context snapshot for audit.
- Template packs: classpath resources under `src/main/resources/template-packs/`, same seeding pattern as field packs.
