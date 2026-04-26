# Legal Full-Lifecycle QA Script Ideation
**Date**: 2026-04-21
**Not a build phase** — this is a new **QA testplan artifact** to be driven by `/qa-cycle-kc`.

## Decision
New file: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` — the first **unified firm + client-portal** legal lifecycle, single tenant (Mathebula & Partners), Sipho Dlamini RAF claim, 90 days, interleaved POV.

## Gap this closes
Before today: firm-side (`legal-za-90day-keycloak.md`, 569 lines) and portal-side (`portal-client-90day-keycloak.md`, cross-vertical 306 lines) had to be run separately. Running both never happened in a single story — the "client saw X the day firm did Y" moment did not exist as a QA artifact or demo.

## Key design choices
1. **Interleaved POV**, not two-phase. Days alternate `[FIRM]` / `[PORTAL]` with an explicit context-swap protocol (close browser context, change port, use correct auth helper). More valuable demo; more realistic; script notes each switch inline.
2. **Legal-only**. No `/retainer` day (RAF matters aren't retainer-based — founder call "keep it realistic"). No cross-vertical gating probes (handled by `portal-client-90day-keycloak.md`).
3. **One portal narrator (Sipho)** + **Moroka onboarded firm-side only** on Day 14 as the isolation target.
4. **Multi-customer isolation is a BLOCKER-severity gate** (Day 15 dedicated, Day 46 / Day 75 / Day 90 spot-checks). Three-level probe: list leak, direct-URL, API-with-portal-JWT. Any 200 with Moroka data = halt cycle.
5. **Firm-side events consistent with current codebase state**: access-request approval, conflict check, KYC adapter, LSSA tariff fee note, disbursements (Phase 67), matter closure (Phase 67 Epic 489), Statement of Account (Phase 67 Epic 491), PayFast sandbox payment.
6. **Master doc updated** to list the new script with a "picking the right script" note, since we now have four scripts with overlapping scope.
7. **7 wow moments** including Day 15 isolation and Day 88 firm+portal activity side-by-side.

## Day-by-day skeleton
- Day 0 `[FIRM]` — access request → OTP → admin approve → Keycloak reg → team invites
- Day 1–2 `[FIRM]` — firm branding, trust account, onboard Sipho, conflict CLEAR + KYC
- Day 3 `[FIRM]` — create RAF matter, send FICA info request
- Day 4 `[PORTAL]` — Sipho first login via magic-link, upload FICA docs
- Day 5 `[FIRM]` — review FICA
- Day 7 `[FIRM]` — draft + send proposal (LSSA tariff fee estimate)
- Day 8 `[PORTAL]` — review + accept proposal
- Day 10 `[FIRM]` — matter ACTIVE, trust deposit R 50k
- Day 11 `[PORTAL]` — view trust balance
- Day 14 `[FIRM]` — onboard Moroka Family Trust (isolation setup)
- Day 15 `[PORTAL]` — **isolation check** (list + URL + API probes)
- Day 21 `[FIRM]` — log time, disbursement, court date
- Day 28 `[FIRM]` — bulk billing, generate fee note
- Day 30 `[PORTAL]` — pay fee note via PayFast sandbox
- Day 45 `[FIRM]` — second info request, second trust deposit
- Day 46 `[PORTAL]` — respond, re-check trust, isolation spot-check
- Day 60 `[FIRM]` — matter closure, SoA generated
- Day 61 `[PORTAL]` — download SoA PDF
- Day 75 `[PORTAL]` — digest + late-cycle isolation spot-check
- Day 85 `[FIRM]` — final paperwork + audit log sweep
- Day 88 `[FIRM → PORTAL]` — wow moment: firm + portal activity side-by-side
- Day 90 — combined regression + exit sweep

## Explicitly parked / out of scope
- `/retainer` flow (RAF isn't retainer; the cross-vertical portal script covers retainer for consulting)
- Multi-portal-contact (Sipho only; Moroka doesn't log in to portal)
- Cross-vertical portal gating probes (covered by `portal-client-90day-keycloak.md`)
- Matter-closure override path (clean-path Day 60 only; override path already covered in firm-only script Day 85)
- Conveyancing pack (Phase 67 Epic 492 — different matter type)

## Deliverables (this session)
- `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` — new 770-line script
- `qa/testplan/demo-readiness-keycloak-master.md` — updated table + "picking the right script" guide

## Next step
Run `/qa-cycle-kc qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` to execute the first clean pass. The driver will produce a gap report at `qa/gap-reports/legal-za-full-lifecycle-{YYYY-MM-DD}.md` when blockers surface. Expect first pass to find 3–8 gaps — this is the first time many of these integration points have been exercised together under Keycloak auth.
