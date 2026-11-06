# Batch B brief: interest-run period guard (G89-F11 HIGH, G89-F10 LOW)

**Branch:** `fix/g89-b-interest-period`, created from a fresh `origin/main`.
**PR title:** `fix(trust): reject interest runs whose period has not ended + wizard client names (G89-F11, G89-F10)`.
**Authorisation:** founder D1 (2026-11-06): one PR for this batch, with one reproduce-first test and one commit per gap. F11 stays HIGH (D4).
**Gates:** backend full verify (the orchestrator runs it), the frontend gate, two bound reviews, CodeRabbit, and a browser-observed check of row S2-03 on the Keycloak stack.

Read `backend/CLAUDE.md` and `frontend/CLAUDE.md` before you change anything. Backend paths below are relative to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/`.

## G89-F11 (HIGH): an interest run for a period that has not ended is accepted and posted

**What 636B saw.** On 5 Nov an interest run for 05→06 Nov was created, calculated, approved and posted. Its INTEREST_CREDIT rows are dated 6 Nov, which was the next day (`qa_cycle/evidence/phase89-rewalk/logs/db-INT-60.txt`).

**Cause** (verified at `30317a851`):
- `InterestService.createInterestRun` (around `:122-174`) checks only four things: the account is ACTIVE, end ≥ start, the period doesn't overlap another run, and a rate exists. There is no check that the period has ended.
- `approveInterestRun` (around `:432-478`) and `postInterestRun` (around `:481`) have no date check either.
- `InterestService` has no injected `Clock`. It calls `Instant.now()` at around `:570`.

**Fix.**
- Inject `java.time.Clock` into `InterestService`. The bean is `KaziTime`'s `Clock.system(KaziTime.ZONE)` in `BackendApplication.java:21-22`. Follow ADR-358, the pattern `TrustTransactionService` uses at `:63` and `:629`.
- Reject `periodEnd.isAfter(LocalDate.now(clock))` with a 400 ProblemDetail in all three of `createInterestRun`, `approveInterestRun` and `postInterestRun`. Title: "Interest period has not ended". Detail names the period end and today's SA date. Use the existing exception type this service uses for 400s (`InvalidStateException` or similar; check what the end ≥ start check throws). The approve and post guards protect DRAFT runs created before this fix.
- Replace `Instant.now()` at around `:570` with `clock.instant()`.
- A period ending **today** is allowed. The rule is "ended or ends today", because the firm runs interest on the last day of the month. Record this as a judgment call in the PR.

**Reproduce-first test** (commit it red, before the fix commit):
- Add tests to `InterestServiceIntegrationTest`, or create a new `InterestPeriodGuardIntegrationTest` in the same package.
- `createInterestRun_periodEndAfterToday_rejected400`. Period start = `LocalDate.now(KaziTime.ZONE)`, end = start + 1 day. Assert 400 and the title. On main today this returns 201.
- `createInterestRun_periodEndToday_allowed`.
- `approveInterestRun_legacyDraftWithFuturePeriod_rejected400`. Create the DRAFT run directly through the repository so it bypasses the create guard, give it a future end date, then approve. Assert 400.
- `postInterestRun_futurePeriod_rejected400`, built the same way.
- **Do not add `@MockitoBean Clock`.** It creates a new Spring context cache key, and the suite already has OOM and slowness pressure (see `backend/CLAUDE.md`). Use dates relative to the real SA clock. If you need a fixed clock, use the `@TestConfiguration` pattern in `transaction/TrustPostingDateZoneIntegrationTest.java:73-77`, but only with a written justification.

**Audit existing tests.** Grep every interest test for periods that end after today: `InterestServiceIntegrationTest`, `InvestmentBasisInterestTest`, `InterestControllerTest`, `InterestRunDeleteIntegrationTest`, `report/TrustReportQueryIntegrationTest`, `report/InvestmentReportBasisIntegrationTest`, plus `grep -rn "interest-runs\|createInterestRun" backend/src/test`. Also check the frontend e2e specs that create interest runs (`frontend/e2e/**/day-60-interest*`, the depth-ii and admin-audit fixtures). Move their periods into the past. **Do not weaken the guard.** List every test you changed in the PR body.

## G89-F10 (LOW): the interest wizard's Calculate table shows client UUIDs and "Arrangement"

**Where.** `frontend/components/trust/InterestRunWizard.tsx`:
- `:362` has the header "Client ID".
- `:386` renders `{a.clientId}` in mono.
- `:371` has the header "LPFF Rate".
- `:400` renders the cell "Arrangement".

The backend `InterestService.InterestAllocationResponse` (`:105-117`) has `clientId` and `lpffRateId`, but no name and no percentage.

**Fix.**
- Add `clientName` (String) and `lpffSharePercent` (BigDecimal, the LPFF share applied to this allocation) to `InterestAllocationResponse`. This is an additive DTO change.
- Batch-load the customer names with one query per response. Do not do N+1 lookups.
- Frontend: rename the header to "Client" and render the name. Fall back to a short id only if the name is null.
- The non-statutory cell reads `LPFF arrangement rate {n} %`, formatted like the existing percentage formatting in `frontend/lib`. The statutory cell stays "5% (statutory)".
- Update `frontend/lib/types/trust.ts`, or wherever the allocation type lives.
- Add a vitest that renders the allocations table and asserts it shows the client name and not a UUID, and the rate label.
- Grep the e2e specs and vitest for "Client ID" and "Arrangement" and update them.

## Process (non-negotiable; see the root `CLAUDE.md` Quality Gates)

1. Run `pwd` before your first write and confirm you are in your worktree, not `/Users/rakheendama/Projects/2026/b2b-strawman`.
2. Commit order: (1) the red F11 test; (2) the F11 fix; (3) the F10 change with its test; plus test-audit commits if needed.
3. **Always include the cross-cutting contract tests in the targeted set**, whatever the batch touches: `architecture/**` (ArchUnit) and `project/WireVocabularyContractTest`. The latter enforces ADR-337: new wire fields use matter/client, never project/customer, and you never add a field to the known-offenders baseline. Lesson from PR #1746: a new DTO field `projectId` passed every targeted package and failed only in the full verify.
3b. Targeted tests: the `interest` package, every package that imports `InterestService` (grep for it), and `report/`. Run them with `./mvnw -q test -Dtest='...'` and send the log to a file. Then run in `frontend/`: `NODE_OPTIONS="" pnpm lint && NODE_OPTIONS="" pnpm build && NODE_OPTIONS="" pnpm test && pnpm format:check`.
4. Push and open the PR with `gh pr create`, base `main`. The body has a "Judgment calls beyond the brief" section and a test list, and ends with the Claude Code attribution line.
5. **Do not run the full `./mvnw verify`.** The orchestrator owns it. **Do not merge.** Report back: the PR number, the head sha, the targeted test results (with counts), and the judgment calls.
