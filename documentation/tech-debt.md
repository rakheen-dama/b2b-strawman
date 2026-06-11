# Tech Debt

Tracked items that are acceptable for now but should be addressed as the system scales.

## TD-001: Unbounded ConcurrentHashMap caches in filters

**Introduced**: Epic 18A (MemberFilter), Epic 6 (TenantFilter)
**Severity**: Low (current scale), Medium (at scale)
**Affected files**:
- `member/MemberFilter.java` — `memberCache` (keyed by `tenantId:clerkUserId`)
- `multitenancy/TenantFilter.java` — `schemaCache` (keyed by `clerkOrgId`)

**Problem**: Both caches are unbounded `ConcurrentHashMap` instances with no TTL or max size. Entries are never evicted (except explicit `evictFromCache` on member delete). At scale (e.g., 1000 tenants x 50 users = 50k entries in memberCache), this could cause memory pressure.

**Why acceptable now**: Entry count is proportional to active users, each entry is ~100 bytes (String key + UUID value), and TenantFilter uses the same pattern without issues. Member IDs are immutable so cached values never go stale (except on delete, which evicts).

**Fix when needed**: Replace with Caffeine cache (`maximumSize` + `expireAfterAccess`). Consider Spring Cache abstraction if multiple caches need unified management.

**Trigger to fix**: When active user count exceeds 10k or memory profiling shows cache as a significant contributor.

---

## Security Concerns

### TD-002: Dev harness security exclusions not profile-gated — RESOLVED

**Introduced**: Epic 57B (PR #131), Epic 58A (PR #132)
**Severity**: Low (current risk), High (if misconfigured)
**Affected files**:
- `security/SecurityConfig.java` — `.requestMatchers("/portal/dev/**").permitAll()` in `portalFilterChain()` (unconditional)
- `portal/CustomerAuthFilter.java` — `shouldNotFilter()` bypasses `/portal/dev/` paths (unconditional)

**Problem**: Both the security filter chain and the `CustomerAuthFilter` exclude `/portal/dev/**` from authentication in ALL profiles, including production. The primary safety net is `@Profile({"local", "dev"})` on `DevPortalController` which prevents the controller bean from registering in production — but the security holes remain open. If anyone adds a non-profile-gated handler at `/portal/dev/**`, it would be accessible without authentication.

**Resolved**: branch `fix/portal-dev-security-profile-gate`. `CustomerAuthFilter.shouldNotFilter()` is the documented single source of truth for which portal paths skip authentication (phase22 architecture), so the gate was applied there: `CustomerAuthFilter` now injects `Environment` and only skips `/portal/dev/**` when one of the dev profiles is active (`Environment.acceptsProfiles(Profiles.of("local", "dev", "keycloak"))`, mirroring the `MockPaymentIntegrationSeeder` house pattern). In any other profile (notably `prod`) the skip is withheld, the filter runs, and an anonymous request to `/portal/dev/**` is rejected with **401** instead of falling through to a 404. The redundant explicit `/portal/dev/**` permitAll in `SecurityConfig.portalFilterChain()` (already subsumed by the blanket `/portal/**` permitAll, with `CustomerAuthFilter` as the real gatekeeper) was removed and replaced with a comment pointing at the filter gate.

**Profile set rationale**: `local` + `dev` cover `DevPortalController`; `keycloak` is included because `MockPaymentController` (`@Profile({"local","dev","keycloak"})`) also serves its mock-payment checkout page under `/portal/dev/**`, and `application-keycloak.yml` explicitly points the checkout base-url at the backend for that route. The `e2e` and `test` profiles do not register any `/portal/dev/**` handler, so they are deliberately excluded.

**Verification**: `SecurityIntegrationTest.devPortalPath_inNonDevProfile_returns401` (integration, `@ActiveProfiles("test")`) observed the 404→401 flip; `CustomerAuthFilterDevPortalGateTest` (pure unit, `MockEnvironment`) covers the local/keycloak skip-allowed and prod skip-withheld branches. Full `./mvnw verify` green.

### TD-003: Thymeleaf dependency unconditional in production classpath

**Introduced**: Epic 58A (PR #132)
**Severity**: Low
**Affected files**:
- `backend/pom.xml` — `spring-boot-starter-thymeleaf` with no scope or Maven profile restriction

**Problem**: Thymeleaf is only used by the dev harness (`DevPortalController`), but the dependency is on the production classpath. This adds the template engine, auto-configuration, and template resolver to every deployment. While no templates are served in production (no controller registers), the template infrastructure increases attack surface marginally (SSTI risk if a future endpoint passes user input to template resolution) and adds unnecessary startup overhead.

**Why acceptable now**: Spring Boot auto-configures Thymeleaf to resolve templates from `classpath:/templates/` which is controlled. No user input reaches template resolution in any production code path.

**Fix when needed**: Scope the dependency to a Maven profile (`dev-tools`) activated only in local/dev builds, or add `<optional>true</optional>` and make `DevPortalConfig` handle the missing classpath gracefully.

**Trigger to fix**: During production image size optimization or a dependency audit.

### TD-004: Portal read-model uses column-level isolation, not schema isolation

**Introduced**: Epic 55A (PR #125)
**Severity**: Medium
**Affected files**:
- `customerbackend/repository/PortalReadModelRepository.java` — all query methods filter by `org_id` parameter
- `config/PortalDataSourceConfig.java` — single `portal` schema, no per-tenant schemas

**Problem**: Unlike tenant-scoped entities (which use schema-per-tenant isolation via Hibernate `@Filter` and `search_path`), the portal read-model uses a single shared `portal` schema with `org_id` column filtering. This means a missing `WHERE org_id = ?` in any query exposes data across organizations. The `JdbcClient` used for portal queries doesn't support Hibernate `@Filter`, so there's no automatic safety net — every query must manually include the filter.

**Why acceptable now**: All existing queries are correctly parameterized and filter by `org_id`. Integration tests (`CrossTenantPortalIsolationTest`) verify cross-org isolation. The read-model is denormalized and read-only, reducing the surface for write-path bugs.

**Fix when needed**: Consider a `PortalQueryBuilder` wrapper that auto-appends `org_id` filtering, or add a pre-commit lint rule that flags portal queries missing `org_id` in their WHERE clause.

**Trigger to fix**: When adding new portal read-model queries, or if the number of query methods exceeds ~20.

### TD-005: No rate limiting on `/internal/*` API key endpoints

**Introduced**: Epic 5A (ApiKeyAuthFilter)
**Severity**: Low (VPC-only), High (if exposed)
**Affected files**:
- `security/ApiKeyAuthFilter.java` — validates `X-API-KEY` with constant-time comparison but allows unlimited attempts

**Problem**: The API key comparison uses `MessageDigest.isEqual()` (correct — prevents timing attacks), but there is no rate limiting on failed authentication attempts. An attacker with network access to `/internal/*` endpoints can brute-force the API key without backoff or lockout.

**Why acceptable now**: `/internal/*` endpoints are designed to be VPC-only (not exposed through ALB). Network-level isolation is the primary control. The API key is a secondary defense.

**Fix when needed**: Add IP-based rate limiting (e.g., 10 failed attempts per minute per IP) using a simple Caffeine-backed counter or Spring Security's built-in rate limiting.

**Trigger to fix**: Before exposing internal endpoints beyond VPC, or during a production security hardening pass.

### TD-006: Magic link rate limiting is per-contact only, not per-IP

**Introduced**: Epic 54A (PR #123)
**Severity**: Low
**Affected files**:
- `portal/MagicLinkService.java` — `countByPortalContactIdAndCreatedAtAfter()` rate check (3 tokens per 5 min per contact)

**Problem**: Rate limiting on magic link generation is keyed by `portalContactId` (account-based), not by IP address. An attacker can request magic links for different contacts from the same IP without hitting any limit. While email enumeration is mitigated (generic response message), the lack of IP-based throttling allows reconnaissance and potential abuse at scale.

**Why acceptable now**: The existing per-contact limit (3 per 5 minutes) prevents token flooding per account. The generic response message prevents email enumeration. Portal contact creation is org-admin-only, limiting the contact pool size.

**Fix when needed**: Add `created_ip`-based rate limiting alongside the existing contact-based check (e.g., 10 requests per 5 minutes per IP). The `created_ip` column already exists on `magic_link_tokens`.

**Trigger to fix**: Before public launch of the customer portal, or if abuse patterns are detected in logs.

### TD-007: Security events logged at DEBUG level, not visible in production

**Introduced**: Epic 54A (PR #123)
**Severity**: Low
**Affected files**:
- `portal/MagicLinkService.java` — token generation and verification logged at `log.debug()`
- `portal/PortalAuthController.java` — auth flow events at DEBUG

**Problem**: Magic link generation, token verification, and failed auth attempts are logged at DEBUG level. Production typically runs at INFO or above, making these events invisible in production logs. Security-relevant events (successful auth, failed auth, rate limit hits) should be visible without enabling DEBUG.

**Why acceptable now**: The audit event infrastructure (Phase 6) can separately track these events via `AuditEventService`. Structured logging with MDC fields is in place.

**Fix when needed**: Promote security-relevant log statements to INFO level: `log.info("Magic link generated for contact {}", contactId)`, `log.warn("Failed token verification for contact {}", contactId)`.

**Trigger to fix**: Before public launch of the customer portal.

### TD-008: ArchUnit 1.3.0 silently imports zero classes on JDK 25 — RESOLVED

**Introduced**: surfaced 2026-05-02 during PR #1265 (TenantScopedRunner consolidation, ADR-T008).
**Resolved**: same PR, by upgrading `archunit-junit5` from `1.3.0` → `1.4.2` in `backend/pom.xml`. ArchUnit 1.4.x supports JDK 25's class-location resolution; verified post-upgrade that `@AnalyzeClasses(packages = "io.b2mash.b2b.b2bstrawman", importOptions = ImportOption.DoNotIncludeTests.class)` imports the full production class set (>100 classes), and that `methods().that().haveName(X).should().beDeclaredInClassesThat().resideInAPackage(Y)` rules now actually fire on injected violations (previously failed silently).

**Side-effect of the fix**: `LayerDependencyRulesTest` and `TestConventionsTest` (which were previously passing vacuously) are now actually enforced. If either rule starts failing post-upgrade, the underlying violation was always present — surfacing was the point.

**Original problem (kept for context)**: ArchUnit 1.3.0's `@AnalyzeClasses` returned zero classes on JDK 25 in this codebase's test setup. Both existing rules used `allowEmptyShould(true)`, passing vacuously rather than failing. A contributor could have added a `PostgreSQLContainer` to any test and `TestConventionsTest` would not have caught it.

### TD-009: Pre-existing controller→repository violations surfaced by TD-008 fix

**Introduced**: surfaced 2026-05-02 when the ArchUnit upgrade in TD-008 made `LayerDependencyRulesTest.controllers_should_not_depend_on_repositories` actually enforce. The violations themselves predate this PR by months — they were hidden by ArchUnit 1.3.0's silent vacuous pass.
**Severity**: Low — these controllers work; the rule is about discipline / boundary clarity, not correctness.

**Affected files** (all violate the controller-discipline rule by injecting `*Repository` types directly):

- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/payment/MockPaymentController.java` — injects `InvoiceRepository`, `OrgSchemaMappingRepository`.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalBrandingController.java` — injects `OrgSchemaMappingRepository`, `OrganizationRepository`, `OrgSettingsRepository`.
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalDigestInternalController.java` — injects repository types.

(Plus pre-existing exemptions for `DevPortalController`, `InternalAuditController`, `PaymentWebhookController` — these were already on the rule's exclusion list before this PR.)

**Why acceptable now**: Adding the three new violators to the rule's exclusion list (file: `backend/src/test/java/.../architecture/LayerDependencyRulesTest.java`) preserves the rule's enforcement for new code while explicitly carrying these three as known debt. The `backend/CLAUDE.md` "Known violations" section already lists 7 other controllers that should not be used as reference patterns; consider amending that list to include these 3 too.

**Fix when needed**: For each controller, extract a service that does the repository work and have the controller delegate. Pattern: `controller calls exactly one service method per endpoint, returns ResponseEntity` (`backend/CLAUDE.md` "Controller Discipline" section). MockPaymentController is a dev-only mock and may stay as-is or move under the dev-profile-only path used by `DevPortalController`.

**Trigger to fix**: Opportunistic — when next touching any of these controllers for a feature change.

## TD-D5: Shard-unaware `runForTenant()` call sites (event-listener shard propagation)

**Introduced**: pre-sharding code; surfaced by the Phase 75 infra review (kazi-infra-review-scheduling-sharding.md, finding D5)
**Severity**: Low today (sharding is off by default; no secondary shards exist), **Blocker before enabling secondary shards in production**
**Guard**: `architecture/TenantScopeBindingTest#no_new_shard_unaware_tenant_binding` (ArchUnit) freezes the set below — new code must use `runForTenantOnShard` / `callForTenantOnShard`; the set may only shrink.

**Problem**: These classes bind tenant scope via the shard-unaware `RequestScopes.runForTenant` / `callForTenant` / `runForTenantWithMember`, which leave `SHARD_ID` unbound so `TenantIdentifierResolver` defaults to the primary shard. For any tenant on a secondary shard, these paths read/write the wrong database.

**Progress**:
- PR #1383 (D1/D2) fixed the job-queue paths.
- PR #1386 (D5) migrated the call sites with the `OrgSchemaMapping` already in hand: `SubscriptionExpiryJob`, `PortalDigestScheduler`, `PortalAuthService`, `PaymentWebhookController`.
- PR #1389 migrated all `@TransactionalEventListener(AFTER_COMMIT)` handlers whose event is a `DomainEvent` to `runForTenantOnShard(event.shardId())`, added `DomainEvent.shardId()` (a default reading `RequestScopes.SHARD_ID` at call time), and fixed `AssistantController`'s SSE virtual-thread carrier to bind `SHARD_ID`. `PortalDocumentNotificationHandler` was fully migrated and removed from the grandfathered set.

**`DomainEvent.shardId()` mechanism + constraint**: it is a *lazy* read of the live `SHARD_ID`, correct **only** for synchronous `AFTER_COMMIT` listeners on the publishing thread (the binding from `TenantFilter` / `runForTenantOnShard` is still active and falls through nested `runForTenant` scopes). Verified by `EventListenerShardScopeTest`. **If any such listener becomes `@Async`, or events move to an outbox/queued-replay model, `shardId()` will silently return `"primary"` — at that point `shardId` MUST become an explicit `DomainEvent` record field captured at publish time.**

**Remaining (still grandfathered) — must resolve before secondary shards go live**:
- **`PortalDomainEvent` / `BillingRunEvent` handlers** in the otherwise-migrated files (`PortalEventHandler`, `NotificationEventHandler`, `AccountingSyncEventListener`, `PortalEmailNotificationChannel`, proposal / information-request / trust listeners): these event types carry no `shardId`, so the handlers still call `runForTenant` and rely on **ScopedValue inheritance** of the outer-scope `SHARD_ID` (proven correct for the synchronous AFTER_COMMIT path by `EventListenerShardScopeTest`). To remove them from the set, give those event base types a `shardId()` and switch to `runForTenantOnShard`.
- **Need a repository lookup / minor refactor**: `MemberSyncService`, `EmailWebhookService`, `UnsubscribeService`, `AcceptanceService` (portal `AcceptanceContext` lacks shardId), `PackInstallService`, `PackReconciliationRunner`, `InternalAuditController`, `AuditExportService`, `CustomerAuthFilter`, `PortalBrandingController`, and the portal sync services (`DeadlinePortalSyncService`, `PortalResyncService`, `RetainerPortalSyncService`, `TrustLedgerPortalSyncService`). Each can inject `OrgSchemaMappingRepository` (most already do) and look up `getShardId()` at the call site, or carry shardId on the relevant context object.

**Fix**: migrate each remaining grandfathered class to `runForTenantOnShard`/`callForTenantOnShard` and remove it from `D5_GRANDFATHERED` in `TenantScopeBindingTest`. The `PortalDomainEvent`/`BillingRunEvent` payload change should be batched with the sharding-activation work.

**Trigger to fix**: before setting `kazi.sharding.enabled=true` with any secondary shard in a production environment.
