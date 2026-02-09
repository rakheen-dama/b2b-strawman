package io.b2mash.b2b.b2bstrawman.multitenancy;

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

  public static final String DEFAULT_TENANT = "public";

  private RequestScopes() {}
}
