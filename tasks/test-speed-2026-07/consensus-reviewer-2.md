# Consensus Reviewer 2 — @SpringBootTest conversion candidates + GreenMail dynamic port

Date: 2026-07-12. Independent adversarial review. Read-only verification: every claim below was
checked against source (file:line cited); no build or test was run. House rules applied: integration
value (DB constraints, tenant isolation, bean discovery, migration shape, email flows) is not
convertible; sibling-coverage claims verified by grep, not trusted.

---

## Item 1 — `notification/template/EmailContextBuilderTest` → Mockito unit test

**Verdict: APPROVE**

- Every assertion maps to pure logic I verified in `EmailContextBuilder.java`: terminology map +
  convenience keys are `EmailTerminology.resolve()` over a static `Map.of` (`EmailTerminology.java:20-45`)
  plus `getOrDefault` puts (`EmailContextBuilder.java:90-104`); `appUrl`/`orgAppUrl` are the `@Value`
  default + `RequestScopes.getOrgIdOrNull()` concat (`EmailContextBuilder.java:85-86,116-119`).
  The asserted literal `http://localhost:3000` is the `@Value` default (`EmailContextBuilder.java:40`);
  `docteams.app.base-url` is absent from `application-test.yml` (verified — `docteams:` block at
  line 95 has only email keys), so the unit test's constructor literal preserves the assertion's meaning.
- What Spring/DB uniquely proved here — that `provisionTenant(..., "legal-za")` persists
  `verticalProfile` and `OrgSettingsRepository.findForCurrentTenant()` resolves it per-tenant — is
  covered by grep-verified siblings: `InvoiceTerminologyLegalZaTest.java:60,72` provisions legal-za +
  generic tenants and asserts "Fee Note: DRAFT" present/absent through the real pipeline
  (lines 119, 134), plus 8+ other tests provisioning legal-za tenants.
- The two provisioning calls in `@BeforeAll` (2 × ~125 tenant migrations) contribute setup only,
  not proof. Cost/benefit strongly favors conversion.

**Mandatory implementation notes:**
1. `OrgSettings` has **no accessible no-arg constructor** — the scout's sketch `new OrgSettings()`
   will not compile from the test package. Use `new OrgSettings("ZAR")` (`OrgSettings.java:328`)
   then `setVerticalProfile(...)` (`OrgSettings.java:525`).
2. Mock `OrganizationRepository` as well (`resolveOrgName()` calls `findByClerkOrgId`,
   `EmailContextBuilder.java:126`); return `Optional.empty()` — `orgName` is not asserted.
3. The `mock(StorageService.class)` is never invoked: `settings.getBranding()` lazy-inits (never
   null per the OrgSettings embeddable contract) and `getLogoS3Key()` null short-circuits
   `generateLogoUrl` (`EmailContextBuilder.java:132-134`). No stubbing needed.

## Item 2 — `template/DocxFieldValidatorTest` → plain JUnit + mocked repo

**Verdict: APPROVE**

- The three assertions map to static registry entries — `project.name`/"Project Name"
  (`VariableMetadataRegistry.java:124`), `customer.name`/"Customer Name" (line 138) — and the
  VALID/UNKNOWN classification loop in `DocxFieldValidator.java:44-54`. The provisioned tenant
  exists solely so `findByEntityTypeAndActiveTrueOrderBySortOrder` can return an empty list
  (`VariableMetadataRegistry.java:81-84` — empty list → early return, no custom groups).
- Dependency chain verified: `DocxFieldValidator(VariableMetadataRegistry)` →
  `VariableMetadataRegistry(FieldDefinitionRepository)` (`VariableMetadataRegistry.java:38`).
  Nothing else is wired.
- The scout's open question ("confirm another test covers the custom-field branch") is **resolved
  affirmatively by grep**: `template/VariableMetadataEndpointTest.java` seeds real
  `FieldDefinition` rows via the repository (lines 13-14, 41) and asserts the merged
  `*.customFields` groups for PROJECT, CUSTOMER, and INVOICE templates (lines 78-147). No gap
  is widened or hidden.

**Mandatory implementation note:** stub the repo for **both** EntityTypes the PROJECT template
queries (PROJECT and CUSTOMER, `VariableMetadataRegistry.java:61-63`) — the scout's
`when(...(any())).thenReturn(List.of())` does this correctly; don't narrow it to `EntityType.PROJECT`.

## Item 3 — `billing/BillingPropertiesTest` → `ApplicationContextRunner` slice

**Verdict: APPROVE** — but ONLY in the yml-pointed variant the scout offered; the inline-properties
primary sketch is a real coverage loss.

- The current test pins **production defaults from `application.yml`**, not just test overrides:
  `monthly-price-cents: 49900`, `trial-days: 14`, `grace-period-days: 60`, `currency: ZAR`,
  `item-name: "HeyKazi Professional"`, `max-members: 10` live at `application.yml:178-185`;
  `application-test.yml:165-174` overrides only the three URLs + payfast credentials. The scout's
  claim that "any consumer test using these properties would catch drift" is **false by grep**:
  `PlatformPayFastServiceTest` mocks `BillingProperties` (line 52-53), and `SubscriptionEntityTest`/
  `SubscriptionItnIntegrationTest`'s 49900s are test-data circularity, not binding pins. With inline
  `withPropertyValues`, the price pin silently evaporates and the test becomes a tautology about
  Spring's binder.
- Bean *discovery* is not lost: `BillingConfig`/`PayFastBillingConfig` (`@EnableConfigurationProperties`,
  verified) load in the shared full context of every other `@SpringBootTest`, and
  `PlatformPayFastService` injects both records — a vanished bean fails the whole suite.

**Mandatory implementation notes:**
1. Use `.withInitializer(new ConfigDataApplicationContextInitializer())` +
   `.withPropertyValues("spring.profiles.active=test")` so the runner binds from the real
   `application.yml` + `application-test.yml` merge — this preserves every existing assertion's
   meaning (env-placeholder URLs in `application.yml:186-190` are overridden by the test yml, so
   binding resolves cleanly; the runner creates only the two properties beans, so other yml keys
   are inert). Inline-only property values are NOT an acceptable substitute.
2. The prefix is **`heykazi.billing`** (`BillingProperties.java:18`) / **`heykazi.billing.payfast`**
   (`PayFastBillingProperties.java:13`) — the scout's sketch says `docteams.billing.*`, which would
   bind nothing.

## Item 4 — extract `@Nested ShardAndSchemaParsingTest` out of `ShardConfigMigrationTest`

**Verdict: APPROVE**

- The 10 nested tests (`ShardConfigMigrationTest.java:87-157`) touch only `ShardAndSchema.parse/
  format/DEFAULT` and constructor rejects. `ShardAndSchema.java` is a plain record + two compiled
  `Pattern`s with zero Spring/DB imports (verified whole file) — nothing integration-flavoured.
- The outer class keeps everything that IS integration: shard_config table existence (line 37),
  seeded primary row (line 47), shard_id column default via `saveAndFlush` (line 57), and the
  entity round-trip (line 67). The proposal keeps the outer class; no migration pin is lost.
  (Minor audit correction: the outer class has **4** tests, not 3 — `shardConfigEntityRoundTrip`
  also stays.)
- Merge target `multitenancy/ShardAndSchemaTest.java` exists and is plain JUnit (verified); overlap
  is small (D7 file covers single-char/format/parse acceptance + length/underscore rejects; the
  nested set adds round-trip, DEFAULT, missing-colon/invalid-schema/null/blank rejects). Dedupe is
  mechanical.

## Item 5 — `s3/S3PresignedUrlServiceTest` → plain JUnit over `new InMemoryStorageService()`

**Verdict: APPROVE**

- Verified: the injected `StorageService` in the test context IS the test double —
  `TestcontainersConfiguration.java:98-102` registers `InMemoryStorageService` as `@Primary`, and
  `InMemoryStorageService` has an implicit no-arg constructor (whole file read). Every assertion
  (URL contains key, byte round-trip, traversal reject, idempotent delete) executes against the
  double's own code (`InMemoryStorageService.java:20-74`); `buildKey` is static
  (`S3PresignedUrlService.java:12`). Spring contributed only the handover of the same object —
  `new InMemoryStorageService()` is behaviourally identical.
- The real adapter (`integration/storage/s3/S3StorageAdapter.java`, own `validateKey` regex at
  lines 35/122) is untested by this class today and remains untested after — a pre-existing gap
  the conversion neither widens nor hides.

**Mandatory implementation note:** fix two **stale/false comments** while converting — the test-infra
javadoc claims this very test "needs real presigned URL HTTP round-trips"/"uses its own LocalStack
setup" (`InMemoryStorageService.java:14-15`, `TestcontainersConfiguration.java:32`), which the test
file disproves (no LocalStack anywhere in it). Leaving them invites someone to "restore" LocalStack.

## Item 6 — `demo/DemoWelcomeEmailServiceTest` → plain JUnit + Mockito

**Verdict: APPROVE**

- One honest correction to the test's own comment: in the test profile `spring.mail.host` IS set
  (`application-test.yml:60-62`), so `MailSenderAutoConfiguration` supplies a `JavaMailSender` and
  the current test runs the Optional-**present** path — a real render + SMTP attempt at 13025 —
  not the `Optional.empty()` path its comment describes. This changes nothing provable: the entire
  send body is wrapped in `catch (Exception e)` (`DemoWelcomeEmailService.java:65-103`), so
  `assertDoesNotThrow` is satisfied by construction on every path; the current test proves nothing
  the catch-all doesn't guarantee. The proposed unit test asserts the same contract (never throws)
  while pinning the documented skip branch (`DemoWelcomeEmailService.java:58-63`), and it removes an
  accidental live SMTP dependency. Constructor signature matches the sketch exactly
  (`DemoWelcomeEmailService.java:30-34`); `EmailTemplateRenderer` is a non-final class
  (`EmailTemplateRenderer.java:23`), mockable.
- Optional (not required): add a second case with a present mock sender + mock renderer to cover
  the try-path shape; today that path has no assertable observable anyway.

## Item 7 — GreenMail dynamic port (flakiness-fixes.md Lever 5)

**Verdict: APPROVE**

- **13025 grep (whole backend: src main+test, pom.xml; no scripts/ dir exists):** functional
  occurrences are exactly the two the scout names — `GreenMailTestSupport.java:25`
  (`new ServerSetup(13025, ...)`) and `application-test.yml:62`. All 8 other test-file hits are
  comments/javadoc only (verified each line individually); zero hits in `src/main` or `pom.xml`.
  No production or test code reads `spring.mail.port` programmatically — the only `spring.mail.*`
  reference in Java is `@ConditionalOnProperty(name = "spring.mail.host")`
  (`SmtpEmailProvider.java:24`), host not port, unaffected.
- **Audit correction (scout's file attribution is wrong):** `InvoiceEmailFallbackGuardIntegrationTest`
  (212 lines) contains **no** mid-test `stop()`/`start()` — it only `purgeEmailFromAllMailboxes()`
  in `@BeforeEach` (line 145). The real stop/start sites are
  `PortalEmailServiceIntegrationTest.java:137/151` and `:168/179`, and
  `EmailNotificationChannelIntegrationTest.java:160` — all try/finally, all restarting the SAME
  singleton `GreenMail` instance, which rebinds the same `ServerSetup` (and therefore the same
  dynamically-chosen port). The pattern is safe under a dynamic port.
- **Context-cache key:** claim verified in principle and in practice. `MergedContextConfiguration`
  hashes configuration sources, not resolved `Environment` values; the `${greenmail.smtp.port:13025}`
  placeholder resolves at refresh from the system property. Grep confirms no test sets any
  `spring.mail.*` via `@TestPropertySource`/`@DynamicPropertySource`, and `src/test/resources`
  contains no other yml or `junit-platform.properties` that could interfere. Because the listener
  sets the property once before any context is built, every cached context resolves the same value —
  single key preserved.
- **IDE single-test run:** two independent safety nets. (a) The `LauncherSessionListener` fires under
  any JUnit Platform launcher (IntelliJ, Surefire) via ServiceLoader. (b) Even without it, email test
  classes hold `private static final GreenMail greenMail = GreenMailTestSupport.getInstance()`
  static fields (e.g. `InvoiceEmailFallbackGuardIntegrationTest.java:57`), so class init — and the
  system property — precedes that class's context creation. The listener's real job is the suite
  ordering hazard (non-email class builds the shared context first), which it closes. Failure mode
  if the services file is mis-registered is loud (email assertions fail), not silent.
- Precedent already in-tree: `SmtpEmailProviderIntegrationTest.java:18` runs GreenMail on
  `ServerSetupTest.SMTP.dynamicPort()` without Spring — unaffected by and consistent with this change.

**Mandatory implementation notes:**
1. Do **not** allocate via bare `new ServerSocket(0)`: that draws from the OS ephemeral range, which
   is also the source-port range for outbound sockets (Hikari/embedded-Postgres churn constantly with
   `minimum-idle=0`). During the three mid-test stopped windows above, the OS could hand GreenMail's
   port to an outbound connection and `greenMail.start()` would throw BindException — a new rare
   flake in the exact campaign meant to kill flakes. Pick a random port below the ephemeral range
   (e.g. try-bind in ~15000–32000, loop on failure) instead.
2. Update the wrong file name in the Lever 5 audit paragraph (fallback-guard test → the two portal/
   notification tests) before it gets copied into commit messages.
3. `GreenMailTestSupport.reset()` (line 46-48) also internally stop/starts the server — currently it
   has zero callers (grep), but the same same-port rebind reasoning covers it; no change needed.

---

## Summary

| # | Item | Verdict |
|---|------|---------|
| 1 | EmailContextBuilderTest → unit | APPROVE (notes: OrgSettings ctor, OrganizationRepository mock) |
| 2 | DocxFieldValidatorTest → unit | APPROVE (sibling custom-field coverage verified) |
| 3 | BillingPropertiesTest → runner slice | APPROVE (mandatory: ConfigData initializer + heykazi prefix) |
| 4 | ShardAndSchemaParsingTest extraction | APPROVE (outer 4 DB tests stay) |
| 5 | S3PresignedUrlServiceTest → unit | APPROVE (fix stale LocalStack javadocs) |
| 6 | DemoWelcomeEmailServiceTest → unit | APPROVE (current test is tautological on both paths) |
| 7 | GreenMail dynamic port | APPROVE (mandatory: non-ephemeral-range port; audit file-name fix) |
