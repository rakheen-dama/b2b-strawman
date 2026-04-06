package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Set;
import java.util.UUID;

/**
 * Helper for running test code within a tenant scope, eliminating repeated ScopedValue.where()
 * chains in test classes.
 */
public final class TenantTestSupport {

  /** Functional interface matching ScopedValue.CallableOp signature for test lambdas. */
  @FunctionalInterface
  public interface ThrowingCallable<T> {
    T call() throws Exception;
  }

  private TenantTestSupport() {}

  /** Run action in a tenant scope (tenant ID only). */
  public static void runInTenant(String tenantSchema, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema).run(action);
  }

  /** Run action in a full member scope (tenant + org + member + role + capabilities). */
  public static void runAsActor(
      String tenantSchema,
      String orgId,
      UUID memberId,
      String orgRole,
      Set<String> capabilities,
      Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, orgRole)
        .where(RequestScopes.CAPABILITIES, capabilities)
        .run(action);
  }

  /** Run action as actor with empty capabilities. */
  public static void runAsActor(
      String tenantSchema, String orgId, UUID memberId, String orgRole, Runnable action) {
    runAsActor(tenantSchema, orgId, memberId, orgRole, Set.of(), action);
  }

  /** Callable version of runInTenant. */
  public static <T> T callInTenant(String tenantSchema, ThrowingCallable<T> action)
      throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema).call(action::call);
  }

  /** Callable version of runAsActor. */
  public static <T> T callAsActor(
      String tenantSchema,
      String orgId,
      UUID memberId,
      String orgRole,
      Set<String> capabilities,
      ThrowingCallable<T> action)
      throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, orgRole)
        .where(RequestScopes.CAPABILITIES, capabilities)
        .call(action::call);
  }

  /** Callable version of runAsActor with empty capabilities. */
  public static <T> T callAsActor(
      String tenantSchema, String orgId, UUID memberId, String orgRole, ThrowingCallable<T> action)
      throws Exception {
    return callAsActor(tenantSchema, orgId, memberId, orgRole, Set.of(), action);
  }
}
