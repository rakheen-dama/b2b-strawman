# Demo Readiness QA Cycle — 2026-04-12

## Goal
Get the platform to **demo-ready state for all three vertical profiles** (`legal-za`, `accounting-za`, `consulting-generic`) by running `/qa-cycle-kc` against one 90-day lifecycle script per vertical, fixing bugs and gaps until each script runs end-to-end on a single clean pass against the real Keycloak dev stack.

## Decisions (confirmed with founder)

1. **Shape**: QA-heavy lifecycle scripts that double as demo rails once bugs are gone. Exhaustive checkboxes, not curated narrative — the bug-finding value comes from exercising every surface.
2. **Agency = `consulting-generic` profile.** Story is "Zolani Creative" (Johannesburg digital/marketing agency). Any moment where the story needs something the profile lacks is logged in the **Agency gap list** below.
3. **Keycloak mode only.** All three plans run on the real dev stack (frontend 3000 / backend 8080 / gateway 8443 / keycloak 8180 / mailpit 8025). The old mock-auth legal plan (`qa-legal-lifecycle-test-plan.md`, port 3001) is superseded but retained as historical reference.
4. **Order of operations**: legal → accounting → agency, sequential, merging fixes between verticals so cascading improvements land in the next pass.
5. **Accounting plan is v2**: new file `accounting-za-90day-keycloak-v2.md`, leave `48-lifecycle-script.md` alone. V2 strips tier/upgrade UI, adds field promotion + progressive disclosure checks.
6. **Scope of today's work**: write the docs only. No `/qa-cycle-kc` runs this session.
7. **Test suite gate is mandatory** (added mid-session after the user flagged that recent QA cycles have been breaking builds). Every fix PR inside any QA cycle must satisfy: `./mvnw -B verify` + `pnpm test` + `pnpm typecheck` + `pnpm lint` all green **before merging**, not just at cycle end. The master doc has a dedicated "Test suite gate" section enumerating common failure modes and loop rules. No fix PR merges to `main` with a failing or newly-skipped test.

## Recent changes baked into every plan

These are the three things the founder called out as likely staleness in older plans, and they each get dedicated checkpoints in all three new plans:

- **Tier removal** — billing page renders flat subscription states (`TRIALING`/`ACTIVE`/`PAST_DUE`/…), no Starter/Pro/upgrade buttons. Every plan has an explicit "no tier UI visible anywhere" checkpoint on Day 0.
- **Field promotion** — 34 customer + 4 project + 2 task + 4 invoice slugs are promoted to native form inputs and filtered out of the `CustomFieldSection`. Plans verify inline rendering + absence from sidebar.
- **Progressive disclosure** — `NavZone` filters nav items via `requiredModule`. Plans verify legal sees 4 vertical modules, accounting+agency see none, and no cross-vertical leaks show up in sidebar, breadcrumbs, or settings pages.

## Agency gap list (side output — grows as plan is run)

`consulting-generic.json` is a near-empty shell. Writing the agency plan exposed these gaps that would matter for a real agency vertical fork (future `agency-generic` or `agency-za` profile):

- **No template pack** — no pre-seeded project/engagement templates
- **No custom field groups** — no agency-flavoured fields (campaign_type, channel, deliverable_type, creative_brief_url)
- **No promoted project slug for engagement/matter type** — agency story needs something like `campaign_type` promoted to Project level
- **No automation pack** — no "project 80% budget used → notify owner" style rules out of the box
- **No rate-card defaults** — owner has to build the rate card from scratch
- **No retainer primitive** — an agency plan cannot cleanly represent monthly retainers with hour banks; today's workaround is "project with a budget"
- **No utilization dashboard** — Phase 9 operational dashboards exist, but no vertical-specific "team billable %" surface for agency ops
- **No proposal/engagement letter flow** — Phase 11+ territory, but agencies lean on this heavily for new business

These do not block the demo plan being written — the plan intentionally uses generic terminology so it runs against today's code. They become input for a future vertical-profile ideation pass.

## Phase roadmap after this cycle

- **Next**: Run legal plan → close all HIGH/BLOCKER gaps → merge → run accounting v2 → close → run agency → close
- **Then**: Consolidate recurring fixes into a regression sweep, update `regression-test-suite.md`
- **Later**: Consider a dedicated agency vertical phase informed by the gap list above

## Files produced today

- `qa/testplan/demo-readiness-keycloak-master.md` — master doc, shared prep/reset, exit criteria, run instructions
- `qa/testplan/demos/legal-za-90day-keycloak.md` — Mathebula & Partners, 90-day
- `qa/testplan/demos/accounting-za-90day-keycloak-v2.md` — Thornton & Associates, refreshed
- `qa/testplan/demos/consulting-agency-90day-keycloak.md` — Zolani Creative, agency story on consulting-generic profile
- `.claude/ideas/demo-readiness-qa-cycle-2026-04-12.md` — this file
