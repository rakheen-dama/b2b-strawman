# Phase 18 Ideation — Customer Portal Frontend
**Date**: 2026-02-20

## Lighthouse Domain
- SA small-to-medium law firms (unchanged)
- Portal is vertical-agnostic — every professional services vertical needs client self-service

## Decision Rationale
Founder selected Customer Portal Frontend over Reporting & Export and Integrations (BYOAK). Key reasoning:
1. Phase 7 backend is fully built — magic links, read-model, portal APIs. The frontend is the missing piece.
2. Revenue engine (Phases 8/10/17) is nearly complete — clients have lots of data to see but no way to see it.
3. Reduces admin burden for firms: less emailing PDFs, less "can you resend that invoice?"
4. High retention impact — clients who use the portal are stickier.

**Market research** confirmed: all major competitors (Clio Connect, Karbon, Accelo, Scoro) use responsive web portals, not native mobile apps. Magic link auth is the industry norm for client portals.

## Key Design Preferences (from founder)
1. **Separate Next.js app** (not embedded in main frontend or Thymeleaf) — design system consistency, rich interactivity
2. **Shared Shadcn, different layout** — same component library, but simplified client-focused shell (no sidebar, minimal header nav, org branding)
3. **Start minimal, extend later** — v1 is view + comment + download. Approvals, uploads, payments deferred.
4. **Include infrastructure** — scaffolding, Docker, shared config, auth middleware all in-scope

## Phase Roadmap (updated)
- Phase 17: Retainer Agreements & Billing (in progress, ~60% done)
- Phase 18: Customer Portal Frontend (requirements written)
- Phase 19+: Candidates — Reporting & Export, Integrations (BYOAK), Resource Planning

## Architecture Notes
- **New app**: `portal/` — Next.js 16, React 19, Tailwind v4, Shadcn UI
- **Auth**: Magic link → portal JWT (1hr TTL) → localStorage → API client
- **Backend additions**: Invoice sync to read-model (portal_invoices, portal_invoice_lines), task sync (portal_tasks), public branding endpoint
- **Pages**: Login, Projects (home), Project Detail, Invoices, Invoice Detail, Profile
- **Org ID strategy**: Derived from JWT (not URL path or subdomain) — simplest infra
- **Invoice visibility**: Only SENT+ status invoices sync to read-model (drafts are internal)
- **No new tenant schema tables** — only portal read-model schema additions
- **Responsive web, not mobile-first** — matches industry standard for client portals
