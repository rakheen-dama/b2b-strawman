package io.b2mash.b2b.b2bstrawman.multitenancy;

import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.MissingOrganizationContextException;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Request-scoped values for multitenancy and member identity. Bound by servlet filters, read by
 * controllers/services/Hibernate.
 *
 * <p>These replace the former ThreadLocal-based TenantContext and MemberContext. Values are
 * immutable within their scope and automatically unbound when the binding lambda (run/call) exits.
 */
public final class RequestScopes {

  /** Tenant schema name (e.g. "tenant_a1b2c3d4e5f6"). Bound by TenantFilter. */
  public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

  /** Current member's UUID within the tenant. Bound by MemberFilter. */
  public static final ScopedValue<UUID> MEMBER_ID = ScopedValue.newInstance();

  /** Current member's org role ("owner", "admin", "member"). Bound by MemberFilter. */
  public static final ScopedValue<String> ORG_ROLE = ScopedValue.newInstance();

  /** Clerk organization ID (e.g., "org_abc123"). Bound by TenantFilter. */
  public static final ScopedValue<String> ORG_ID = ScopedValue.newInstance();

  /** Authenticated customer's UUID. Bound by CustomerAuthFilter for portal requests. */
  public static final ScopedValue<UUID> CUSTOMER_ID = ScopedValue.newInstance();

  /** Authenticated portal contact's UUID. Bound by CustomerAuthFilter for portal requests. */
  public static final ScopedValue<UUID> PORTAL_CONTACT_ID = ScopedValue.newInstance();

  /**
   * Automation execution ID. Bound by AutomationActionExecutor when executing actions. Used by
   * services to propagate the execution ID into domain events for cycle detection.
   */
  public static final ScopedValue<UUID> AUTOMATION_EXECUTION_ID = ScopedValue.newInstance();

  /** Effective capability names for the current member. Bound by MemberFilter. */
  public static final ScopedValue<Set<String>> CAPABILITIES = ScopedValue.newInstance();

  /** JWT group memberships (e.g., "platform-admins"). Bound by PlatformAdminFilter. */
  public static final ScopedValue<Set<String>> GROUPS = ScopedValue.newInstance();

  private static final String PLATFORM_ADMINS_GROUP = "platform-admins";

  public static final String DEFAULT_TENANT = "public";

  /** Returns the current member's UUID. Throws if not bound by filter chain. */
  public static UUID requireMemberId() {
    if (!MEMBER_ID.isBound()) {
      throw new MemberContextNotBoundException();
    }
    return MEMBER_ID.get();
  }

  /** Returns the Clerk org ID. Throws if not bound by filter chain. */
  public static String requireOrgId() {
    if (!ORG_ID.isBound()) {
      throw new MissingOrganizationContextException();
    }
    return ORG_ID.get();
  }

  /** Returns the current member's org role, or null if not bound. */
  public static String getOrgRole() {
    return ORG_ROLE.isBound() ? ORG_ROLE.get() : null;
  }

  /**
   * Requires the current member to have the "owner" org role. Throws ForbiddenException if the
   * current member is not the organization owner.
   */
  public static void requireOwner() {
    if (!Roles.ORG_OWNER.equals(getOrgRole())) {
      throw new ForbiddenException(
          "Owner required", "Only the organization owner can perform this action");
    }
  }

  /** Returns the tenant schema name. Throws if not bound by filter chain. */
  public static String requireTenantId() {
    if (!TENANT_ID.isBound()) {
      throw new IllegalStateException("Tenant context not available — TENANT_ID not bound");
    }
    return TENANT_ID.get();
  }

  /** Returns the tenant schema name, or null if not bound. */
  public static String getTenantIdOrNull() {
    return TENANT_ID.isBound() ? TENANT_ID.get() : null;
  }

  /** Returns the Clerk org ID, or null if not bound. */
  public static String getOrgIdOrNull() {
    return ORG_ID.isBound() ? ORG_ID.get() : null;
  }

  /** Returns the authenticated customer's UUID. Throws if not bound by CustomerAuthFilter. */
  public static UUID requireCustomerId() {
    if (!CUSTOMER_ID.isBound()) {
      throw new IllegalStateException("Customer context not available — CUSTOMER_ID not bound");
    }
    return CUSTOMER_ID.get();
  }

  /** Returns the authenticated portal contact's UUID. Throws if not bound by CustomerAuthFilter. */
  public static UUID requirePortalContactId() {
    if (!PORTAL_CONTACT_ID.isBound()) {
      throw new IllegalStateException(
          "Portal contact context not available — PORTAL_CONTACT_ID not bound");
    }
    return PORTAL_CONTACT_ID.get();
  }

  /** Returns the JWT groups, or an empty set if not bound. */
  public static Set<String> getGroups() {
    return GROUPS.isBound() ? GROUPS.get() : Collections.emptySet();
  }

  /** Returns true if the current request has the platform-admins group. */
  public static boolean isPlatformAdmin() {
    return getGroups().contains(PLATFORM_ADMINS_GROUP);
  }

  /** Returns the effective capabilities, or an empty set if not bound. */
  public static Set<String> getCapabilities() {
    return CAPABILITIES.isBound() ? CAPABILITIES.get() : Collections.emptySet();
  }

  /**
   * Returns true if the current member has the given capability. Owner/admin system roles have all
   * individual capability names resolved into the set by {@code OrgRoleService}.
   */
  public static boolean hasCapability(String capability) {
    return getCapabilities().contains(capability);
  }

  /**
   * Run {@code action} with {@link #TENANT_ID} (and optionally {@link #ORG_ID}) bound on a fresh
   * ScopedValue carrier. The only sanctioned way to bind tenant scope outside this class; see
   * {@code TenantScopeBindingTest} and ADR-T008.
   *
   * <p>Replaces the duplicated private {@code handleInTenantScope} helpers that previously lived in
   * 14 notification handlers (PR #1, 2026-05-02). Null-rejection is intentional: schema-per-tenant
   * means an unbound tenant scope would run repository operations against the default {@code
   * public} search_path, silently reading/writing the wrong schema. Failing fast at the entry point
   * surfaces the bug at the call site rather than letting it fan out into Hibernate.
   *
   * <p>Blank {@code orgId} values are treated as null (no binding). This diverges slightly from the
   * 14 original helpers, which would have bound an empty/whitespace string as ORG_ID — that
   * behaviour is judged a bug since blank values are never legitimate input.
   *
   * <p><b>Nested-call note:</b> when {@code runForTenant} is called from within another {@code
   * runForTenant} scope and the inner call passes {@code orgId == null} (or blank), the outer
   * scope's {@code ORG_ID} binding remains visible to the inner action body via {@link
   * #getOrgIdOrNull()}. {@code TENANT_ID} is always rebound, so this only affects {@code ORG_ID}
   * reads. The migrated AFTER_COMMIT handlers do not exercise this path (every event payload they
   * consume carries a non-null {@code orgId}), so the asymmetry is theoretical in PR #1's scope;
   * documented here as a known limitation. See {@code
   * RequestScopesTest.runForTenant_nestedCallWithNullOrgId_outerOrgIdRemainsVisible}.
   *
   * @throws IllegalArgumentException if {@code tenantId} is null or blank.
   * @throws NullPointerException if {@code action} is null.
   */
  public static void runForTenant(String tenantId, @Nullable String orgId, Runnable action) {
    Objects.requireNonNull(action, "action");
    requireValidTenantId(tenantId);
    bindTenantScope(tenantId, orgId).run(action);
  }

  /**
   * Variant of {@link #runForTenant} that returns a value. Checked exceptions thrown by the
   * Callable are wrapped in {@link RuntimeException} per JDK Callable convention.
   *
   * @throws IllegalArgumentException if {@code tenantId} is null or blank.
   * @throws NullPointerException if {@code action} is null.
   */
  public static <T> T callForTenant(String tenantId, @Nullable String orgId, Callable<T> action) {
    Objects.requireNonNull(action, "action");
    requireValidTenantId(tenantId);
    ScopedValue.CallableOp<T, Exception> op = action::call;
    try {
      return bindTenantScope(tenantId, orgId).call(op);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Variant of {@link #runForTenant} that additionally binds {@link #MEMBER_ID} to a system-actor
   * sentinel UUID. Use only from internal admin-authenticated callers (currently the two portal
   * read-model backfill helpers — {@code RetainerPortalSyncService.backfillForTenant} and {@code
   * TrustLedgerPortalSyncService.backfillForTenant}) where downstream services read {@link
   * #requireMemberId()} for audit attribution and would throw without a bound member.
   *
   * <p>Tenant-isolation guard remains the caller's responsibility: this method binds whatever
   * {@code tenantId} / {@code orgId} it is handed without an authorisation check. Callers that
   * accept these values from a request parameter MUST verify they are authorised for the current
   * request scope before invoking (see {@code RetainerPortalSyncService.backfillForTenant} for the
   * canonical guard pattern: assert {@link #ORG_ID} is bound and matches the supplied {@code orgId}
   * before calling).
   *
   * @throws IllegalArgumentException if {@code tenantId} is null or blank.
   * @throws NullPointerException if {@code action} or {@code actorId} is null.
   */
  public static void runForTenantAsSystemActor(
      String tenantId, @Nullable String orgId, UUID actorId, Runnable action) {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(actorId, "actorId");
    requireValidTenantId(tenantId);
    bindTenantScope(tenantId, orgId).where(MEMBER_ID, actorId).run(action);
  }

  private static void requireValidTenantId(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must be non-null and non-blank");
    }
  }

  private static ScopedValue.Carrier bindTenantScope(String tenantId, @Nullable String orgId) {
    ScopedValue.Carrier carrier = ScopedValue.where(TENANT_ID, tenantId);
    if (orgId != null && !orgId.isBlank()) {
      carrier = carrier.where(ORG_ID, orgId);
    }
    return carrier;
  }

  private RequestScopes() {}
}
