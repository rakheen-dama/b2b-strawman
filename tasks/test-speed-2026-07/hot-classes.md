# Baseline — 2026-07-12 clean verify (main @ 9790b2c92)

Result: **GREEN**, exit=0, 6,069 testcases / 771 classes, wall **3587s (~60 min)**.

## Wall-time correction (evidence-backed)

The raw wall time is NOT comparable to the ~23 min norm. `pmset -g log` proves two system-sleep
windows mid-run:
- 13:11:31 "Maintenance Sleep" — **989s** → absorbed by `InvoiceLifecycleIntegrationTest.shouldRejectApproveWhenNotDraft` (measured 988.0s; every other method in the class ≤1.5s)
- 13:29:07 "Sleep Service Back to Sleep" — **977s** → absorbed by `StatementControllerIntegrationTest` class setup (suite attr 1013s; its 4 testcases sum to 0.2s)

Corrected wall ≈ 3587 − 1966 ≈ **1621s (~27 min)**, run partly under 5-subagent load (scouts +
reviewers active for the first ~20 min). Consistent with the June norm ~23 min + load. **No new
monster classes exist** — excluding the two sleep artifacts, the top of the distribution matches the
2026-06-14 audit shape (SubscriptionItn ~34s, JobWorker ~33s, shard/jobqueue cluster ~20-30s each).

Consequence adopted: the full-verify wrapper (Builder 3) must run Maven under `caffeinate -is`
(macOS) so unattended verifies can't absorb sleep. This is likely a real contributor to the
"tests keep getting slower / flaky" perception for detached runs.

## Top classes by testsuite time (sleep artifacts marked)

| Time | Class | Note |
|---|---|---|
| 1013.2s | StatementControllerIntegrationTest | SLEEP ARTIFACT (real ≈ 30s per June audit) |
| 991.2s | InvoiceLifecycleIntegrationTest | SLEEP ARTIFACT (real ≈ 3s testcases + setup) |
| 33.9s | SubscriptionItnIntegrationTest | known (June audit: skip — state-machine invariants) |
| 33.0s | CollectionsEscalationTest | new since June (Phase 83) — integration is the value |
| 32.7s | JobWorkerIntegrationTest | known |
| 32.4s | PortalRetainerControllerIntegrationTest | known |
| 31.3s | PortalDeadlineControllerIntegrationTest | known |
| 29.0s | TestConventionsTest | ArchUnit — classpath scan, keep |
| 28.6s | AiReminderComposerTest | rejected near-miss (pipeline is the value) |
| 28.3s | CollectionsScanIdempotencyTest | new since June — DB idempotency is the value |
| 27.8s | EndToEndMultiShardTest | secondary-Postgres cluster, measured irreducible |
| 27.4s | InvitationServiceTest | known |
| 27.3s | ObservabilityTest | unique context, known |
| 27.1s | AutomationSchedulerScheduledTriggerIntegrationTest | known |
| 27.0s | SchedulerMigrationBatch1BTest | known |
| 25.5s | JobWorkerParallelismTest | known |
| 24.9s | ShardIsolationTest | shard cluster |
| 24.7s | InvokeAiSpecialistActionExecutorIntegrationTest | known |
| 23.2s | SecurityAuditTest | unique context, justified |
| 22.0s | SingleShardFullCharacterizationTest | shard cluster |

Campaign classes' baseline testcase-time (excludes @BeforeAll provisioning, which is their real cost):
EmailContextBuilderTest 0.0s+2 provisions, DocxFieldValidatorTest 0.0s+1 provision,
BillingPropertiesTest 0.0s, ShardConfigMigrationTest 0.0s (14 tc), S3PresignedUrlServiceTest 0.0s,
DemoWelcomeEmailServiceTest 0.2s. Expected win = ~3 tenant provisions + heap/OOM pressure relief,
plus the flakiness fixes; NOT a big wall-time move (suite remains integration-dominated, per the
2026-06 audits).

Raw ranking: `hot-classes-raw.txt`. Reports snapshot: main repo `backend/target/surefire-reports/` (this run).
