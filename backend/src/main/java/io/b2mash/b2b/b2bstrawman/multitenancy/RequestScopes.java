package io.b2mash.b2b.b2bstrawman.multitenancy;

import io.b2mash.b2b.b2bstrawman.exception.MissingOrganizationContextException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

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

  private RequestScopes() {}
}
