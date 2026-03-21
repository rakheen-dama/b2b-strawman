package io.b2mash.b2b.b2bstrawman.assistant.tool;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable snapshot of request-scoped tenant and member context, constructed from {@link
 * RequestScopes} before tool execution. Passed to each tool's {@link AssistantTool#execute} method.
 *
 * <p>Most tools do not use this directly — they rely on the existing service methods which read
 * {@link RequestScopes} themselves. It is available for tools that need explicit context (e.g.,
 * {@code get_my_tasks} passes {@code memberId()} to {@code MyWorkService}).
 */
public record TenantToolContext(
    String tenantId, UUID memberId, String orgRole, Set<String> capabilities) {

  /**
   * Constructs a {@code TenantToolContext} from the currently bound {@link RequestScopes}. Must be
   * called within a scoped binding (i.e., inside a virtual thread that has re-bound the scoped
   * values from the request thread).
   *
   * @throws IllegalStateException if {@code TENANT_ID} is not bound
   * @throws io.b2mash.b2b.b2bstrawman.multitenancy.MemberContextNotBoundException if {@code
   *     MEMBER_ID} is not bound
   */
  public static TenantToolContext fromRequestScopes() {
    return new TenantToolContext(
        RequestScopes.requireTenantId(),
        RequestScopes.requireMemberId(),
        RequestScopes.getOrgRole(),
        RequestScopes.getCapabilities());
  }
}
