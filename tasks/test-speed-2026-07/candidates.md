# Scout A — @SpringBootTest → plain-unit conversion candidates

Date: 2026-07-12. Scope: `backend/src/test/java` (~590 files containing `@SpringBootTest`).
Method: read-only sweep — recent-change list (post 2026-06-23) first, then whole-suite structural
greps (no-footprint filter, @TestPropertySource/@MockitoBean/@TestConfiguration/@DynamicPropertySource
owners, small single-bean classes). No build or test was run.

**Prior-art status checked:**
- `ShardAndSchemaParsingTest` (2026-06-14 audit item): **partially done.** A plain
  `multitenancy/ShardAndSchemaTest.java` now exists (D7 validation tests), but the original
  `@Nested class ShardAndSchemaParsingTest` (10 pure tests) **still lives inside**
  `@SpringBootTest ShardConfigMigrationTest` (lines 87–158). → Candidate 4.
- `BillingPropertiesTest` (audit item): **NOT converted.** Still full `@SpringBootTest`. → Candidate 3.
- `PackReconciliationRunnerTest` / the 10 `@MockitoBean StorageService` files: not re-proposed.

**Context-key note for reviewers:** the suite has (at least) two "baseline" contexts — the
CLAUDE.md-standard `@SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration) +
@ActiveProfiles("test")` one, and a **no-MockMvc** variant (same minus `@AutoConfigureMockMvc`) used
by several classes below. Neither dies if these candidates convert (Shard*/jobqueue/portal tests keep
the no-MockMvc context alive), so per-candidate saving = the class's own runtime (dominated by
tenant provisioning where present), not a context build.

---

## Candidates (ordered by estimated saving)

### 1. `notification/template/EmailContextBuilderTest.java`
- **Path:** `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/test/java/io/b2mash/b2b/b2bstrawman/notification/template/EmailContextBuilderTest.java`
- **What it asserts:** `EmailContextBuilder.buildBaseContext()` map contents — legal-za terminology
  keys (Fee Note / Engagement Letter), generic-tenant identity fallback, unknown-profile fallback,
  `appUrl`/`orgAppUrl` construction with/without bound ORG_ID. Pure map-assembly logic.
- **Context:** shares the no-MockMvc baseline (`@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")` — no unique annotations). Cost is **two full tenant provisions**
  (`provisionTenant(...)` × 2 in `@BeforeAll`, i.e. 2 × ~125 tenant migrations + seeders) purely to
  persist a `verticalProfile` string the builder reads back.
- **Conversion:** Mockito unit test. `EmailContextBuilder` deps are all mockable/pure
  (`OrgSettingsRepository`, `OrganizationRepository`, `StorageService`, real `EmailTerminology`
  which is a static-map component, two `@Value` strings passed as literals):
  ```java
  var settings = new OrgSettings(); settings.setVerticalProfile("legal-za");
  when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));
  var builder = new EmailContextBuilder(orgSettingsRepository, organizationRepository,
      mock(StorageService.class), new EmailTerminology(), "http://localhost:3000", "Kazi");
  var ctx = ScopedValue.where(RequestScopes.TENANT_ID, "tenant_x")
      .where(RequestScopes.ORG_ID, "org_x").call(() -> builder.buildBaseContext("Customer", null));
  ```
  `ScopedValue` binding needs no Spring; keep all five test methods verbatim, swapping the
  provisioned-tenant setup for `when(...)` stubs (unknown-profile case: `setVerticalProfile("retail-uk")`).
- **Estimated saving:** class runtime ≈ **2 tenant provisions + 5 method runs — likely the largest
  single-class win in this list** (provisioning dominates suite cost per the 2026-06 diagnosis).
- **Risk:** LOW. The DB round-trip of `verticalProfile` is exercised by many legal-za integration
  tests (e.g. `InvoiceTerminologyLegalZaTest`, `EmailNotificationChannelIntegrationTest`); the only
  coverage this class uniquely adds is the map logic, which the unit test keeps.

### 2. `template/DocxFieldValidatorTest.java`
- **Path:** `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/test/java/io/b2mash/b2b/b2bstrawman/template/DocxFieldValidatorTest.java`
- **What it asserts:** one test — `DocxFieldValidator.validateFields()` classifies
  `customer.name`/`project.name` as VALID with labels and `nonexistent.field` as UNKNOWN. All three
  paths are **static** registry fields; no custom field is ever seeded, yet the test provisions a
  full tenant just so `FieldDefinitionRepository` can return an empty list.
- **Context:** shares the no-MockMvc baseline (no unique annotations). Cost = **1 full tenant provision**.
- **Conversion:** plain JUnit + one mock:
  ```java
  var repo = mock(FieldDefinitionRepository.class);
  when(repo.findByEntityTypeAndActiveTrueOrderBySortOrder(any())).thenReturn(List.of());
  var validator = new DocxFieldValidator(new VariableMetadataRegistry(repo));
  var results = validator.validateFields(
      List.of("customer.name", "project.name", "nonexistent.field"), TemplateEntityType.PROJECT);
  // same three assertions
  ```
  (`VariableMetadataRegistry(FieldDefinitionRepository)` is the only dependency chain —
  verified in `template/VariableMetadataRegistry.java:38`.) No ScopedValue needed once the repo is mocked.
- **Estimated saving:** ≈ 1 tenant provision + class overhead.
- **Risk:** LOW. Custom-field merge behaviour of `VariableMetadataRegistry` is not asserted here at
  all (empty custom-field set either way); reviewers should confirm another test covers the
  custom-field branch (`fielddefinition`/template rendering tests) — if none does, that's a
  pre-existing gap this conversion neither widens nor hides.

### 3. `billing/BillingPropertiesTest.java`  *(2026-06-14 audit item, still open)*
- **Path:** `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/test/java/io/b2mash/b2b/b2bstrawman/billing/BillingPropertiesTest.java`
- **What it asserts:** `@ConfigurationProperties` binding of `BillingProperties` and
  `PayFastBillingProperties` — literal values from `application-test.yml` (price, trial days, URLs,
  sandbox flags). Nothing else.
- **Context:** shares the no-MockMvc baseline (`@SpringBootTest @Import(TestcontainersConfiguration.class) @ActiveProfiles("test")`).
- **Conversion:** `ApplicationContextRunner` slice — kills the full-app boot for this class:
  ```java
  new ApplicationContextRunner()
      .withUserConfiguration(EnableProps.class) // @EnableConfigurationProperties({BillingProperties.class, PayFastBillingProperties.class})
      .withPropertyValues("docteams.billing.monthly-price-cents=49900", "docteams.billing.trial-days=14", ...)
      .run(ctx -> assertThat(ctx.getBean(BillingProperties.class).monthlyPriceCents()).isEqualTo(49900));
  ```
- **Estimated saving:** small (shared context; ≈ its 3 test-method runtime + heap), but it is the
  audit-mandated conversion and removes a full-context class from the OOM-pressure set.
- **Risk:** LOW, with one honest semantics note: the runner asserts binding mechanics against
  inline properties rather than "the values in application-test.yml bind". The yml-literal check is
  low-value (any consumer test using these properties would catch drift); if reviewers want to keep
  it, point the runner at the yml via `withInitializer(new ConfigDataApplicationContextInitializer())`
  + `@ActiveProfiles`-equivalent property.

### 4. `multitenancy/ShardConfigMigrationTest.java` — extract nested `ShardAndSchemaParsingTest`  *(audit item, half-done)*
- **Path:** `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/ShardConfigMigrationTest.java` (lines 87–158)
- **What it asserts:** 10 pure value-object tests on `ShardAndSchema.parse/format/DEFAULT` +
  rejection cases — zero DB/Spring usage, but as a `@Nested` class they run inside the enclosing
  `@SpringBootTest`.
- **Context:** enclosing class shares the no-MockMvc baseline; the outer 3 migration tests
  (shard_config table exists, primary row seeded, shard_id default) are genuine migration pins and **stay**.
- **Conversion:** move the 10 tests into the existing plain `multitenancy/ShardAndSchemaTest.java`,
  deduping the overlap (its D7 tests already cover single-char/format/parse validation; the nested
  set adds parse round-trip, DEFAULT constant, missing-colon/invalid-schema/null/blank rejects).
  Delete the `@Nested` class. No new file needed.
- **Estimated saving:** small (no context killed — outer class remains), but 10 tests stop running
  under a booted context, and it closes the audit item as specified.
- **Risk:** LOW. Pure static-method tests; nothing integration-flavoured.

### 5. `s3/S3PresignedUrlServiceTest.java`
- **Path:** `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/test/java/io/b2mash/b2b/b2bstrawman/s3/S3PresignedUrlServiceTest.java`
- **What it asserts:** the static `S3PresignedUrlService.buildKey()` format, plus
  upload/download-URL generation, byte round-trip, path-traversal rejection and idempotent delete —
  **all against `InMemoryStorageService`**, the test double `TestcontainersConfiguration` registers
  as `@Primary` (`TestcontainersConfiguration.java:101`, no-arg constructor). It never touches real
  S3, so the Spring context adds nothing.
- **Context:** shares the no-MockMvc baseline (no unique annotations).
- **Conversion:** plain JUnit:
  ```java
  private final StorageService storageService = new InMemoryStorageService();
  // every existing test method unchanged
  ```
- **Estimated saving:** ≈ class runtime (~seconds) + heap; no context killed.
- **Risk:** LOW. The only thing "integration" here was Spring handing over the same test double;
  the key-validation regex parity with `S3StorageAdapter` lives in `InMemoryStorageService` itself
  and is exercised identically.

### 6. `demo/DemoWelcomeEmailServiceTest.java`
- **Path:** `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/test/java/io/b2mash/b2b/b2bstrawman/demo/DemoWelcomeEmailServiceTest.java`
- **What it asserts:** single test — `sendWelcomeEmail(...)` does not throw when `JavaMailSender`
  is absent (fire-and-forget catch-all).
- **Context:** shares the no-MockMvc baseline; 35-line class, one `@Autowired` bean.
- **Conversion:** plain JUnit + Mockito:
  ```java
  var svc = new DemoWelcomeEmailService(Optional.empty(),
      mock(EmailTemplateRenderer.class), "noreply@kazi.app", "Kazi");
  assertDoesNotThrow(() -> svc.sendWelcomeEmail("test@example.com", "Test Org",
      "test-org", "legal-za", "http://localhost:3000/org/test-org", "TempPass123!"));
  ```
  Arguably stronger than today: with `Optional.empty()` injected explicitly, the test no longer
  depends on the test profile happening to not configure a mail sender.
- **Estimated saving:** ≈ 1 short test's runtime; cheapest win, near-zero effort.
- **Risk:** LOW.

---

## Rejected near-misses (read; do not redo)

- `collections/AiReminderComposerTest` — owns a unique context (`@MockitoBean IntegrationRegistry`) but the value IS the scan→composer→gate→activity pipeline over a provisioned tenant with `StubAiProvider`; assertions hit DB rows and gate lifecycle. Not pure logic.
- `collections/CashDigestDataTest` — deterministic-numbers assembly, but the determinism under test is SQL (aging buckets, 7-day windows via JDBC-pinned dates, stale-WIP joins). DB is the subject.
- `mcp/McpReadOnlyRegistryTest` — value is the LIVE Spring-registered `@McpTool` bean set + MockMvc `initialize` capability handshake; a hand-built registry defeats the guard.
- `mcp/Phase81BoundaryTest` — half is tenant isolation over two provisioned tenants (needs DB); the pom/source-scan half rides along on the shared context for ~free. Splitting buys nothing.
- `invoice/InvoiceTerminologyLegalZaTest` — terminology asserted through the REAL `InvoiceRenderingService` HTML output and `InvoiceValidationService` messages on a provisioned legal-za tenant; that pipeline is the point.
- `proposal/ProposalSentEmailHandlerTest` — GreenMail AFTER_COMMIT email flow driven via `POST /api/proposals/{id}/send`. Explicitly non-convertible category.
- `automation/AiSpecialistAutomationPackValidityTest` — no DB/provisioning, tempting; but it validates seeded pack JSON against the REAL Spring-collected `SpecialistRegistry(List<Specialist>)`. A manually-constructed specialist list would silently drift from production wiring — the drift is the bug class this test exists to catch (OBS-505).
- `config/PortalDataSourceConfigIntegrationTest` — portal datasource/`JdbcClient` wiring + real INSERT/SELECT against `portal.*` tables.
- `billing/SubscriptionEntityTest`, `infrastructure/jobqueue/JobQueueEntityTest` — persistence-defaults tests (`saveAndFlush` → column defaults); DB is the subject.
- `infrastructure/jobqueue/JobEnqueuerDisabledTest` — asserts no rows written when the queue flag is off; needs the real repo.
- `multitenancy/ShardRegistryTest` — `@TestPropertySource(kazi.sharding.enabled=true)` is justified in-file per CLAUDE.md policy; asserts Spring-managed DataSource identity (`isSameAs(primaryDataSource)`) — meaningless without the context.
- `multitenancy/EventListenerShardScopeTest` — reproduces `TransactionSynchronization.afterCommit` timing on the publishing thread; needs a real `PlatformTransactionManager`.
- `multitenancy/Shard*`/`SingleShard*Characterization`/jobqueue `SchedulerMigration*`/`JobWorker*`/`ObservabilityTest`/`EndToEndMultiShardTest` — shard/scheduler/worker infra; the `@TestPropertySource` unique contexts are the cost of genuinely different wiring, not convenience.
- `billing/BillingMethodTest` — already the model pattern: 19 plain unit tests outer + `@Nested @SpringBootTest PersistenceTests` inner. No action.
- `crm/DealTransitionServiceTest`, `crm/PipelineSummaryServiceTest`, `collections/*ServiceTest`, `correspondence/*`, `mcp/*Tool*`/`*TenantIsolation*`, `verticals/legal/closure/*` (recent-change set) — all provision tenants and/or drive MockMvc; integration is the value.
- The 41 `@MockitoBean`/`@SpyBean` classes (unique contexts): every one also uses provisioning, repositories, or MockMvc (verified by token profile) — the mock isolates an external (Xero, AI, mail), not the DB. Converting loses the integration.
- Already-plain (verified, no action): `CustomerAuthFilterDevPortalGateTest`, `mcp/tool/ClientToolsCeilingTest`, `multitenancy/ShardAndSchemaTest`, `activity/ActivityMessageFormatterTest`, `audit/AuditEventTypeRegistryTest`, `collections/ReminderHtmlSanitizerTest`, `collections/CollectionsCoreBoundaryTest`, `notification/template/EmailTerminologyTest`, `template/VariableFormatterTest`, `verticals/legal/collections/TrustAwareCollectionsAdvisorTest`.
- `settings/OrgSettingsSchemaSnapshotTest` — schema pin, categorically excluded.
