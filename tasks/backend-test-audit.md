# Backend Test Audit

**Date:** 2026-02-22
**Scope:** All 208 test files in `backend/src/test/java/`
**Goal:** Identify useless/redundant tests and Testcontainers misuse; recommend speedups without losing coverage

---

## Executive Summary

| Metric | Value |
|--------|-------|
| Total test files | 208 (195 with `@Test` methods) |
| Total test methods | ~1,787 |
| `@SpringBootTest` (Testcontainers) | ~164 files |
| Pure unit tests (no Spring) | ~38 files |
| Baseline wall time (`mvn clean verify`) | **5m 22s (322s)** |
| Testcontainers containers | 1 shared Postgres + 5 per-class LocalStack |

**Key findings:**
1. **H2 is not viable** — schema-per-tenant (`SET search_path`), JSONB on 12 entities, `ON CONFLICT RETURNING`, `->>` operators make every `@SpringBootTest` PostgreSQL-dependent
2. **3 test files are deletable** (20 tests, ~20s saved) with zero coverage loss
3. **~7 individual test methods** are redundant/trivial and can be pruned
4. **Shared LocalStack container** saves ~20s (5 classes each starting their own)
5. **JUnit 5 parallel class execution is NOT safe** without significant per-test annotation work (see Post-Attempt Findings)

---

## Section 1: Tests to Delete

### 1.1 `BackendApplicationTests.java` — DELETE

| | |
|---|---|
| **Tests** | 1 (`contextLoads()`) |
| **Time** | 16.7s |
| **Reason** | Empty body. Every other `@SpringBootTest` class also boots the context. Zero unique assertions. |

### 1.2 `DocumentTemplateIntegrationTest.java` — DELETE

| | |
|---|---|
| **Tests** | 8 |
| **Time** | ~2s |
| **Reason** | Every test covered by `DocumentTemplateControllerTest` (HTTP) or `DocumentTemplateSlugTest` (slug logic). |
| **Before deleting** | Merge `shouldIsolateBetweenTenants` into controller test as a cross-tenant 404 assertion. |

### 1.3 `BillingRateIntegrationTest.java` — DELETE

| | |
|---|---|
| **Tests** | 11 |
| **Time** | ~1.5s |
| **Reason** | Heavy overlap with `BillingRateControllerTest` (16 tests). Both test create, list, update, delete via same HTTP endpoints. |
| **Before deleting** | Merge `createRate_rejectsCompoundScope` into controller test as a 400 assertion. |

---

## Section 2: Individual Methods to Prune

### 2.1 `CustomerLifecycleEntityTest` — remove 5 trivial constructor tests

- `defaultLifecycleIsProspect`, `customerTypeDefaultsToIndividual`, `customerTypeConstructorAcceptsEnum`, `offboardedAtIsNullByDefault`, `lifecycleStatusConstructorSetsExplicitStatus`
- Keep all state transition tests (the real business logic)

### 2.2 `RetainerAgreementEntityTest` — remove 2 trivial tests

- `create_createdAtAndUpdatedAtSet` (tests Hibernate `@CreationTimestamp`)
- `create_defaultsConsumedAndOverageToZero` (tests `BigDecimal.ZERO` field init)

---

## Section 3: H2 Viability Assessment

### Verdict: Not Viable

Every `@SpringBootTest` imports `TestcontainersConfiguration` which hardwires a PostgreSQL container. Six hard blockers prevent H2:

| Blocker | Scope |
|---------|-------|
| `SET search_path TO tenant_xxx` | Every request via `SchemaMultiTenantConnectionProvider` |
| `columnDefinition = "jsonb"` | 12 entities (AuditEvent, Customer, Project, Task, etc.) |
| `ON CONFLICT DO UPDATE RETURNING` | InvoiceNumberService, PortalReadModelRepository |
| `->>` JSONB operator | AuditEventRepository, CustomFieldFilterHandler |
| `::uuid`, `::numeric` casts | TimeEntryRepository (11 queries), ReportRepository (10 queries) |
| `SET search_path TO portal, public` | Portal datasource connection-init-sql |

The 38 pure unit tests already run without any database (<0.1s each).

---

## Section 4: Speed Optimization — Shared LocalStack

5 test classes each start their own `LocalStackContainer` (~5-7s startup each = ~25-35s total):
- `S3PresignedUrlServiceTest` (6.7s)
- `BrandingIntegrationTest` (9.7s)
- `DocumentGenerationIntegrationTest` (7.2s)
- `GeneratedDocumentControllerTest` (12.8s)
- `DocumentAuditNotificationTest` (7.4s)

**Fix:** Add LocalStack as a shared bean in `TestcontainersConfiguration` (same pattern as Postgres). Remove per-class `@Container` fields.

**Estimated saving:** ~20s

---

## Section 5: Post-Attempt Findings (JUnit 5 Parallel Execution)

### What We Tried

Enabled parallel class execution in `junit-platform.properties`:
```
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.config.strategy=fixed
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

### What Happened

**Initial run: 9 failures.** Fixed progressively to 1 failure, then hit another. The process was whack-a-mole — each fix required a ~3 min build cycle, and new failures surfaced after previous ones were fixed.

### Root Causes Identified

| Pattern | Tests affected | Why it breaks under parallelism |
|---------|---------------|-------------------------------|
| `@RecordApplicationEvents` | 5 classes (EventPublicationTest, PortalEventPublicationTest, BudgetAlertNotificationTest, InvoiceNotificationIntegrationTest, NotificationEventHandlerIntegrationTest) | Shared event recorder captures events from all parallel tests — assertions on "exactly N events" fail |
| `OutputCaptureExtension` | 1 class (SecurityAuditTest) | Captures all `System.out`/`System.err` globally — parallel test output bleeds in |
| `executeSchedules()` global scan | 1 class (RecurringScheduleExecutorTest) | Iterates ALL tenant schemas in the DB. Also drops FK constraints and deletes rows as test setup — visible to parallel tests |
| Unknown | Potentially more | We stopped after 2 rounds of whack-a-mole |

### Fix for Each: `@Execution(ExecutionMode.SAME_THREAD)`

This annotation forces a test class to run alone (not concurrently). Applied to 7 classes during the attempt. **But the problem is we don't know the full set** — there could be more subtle data-sharing issues that only manifest under specific parallel orderings.

### Measured Improvement (Partial)

With parallel execution enabled (4 threads) + shared LocalStack + test deletions + 7 `@Execution(SAME_THREAD)` annotations:
- **3m 07s** (187s) — down from 5m 22s baseline
- But **1 failure remained** (SecurityAuditTest), and possibly more lurk

### Recommendation

**Do not enable parallel execution without a dedicated stabilization effort.** The right approach would be:
1. Run the full suite 10+ times with parallel enabled to surface all flaky tests
2. Annotate every problematic class with `@Execution(SAME_THREAD)`
3. Consider `@ResourceLock` for tests that share global state (schedule executor, output capture)
4. Only merge when 10 consecutive green runs are achieved

**The safe wins (test deletion + shared LocalStack) should be done independently** — they provide ~20-40s improvement with zero risk.

---

## Section 6: Worktree Status

Branch `test-audit` in worktree `worktree-test-audit` contains partial work:
- Test deletions + merges (Agent 1 — **verified green**)
- Shared LocalStack + parallel execution + `@Execution` annotations (Agent 2 — **NOT green**)
- `postgres:16-alpine` image swap

**The worktree should be discarded.** To implement the safe wins, start fresh on a new branch and only apply Sections 1-2 and the shared LocalStack (Section 4).

---

## Appendix A: Top 15 Slowest Test Classes

| Rank | Time (s) | Tests | Class |
|------|----------|-------|-------|
| 1 | 29.2 | 8 | `DataSubjectRequestServiceTest` |
| 2 | 16.7 | 1 | `BackendApplicationTests` — **DELETE** |
| 3 | 12.8 | 5 | `GeneratedDocumentControllerTest` — LocalStack |
| 4 | 9.7 | 7 | `BrandingIntegrationTest` — LocalStack |
| 5 | 9.2 | 4 | `ProvisioningIntegrationTest` |
| 6 | 7.6 | 16 | `MemberSyncIntegrationTest` |
| 7 | 7.4 | 3 | `DocumentAuditNotificationTest` — LocalStack |
| 8 | 7.2 | 14 | `CustomerProjectIntegrationTest` |
| 9 | 7.2 | 7 | `DocumentGenerationIntegrationTest` — LocalStack |
| 10 | 6.7 | 5 | `S3PresignedUrlServiceTest` — LocalStack |
| 11 | 6.1 | 7 | `CommentControllerTest` |
| 12 | 5.6 | 17 | `ProjectMemberIntegrationTest` |
| 13 | 5.0 | 6 | `PlanEnforcementIntegrationTest` |
| 14 | 5.0 | 13 | `DevPortalControllerTest` |
| 15 | 4.2 | 4 | `MemberFilterIntegrationTest` |

## Appendix B: Tests NOT to Delete (Despite Appearing Redundant)

| Test | Why it looks redundant | Why to keep |
|------|----------------------|-------------|
| `AuditServiceIntegrationTest` | Overlaps controller test | Tests JSONB storage/query at service level |
| Domain audit tests (7 files) | "Creates event" duplicates domain tests | Delta capture, rollback semantics, actorType tests are unique |
| Setup status tests (8 files) | Controller + service pairs | Unit tests cover 3x more logic branches |
| View filter unit tests (5 files) | Overlaps `ViewFilterIntegrationTest` | Unit tests verify SQL string construction; IT verifies PG execution |
| `BillingRateResolutionTest` | Overlaps controller test | Tests unique 3-level resolution hierarchy algorithm |
| All provisioning tests (3 files) | Three tests for one flow | Each tests at a different layer (HTTP, service, mock) |
