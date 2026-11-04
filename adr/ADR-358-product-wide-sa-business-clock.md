# ADR-358: Product-wide `Africa/Johannesburg` business clock and the ban on zone-less `now()`

**Status**: Accepted

**Context**:

This ADR records a decision shipped in PR #1716 (merge `a216f44308dd7fa0fe2e543323a5128fc5be7e91`, 2026-11-04) before Phase 89 started; it is written here so the decision is discoverable. Before the PR, `BackendApplication.clock()` returned `Clock.systemDefaultZone()`, the containers run UTC (no `TZ` / `user.timezone` in compose), and `LocalDate.now()` with no clock appeared at 57 sites in 32 files under `backend/src/main/java` against only 7 `LocalDate.now(clock)` sites. The Phase 88 re-walk caught the consequence in the trust ledger: a fee transfer posted at 01:30 SAST was dated the UTC day before the deposit that funded it, so the interest walk and the client ledger disagreed with the attorney's calendar (G88-F48, HIGH; trust sites `TrustTransactionService.java:622,1114,1139,1264,1281,1328,1334`, `TrustAccountService.java:147,244`, `TrustInvestmentService.java:243,289` at the time). The Next.js server computed "today" in the process zone (`my-work/page.tsx:109`, `dashboard/page.tsx:92` via `new Date().toLocaleDateString("en-CA")`), so the My Work and dashboard date ranges rolled over two hours late for a South African user (G88-F49, LOW; same-class cluster authorised). Phase 88's lifecycle suite had already shown that time of day is a test variable: run 1 inside the 00:00–02:00 SAST window scored 82/4/1/17 while the same suite outside the window was green.

The founder decided (requirements decision 4) that "today" is South African time product-wide: Kazi is SA-only, `Africa/Johannesburg` has no DST, and no per-firm timezone setting exists or is planned.

**Options Considered**:

1. **Per-firm timezone setting on `OrgSettings`, read by every "today" site.**
   - Pros:
     - Would survive a future non-SA deployment without a code change.
   - Cons:
     - Adds a tenant setting, a settings UI, a migration and a resolution step to every one of the 57 sites for a product that is legal-za only ([ADR-330](ADR-330-archive-tag-not-fork.md) — no horizontal features, no non-SA deployments in scope).
     - Scheduled jobs (`@Scheduled(cron=…)`) run once per JVM, not once per firm; a per-firm zone cannot drive a cron trigger without a scheduler redesign.
     - The founder ruled it out explicitly.
2. **Set the container / JVM default zone (`TZ=Africa/Johannesburg`, `-Duser.timezone`) and leave the code on `LocalDate.now()`.**
   - Pros:
     - Zero code change; fixes the symptom on the deployed stack immediately.
   - Cons:
     - The correctness of every date in the product would depend on an environment variable that no test asserts; a mis-set e2e container (the e2e stack runs UTC) or a developer laptop reproduces the bug silently.
     - Tests could not pin a boundary instant: `LocalDate.now()` reads the JVM default, so the "23:30Z is already tomorrow in SAST" case is untestable without a `Clock`.
     - Storage instants, audit-export labels and provider billing months that must stay UTC would silently move with the JVM zone.
3. **Product-wide `Clock` bean in `Africa/Johannesburg` injected everywhere, plus an ArchUnit test that bans zone-less `now()` and JVM-default-zone reads in main code (CHOSEN).**
   - Pros:
     - One zone constant (`config/KaziTime.java:17-19`: `ZONE_ID = "Africa/Johannesburg"`, `ZONE = ZoneId.of(ZONE_ID)`) and one bean (`BackendApplication.java:20-23`: `Clock.system(KaziTime.ZONE)`); every business date is `LocalDate.now(clock)` and a stored `Instant` becomes a business date through `KaziTime.ZONE`.
     - The clock is injectable, so an integration test can pin `Clock.fixed(2026-10-29T23:30Z, Africa/Johannesburg)` and prove a posting lands on 30 October (`TrustPostingDateZoneIntegrationTest`).
     - The guard (`architecture/ZoneAwareTimeArchTest`) makes regression a compile-adjacent failure, not a re-walk finding: adding an allow-list entry is a review-time decision visible in the diff.
     - UTC stays explicit and enumerated: `AuditExportService`, `AuditPdfExporter`, `AiCostService`, `TrustLedgerPortalSyncService`, `XeroApiClient`, `KaziTime` are the only classes allowed to touch `ZoneOffset.UTC` / `ZoneId.of(String)`.
   - Cons:
     - 154 files changed in one PR (59 backend main, 49 backend test, 26 frontend, 4 `packages/shared`, 1 portal, `pom.xml`); 49 existing test files had to move from test-side `LocalDate.now()` to `LocalDate.now(KaziTime.ZONE)` to pass under a non-SA Surefire zone.
     - Each class that needs "today" now takes a `Clock` constructor argument; abstract bases without a bean (`BaseDemoDataSeeder`) are allow-listed rather than injected.
4. **Store and compute everything in UTC; convert only at the presentation layer (frontend / PDF).**
   - Pros:
     - Backend code stays zone-free.
   - Cons:
     - Business dates are not presentation: a trust posting date, an invoice due date, a prescription date and a cron trigger are domain facts that must be the SAST calendar day at write time. Converting at render would still write the wrong `transaction_date`.
     - SQL `CURRENT_DATE` predicates (debtor book, outstanding list, fee-note summary, dunning scan) run in the database and have no presentation layer to convert in.

**Decision**: Option 3 — `KaziTime.ZONE` is the single business zone; the `Clock` bean is `Clock.system(KaziTime.ZONE)`; every server-side business "today" reads `LocalDate.now(clock)`; `@Scheduled(cron)` carries `zone = KaziTime.ZONE_ID`; SQL `CURRENT_DATE` is replaced by a bound `CAST(:today AS DATE)`; the frontend, portal and e2e helpers read "today" from `@b2mash/shared/date` (`todayInSA`, `isoDateInSA`, `daysBetween`); and `ZoneAwareTimeArchTest` bans zone-less `now()`, `Clock.systemDefaultZone()`, `ZoneId.systemDefault()`, `TimeZone.getDefault()` and the legacy `java.util.Date` / `Calendar` APIs in main code, with UTC confined to two commented allow-lists.

**Rationale**:

1. **The governing rule is semantic, not mechanical.** A calendar date or wall-clock time a user reasons about is SAST; wire formats, storage instants, sort keys, provider billing months and audit-export timestamps stay UTC. This is the rule mirrored in the `KaziTime` Javadoc, and it is why the guard has allow-lists rather than a blanket ban: `AuditExportService.java:226,230` keeps `LocalDate.now(ZoneOffset.UTC)` because the audit-log UI filters by UTC day, and `TrustLedgerPortalSyncService:560` keeps a UTC sort key for the portal projection ([ADR-253](ADR-253-portal-surfaces-as-read-model-extensions.md)). Entity `@PrePersist Instant.now()` is untouched — instants are not banned.
2. **Schema-per-tenant does not change the answer.** All tenants are South African law firms (`legal-za` is the only profile since Phase 86); a per-tenant zone would be a horizontal feature [ADR-330](ADR-330-archive-tag-not-fork.md) archived. One product zone keeps crons, the portal read model in the global schema and the tenant schemas on the same calendar.
3. **Phase 88 lesson — real-write-path tests and browser observation.** The HIGH gap was not found by unit tests because none walked the real posting path at a boundary instant. The PR therefore added `TrustPostingDateZoneIntegrationTest` (fixed clock 2026-10-29T23:30Z = 01:30 SAST 30 Oct: fee transfer + reversal, transfer reversal legs + ledger last-dates, refund reversal, account open/close, collections `:today` days-overdue), an automation-cron-in-SAST integration test (both directions of the 2 h skew), an SoA period-boundary test (a payment at 2026-10-31T23:30Z lands in November), `BackendApplicationClockTest` and frontend boundary tests (`__tests__/lib/today-in-sa.test.ts`, `my-work-date-range`, retention-card, DSAR). Surefire runs the JVM in UTC by default (`pom.xml:39` `<test.timezone>UTC</test.timezone>`, `:314` `-Duser.timezone=${test.timezone}`), and `-Dtest.timezone=Pacific/Kiritimati` is the boundary override.
4. **Observed, not inferred.** The E2E-LEGAL lifecycle suite was run inside the 00:00–02:00 SAST window (23:08–23:11Z = 01:08–01:11 SAST on 2026-11-04, e2e containers on UTC): 101 passed / 0 failed / 3 skipped, equal to the 628I baseline where Phase 88 run 1 in the same window was 82/4/1/17. DB read-back: Moroka DEPOSIT R 250 000 and FEE_TRANSFER R 8 500 both dated 2026-11-04 with `created_at` 2026-11-03 23:09Z. Full backend verify on `cfb4fa03`: 6304/0/0/26.

**Consequences**:

- Positive:
  - `grep -rn "LocalDate.now()\|LocalDateTime.now()\|LocalTime.now()" backend/src/main/java` → 0; `LocalDate.now(clock)` / `LocalDateTime.now(clock)` → 75 sites. All 9 `@Scheduled(cron=…)` carry `zone = KaziTime.ZONE_ID`; `AutomationPollTriggersHandler` evaluates firm-authored cron expressions in SAST.
  - Playwright `timezoneId` defaults to `Africa/Johannesburg` in all four configs (`PW_TIMEZONE` overrides), so suite results no longer depend on the host's zone, and the Phase 89 exit rule "score outside 00:00–02:00 SAST; boundary reproductions are separate runs" has a mechanism behind it.
  - Phase 89 code has a fixed rule: inject `Clock`, use `LocalDate.now(clock)`, convert `Instant` → business date with `KaziTime.ZONE`; new UTC sites need an allow-list entry in the diff.
- Negative:
  - No historical correction: there are no live firms, so QA/demo rows were not re-dated. Legacy CLOSED matters whose `retention_ends_on` was backfilled in UTC by the tenant baseline keep that date (forward-only; a pre-PR close between 22:00–24:00Z can be one day early — comment in `MatterClosureService`).
  - Stored `time_reminder_time` values were not converted; the form label now reads "Reminder Time (SAST)" and the semantics are SA wall clock (CodeRabbit Major declined with rationale, J11).
  - Four frontend components still read browser-zone dates outside the J8 list and fail vitest under a non-SA process zone on `main` too: `statement-of-account-dialog` (first-of-month default), `packs-page` (installed date), `task-detail-sheet` (completion metadata), `billing-admin` (`BillingDetailSheet` dates). `ProjectServiceTest` uses `@InjectMocks` without a `Clock` (pre-existing, does not reach the clock path).
  - A `@TestConfiguration` fixed clock creates a distinct Spring context-cache key per pinned instant; new boundary tests should reuse an existing pinned-clock context class where possible.
- Follow-ups:
  - Phase 89 R-E2c pins `formatComplianceDateWithTime` in `@b2mash/shared` to `Africa/Johannesburg` (the `compliance/requests/[id]` page still renders UTC wall-clock, the same class as the #1719 audit-trail fix).
  - The four browser-zone components above and the SoA dialog defaults (R-E3d uses `todayInSA()`).
- Explicitly not done: no per-firm timezone, no container `TZ`, no DST handling, no conversion of stored instants.

**Related**: [ADR-330](ADR-330-archive-tag-not-fork.md) (legal-za only, no horizontal settings), [ADR-253](ADR-253-portal-surfaces-as-read-model-extensions.md) (portal sort key stays UTC), [ADR-347](ADR-347-fee-note-due-date-and-balance-model.md) (due date stamped from "today" at approve), [ADR-354](ADR-354-persisted-retention-end-date.md) (the UTC-backfilled `retention_ends_on` left as-is), [ADR-361](ADR-361-client-ledger-running-balance-server-side.md) (posting order on the SA calendar day); `architecture/phase89-go-live-readiness.md` §14.1, §14.3, §14.8, §14.11; `qa_cycle/evidence/phase88-rewalk/full/gaps.md` G88-F48 / G88-F49.
