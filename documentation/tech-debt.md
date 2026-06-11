# Tech Debt

Tracked items that are acceptable for now but should be addressed as the system scales.

## TD-001: Unbounded ConcurrentHashMap caches in filters — RESOLVED

**Introduced**: Epic 18A (MemberFilter), Epic 6 (TenantFilter)
**Severity**: Low (current scale), Medium (at scale)
**Affected files**:
- `member/MemberFilter.java` — `memberCache` (keyed by `tenantId:clerkUserId`)
- `multitenancy/TenantFilter.java` — `tenantCache` (keyed by `clerkOrgId`)

**Problem**: Both caches were unbounded `ConcurrentHashMap` instances with no TTL or max size. Entries were never evicted (except explicit `evictFromCache` on member delete/role change). At scale (e.g., 1000 tenants x 50 users = 50k entries in memberCache), this could cause memory pressure.

**Why acceptable now**: Entry count is proportional to active users, each entry is ~100 bytes (String key + UUID value), and TenantFilter uses the same pattern without issues. Member IDs are immutable so cached values never go stale (except on delete, which evicts).

**Resolved**: PR #42 ("Tune backend performance", commit `8866a73ec`) replaced both unbounded `ConcurrentHashMap` caches with bounded Caffeine caches (`maximumSize` + `expireAfterWrite`). `TenantFilter.tenantCache` is sized at `maximumSize(10_000)` (one entry per provisioned org); `MemberFilter.memberCache` is sized at `maximumSize(50_000)` — larger because member entries are keyed by `tenantId:clerkUserId` (≈ tenants × users) rather than per-org. Both use `expireAfterWrite(Duration.ofHours(1))`. The explicit `evictFromCache` eviction semantics are preserved — invalidation still fires on member sync (`MemberSyncService`) and role change (`OrgRoleService`), now via `Cache.invalidate(key)` instead of `Map.remove(key)`. Eviction coverage is verified by `MemberFilterCacheEvictionTest`.

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

### TD-003: Thymeleaf dependency unconditional in production classpath — INVALID (premise disproven)

**Introduced**: Epic 58A (PR #132)
**Severity**: Low (as originally filed)
**Affected files**:
- `backend/pom.xml` — `spring-boot-starter-thymeleaf` with no scope or Maven profile restriction

**Original problem statement**: "Thymeleaf is only used by the dev harness (`DevPortalController`)", so the production classpath carries the template engine + auto-configuration unnecessarily; proposed fix was to scope the dependency out of prod (or exclude `ThymeleafAutoConfiguration` in the prod profile).

**Why INVALID (verified 2026-06-11 by empirical grep + source read, not by spec)**: Thymeleaf is a **production** dependency with multiple production consumers, not a dev-only one. Two of them depend specifically on the Spring-managed `ITemplateEngine`/`TemplateEngine` bean that **`ThymeleafAutoConfiguration` provides** — there is no custom `@Bean TemplateEngine` anywhere in the codebase to supply it otherwise (`grep -rn 'TemplateEngine' backend/src/main/java` finds zero `@Bean` definitions). Excluding the auto-config in prod, or removing the dependency, would fail context startup (unsatisfied constructor dependency) and break these live features:

- `invoice/InvoiceRenderingService.java:37,47,124` — constructor-injects `ITemplateEngine` (the auto-configured bean) and calls `templateEngine.process("invoice-preview", ctx)` to render invoice HTML previews. Plain `@Service` (line 30), no `@Profile` — active in prod.
- `acceptance/AcceptanceCertificateService.java:41,51,128` — constructor-injects `TemplateEngine` (the auto-configured bean) and calls `templateEngine.process("certificates/certificate-of-acceptance", ctx)`. Plain `@Service` (line 28), no `@Profile` — active in prod.

Additional production Thymeleaf consumers build their **own** `new TemplateEngine()` (so they'd survive an auto-config exclusion, but still prove Thymeleaf is not dev-only): `reporting/ReportRenderingService.java:45,56,268` (report HTML/PDF rendering from `template_body`) and `notification/template/EmailTemplateRenderer.java:27,30,102` (branded HTML emails). Reporting's use is the same path noted in `tasks/tech-debt-assessment.md` **B-03** ("Reporting uses Thymeleaf, ADR-263 says Tiptap" — both ADRs valid for their scope; cross-reference).

**Decision**: No code change. The dependency stays on the production classpath and `ThymeleafAutoConfiguration` remains active in all profiles. The Wave 1.5 minimal fix (exclude `ThymeleafAutoConfiguration` in `application-prod.yml`) was **not applied** because it would break prod invoice-preview and acceptance-certificate rendering at startup. The `DevPortalController` is one Thymeleaf consumer among several, not the only one — the original filing missed the invoice/acceptance/reporting/email consumers.

### TD-004: Portal read-model uses column-level isolation, not schema isolation

**Introduced**: Epic 55A (PR #125)
**Severity**: Medium
**Status**: GUARD IN PLACE (Wave 3.2a) — structural guard test landed; repository split (Wave 3.2b) still pending.
**Affected files**:
- `customerbackend/repository/PortalReadModelRepository.java` — ~52 hand-written `org_id`-filtered JDBC methods
- `customerbackend/repository/PortalTrustReadModelRepository.java`, `PortalRetainerSummaryRepository.java`, `PortalRetainerConsumptionEntryRepository.java`, `PortalDeadlineViewRepository.java` — sibling read-model repos that scope by `customer_id` (these tables have NO `org_id` column; V19–V21)
- `config/PortalDataSourceConfig.java` — single `portal` schema, no per-tenant schemas
- `test/.../architecture/PortalReadModelOrgScopingGuardTest.java` — the Wave 3.2a guard (see below)

**Problem**: Unlike tenant-scoped entities (which use schema-per-tenant isolation via Hibernate `@Filter` and `search_path`), the portal read-model uses a single shared `portal` schema with column-level filtering. This means a missing tenant predicate in any query exposes data across organizations. The `JdbcClient` used for portal queries doesn't support Hibernate `@Filter`, so there's no automatic safety net — every query must manually include the filter.

**Correction to original filing**: not "all query methods filter by `org_id`". The layer uses **two** tenant discriminators by design — `org_id` (projects/documents/comments/invoices/tasks/requests) and `customer_id` (trust/retainer/deadline tables, which have no `org_id` column at all). A handful of statements legitimately bind neither (FK children of org-scoped parents, magic-link `request_token` lookups, PK/contact-scoped writes driven only by internal sync handlers, and one admin backfill sweep). See the guard's `SCOPING_EXEMPT` map for the per-statement justifications.

**Wave 3.2a guard (DONE)**: `PortalReadModelOrgScopingGuardTest` scans the source of the read-model **package** (not a single class, so Wave 3.2b's split successors are auto-covered) and asserts every `jdbc.sql(...)` statement either binds a tenant discriminator (`org_id`/`customer_id`) in a predicate-or-INSERT-column position, or is on the justified exemption list. The check is predicate-aware (a discriminator in a `SELECT` list does not count). Verified to fail with a method-naming message when an unscoped query is introduced.

**Fix when needed (Wave 3.2b)**: Split `PortalReadModelRepository` into per-domain read-repositories and introduce a `PortalQueryBuilder`/helper that makes the tenant predicate impossible to omit. The guard above protects that refactor. Several exempted statements (e.g. `findRequestById`, `updatePortalRequestStatus`, `deleteBySourceEntityAndId`) are hardening candidates that could gain an explicit discriminator predicate during the split.

**Trigger to fix**: When adding new portal read-model queries, or if the number of query methods exceeds ~20 (exceeded — guard now compensates until the split lands).

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

**Progress** (broader `backend/CLAUDE.md` "Known violations" list — the thin-controller-discipline cleanup beyond just repo injection):

- PR #1424 (2026-06-11): `ProjectController` — moved ~117 lines of view-filter/tag/custom-field listing logic into `ProjectService`; controller methods are pure delegation. (ProjectController was never on the ArchUnit exclusion list — it never injected a repository — so no rule change was needed.)
- `DocumentController` (2026-06-11): moved scope-dispatch (`GET /api/documents` ORG/CUSTOMER switch), uploader-name resolution + DTO mapping, and the org-context guard into `DocumentService` (DTO-returning orchestration methods + `RequestScopes`-resolving upload overloads). Controller is now pure delegation. Removed from the `backend/CLAUDE.md` "Known violations" list. No ArchUnit exclusion entry existed (it never injected a repository), so no rule change was needed. Characterization test (`DocumentScopeIntegrationTest#orgScopedListingResolvesUploaderName`) added for the previously-unasserted `uploadedByName` path before the move.

**Trigger to fix**: Opportunistic — when next touching any of these controllers for a feature change.

**Thin-controller cleanup progress (Wave 3.1, TD-009 family)** — separate from the repository-injection violators above, the `backend/CLAUDE.md` "Known violations" prose list named controllers that carry orchestration/validation logic the thin-controller rule forbids. These are being restored to pure delegation one controller per PR:

- **Slice 1 — `ProjectController`** (PR #1424, merged): ~117 lines of view-filter/tag/custom-field logic moved into `ProjectService`. Removed from the known-violators list.
- **Slice 2 — `DocumentController`**: thin-controller restoration (merged/staged in the same family).
- **Slice 3 — `DashboardController`** (this slice): two violation classes removed — (a) multi-service orchestration (each project-scoped endpoint called `ProjectAccessService.requireViewAccess` *and* a `DashboardService` getter in sequence) and (b) business-policy conditionals in the controller (`from.isAfter(to)` date-range validation on 4 endpoints; `Math.max/min` activity-`limit` clamping). The access-control orchestration now lives inside the `DashboardService` getters (which take `ActorContext`); date validation moved to a private `requireValidDateRange` guard; the limit clamp moved into `getCrossProjectActivity` (bounds as named constants). `DashboardController` no longer injects `ProjectAccessService` and every method is one-line delegation. Removed from the known-violators list. Characterization tests added first for the previously-uncovered moved paths (inverted-date-range 400 ProblemDetail on kpis/team-workload/member-hours/personal; activity limit clamped above 50 and below 1). Note: `DashboardController` was never in the ArchUnit exclusion list or the repository-injection set above — it injected no repositories, so no exemption needed deleting; the rule already passed for it.

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
