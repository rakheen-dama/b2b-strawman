# Kazi — Go-to-Market: Design-Partner Plan (2026-07-11)

**Strategic decision (from /ideate 2026-07-09 → 2026-07-11):** Run Kazi as a SaaS first. A sale to a strategic
(LexisNexis, AJS, Lexpro) is not rejected — it is *sequenced*: acquirers pay for retained customers and growth,
not pre-revenue codebases. 10–30 paying firms with low churn makes Kazi an acquisition; today it is a demo.
Running it is the only path that preserves the sell option at a real multiple.

## The next milestone is a firm, not a phase

Target: **2–3 design-partner firms live within one quarter.** Founder has 1–2 firms in mind plus
domain-expert contacts who can source more. Everything below serves that milestone.

## Positioning (pitch in this order)

1. **Pain first** — month-end fee runs, trust reconciliation dread, chasing debtors, FICA admin.
   Do NOT lead with feature breadth; 40 modules reads as risk to a small firm, not value.
2. **The "from the future" moment** — the AI layer: point your own Claude at your practice
   (MCP + kazi-legal-za skills: file-email, fee-note-run, trust-reconciliation, FICA gap review).
   No SA incumbent has this. It gets the meeting.
3. **Close on take-on** — "we move your data for you" (clients, matters, debtor balances, trust
   ledger opening balances). Phase 84 builds the tooling; founder white-gloves the first imports.
4. **Continuity answer ready** — solo-operator risk is the unspoken objection for trust books:
   data export any time (POPIA/DSAR export already built), documented take-off path, honest SLA.

## Pilot offer (put it in writing)

- 2–3 firms, free or near-free for 6 months.
- Founder personally performs the take-on and onboarding.
- In exchange: weekly 30-min feedback call + permission to use the firm as a reference at GA.
- Explicit exit clause: full data export if they leave. Reduces perceived risk, increases signings.

## Channels (ranked by expected yield)

1. **Founder's direct firm contacts** — the 1–2 firms in mind. Start this week; take-on tooling
   need not be finished to sign a pilot letter.
2. **Domain experts** — as advisors/introducers; consider a referral arrangement.
3. **Trust-account auditors** — they inspect every firm annually, see the operational mess, and are
   trusted advisors. One or two auditor relationships beat any ad spend. Ask the domain experts for intros.
4. **Content/SEO (the blog series, repurposed)** — in service of Kazi, not as a separate business:
   "LPA s86 explained", "what your trust audit checks", "FICA for small firms". Park standalone monetization.
5. **Later (post design-partners):** LPC-adjacent events, attorney association newsletters.

## Non-code gates (these block GA, not code)

- [ ] **Attorney/legal-auditor sign-off on the trust module** (open GA gate from kazi-legal-za work).
      An acquirer would diligence this too — it serves both strategic paths.
- [ ] Pilot agreement template (1 page).
- [ ] Pricing decision for post-pilot: firm-level (not per-seat — no-plan-tiers decision stands),
      anchor ~R2,500–R5,000/month/firm. 20 firms = real income; 50 = very good business; either = sellable.
- [ ] Continuity/one-pager for the "what if you get hit by a bus" question.

## What is deliberately parked

- Template extraction (`java-keycloak-multitenant-saas`) and standalone blog monetization — until a paying firm exists.
- New horizontal product phases after 83 — **feature freeze except Phase 84 (take-on/migration)** and
  fixes surfaced by design partners.

## Review trigger

Revisit this plan when: (a) first pilot signed, (b) first pilot refuses — capture the objection verbatim,
it is the real roadmap.
