# Consensus Reviewer 1 — @SpringBootTest conversion candidates + GreenMail dynamic port

Date: 2026-07-12. Method: read-only source verification (file reads + grep only; no builds run).
Every claim below was personally verified in the source at the cited file:line.

---

## 1. `notification/template/EmailContextBuilderTest` → Mockito unit test — **APPROVE**

- Constructor verified: `EmailContextBuilder(OrgSettingsRepository, OrganizationRepository, StorageService, EmailTerminology, String appBaseUrl, String productName)` (`EmailContextBuilder.java:35-41`) — sketch arity/order correct. `EmailTerminology` is a pure static-map component with no deps (`EmailTerminology.java:20-33`), safe to instantiate directly.
- Everything the assertions touch is map assembly over `settings.getVerticalProfile()` + `RequestScopes.getOrgIdOrNull()` (`EmailContextBuilder.java:90-104,116-118`; `RequestScopes.java:105`). The DB contributes only the persisted `verticalProfile` string; `findForCurrentTenant()` returns `Optional<OrgSettings>` (`OrgSettingsRepository.java:14`) — a stub reproduces it exactly. `ScopedValue.where(...)` needs no Spring.
- Lost integration coverage (provisioning persists `verticalProfile`; tenant-scoped settings query) is covered elsewhere: `InvoiceTerminologyLegalZaTest`, `InvoiceEmailFallbackGuardIntegrationTest`, `InvoiceSentDocumentPersistenceIntegrationTest` all assert "Fee Note" through a provisioned legal-za tenant (grep-verified), and `TenantProvisioningServiceIntegrationTest` provisions legal-za.
- **Mandatory note:** the sketch's `new OrgSettings()` does NOT compile — the no-arg constructor is `protected` (`OrgSettings.java:314`) and the test lives in a different package. Use the public `new OrgSettings("ZAR")` (`OrgSettings.java:328`). Branding group lazy-inits (null logo key → `orgLogoUrl` null), so no further stubbing needed.
- **Mandatory note:** Mockito's default answer returns `Optional.empty()` for `organizationRepository.findByClerkOrgId(...)` — `orgName` falls back to `productName`, which no assertion touches. Do not add stubs for it.

## 2. `template/DocxFieldValidatorTest` → plain JUnit + one mock — **APPROVE**

- Chain verified: `DocxFieldValidator(VariableMetadataRegistry)` (`DocxFieldValidator.java:21`), `VariableMetadataRegistry(FieldDefinitionRepository)` (`VariableMetadataRegistry.java:38`), repo method name `findByEntityTypeAndActiveTrueOrderBySortOrder` exact (`VariableMetadataRegistry.java:82`). Sketch compiles conceptually.
- All three asserted paths are static registry entries (`customer.name`→"Customer Name" `VariableMetadataRegistry.java:138`, `project.name`→"Project Name" line 124); no custom field is ever seeded, so the provisioned tenant only supplies an empty `FieldDefinition` list — the mock returns the same.
- The custom-field merge branch (`appendCustomFieldGroups`) the scout asked reviewers to confirm IS covered elsewhere: `VariableMetadataEndpointTest` seeds `FieldDefinition`s via `FieldDefinitionRepository` and asserts `project.customFields` / `customer.customFields` groups through the endpoint (`VariableMetadataEndpointTest.java:78-147,221-236`). No coverage lost.
- The tenant `ScopedValue` binding in the current test exists only so the repo query can run; with the repo mocked it is dead weight — dropping it is correct.

## 3. `billing/BillingPropertiesTest` → `ApplicationContextRunner` slice — **APPROVE**

- Records verified: `BillingProperties` prefix `heykazi.billing` (`BillingProperties.java:18`), `PayFastBillingProperties` prefix `heykazi.billing.payfast` (`PayFastBillingProperties.java:13`); accessor names match the test's assertions.
- **Mandatory note — sketch error:** candidates.md line 88 uses `docteams.billing.*` property keys; the real prefix is `heykazi.billing.*`. Mechanical fix, does not change the proposal.
- **Mandatory note — do NOT use inline `withPropertyValues` for the value assertions.** The asserted numbers/names (49900, 14, 60, ZAR, "HeyKazi Professional", 10) come from the PRODUCTION `src/main/resources/application.yml:180-185` (application-test.yml:165-174 only overrides URLs + payfast creds). Inline properties would turn the test into a tautology (assert the value you injected) and silently drop the production-config pin. Use the option the proposal itself offers: `withInitializer(new ConfigDataApplicationContextInitializer())` + `spring.profiles.active=test`, so the runner binds from the real yml chain. With that variant the test keeps proving exactly what it proves today, minus the full-app boot.
- Registration-loss risk (deleting `BillingConfig`'s `@EnableConfigurationProperties`, `BillingConfig.java:7`) is caught broadly: `SubscriptionService`, `MemberSyncService`, `SubscriptionExpiryJob` inject `BillingProperties` as a bean, so any full-context test fails at boot if registration breaks. Verified by grep.

## 4. Extract `@Nested ShardAndSchemaParsingTest` from `ShardConfigMigrationTest` — **APPROVE**

- The 10 nested tests (`ShardConfigMigrationTest.java:87-157`) call only `ShardAndSchema.parse/format/DEFAULT` and the record constructor — all static/pure (`ShardAndSchema.java:32,41,68`); zero use of the enclosing class's repositories, `JdbcTemplate`, or `@Transactional`. Nothing integration-flavoured.
- The outer 4 tests (migration table pin, seeded primary row, shard_id default, entity round-trip, lines 36-85) genuinely need the DB and stay — the shared no-MockMvc context survives, exactly as the scout states.
- Overlap with the existing plain `ShardAndSchemaTest.java` is partial only (D7 constructor validation vs. the nested set's parse round-trip / DEFAULT / missing-colon / null / blank rejects); merging all 10 in loses nothing and duplicates nothing exactly.
- **Mandatory note:** move tests verbatim (they reference no instance state); delete only the `@Nested` class, not the outer class.

## 5. `s3/S3PresignedUrlServiceTest` → plain JUnit with `new InMemoryStorageService()` — **APPROVE**

- Verified: the autowired `StorageService` in the current Spring run IS `InMemoryStorageService` — registered `@Primary` with no-arg construction (`TestcontainersConfiguration.java:98-102`). The test never touches real S3; Spring's only contribution is handing over the same test double the plain test would `new` up.
- All five test methods exercise `InMemoryStorageService` behaviour (URL format includes the key, `InMemoryStorageService.java:57-68`; key regex `:20-22`; map-backed round-trip/delete `:24-54`) plus the static `S3PresignedUrlService.buildKey` (`S3PresignedUrlService.java:12`). Identical semantics unit-side.
- No coverage lost: the real `S3StorageAdapter` presign/validation path was never exercised by this class (it got the in-memory `@Primary` bean), so nothing changes for production-code coverage.
- **Mandatory note:** fix the stale/false comments while converting — `InMemoryStorageService.java:14-15` and `TestcontainersConfiguration.java:32` both claim "S3PresignedUrlServiceTest … needs real presigned URL HTTP round-trips / uses its own LocalStack setup"; the test file (`S3PresignedUrlServiceTest.java:15-21`) has no LocalStack and autowires the in-memory bean. Also update the test's own javadoc ("Tests the StorageService (S3StorageAdapter)") which is wrong today.

## 6. `demo/DemoWelcomeEmailServiceTest` → plain JUnit + Mockito — **APPROVE**

- Constructor verified: `DemoWelcomeEmailService(Optional<JavaMailSender>, EmailTemplateRenderer, String senderAddress, String productName)` (`DemoWelcomeEmailService.java:30-34`) — sketch arity correct.
- **Mandatory note — the sketch as written NARROWS branch coverage; add a second case.** The test's comment "JavaMailSender may not be configured" is false in the current Spring run: `application-test.yml:60-62` sets `spring.mail.host/port`, so Boot auto-configures a `JavaMailSender` and the current test exercises the render+send try/catch path (`DemoWelcomeEmailService.java:65-103`), not the `isEmpty()` early return (line 58). `Optional.empty()` alone would only test the early return. Required shape: (a) `Optional.empty()` → no throw (early return); (b) `Optional.of(mock(JavaMailSender.class))` with the renderer or `send()` stubbed to throw → no throw (the fire-and-forget catch-all at line 97, the actual production contract). Both are trivial; the conversion then covers strictly MORE than today, deterministically.
- With that note applied, the Spring context contributes nothing: no DB, no tenant, single bean with mockable deps.

## 7. GreenMail dynamic port (Lever 5: sysprop + yml placeholder + LauncherSessionListener) — **APPROVE**

- **Cache-key claim: verified correct.** `MergedContextConfiguration` equality hashes configuration SOURCES (test classes, profiles, initializers, `@TestPropertySource` locations/properties, customizers, loader, parent) — `application-test.yml` participates only via the shared `test` profile and its *contents* never enter the key; even today's literal `13025` is not in the key. Swapping it for `${greenmail.smtp.port:13025}` (yml line 62) keeps exactly one baseline cache key. The suite's own design already relies on this (yml comment lines 53-59).
- **Ordering claim: verified correct and genuinely the hinge.** `spring.mail.port` is bound into `MailProperties` at context refresh and the resolved value is frozen in the cached context. `GreenMailTestSupport` is lazily initialized today (`GreenMailTestSupport.java:20`, static init on first class touch); without forced init, a non-email test building the shared context first would freeze the `:13025` fallback while GreenMail later binds a random port → silent cascade. The `LauncherSessionListener` forcing `getInstance()` at session open closes this for Surefire AND IDE runs (JUnit Platform ≥1.8 ServiceLoader-discovers `LauncherSessionListener` from the test classpath; `src/test/resources/META-INF/services/` does not yet exist — grep-verified — so no registration conflicts).
- **Port-literal audit: verified complete.** Whole-tree grep for `13025`: only two functional sites (`GreenMailTestSupport.java:25`, `application-test.yml:62`); the 8 Java hits listed in the proposal are all comments/javadoc, exactly as claimed. No test asserts the literal port or opens its own socket to 13025. `SmtpEmailProviderIntegrationTest` runs its own `GreenMailExtension(...dynamicPort())` and reads `greenMail.getSmtp().getPort()` from that extension — unaffected. `InvoiceEmailFallbackGuardIntegrationTest`'s mid-test stop/start uses the singleton (`:57`), whose retained `ServerSetup` rebinds the same resolved port — unaffected.
- **Mandatory note — the proposal is missing a required pom edit.** `org.junit.platform:junit-platform-launcher` is NOT a compile-time test dependency: `spring-boot-starter-test:4.0.2` pom declares `junit-jupiter` + `mockito-junit-jupiter` but no launcher (verified in the resolved pom), and `junit-jupiter:6.0.3 → junit-jupiter-engine → junit-platform-engine` never reaches the launcher artifact. The 6.0.x launcher jars in the local repo are resolved by Surefire's provider at RUNTIME only. `GreenMailLauncherSessionListener implements LauncherSessionListener` will not compile without adding `junit-platform-launcher` (test scope, version managed by the JUnit BOM via the Boot parent). This contradicts flakiness-fixes.md's "no pom change is proposed at all" (line 9) and "no pom.xml edit" (line 210) — but it is an enabling one-liner in `<dependencies>`, not a change to the port mechanism, cache-key story, or Surefire config the "no pom edit" claim was actually protecting. I judge it an implementation note, not a substance change.
- **Mandatory note — behaviour change to document:** GreenMail currently starts lazily (only when an email test first touches the class); with the listener it starts eagerly in EVERY test JVM, including single non-email IDE runs and targeted `-Dtest=` runs (~sub-second cost, one ephemeral socket). Acceptable, but say so in the `backend/CLAUDE.md` update so nobody "optimizes" the listener away later.
- Residual accepted risks match the proposal's own framing: the loud port-collision signature that today reveals illegal concurrent verifies disappears — Lever 3's preflight refusal remains the guard for that policy; keep the `:13025` yml fallback as proposed.

---

## Summary

| # | Item | Verdict |
|---|------|---------|
| 1 | EmailContextBuilderTest → unit | APPROVE (fix `new OrgSettings()` → `new OrgSettings("ZAR")`) |
| 2 | DocxFieldValidatorTest → unit | APPROVE |
| 3 | BillingPropertiesTest → ApplicationContextRunner | APPROVE (prefix is `heykazi.*`; MUST bind from real yml via `ConfigDataApplicationContextInitializer`, not inline values) |
| 4 | Extract nested ShardAndSchemaParsingTest | APPROVE |
| 5 | S3PresignedUrlServiceTest → unit | APPROVE (fix 3 stale comments claiming it uses LocalStack/real S3) |
| 6 | DemoWelcomeEmailServiceTest → unit | APPROVE (must add throwing-sender case; `Optional.empty()` alone narrows coverage) |
| 7 | GreenMail dynamic port (Lever 5) | APPROVE (must add `junit-platform-launcher` test dep to pom — proposal's "no pom edit" claim is wrong; document eager-start behaviour change) |
