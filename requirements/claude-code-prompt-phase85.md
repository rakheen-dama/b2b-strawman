# Phase 85 — `/legal` Vertical Landing Page (GTM Surface)

> **Status: READY for `/architecture` — or, given its size, directly for a single `/epic`.** Scope agreed in `/ideate` (2026-07-13, GTM-collateral session). This phase exists because of the **dual-track site decision**: `kazi.africa`'s homepage stays horizontal ("built for accounting firms, consultancies, and professional services"), but the entire design-partner motion — elevator pitch, LinkedIn posts, exec deck, SEO content — targets SA law firms and needs a destination that speaks their language. Today a managing partner who hears the legal pitch and visits the site finds a generic professional-services tool that never mentions matters, trust accounting, or attorneys. That credibility gap lands exactly at the decision moment.
>
> **Feature-freeze note:** the post-Phase-83 freeze (see `.claude/ideas/gtm-design-partner-plan-2026-07-11.md`) exempts Phase 84 (take-on) and design-partner-driven fixes. This phase is authorized as a **GTM surface, not a product feature**: frontend-only, one static marketing route, zero backend/domain changes. If scope grows beyond that description, stop and re-spec.

## System Context

The marketing site lives in `frontend/app/page.tsx`, composed from section components in `frontend/components/marketing/` (NavBar, HeroSection, BuiltForAfrica, FeaturesSection, AiSection, IntegrationsSection, PricingPreview, CtaSection, Footer). Design system is "Signal Deck" (slate OKLCH palette, teal accent, Sora/IBM Plex Sans/JetBrains Mono via `next/font/google`) — see `frontend/CLAUDE.md`. The hero CTA is `mailto:hello@kazi.africa` (keycloak mode) with a `/request-access` variant.

Copy source-of-truth for this page: `marketing/elevator-pitch.md`, `marketing/exec-deck-outline.md`, and `marketing/seo-strategy.md` (all from the 2026-07-13 ideation). The narrative spine is fixed: **pain → what Kazi is → trust credibility → AI moment → take-on → continuity → pilot CTA.**

## Objective

Ship `kazi.africa/legal`: a public, statically-rendered vertical landing page for South African law firms that (a) converts pitch-warmed managing partners into pilot conversations, and (b) serves as the SEO hub all legal content links to. Success = a partner who heard the elevator pitch lands here and sees their world reflected — matters, fee notes, trust accounting, FICA — with one clear action: book a conversation.

## Constraints & Assumptions

- **Frontend-only. No backend, portal, or packages changes.** No new dependencies unless unavoidable.
- **Reuse the marketing component idiom** — new section components live in `frontend/components/marketing/legal/` (or a naming convention `/architecture` prefers), following the existing RSC-by-default, Signal Deck-styled patterns. Do not fork the design system; this is the same brand, vertical voice.
- **Static/RSC only.** No client-side data fetching; `"use client"` only where an existing pattern demands it (e.g. mobile nav). Page must render fully without JS.
- **Copy comes from the marketing docs, adapted for the web** — do not invent new claims. The honesty rules from `marketing/elevator-pitch.md` apply verbatim: no customer counts, no invented statistics, no feature-count bragging.
- **The horizontal homepage is untouched** except for at most one addition: a link/banner from the homepage to `/legal` ("Running a law firm? →") — `/architecture` decides placement; keep it minimal.
- **Quality gates as always:** `pnpm lint && pnpm build && pnpm test` + prettier `format:check`; verification includes actually loading the page and checking rendered output, metadata, and structured data (PASS means observed).

## Section 1 — Page structure & content

Sections, in the pitch-spine order (each maps to exec-deck slides; copy in `marketing/exec-deck-outline.md`):

1. **Hero** — legal-specific headline (working copy: "Run your firm without the month-end dread"), subhead naming the audience (South African law firms, 2–15 attorneys), CTA pair: "Talk to us" (mailto / request-access, same mechanism as homepage hero) + secondary anchor link to the pilot section. Badge: "Taking on 2–3 design-partner firms".
2. **The three worst days of your month** — fee-note run, trust reconciliation, debtors & FICA. Three cards.
3. **What Kazi is** — the six pillars (matters & time, fee notes, trust accounting, FICA, debtors, client portal), one line each.
4. **Trust accounting you can defend** — approval-gated payments, immutable audit trail, continuous three-way reconciliation, auditor-ready exports.
5. **Your own AI, on your practice** — the four skill moments (fee-note drafting, s86 screening, FICA gap review, email filing) + the human-approval line. Visual: screenshot or short looping demo capture (static image acceptable for v1; assets to `frontend/public/screenshots/legal/`).
6. **Switching is our job** — take-on promise (exports in, white-glove migration, control totals tie to the bank at cutover, take-on report).
7. **Your data is always yours** — the three continuity commitments (full export any time, documented take-off path, exit clause in the pilot agreement).
8. **The design-partner pilot** — offer terms + what we ask + post-pilot pricing anchor (flat firm-level fee, R2,500–R5,000/month, unlimited users). Final CTA.
9. **FAQ** — 5–7 questions drawn from the objection table in `marketing/elevator-pitch.md` (continuity, POPIA/AI, parallel running, pricing, firm size). This section feeds the FAQPage structured data.
10. **Footer** — reuse the existing marketing Footer.

## Section 2 — SEO & metadata

- Route: `frontend/app/legal/page.tsx` (public, outside `(app)`), fully static.
- Next.js Metadata API: unique `title` (e.g. "Kazi — Practice Management for South African Law Firms"), `description`, canonical URL, OpenGraph + Twitter card with a legal-page-specific OG image.
- JSON-LD: `SoftwareApplication` (name, applicationCategory, operatingSystem "Web", offer described qualitatively — no misleading price claims) and `FAQPage` mirroring the rendered FAQ exactly (no invisible-content tricks).
- Verify/add `sitemap.xml` and `robots.txt` coverage for `/` and `/legal` (Next conventions — `app/sitemap.ts` / `app/robots.ts` if none exist).
- Headings hierarchy must be semantic (single `h1`, ordered `h2`s) — this page is the SEO hub per `marketing/seo-strategy.md`.

## Out of Scope

- Blog engine, CMS, or MDX pipeline (SEO strategy publishes via LinkedIn until pilot-funded).
- Any additional statute-explainer static pages (e.g. "s86 explained") — flagged in the SEO strategy as a possible cheap rider; only include if `/architecture` judges it a trivial addition, otherwise defer.
- Rewriting the horizontal homepage, other vertical pages (accounting, consulting), A/B testing, analytics beyond whatever already exists.
- Backend changes of any kind; portal changes; new auth surface.
- Pricing page changes; the pricing anchor appears only in the pilot section copy.

## ADR Topics (only if run through `/architecture`; skippable if going straight to `/epic`)

1. Marketing route organization for vertical pages (`/legal` flat vs. a `(marketing)` route group as more verticals arrive).
2. Structured-data implementation pattern (inline `<script type="application/ld+json">` in RSC vs. a helper).
3. Screenshot/demo-asset pipeline for marketing pages (static PNG now; what replaces it).

## Style & Boundaries

- Follow `frontend/CLAUDE.md` conventions strictly (slate not gray/zinc, teal not indigo, RSC serialization boundary, no `@radix-ui/react-*`).
- One PR is plausible for the whole phase; if split, split by page-sections vs. SEO-infrastructure.
- The reviewer must check rendered copy against `marketing/elevator-pitch.md`'s "Do not say" list — marketing claims are part of the diff.
