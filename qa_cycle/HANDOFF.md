# QA Cycle Handoff — Legal ZA Full Lifecycle

Last updated: 2026-04-22

## TL;DR

Cycle 1 of `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` merged to `main` as commit `61774873`. Days 0–15 executed + passed (isolation gate PASSED 36/36). Days 21–90 not executed. 21 deferred OPEN gaps. The prior cycle's full log, tracker, fix specs, and checkpoint evidence live in `qa_cycle/_archive_2026-04-22_legal-full-lifecycle-cycle1/` (move there before starting a new cycle).

## How to resume

```
/qa-cycle-kc qa/testplan/demos/legal-za-full-lifecycle-keycloak.md
```

The skill will detect `qa_cycle/status.md` is absent (since you archived the prior one) and ask for seed. Point it at `qa_cycle/_archive_*/status.md` as the conventions source and this HANDOFF.md as the starting state.

## What's on main (merged in PR #1107 / commit 61774873)

Cycle 1 merged 13 internal PRs. Key behavior changes the next cycle should assume are in place:

- **L-23** — server-actions type re-exports removed; `/settings/general` mutations work
- **L-33** — `fica-onboarding-pack.json` request template available on legal-za / accounting-za / consulting-za vertical profiles
- **L-34** — `PortalContactAutoProvisioner` auto-creates a PortalContact (GENERAL) on every `CustomerCreatedEvent` with an email. Idempotent + race-safe (catches `ResourceConflictException` + `DataIntegrityViolationException`).
- **P-01** — portal `/requests` path + `information_requests` module in legal-za/accounting-za/consulting-za profiles + `VerticalModuleRegistry` has matching `ModuleDefinition`
- **P-02** — portal has `/requests` list and `/requests/[id]` detail routes with presigned-URL upload + submit
- **L-42** — info-request email embeds a magic-link token pointing at `http://localhost:3002/auth/exchange?token=...&orgId=...`. `PROPAGATION_REQUIRES_NEW` transaction wrapping on token mint. **No longer sends a duplicate standalone magic-link email** (#1109 split introduced `MagicLinkService.generateTokenOnly`).
- **L-43** — `PortalEventHandler` handles `RequestItemSubmittedEvent` → portal read-model row goes PENDING → SUBMITTED
- **L-50** — `AcceptanceService` uses canonical `docteams.app.portal-base-url` config key (default `http://localhost:3002`)
- **P-06** — portal `/accept/[token]` handles `SENT` and `VIEWED` statuses (backend auto-flips `PENDING → SENT → VIEWED`)
- `CustomerAuthFilter` resolves portal-contacts deterministically: PRIMARY > BILLING > GENERAL, then by `createdAt ASC`

## Deferred gaps (OPEN) — priority-ordered

### HIGH — fix before continuing portal POV days
- **GAP-L-52** — `TrustLedgerPortalSyncService` missing listener for `TrustTransactionRecorded` (direct-RECORDED deposits don't sync to portal read-model). Fix shape: mirror L-43 (event + listener + integration test). Estimated S (<30 min).
- **GAP-L-48** — no proposal-builder entity / `/api/customers` endpoint / matter-level "New Proposal" CTA. Workaround on Day 7 = Generate Document → Engagement Letter. Multi-day scope.

### MED
- **GAP-L-49** — engagement-letter PDF lacks LSSA tariff / VAT / fee-estimate structure. Day 7.3/7.4/8.2/8.3 currently SKIPPED-BY-DESIGN. Multi-day scope.
- **GAP-L-22** — post-registration KC session handoff requires explicit logout+login. Reliable workaround; proper fix non-trivial.
- **GAP-L-28** — conflict-check self-match on Day 2.6 (subject not excluded from candidates).
- **GAP-L-35** — PROSPECT-status gate blocks custom-field PATCH after matter already created.
- **GAP-L-36** — RAF-specific matter template missing (only generic Litigation shipped).

### LOW (bundle into polish slice)
L-20, L-21, L-25, L-26, L-27, L-29, L-30, L-31, L-32, L-37, L-38, L-39, L-40 (Add Portal Contact dialog), L-41, L-44, L-45, L-46, L-47, L-51, L-53, L-54, L-55, P-03, P-05, P-07, OBS-L-27.

## Known carry-forward from pre-cycle archives (may resurface)

- GAP-L-04 Keycloak end-session confirmation (LOW, OPEN)
- GAP-L-06 Disbursements "Add / Log Expense" button (MED, OPEN, ADR-247) — Day 21 Phase B will hit this
- GAP-L-07 Matter closure CLOSED state + pre-closure gates (MED, OPEN, ADR-248) — Day 60 Phase B will hit this
- GAP-L-09 Statement of Account template (MED, OPEN, ADR-250) — Day 60/61 will hit this
- GAP-L-10 Acceptance-eligible manifest flag (MED, OPEN, ADR-251) — verified as working in cycle 1, regression risk
- GAP-L-11 Audit log UI (MED, OPEN) — Day 85.4 will hit this

## Dev stack state assumption

If you're resuming mid-scenario (Day 21+), assume the dev stack is still running with cycle-1 data intact:

- Tenant schema: `tenant_5039f2d497cf` (mathebula-partners)
- Users: Thandi (owner) / Bob (admin) / Carol (member) @ `@mathebula-test.local`
- Sipho Dlamini customer (`8fe5eea2-75fc-4df2-b4d0-267486df68bd`) with RAF-2026-001 matter (`40881f2f-7cfc-45d9-8619-de18fd2d75bb`), R 50 000 trust deposit, FICA request complete, proposal accepted
- Moroka Family Trust customer (`29ef543a-d0ad-4851-97e0-77344e0b1b1d`) with EST-2026-002 matter (`4e87b24f-cf40-4b5b-9d1e-59a63fdda55a`) and R 25 000 trust deposit
- Full ID manifest in `qa_cycle/_archive_2026-04-22_legal-full-lifecycle-cycle1/isolation-probe-ids.txt`

If the stack was wiped (`dev-down.sh --clean`), you have two choices:
1. Re-run Days 0–20 from the scenario (≈3–5 hours of QA agent time) to rebuild state.
2. Write a seed script that hydrates state directly via the REST API (respecting the no-SQL rule). Faster but requires crafting the calls carefully.

## Scripted days remaining

Day 21, 28, 30, 45, 46, 60, 61, 75, 85, 88, 90. Day 90 includes a repeat isolation probe (should still pass 36/36 given no changes to isolation-relevant code).

## Infra cleanup notes

10 locked agent worktrees remain under `.claude/worktrees/agent-*` from cycle 1's Dev turns. Non-urgent. Clean with:

```
git worktree list | grep agent- | awk '{print $1}' | while read path; do
  git worktree remove --force "$path" 2>/dev/null
done
git worktree prune
```
