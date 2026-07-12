# Consensus Record — Test-Speed Campaign 2026-07-12

Protocol: 2 independent reviewer agents (separate contexts, no cross-visibility), binary APPROVE/REJECT
per item, 2/2 required to proceed. Full reasoning in `consensus-reviewer-1.md` / `consensus-reviewer-2.md`.

| # | Item | R1 | R2 | Result |
|---|------|----|----|--------|
| 1 | EmailContextBuilderTest → Mockito unit | APPROVE | APPROVE | **PROCEED** |
| 2 | DocxFieldValidatorTest → Mockito unit | APPROVE | APPROVE | **PROCEED** |
| 3 | BillingPropertiesTest → ApplicationContextRunner | APPROVE | APPROVE | **PROCEED** |
| 4 | ShardAndSchemaParsingTest → extract to plain ShardAndSchemaTest | APPROVE | APPROVE | **PROCEED** |
| 5 | S3PresignedUrlServiceTest → plain JUnit + direct InMemoryStorageService | APPROVE | APPROVE | **PROCEED** |
| 6 | DemoWelcomeEmailServiceTest → plain JUnit + Mockito | APPROVE | APPROVE | **PROCEED** |
| 7 | GreenMail dynamic port (system property + yml placeholder + LauncherSessionListener) | APPROVE | APPROVE | **PROCEED** |

## Mandatory implementation notes (binding on builders)

1. **EmailContextBuilderTest**: `OrgSettings` no-arg constructor is protected — use `new OrgSettings("ZAR")`
   (OrgSettings.java:314/328). Map all 5 existing assertions 1:1.
2. **DocxFieldValidatorTest**: custom-field branch coverage lives in `VariableMetadataEndpointTest`
   (verified by both reviewers) — no extra work needed, but don't drop the UNKNOWN-field assertion.
3. **BillingPropertiesTest**: property prefix is `heykazi.billing`, NOT `docteams.billing`
   (BillingProperties.java:18). Inline `withPropertyValues` is FORBIDDEN — it would make the test a
   tautology (asserted values live in production `application.yml:180-185`, and consumer tests mock the
   properties, so nothing else pins them). The runner MUST bind the real yml via
   `ConfigDataApplicationContextInitializer` with the test profile active.
4. **ShardAndSchemaParsingTest**: move the 10 nested tests into existing plain
   `multitenancy/ShardAndSchemaTest.java`, dedupe overlap with its D7 tests, delete the `@Nested` class.
   All outer migration-pin tests in `ShardConfigMigrationTest` STAY.
5. **S3PresignedUrlServiceTest**: instantiate `new InMemoryStorageService()` directly; fix stale
   LocalStack javadoc wording while there.
6. **DemoWelcomeEmailServiceTest**: the test profile DOES auto-configure a JavaMailSender
   (application-test.yml:60-62), so today's test exercises the try/catch send path. The unit test must
   cover BOTH branches: `Optional.empty()` (absent sender) AND a mock sender that throws
   (fire-and-forget catch). `Optional.empty()` alone loses a branch → not acceptable.
7. **GreenMail dynamic port**:
   - `junit-platform-launcher` is NOT on the test compile classpath (verified against starter-test
     4.0.2 / junit-jupiter 6.0.3 poms) — add it as a test-scoped dependency (surefire provides it at
     runtime; the listener class needs it at compile time).
   - Do NOT use `new ServerSocket(0)` (ephemeral range): the suite has mid-test GreenMail
     `stop()`/`start()` windows (PortalEmailServiceIntegrationTest:137/168,
     EmailNotificationChannelIntegrationTest:160 — reviewer 2 corrected Scout B's site attribution)
     during which an OS-assigned ephemeral outbound socket could steal the port → rare BindException
     flake. Scan for a free port in a low, non-ephemeral range (e.g. start at 13025, increment) instead.
   - Keep the `${greenmail.smtp.port:13025}` yml fallback; keep exactly one context cache key
     (config-source-based key verified by both reviewers).

## Flakiness fixes (Scout B, reviewed inline by orchestrator + item 7 consensus)
- Preflight + wrapper scripts (`backend/scripts/verify-preflight.sh`, `backend/scripts/verify.sh`): GO.
- Surefire `forkedProcessExitTimeoutInSeconds=60` + `shutdown=kill`: already in pom since Feb 2026 — no change.
- Hikari register-mbeans: NO-GO (already default; mechanism doesn't map to any observed failure).
