# GTM Collateral Ideation — Pitches, Decks, SEO (2026-07-13)

Builds on `gtm-design-partner-plan-2026-07-11.md` (run-as-SaaS, 2–3 pilot firms this quarter) and
`phase84-ideation-2026-07-11.md` (take-on build). This session produced the collateral stack, not a product phase.

## Decision rationale

- Founder asked for: elevator pitch, LinkedIn pitch, exec presentation, tech presentation, SEO.
- Governing principle agreed: **one narrative, five containers** — everything projects the GTM plan's
  pitch spine (pain → AI moment → take-on close → continuity). No artifact invents a new story.
- Priority: elevator pitch + exec deck (serve bookable pilot meetings) > LinkedIn (warms same firms) >
  tech deck > SEO (channel #4, keyword strategy now / content later).
- **Site tension surfaced and resolved:** kazi.africa hero is horizontal ("accounting firms, consultancies,
  professional services") while GTM is vertical-legal. Founder chose **dual-track site**: homepage stays
  horizontal; new `/legal` landing page carries the vertical message and is the destination for all
  pitches + SEO. Specced as **Phase 85** (frontend-only; explicit feature-freeze exemption as GTM surface).

## Key founder decisions

- Exec deck audience: **pilot-firm managing partners** (not acquirers, not auditors) — closes on the pilot letter.
- Tech deck audience: **developers / collaborators / first hire** — the agent-fleet build-process story;
  this is also where the horizontal fork thesis lives (kept out of firm-facing materials).
- LinkedIn: **founder-personal identity**; 4-post launch month (pain, AI demo w/ screen recording,
  switching-cost/take-on, honest continuity), 1/week, objections from comments feed the GTM plan verbatim.
- Kazi site is **live at kazi.africa** (hello@kazi.africa CTA) — SEO is real, all content targets /legal,
  never a subdomain. Blog engine deliberately deferred; publish via LinkedIn articles until pilot-funded.

## Honesty guardrails (baked into every artifact)

No customer counts (there are none), no invented pain statistics, no feature/module/phase-count bragging
to firms (reads as risk), no incumbent-bashing. Build numbers (84 phases, 1,500+ PRs, ~285 ADRs) are
fair game in the tech deck only — refresh from git before presenting.

## Outputs

- `marketing/` — elevator-pitch.md, linkedin-pitch.md, exec-deck-outline.md, tech-deck-outline.md,
  seo-strategy.md, README.md, decks/exec-deck.html, decks/tech-deck.html (presentable, ←/→ nav, P=print).
- `requirements/claude-code-prompt-phase85.md` — /legal page; small enough for direct /epic, ADRs optional.

## Open GTM gates (unchanged from 2026-07-11 plan, still non-code)

Pilot letter template · continuity one-pager (exec deck slide 7 is its skeleton) · attorney/auditor
trust-module sign-off · pricing confirmation (R2.5–5k/month anchor used in all collateral).

## Review trigger

Same as GTM plan: first pilot signed or first refusal. Also: refresh deck numbers before any presentation.
