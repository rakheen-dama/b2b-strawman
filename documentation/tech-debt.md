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

### TD-002: Dev harness security exclusions not profile-gated

**Introduced**: Epic 57B (PR #131), Epic 58A (PR #132)
**Severity**: Low (current risk), High (if misconfigured)
**Affected files**:
- `security/SecurityConfig.java` — `.requestMatchers("/portal/dev/**").permitAll()` in `portalFilterChain()` (unconditional)
- `portal/CustomerAuthFilter.java` — `shouldNotFilter()` bypasses `/portal/dev/` paths (unconditional)

**Problem**: Both the security filter chain and the `CustomerAuthFilter` exclude `/portal/dev/**` from authentication in ALL profiles, including production. The primary safety net is `@Profile({"local", "dev"})` on `DevPortalController` which prevents the controller bean from registering in production — but the security holes remain open. If anyone adds a non-profile-gated handler at `/portal/dev/**`, it would be accessible without authentication.

**Why acceptable now**: `DevPortalController` is profile-gated, so requests to `/portal/dev/**` return 404 in production. ADR-033 documents this layered approach.

**Fix when needed**: Move `/portal/dev/**` permitAll into a separate `@Profile({"local", "dev"})` SecurityFilterChain bean. Inject `Environment` into `CustomerAuthFilter` and only bypass `/portal/dev/` when local/dev profiles are active.

**Trigger to fix**: Before adding any additional dev-only endpoints, or during a production security hardening pass.

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
