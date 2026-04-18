# Lessons Learned

## Surefire JVM Hang — HikariPool Housekeeping Threads (2026-02-20)

**Symptom**: After all tests complete, the Surefire forked JVM hangs indefinitely (2+ hours observed). Test report files stop being written but the JVM process stays alive at 0% CPU.

**Root cause**: HikariCP connection pool housekeeping threads (daemon threads) are kept alive by something holding a non-daemon thread reference. The JVM won't exit because non-daemon threads are still running. The `surefire-forkedjvm-last-ditch-daemon-shutdown-thread-60s` thread sometimes triggers but doesn't always terminate the process.

**Impact**: Blocks the entire `/phase` pipeline — the `run-phase.sh` script waits for the claude process, which waits for the Surefire process.

**Workaround**: `kill <surefire-pid>` — the agent gets a non-zero exit and can re-run or proceed.

**Potential fix**: Add `spring.datasource.hikari.register-mbeans: false` and ensure `@DirtiesContext` or explicit pool shutdown in test teardown. Alternatively, configure Surefire with `<forkedProcessExitTimeoutInSeconds>120</forkedProcessExitTimeoutInSeconds>` to auto-kill hung forks.

**Detection**: If a Surefire process has been running 60+ minutes with 0% CPU and no new TEST-*.xml files in the last 30 minutes, it's hung.

## Detail Section Row Must Be Marked Done (2026-02-20)

**Symptom**: `run-phase.sh` crashes or re-runs a completed slice because it doesn't detect it as Done.

**Root cause**: The `/epic_v2` skill instructed agents to mark Done in "the row starting with `| **{SLICE}** |`" but agents were updating the Implementation Order table (which has format `| 2a | Epic 116 | 116A |`) instead of the Detail Section rows (which have format `| **116A** | 116.1–116.10 |`).

**Fix**: Updated `/epic_v2` SKILL.md to explicitly require updating FOUR locations: (1) Detail Section row (most critical), (2) Implementation Order row, (3) Epic Overview, (4) TASKS.md.

**Detection**: Run `./scripts/run-phase.sh {N} --dry-run` — if "Done" count doesn't match expected, check detail section rows.

## Backend Test Speed — Context-Cache Evictions (2026-04-18)

**Symptom**: `./mvnw test` wall time regressed from ~13m (post-April optimization) to ~10m 48s over a 3-week period, despite no Testcontainers/LocalStack violations.

**Root cause**: Every unique `@TestPropertySource`, `@DynamicPropertySource`, and `@MockitoBean`/`@SpyBean` combination creates a distinct Spring ApplicationContext cache key. Even with the Zonky embedded Postgres singleton shared across contexts, Spring still rebuilds beans + Hibernate + Flyway on each cache miss (~2–3s per rebuild). 27 unique combinations cost ~60–80s of wasted time per run.

**Fix pattern**:
- Move static test overrides into `backend/src/test/resources/application-test.yml` instead of per-class `@TestPropertySource`. Example: `spring.mail.*` for GreenMail was moved there, letting 3 email tests share one context.
- For `@DynamicPropertySource` used to inject runtime-generated values (e.g., ECDSA keypairs), only keep it if the runtime generation is structurally required — prefer a hardcoded static fake when safe.
- For `@MockitoBean` duplicated across 3+ tests with identical behaviour, extract a `@TestConfiguration` class and `@Import` it so tests share one context.

**Anti-patterns to reject in review**:
- Adding `@TestPropertySource` for a value that could live in `application-test.yml`.
- Adding `@DynamicPropertySource` where a static value would work.
- Copy-pasting GreenMail port definitions — use the test default `spring.mail.port=13025`.
- Classifying a `*ControllerTest` and `*IntegrationTest` as "duplicates" based on filename — verify they actually cover the same layer before deleting either.

**Detection**: `grep -rn "@TestPropertySource\|@DynamicPropertySource\|@MockitoBean" backend/src/test/java | wc -l` — if this count grows in a PR, scrutinize.

## Backend Test Speed — JaCoCo Gated Behind `-Pcoverage` Profile (2026-04-18)

**Background**: JaCoCo instrumentation adds ~10–15% runtime overhead on every test run. Default CI `./mvnw test` and local dev loops don't need the coverage report — release/nightly can opt in.

**Change**: `backend/pom.xml` moved `jacoco-maven-plugin` from `<build><plugins>` into a `<profile id="coverage">`. Default `./mvnw test` skips instrumentation. Use `./mvnw -Pcoverage test` when a coverage report is required.

**Migration note**: If CI later adds Codecov/SonarQube integration, enable the `coverage` profile specifically for that job — do NOT re-add JaCoCo to the default build.

## @MockitoBean Audit — 2026-04-18

**Baseline**: 19 `@MockitoBean` declarations across 18 test files. Each creates a distinct Spring-context cache key, so every new mock-bean combination costs ~2–3s for a context rebuild.

**Removed (unused declarations, 2 files)**:
- `PortalPaymentStatusIntegrationTest` had `@MockitoBean StorageService storageService` with no references in the test body — dead code. Removed.
- `PlatformAdminControllerTest` had `@MockitoBean JavaMailSender javaMailSender` with no references. Removed.

**Retained with rationale (17 declarations across 16 files)**:
- **Bespoke stubbing / verification (9 files)**: `DataExportServiceTest`, `AnonymizationControllerTest`, `DataAnonymizationServiceTest`, `DataExportControllerTest`, `InvitationServiceTest`, `OrgCreationControllerTest`, `DemoCleanupServiceTest`, `DemoProvisionServiceTest`, `AccessRequestApprovalServiceTest`. Each test stubs a different method subset with different returns or uses `verify()` + `ArgumentCaptor`. Not safely consolidable without losing mock isolation between test methods.
- **Future migration candidates (deferred)**:
  - `PortalBrandingControllerIntegrationTest`, `PortalInvoiceControllerIntegrationTest`, `PortalCommentPostIntegrationTest` — all three only stub `storageService.generateDownloadUrl()`. They could drop `@MockitoBean` entirely by using `InMemoryStorageService` with keys that pass its regex (update test data + change assertions from `https://s3.example.com/...` to `http://test-storage/test-bucket/...`). ~6s potential saving.
  - `AccessRequestPublicControllerTest`, `AccessRequestVerifyTest` — both stub `JavaMailSender.createMimeMessage()` identically. Now that `GreenMailTestSupport` provides a JVM-singleton SMTP server on port 13025, these could send real mail and assert on `greenMail.getReceivedMessages()` instead. ~4s potential saving.
- **Single-use (legitimate)**: `SubscriptionItnIntegrationTest` mocks `PlatformPayFastService`; `AssistantControllerTest` + `AssistantServiceTest` mock `LlmChatProviderRegistry` (the service mock comment says "Mock the entire registry to avoid the duplicate provider ID issue").

**Why not force a shared `@TestConfiguration` for Keycloak mocks**: attempted mentally — the 4 Keycloak tests stub different method subsets, so a shared mock instance would need manual `Mockito.reset()` between methods. That trades ~6–9s of context rebuild for the risk of stub-leakage flakiness. Not a net win.


