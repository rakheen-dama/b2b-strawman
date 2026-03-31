# What AI Agents Can't Do (And What That Means for Your Career)

*Part 7 of "One Dev, 843 PRs" — a series about building a production B2B SaaS platform with AI coding agents. This is the final post.*

---

There's a narrative forming in tech circles that goes something like: AI coding agents will replace developers. The evidence cited is usually a demo where an agent builds a to-do app in 90 seconds, or a startup claiming their AI wrote 95% of the code.

I just spent 8 weeks shipping 843 PRs with AI agents on a production multi-tenant SaaS platform. I am probably one of the heaviest users of AI-assisted development that exists. And I'm here to tell you: the narrative is wrong. Not because AI agents aren't powerful — they are. But because the narrative misunderstands what "building software" actually means.

Let me show you the things my agents couldn't do. Not "couldn't do yet." Couldn't do *in principle*.

## 1. AI Can't Decide What to Build

The hardest question in software isn't "how do I implement this?" It's "should I implement this at all?"

When I sat down to plan the accounting vertical for DocTeams, I needed to answer questions like:

- Do South African accounting firms actually need software for FICA compliance, or do they manage fine with spreadsheets?
- Is the onboarding flow (PROSPECT → ONBOARDING → ACTIVE) worth the complexity, or would a simple "create customer" button be enough for a 3-person firm?
- Should I build retainer billing now, or is hourly billing sufficient for the first paying customers?
- Is court date tracking for law firms a feature or a product?

These aren't technical questions. They're *judgment calls* about markets, users, and priorities. They require:

- **Domain knowledge** that doesn't exist in training data. The specific requirements of FICA/KYC compliance for South African accounting firms, the way small practices actually manage client onboarding, the distinction between what firms *say* they need and what they *actually use* — this comes from research, conversations, and market intuition.

- **Prioritization under constraints.** I have finite time and budget. Building retainer billing means *not* building something else. The AI can tell me what retainer billing involves. It can't tell me whether it's more valuable than improving the profitability reports.

- **Taste.** Some features are technically feasible and user-requested but would make the product worse. A feature-flag system for every UI element? Technically doable. But it would turn the product into a configuration nightmare for the small firms I'm targeting. Saying "no" is a product decision that AI can't make.

The `/ideate` skill helped. Enormously. Having an AI that knows the entire codebase and can research domain requirements is like having a tireless research assistant. But the *decisions* that came out of those sessions were mine. The AI explored the space. I chose the path.

## 2. AI Can't Evaluate Its Own Architecture

Remember the RLS reversal from Post 1? In Phase 2, I designed a tiered tenancy model with shared-schema isolation for Starter tenants and dedicated schemas for Pro tenants. The architecture doc was thorough. The ADRs explained the tradeoffs. The implementation was clean.

It was also wrong.

Not wrong in the "it doesn't work" sense — it worked fine. Wrong in the "this complexity isn't justified" sense. Two isolation strategies meant two code paths in every query, two test suites, two provisioning flows, and twice the surface area for tenant isolation bugs. For a product targeting 3-person accounting firms, this was over-engineering.

I realized this 11 phases later, after living with the consequences. The AI didn't realize it. The AI *couldn't* realize it, because:

- The architecture was internally consistent. Every ADR had sound reasoning.
- The implementation matched the spec. Tests passed. Reviews approved.
- The complexity cost was *cumulative* — each individual feature was slightly harder to implement, slightly harder to test. No single moment screamed "this is wrong."

Architectural mistakes reveal themselves through lived experience, not analysis. You need to feel the friction of maintaining something for weeks before you know whether the abstraction was worth it.

When I ran Phase 13 to rip out the shared schema, the codebase got simpler. Entities lost `@Filter` annotations. Repositories lost custom queries. Tests lost setup complexity. The delete touched 27 entities and 7 test files. Every developer who's ever removed an unnecessary abstraction knows the feeling: the code breathes again.

AI agents will faithfully build the wrong architecture. And they'll faithfully tear it down when you tell them to. The *knowing* which to do is irreducibly human.

## 3. AI Can't Navigate Organizational Reality

This one doesn't apply to me — I'm a solo developer. But it's the biggest gap for teams.

Software development in organizations involves:

- **Negotiating scope** with stakeholders who want everything by yesterday
- **Communicating tradeoffs** to non-technical decision-makers
- **Navigating politics** around which team owns which service
- **Building consensus** on technical direction when smart people disagree
- **Mentoring** junior developers who need to understand the *why*, not just the *what*

AI agents operate in a world of clear specifications and unambiguous success criteria. The real world has neither. "Build the invoicing system" is actually "build the invoicing system that accounting wants, but also works for the legal team's different billing model, and doesn't break the reporting dashboard that the CEO uses, and can ship by Q3 because sales already promised it."

No agent handles this. No agent will handle this. Because it's not a technical problem — it's a human coordination problem.

## 4. AI Can't Learn From Production

My QA cycle for the accounting vertical found 27 gaps. Some were missing features (no proposal workflow). Some were bugs (FICA fields not auto-attaching during customer creation). Some were UX issues (currency defaulting to USD instead of ZAR).

These gaps weren't in the requirements. They were discovered by *using the product* — navigating screens, trying workflows, noticing where things felt wrong. The QA agents automated some of this (Playwright scripts that walk through user journeys), but they could only test what I told them to test.

The gaps that matter most are the ones nobody specified. The "huh, that's weird" moments. The "I thought this would be here" expectations. The "this works but it's annoying" friction.

Feedback from real usage — not test suites, not specs, not demos — is what makes software good. AI agents can process feedback once you've articulated it. They can't *experience* the product the way a user does.

## 5. AI Can't Maintain Conviction Under Pressure

Three weeks into the build, I hit a stretch where nothing felt right. The billing model was too complex. The frontend had accumulated design debt from rapid phase execution. The Clerk auth integration was flaky. The Phase 35 Keycloak migration had just failed and been rolled back.

At that point, I had two choices: simplify aggressively and keep going, or abandon the approach and start over.

I chose to simplify. Ripped out the RLS layer. Simplified the billing model. Standardized the frontend patterns. Migrated auth more carefully on the second attempt. Each of these was a judgment call informed by frustration, pattern recognition, and the gut sense that the foundation was sound even if the execution was messy.

AI agents don't have conviction. They don't have the ability to step back and say "the last 14 PRs were wrong, but the underlying architecture is right — we just need to approach the migration differently." They execute what you tell them. The *deciding what to tell them* during moments of doubt is entirely on you.

## So What Does This Mean?

If AI agents can't decide what to build, evaluate architecture holistically, navigate organizations, learn from production, or maintain conviction — what are they actually good at?

**Execution at scale.** Given a clear spec, they implement fast and consistently.

**Pattern application.** Given reference code, they produce code that matches the patterns. At 843 PRs, this consistency is what kept the codebase navigable.

**Tireless review.** They catch bugs, security issues, and convention drift without getting bored or rushing before a deadline.

**Research synthesis.** They read documentation, explore codebases, and summarize findings faster than any human.

**Collaborative thinking.** In ideation sessions, they explore edge cases and domain considerations that I might miss. They're thinking partners, not just executors.

None of these are small things. Together, they let one developer produce what would normally take a small team. But they don't eliminate the need for the developer. They *amplify* the developer.

## What This Means for Your Career

If you're a developer worried about AI, here's my honest assessment after 8 weeks in the trenches:

**The skills that matter more now:**
- System design and architecture (AI amplifies your decisions — make sure they're good)
- Domain knowledge (understanding *what* users need, not just *how* to build it)
- Product judgment (knowing when to say no, when to simplify, when to invest)
- Communication (translating technical tradeoffs for non-technical stakeholders)
- Quality standards (knowing what "good" looks like and insisting on it)

**The skills that matter less:**
- Typing speed
- Memorizing API signatures
- Boilerplate generation
- Routine CRUD implementation

**The skills that still matter exactly as much:**
- Debugging (AI agents produce bugs — you need to understand them)
- Reading code (you'll read more code than ever, because AI writes it faster than you can review it)
- Testing strategy (deciding *what* to test is harder than writing the tests)
- Operational awareness (what happens when this code hits production?)

The developer role isn't disappearing. It's shifting. Less time writing boilerplate, more time thinking about architecture, product, and quality. Less time as an individual contributor, more time as a *technical director* — even if your "team" is a fleet of AI agents.

If that sounds like a senior engineer's job, that's because it is. The AI handles the work that junior engineers used to do. What's left is the judgment, taste, and decision-making that makes senior engineers valuable.

The good news: those skills are learnable. The better news: the experience of *using* AI agents effectively is itself a skill that compounds. Every phase I ran, the pipeline got better. Not because the AI improved — because *I* got better at specifying, reviewing, and directing.

## The Honest Summary

One developer. Eight weeks. 843 pull requests. A production multi-tenant SaaS platform with 83 entities, 240K lines of Java, 111K lines of TypeScript, and enough features for an accounting firm to run 75% of its practice.

The AI wrote the code. I designed the product.

The AI followed the patterns. I chose the patterns.

The AI built what I specified. I learned what I'd failed to specify.

The AI worked tirelessly. I decided when to stop, when to simplify, and when to throw away 27 commits and start over.

This is what AI-assisted development actually looks like. Not magic. Not replacement. A very capable workforce that executes your vision — including your mistakes — at a speed that makes the quality of your thinking the only bottleneck that matters.

Think carefully. Specify clearly. Review ruthlessly. And let the agents do the rest.

---

*This is the final post in "One Dev, 843 PRs." If you found the series valuable, I'm building more:*

- *[Multi-Tenant from Scratch](#) — the architecture deep-dives*
- *[From Generic to Vertical](#) — building for specific industries*
- *[Modern Java for SaaS](#) — Java 25, Spring Boot 4, and Hibernate 7*

*I'm also extracting the multi-tenant foundation into an open-source template. [Subscribe](#) to know when it's ready.*
